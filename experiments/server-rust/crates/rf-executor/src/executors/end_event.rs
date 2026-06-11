//! `EndEventExecutor` — V5.30.
//!
//! BPMN 2.0 `<bpmn:endEvent>` is the exit of every
//! branch. V5.27 treated it as a parameterless
//! no-op (the dispatcher returned
//! `NodeResult::Continue` directly). V5.30 adds a
//! **end-type discriminator** so the same node can
//! model:
//!
//! - **Normal end** (the default) — the flow
//!   finishes successfully. Executor returns
//!   `Continue`, the traverser sees the end node
//!   has no outgoings and produces
//!   `TraverseOutcome::Completed`.
//! - **Error end** — `ruleforge:endType="error"` +
//!   `ruleforge:errorRef` (default `"error"`).
//!   The end marks the flow as failed at a
//!   business level (e.g. "loan rejected"). The
//!   executor writes `ctx.thrown_error = Some(ref)`
//!   for observability and returns
//!   `NodeResult::Fail(FlowError::ErrorEnd(ref))`.
//!   The traverser exits with
//!   `TraverseOutcome::Failed(_, ErrorEnd(ref))`.
//! - **Escalation end** —
//!   `ruleforge:endType="escalation"` +
//!   `ruleforge:escalationRef`. v0 takes the same
//!   `Fail` path as `Error`; V5.31 CompensationScope
//!   will refine the parent-scope-continuation
//!   behaviour.
//! - **Terminate end** —
//!   `ruleforge:endType="terminate"`. v0 is
//!   equivalent to `Error`; V5.31 P1 adds real
//!   token-kill semantics (kill all in-flight
//!   tokens across all flows).
//!
//! ## Why a new executor?
//!
//! The EndEvent is the **last node the traverser
//! steps on** — there is no downstream context. The
//! dispatcher can't infer the end type from `ctx`;
//! it has to read the node's `attrs.endType`. This
//! is structurally identical to the
//! `StartEventExecutor` (which reads
//! `startTrigger`) and the `BoundaryEventExecutor`
//! (which reads `eventType`) — same shape, same
//! pattern.
//!
//! ## Why a new `NodeResult::Fail` variant?
//!
//! `TraverseOutcome::Failed(t, FlowError)` is the
//! existing terminal-failure channel. The
//! `EndEventExecutor` is the *only* production code
//! that *successfully traverses to* a `Failed`
//! outcome (all other `Failed` paths are dispatch
//! errors / loop detection / etc.). The cleanest
//! way to surface "I want to exit with a specific
//! `FlowError`" is a new `NodeResult` variant —
//! the traverser `step()` matches on it, builds
//! `into_failed()`, and returns
//! `Err((failed, err))` (reusing the existing
//! `TraverseOutcome::Failed` plumbing).

use async_trait::async_trait;
use rf_ir::flow_node::FlowNode;
use rf_ir::node_kind::{EndEventKind, NodeKind};

use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::node_executor::NodeExecutor;
use crate::node_result::NodeResult;

pub struct EndEventExecutor;

#[async_trait]
impl NodeExecutor for EndEventExecutor {
    async fn execute(
        &self,
        node: &FlowNode,
        ctx: &mut FlowContext,
    ) -> Result<NodeResult, FlowError> {
        let NodeKind::EndEvent { end_kind: _, attrs } = &node.kind else {
            return Err(FlowError::Unsupported(
                "EndEventExecutor on non-EndEvent".to_string(),
            ));
        };
        let kind = EndEventKind::from_attrs(attrs)
            .map_err(|e| FlowError::Action(e.to_string()))?;
        match kind {
            EndEventKind::None => {
                // Normal end — the traverser's
                // `next_node` resolver will return
                // `None` (an EndEvent has no
                // outgoings) and the flow finishes
                // as `TraverseOutcome::Completed`.
                tracing::debug!(
                    flow_run_id = %ctx.flow_run_id,
                    node_id = %node.node_id,
                    "end event (normal)"
                );
                Ok(NodeResult::Continue)
            }
            EndEventKind::Error { error_ref } => {
                // V5.30 — mark the throw as
                // "consumed" so the /flow/evaluate
                // HTTP layer (and any future
                // observability hook in V5.32) can
                // read it. The end is a *successful*
                // traversal that hit a configured
                // failure terminal; `Fail` carries
                // the `FlowError` for the
                // `TraverseOutcome::Failed` channel.
                tracing::info!(
                    flow_run_id = %ctx.flow_run_id,
                    node_id = %node.node_id,
                    error_ref = %error_ref,
                    "end event (error)"
                );
                ctx.thrown_error = Some(error_ref.clone());
                // V5.30 — `NodeResult::Fail(String)` carries
                // the `Display` form of the
                // `FlowError::ErrorEnd(ref)`. The
                // traverser wraps it into
                // `FlowError::Action(msg)` for the
                // terminal-failure channel (we keep
                // `NodeResult` `Serialize`-friendly
                // by not nesting the rich `FlowError`
                // enum). `ctx.thrown_error` keeps the
                // structured `ref` for observability.
                Ok(NodeResult::Fail(FlowError::ErrorEnd(error_ref).to_string()))
            }
            EndEventKind::Escalation { escalation_ref } => {
                tracing::info!(
                    flow_run_id = %ctx.flow_run_id,
                    node_id = %node.node_id,
                    escalation_ref = %escalation_ref,
                    "end event (escalation)"
                );
                ctx.thrown_error = Some(escalation_ref.clone());
                Ok(NodeResult::Fail(FlowError::EscalationEnd(escalation_ref).to_string()))
            }
            EndEventKind::Terminate => {
                tracing::warn!(
                    flow_run_id = %ctx.flow_run_id,
                    node_id = %node.node_id,
                    "end event (terminate) — v0: same as Error, V5.31 P1 adds real token-kill"
                );
                Ok(NodeResult::Fail(FlowError::Terminated.to_string()))
            }
        }
    }
}