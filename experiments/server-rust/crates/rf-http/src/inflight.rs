//! Storage for in-flight (suspended) flow runs.
//!
//! Phase 5 stored them in a `DashMap` (`MemInflightStore`); Phase 6
//! adds a Postgres-backed impl (`PgInflightStore`) that writes the
//! run state to `rust_decision_flow_state` and reads it back on
//! resume. The HTTP route handlers see a single trait
//! [`InflightStore`] — the choice of backend is made in `main.rs`
//! based on whether a `pg_url` is configured.
//!
//! ## Why a trait, not a free fn
//!
//! Tests need a fast, side-effect-free store. Production needs a
//! cross-process store. Splitting them lets us:
//! - keep the BDD tests fast (no DB fixture needed)
//! - have the recovery loop hit a real DB (Phase 6 acceptance)
//! - write a `PgInflightStore` adapter that reuses `PgStateStore`
//!   for the persistence calls

use std::sync::Arc;

use async_trait::async_trait;
use dashmap::DashMap;
use rf_executor::flow_context::FlowContext;
use rf_executor::node_result::{SuspendInfo, WaitType};
use rf_ir::flow_definition::FlowDefinition;
use rf_state::persistence::PgStateStore;
use rf_state::serialization::SuspendPayload;
use rf_state::state_row::FlowStatus;
use serde_json::{Map, Value};
use tracing::{debug, warn};

use crate::flow_def_repo::FlowDefinitionRepo;

/// One in-flight (suspended) flow run. The `def` is held by `Arc` so
/// cloning an `InflightFlow` is cheap.
#[derive(Debug, Clone)]
pub struct InflightFlow {
    pub def: Arc<FlowDefinition>,
    pub ctx: FlowContext,
    /// `None` after the flow has been resumed to completion; `Some(_)`
    /// if it suspended and is awaiting a decision.
    pub suspend_info: Option<SuspendInfo>,
}

/// Backend-agnostic in-flight store. The HTTP layer talks to this;
/// `MemInflightStore` (tests) and `PgInflightStore` (production) are
/// the two impls.
#[async_trait]
pub trait InflightStore: Send + Sync + 'static {
    /// Stash a suspended flow. Idempotent — repeated calls with the
    /// same `flow_run_id` overwrite the existing row.
    async fn put(&self, flow_run_id: String, flow: InflightFlow);

    /// Look up a suspended flow by `flow_run_id`. Returns `None` if
    /// the run isn't suspended (either completed or never existed).
    async fn get(&self, flow_run_id: &str) -> Option<InflightFlow>;

    /// Replace an existing entry (multi-suspend chains).
    async fn update(&self, flow_run_id: &str, flow: InflightFlow);

    /// Drop a finished / errored flow. Returns the removed entry if
    /// any.
    async fn remove(&self, flow_run_id: &str) -> Option<InflightFlow>;

    /// Number of suspended runs. O(1) for the in-memory impl; one
    /// `COUNT(*)` for the pg impl.
    async fn len(&self) -> usize;

    async fn is_empty(&self) -> bool {
        self.len().await == 0
    }
}

// =====================================================================
// In-memory impl (Phase 5)
// =====================================================================

/// Process-local store backed by a `DashMap`. Used by the BDD tests
/// (no DB fixture required) and as the fallback when `pg_url` is
/// empty.
#[derive(Default)]
pub struct MemInflightStore {
    map: DashMap<String, InflightFlow>,
}

impl MemInflightStore {
    pub fn new() -> Self {
        Self::default()
    }
}

#[async_trait]
impl InflightStore for MemInflightStore {
    async fn put(&self, flow_run_id: String, flow: InflightFlow) {
        self.map.insert(flow_run_id, flow);
    }

    async fn get(&self, flow_run_id: &str) -> Option<InflightFlow> {
        self.map.get(flow_run_id).map(|r| r.clone())
    }

    async fn update(&self, flow_run_id: &str, flow: InflightFlow) {
        self.map.insert(flow_run_id.to_string(), flow);
    }

    async fn remove(&self, flow_run_id: &str) -> Option<InflightFlow> {
        self.map.remove(flow_run_id).map(|(_, v)| v)
    }

