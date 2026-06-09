//! IR / parse error type.
//!
//! Variants mirror the throw points of Java `BpmnXmlParser.parse()` and
//! `BpmnFlowController.invalidate()` so the executor can pattern-match on
//! the same failure modes a Java operator would recognise.

#[derive(Debug, thiserror::Error)]
pub enum IrError {
    #[error("BPMN XML is empty")]
    Empty,
    #[error("Invalid BPMN XML: {0}")]
    Invalid(String),
    #[error("BPMN root missing namespace {0}")]
    MissingNamespace(&'static str),
    #[error("BPMN has no <process> element")]
    NoProcess,
    #[error("BPMN <process> missing id attribute")]
    MissingProcessId,
    #[error("Duplicate node id: {0} in process {1}")]
    DuplicateNodeId(String, String),
    #[error("Multiple startEvent in process {0}")]
    MultipleStartEvents(String),
    #[error("No startEvent in process {0}")]
    NoStartEvent(String),
    #[error("No endEvent in process {0}")]
    NoEndEvent(String),
    /// `serviceTask` without a recognised `ruleforge:taskType`. Java silently
    /// routes to NoOp; Rust surfaces it as a parse error so the BPMN is
    /// fixed in the editor rather than misfiring at runtime.
    #[error("serviceTask '{0}' has unknown ruleforge:taskType '{1}'")]
    UnknownTaskType(String, String),
}
