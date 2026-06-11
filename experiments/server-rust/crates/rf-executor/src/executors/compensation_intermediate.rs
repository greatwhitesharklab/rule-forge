//! `CompensationIntermediateExecutor` — V5.31 P0.
//!
//! BPMN 2.0 `<bpmn:compensateIntermediateThrowEvent>`
//! — sits on the edge of an activity (the
//! `attachedToRef` attribute is parsed into
//! `NodeKind::CompensationIntermediate.attached_to`).
//!
//! ## v0 behaviour: pure no-op
//!
//! V5.31 P0 v0 is deliberately a **no-op**:
//! visiting this node does NOT register
//! handlers, push/pop scopes, or write to
//! vars. The `def.attached_compensations`
//! reverse-lookup is built by the parser
//! from `attachedToRef`, and the throw
//! executor iterates it at throw time.
//!
//! Why not register on visit? BPMN's
//! semantics say "an activity that
//! completed can be compensated" — the
//! spec scopes handlers to *completed*
//! activities, not to *visited*
//! compensation nodes. V5.31 P0 v0 is
//! conservative: it runs **all**
//! registered handlers on throw (not just
//! "completed" ones), which means the
//! intermediate node's own executor
//! doesn't need to do anything.
//!
//! V5.31+ refines with a
//! completed-activities set; at that
//! point this executor may need to
//! interact with the registry, but
//! nothing more for now.

use async_trait::async_trait;
use rf_ir::flow_node::FlowNode;
use rf_ir::node_kind::NodeKind;

use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::node_executor::NodeExecutor;
use crate::node_result::NodeResult;

pub struct CompensationIntermediateExecutor;

#[async_trait]
impl NodeExecutor for CompensationIntermediateExecutor {
    async fn execute(
        &self,
        node: &FlowNode,
        ctx: &mut FlowContext,
    ) -> Result<NodeResult, FlowError> {
        let NodeKind::CompensationIntermediate { attached_to, attrs: _ } = &node.kind else {
            return Err(FlowError::Unsupported(
                "CompensationIntermediateExecutor on non-CompensationIntermediate"
                    .to_string(),
            ));
        };
        // v0: log + pass through. The
        // `attached_to` is captured in
        // `def.attached_compensations`
        // (built by the parser) and the
        // throw executor walks it.
        tracing::debug!(
            flow_run_id = %ctx.flow_run_id,
            node_id = %node.node_id,
            attached_to = ?attached_to,
            "compensate intermediate (v0: no-op)"
        );
        Ok(NodeResult::Continue)
    }
}