    async fn len(&self) -> usize {
        self.map.len()
    }
}

// =====================================================================
// Postgres impl (Phase 6)
// =====================================================================

/// Postgres-backed inflight store. Writes the run state to
/// `rust_decision_flow_state` on `put` / `update`; reads the row back
/// on `get`. The `def` (the `Arc<FlowDefinition>`) is reconstructed
/// from the in-memory `FlowDefinitionRepo` cache — the BPMN itself
/// is *not* stored in pg, only the `flow_id` / `flow_xml_version` that
/// identifies it. After a worker restart the repo is empty, so the
/// first `get` will trigger a re-fetch from the console (Phase 7
/// path); in steady state the repo cache is hot and `get` is a pg
/// round-trip plus an in-memory hashmap lookup.
pub struct PgInflightStore {
    state: Arc<PgStateStore>,
    repo: Arc<FlowDefinitionRepo>,
}

impl PgInflightStore {
    pub fn new(state: Arc<PgStateStore>, repo: Arc<FlowDefinitionRepo>) -> Self {
        Self { state, repo }
    }

    /// Convert a `serde_json::Value` (jsonb on the wire) into a
    /// `FlowContext`. Re-hydrates the executor's `Vars` (BTreeMap) and
    /// the `current_awaiting_field` / `current_awaiting_value` pair
    /// that the userTask path uses for binary-decision routing.
    fn hydrate_ctx(
        flow_run_id: &str,
        row_vars: Option<&Value>,
        current_awaiting_field: Option<&str>,
        current_awaiting_value: Option<&Value>,
    ) -> FlowContext {
        let mut ctx = FlowContext::new(flow_run_id);
        if let Some(v) = row_vars.and_then(Value::as_object) {
            for (k, vv) in v {
                ctx.vars.insert(k.clone(), vv.clone());
            }
        }
        // The userTask executor writes `current_awaiting_field` /
        // `current_awaiting_value` *outside* of `vars` (it lives on
        // the ctx struct, not in the BTreeMap). For pg round-trip we
        // synthesise them from the row's two columns.
        ctx.current_awaiting_field = current_awaiting_field.map(str::to_string);
        ctx.current_awaiting_value = current_awaiting_value.cloned();
        ctx
    }
}

#[async_trait]
impl InflightStore for PgInflightStore {
    async fn put(&self, flow_run_id: String, flow: InflightFlow) {
        let flow_id = flow.def.process_id.clone();
        let vars = btreemap_to_value(flow.ctx.vars.clone());
        let (wait_type, wait_ref, payload) = match &flow.suspend_info {
            Some(info) => (info.wait_type, info.wait_ref.clone(), info.payload.clone()),
            None => {
                warn!(
                    flow_run_id = %flow_run_id,
                    "PgInflightStore::put called with no suspend_info; treating as ASYNC_TASK"
                );
                (WaitType::AsyncTask, String::new(), Value::Null)
            }
        };
        // Ensure a row exists (PENDING) and then immediately update to
        // WAITING_CALLBACK. The userTask path's row_vars needs the
        // intermediate ctx state, so we mirror the in-memory store:
        // write a row, then suspend it.
        if let Err(e) = self
            .state
            .insert_start(
                &flow_id,
                &flow_run_id,
                Some(flow.ctx.current_node_id.as_deref().unwrap_or("")),
                Some("UserTask"),
                Some(flow.def.source_xml_hash.as_str()),
            )
            .await
        {
            warn!(?e, "PgInflightStore::put insert_start failed");
            return;
        }
        let payload_json = SuspendPayload {
            wait_type: wait_type.into(),
            wait_ref: wait_ref.clone(),
            payload: payload.clone(),
            next_retry_at: flow.suspend_info.as_ref().and_then(|i| i.next_retry_at),
        }
        .to_value();
        if let Err(e) = self
            .state
            .mark_suspended(
                &flow_run_id,
                Some(flow.ctx.current_node_id.as_deref().unwrap_or("")),
                Some("UserTask"),
                wait_type.into(),
                &wait_ref,
                flow.suspend_info.as_ref().and_then(|i| i.next_retry_at),
                payload_json,
            )
            .await
        {
            warn!(?e, "PgInflightStore::put mark_suspended failed");
            return;
        }
        debug!(%flow_run_id, %flow_id, "PgInflightStore: put");
        // vars is unused on the put path (the suspend row doesn't
        // store row_vars — only output_model). It will be filled in
        // when the flow completes.
        let _ = vars;
    }

