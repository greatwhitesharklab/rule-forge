//! Type-state traverser.
//!
//! The four states encode the legal transitions of a running decision flow.
//! Compile-time enforcement: you cannot `.resume()` a `Traverser<Completed>`
//! because that impl block doesn't exist.
//!
//! ```text
//!       ┌──── start() ────► Running ── step*() ──► Completed
//!       │                    │   │
//!       │                    │   └─ suspend(info) ─► Suspended ── resume() ──► Running
//!       │                    │
//!       │                    └─ fail() ─► Failed
//! ```
//!
//! Compare Java: `state.status` is a `String` and the same `FlowNodeRunner`
//! code path handles all transitions, with `instanceof` checks scattered
//! through the codebase to enforce "completed can't be resumed". The
//! type-state makes that check at compile time.
//!
//! ## Parallel split (V5.28)
//!
//! A `NodeResult::Fork(Vec<ForkBranch>)` is produced by
//! `GatewayExecutor` for a `parallelGateway` node. The
//! `traverse()` driver handles it inline by running each
//! branch recursively via `traverse()` and merging the
//! results. V5.28 v0 has no join synchronization — the
//! parent's "post-fork" continuation is the end of flow.
//! See [`node_result::ForkBranch`] for the per-branch
//! payload.

use std::collections::HashSet;
use std::marker::PhantomData;
use std::sync::Arc;

use rf_ir::flow_definition::FlowDefinition;
use rf_ir::node_kind::NodeKind;

use crate::dispatch::{dispatch, ExecutorRegistry};
use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::next_node::next_node;
use crate::node_result::{ForkBranch, NodeResult, SuspendInfo};

const MAX_STEPS: usize = 1000;

/// V5.28 P1 / P4 — `BoundaryTarget` enum capturing the
/// three resolution outcomes for boundary routing:
/// - `Single(target)` — boundary has 1 outgoing edge;
///   simple Continue (P1 fast path).
/// - `Fork(targets)` — boundary has 2+ outgoing edges;
///   fan out to all (P4: parallel fan-out semantics).
///   The `targets` is `Vec<String>` (target node ids);
///   `step()` clones the parent's `ctx` once per target
///   to build `Vec<ForkBranch>`.
/// - `None` — no matching attached boundary; fall
///   through to activity's normal outgoing (P1 fallback).
enum BoundaryTarget {
    Single(String),
    Fork(Vec<String>),
    None,
}

/// V5.28 P1 / P4 — resolve the boundary-routing target(s)
/// for a thrown error. Looks up the activity's attached
/// boundaries (built by the parser from `bpmn:attachedToRef`)
/// and returns either:
/// - `Single(target)` — 1-outgoing boundary, fast path
/// - `Fork(targets)` — 2+ outgoings, fan out (P4)
/// - `None` — no match, fall through
///
/// Document order is preserved by the parser (BTreeMap
/// iteration is stable), so when multiple boundaries match,
/// the first one in document order wins — same semantics as
/// V5.28 P1.
///
/// Each boundary's `outgoing_ids` are **edge ids** (not
/// target node ids — same shape as every other node). We
/// resolve the targets via `def.edges.iter().find(|e| e.id
/// == edge_id)`.
fn boundary_nexts(
    def: &FlowDefinition,
    activity_id: &str,
    thrown_ref: &str,
) -> BoundaryTarget {
    let Some(boundary_ids) = def.attached_boundaries.get(activity_id) else {
        return BoundaryTarget::None;
    };
    for bid in boundary_ids {
        let Some(boundary) = def.nodes.get(bid) else {
            continue;
        };
        let NodeKind::BoundaryEvent { attrs, .. } = &boundary.kind else {
            continue;
        };
        // Default to "error" — matches
        // `BoundaryEventKind::from_attrs` which also defaults
        // to "error" when `ruleforge:errorRef` is missing.
        let boundary_ref = attrs.ruleforge("errorRef").unwrap_or("error");
        if boundary_ref != thrown_ref {
            continue;
        }
        // Resolve all outgoing edge targets. A boundary
        // with zero outgoings is malformed (an error
        // boundary without a handler path) — treat as
        // None so the activity falls through normally.
        if boundary.outgoing_ids.is_empty() {
            tracing::warn!(
                activity_id,
                                boundary_id = %bid,
                                "attached boundary has no outgoing edges; falling through"
                            );
            return BoundaryTarget::None;
        }
        let mut targets = Vec::with_capacity(boundary.outgoing_ids.len());
        for edge_id in &boundary.outgoing_ids {
            if let Some(edge) = def.edges.iter().find(|e| &e.id == edge_id) {
                targets.push(edge.target.clone());
            }
        }
        if targets.is_empty() {
            return BoundaryTarget::None;
        }
        // 1 target → fast path, no fork machinery
        if targets.len() == 1 {
            return BoundaryTarget::Single(targets.into_iter().next().unwrap());
        }
        // 2+ targets → fan out (P4)
        return BoundaryTarget::Fork(targets);
    }
    BoundaryTarget::None
}

