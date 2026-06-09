//! `MockRuleEngine` — the Phase 4 stand-in for a real rule engine.
//!
//! Reads `vars.applicant.age` and writes back derived decisions into
//! the same `vars` bag. This mirrors what a real V5.x ruleset would
//! compute (approval + credit limit based on age + income), but is
//! expressed as a hand-coded Rust function so the executor's wiring
//! can be smoke-tested without depending on a full RETE engine or the
//! Java executor-app via HTTP.
//!
//! Compare Java `MockRuleEngine` (test fixture in `ruleforge-executor`):
//! ```java
//! public class MockRuleEngine implements RuleEngine {
//!     public RuleResults fireRules(Map<String, Object> facts) {
//!         int age = (int) facts.get("applicant.age");
//!         facts.put("approved", age >= 18);
//!         facts.put("creditLimit", age >= 18 ? 12000 : 0);
//!         return new RuleResults(...);
//!     }
//! }
//! ```
//! Rust: same shape, no `Map<String,Object>` erasure — `vars` is
//! `BTreeMap<String, serde_json::Value>`, so the rule engine can't
//! accidentally shove a non-JSON type into the flow context.

use async_trait::async_trait;
use rf_executor::flow_context::FlowContext;
use rf_executor::rule_engine::{RuleEngine, RuleEngineError, RuleResults};
use serde_json::Value;

pub struct MockRuleEngine;

#[async_trait]
impl RuleEngine for MockRuleEngine {
    async fn fire_rules(&self, ctx: &mut FlowContext) -> Result<RuleResults, RuleEngineError> {
        // 1. Read the input fact.
        let age = ctx
            .vars
            .resolve_path("applicant.age")
            .and_then(Value::as_i64)
            .unwrap_or(0);
        let income = ctx
            .vars
            .resolve_path("applicant.income")
            .and_then(Value::as_i64)
            .unwrap_or(0);

        // 2. Compute the decision. Real rule sets would do groovy
        //    REte over a knowledge base; here we just hand-code the
        //    two output facts the loan-decision flow cares about.
        let approved = age >= 18 && income >= 5000;
        let credit_limit = if approved {
            // crude gradient: 5000 base + 100 per year over 18
            (5000 + (age - 18).max(0) * 100).min(50_000)
        } else {
            0
        };

        // 3. Write back into the same vars bag.
        ctx.vars.insert("approved", Value::Bool(approved));
        ctx.vars
            .insert("creditLimit", Value::Number(credit_limit.into()));

        // 4. Return the audit trail for the state row.
        let fired_rules: Vec<String> = vec!["MockRuleEngine:fire".to_string()];
        let matched_rules: Vec<String> = if approved {
            vec![
                "age>=18".to_string(),
                "income>=5000".to_string(),
                "creditLimit=computed".to_string(),
            ]
        } else {
            vec![]
        };

        Ok(RuleResults {
            fired_rules,
            matched_rules,
        })
    }
}

/// Convenience constructor that returns an `Arc<MockRuleEngine>` for
/// direct wiring into `RuleExecutor::new(Arc::new(MockRuleEngine))`.
pub fn mock_engine() -> std::sync::Arc<MockRuleEngine> {
    std::sync::Arc::new(MockRuleEngine)
}

// Sanity: a `BTreeMap<String, Value>` is the type the engine writes
// into. This test asserts the helper compiles + runs the trivial
// case (no input) without panicking.
#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::BTreeMap;

    #[tokio::test]
    async fn mock_engine_handles_missing_applicant() {
        let engine = MockRuleEngine;
        let mut ctx = FlowContext::new("r");
        let results = engine.fire_rules(&mut ctx).await.unwrap();
        assert!(results
            .fired_rules
            .iter()
            .any(|r| r.contains("MockRuleEngine")));
        assert!(results.matched_rules.is_empty());
        assert_eq!(ctx.vars.get("approved"), Some(&Value::Bool(false)));
        assert_eq!(ctx.vars.get("creditLimit"), Some(&Value::Number(0.into())));
    }

    #[tokio::test]
    async fn mock_engine_approves_qualified_applicant() {
        let engine = MockRuleEngine;
        let mut ctx = FlowContext::new("r");
        ctx.vars.insert(
            "applicant",
            Value::Object(
                BTreeMap::from_iter([
                    ("age".to_string(), Value::Number(25.into())),
                    ("income".to_string(), Value::Number(12_000.into())),
                ])
                .into_iter()
                .collect(),
            ),
        );
        engine.fire_rules(&mut ctx).await.unwrap();
        assert_eq!(ctx.vars.get("approved"), Some(&Value::Bool(true)));
        let limit = ctx.vars.get("creditLimit").and_then(Value::as_i64).unwrap();
        assert!(limit > 5000, "expected creditLimit > 5000, got {limit}");
    }
}
