//! Gateway executors — `ExclusiveGateway` + `ParallelGateway`.
//!
//! Gateway *routing* (which outgoing edge to follow) for
//! `ExclusiveGateway` lives in [`crate::next_node`], not in
//! the executor. The executor itself just signals
//! "continue" so the traverser can call `next_node()` and
//! pick one edge. This matches Java's
//! `GatewayNodeExecutor.execute()` which also doesn't pick
//! the next edge — `FlowNodeRunner.nextNode` does.
//!
//! ## `ParallelGateway` (V5.28)
//!
//! Parallel gateway is different. Its semantics are
//! "fork N branches", which means the executor itself
//! produces the routing decision (an N-way split, not a
//! 1-way choice). We can't model that with `next_node`'s
//! `Option<String>` return — it returns a single target.
//!
//! The `GatewayExecutor::execute_with` for a
//! `ParallelGateway` therefore:
//!
//! 1. Reads the registry's `def` (auto-wired by
//!    `traverse()`).
//! 2. For each outgoing edge, builds a [`ForkBranch`]
//!    with a cloned `FlowContext` and a per-branch
//!    `visited` set (so each branch's loop detection is
//!    independent).
//! 3. (V5.28 P6) Detects an explicit join gateway — a
//!    downstream `parallelGateway` whose incoming-edge
//!    count matches this fork's branch count — and
//!    threads the join id into every branch's
//!    `join_target`. When all branches reach the join,
//!    the traverser's union-merge step fires and the
//!    parent continues from the join's outgoing edge.
//! 4. Returns [`NodeResult::Fork`] so the traverser
//!    driver runs the branches.
//!
//! ### V5.28 v0 join semantics
//!
//! We **do not** model join synchronization. Each branch
//! runs to its own end event; the parent's "post-fork"
//! continuation is the end of flow (the traverser sets
//! `next = None` after all branches complete). The
//! diamond pattern (split + join) is supported
//! "naively" — each branch runs to its own end, the join
//! gateway on each branch is a no-op `Continue`.
//! Future versions can add a per-gateway visit-count
//! tracker + `outputMapping` to merge branches back at a
//! true join.
//!
//! ### V5.28 P6 — explicit join
//!
//! V5.28 P6 introduces explicit-join detection. A
//! `parallelGateway` with **multiple incoming edges**
//! (in-degree ≥ 2) is a join candidate. The gateway
//! executor that initiates a fork looks for the unique
//! join candidate reachable from the fork's outgoing
//! targets; if exactly one is found, the join id is
//! threaded into every branch and the post-merge step
//! routes the parent through the join's outgoing edge.
//!
//! The detection is **heuristic** — when the BPMN has
//! multiple parallel gateways with the right in-degree
//! (e.g. a nested fork-join), the heuristic returns
//! `None` and the executor falls back to P0 behaviour
//! (each branch runs to its own end event). Java has the
//! same limitation in v0; the real spec-compliance
//! detection (BPMN token-based join tracking) is left
//! for V5.30+.
//!
//! [`ForkBranch`]: crate::node_result::ForkBranch

use std::collections::HashSet;

use async_trait::async_trait;
use rf_ir::flow_node::FlowNode;
use rf_ir::node_kind::NodeKind;

use crate::dispatch::ExecutorRegistry;
use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::node_executor::NodeExecutor;
use crate::node_result::{ForkBranch, NodeResult};

pub struct GatewayExecutor;

#[async_trait]
impl NodeExecutor for GatewayExecutor {
    /// Exclusive gateway: pass through. The 4-segment routing
    /// in [`crate::next_node`] picks one outgoing edge
    /// (UEL condition / weighted random / default).
    /// The "no outgoing edges" case is also handled by
    /// `next_node` (it returns `None` → `Done`).
    async fn execute(
        &self,
        _node: &FlowNode,
        _ctx: &mut FlowContext,
    ) -> Result<NodeResult, FlowError> {
        Ok(NodeResult::Continue)
    }

