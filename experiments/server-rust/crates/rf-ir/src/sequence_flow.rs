//! One `<sequenceFlow>` element from the BPMN file.
//!
//! `condition` is the raw UEL expression text (or `None` for unconditional
//! / default / percent flows). `is_default` is true when neither a condition
//! nor a percent is set — the gateway executor treats the unique default as
//! the fallback when no other edge matches.

use serde::{Deserialize, Serialize};

use crate::attrs::Attrs;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SequenceFlow {
    pub id: String,
    pub source: String,
    pub target: String,
    /// Body of the `<conditionExpression>` child element, trimmed. `None` for
    /// unconditional / percent / default flows.
    pub condition: Option<String>,
    /// `ruleforge:percent` value (0..=100). Edges without a percent are not
    /// weighted, even if they have a `condition`.
    pub percent: Option<u32>,
    /// `condition.is_none() && percent.is_none()`. Computed once at parse time
    /// so the gateway executor doesn't have to re-check on every traversal.
    pub is_default: bool,
    /// Other extension attrs (`flowable:`, plus any `ruleforge:` we don't
    /// treat specially — e.g. `ruleforge:decisionValue` for value routing).
    pub attrs: Attrs,
}
