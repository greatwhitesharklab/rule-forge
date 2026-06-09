//! `NodeExecutor` trait — the seam between the dispatcher and a concrete
//! implementation. Phase 4 adds the 5 concrete impls; the dispatcher in
//! `dispatch.rs` holds a `ExecutorRegistry` of `Arc<dyn NodeExecutor>`s.

use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::node_result::NodeResult;
use rf_ir::flow_node::FlowNode;

#[async_trait::async_trait]
pub trait NodeExecutor: Send + Sync {
    async fn execute(
        &self,
        node: &FlowNode,
        ctx: &mut FlowContext,
    ) -> Result<NodeResult, FlowError>;
}
