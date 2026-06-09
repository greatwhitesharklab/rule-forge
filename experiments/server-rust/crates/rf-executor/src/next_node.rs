//! Outgoing-edge routing — picks the next node id after a node finishes.
//!
//! 4-segment priority (matches Java `FlowNodeRunner.nextNode` line 127-170):
//!
//! 1. **userTask binary decision** — if the just-finished node is a
//!    `UserTask` AND `ctx.current_awaiting_field` is set AND its value
//!    matches a `ruleforge:decisionValue` on one of the outgoing edges,
//!    take that edge and clear the awaiting state.
//! 2. **UEL condition** — evaluate each outgoing edge's
//!    `<conditionExpression>`; first true wins.
//! 3. **Weighted random** — if any outgoing edge has a `ruleforge:percent`,
//!    sum them and pick a random cumulative bucket.
//! 4. **Default** — first edge with `is_default == true`, else the first
//!    edge whose condition is `None`, else just the first edge.

use rand::Rng;
use rf_ir::flow_definition::FlowDefinition;
use rf_ir::flow_node::FlowNode;
use rf_ir::node_kind::NodeKind;
use serde_json::Value;

use crate::condition::ConditionEvaluator;
use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::vars::Vars;

const MAX_PERCENT: u32 = 100;

pub fn next_node(
    def: &FlowDefinition,
    node: &FlowNode,
    ctx: &FlowContext,
) -> Result<Option<String>, FlowError> {
    if def.ends.contains(&node.node_id) {
        return Ok(None);
    }
    if node.outgoing_ids.is_empty() {
        return Ok(None);
    }
    if node.outgoing_ids.len() == 1 {
        let edge = find_edge(def, &node.outgoing_ids[0])?;
        return Ok(Some(edge.target.clone()));
    }

    // ── segment 1: userTask binary decision ──
    if matches!(node.kind, NodeKind::UserTask { .. }) {
        if let (Some(field), Some(value)) =
            (&ctx.current_awaiting_field, &ctx.current_awaiting_value)
        {
            // The decision field is set on the userTask; we compare the
            // value stored in vars by the `decision` route. The awaiting
            // value is already extracted from vars into ctx.
            if let Some(target) = match_decision_value(def, node, value) {
                tracing::debug!(node_id = %node.node_id, field, value = %value, "routed via userTask decisionValue");
                return Ok(Some(target));
            }
        }
    }

    // ── segment 2: UEL condition ──
    if let Some(target) = match_condition(def, node, &ctx.vars)? {
        return Ok(Some(target));
    }

    // ── segment 3: percent (weighted random) ──
    if let Some(target) = match_percent(def, node) {
        return Ok(Some(target));
    }

    // ── segment 4: default ──
    for out_id in &node.outgoing_ids {
        if let Some(e) = def.edges.iter().find(|e| &e.id == out_id) {
            if e.is_default {
                return Ok(Some(e.target.clone()));
            }
        }
    }
    // Final fallback: first edge's target, even if it has a condition we
    // couldn't evaluate. Mirrors Java line 168-169.
    let first = find_edge(def, &node.outgoing_ids[0])?;
    Ok(Some(first.target.clone()))
}

fn find_edge<'a>(
    def: &'a FlowDefinition,
    edge_id: &str,
) -> Result<&'a rf_ir::sequence_flow::SequenceFlow, FlowError> {
    def.edges
        .iter()
        .find(|e| e.id == edge_id)
        .ok_or_else(|| FlowError::EdgeNotFound(edge_id.to_string()))
}

fn match_decision_value(def: &FlowDefinition, node: &FlowNode, value: &Value) -> Option<String> {
    // `serde_json::Value::String("no").to_string()` is `"\"no\""` (with
    // the JSON quotes), but `ruleforge:decisionValue` attr is the
    // plain string `no`. Unwrap strings explicitly; fall back to the
    // JSON form for non-string decisions (numbers, bools).
    let needle: String = if let Some(s) = value.as_str() {
        s.to_string()
    } else {
        value.to_string()
    };
    for out_id in &node.outgoing_ids {
        if let Some(e) = def.edges.iter().find(|e| &e.id == out_id) {
            if e.attrs.ruleforge("decisionValue") == Some(&needle) {
                return Some(e.target.clone());
            }
        }
    }
    None
}

fn match_condition(
    def: &FlowDefinition,
    node: &FlowNode,
    vars: &Vars,
) -> Result<Option<String>, FlowError> {
    for out_id in &node.outgoing_ids {
        let Some(e) = def.edges.iter().find(|e| &e.id == out_id) else {
            continue;
        };
        let Some(expr) = e.condition.as_deref() else {
            continue;
        };
        match ConditionEvaluator::evaluate(expr, vars) {
            Ok(true) => return Ok(Some(e.target.clone())),
            Ok(false) => continue,
            Err(err) => {
                tracing::warn!(expr, edge_id = %e.id, error = %err, "condition eval failed");
                continue;
            }
        }
    }
    Ok(None)
}

fn match_percent(def: &FlowDefinition, node: &FlowNode) -> Option<String> {
    let mut total: u32 = 0;
    let mut any = false;
    for out_id in &node.outgoing_ids {
        if let Some(e) = def.edges.iter().find(|e| &e.id == out_id) {
            if let Some(p) = e.percent {
                total = total.saturating_add(p);
                any = true;
            }
        }
    }
    if !any {
        return None;
    }
    let target = rand::thread_rng().gen_range(0..total.max(1));
    let mut cumulative: u32 = 0;
    for out_id in &node.outgoing_ids {
        if let Some(e) = def.edges.iter().find(|e| &e.id == out_id) {
            if let Some(p) = e.percent {
                cumulative = cumulative.saturating_add(p);
                if target < cumulative {
                    return Some(e.target.clone());
                }
            }
        }
    }
    // Fallback: last percent edge.
    for out_id in node.outgoing_ids.iter().rev() {
        if let Some(e) = def.edges.iter().find(|e| &e.id == out_id) {
            if e.percent.is_some() {
                return Some(e.target.clone());
            }
        }
    }
    None
}

/// Sanity cap on percent sum — Java's `nextInt(total)` would mis-distribute
/// if a flow author typo'd `percent="700"`. This is just a marker; the
/// matcher above is correct for any non-negative sum.
#[allow(dead_code)]
pub(crate) const PERCENT_SANITY: u32 = MAX_PERCENT;
