//! BoundaryEventNodeExecutor — error / timer boundary on an activity edge.
//!
//! BPMN 2.0 boundary events sit on the perimeter of an activity
//! (the `attachedToRef` attribute in the XML). Two flavors:
//!
//! - `errorBoundaryEvent` — fires when the attached activity
//!   "throws" an error (an action sets
//!   [`FlowContext::thrown_error`]). The boundary suspends
//!   waiting for a resume key of the form
//!   `error:<errorRef-or-default>`, OR — in V5.28 P1
//!   "really attached" semantics — the
//!   [`crate::traverser`] driver routes the activity's
//!   outcome to the boundary's outgoing edges when the
//!   activity throws (no visit, no suspend).
//! - `timerBoundaryEvent` — fires after a duration. Same
//!   shape as the IntermediateEvent timer, but tagged with
//!   `event_type: "boundaryTimer"` so observability can
//!   distinguish them.
//!
//! ## V5.28 P1 — really attached semantics
//!
//! V5.27 treated every boundary as a sibling node the
//! traverser visits (sibling-style). V5.28 P1 introduces
//! "really attached" routing: when the activity throws
//! an error and a boundary with a matching `errorRef`
//! is `attachedToRef` the activity, the traverser
//! short-circuits the activity's normal outgoing and
//! routes to the boundary's outgoing (the handler
//! path). The boundary is **not visited** in this
//! path — the executor below only runs for
//! sibling-style boundaries (where the boundary is on
//! a sequenceFlow in the main path) and for "second
//! chance" resume handling (where a caller resumes
//! with `current_awaiting_field` matching the
//! boundary's wait_ref).
//!
//! ## Why a separate executor
//!
//! The dispatcher pattern is "visit one node, get one
//! NodeResult, route to next_node". A boundary event
//! visited via a sequenceFlow is a regular node that
//! goes through this pattern. A "really attached"
//! boundary is handled in the traverser's
//! `attached_boundaries` lookup before `next_node` is
//! called — see [`crate::traverser::traverse_branch`].
//!
//! ## Resume
//!
//! Like [`IntermediateEventExecutor`], the resume path
//! checks `current_awaiting_field` against the
//! boundary's wait_ref. If a caller (HTTP `/flow/event`
//! handler) already wrote the matching field and value,
//! this is a continuation — return `Continue` so the
//! boundary's outgoing edge picks up the handler path.

use async_trait::async_trait;
use chrono::{DateTime, Duration, Utc};
use rf_ir::attrs::Attrs;
use rf_ir::flow_node::FlowNode;
use rf_ir::node_kind::NodeKind;
use serde_json::json;

use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::executors::intermediate_event::parse_iso_duration;
use crate::node_executor::NodeExecutor;
use crate::node_result::{NodeResult, SuspendInfo, WaitType};

/// `BoundaryEventError` — local error type for parsing
/// boundary-event attrs. Wraps into `FlowError::Action` at the
/// dispatch site.
#[derive(Debug, thiserror::Error)]
pub enum BoundaryEventError {
    #[error("boundary event unknown kind: {kind}")]
    UnknownKind { kind: String },
    #[error("boundary event missing required field: {field}")]
    MissingField { field: String },
    #[error("boundary event bad ISO 8601 duration: {raw}")]
    BadDuration { raw: String },
}

/// `BoundaryEventKind` — discriminator parsed from
/// `ruleforge:eventType` on the node attrs. Mirrors the BPMN 2.0
/// boundary flavors we support in v0.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum BoundaryEventKind {
    /// `errorBoundaryEvent` — fires when the attached activity
    /// throws a matching error. The error ref (from
    /// `ruleforge:errorRef`, defaulting to `"error"`) is the
    /// resume key for the `/flow/event` handler.
    Error { error_ref: String },
    /// `timerBoundaryEvent` — fires after a duration. Mirrors
    /// the IntermediateEvent timer.
    Timer { duration: Duration },
    /// No `ruleforge:eventType` set — boundary is configured
    /// incorrectly; we treat as None (pass-through) so a
    /// misconfigured boundary doesn't break the flow. The
    /// operator can see the warn in logs.
    None,
}

impl BoundaryEventKind {
    /// Parse from `attrs`. The discriminator is
    /// `ruleforge:eventType`. For `error` the
    /// `ruleforge:errorRef` is optional (defaults to
    /// `"error"`); for `timer` the `ruleforge:timerDuration`
    /// is required.
    pub fn from_attrs(attrs: &Attrs) -> Result<Self, BoundaryEventError> {
        let event_type = attrs.ruleforge("eventType");
        match event_type {
            None => Ok(BoundaryEventKind::None),
            Some("error") => {
                let error_ref = attrs
                    .ruleforge("errorRef")
                    .unwrap_or("error")
                    .to_string();
                Ok(BoundaryEventKind::Error { error_ref })
            }
            Some("timer") => {
                let raw = attrs
                    .ruleforge("timerDuration")
                    .ok_or_else(|| BoundaryEventError::MissingField {
                        field: "timerDuration".to_string(),
                    })?;
                let duration = parse_iso_duration(raw).map_err(|_| {
                    BoundaryEventError::BadDuration {
                        raw: raw.to_string(),
                    }
                })?;
                Ok(BoundaryEventKind::Timer { duration })
            }
            Some(other) => Err(BoundaryEventError::UnknownKind {
                kind: other.to_string(),
            }),
        }
    }
}

pub struct BoundaryEventExecutor;