/// Public entry — single-call traversal. Loops `step()` until done,
/// suspended, or failed. Wraps the type-state for callers that don't
/// want to drive the state machine by hand.
pub fn traverse(
    def: Arc<FlowDefinition>,
    ctx: FlowContext,
    reg: Arc<ExecutorRegistry>,
) -> TraverseOutcome {
    // V5.28 — auto-wire the def into the registry so executors
    // that need to read it (e.g. `GatewayExecutor` for a
    // `ParallelGateway` resolving outgoing edge targets) can.
    // Callers can pre-set `reg.def` to skip the clone; if
    // they don't, we clone the Arc into a fresh registry.
    // Arc clone is cheap (atomic increment) so this is
    // not a hot path concern.
    let reg = if reg.def.is_some() {
        reg
    } else {
        let mut r = (*reg).clone();
        r.def = Some(Arc::clone(&def));
        Arc::new(r)
    };
    // Hand off to `traverse_branch` so that the top-level
    // entry and per-branch entries share a single
    // implementation. The top-level call starts at
    // `def.start`; branch calls start at the branch's
    // outgoing-edge target.
    traverse_branch(Arc::clone(&def), def.start.clone(), ctx, reg, HashSet::new())
}

/// Run a sub-traversal starting at a specific node id.
/// Used for parallel-fork branches (and, in principle,
/// any other "resume from here" entry point). Each branch
/// gets its own `FlowContext` (cloned by the gateway
/// executor) and its own `visited` set, so writes are
/// isolated and loop detection is local.
fn traverse_branch(
    def: Arc<FlowDefinition>,
    start: String,
    ctx: FlowContext,
    reg: Arc<ExecutorRegistry>,
    visited: HashSet<String>,
) -> TraverseOutcome {
    let mut t = Traverser::<Running>::begin_at(def, start, ctx, reg, visited);
    loop {
        match t.step() {
            Ok(StepOutcome::Continue(next)) => t = next,
            Ok(StepOutcome::Done(done)) => return TraverseOutcome::Completed(done),
            Ok(StepOutcome::Suspended(suspended, info)) => {
                return TraverseOutcome::Suspended(suspended, info)
            }
            Ok(StepOutcome::Fork(parent, branches)) => {
                // Nested fork (a branch that itself contains
                // a parallel gateway). Recurse via
                // `traverse_branch` so each sub-branch
                // starts at its outgoing-edge target.
                //
                // V5.28 P6 — vars are **union-merged**
                // (all branches' writes survive; conflicts
                // resolved last-wins by branch iteration
                // order = outgoing_ids order). When the
                // fork has an explicit `join_target`, the
                // parent's `next` becomes the join id and
                // the join gateway takes over routing
                // (diamond pattern). When the fork has no
                // join (P0 behaviour), the parent finishes
                // here.
                let mut merged_ctx = parent.ctx.clone();
                let mut merged_visited = parent.visited.clone();
                // V5.28 P6 — capture the join target from
                // the first branch. All branches in one
                // fork share the same `join_target` (the
                // gateway executor writes it identically
                // for every branch).
                let join_target = branches.first().and_then(|b| b.join_target.clone());
                for branch in branches {
                    let outcome = traverse_branch(
                        parent.def.clone(),
                        branch.start,
                        branch.ctx,
                        parent.reg.clone(),
                        branch.visited,
                    );
                    match outcome {
                        TraverseOutcome::Completed(c) => {
                            // V5.28 P6 — union-merge vars.
                            // `Vars::assign` is a
                            // per-key overwrite, so walking
                            // branches in
                            // `outgoing_ids` order and
                            // assigning each branch's vars
                            // gives:
                            // - distinct keys: all kept
                            //   (union semantics)
                            // - colliding keys:
                            //   last-branch-wins (the
                            //   branch with the higher
                            //   outgoing-ids index
                            //   overwrites the earlier
                            //   one)
                            // The visited set is the
                            // union so the parent's loop
                            // detection is correct after
                            // the fork (any node any
                            // branch visited is
                            // "off-limits" for re-visit
                            // by the parent).
                            for (k, v) in c.ctx.vars.as_object() {
                                merged_ctx.vars.assign(k.clone(), v.clone());
                            }
                            // V5.28 P6 — also union the
                            // join-arrival counter map
                            // (V5.29 Multi-Instance will
                            // rely on this for async
                            // join barriers; P6 v0 is
                            // sync, so the map is
                            // effectively a no-op here
                            // beyond keeping the merge
                            // contract explicit).
                            for (k, v) in c.ctx.join_arrivals {
                                merged_ctx.join_arrivals.insert(k, v);
                            }
                            // V5.28 P6 — `current_node_id`
                            // uses **last-branch-wins** to
                            // preserve the P0 observable
                            // (the last branch to finish
                            // surfaces its end-state node
                            // id to the caller). When a
                            // true join is present, the
                            // join's `next_node` routing
                            // immediately overwrites
                            // this on the next step.
                            merged_ctx.current_node_id = c.ctx.current_node_id;
                            merged_visited.extend(c.visited.iter().cloned());
                        }
                        TraverseOutcome::Suspended(s, info) => {
                            return TraverseOutcome::Suspended(s, info);
                        }
                        TraverseOutcome::Failed(f, err) => {
                            return TraverseOutcome::Failed(f, err);
                        }
                    }
                }
                // V5.28 P6 — when the fork has an
                // explicit `join_target`, the parent
                // resumes from the join onwards. The
                // branch's visited sets include the
                // join and the post-join chain (the
                // branches traversed through the join
                // and on to the end), so we **discard
                // the branch visited** entirely and
                // start the parent with a fresh
                // visited set containing only the
                // pre-fork nodes. This keeps loop
                // detection correct for the parent's
                // post-join continuation (the parent's
                // post-join chain hasn't been visited
                // by anyone yet — we want the parent
                // to step on it cleanly) while still
                // flagging any pre-fork loop.
                //
                // We **skip the join node itself** in
                // the parent's `next` — the join's
                // purpose is synchronisation (the
                // barrier fires here, in the union
                // merge above), not node execution.
                // The join was a parallel gateway with
                // one outgoing; its outgoing target is
                // the parent's "post-join" first node.
                let next = if let Some(join_id) = join_target.as_ref() {
                    merged_visited = parent.visited.clone();
                    parent
                        .def
                        .nodes
                        .get(join_id)
                        .and_then(|n| n.outgoing_ids.first().cloned())
                        .and_then(|first_edge_id| {
                            parent
                                .def
                                .edges
                                .iter()
                                .find(|e| e.id == first_edge_id)
                                .map(|e| e.target.clone())
                        })
                } else {
                    None
                };
                let new_parent = Traverser::<Running> {
                    def: parent.def,
                    ctx: merged_ctx,
                    reg: parent.reg,
                    next,
                    visited: merged_visited,
                    _state: PhantomData,
                };
                t = new_parent;
            }
            Err((failed, err)) => return TraverseOutcome::Failed(failed, err),
        }
    }
}

