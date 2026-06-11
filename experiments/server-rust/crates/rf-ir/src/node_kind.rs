//! Sum-type node kinds.
//!
//! `NodeKind` encodes both the BPMN element kind AND the extension data the
//! executor needs. The Java side keeps `NodeType` as a flat enum + an
//! `extensionAttrs: Map<String,String>` and re-parses those attrs on every
//! dispatch — Rust pushes the discriminator into the type so dispatch is
//! exhaustive and attribute lookups are confined to the parser.
//!
//! Compare Java `NodeExecutorRegistry` (string-keyed `Map`):
//! ```java
//! registry.get(node.getType() + ":" + ext.get("ruleforge:taskType"))
//! ```
//! Rust `match`:
//! ```ignore
//! match &node.kind {
//!     NodeKind::ServiceTask { task_type: TaskType::Rule, .. } => …,
//!     NodeKind::ServiceTask { task_type: TaskType::Action, .. } => …,
//!     …
//! }
//! ```
//! Adding a node kind is a compile-time error in the dispatch match.

use serde::{Deserialize, Serialize};

use crate::attrs::Attrs;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum NodeKind {
    /// `bpmn:startEvent`. V5.27 treated this as a
    /// parameterless no-op; V5.28 P7 attaches
    /// `attrs` to carry the **start trigger**
    /// discriminator (mirrors `IntermediateEvent`'s
    /// `eventType`):
    ///
    /// - no `startTrigger` (or `startTrigger="manual"`)
    ///   — the **default** behaviour. The flow starts
    ///   when the HTTP caller POSTs to
    ///   `/ruleforge/evaluate`. The dispatcher's
    ///   `StartEvent` arm is a no-op `Continue`.
    /// - `startTrigger="message"` + `eventName` —
    ///   **message start**. The dispatcher suspends
    ///   with `AsyncData` + wait_ref
    ///   `message:<eventName>` and the flow stays in
    ///   the inflight store until a caller hits
    ///   `POST /flow/start-by-message` with the same
    ///   event name. (V5.28 P7 v0 supports a single
    ///   registered flow per event name; multi-flow
    ///   fan-out is V5.29 Multi-Instance.)
    /// - `startTrigger="timer"` + `timerDuration` —
    ///   **timer start**. The dispatcher is NOT
    ///   called for a timer-start flow in v0 — the
    ///   scheduler in `main.rs` is responsible for
    ///   creating flow runs (timer scheduling is
    ///   out of V5.28 P7 scope; the parser
    ///   recognises the trigger so a future
    ///   `TimerScheduler` can register it).
    ///
    /// `attrs` is **always present** (V5.28 P7) so
    /// callers can read the trigger discriminator
    /// uniformly; the parser fills it with an empty
    /// `Attrs` for legacy BPMN files that don't set
    /// `startTrigger`.
    StartEvent { attrs: Attrs },
    /// `bpmn:endEvent`. V5.30 — evolved from the
    /// V5.27 unit variant to carry an `end_kind`
    /// discriminator + attrs, mirroring
    /// `StartEvent` / `BoundaryEvent` /
    /// `IntermediateEvent` shape. v0 supports:
    ///
    /// - `end_kind = None` — no `endType` (the
    ///   default). Normal end → `Continue` →
    ///   `TraverseOutcome::Completed`.
    /// - `end_kind = Error { error_ref }` —
    ///   `ruleforge:endType="error"` +
    ///   `ruleforge:errorRef` (default `"error"`).
    ///   The end marks the flow as failed at a
    ///   business level (e.g. loan rejected); the
    ///   traverser returns `Failed(FlowError::ErrorEnd(ref))`.
    /// - `end_kind = Escalation { escalation_ref }` —
    ///   `ruleforge:endType="escalation"` +
    ///   `ruleforge:escalationRef`. v0 collapses
    ///   to the same `Fail` path as `Error`
    ///   (escalation is a "softer" error in BPMN
    ///   spec — V5.31 CompensationScope will refine
    ///   the parent-scope-continuation behaviour).
    /// - `end_kind = Terminate` —
    ///   `ruleforge:endType="terminate"`. v0 is
    ///   equivalent to `Error`; V5.31 P1 adds real
    ///   token-kill semantics.
    ///
    /// The `attrs` field is always present so the
    /// executor can read the discriminator uniformly
    /// (matches `StartEvent`'s V5.28 P7 pattern).
    EndEvent { end_kind: EndEventKind, attrs: Attrs },
    ServiceTask {
        task_type: TaskType,
        attrs: Attrs,
    },
    ScriptTask {
        format: String,
        source: String,
        attrs: Attrs,
    },
    UserTask {
        decision_type: String,
        decision_field: String,
        attrs: Attrs,
    },
    ExclusiveGateway {
        attrs: Attrs,
    },
    ParallelGateway {
        attrs: Attrs,
    },
    IntermediateEvent {
        attrs: Attrs,
    },
    /// `boundaryEvent` — sits on the edge of an activity. Two flavors
    /// in V5.27: `errorBoundaryEvent` (catches a thrown error from
    /// the attached activity) and `timerBoundaryEvent` (fires after
    /// `timerDuration`; common for SLA timeouts). The
    /// `eventType` attr is the discriminator (mirrors
    /// `IntermediateEvent`'s `eventType`). Attached activity is
    /// identified by the BPMN `attachedToRef` attribute.
    ///
    /// V5.28 P1 — `attached_to` is the id of the activity this
    /// boundary is attached to (parsed from the BPMN
    /// `attachedToRef` attribute). When the activity throws an
    /// error matching this boundary's `ruleforge:errorRef`, the
    /// `traverse` driver routes the flow to this boundary's
    /// outgoing edges (the handler path) instead of the
    /// activity's normal outgoing. V5.27 treated the boundary as
    /// a sibling in the sequence flow (its outgoing was the
    /// handler path, but no actual error routing happened);
    /// V5.28 P1 is the "really attached" version. Backward
    /// compat: `attached_to == None` keeps the V5.27 sibling
    /// behaviour (executor suspends with `error:<ref>` or
    /// timer).
    BoundaryEvent {
        attached_to: Option<String>,
        attrs: Attrs,
    },
    /// Recognized but not executed by the v0 executor — parser still extracts
    /// the node so legacy BPMN files don't fail to load, but a flow that
    /// actually traverses into a subProcess will be rejected at runtime.
    SubProcess {
        attrs: Attrs,
    },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum TaskType {
    Rule,
    Action,
    Package,
    RulesPackage,
}

/// `EndEventKind` — V5.30 discriminator for
/// `NodeKind::EndEvent`. Mirrors the
/// `IntermediateEventKind` / `BoundaryEventKind`
/// pattern (`from_attrs` reads `ruleforge:endType`
/// + the type-specific `ref` attr).
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum EndEventKind {
    /// Default — no `endType`. The end is a normal
    /// "flow finished" marker → `Continue` →
    /// `Completed`.
    None,
    /// `ruleforge:endType="error"` +
    /// `ruleforge:errorRef` (default `"error"`).
    /// The end marks the flow as failed at a
    /// business level; the traverser exits with
    /// `Failed(FlowError::ErrorEnd(error_ref))`.
    Error { error_ref: String },
    /// `ruleforge:endType="escalation"` +
    /// `ruleforge:escalationRef` (default
    /// `"escalation"`). v0 takes the same
    /// `Fail` path as `Error`; V5.31 will
    /// refine the parent-scope behaviour.
    Escalation { escalation_ref: String },
    /// `ruleforge:endType="terminate"`. v0 is
    /// equivalent to `Error`; V5.31 P1 adds
    /// real token-kill semantics.
    Terminate,
}

/// `EndEventError` — V5.30 parse errors for
/// `EndEventKind::from_attrs`. Wrapped into
/// `FlowError::Action` at the dispatch site.
#[derive(Debug, thiserror::Error)]
pub enum EndEventError {
    #[error("end event unknown endType: {kind}")]
    UnknownEndType { kind: String },
}

impl EndEventKind {
    /// Parse the `ruleforge:endType` attribute. Mirrors
    /// `IntermediateEventKind::from_attrs` and
    /// `BoundaryEventKind::from_attrs`.
    ///
    /// - `None` (or `endType=""` / `endType="none"`) → `None`
    /// - `endType="error"` → `Error { error_ref }`
    ///   (default `"error"`)
    /// - `endType="escalation"` → `Escalation { escalation_ref }`
    ///   (default `"escalation"`)
    /// - `endType="terminate"` → `Terminate`
    /// - anything else → `Err(EndEventError::UnknownEndType)`
    pub fn from_attrs(attrs: &Attrs) -> Result<Self, EndEventError> {
        let end_type = attrs.ruleforge("endType");
        match end_type {
            None => Ok(EndEventKind::None),
            Some("") | Some("none") => Ok(EndEventKind::None),
            Some("error") => {
                let error_ref = attrs
                    .ruleforge("errorRef")
                    .unwrap_or("error")
                    .to_string();
                Ok(EndEventKind::Error { error_ref })
            }
            Some("escalation") => {
                let escalation_ref = attrs
                    .ruleforge("escalationRef")
                    .unwrap_or("escalation")
                    .to_string();
                Ok(EndEventKind::Escalation { escalation_ref })
            }
            Some("terminate") => Ok(EndEventKind::Terminate),
            Some(other) => Err(EndEventError::UnknownEndType {
                kind: other.to_string(),
            }),
        }
    }
}

impl TaskType {
    /// Parse the `ruleforge:taskType` attribute value. Unknown values yield
    /// `None` so the parser can surface a clear error (a serviceTask without
    /// a recognised task type is malformed BPMN, not a silent skip).
    pub fn from_ruleforge(value: &str) -> Option<Self> {
        match value {
            "rule" | "rules" => Some(TaskType::Rule),
            "action" => Some(TaskType::Action),
            "package" => Some(TaskType::Package),
            "rulesPackage" => Some(TaskType::RulesPackage),
            _ => None,
        }
    }
}