#[async_trait]
impl NodeExecutor for BoundaryEventExecutor {
    async fn execute(
        &self,
        node: &FlowNode,
        ctx: &mut FlowContext,
    ) -> Result<NodeResult, FlowError> {
        let NodeKind::BoundaryEvent { attached_to, attrs } = &node.kind else {
            return Err(FlowError::Unsupported(
                "BoundaryEventExecutor on non-BoundaryEvent".to_string(),
            ));
        };
        // V5.28 P1 — `attached_to` is the activity id from
        // `bpmn:attachedToRef`. The "really attached" path
        // routes the activity's outcome to the boundary's
        // outgoing edges **without visiting this node** —
        // see [`crate::traverser::traverse_branch`]. The
        // executor below only runs when the boundary is on
        // a sequenceFlow in the main path (sibling-style,
        // V5.27 back-compat) or when a caller resumes a
        // suspended sibling boundary. We trace the
        // `attached_to` value so observability can tell
        // the two modes apart.
        tracing::debug!(
            node_id = %node.node_id,
            attached_to = ?attached_to,
            "BoundaryEventExecutor reached (sibling-style path)"
        );
        let kind = BoundaryEventKind::from_attrs(attrs)
            .map_err(|e| FlowError::Action(e.to_string()))?;
        match kind {
            BoundaryEventKind::None => Ok(NodeResult::Continue),
            BoundaryEventKind::Error { error_ref } => {
                catch_error(node, ctx, &error_ref)
            }
            BoundaryEventKind::Timer { duration } => catch_timer(node, duration),
        }
    }
}

/// Error-boundary catch path. Wait ref is `error:<errorRef>` so
/// the resume handler can disambiguate from other AsyncData
/// suspends. The resume path mirrors IntermediateEvent's
/// message/signal handler.
fn catch_error(
    node: &FlowNode,
    ctx: &mut FlowContext,
    error_ref: &str,
) -> Result<NodeResult, FlowError> {
    let wait_ref = format!("error:{error_ref}");
    if ctx.current_awaiting_field.as_deref() == Some(&wait_ref)
        && ctx.current_awaiting_value.is_some()
    {
        return Ok(NodeResult::Continue);
    }
    ctx.current_awaiting_field = Some(wait_ref.clone());
    let payload = json!({
        "node_id": node.node_id,
        "event_type": "boundaryError",
        "error_ref": error_ref,
    });
    Ok(NodeResult::Suspend(SuspendInfo {
        wait_type: WaitType::AsyncData,
        wait_ref,
        next_retry_at: None,
        payload,
    }))
}

/// Timer-boundary catch path. Identical to IntermediateEvent's
/// timer path, but the payload tags it as a boundary so
/// observability dashboards can distinguish a "SLA timer
/// interrupted this activity" from a "delayed in a catch
/// event".
fn catch_timer(
    node: &FlowNode,
    duration: Duration,
) -> Result<NodeResult, FlowError> {
    let next_retry_at: DateTime<Utc> = Utc::now() + duration;
    let payload = json!({
        "node_id": node.node_id,
        "event_type": "boundaryTimer",
        "duration_seconds": duration.num_seconds(),
    });
    Ok(NodeResult::Suspend(SuspendInfo {
        wait_type: WaitType::AsyncTask,
        // V5.28 P2 — namespace timer wait_ref with
        // `boundaryTimer:` so it can't collide with
        // `IntermediateEvent`'s `timer:<node_id>` (the
        // node_id may differ for a boundary on an
        // activity) or other AsyncTask wait_refs in
        // the same run. Same shape as `error:<ref>`
        // above.
        wait_ref: format!("boundaryTimer:{}", node.node_id),
        next_retry_at: Some(next_retry_at),
        payload,
    }))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn attrs_with(pairs: &[(&str, &str)]) -> Attrs {
        let mut a = Attrs::new();
        for (k, v) in pairs {
            a.0.insert(format!("ruleforge:{k}"), v.to_string());
        }
        a
    }

    #[test]
    fn from_attrs_none_when_no_event_type() {
        let a = Attrs::new();
        assert_eq!(BoundaryEventKind::from_attrs(&a).unwrap(), BoundaryEventKind::None);
    }

    #[test]
    fn from_attrs_error_uses_default_ref_when_missing() {
        let a = attrs_with(&[("eventType", "error")]);
        assert_eq!(
            BoundaryEventKind::from_attrs(&a).unwrap(),
            BoundaryEventKind::Error {
                error_ref: "error".into()
            }
        );
    }

    #[test]
    fn from_attrs_error_uses_explicit_ref() {
        let a = attrs_with(&[("eventType", "error"), ("errorRef", "approval_timeout")]);
        assert_eq!(
            BoundaryEventKind::from_attrs(&a).unwrap(),
            BoundaryEventKind::Error {
                error_ref: "approval_timeout".into()
            }
        );
    }

    #[test]
    fn from_attrs_timer_requires_duration() {
        let a = attrs_with(&[("eventType", "timer")]);
        assert!(matches!(
            BoundaryEventKind::from_attrs(&a),
            Err(BoundaryEventError::MissingField { .. })
        ));
    }

    #[test]
    fn from_attrs_timer_parses_iso_duration() {
        let a = attrs_with(&[("eventType", "timer"), ("timerDuration", "PT30S")]);
        match BoundaryEventKind::from_attrs(&a).unwrap() {
            BoundaryEventKind::Timer { duration } => {
                assert_eq!(duration.num_seconds(), 30);
            }
            other => panic!("expected Timer, got {:?}", other),
        }
    }

    #[test]
    fn from_attrs_unknown_kind_rejected() {
        let a = attrs_with(&[("eventType", "signal")]);
        assert!(matches!(
            BoundaryEventKind::from_attrs(&a),
            Err(BoundaryEventError::UnknownKind { .. })
        ));
    }
}