#[derive(Debug)]
pub enum TraverseOutcome {
    Completed(Traverser<Completed>),
    Suspended(Traverser<Suspended>, SuspendInfo),
    Failed(Traverser<Failed>, FlowError),
}

impl TraverseOutcome {
    pub fn context(&self) -> &FlowContext {
        match self {
            TraverseOutcome::Completed(t) => t.ctx(),
            TraverseOutcome::Suspended(t, _) => t.ctx(),
            TraverseOutcome::Failed(t, _) => t.ctx(),
        }
    }
    pub fn into_context(self) -> FlowContext {
        match self {
            TraverseOutcome::Completed(t) => t.ctx,
            TraverseOutcome::Suspended(t, _) => t.ctx,
            TraverseOutcome::Failed(t, _) => t.ctx,
        }
    }
}

// ── state markers ─────────────────────────────────────────────────────────

#[derive(Debug)]
pub struct Pending;
#[derive(Debug)]
pub struct Running;
#[derive(Debug)]
pub struct Suspended;
#[derive(Debug)]
pub struct Completed;
#[derive(Debug)]
pub struct Failed;

#[derive(Debug)]
pub struct Traverser<State> {
    pub def: Arc<FlowDefinition>,
    pub ctx: FlowContext,
    pub reg: Arc<ExecutorRegistry>,
    /// Next node id to visit. `None` means traversal finished.
    pub next: Option<String>,
    /// Tracks which node ids have been stepped on this run, for loop
    /// detection. Java uses the same trick at line 79.
    visited: HashSet<String>,
    _state: PhantomData<State>,
}

