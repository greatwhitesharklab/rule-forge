//! RuleNodeExecutor — delegates to a `dyn RuleEngine`.
//!
//! The executor is a thin adapter: pull `vars` from the context, hand
//! them to the engine, write back the matched-rule list for logging.
//! All the real work lives in the engine impl (mock for v0, Java
//! executor-app via HTTP in production, or a real Rust rule engine).
//!
//! Compare Java `RuleNodeExecutor.execute(node, ctx)`:
//! ```java
//! KnowledgeSession session = ctx.getSession();
//! List<GeneralEntity> facts = ctx.getInsertedEntities();
//! session.insert(facts);
//! session.fireRules();
//! // ...copy session.getGlobal("approved") back to ctx.vars...
//! ```
//! Rust: no `KnowledgeSession` indirection. The engine takes a
//! `&mut FlowContext` and writes back into `ctx.vars` directly.

use std::sync::Arc;

use async_trait::async_trait;
use rf_ir::flow_node::FlowNode;
use rf_ir::node_kind::{NodeKind, TaskType};

use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::node_executor::NodeExecutor;
use crate::node_result::NodeResult;
use crate::rule_engine::RuleEngine;

pub struct RuleExecutor {
    pub engine: Arc<dyn RuleEngine>,
}

impl RuleExecutor {
    pub fn new(engine: Arc<dyn RuleEngine>) -> Self {
        Self { engine }
    }
}

#[async_trait]
impl NodeExecutor for RuleExecutor {
    async fn execute(
        &self,
        node: &FlowNode,
        ctx: &mut FlowContext,
    ) -> Result<NodeResult, FlowError> {
        let NodeKind::ServiceTask { task_type, attrs } = &node.kind else {
            return Err(FlowError::Unsupported(
                "RuleExecutor on non-serviceTask".to_string(),
            ));
        };
        debug_assert_eq!(*task_type, TaskType::Rule);

        let results = self
            .engine
            .fire_rules(ctx)
            .await
            .map_err(|e| FlowError::Script(e.to_string()))?;

        // Record what fired / matched for the state row's audit columns.
        // Java's RuleNodeExecutor does the same on the `nd_fireable_rules` /
        // `nd_matched_rules` columns of `nd_decision_flow_state`.
        if !results.fired_rules.is_empty() {
            ctx.vars
                .insert("_fireable_rules", serde_json::json!(results.fired_rules));
        }
        if !results.matched_rules.is_empty() {
            ctx.vars
                .insert("_matched_rules", serde_json::json!(results.matched_rules));
        }

        // Honour the file/project attrs on the node (logged for now;
        // real engine would use them to load the rule package).
        if let Some(file) = attrs.ruleforge("file") {
            tracing::debug!(file, node_id = %node.node_id, "RuleExecutor fired");
        }
        let _ = attrs; // silence unused
        Ok(NodeResult::Continue)
    }
}
