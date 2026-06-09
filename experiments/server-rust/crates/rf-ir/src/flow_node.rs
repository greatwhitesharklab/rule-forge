//! One node in the parsed IR.
//!
//! `outgoing_ids` is the list of `<outgoing>` child texts of the BPMN element
//! (in document order). It is **not** the same as `FlowDefinition.edges` —
//! the edges are the canonical sequenceFlow records, while `outgoing_ids` is
//! the order the BPMN author wrote them. The gateway executor prefers
//! `outgoing_ids` so that `default` flow positioning in the XML is honoured.

use serde::{Deserialize, Serialize};

use crate::node_kind::NodeKind;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct FlowNode {
    pub node_id: String,
    pub kind: NodeKind,
    pub name: Option<String>,
    /// `<outgoing>` child texts, in document order. Empty for terminal nodes
    /// (EndEvent) and for nodes that are connected only via `sourceRef` from
    /// sequenceFlow elements.
    pub outgoing_ids: Vec<String>,
    /// `ruleforge:async` attribute; controls whether the executor persists
    /// the run before this node and releases the request back to the caller.
    pub async_flag: bool,
}
