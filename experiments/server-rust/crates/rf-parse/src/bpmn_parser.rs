//! BPMN 2.0 XML → [`FlowDefinition`].
//!
//! Zero-copy via [`roxmltree`]: all string slices borrowed from the input
//! stay live for the lifetime of the parse call. We own copies only of
//! strings we need to outlive the input (node ids, condition text, etc.).
//!
//! Namespaces recognised (mirroring Java `BpmnXmlParser`):
//!   bpmn      = `http://www.omg.org/spec/BPMN/20100524/MODEL`
//!   ruleforge = `http://ruleforge.com/schema`
//!   flowable  = `http://flowable.org/bpmn`  (V5.x compat — stored in attrs, not executed)
//!
//! Differences from Java:
//!   - `serviceTask` with an unknown `ruleforge:taskType` is a hard parse
//!     error (Java silently routed to NoOp). Cleaner to fail in the editor.
//!   - `outgoing_ids` preserves document order; no sort. The gateway executor
//!     picks the unique `is_default` edge if present, otherwise evaluates
//!     `condition` / `percent` in declared order.

use std::collections::BTreeMap;

use chrono::Utc;
use rf_ir::attrs::{Attrs, NS_FLOWABLE, NS_RULEFORGE};
use rf_ir::flow_definition::FlowDefinition;
use rf_ir::flow_node::FlowNode;
use rf_ir::ir_error::IrError;
use rf_ir::node_kind::{EndEventKind, NodeKind, TaskType};
use rf_ir::sequence_flow::SequenceFlow;
use roxmltree::Document;
use sha2::{Digest, Sha256};

const NS_BPMN: &str = "http://www.omg.org/spec/BPMN/20100524/MODEL";

pub struct BpmnXmlParser;

