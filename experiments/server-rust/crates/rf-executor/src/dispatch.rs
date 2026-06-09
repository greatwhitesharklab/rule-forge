//! Exhaustive node dispatch — the core architectural win over Java's
//! `NodeExecutorRegistry`.
//!
//! Java: `registry.get(node.getType() + ":" + ext.get("ruleforge:taskType"))`
//! misses at runtime as NPE if a new node kind is added without registering.
//!
//! Rust: this `match` is exhaustive. Adding a `NodeKind` variant without
//! handling it here is a **compile error** at the `match` arm. The
//! `NodeKind` sum type + this dispatcher form a closed system.

use std::sync::Arc;

use rf_ir::flow_node::FlowNode;
use rf_ir::node_kind::NodeKind;

use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::node_executor::NodeExecutor;
use crate::node_result::NodeResult;

/// Holds concrete `Arc<dyn NodeExecutor>` instances keyed by executor
/// role. The dispatcher picks the right one based on the `NodeKind`
/// variant. The registry is `Send + Sync` so it can live in an
/// `axum::State` and be shared across HTTP handler tasks.
#[derive(Clone)]
pub struct ExecutorRegistry {
    pub rule: Arc<dyn NodeExecutor>,
    pub action: Arc<dyn NodeExecutor>,
    pub script: Arc<dyn NodeExecutor>,
    pub gateway: Arc<dyn NodeExecutor>,
    pub user_task: Arc<dyn NodeExecutor>,
}

// Manual Debug — the inner `dyn NodeExecutor` doesn't require Debug, so
// we just print the type name of each instance (useful for "which rule
// engine did the HTTP request get?" diagnostics).
impl std::fmt::Debug for ExecutorRegistry {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("ExecutorRegistry")
            .field("rule", &std::any::type_name_of_val(&*self.rule))
            .field("action", &std::any::type_name_of_val(&*self.action))
            .field("script", &std::any::type_name_of_val(&*self.script))
            .field("gateway", &std::any::type_name_of_val(&*self.gateway))
            .field("user_task", &std::any::type_name_of_val(&*self.user_task))
            .finish()
    }
}

impl ExecutorRegistry {
    /// Construct a registry with a specific `RuleEngine` wired in.
    /// The other four executors use their defaults. Used by tests to
    /// inject `MockRuleEngine` (or by `rf-http` to inject the real
    /// engine impl).
    pub fn with_rule_engine(rule_engine: Arc<dyn crate::rule_engine::RuleEngine>) -> Self {
        Self {
            rule: Arc::new(crate::executors::rule::RuleExecutor::new(rule_engine)),
            action: Arc::new(crate::executors::action::ActionExecutor::default()),
            script: Arc::new(crate::executors::script::ScriptExecutor::default()),
            gateway: Arc::new(crate::executors::gateway::GatewayExecutor),
            user_task: Arc::new(crate::executors::user_task::UserTaskExecutor),
        }
    }
}

impl Default for ExecutorRegistry {
    /// Default registry with `Arc::new(UserTaskExecutor)` etc. for the
    /// executors that have no external state. `RuleExecutor` requires
    /// a `RuleEngine` impl (lives in `rf-rule`), so the default wires
    /// a `NoopRuleEngine` that rejects every call — tests and the
    /// production HTTP binary should override the `rule` field.
    fn default() -> Self {
        Self {
            rule: Arc::new(crate::executors::rule::RuleExecutor::new(Arc::new(
                NoopRuleEngine,
            ))),
            action: Arc::new(crate::executors::action::ActionExecutor::default()),
            script: Arc::new(crate::executors::script::ScriptExecutor::default()),
            gateway: Arc::new(crate::executors::gateway::GatewayExecutor),
            user_task: Arc::new(crate::executors::user_task::UserTaskExecutor),
        }
    }
}

/// Placeholder rule engine used by `ExecutorRegistry::default()`. Returns
/// an error for every call so misconfiguration surfaces immediately
/// rather than silently misfiring.
struct NoopRuleEngine;

#[async_trait::async_trait]
impl crate::rule_engine::RuleEngine for NoopRuleEngine {
    async fn fire_rules(
        &self,
        _ctx: &mut crate::flow_context::FlowContext,
    ) -> Result<crate::rule_engine::RuleResults, crate::rule_engine::RuleEngineError> {
        Err(crate::rule_engine::RuleEngineError::Engine(
            "NoopRuleEngine: no rule engine wired (override ExecutorRegistry.rule)".to_string(),
        ))
    }
}

/// Run a single node against the context. Exhaustive over `NodeKind` —
/// a new variant is a compile error in this match.
pub async fn dispatch(
    node: &FlowNode,
    ctx: &mut FlowContext,
    reg: &ExecutorRegistry,
) -> Result<NodeResult, FlowError> {
    match &node.kind {
        NodeKind::StartEvent | NodeKind::EndEvent => Ok(NodeResult::Continue),
        NodeKind::ServiceTask { task_type, .. } => match task_type {
            rf_ir::node_kind::TaskType::Rule => reg.rule.execute(node, ctx).await,
            rf_ir::node_kind::TaskType::Action => reg.action.execute(node, ctx).await,
            rf_ir::node_kind::TaskType::Package => {
                Err(FlowError::Unsupported("Package service task".to_string()))
            }
            rf_ir::node_kind::TaskType::RulesPackage => Err(FlowError::Unsupported(
                "RulesPackage service task".to_string(),
            )),
        },
        NodeKind::ScriptTask { .. } => reg.script.execute(node, ctx).await,
        NodeKind::UserTask { .. } => reg.user_task.execute(node, ctx).await,
        NodeKind::ExclusiveGateway { .. } | NodeKind::ParallelGateway { .. } => {
            reg.gateway.execute(node, ctx).await
        }
        NodeKind::IntermediateEvent { .. } => Ok(NodeResult::Continue),
        NodeKind::SubProcess { .. } => Err(FlowError::Unsupported("SubProcess".to_string())),
    }
}
