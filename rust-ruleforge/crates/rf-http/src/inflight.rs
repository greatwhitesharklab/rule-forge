//! In-memory storage for in-flight (suspended) flow runs.
//!
//! Phase 5 stores them in a `DashMap` so `/flow/decision` can look up
//! the context, write the user's decision into `current_awaiting_value`,
//! and re-run the traverser to completion.
//!
//! Phase 6 replaces this with a Postgres-backed store (`rust_decision_flow_state`).
//! The HTTP handler signatures don't change — just the storage backend.

use std::sync::Arc;

use dashmap::DashMap;
use rf_executor::flow_context::FlowContext;
use rf_executor::node_result::SuspendInfo;
use rf_ir::flow_definition::FlowDefinition;

#[derive(Debug, Clone)]
pub struct InflightFlow {
    pub def: Arc<FlowDefinition>,
    pub ctx: FlowContext,
    /// `None` after the flow has been resumed to completion; `Some(_)` if
    /// it suspended and is awaiting a decision.
    pub suspend_info: Option<SuspendInfo>,
}

#[derive(Default)]
pub struct InflightStore {
    map: DashMap<String, InflightFlow>,
}

impl InflightStore {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn put(&self, flow_run_id: String, flow: InflightFlow) {
        self.map.insert(flow_run_id, flow);
    }

    pub fn get(&self, flow_run_id: &str) -> Option<InflightFlow> {
        self.map.get(flow_run_id).map(|r| r.clone())
    }

    /// Remove a finished/errored flow. Returns the removed entry if any.
    pub fn remove(&self, flow_run_id: &str) -> Option<InflightFlow> {
        self.map.remove(flow_run_id).map(|(_, v)| v)
    }

    /// Replace a flow's value (used to update ctx after a resume step).
    pub fn update(&self, flow_run_id: &str, flow: InflightFlow) {
        self.map.insert(flow_run_id.to_string(), flow);
    }

    pub fn len(&self) -> usize {
        self.map.len()
    }

    pub fn is_empty(&self) -> bool {
        self.map.is_empty()
    }
}