impl BpmnXmlParser {
    pub fn parse(bpmn_xml: &str) -> Result<FlowDefinition, IrError> {
        if bpmn_xml.is_empty() {
            return Err(IrError::Empty);
        }
        let doc = Document::parse(bpmn_xml).map_err(|e| IrError::Invalid(e.to_string()))?;

        let root = doc.root_element();
        let ns_str = root.tag_name().namespace().map(|n| n.to_string());
        if ns_str.as_deref() != Some(NS_BPMN) {
            return Err(IrError::MissingNamespace(NS_BPMN));
        }

        // Plain iteration over the root's element children to find <process>.
        // Mirrors Java BpmnXmlParser line 72-78: no XPath, so no jaxen.
        let process = root
            .children()
            .find(|c| c.is_element() && c.has_tag_name((NS_BPMN, "process")))
            .ok_or(IrError::NoProcess)?;

        let process_id = process
            .attribute("id")
            .filter(|s| !s.is_empty())
            .ok_or(IrError::MissingProcessId)?
            .to_string();
        let process_name = process.attribute("name").map(|s| s.to_string());

        let mut nodes: BTreeMap<String, FlowNode> = BTreeMap::new();
        let mut edges: Vec<SequenceFlow> = Vec::new();

        for el in process.children().filter(|c| c.is_element()) {
            let local = el.tag_name().name();
            if local == "sequenceFlow" {
                if let Some(edge) = parse_sequence_flow(&el) {
                    edges.push(edge);
                }
                continue;
            }

            // `id` is unqualified in BPMN core (per spec §8.3.2). Look it up
            // by local name only — matching Java's attributeValue("id")
            // which is namespace-agnostic.
            let node_id = match el.attribute("id") {
                Some(s) if !s.is_empty() => s.to_string(),
                _ => continue, // BPMN elements without id are ignored
            };

            let ext = extract_extension_attrs(&el);
            let kind = match build_node_kind(local, &el, &ext) {
                Ok(Some(k)) => k,
                Ok(None) => {
                    tracing::warn!(local, %node_id, "Skipping unknown BPMN element");
                    continue;
                }
                Err(e) => return Err(e),
                // Propagate hard errors (unknown taskType) — Java silently
                // routes those to NoOp, but a misconfigured serviceTask is
                // better caught in the editor than at runtime.
            };
            if nodes.contains_key(&node_id) {
                return Err(IrError::DuplicateNodeId(node_id, process_id.clone()));
            }

            let outgoing_ids: Vec<String> = el
                .children()
                .filter(|c| c.is_element() && c.has_tag_name((NS_BPMN, "outgoing")))
                .filter_map(|c| c.text().map(|s| s.trim().to_string()))
                .filter(|s| !s.is_empty())
                .collect();

            let name = el.attribute("name").map(|s| s.to_string());
            let async_flag = ext
                .ruleforge("async")
                .and_then(|s| s.parse::<bool>().ok())
                .unwrap_or(false);

            nodes.insert(
                node_id.clone(),
                FlowNode {
                    node_id,
                    kind,
                    name,
                    outgoing_ids,
                    async_flag,
                },
            );
        }

        let mut start: Option<String> = None;
        let mut ends: Vec<String> = Vec::new();
        // V5.28 P1 — build the `activity_id → boundary_ids`
        // reverse-lookup. BPMN allows multiple boundaries
        // on one activity (one per `errorRef`), so the
        // value is a `Vec`. We insert in document order
        // (BTreeMap iteration is stable), which means
        // when an activity throws, the first matching
        // boundary in document order wins.
        let mut attached_boundaries: BTreeMap<String, Vec<String>> = BTreeMap::new();
        for n in nodes.values() {
            match &n.kind {
                NodeKind::StartEvent { .. } => {
                    if start.is_some() {
                        return Err(IrError::MultipleStartEvents(process_id.clone()));
                    }
                    start = Some(n.node_id.clone());
                }
                NodeKind::EndEvent { .. } => ends.push(n.node_id.clone()),
                NodeKind::BoundaryEvent {
                    attached_to: Some(activity_id),
                    ..
                } => {
                    attached_boundaries
                        .entry(activity_id.clone())
                        .or_default()
                        .push(n.node_id.clone());
                }
                _ => {}
            }
        }
        let start = start.ok_or_else(|| IrError::NoStartEvent(process_id.clone()))?;
        if ends.is_empty() {
            return Err(IrError::NoEndEvent(process_id));
        }

        let source_xml_hash = sha256_hex(bpmn_xml);

        Ok(FlowDefinition {
            process_id,
            name: process_name,
            nodes,
            edges,
            start,
            ends,
            attached_boundaries,
            source_xml: bpmn_xml.to_string(),
            source_xml_hash,
            parsed_at: Utc::now(),
        })
    }
}

