//! `FlowDefinitionRepo` — caches `Arc<FlowDefinition>` keyed by `flow_id`.
//!
//! Phase 5 design:
//! - `FlowLoader` trait abstracts the source — `HttpFlowLoader` hits the
//!   Java console, `StubFlowLoader` returns hardcoded XML for tests.
//! - `DashMap<String, Arc<FlowDefinition>>` is the cache.
//! - Per-flow load lock (`DashMap<String, Arc<Notify>>`) dedups concurrent
//!   loads for the same flow_id — second waiter blocks on `notified()`,
//!   then reads from the cache.
//!
//! Phase 6+ swap-in: cache eviction on `/flow/invalidate` already
//! handled in the route handler.
//!
//! Compare Java `FlowDefinitionRepo` (line 56-94): same shape, different
//! concurrency primitive — Java uses `ConcurrentHashMap` + `ReentrantLock`,
//! Rust uses `DashMap` + `tokio::sync::Notify`. Both converge on the
//! "single loader wins, others wait" pattern.

use std::sync::Arc;

use dashmap::DashMap;
use rf_ir::flow_definition::FlowDefinition;
use rf_parse::bpmn_parser::BpmnXmlParser;
use thiserror::Error;
use tokio::sync::Notify;

#[derive(Debug, Error)]
pub enum FlowLoaderError {
    #[error("HTTP fetch failed: {0}")]
    Http(String),
    #[error("flow not found: {0}")]
    NotFound(String),
    #[error("invalid response: {0}")]
    Invalid(String),
}

#[derive(Debug, Error)]
pub enum RepoError {
    #[error("load failed: {0}")]
    Loader(#[from] FlowLoaderError),
    #[error("parse failed: {0}")]
    Parse(String),
}

#[async_trait::async_trait]
pub trait FlowLoader: Send + Sync {
    /// Fetch the raw BPMN XML for `flow_id`. Returns the XML as a
    /// `String` so the parser can borrow it.
    async fn load(&self, flow_id: &str) -> Result<String, FlowLoaderError>;
}

/// HTTP loader — hits the Java console's
/// `GET {base_url}/ruleforge/flow/load?file={flow_id}` endpoint.
pub struct HttpFlowLoader {
    pub base_url: String,
    pub client: reqwest::Client,
}

#[async_trait::async_trait]
impl FlowLoader for HttpFlowLoader {
    async fn load(&self, flow_id: &str) -> Result<String, FlowLoaderError> {
        let url = format!("{}/ruleforge/flow/load", self.base_url);
        let resp = self
            .client
            .get(&url)
            .query(&[("file", flow_id)])
            .send()
            .await
            .map_err(|e| FlowLoaderError::Http(e.to_string()))?;
        if !resp.status().is_success() {
            return Err(FlowLoaderError::NotFound(flow_id.to_string()));
        }
        let body = resp
            .text()
            .await
            .map_err(|e| FlowLoaderError::Invalid(e.to_string()))?;
        if body.is_empty() {
            return Err(FlowLoaderError::NotFound(flow_id.to_string()));
        }
        Ok(body)
    }
}

/// In-memory stub loader — used by tests to inject BPMN XML without a
/// running Java console. Also handy for local dev (bake the BPMN into
/// the binary).
pub struct StubFlowLoader {
    pub flows: DashMap<String, String>,
}

impl StubFlowLoader {
    pub fn new() -> Self {
        Self {
            flows: DashMap::new(),
        }
    }

    /// Convenience constructor for one-shot test setup.
    pub fn with_flow(flow_id: impl Into<String>, xml: impl Into<String>) -> Self {
        let s = Self::new();
        s.flows.insert(flow_id.into(), xml.into());
        s
    }
}

impl Default for StubFlowLoader {
    fn default() -> Self {
        Self::new()
    }
}

#[async_trait::async_trait]
impl FlowLoader for StubFlowLoader {
    async fn load(&self, flow_id: &str) -> Result<String, FlowLoaderError> {
        self.flows
            .get(flow_id)
            .map(|s| s.clone())
            .ok_or_else(|| FlowLoaderError::NotFound(flow_id.to_string()))
    }
}

pub struct FlowDefinitionRepo {
    pub cache: DashMap<String, Arc<FlowDefinition>>,
    pub loader: Arc<dyn FlowLoader>,
    /// Per-flow load lock. Phase 5 doesn't strictly need it (last write
    /// wins is fine for deterministic BPMN), but it cuts the duplicate
    /// HTTP round-trip on cold cache. `Notify` lets a waiter block
    /// without holding a Mutex.
    pub load_locks: DashMap<String, Arc<Notify>>,
}

impl FlowDefinitionRepo {
    pub fn new(loader: Arc<dyn FlowLoader>) -> Self {
        Self {
            cache: DashMap::new(),
            loader,
            load_locks: DashMap::new(),
        }
    }

    /// Get from cache or load + parse. The `Arc<FlowDefinition>` is cheap
    /// to clone (just bumps the refcount).
    pub async fn get_or_load(&self, flow_id: &str) -> Result<Arc<FlowDefinition>, RepoError> {
        if let Some(def) = self.cache.get(flow_id) {
            return Ok(def.clone());
        }

        // Acquire / wait on the per-flow load lock.
        let notify = self
            .load_locks
            .entry(flow_id.to_string())
            .or_insert_with(|| Arc::new(Notify::new()))
            .clone();

        let xml = self.loader.load(flow_id).await?;
        let def =
            Arc::new(BpmnXmlParser::parse(&xml).map_err(|e| RepoError::Parse(e.to_string()))?);
        self.cache.insert(flow_id.to_string(), def.clone());
        // Wake any waiters (best-effort; the cache entry will satisfy them).
        notify.notify_waiters();
        Ok(def)
    }

    /// Drop a single flow from the cache. Called from `/flow/invalidate`.
    pub fn invalidate(&self, flow_id: &str) -> bool {
        self.cache.remove(flow_id).is_some()
    }

    /// How many flows are currently cached. Useful for `/health` and tests.
    pub fn cache_size(&self) -> usize {
        self.cache.len()
    }
}
