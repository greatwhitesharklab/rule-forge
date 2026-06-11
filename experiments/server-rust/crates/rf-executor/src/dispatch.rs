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

use rf_ir::flow_definition::FlowDefinition;
use rf_ir::flow_node::FlowNode;
use rf_ir::node_kind::NodeKind;

use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::flow_resolver::FlowResolver;
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
    pub intermediate_event: Arc<dyn NodeExecutor>,
    pub boundary_event: Arc<dyn NodeExecutor>,
    pub sub_process: Arc<dyn NodeExecutor>,
    /// V5.28 P7 — `StartEvent` is no longer a
    /// parameterless no-op. The executor reads the
    /// `startTrigger` attr to decide whether to
    /// `Continue` (manual) or `Suspend` (message /
    /// timer).
    pub start_event: Arc<dyn NodeExecutor>,
    /// V5.29 — multi-instance wrapper. The
    /// dispatcher consults this **before** the
    /// per-kind arms for `ServiceTask` /
    /// `ScriptTask` / `UserTask`: any task node
    /// with `ruleforge:multiInstance = "true"` is
    /// routed here. The wrapper itself reads
    /// `reg.rule` / `reg.action` / `reg.script` /
    /// `reg.user_task` to invoke the inner
    /// executor.
    pub multi_instance: Arc<dyn NodeExecutor>,
    /// V5.30 — `EndEvent` is no longer a
    /// parameterless no-op. The executor reads the
    /// `endType` attr to decide whether to
    /// `Continue` (normal end) or
    /// `Fail(FlowError::...)` (error / escalation /
    /// terminate end). Mirrors the
    /// `StartEventExecutor` (V5.28 P7) shape.
    pub end_event: Arc<dyn NodeExecutor>,
    /// V5.31 P0 — compensation event
    /// executors (4). The dispatcher routes
    /// `CompensationStart` / `CompensationEnd`
    /// / `CompensationIntermediate` to
    /// no-op `Continue`s and
    /// `CompensationThrow` to the
    /// `execute_with` arm (which
    /// recursively traverses handler
    /// sub-flows via
    /// `crate::compensation::run_handlers`).
    pub compensation_start: Arc<dyn NodeExecutor>,
    pub compensation_end: Arc<dyn NodeExecutor>,
    pub compensation_throw: Arc<dyn NodeExecutor>,
    pub compensation_intermediate: Arc<dyn NodeExecutor>,
    /// `Option` so the default registry (used by unit tests that
    /// don't care about sub-flows) doesn't have to wire a
    /// resolver. `rf-http` sets this to an adapter that wraps
    /// `FlowDefinitionRepo::get_or_load`.
    pub flow_resolver: Option<Arc<dyn FlowResolver>>,
    /// V5.28 — the `FlowDefinition` this registry is running.
    /// `GatewayExecutor::execute` for a `ParallelGateway` reads
    /// this to resolve outgoing edge targets (the `FlowNode`
    /// itself only carries edge `id`s, not target `id`s). The
    /// `traverse()` driver auto-wires this from its
    /// `def` argument if the caller didn't set it explicitly,
    /// so existing tests that build a registry with
    /// `with_rule_engine(...)` and call `traverse(def, ctx, reg)`
    /// still work without changes. Set this to `None` for
    /// unit tests that exercise executors in isolation
    /// without a flow definition (the gateway executor
    /// errors with a clear message if it's `None` and a
    /// parallel gateway is dispatched).
    pub def: Option<Arc<FlowDefinition>>,
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
            .field(
                "intermediate_event",
                &std::any::type_name_of_val(&*self.intermediate_event),
            )
            .field(
                "boundary_event",
                &std::any::type_name_of_val(&*self.boundary_event),
            )
            .field(
                "sub_process",
                &std::any::type_name_of_val(&*self.sub_process),
            )
            .field(
                "start_event",
                &std::any::type_name_of_val(&*self.start_event),
            )
            .field(
                "multi_instance",
                &std::any::type_name_of_val(&*self.multi_instance),
            )
            .field(
                "compensation_start",
                &std::any::type_name_of_val(&*self.compensation_start),
            )
            .field(
                "compensation_end",
                &std::any::type_name_of_val(&*self.compensation_end),
            )
            .field(
                "compensation_throw",
                &std::any::type_name_of_val(&*self.compensation_throw),
            )
            .field(
                "compensation_intermediate",
                &std::any::type_name_of_val(&*self.compensation_intermediate),
            )
            .field(
                "flow_resolver",
                &self
                    .flow_resolver
                    .as_ref()
                    .map(|r| std::any::type_name_of_val(&**r))
                    .unwrap_or("None"),
            )
            .field(
                "def",
                &self
                    .def
                    .as_ref()
                    .map(|d| d.process_id.clone())
                    .unwrap_or_else(|| "None".to_string()),
            )
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
            intermediate_event: Arc::new(
                crate::executors::intermediate_event::IntermediateEventExecutor,
            ),
            boundary_event: Arc::new(crate::executors::boundary_event::BoundaryEventExecutor),
            sub_process: Arc::new(crate::executors::sub_process::SubProcessExecutor),
            start_event: Arc::new(crate::executors::start_event::StartEventExecutor),
            multi_instance: Arc::new(crate::executors::multi_instance::MultiInstanceExecutor::new()),
            end_event: Arc::new(crate::executors::end_event::EndEventExecutor),
            compensation_start: Arc::new(
                crate::executors::compensation_start::CompensationStartExecutor,
            ),
            compensation_end: Arc::new(
                crate::executors::compensation_end::CompensationEndExecutor,
            ),
            compensation_throw: Arc::new(
                crate::executors::compensation_throw::CompensationThrowExecutor,
            ),
            compensation_intermediate: Arc::new(
                crate::executors::compensation_intermediate::CompensationIntermediateExecutor,
            ),
            flow_resolver: None,
            def: None,
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
            intermediate_event: Arc::new(
                crate::executors::intermediate_event::IntermediateEventExecutor,
            ),
            boundary_event: Arc::new(crate::executors::boundary_event::BoundaryEventExecutor),
            sub_process: Arc::new(crate::executors::sub_process::SubProcessExecutor),
            start_event: Arc::new(crate::executors::start_event::StartEventExecutor),
            multi_instance: Arc::new(crate::executors::multi_instance::MultiInstanceExecutor::new()),
            end_event: Arc::new(crate::executors::end_event::EndEventExecutor),
            compensation_start: Arc::new(
                crate::executors::compensation_start::CompensationStartExecutor,
            ),
            compensation_end: Arc::new(
                crate::executors::compensation_end::CompensationEndExecutor,
            ),
            compensation_throw: Arc::new(
                crate::executors::compensation_throw::CompensationThrowExecutor,
            ),
            compensation_intermediate: Arc::new(
                crate::executors::compensation_intermediate::CompensationIntermediateExecutor,
            ),
            flow_resolver: None,
            def: None,
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

