//! Executor error type. Mirrors the failure modes surfaced by Java's
//! `FlowNodeRunner` + `NodeExecutor` chain so the persistence layer can
//! classify and the HTTP layer can return appropriate status codes.

#[derive(Debug, thiserror::Error)]
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
}
