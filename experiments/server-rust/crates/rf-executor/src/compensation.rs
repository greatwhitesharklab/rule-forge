//! V5.31 P0 — shared compensation handler
//! sub-flow runner.
//!
//! Both [`crate::executors::compensation_throw::CompensationThrowExecutor`]
//! and the traverser's
//! `NodeResult::Fail` arm in
//! [`crate::traverser`] call
//! [`run_handlers`] to walk the compensation
//! stack LIFO and run each handler
//! sub-flow. The shared logic lives here so
//! the two call sites don't drift.
//!
//! ## v0 conservative behaviour
//!
//! - We do **not** track which activities
//!   have "completed". When a throw fires,
//!   we look up the **first** registered
//!   handler in `def.attached_compensations`
//!   for every activity on the stack and
//!   run it. V5.31+ refines this with a
//!   completed-activities set.
//! - Handler sub-flow failure is logged +
//!   captured into the compensation trace
//!   (`Vec<String>`); the next handler
//!   still runs. The outer flow's terminal
//!   state is unchanged from what it would
//!   have been without compensation
//!   (V5.31 P0 is best-effort).
//! - Handler sub-flow suspend propagates
//!   upward — the whole flow suspends.
//! - Handler sub-flows do not run their
//!   own compensation (`ctx.compensation_stack`
//!   is `vec![]` in the sub-flow's `ctx`).

use std::sync::Arc;

use rf_ir::flow_definition::FlowDefinition;

use crate::dispatch::ExecutorRegistry;
use crate::flow_context::FlowContext;
use crate::traverser::TraverseOutcome;

/// V5.31 P0 v0 — the conservative
/// "run all declared handlers"
/// iterator. We don't track which
/// activities have completed (that
/// needs a completed-set in
/// `FlowContext`, deferred to V5.31+).
/// When a throw fires, we walk
/// `def.attached_compensations` in
/// REVERSE `BTreeMap` order
/// (alphabetic descending = LIFO
/// for the "most recently registered"
/// activity heuristic) and invoke
/// **every** registered handler for
/// each activity. We skip any pair
/// already in `ctx.compensated_handlers`
/// (so a second `CompensationThrow`
/// for an outer scope doesn't
/// re-run an inner-scope handler).
/// The BPMN spec says "only
/// compensate completed activities"
/// — V5.31 P0 v0 deliberately
/// ignores that and compensates
/// everything. Same v0 simplification
/// stance we took for
/// `EscalationEnd` / `Terminate` /
/// ErrorEnd in V5.30.
fn v0_all_handlers(
    def: &FlowDefinition,
    ctx: &FlowContext,
) -> Vec<(String, String)> {
    let mut out: Vec<(String, String)> = Vec::new();
    for (activity_id, handler_ids) in def.attached_compensations.iter().rev() {
        for handler_id in handler_ids.iter().rev() {
            let key = (activity_id.clone(), handler_id.clone());
            if ctx.compensated_handlers.contains(&key) {
                continue;
            }
            out.push(key);
        }
    }
    out
}

/// Result of running compensation handlers.
/// `Failures` is the per-handler failure
/// traces; the outer flow uses this to
/// append a `"compensation: <details>"`
/// suffix to its own `FlowError::Action`
/// message (when applicable).
#[derive(Debug, Default, Clone)]
pub struct CompensationTrace {
    /// `"<handler_node_id>: <flow_error>"` for
    /// each handler that failed.
    pub failures: Vec<String>,
}

