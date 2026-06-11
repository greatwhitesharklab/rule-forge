//! `CompensationStartExecutor` — V5.31 P0.
//!
//! BPMN 2.0 `<bpmn:compensateStartEvent>` — the
//! entry of a compensation scope. Pair with
//! [`NodeKind::CompensationEnd`] (executed by
//! [`super::compensation_end::CompensationEndExecutor`])
//! to close the scope. Anything that throws
//! an `ErrorEnd` / `EscalationEnd` while a scope
//! is on the stack triggers LIFO compensation
//! rollback — see [`crate::compensation`].
//!
//! Behaviour:
//! - Reads `scope_id` from the parsed
//!   `NodeKind::CompensationStart.scope_id`
//!   (which the parser populated from
//!   `ruleforge:scopeId` or the node's id).
//! - Calls `ctx.push_compensation_scope(id)`
//!   which is idempotent against a consecutive
//!   push with the same id (warn + skip).
//! - Returns `Continue`.
//!
//! v0 conservative: does NOT register
//! handlers here. Handler registration
//! happens at `CompensationThrow` time by
//! iterating `def.attached_compensations`.

use async_trait::async_trait;
use rf_ir::flow_node::FlowNode;
use rf_ir::node_kind::NodeKind;

use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::node_executor::NodeExecutor;
use crate::node_result::NodeResult;

pub struct CompensationStartExecutor;

#[async_trait]
impl NodeExecutor for CompensationStartExecutor {
    async fn execute(
        &self,
        node: &FlowNode,
        ctx: &mut FlowContext,
    ) -> Result<NodeResult, FlowError> {
        let NodeKind::CompensationStart { scope_id, attrs: _ } = &node.kind else {
            return Err(FlowError::Unsupported(
                "CompensationStartExecutor on non-CompensationStart".to_string(),
            ));
        };
        tracing::info!(
            flow_run_id = %ctx.flow_run_id,
            node_id = %node.node_id,
            scope_id = %scope_id,
            "compensate start (push scope)"
        );
        ctx.push_compensation_scope(scope_id.clone());
        Ok(NodeResult::Continue)
    }
}
