//! Executor error type. Mirrors the failure modes surfaced by Java's
//! `FlowNodeRunner` + `NodeExecutor` chain so the persistence layer can
//! classify and the HTTP layer can return appropriate status codes.

#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error)]
pub enum FlowError {
    #[error("node not found: {0}")]
    NodeNotFound(String),

    #[error("edge not found: {0}")]
    EdgeNotFound(String),

    /// Java throws this on `visited.add()` returning false; we use the
    /// same shape so a runaway gateway manifests the same way.
    #[error("loop detected: node {0} visited twice")]
    LoopDetected(String),

    #[error("max steps ({0}) exceeded — possible infinite loop")]
    MaxStepsExceeded(usize),

    #[error("unsupported: {0}")]
    Unsupported(String),

    #[error("no rule engine configured for service task")]
    NoRuleEngine,

    #[error("action error: {0}")]
    Action(String),

    #[error("script error: {0}")]
    Script(String),

    #[error("user task missing required field: {0}")]
    UserTaskRequiredField(String),

    #[error("no default edge on gateway {0}")]
    NoDefaultEdge(String),

    #[error("decisionValue lookup for user task did not match any edge")]
    DecisionValueNotMatched,

    #[error("percent edges do not sum to > 0 on gateway {0}")]
    InvalidPercent(String),

    /// V5.30 — `bpmn:endEvent` with
    /// `ruleforge:endType="error"`. The flow
    /// exited at a business-level error end (e.g.
    /// "loan rejected"). Distinct from `Action` /
    /// dispatch failures: this is a *successful*
    /// traversal that hit a configured failure
    /// terminal.
    #[error("flow reached error end: {0}")]
    ErrorEnd(String),

    /// V5.30 — `bpmn:endEvent` with
    /// `ruleforge:endType="escalation"`. v0
    /// terminates the flow like `ErrorEnd`; V5.31
    /// CompensationScope will refine parent-scope
    /// behaviour.
    #[error("flow reached escalation end: {0}")]
    EscalationEnd(String),

    /// V5.30 — `bpmn:endEvent` with
    /// `ruleforge:endType="terminate"`. v0 is
    /// equivalent to `ErrorEnd`; V5.31 P1 adds
    /// real token-kill semantics (kill all
    /// in-flight tokens across all flows).
    #[error("flow terminated")]
    Terminated,

    /// V5.31 P0 — a compensation handler
    /// failed (the sub-flow inside a
    /// `CompensationThrow` exited with
    /// `TraverseOutcome::Failed`). The carried
    /// `String` is the Display form of the
    /// handler's own `FlowError` (typically a
    /// `FlowError::Action(...)` wrap). The
    /// outer flow's terminal state remains
    /// `Failed` regardless of compensation
    /// success; this variant exists for the
    /// compensation trace appended to the
    /// outer `FlowError::Action` message at
    /// the traverser seam.
    #[error("compensation failure: {0}")]
    Compensation(String),

    /// V5.31 P0 — `CompensationThrow` ran with
    /// an empty stack (no `CompensationStart`
    /// to match). Outer flow exits as `Failed`
    /// with this error so the misconfiguration
    /// surfaces in HTTP responses.
    #[error("compensation throw with no open scope")]
    CompensationNoScope,
}
