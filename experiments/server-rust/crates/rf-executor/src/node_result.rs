//! What a node executor returns.
//!
//! Sealed enum that replaces Java's two channels (return value +
//! `AsyncNodeSuspendException`). Suspend is **data**, not control flow —
//! the traverse loop pattern-matches on it and stops cleanly.

use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use serde_json::Value;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub enum NodeResult {
    /// Node finished, walk to the next node via `next_node` routing.
    Continue,
    /// Node forces a specific next node, skipping the normal routing.
    /// Used by Action/Rule executors when they need to skip the gateway
    /// (e.g. on a hard short-circuit). Mirrors Java's
    /// "write to vars then let routing pick it up" but more direct.
    Branch(String),
    /// Node needs an external event to continue. The traverser hands the
    /// [`SuspendInfo`] back to the caller, which persists it to the
    /// state table and returns `PENDING` to the HTTP client.
    Suspend(SuspendInfo),
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct SuspendInfo {
    /// What kind of event we are waiting for. `UserTask` = manual decision,
    /// `AsyncData` = external API call, `AsyncTask` = scheduled retry.
    pub wait_type: WaitType,
    /// Caller-supplied identifier (e.g. the userTask's `decisionField`,
    /// or the async task's `jobId`). Used to correlate the resume.
    pub wait_ref: String,
    /// Earliest time the recovery loop may retry (relevant for `AsyncTask`;
    /// `None` for `UserTask` until the user clicks).
    pub next_retry_at: Option<DateTime<Utc>>,
    /// JSON payload echoed back to the caller — e.g. the userTask's
    /// `decisionField` so the frontend knows which radio button to render.
    pub payload: Value,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum WaitType {
    UserTask,
    AsyncData,
    AsyncTask,
}