    async fn get(&self, flow_run_id: &str) -> Option<InflightFlow> {
        let row = self.state.select_by_flow_run_id(flow_run_id).await.ok()??;
        if row.status != FlowStatus::WaitingCallback {
            // Not in a suspended state — treat as absent.
            return None;
        }
        // Re-hydrate the def from the repo. After a restart the
        // cache is empty and this triggers an HTTP fetch via
        // `HttpFlowLoader`. If the loader can't find the flow,
        // we surface `None` and the route returns 404.
        let def = match self.repo.get_or_load(&row.flow_id).await {
            Ok(d) => d,
            Err(e) => {
                warn!(?e, flow_id = %row.flow_id, "PgInflightStore::get: def lookup failed");
                return None;
            }
        };
        let suspend = match row.output_model {
            Some(v) => match SuspendPayload::from_value(v.clone()) {
                Ok(p) => Some(SuspendInfo {
                    wait_type: p.wait_type.into(),
                    wait_ref: p.wait_ref,
                    next_retry_at: p.next_retry_at,
                    payload: p.payload,
                }),
                Err(e) => {
                    warn!(
                        ?e,
                        flow_run_id, "PgInflightStore::get: output_model decode failed"
                    );
                    None
                }
            },
            None => None,
        };
        // The userTask executor sets `ctx.current_awaiting_field` to
        // the decisionField name (e.g. "approve"). That field is
        // not in the row's `current_*` columns — but the
        // `SuspendInfo.wait_ref` is the same string (the
        // userTask executor uses `decision_field` for both). Re-hydrate
        // current_awaiting_field from the suspend so the
        // /flow/decision handler can write the decision into
        // `vars[decision_field]` on resume.
        let awaiting_field = suspend.as_ref().map(|s| s.wait_ref.clone());
        let ctx = Self::hydrate_ctx(
            flow_run_id,
            row.row_vars.as_ref(),
            awaiting_field.as_deref(),
            None,
        );
        Some(InflightFlow {
            def,
            ctx,
            suspend_info: suspend,
        })
    }

    async fn update(&self, flow_run_id: &str, flow: InflightFlow) {
        self.put(flow_run_id.to_string(), flow).await;
    }

    async fn remove(&self, flow_run_id: &str) -> Option<InflightFlow> {
        // Mark as COMPLETED (the in-memory store used to hard-delete
        // on remove; we now use mark_completed so the audit trail
        // survives). For tests, the route handler checks
        // `inflight.len() == 0` — that counts only WAITING_CALLBACK
        // rows, so a COMPLETED row is correctly invisible.
        let prev = self.get(flow_run_id).await;
        if prev.is_some() {
            if let Ok(Some(row)) = self.state.select_by_flow_run_id(flow_run_id).await {
                let _ = self
                    .state
                    .mark_completed(
                        flow_run_id,
                        row.current_node_id.as_deref(),
                        row.row_vars.unwrap_or(Value::Null),
                        row.total_execution_ms,
                    )
                    .await;
            }
        }
        prev
    }

    async fn len(&self) -> usize {
        self.state
            .count_by_status(FlowStatus::WaitingCallback)
            .await
            .map(|n| n as usize)
            .unwrap_or(0)
    }
}

/// `BTreeMap<String, Value>` → `serde_json::Value::Object`. The
/// executor's `Vars` is a BTreeMap; pg's jsonb column is a `Value`.
/// We don't sort on the wire (BTreeMap iter is already sorted) and
/// the re-hydration on `get` rebuilds the BTreeMap from the keys.
fn btreemap_to_value(vars: rf_executor::vars::Vars) -> Value {
    let map: Map<String, Value> = vars.into_inner().into_iter().collect();
    Value::Object(map)
}