impl<S> Traverser<S> {
    pub fn def(&self) -> &FlowDefinition {
        &self.def
    }
    pub fn ctx(&self) -> &FlowContext {
        &self.ctx
    }
    pub fn ctx_mut(&mut self) -> &mut FlowContext {
        &mut self.ctx
    }
    pub fn visited(&self) -> &HashSet<String> {
        &self.visited
    }
}

impl Traverser<Running> {
    pub fn begin(def: Arc<FlowDefinition>, ctx: FlowContext, reg: Arc<ExecutorRegistry>) -> Self {
        let start = def.start.clone();
        Self {
            def,
            ctx,
            reg,
            next: Some(start),
            visited: HashSet::new(),
            _state: PhantomData,
        }
    }

    /// Construct a traverser that starts at a specific node id
    /// rather than `def.start`. Used by the parallel-split
    /// fork handler to run each branch from its outgoing
    /// edge's target. The `visited` set is passed in (cloned
    /// from the parent) so each branch's loop detection is
    /// local — siblings don't interfere with each other.
    pub fn begin_at(
        def: Arc<FlowDefinition>,
        start: String,
        ctx: FlowContext,
        reg: Arc<ExecutorRegistry>,
        visited: HashSet<String>,
    ) -> Self {
        Self {
            def,
            ctx,
            reg,
            next: Some(start),
            visited,
            _state: PhantomData,
        }
    }

    /// Take a single step. The executor is intentionally synchronous-style
    /// (`async` for future-proofing when Phase 4 adds real async nodes, but
    /// Phase 3's `dispatch` is pure-CPU).
    ///
    /// The Err variant carries the failed traverser alongside the error
    /// because callers (the `traverse()` driver, the recovery loop in
    /// Phase 6) need the final `FlowContext` to persist `current_node_id`
    /// and `vars` to the state row. The size warning is intentional.
    #[allow(clippy::result_large_err)]
    pub fn step(mut self) -> Result<StepOutcome, (Traverser<Failed>, FlowError)> {
        let Some(node_id) = self.next.clone() else {
            return Ok(StepOutcome::Done(self.into_completed()));
        };

        if self.visited.len() > MAX_STEPS {
            return Err((self.into_failed(), FlowError::MaxStepsExceeded(MAX_STEPS)));
        }
        if !self.visited.insert(node_id.clone()) {
            return Err((self.into_failed(), FlowError::LoopDetected(node_id)));
        }

        let Some(node) = self.def.nodes.get(&node_id).cloned() else {
            return Err((self.into_failed(), FlowError::NodeNotFound(node_id)));
        };

        self.ctx.current_node_id = Some(node_id.clone());

        // The dispatch call is async — wrap in block_in_place for the
        // Phase 3 sync executor so we don't have to pull in tokio::task
        // for the test runtime.
        let result = match pollster::block_on(dispatch(&node, &mut self.ctx, &self.reg)) {
            Ok(r) => r,
            Err(e) => return Err((self.into_failed(), e)),
        };

        match result {
            NodeResult::Continue => {
                // V5.28 P1 / P4 — boundary error routing. If
                // the activity threw (an action set
                // `ctx.thrown_error`), look up the attached
                // boundaries for this node and route to the
                // matching boundary's outgoing edge(s). We
                // do this BEFORE `next_node` so the boundary
                // short-circuits the activity's normal
                // outgoing. If no boundary matches, fall
                // through to the normal next_node path.
                if let Some(thrown_ref) = self.ctx.thrown_error.take() {
                    match boundary_nexts(&self.def, &node_id, &thrown_ref) {
                        BoundaryTarget::Single(target) => {
                            // V5.28 P1 — 1-outgoing
                            // boundary: simple Continue (no
                            // fork machinery needed).
                            self.next = Some(target);
                            return Ok(StepOutcome::Continue(self));
                        }
                        BoundaryTarget::Fork(targets) => {
                            // V5.28 P4 — multi-outgoing
                            // boundary: fan out to ALL
                            // outgoing edges. Build one
                            // `ForkBranch` per target; each
                            // branch clones `self.ctx` so
                            // activity writes (like
                            // `__throw_ran__`) are visible
                            // to all branches. The boundary
                            // itself is **not visited** —
                            // its outgoings are direct
                            // targets, just like
                            // ParallelGateway's fork.
                            let branches: Vec<ForkBranch> = targets
                                .into_iter()
                                .map(|target| ForkBranch {
                                    start: target,
                                    ctx: self.ctx.clone(),
                                    visited: HashSet::new(),
                                    // V5.28 P4 boundary
                                    // multi-outgoing fan-out
                                    // is a "throw routing"
                                    // construct, not a true
                                    // parallel fork — there's
                                    // no join synchronisation
                                    // for boundary outgoings.
                                    join_target: None,
                                })
                                .collect();
                            let parent = Traverser::<Running> {
                                def: self.def,
                                ctx: self.ctx,
                                reg: self.reg,
                                next: None,
                                visited: self.visited,
                                _state: PhantomData,
                            };
                            return Ok(StepOutcome::Fork(parent, branches));
                        }
                        BoundaryTarget::None => {
                            // No matching boundary — re-attach
                            // the thrown error so an outer
                            // handler (or a log line) can
                            // still see it. v0 doesn't have
                            // an outer handler, so this is a
                            // deliberate "drop with trace" —
                            // the activity continues normally.
                            tracing::warn!(
                                node_id = %node_id,
                                thrown_ref = %thrown_ref,
                                "activity threw but no matching attached boundary; falling through to normal outgoing"
                            );
                            self.ctx.thrown_error = Some(thrown_ref);
                        }
                    }
                }
                match next_node(&self.def, &node, &self.ctx) {
                    Ok(Some(next_id)) => {
                        self.next = Some(next_id);
                        Ok(StepOutcome::Continue(self))
                    }
                    Ok(None) => Ok(StepOutcome::Done(self.into_completed())),
                    Err(e) => Err((self.into_failed(), e)),
                }
            }
            NodeResult::Branch(target) => {
                self.next = Some(target);
                Ok(StepOutcome::Continue(self))
            }
            NodeResult::Fork(branches) => {
                // The GatewayExecutor built the branches —
                // we just hand them up to the driver. The
                // driver runs each branch via
                // `traverse_loop()`, merges results, and
                // returns a fresh `Traverser<Running>` with
                // `next = None` (V5.28 v0: parallel split
                // has no join semantics).
                let parent = Traverser::<Running> {
                    def: self.def,
                    ctx: self.ctx,
                    reg: self.reg,
                    next: None,
                    visited: self.visited,
                    _state: PhantomData,
                };
                Ok(StepOutcome::Fork(parent, branches))
            }
            NodeResult::Suspend(info) => {
                let suspended = self.into_suspended();
                Ok(StepOutcome::Suspended(suspended, info))
            }
        }
    }

