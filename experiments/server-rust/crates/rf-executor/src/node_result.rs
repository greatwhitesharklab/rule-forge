//! What a node executor returns.
//!
//! Sealed enum that replaces Java's two channels (return value +
//! `AsyncNodeSuspendException`). Suspend is **data**, not control flow —
//! the traverse loop pattern-matches on it and stops cleanly.

use std::collections::HashSet;

use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::flow_context::FlowContext;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub enum NodeResult {
    /// Node finished, walk to the next node via `next_node` routing.
    Continue,
    /// Node forces a specific next node, skipping the normal routing.
    /// Used by Action/Rule executors when they need to skip the gateway
    /// (e.g. on a hard short-circuit). Mirrors Java's
    /// "write to vars then let routing pick it up" but more direct.
    Branch(String),
    /// Parallel split — fork N sub-traversals. V5.28: the
    /// `ParallelGateway` executor returns this when the
    /// gateway has >1 outgoing edges. The
    /// [`crate::traverser::traverse`] driver runs each
    /// branch recursively, then continues the parent.
    /// V5.28 v0 does not model join synchronization — each
    /// branch runs to its own end event; the parent's
    /// "post-fork" continuation is the end of flow. Future
    /// versions can add join tracking (per-gateway
    /// visit-count + `outputMapping` for the merge).
    Fork(Vec<ForkBranch>),
    /// Node needs an external event to continue. The traverser hands the
    /// [`SuspendInfo`] back to the caller, which persists it to the
    /// state table and returns `PENDING` to the HTTP client.
    Suspend(SuspendInfo),
}

/// One branch of a parallel split. Carries the per-branch
/// context and a clone of the parent's `visited` set so each
/// branch's loop detection is independent of the parent and
/// the siblings.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ForkBranch {
    /// First node id of this branch (the parallel gateway's
    /// outgoing edge's target).
    pub start: String,
    /// Per-branch context. Cloned from the parent's `ctx`
    /// before the fork so each branch's writes are isolated
    /// until the post-fork merge.
    pub ctx: FlowContext,
    /// Per-branch visited set. The parent's visited is the
    /// `Traverser`'s own set; the parallel gateway's id is
    /// in it. Each branch starts with the same set so a
    /// branch never re-visits a node the parent has already
    /// visited (other than its sibling's first node, which
    /// is allowed because each branch has its own copy).
    pub visited: HashSet<String>,
    /// V5.28 P6 — explicit join target for the branch.
    ///
    /// `None` (the P0 default) means "no join synchronisation":
    /// each branch runs to its own end event, and the
    /// parent's post-fork continuation is the end of flow
    /// (the diamond pattern collapses to N independent
    /// runs).
    ///
    /// `Some(join_id)` means "this branch, when it completes,
    /// returns to the join gateway at `join_id`". When ALL
    /// branches in the fork reach the join, the join is
    /// crossed and the parent's post-fork continuation is
    /// the join's outgoing edge (true diamond pattern).
    ///
    /// The fork origin writes the same `join_id` to all
    /// branches in one fork; the join itself is a regular
    /// `NodeKind::ParallelGateway` node that the traverser
    /// steps on normally — its presence in the BPMN is
    /// what signals the design intent to the gateway
    /// executor.
    pub join_target: Option<String>,
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
