//! `CompensationEndExecutor` — V5.31 P0.
//!
//! BPMN 2.0 `<bpmn:compensateEndEvent>` — the
//! exit of a compensation scope. Pops the
//! topmost `CompensationScope` whose id
//! matches the `ruleforge:scopeId` (or the
//! node's id). Mismatched scope_id or empty
//! stack is `warn + Continue` — we never
//! fail a flow for a stack-shape error in
//! v0; compensation is best-effort.
//!
//! Behaviour:
//! - Reads `scope_id` from the parsed
//!   `NodeKind::CompensationEnd.scope_id`.
//! - Calls `ctx.pop_compensation_scope(id)`
//!   which handles the warn-on-mismatch
//!   internally.
//! - Returns `Continue`.
//!
//! Note: a `CompensationEnd` that doesn't
//! match the stack top is silently passed
//! through. The scope stays on the stack
//! (it'll be popped at the next successful
//! `pop`, or the throw will pop
//! everything).

use async_trait::async_trait;
use rf_ir::flow_node::FlowNode;
use rf_ir::node_kind::NodeKind;

use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::node_executor::NodeExecutor;
use crate::node_result::NodeResult;

pub struct CompensationEndExecutor;

#[async_trait]
impl NodeExecutor for CompensationEndExecutor {
    async fn execute(
        &self,
        node: &FlowNode,
        ctx: &mut FlowContext,
    ) -> Result<NodeResult, FlowError> {
        let NodeKind::CompensationEnd { scope_id, attrs: _ } = &node.kind else {
            return Err(FlowError::Unsupported(
                "CompensationEndExecutor on non-CompensationEnd".to_string(),
            ));
        };
        tracing::info!(
            flow_run_id = %ctx.flow_run_id,
            node_id = %node.node_id,
            scope_id = %scope_id,
            "compensate end (pop scope)"
        );
        let popped = ctx.pop_compensation_scope(scope_id);
        tracing::debug!(
            popped = ?popped.is_some(),
            "compensation end pop result"
        );
        Ok(NodeResult::Continue)
    }
}
