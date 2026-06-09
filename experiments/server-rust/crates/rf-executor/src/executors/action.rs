//! ActionNodeExecutor — calls a registered `fn(&mut Vars)` from a
//! mock `ActionRegistry`.
//!
//! Real V5.x had two flavours: `ruleforge:bean` + `ruleforge:method`
//! pointed to a Spring bean in the executor-app; or `ruleforge:method`
//! alone looked up a static `ActionRegistry`. The Rust port takes the
//! static-registry path because we don't have Spring here; production
//! integration (Phase 7+) would either build the registry from
//! registered Rust fns or HTTP-call out to the Java side.

use std::collections::HashMap;
use std::sync::Arc;

use async_trait::async_trait;
use rf_ir::flow_node::FlowNode;
use rf_ir::node_kind::{NodeKind, TaskType};

use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::node_executor::NodeExecutor;
use crate::node_result::NodeResult;
use crate::vars::Vars;

pub type ActionFn = Arc<dyn Fn(&mut Vars) -> Result<(), String> + Send + Sync>;

#[derive(Default, Clone)]
pub struct MockActionRegistry {
    actions: HashMap<String, ActionFn>,
}

impl MockActionRegistry {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn register<F>(mut self, name: impl Into<String>, f: F) -> Self
    where
        F: Fn(&mut Vars) -> Result<(), String> + Send + Sync + 'static,
    {
        self.actions.insert(name.into(), Arc::new(f));
        self
    }

    pub fn get(&self, name: &str) -> Option<&ActionFn> {
        self.actions.get(name)
    }
}

pub struct ActionExecutor {
    pub registry: Arc<MockActionRegistry>,
}

impl ActionExecutor {
    pub fn new(registry: Arc<MockActionRegistry>) -> Self {
        Self { registry }
    }
}

impl Default for ActionExecutor {
    fn default() -> Self {
        Self::new(Arc::new(MockActionRegistry::new()))
    }
}

#[async_trait]
impl NodeExecutor for ActionExecutor {
    async fn execute(
        &self,
        node: &FlowNode,
        ctx: &mut FlowContext,
    ) -> Result<NodeResult, FlowError> {
        let NodeKind::ServiceTask { task_type, attrs } = &node.kind else {
            return Err(FlowError::Unsupported(
                "ActionExecutor on non-serviceTask".to_string(),
            ));
        };
        debug_assert_eq!(*task_type, TaskType::Action);

        let method = attrs.ruleforge("method").ok_or_else(|| {
            FlowError::Action(format!(
                "serviceTask '{}' missing ruleforge:method",
                node.node_id
            ))
        })?;

        let action = self.registry.get(method).ok_or_else(|| {
            FlowError::Action(format!(
                "action '{method}' not registered (node={})",
                node.node_id
            ))
        })?;

        (action)(&mut ctx.vars).map_err(FlowError::Action)?;
        tracing::debug!(method, node_id = %node.node_id, "ActionExecutor ran");
        Ok(NodeResult::Continue)
    }
}
