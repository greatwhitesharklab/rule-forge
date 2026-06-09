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
    /// Original BPMN XML, kept so the executor can hash it again to detect
    /// external mutation (mirrors Java `FlowDefinition.sourceXml`).
    pub source_xml: String,
    /// Hex-encoded SHA-256 of `source_xml`. Used to invalidate the run when
    /// the BPMN is re-saved in the console (matches Java `sourceXmlHash`).
    pub source_xml_hash: String,
    pub parsed_at: DateTime<Utc>,
}