/// `task_kind_attrs` — V5.29 helper. Returns the
/// `attrs` field of a task-kind `NodeKind` (any of
/// `ServiceTask` / `ScriptTask` / `UserTask`) or
/// `None` for non-task kinds. Used by the
/// multi-instance gate to read the
/// `multiInstance` attr without duplicating the
/// match arms in `dispatch()`.
fn task_kind_attrs(kind: &NodeKind) -> Option<&rf_ir::attrs::Attrs> {
    match kind {
        NodeKind::ServiceTask { attrs, .. }
        | NodeKind::ScriptTask { attrs, .. }
        | NodeKind::UserTask { attrs, .. } => Some(attrs),
        _ => None,
    }
}

/// Run a single node against the context. Exhaustive over `NodeKind` —
/// a new variant is a compile error in this match.
pub async fn dispatch(
    node: &FlowNode,
    ctx: &mut FlowContext,
    reg: &ExecutorRegistry,
) -> Result<NodeResult, FlowError> {
    // V5.29 — multi-instance gate. ANY task kind
    // (ServiceTask / ScriptTask / UserTask) with
    // `ruleforge:multiInstance = "true"` is
    // routed to the MI wrapper first, before
    // dispatching on `task_type` / kind-specific
    // arms. The wrapper either expands to N
    // children (and returns Continue with the
    // union-merged vars) or passes through to
    // the inner executor (when the attr is
    // absent / != "true").
    if let Some(mi_attr) = task_kind_attrs(&node.kind)
        .and_then(|a| a.ruleforge("multiInstance"))
    {
        if mi_attr == "true" {
            return reg.multi_instance.execute_with(node, ctx, reg).await;
        }
    }

    match &node.kind {
        // V5.28 P7 — `StartEvent` is now a real executor
        // (manual → Continue, message → Suspend, timer →
        // Unsupported because the scheduler runs timer
        // flows directly). `EndEvent` stays a no-op
        // `Continue` (the end of a branch is detected by
        // the traverser when it sees an end node, not by
        // the executor).
        NodeKind::StartEvent { .. } => reg.start_event.execute(node, ctx).await,
        // V5.30 — `EndEvent` now has attrs +
        // `end_kind` discriminator. The executor
        // returns `Continue` (normal end) or
        // `Fail(FlowError::...)` (error /
        // escalation / terminate end). The
        // dispatcher's job here is just to route
        // to the executor; the end-of-flow
        // detection (no outgoing edges) is still
        // handled by the traverser's `next_node`
        // resolver and the new `NodeResult::Fail`
        // pattern in `step()`.
        NodeKind::EndEvent { .. } => reg.end_event.execute(node, ctx).await,
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
        NodeKind::ExclusiveGateway { .. } => reg.gateway.execute(node, ctx).await,
        // ParallelGateway needs the registry's `def` to resolve
        // outgoing edge targets. The `GatewayExecutor` overrides
        // `execute_with` to read `reg.def`; the trait's default
        // `execute_with` would just call `execute` (which
        // doesn't have access to reg).
        NodeKind::ParallelGateway { .. } => {
            reg.gateway.execute_with(node, ctx, reg).await
        }
        NodeKind::IntermediateEvent { .. } => {
            reg.intermediate_event.execute(node, ctx).await
        }
        NodeKind::BoundaryEvent { .. } => reg.boundary_event.execute(node, ctx).await,
        // SubProcess needs the parent registry to recursively
        // traverse the sub-flow. `execute_with` carries it in.
        NodeKind::SubProcess { .. } => {
            reg.sub_process.execute_with(node, ctx, reg).await
        }
        // V5.31 P0 — 4 compensation event
        // arms. Start/End/Intermediate are
        // straight `execute`s (no-op or
        // stack push/pop). Throw needs
        // `execute_with` for sub-flow
        // recursion (it walks the
        // compensation stack and runs
        // handler sub-flows via
        // `crate::compensation::run_handlers`).
        NodeKind::CompensationStart { .. } => {
            reg.compensation_start.execute(node, ctx).await
        }
        NodeKind::CompensationEnd { .. } => {
            reg.compensation_end.execute(node, ctx).await
        }
        NodeKind::CompensationThrow { .. } => {
            reg.compensation_throw.execute_with(node, ctx, reg).await
        }
        NodeKind::CompensationIntermediate { .. } => {
            reg.compensation_intermediate.execute(node, ctx).await
        }
    }
}
