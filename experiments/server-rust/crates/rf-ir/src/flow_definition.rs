//! Top-level parsed flow: a single BPMN `<process>` projected to Rust types.

use std::collections::BTreeMap;

use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

use crate::flow_node::FlowNode;
use crate::sequence_flow::SequenceFlow;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct FlowDefinition {
    pub process_id: String,
    pub name: Option<String>,
    /// All flow elements keyed by `id`. BTreeMap so iteration order is stable
    /// — useful for snapshotting and for `cargo test` diffs.
    pub nodes: BTreeMap<String, FlowNode>,
    /// All `<sequenceFlow>` elements, in document order. Edges reference
    /// nodes by `node_id`; missing endpoints surface as runtime errors during
    /// traversal, not as parse errors.
    pub edges: Vec<SequenceFlow>,
    /// `id` of the unique `<startEvent>`. The parser rejects BPMN with zero
    /// or multiple start events.
    pub start: String,
    /// `id`s of `<endEvent>` elements. Multiple end events are allowed (the
    /// last one reached determines the run's terminal state).
    pub ends: Vec<String>,
    /// V5.28 P1 — `activity_id → boundary_id` reverse-lookup.
    /// Built by the parser from each boundary event's
    /// `attachedToRef` attribute. Used by the `traverse` driver
    /// to route to a boundary's outgoing when the attached
    /// activity throws an error. `Vec<String>` because BPMN
    /// allows multiple boundaries on one activity (e.g. one
    /// error boundary per `errorRef`).
    #[serde(default)]
    pub attached_boundaries: BTreeMap<String, Vec<String>>,
    /// V5.31 P0 — `activity_id → compensation_handler_id`
    /// reverse-lookup. Built by the parser from each
    /// `compensateIntermediateThrowEvent`'s
    /// `attachedToRef` attribute. When a
    /// compensation scope is thrown, the executor
    /// walks the scope's `handlers` LIFO and looks
    /// up the first handler for each registered
    /// activity id here. Multiple handlers per
    /// activity are allowed (mirrors the boundary
    /// shape) but v0 only invokes the first one
    /// per activity on throw. Document order is
    /// preserved (BTreeMap iteration is stable).
    #[serde(default)]
    pub attached_compensations: BTreeMap<String, Vec<String>>,
    /// Original BPMN XML, kept so the executor can hash it again to detect
    /// external mutation (mirrors Java `FlowDefinition.sourceXml`).
    pub source_xml: String,
    /// Hex-encoded SHA-256 of `source_xml`. Used to invalidate the run when
    /// the BPMN is re-saved in the console (matches Java `sourceXmlHash`).
    pub source_xml_hash: String,
    pub parsed_at: DateTime<Utc>,
}
