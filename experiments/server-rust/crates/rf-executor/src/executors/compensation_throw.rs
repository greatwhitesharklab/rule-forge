//! `CompensationThrowExecutor` — V5.31 P0.
//!
//! BPMN 2.0 `<bpmn:compensateThrowEvent>` —
//! triggers the compensation rollback. Runs
//! the scope's registered handlers LIFO via
//! the shared
//! [`crate::compensation::run_handlers`]
//! helper (the traverser's `Fail` arm uses
//! the same helper for the SAGA-pattern
//! automatic rollback).
//!
//! ## v0 scope handling
//!
//! - Empty stack →
//!   `Err(FlowError::CompensationNoScope)`.
//!   This is the "throw without a matching
//!   start" misconfiguration.
//! - The throw itself is a `Continue` to
//!   the traverser when the sub-flows
//!   complete normally — the outer flow
//!   continues to the next node (the
//!   post-throw outgoing edge).
//! - The throw returns
//!   `NodeResult::Suspend(info)` when a
//!   sub-flow suspends (e.g. a handler
//!   userTask) — V5.31 P0 propagates the
//!   Suspend cleanly. The traverser's
//!   `step()` converts that into
//!   `TraverseOutcome::Suspended`, so the
//!   outer flow's HTTP response is
//!   `PENDING` (not `Failed`); once the
//!   user completes the handler, the
//!   flow resumes from the throw with
//!   `compensated_handlers` already
//!   containing this pair (so a second
//!   resume doesn't re-run the handler).
//!   Sub-flow vars are union-merged into
//!   the outer ctx before suspend so the
//!   outer flow sees whatever the
//!   handler managed to write up to the
//!   suspend point.

use std::sync::Arc;

use async_trait::async_trait;
use rf_ir::flow_node::FlowNode;
use rf_ir::node_kind::NodeKind;

use crate::compensation;
use crate::dispatch::ExecutorRegistry;
use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::node_executor::NodeExecutor;
use crate::node_result::NodeResult;
use crate::traverser::TraverseOutcome;

pub struct CompensationThrowExecutor;

#[async_trait]
impl NodeExecutor for CompensationThrowExecutor {
    async fn execute(
        &self,
        _node: &FlowNode,
        _ctx: &mut FlowContext,
    ) -> Result<NodeResult, FlowError> {
        // `execute_with` is the real
        // entry — it has access to
        // `reg` for sub-flow recursion.
        // The dispatcher must use
        // `execute_with` for
        // `CompensationThrow`.
        Err(FlowError::Unsupported(
            "CompensationThrowExecutor requires execute_with (use dispatcher)"
                .to_string(),
        ))
    }

    async fn execute_with(
        &self,
        node: &FlowNode,
        ctx: &mut FlowContext,
        reg: &ExecutorRegistry,
    ) -> Result<NodeResult, FlowError> {
        let NodeKind::CompensationThrow { scope_ref, attrs: _ } = &node.kind else {
            return Err(FlowError::Unsupported(
                "CompensationThrowExecutor on non-CompensationThrow".to_string(),
            ));
        };
        let Some(def) = reg.def.as_ref() else {
            return Err(FlowError::Unsupported(
                "CompensationThrowExecutor: registry missing def".to_string(),
            ));
        };
        tracing::info!(
            flow_run_id = %ctx.flow_run_id,
            node_id = %node.node_id,
            scope_ref = %scope_ref,
            "compensate throw — running handlers"
        );

        if ctx.compensation_stack.is_empty() {
            return Err(FlowError::CompensationNoScope);
        }

        let reg_arc = Arc::new(reg.clone());
        match compensation::run_handlers(def, ctx, &reg_arc) {
            Ok(trace) => {
                if !trace.failures.is_empty() {
                    tracing::warn!(
                        flow_run_id = %ctx.flow_run_id,
                        failures = ?trace.failures,
                        "compensation handlers had failures (best-effort)"
                    );
                }
                Ok(NodeResult::Continue)
            }
            Err((outcome, info)) => match outcome {
                TraverseOutcome::Suspended(s, _) => {
                    // V5.31 P0 — handler
                    // sub-flow suspended
                    // (e.g. a userTask
                    // inside the handler).
                    // Propagate the
                    // Suspend upward so the
                    // outer flow's HTTP
                    // response is `PENDING`
                    // (not `Failed`). The
                    // outer traverser
                    // converts
                    // `NodeResult::Suspend`
                    // into
                    // `TraverseOutcome::Suspended`
                    // automatically — the
                    // standard userTask
                    // path. On resume, the
                    // outer flow steps
                    // back into this
                    // throw; we don't
                    // re-run the handler
                    // because it's already
                    // in
                    // `ctx.compensated_handlers`
                    // (the entry was
                    // added before the
                    // sub-flow
                    // `traverse_branch`
                    // call in
                    // `run_handlers`).
                    //
                    // Surface the
                    // sub-flow's
                    // `current_node_id` on
                    // the outer ctx so the
                    // caller's persistence
                    // layer writes the
                    // *deepest* suspended
                    // node (the userTask),
                    // not the throw —
                    // matches Java's
                    // behaviour for nested
                    // suspends and is what
                    // the
                    // `compensation_handler_with_user_task_suspends`
                    // test asserts.
                    if let Some(inner) = s.ctx.current_node_id.clone() {
                        ctx.current_node_id = Some(inner);
                    }
                    Ok(NodeResult::Suspend(info))
                }
                _ => Ok(NodeResult::Continue),
            },
        }
    }
}
