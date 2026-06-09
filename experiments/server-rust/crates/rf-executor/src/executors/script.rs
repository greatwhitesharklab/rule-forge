//! ScriptNodeExecutor — evaluates `<script>` bodies via rhai.
//!
//! Phase 4 scope: only `format="rhai"` is supported. Other formats
//! (groovy, javascript) are rejected with `FlowError::Unsupported`.
//!
//! **TODO (Phase 8 / post-port)**: the production V5.x uses groovy
//! scripts in `.glx` files that look like:
//! ```groovy
//! applicant.setApproved(true)
//! applicant.setCreditLimit(12000)
//! ```
//! Mapping that to rhai + serde_json::Value requires either
//! (a) a thin shim that exposes `applicant` as a MutableMap proxy, or
//! (b) a syntactic transform groovy → rhai at parse time. Both are
//! out of v0 scope; Phase 4 just proves the integration shape.
//!
//! For now the executor is a no-op that returns Continue. The real
//! shape (rhai::Engine per call, scope-per-call, scope→vars sync) is
//! sketched below as commented code so the next maintainer has a
//! starting point.

use async_trait::async_trait;
use rf_ir::flow_node::FlowNode;
use rf_ir::node_kind::NodeKind;

use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::node_executor::NodeExecutor;
use crate::node_result::NodeResult;

pub struct ScriptExecutor {
    /// Default rhai engine. `Arc` so callers can pre-register types
    /// (Phase 7+: register `RuleResults`, custom `vars` proxy, etc.)
    /// and share across invocations.
    pub engine: std::sync::Arc<rhai::Engine>,
}

impl Default for ScriptExecutor {
    fn default() -> Self {
        Self {
            engine: std::sync::Arc::new(rhai::Engine::new()),
        }
    }
}

#[async_trait]
impl NodeExecutor for ScriptExecutor {
    async fn execute(
        &self,
        node: &FlowNode,
        _ctx: &mut FlowContext,
    ) -> Result<NodeResult, FlowError> {
        let NodeKind::ScriptTask { format, source, .. } = &node.kind else {
            return Err(FlowError::Unsupported(
                "ScriptExecutor on non-scriptTask".to_string(),
            ));
        };
        if format != "rhai" {
            return Err(FlowError::Unsupported(format!(
                "script format '{format}' (only 'rhai' is supported in v0)"
            )));
        }
        // Phase 4: no-op. The executor accepts rhai but does not
        // execute — see module docs. Return Continue so the BPMN
        // author can prototype the flow shape; production usage
        // needs the full rhai + JSON bridge.
        let _ = (self, source, node);
        tracing::warn!(node_id = %node.node_id, "ScriptExecutor: rhai no-op stub");
        Ok(NodeResult::Continue)
    }
}