    /// Parallel gateway: fork N branches. Override of
    /// `execute_with` because we need the registry's
    /// `def` to resolve outgoing edge targets (the
    /// `FlowNode` only carries edge `id`s, not target
    /// `id`s). If the registry has no `def` wired, we
    /// surface a clear error rather than silently
    /// mis-executing.
    async fn execute_with(
        &self,
        node: &FlowNode,
        ctx: &mut FlowContext,
        reg: &ExecutorRegistry,
    ) -> Result<NodeResult, FlowError> {
        // We accept both ParallelGateway and ExclusiveGateway
        // here defensively — the dispatcher routes
        // ExclusiveGateway to `execute` and ParallelGateway
        // to `execute_with`, but if a caller invokes
        // `execute_with` directly for an ExclusiveGateway
        // (e.g. from a test), we still want the right
        // behavior.
        let NodeKind::ParallelGateway { .. } = &node.kind else {
            return Ok(NodeResult::Continue);
        };

        let def = reg.def.as_ref().ok_or_else(|| {
            FlowError::Action(format!(
                "ParallelGateway {}: registry has no def wired (callers must \
                 invoke via traverse() or set reg.def explicitly)",
                node.node_id
            ))
        })?;

        if node.outgoing_ids.is_empty() {
            return Err(FlowError::Action(format!(
                "ParallelGateway {}: has no outgoing edges (a parallel gateway \
                 must have at least one outgoing edge to be meaningful)",
                node.node_id
            )));
        }

        // 1 outgoing edge → "join only" / pass-through
        // (no fan-out). Return Continue so the traverser
        // routes via `next_node` like an exclusive
        // gateway. This is the case where a parallel
        // gateway is used purely as a sync point — the
        // single branch that entered continues.
        if node.outgoing_ids.len() == 1 {
            return Ok(NodeResult::Continue);
        }

        // 2+ outgoing edges → fork. Build one
        // `ForkBranch` per edge. Each branch's `ctx` is
        // a clone of the parent's `ctx` BEFORE the
        // gateway executes (so each branch's writes are
        // isolated until the post-fork merge). The
        // visited set is shared so that nodes the
        // parent has already visited (i.e. before
        // reaching the parallel gateway) are not
        // re-visited by any branch — branches inherit
        // the parent's loop-detection state.
        //
        // We don't track which nodes the parent has
        // visited here directly; the traverser hands
        // each branch a `visited` snapshot via
        // `ForkBranch.visited` (see
        // `Traverser::step`'s `NodeResult::Fork` arm and
        // the `traverse()` driver's branch loop).
        let branch_targets: Vec<String> = node
            .outgoing_ids
            .iter()
            .map(|edge_id| {
                def.edges
                    .iter()
                    .find(|e| &e.id == edge_id)
                    .ok_or_else(|| {
                        FlowError::EdgeNotFound(format!(
                            "{} (referenced by parallel gateway {})",
                            edge_id, node.node_id
                        ))
                    })
                    .map(|e| e.target.clone())
            })
            .collect::<Result<Vec<_>, _>>()?;

        // V5.28 P6 — find an explicit join target. The
        // heuristic looks for a `parallelGateway` node
        // whose in-degree equals the fork's branch
        // count. If exactly one such node exists, it's
        // the join. If multiple match (nested fork
        // ambiguity) or none match, fall back to P0
        // (`join_target = None`).
        let join_target = find_join_target(def, &branch_targets, branch_targets.len());

        let mut branches = Vec::with_capacity(branch_targets.len());
        for start in branch_targets {
            let branch_ctx = ctx.clone();
            // The branch's visited set starts EMPTY —
            // each branch's loop detection is local to
            // the branch. The traverser, when it runs
            // the branch, will not pass the parent's
            // visited into it; instead, it creates a
            // fresh `Traverser<Running>` for the branch
            // (see `Traverser::begin_at`). The parallel
            // gateway's own id is already in the
            // parent's visited (it was just stepped on).
            let branch_visited: HashSet<String> = HashSet::new();
            branches.push(ForkBranch {
                start,
                ctx: branch_ctx,
                visited: branch_visited,
                join_target: join_target.clone(),
            });
        }

        tracing::debug!(
            node_id = %node.node_id,
            branch_count = branches.len(),
            join_target = ?join_target,
            "parallel gateway fork"
        );
        // V5.28 P6 observability — `info_span!` wraps
        // the fork decision so dashboards (e.g. the
        // tracing-subscriber JSON formatter) can
        // see one structured record per fork. The
        // span is created but not entered — `tracing`
        // macros inside the recursive `traverse_branch`
        // call still surface; this just makes sure
        // the fork's metadata is on the trail too.
        // The metrics counter (Prometheus) for fork
        // count is recorded in the traverser's
        // `StepOutcome::Fork` arm in V5.32; for now
        // we just log.
        let _span = tracing::info_span!(
            "parallel_gateway_fork",
            gateway_id = %node.node_id,
            branch_count = branches.len(),
            join_target = ?join_target,
        );
        Ok(NodeResult::Fork(branches))
    }
}

/// V5.28 P6 — explicit-join detection heuristic.
///
/// A "join" is a `parallelGateway` node with multiple
/// incoming edges (in-degree ≥ 2) that should
/// synchronise the branches of a fork. The heuristic
/// returns the **unique** join candidate whose
/// in-degree matches the fork's branch count; if no
/// candidate or multiple candidates match, returns
/// `None` (fallback to P0 "no join" behaviour).
///
/// The uniqueness check is on the **whole def** — we
/// don't restrict the search to nodes reachable from
/// `branch_targets`. This is intentionally lenient:
/// the BPMN author is responsible for placing the join
/// downstream of the fork, and a unique in-degree
/// match is a strong signal. (A more precise check
/// would do a BFS from each branch target, but the
/// extra precision is not worth the complexity in
/// v0.)
fn find_join_target(
    def: &rf_ir::flow_definition::FlowDefinition,
    _branch_targets: &[String],
    branch_count: usize,
) -> Option<String> {
    if branch_count < 2 {
        return None;
    }
    // In-degree per node id, counting edges whose
    // target is the node.
    let mut in_degree: std::collections::HashMap<String, usize> =
        std::collections::HashMap::new();
    for edge in &def.edges {
        *in_degree.entry(edge.target.clone()).or_insert(0) += 1;
    }
    // Walk all nodes; collect those that are
    // `parallelGateway` AND have in-degree ==
    // branch_count. A join must also have ≥ 1
    // outgoing edge (otherwise it's a dead-end).
    let mut candidates: Vec<String> = Vec::new();
    for (node_id, node) in &def.nodes {
        if !matches!(node.kind, NodeKind::ParallelGateway { .. }) {
            continue;
        }
        if in_degree.get(node_id).copied().unwrap_or(0) != branch_count {
            continue;
        }
        if node.outgoing_ids.is_empty() {
            continue;
        }
        candidates.push(node_id.clone());
    }
    if candidates.len() == 1 {
        Some(candidates.remove(0))
    } else {
        // 0 candidates → no join (P0 behaviour);
        // 2+ → ambiguous (nested fork-join), also
        // fall back to P0.
        None
    }
}