    pub fn into_suspended(self) -> Traverser<Suspended> {
        Traverser {
            def: self.def,
            ctx: self.ctx,
            reg: self.reg,
            next: self.next,
            visited: self.visited,
            _state: PhantomData,
        }
    }

    pub fn into_completed(self) -> Traverser<Completed> {
        Traverser {
            def: self.def,
            ctx: self.ctx,
            reg: self.reg,
            next: None,
            visited: self.visited,
            _state: PhantomData,
        }
    }

    pub fn into_failed(self) -> Traverser<Failed> {
        Traverser {
            def: self.def,
            ctx: self.ctx,
            reg: self.reg,
            next: None,
            visited: self.visited,
            _state: PhantomData,
        }
    }
}

impl Traverser<Suspended> {
    /// Resume from suspension. The caller (HTTP handler / recovery job) is
    /// responsible for re-populating `ctx.vars` from the persisted
    /// `row_vars` JSONB column before calling this.
    pub fn resume(self) -> Traverser<Running> {
        Traverser {
            def: self.def,
            ctx: self.ctx,
            reg: self.reg,
            next: self.next,
            visited: self.visited,
            _state: PhantomData,
        }
    }
}

#[derive(Debug)]
pub enum StepOutcome {
    Continue(Traverser<Running>),
    Done(Traverser<Completed>),
    Suspended(Traverser<Suspended>, SuspendInfo),
    /// Parallel split — the driver should run each branch
    /// via `traverse_loop` and continue the parent with
    /// the merged context. The `Traverser<Running>` here
    /// carries the parent's `def` / `reg` / `visited` set
    /// (cloned into the branches' `ForkBranch.visited`).
    Fork(Traverser<Running>, Vec<ForkBranch>),
}
