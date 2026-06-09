//! `AppState` — shared state for the axum router.
//!
//! Held as `axum::extract::State<AppState>` in every route handler.
//! `Arc<...>` everywhere so clone-the-state is cheap.
//!
//! Compare Java: each `@RestController` autowires its own service beans
//! from the Spring context. Rust's `axum::State` is the same idea but
//! with explicit sharing via `Arc` (no DI container magic).

use std::sync::Arc;

use rf_executor::dispatch::ExecutorRegistry;
use uuid::Uuid;

use crate::flow_def_repo::FlowDefinitionRepo;
use crate::inflight::InflightStore;

#[derive(Clone)]
pub struct AppState {
    pub repo: Arc<FlowDefinitionRepo>,
    pub registry: Arc<ExecutorRegistry>,
    pub inflight: Arc<dyn InflightStore>,
    pub worker_id: String,
    /// Base URL of the Java console. `/flow/load` proxy and the
    /// `HttpFlowLoader` use this.
    pub console_url: String,
    /// pg URL (if set, the production main binary builds a
    /// `PgInflightStore`; empty = in-memory). Phase 6.
    pub pg_url: String,
}

impl AppState {
    /// Build an `AppState` with the in-memory `MemInflightStore`.
    /// Used by tests and the dev fallback path.
    pub fn new(
        repo: Arc<FlowDefinitionRepo>,
        registry: Arc<ExecutorRegistry>,
        worker_id: impl Into<String>,
        console_url: impl Into<String>,
        pg_url: impl Into<String>,
    ) -> Self {
        Self {
            repo,
            registry,
            inflight: Arc::new(crate::inflight::MemInflightStore::new()),
            worker_id: worker_id.into(),
            console_url: console_url.into(),
            pg_url: pg_url.into(),
        }
    }

    /// Build an `AppState` with an explicit in-flight store. Used by
    /// `main.rs` to pick the pg-backed impl.
    pub fn with_inflight(
        repo: Arc<FlowDefinitionRepo>,
        registry: Arc<ExecutorRegistry>,
        inflight: Arc<dyn InflightStore>,
        worker_id: impl Into<String>,
        console_url: impl Into<String>,
        pg_url: impl Into<String>,
    ) -> Self {
        Self {
            repo,
            registry,
            inflight,
            worker_id: worker_id.into(),
            console_url: console_url.into(),
            pg_url: pg_url.into(),
        }
    }

    /// Mint a flow_run_id. Mirrors Java's
    /// `UUID.randomUUID().toString().replace("-", "")` (32 hex chars, no dashes).
    pub fn new_flow_run_id() -> String {
        Uuid::new_v4().simple().to_string()
    }
}
