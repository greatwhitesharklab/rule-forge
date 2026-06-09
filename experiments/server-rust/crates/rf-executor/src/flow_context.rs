//! Per-execution context.
//!
//! Every `evaluate` call gets a fresh `FlowContext`. The traverser mutates
//! `current_node_id` as it steps; the userTask path writes
//! `current_awaiting_field` + `current_awaiting_value` to coordinate with
//! the next gateway's binary-decision routing (mirrors Java's
//! `currentAwaitingField` mechanism).

use serde_json::Value;

use crate::vars::Vars;

#[derive(Debug, Clone)]
pub struct FlowContext {
    /// UUID for this run, links to `rust_decision_flow_state.flow_run_id`.
    pub flow_run_id: String,
    /// Process variables. Plain JSON values — `Value::String`,
    /// `Value::Number`, `Value::Object`, etc. No `Object`/POJO escape hatch
    /// like Java's `outputModel` — the Rust executor stays inside the JSON
    /// model end-to-end.
    pub vars: Vars,
    /// Updated by the traverser on every step. Useful for the persistence
    /// layer to write `current_node_id` to the state row.
    pub current_node_id: Option<String>,
    /// Field name whose value determines the next gateway's branch — set
    /// by `UserTaskNodeExecutor` (Phase 4) before suspending, read by
    /// [`next_node`](crate::next_node) on the very next step.
    pub current_awaiting_field: Option<String>,
    /// Value of `current_awaiting_field` in the current vars. Cleared once
    /// consumed by the gateway.
    pub current_awaiting_value: Option<Value>,
}

impl FlowContext {
    pub fn new(flow_run_id: impl Into<String>) -> Self {
        Self {
            flow_run_id: flow_run_id.into(),
            vars: Vars::new(),
            current_node_id: None,
            current_awaiting_field: None,
            current_awaiting_value: None,
        }
    }
}
