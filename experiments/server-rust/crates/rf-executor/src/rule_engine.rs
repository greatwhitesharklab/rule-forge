//! Abstraction over "execute a set of rules and update vars".
//!
//! The trait takes `&mut FlowContext` (not `&`) so impls can write back
//! derived values like `vars.approved = true` directly. Java's
//! equivalent does the same through `KnowledgeSession.fireRules(input)`
//! which mutates the session's working memory.
//!
//! `RuleResults` is the diagnostic channel: which rules fired, which
//! matched. Currently used for logging and the `fireable_rules` /
//! `matched_rules` columns on `rust_decision_flow_state`. Real impls
//! (V5.x Java equivalent) also return these for the audit log.

use crate::flow_context::FlowContext;

#[async_trait::async_trait]
pub trait RuleEngine: Send + Sync {
    async fn fire_rules(&self, ctx: &mut FlowContext) -> Result<RuleResults, RuleEngineError>;
}

#[derive(Debug, Default, Clone, PartialEq)]
pub struct RuleResults {
    /// Every rule the engine tried. Larger than `matched_rules` because
    /// the engine evaluates all candidates and picks the matching ones.
    pub fired_rules: Vec<String>,
    /// Rules that actually matched (i.e. whose conditions were true).
    /// Java `RuleNodeExecutor` writes this to the `nd_fireable_rules` /
    /// `nd_matched_rules` columns on the state row.
    pub matched_rules: Vec<String>,
}

#[derive(Debug, thiserror::Error)]
pub enum RuleEngineError {
    #[error("rule engine error: {0}")]
    Engine(String),
    #[error("rule package not found: {0}")]
    PackageNotFound(String),
    #[error("rule file parse error: {0}")]
    Parse(String),
}
