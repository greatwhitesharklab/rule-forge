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

    /// V5.31 P0 — compensation scope stack
    /// (LIFO). Pushed by
    /// `CompensationStartExecutor`, popped
    /// by `CompensationEndExecutor`. When a
    /// `CompensationThrow` (or an
    /// `ErrorEnd` while the stack is
    /// non-empty — via the traverser's
    /// Fail-arm hook) runs, the handlers
    /// in each scope are walked LIFO and
    /// each handler's sub-flow is
    /// traversed (handler node id =
    /// sub-flow start). `#[serde(default)]`
    /// keeps suspend/resume compatibility
    /// for pre-V5.31 state rows (they
    /// deserialize to an empty stack —
    /// no compensation on resume, which
    /// is the right thing because the
    /// throw path that needs compensation
    /// would have been terminal, not
    /// resumable).
    #[serde(default)]
    pub compensation_stack: Vec<CompensationScope>,

    /// V5.31 P0 — the set of
    /// `(activity_id, handler_node_id)`
    /// pairs that have already been run
    /// as compensation. Prevents a
    /// second `CompensationThrow` from
    /// re-running the same handler
    /// (important for nested scopes —
    /// each `throw` only runs handlers
    /// it hasn't run before). `#[serde(default)]`
    /// for suspend/resume backward
    /// compatibility.
    #[serde(default)]
    pub compensated_handlers: std::collections::BTreeSet<(String, String)>,
}

/// V5.31 P0 — a single compensation scope,
/// representing the body between a
/// `CompensationStart` and `CompensationEnd`
/// pair. `id` is the scope's identity
/// (defaults to the `CompensationStart`
/// node_id); `handlers` is the LIFO list of
/// registered activity-id → handler-node-id
/// pairs.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct CompensationScope {
    pub id: String,
    pub handlers: Vec<CompensationHandler>,
}

/// V5.31 P0 — one compensation handler.
/// `activity_id` is the id of the activity
/// that this handler can roll back; the
/// sub-flow starts at `handler_node_id` and
/// traverses to its end.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct CompensationHandler {
    pub activity_id: String,
    pub handler_node_id: String,
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
            compensation_stack: Vec::new(),
            compensated_handlers: std::collections::BTreeSet::new(),
        }
    }

    /// V5.31 P0 — push a new scope onto the
    /// compensation stack. If the stack top
    /// already has the same id (consecutive
    /// `CompensationStart` with the same
    /// `scopeId`), this is a no-op + warn to
    /// match the dispatcher's conservative
    /// behavior (we don't push duplicates
    /// because the LIFO pop at
    /// `CompensationEnd` would otherwise
    /// match the wrong scope).
    pub fn push_compensation_scope(&mut self, id: String) {
        if let Some(top) = self.compensation_stack.last() {
            if top.id == id {
                tracing::warn!(
                    flow_run_id = %self.flow_run_id,
                    scope_id = %id,
                    "duplicate CompensationStart with same scope_id at top of stack; ignoring"
                );
                return;
            }
        }
        self.compensation_stack.push(CompensationScope {
            id,
            handlers: Vec::new(),
        });
    }

    /// V5.31 P0 — pop the topmost scope.
    /// Returns `None` if the stack is empty.
    /// Mismatched scope_id (the top's id
    /// doesn't match the request) is a
    /// `warn + return None` — the flow
    /// keeps going (we never fail a flow
    /// for a stack-shape error in v0;
    /// compensation is best-effort).
    pub fn pop_compensation_scope(&mut self, expected_id: &str) -> Option<CompensationScope> {
        match self.compensation_stack.last() {
            Some(top) if top.id == expected_id => self.compensation_stack.pop(),
            Some(top) => {
                tracing::warn!(
                    flow_run_id = %self.flow_run_id,
                    expected = %expected_id,
                    actual = %top.id,
                    "CompensationEnd scope_id mismatch; leaving stack intact"
                );
                None
            }
            None => {
                tracing::warn!(
                    flow_run_id = %self.flow_run_id,
                    expected = %expected_id,
                    "CompensationEnd with empty compensation stack"
                );
                None
            }
        }
    }

    /// V5.31 P0 — register a handler to the
    /// scope with id `scope_id`. v0 records
    /// the *activity id* (the activity that
    /// "may" be compensated); the actual
    /// handler sub-flow is resolved at
    /// throw time from
    /// `def.attached_compensations`. If the
    /// scope isn't on the stack, warn + skip
    /// (a misordered `CompensationStart` /
    /// `register_compensation_handler`
    /// sequence should never fail the
    /// flow).
    pub fn register_compensation_handler(
        &mut self,
        scope_id: &str,
        activity_id: String,
        handler_node_id: String,
    ) {
        if let Some(scope) = self
            .compensation_stack
            .iter_mut()
            .find(|s| s.id == scope_id)
        {
            scope.handlers.push(CompensationHandler {
                activity_id,
                handler_node_id,
            });
        } else {
            tracing::warn!(
                flow_run_id = %self.flow_run_id,
                scope_id = %scope_id,
                "register_compensation_handler: scope not on stack; skipping"
            );
        }
    }
}
