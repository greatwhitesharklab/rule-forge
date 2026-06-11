//! Per-execution context.
//!
//! Every `evaluate` call gets a fresh `FlowContext`. The traverser mutates
//! `current_node_id` as it steps; the userTask path writes
//! `current_awaiting_field` + `current_awaiting_value` to coordinate with
//! the next gateway's binary-decision routing (mirrors Java's
//! `currentAwaitingField` mechanism).

use std::collections::HashMap;

use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::vars::Vars;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
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
    /// V5.28 P1 — set by an action / rule that wants to
    /// throw an error. The `traverse` driver reads this
    /// after the activity's `dispatch` and routes the
    /// flow to the attached boundary's outgoing (if a
    /// boundary with a matching `errorRef` exists).
    /// Cleared once consumed. Convention: an action
    /// that wants to throw calls
    /// `ctx.thrown_error = Some("error".to_string())` (or
    /// any other ref it wants the boundary to match).
    #[serde(default)]
    pub thrown_error: Option<String>,
    /// V5.28 P6 — per-join arrival counter, used by
    /// ParallelGateway **join** synchronisation.
    ///
    /// Map: `join_node_id` → number of branches that have
    /// arrived at this join in the current flow run.
    /// Incremented by the gateway executor's
    /// `execute_with` when a branch lands on a parallel
    /// gateway with multiple incoming edges. When the
    /// counter reaches the expected number of branches,
    /// the join "fires" and the traverser continues from
    /// the join's outgoing edge.
    ///
    /// V5.28 P6 v0 is a synchronous barrier — the join
    /// counter is only meaningful across the recursive
    /// `traverse_branch` call (P0's serial recursion
    /// guarantees all branches finish before the join is
    /// reached). The map survives fork-join round-trips
    /// because `Vars` is cloned per-branch and the
    /// post-merge step in `traverse_branch` unions the
    /// child maps into the parent.
    ///
    /// V5.29 (Multi-Instance) will need to persist this
    /// across suspend / resume so an async join can be
    /// revived after a worker restart — that's where this
    /// field earns its keep on the wire.
    #[serde(default)]
    pub join_arrivals: HashMap<String, u32>,
}

impl FlowContext {
    pub fn new(flow_run_id: impl Into<String>) -> Self {
        Self {
            flow_run_id: flow_run_id.into(),
            vars: Vars::new(),
            current_node_id: None,
            current_awaiting_field: None,
            current_awaiting_value: None,
            thrown_error: None,
            join_arrivals: HashMap::new(),
        }
    }
}