fn build_node_kind(
    local: &str,
    el: &roxmltree::Node,
    ext: &Attrs,
) -> Result<Option<NodeKind>, IrError> {
    let kind = match local {
        "startEvent" => Some(NodeKind::StartEvent { attrs: ext.clone() }),
        // V5.30 — `endEvent` is now a struct
        // variant. The parser fills `end_kind`
        // with `None` (the default) and `attrs`
        // with the node's extension attrs; the
        // `EndEventExecutor` reads `endType` /
        // `errorRef` / `escalationRef` at
        // execution time (mirrors `startEvent`'s
        // V5.28 P7 pattern).
        "endEvent" => Some(NodeKind::EndEvent {
            end_kind: EndEventKind::None,
            attrs: ext.clone(),
        }),
        "serviceTask" => {
            let task_type_raw = ext.ruleforge("taskType").ok_or_else(|| {
                IrError::UnknownTaskType(
                    el.attribute("id").unwrap_or("?").to_string(),
                    "<missing>".to_string(),
                )
            })?;
            let task_type = TaskType::from_ruleforge(task_type_raw).ok_or_else(|| {
                IrError::UnknownTaskType(
                    el.attribute("id").unwrap_or("?").to_string(),
                    task_type_raw.to_string(),
                )
            })?;
            Some(NodeKind::ServiceTask {
                task_type,
                attrs: ext.clone(),
            })
        }
        "scriptTask" => {
            let script = el
                .children()
                .find(|c| c.is_element() && c.has_tag_name((NS_BPMN, "script")));
            // `scriptFormat` is unqualified in BPMN.
            let format = script
                .and_then(|s| s.attribute("scriptFormat"))
                .unwrap_or("groovy")
                .to_string();
            let source = script
                .and_then(|s| s.text().map(|t| t.trim().to_string()))
                .unwrap_or_default();
            Some(NodeKind::ScriptTask {
                format,
                source,
                attrs: ext.clone(),
            })
        }
        "userTask" => {
            let decision_type = ext
                .ruleforge("decisionType")
                .unwrap_or("binary")
                .to_string();
            let decision_field = ext.ruleforge("decisionField").unwrap_or("").to_string();
            Some(NodeKind::UserTask {
                decision_type,
                decision_field,
                attrs: ext.clone(),
            })
        }
        "exclusiveGateway" => Some(NodeKind::ExclusiveGateway { attrs: ext.clone() }),
        "parallelGateway" => Some(NodeKind::ParallelGateway { attrs: ext.clone() }),
        "intermediateCatchEvent" => Some(NodeKind::IntermediateEvent { attrs: ext.clone() }),
        // V5.28 P1 — read `attachedToRef` from the
        // BPMN-core attribute namespace (not
        // `ruleforge:` / `flowable:`). A boundary
        // without `attachedToRef` is a sibling-style
        // boundary (V5.27 behaviour, kept for
        // back-compat) — the executor suspends with
        // `error:<ref>` or timer wait_ref.
        "boundaryEvent" => {
            let attached_to = el
                .attribute((NS_BPMN, "attachedToRef"))
                .or_else(|| el.attribute("attachedToRef"))
                .map(|s| s.to_string());
            Some(NodeKind::BoundaryEvent {
                attached_to,
                attrs: ext.clone(),
            })
        }
        "subProcess" => Some(NodeKind::SubProcess { attrs: ext.clone() }),
        _ => None,
    };
    Ok(kind)
}

fn parse_sequence_flow(el: &roxmltree::Node) -> Option<SequenceFlow> {
    // `id` / `sourceRef` / `targetRef` are unqualified in BPMN.
    let id = el.attribute("id")?.to_string();
    let source = el.attribute("sourceRef")?.to_string();
    let target = el.attribute("targetRef")?.to_string();
    let attrs = extract_extension_attrs(el);
    let condition = el
        .children()
        .find(|c| c.is_element() && c.has_tag_name((NS_BPMN, "conditionExpression")))
        .and_then(|c| c.text().map(|s| s.trim().to_string()))
        .filter(|s| !s.is_empty());
    let percent = attrs
        .ruleforge("percent")
        .and_then(|s| s.parse::<u32>().ok());
    let is_default = condition.is_none() && percent.is_none();
    Some(SequenceFlow {
        id,
        source,
        target,
        condition,
        percent,
        is_default,
        attrs,
    })
}

fn extract_extension_attrs(el: &roxmltree::Node) -> Attrs {
    let mut map = BTreeMap::new();
    for attr in el.attributes() {
        let ns = attr.namespace().map(|n| n.to_string());
        if ns.as_deref() == Some(NS_RULEFORGE) {
            map.insert(
                format!("ruleforge:{}", attr.name()),
                attr.value().to_string(),
            );
        } else if ns.as_deref() == Some(NS_FLOWABLE) {
            map.insert(
                format!("flowable:{}", attr.name()),
                attr.value().to_string(),
            );
        }
    }
    Attrs(map)
}

fn sha256_hex(s: &str) -> String {
    let mut h = Sha256::new();
    h.update(s.as_bytes());
    let out = h.finalize();
    out.iter().map(|b| format!("{:02x}", b)).collect()
}
