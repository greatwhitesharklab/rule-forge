//! GatewayNodeExecutor — no-op stub.
//!
//! Gateway *routing* (which outgoing edge to follow) lives in
//! [`crate::next_node`], not in the executor. The executor itself just
//! signals "continue" so the traverser can move to the next node. This
//! matches Java's `GatewayNodeExecutor.execute()` which also doesn't
//! pick the next edge — `FlowNodeRunner.nextNode` does.
//!
//! `ParallelGateway` reuses the same executor (join-all is a Phase 8
//! follow-up — currently a no-op split-join that may double-execute
//! downstream nodes). v0 only has a smoke test for the shape.

use async_trait::async_trait;
use rf_ir::flow_node::FlowNode;

use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::node_executor::NodeExecutor;
use crate::node_result::NodeResult;

pub struct GatewayExecutor;

#[async_trait]
impl NodeExecutor for GatewayExecutor {
    async fn execute(
        &self,
        _node: &FlowNode,
        _ctx: &mut FlowContext,
    ) -> Result<NodeResult, FlowError> {
        Ok(NodeResult::Continue)
    }
}