/// Run the compensation handlers for the
/// **innermost** scope on the stack. Walks
/// the scope's handlers in LIFO registration
/// order and looks up the first handler
/// node id in `def.attached_compensations`
/// for each one, recursively traversing
/// the sub-flow.
///
/// V5.31 P0 v0 semantics: a `CompensationThrow`
/// triggers only the **innermost** scope's
/// handlers (BPMN's "throw current scope"
/// default). To compensate multiple nested
/// scopes, place a `CompensationThrow`
/// after each `CompensationEnd` (each
/// throw pops one scope and runs ITS
/// handlers). The next-outer throw
/// handles the outer scope. This matches
/// BPMN's per-scope compensation
/// contract — V5.31+ may add a "throw
/// all" mode.
///
/// Returns `Ok(trace)` if the scope's
/// handlers completed (whether or not
/// each individual handler failed
/// internally — failures are captured
/// in `trace`). Returns `Err((Suspended(...),
/// info))` if a handler sub-flow suspended.
pub fn run_handlers(
    def: &Arc<FlowDefinition>,
    ctx: &mut FlowContext,
    reg: &Arc<ExecutorRegistry>,
) -> Result<CompensationTrace, (TraverseOutcome, crate::node_result::SuspendInfo)> {
    let mut trace = CompensationTrace::default();

    // V5.31 P0 v0 — only the innermost
    // scope's handlers run on a single
    // throw. Pop the top (the throw
    // targets the current scope per BPMN
    // "compensate current scope" default);
    // outer scopes are thrown by their own
    // (later) `CompensationThrow` nodes.
    let Some(scope) = ctx.compensation_stack.pop() else {
        return Ok(trace);
    };
    tracing::debug!(
        scope_id = %scope.id,
        "compensation throw popped innermost scope; running declared handlers"
    );

    // V5.31 P0 v0 conservative: run
    // ALL declared handlers (one per
    // activity that has an entry in
    // `def.attached_compensations`),
    // not just the ones whose activity
    // has "completed" (we don't track
    // that in v0). See
    // [`v0_all_handlers`].
    let handlers = v0_all_handlers(def, ctx);
    tracing::debug!(
        scope_id = %scope.id,
        n = handlers.len(),
        "compensation handler candidates (v0: run all, LIFO activity order, dedup via compensated_handlers)"
    );

    for (activity_id, handler_node_id) in handlers {
        let start = def
            .nodes
            .get(&handler_node_id)
            .and_then(|n| n.outgoing_ids.first().cloned())
            .and_then(|first_edge_id| {
                def.edges
                    .iter()
                    .find(|e| e.id == first_edge_id)
                    .map(|e| e.target.clone())
            });
        let Some(start) = start else {
            tracing::warn!(
                handler_node_id = %handler_node_id,
                "compensation handler node has no outgoing; skipping"
            );
            continue;
        };

        let mut sub_ctx = FlowContext::new(ctx.flow_run_id.clone());
        sub_ctx.vars = ctx.vars.clone();

        let mut sub_reg = (**reg).clone();
        sub_reg.def = Some(Arc::clone(def));
        let sub_reg = Arc::new(sub_reg);

        let outcome = crate::traverser::traverse_branch(
            Arc::clone(def),
            start,
            sub_ctx,
            sub_reg,
            std::collections::HashSet::new(),
        );
        // Record this (activity, handler) as compensated BEFORE
        // we process the outcome — even on sub-flow failure
        // we don't re-run.
        ctx.compensated_handlers
            .insert((activity_id.clone(), handler_node_id.clone()));
        match outcome {
            TraverseOutcome::Completed(c) => {
                for (k, v) in c.ctx.vars.as_object() {
                    ctx.vars.assign(k.clone(), v.clone());
                }
            }
            TraverseOutcome::Failed(_, err) => {
                tracing::warn!(
                    activity_id = %activity_id,
                    handler_node_id = %handler_node_id,
                    err = %err,
                    "compensation handler sub-flow failed; continuing with next handler"
                );
                trace
                    .failures
                    .push(format!("{handler_node_id}: {err}"));
            }
            TraverseOutcome::Suspended(s, info) => {
                // V5.31 P0 — the handler
                // sub-flow suspended. Union
                // whatever the sub-flow
                // managed to write into
                // outer ctx before
                // propagating the Suspend
                // (the handler may have
                // set some vars before
                // hitting the userTask /
                // async node). The
                // `suspended` traverser is
                // discarded — the outer
                // traverser takes over
                // when the user completes
                // the handler (re-driving
                // from the throw node, but
                // the `(activity,
                // handler)` pair is
                // already in
                // `compensated_handlers`,
                // so we skip it on the
                // second pass).
                for (k, v) in s.ctx.vars.as_object() {
                    ctx.vars.assign(k.clone(), v.clone());
                }
                return Err((
                    TraverseOutcome::Suspended(s, info.clone()),
                    info,
                ));
            }
        }
    }
    Ok(trace)
}

/// Format a [`CompensationTrace`] for
/// appending to a `FlowError::Action`
/// message. Returns `""` if the trace has
/// no failures (so the caller can `+`
/// unconditionally).
pub fn trace_suffix(trace: &CompensationTrace) -> String {
    if trace.failures.is_empty() {
        String::new()
    } else {
        format!("; compensation: [{}]", trace.failures.join(", "))
    }
}
