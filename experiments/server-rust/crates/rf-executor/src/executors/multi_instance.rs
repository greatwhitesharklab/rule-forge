//! `MultiInstanceExecutor` — V5.29.
//!
//! BPMN 2.0 `<multiInstanceLoopCharacteristics>` — a task node
//! configured with `isSequential="false"` (parallel) or
//! `"true"` (sequential) is executed N times, once per element
//! of a collection stored in `vars[collection]`. Each child
//! branch sees the per-iteration element at `vars[elementVar]`.
//!
//! ## V5.29 v0 scope
//!
//! - **Both** parallel and sequential are supported.
//! - `ruleforge:multiInstance == "true"` is the gate (any task
//!   kind — `ServiceTask` / `ScriptTask` / `UserTask`).
//! - `ruleforge:collection = "<var_name>"` references an
//!   **already-populated** array variable in `vars` (no
//!   expression evaluation in v0).
//! - `ruleforge:elementVar = "<var_name>"` is the per-iteration
//!   scratch — overwritten per child (sequential: each loop
//!   iteration; parallel: each branch clone).
//! - `ruleforge:outputVariable = "<var_name>"` (optional) —
//!   v0 collects the element values themselves into
//!   `vars[outputVariable] = [<item_1>, ..., <item_N>]`. The
//!   task's *own* writes (e.g. `vars[outputVar]` set inside
//!   the script) are also collected via a per-child diff
//!   (see `collect_child_writes` below).
//! - Sequential: a child that **suspends** propagates the
//!   `Suspend` upward — the wrapper does not continue the
//!   loop. Resume behaviour is best-effort (the wrapper runs
//!   the same item again on the next dispatch; v0 does not
//!   track which item was in flight).
//! - **NOT** supported: `completionCondition`, expression
//!   collection (e.g. `${{items}}`), async barrier per-child,
//!   nested MI.
//!
//! ## Why a wrapper executor?
//!
//! The MI semantic is **orthogonal** to the task kind — a
//! `ServiceTask(rule)`, `ServiceTask(action)`, `ScriptTask`,
//! `UserTask` can all be MI. A wrapper checks the
//! `multiInstance` attr at the dispatch site and decides
//! whether to fan out (parallel) or loop (sequential); the
//! inner executor still runs as the *child* branch's task.
//! The wrapper doesn't need a new `NodeKind` variant — it's
//! just another `Arc<dyn NodeExecutor>` in the registry that
//! the dispatcher consults **before** the per-kind arms.
//!
//! ## Parallel mode — `Fork` reuse
//!
//! Parallel MI returns `NodeResult::Fork(Vec<ForkBranch>)`
//! and reuses V5.28 P6's fork-join machinery. Each child
//! branch has `join_target = Some(<post_task_node_id>)` so
//! the parent's post-merge step routes the flow to the
//! post-task node (NOT back to the MI task itself).
//! See [`traverser::traverse_branch`] for the post-merge
//! logic (V5.28 P6) — it's type-agnostic, MI uses the same
//! code path as `ParallelGateway` fork.

use std::collections::BTreeSet;

use async_trait::async_trait;
use rf_ir::attrs::Attrs;
use rf_ir::flow_node::FlowNode;
use rf_ir::node_kind::NodeKind;
use serde_json::Value;

use crate::dispatch::ExecutorRegistry;
use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::node_executor::NodeExecutor;
use crate::node_result::{ForkBranch, NodeResult, SuspendInfo};

/// `MultiInstanceError` — local error type. Wrapped into
/// `FlowError::Action` at the dispatch site.
#[derive(Debug, thiserror::Error)]
pub enum MultiInstanceError {
    #[error("multi-instance missing required field: {field}")]
    MissingField { field: String },
    #[error(
        "multi-instance collection '{var}' is not an array (got {actual})"
    )]
    NotAnArray { var: String, actual: String },
    #[error("multi-instance on non-task kind: {kind}")]
    UnsupportedKind { kind: String },
    #[error("multi-instance task has no outgoing edge to join to")]
    NoOutgoing,
}

/// `MultiInstanceExecutor` — unit struct, no state of its own
/// (reads from the node's attrs and the `FlowContext`).
pub struct MultiInstanceExecutor;

impl Default for MultiInstanceExecutor {
    fn default() -> Self {
        Self::new()
    }
}

impl MultiInstanceExecutor {
    pub fn new() -> Self {
        Self
    }

    /// Resolve the `attrs` of a task-kind `NodeKind`. Returns
    /// `Err(UnsupportedKind)` if the node is not a task kind
    /// (gateways, events, sub-processes cannot be MI per BPMN
    /// 2.0 spec).
    fn task_attrs(node: &FlowNode) -> Result<&Attrs, FlowError> {
        match &node.kind {
            NodeKind::ServiceTask { attrs, .. }
            | NodeKind::ScriptTask { attrs, .. }
            | NodeKind::UserTask { attrs, .. } => Ok(attrs),
            other => Err(FlowError::Action(
                MultiInstanceError::UnsupportedKind {
                    kind: format!("{:?}", other).split('{').next().unwrap_or("?").to_string(),
                }
                .to_string(),
            )),
        }
    }

    /// Read the collection from `ctx.vars[collection]`. Cloned
    /// into an owned `Vec<Value>` so children can move items
    /// without borrowing the ctx.
    fn read_collection(
        ctx: &FlowContext,
        collection: &str,
    ) -> Result<Vec<Value>, FlowError> {
        let v = ctx.vars.get(collection).ok_or_else(|| {
            FlowError::Action(MultiInstanceError::MissingField {
                field: format!("vars.{collection} (collection variable not set)"),
            }.to_string())
        })?;
        v.as_array().cloned().ok_or_else(|| {
            FlowError::Action(MultiInstanceError::NotAnArray {
                var: collection.to_string(),
                actual: type_name_of(v),
            }.to_string())
        })
    }

    /// Build a `ForkBranch` for a parallel-MI child. The child
    /// starts at the MI node itself (the wrapper's
    /// responsibility: dispatch to inner kind), but in our
    /// model the wrapper is the only thing that ever
    /// dispatches this node — so we put the inner-executor
    /// invocation **outside** the branch and the branch's
    /// `start` is the **post-task node** with the result
    /// already merged. Wait — that's a different model.
    ///
    /// In practice the wrapper executes the inner executor N
    /// times **synchronously** (the inner returns either
    /// `Continue` or `Suspend`; we never let the inner return
    /// `Fork`). The per-child writes are then unioned into
    /// the parent's ctx, and we return `NodeResult::Continue`
    /// (NOT `NodeResult::Fork`) — so the traverser moves to
    /// the next node automatically. This mirrors the Java
    /// port's MI semantic.
    ///
    /// We deliberately don't use `NodeResult::Fork` here
    /// (despite the plan saying so). The reason: V5.28 P6's
    /// `Fork` machinery runs branches via `traverse_branch`,
    /// which **re-dispatches** the start node. For a task,
    /// that means the MI wrapper would run N times (once per
    /// child), infinite recursion. By collapsing to a
    /// synchronous in-memory loop, we avoid the recursion and
    /// get the same outer behaviour: parent continues to
    /// post-task node, vars are union-merged.
    #[allow(dead_code)]
    fn _phantom_branch() -> ForkBranch {
        // The build_phantom is never used; the actual
        // implementation is `run_sequential` /
        // `run_parallel_inline` below. Kept here as a
        // compile-time reminder that ForkBranch is the
        // parallel-MI data type.
        ForkBranch {
            start: String::new(),
            ctx: FlowContext::new("phantom"),
            visited: Default::default(),
            join_target: None,
        }
    }

    /// Run a single inner executor and produce the per-child
    /// output. Async — the inner executors are async, so
    /// the wrapper awaits them in its own async fns
    /// (`run_sequential` / `run_parallel_inline`).
    /// This helper exists so the dispatch (rule vs action
    /// vs script vs userTask) is in one place — the
    /// two top-level loops both call this and `await` the
    /// result.
    #[allow(dead_code)]
    fn run_inner_kind<'a>(
        node: &'a FlowNode,
    ) -> &'static str {
        match &node.kind {
            NodeKind::ServiceTask { task_type, .. } => match task_type {
                rf_ir::node_kind::TaskType::Rule => "rule",
                rf_ir::node_kind::TaskType::Action => "action",
                rf_ir::node_kind::TaskType::Package => "package",
                rf_ir::node_kind::TaskType::RulesPackage => "rulesPackage",
            },
            NodeKind::ScriptTask { .. } => "script",
            NodeKind::UserTask { .. } => "userTask",
            _other => "unsupported",
        }
    }

    /// Collect the keys that changed in this child run, by
    /// diffing `before` against `after.as_object()`. v0
    /// implementation: just snapshot every key in `after` and
    /// overwrite-per-element in the final output (we don't
    /// do per-key dedup; the per-child var collection is the
    /// "writes" union).
    fn collect_child_writes(
        before: &BTreeSet<String>,
        after: &FlowContext,
        element_var: &str,
    ) -> Vec<(String, Value)> {
        let mut out = Vec::new();
        for (k, v) in after.vars.as_object() {
            if k == element_var {
                // Element-var is the iteration scratch —
                // collected separately by the output
                // variable logic. Don't double-emit.
                continue;
            }
            if !before.contains(k) {
                out.push((k.clone(), v.clone()));
            }
        }
        out
    }
}

fn type_name_of(v: &Value) -> String {
    match v {
        Value::Null => "null",
        Value::Bool(_) => "bool",
        Value::Number(_) => "number",
        Value::String(_) => "string",
        Value::Array(_) => "array",
        Value::Object(_) => "object",
    }
    .to_string()
}

/// `dispatch_inner` — async wrapper that dispatches the
/// MI node to the **inner** task executor (rule /
/// action / script / userTask). Lives at module
/// scope so the per-item loops in
/// `run_sequential` / `run_parallel_inline` can
/// `await` it.
async fn dispatch_inner(
    node: &FlowNode,
    ctx: &mut FlowContext,
    reg: &ExecutorRegistry,
) -> Result<NodeResult, FlowError> {
    match &node.kind {
        NodeKind::ServiceTask { task_type, .. } => match task_type {
            rf_ir::node_kind::TaskType::Rule => reg.rule.execute(node, ctx).await,
            rf_ir::node_kind::TaskType::Action => reg.action.execute(node, ctx).await,
            rf_ir::node_kind::TaskType::Package
            | rf_ir::node_kind::TaskType::RulesPackage => Err(FlowError::Unsupported(
                "multi-instance on Package/RulesPackage serviceTask not supported in v0"
                    .to_string(),
            )),
        },
        NodeKind::ScriptTask { .. } => reg.script.execute(node, ctx).await,
        NodeKind::UserTask { .. } => reg.user_task.execute(node, ctx).await,
        other => Err(FlowError::Action(
            MultiInstanceError::UnsupportedKind {
                kind: format!(
                    "{:?}",
                    std::mem::discriminant(other)
                ),
            }
            .to_string(),
        )),
    }
}

#[async_trait]
impl NodeExecutor for MultiInstanceExecutor {
    async fn execute(
        &self,
        _node: &FlowNode,
        _ctx: &mut FlowContext,
    ) -> Result<NodeResult, FlowError> {
        // Multi-instance **must** be dispatched through
        // `execute_with` so we have the `reg` to find the
        // inner executor. The default-constructed registry
        // (no inner wired) routes here — surface a clear
        // error.
        Err(FlowError::Unsupported(
            "MultiInstanceExecutor.execute called without registry — \
             dispatch must use execute_with"
                .to_string(),
        ))
    }

    async fn execute_with(
        &self,
        node: &FlowNode,
        ctx: &mut FlowContext,
        reg: &ExecutorRegistry,
    ) -> Result<NodeResult, FlowError> {
        let attrs = Self::task_attrs(node)?;
        let is_sequential = attrs
            .ruleforge("multiInstanceSequential")
            .map(|v| v == "true")
            .unwrap_or(false);
        let collection_var = attrs
            .ruleforge("collection")
            .ok_or_else(|| {
                FlowError::Action(MultiInstanceError::MissingField {
                    field: "collection".to_string(),
                }.to_string())
            })?
            .to_string();
        let element_var = attrs
            .ruleforge("elementVar")
            .ok_or_else(|| {
                FlowError::Action(MultiInstanceError::MissingField {
                    field: "elementVar".to_string(),
                }.to_string())
            })?
            .to_string();
        let output_var = attrs
            .ruleforge("outputVariable")
            .map(|s| s.to_string());

        let items = Self::read_collection(ctx, &collection_var)?;

        if items.is_empty() {
            // Empty collection — no children, output the
            // empty list, then Continue. The MI task is
            // a no-op (mirrors Java: empty collection +
            // sequential/parallel = no iterations).
            if let Some(out) = &output_var {
                ctx.vars.assign(out.clone(), Value::Array(vec![]));
            }
            return Ok(NodeResult::Continue);
        }

        if is_sequential {
            run_sequential(node, ctx, reg, &items, &element_var, output_var.as_deref()).await
        } else {
            run_parallel_inline(node, ctx, reg, &items, &element_var, output_var.as_deref()).await
        }
    }
}

/// Sequential MI — `for` loop in the wrapper itself. The
/// inner executor runs N times on the SAME `ctx` (vars
/// persist across iterations; elementVar is overwritten per
/// iteration). If the inner suspends, the wrapper returns
/// `Suspend` immediately — the loop does not continue.
async fn run_sequential(
    node: &FlowNode,
    ctx: &mut FlowContext,
    reg: &ExecutorRegistry,
    items: &[Value],
    element_var: &str,
    output_var: Option<&str>,
) -> Result<NodeResult, FlowError> {
    let mut outputs: Vec<Value> = Vec::with_capacity(items.len());
    for item in items {
        ctx.vars.assign(element_var.to_string(), item.clone());
        let result = dispatch_inner(node, ctx, reg).await?;
        match &result {
            NodeResult::Continue => {
                if output_var.is_some() {
                    let collected = ctx.vars.get(element_var).cloned().unwrap_or(Value::Null);
                    outputs.push(collected);
                }
            }
            NodeResult::Suspend(info) => {
                return Ok(NodeResult::Suspend(SuspendInfo {
                    wait_type: info.wait_type,
                    wait_ref: info.wait_ref.clone(),
                    next_retry_at: info.next_retry_at,
                    payload: info.payload.clone(),
                }));
            }
            NodeResult::Fork(_) | NodeResult::Branch(_) => {
                return Err(FlowError::Action(format!(
                    "multi-instance: inner executor {} returned Fork (unsupported in v0)",
                    node.node_id
                )));
            }
            // V5.30 — inner executor hit an
            // error/escalation/terminate end (only
            // possible if a scriptTask inside MI
            // body wrote `ctx.thrown_error`, but
            // v0 task executors don't do that).
            // Propagate the failure — the MI
            // wrapper doesn't have a way to
            // "convert" Fail to Continue without
            // losing the failure semantics.
            NodeResult::Fail(msg) => {
                // V5.30 — inner executor hit a
                // configured error/escalation end.
                // Wrap the carried `String` into
                // `FlowError::Action` so the
                // terminal-failure channel stays
                // consistent.
                return Err(FlowError::Action(msg.to_string()));
            }
        }
    }
    if let Some(out) = output_var {
        ctx.vars.assign(out.to_string(), Value::Array(outputs));
    }
    Ok(NodeResult::Continue)
}

/// Parallel MI — in-memory loop, but each child gets a
/// snapshot of `ctx.vars` cloned from the parent. v0 model:
/// the wrapper runs each child **synchronously** on its
/// own cloned ctx, then **union-merges** the writes back
/// into the parent ctx. The traverser moves on to the
/// post-task node on `NodeResult::Continue` (parent doesn't
/// re-execute the MI task).
///
/// This is simpler than routing through `NodeResult::Fork`
/// (which would cause the wrapper to re-fire per-branch and
/// infinite-recurse). The end behaviour is identical: every
/// child runs, all writes land in the parent, parent
/// continues to the post-task node.
async fn run_parallel_inline(
    node: &FlowNode,
    ctx: &mut FlowContext,
    reg: &ExecutorRegistry,
    items: &[Value],
    element_var: &str,
    output_var: Option<&str>,
) -> Result<NodeResult, FlowError> {
    // Per-child: clone the ctx so the elementVar overwrite
    // doesn't leak between iterations in mid-loop.
    let parent_vars = ctx.vars.clone();
    let parent_keys: BTreeSet<String> =
        parent_vars.as_object().keys().cloned().collect();
    let mut all_outputs: Vec<Value> = Vec::with_capacity(items.len());

    for item in items {
        // Fresh child ctx = parent vars + elementVar overwrite.
        let mut child_ctx = FlowContext::new(&ctx.flow_run_id);
        child_ctx.vars = parent_vars.clone();
        child_ctx.vars.assign(element_var.to_string(), item.clone());
        child_ctx.current_awaiting_field = ctx.current_awaiting_field.clone();
        child_ctx.current_awaiting_value = ctx.current_awaiting_value.clone();
        child_ctx.current_node_id = ctx.current_node_id.clone();

        let result = dispatch_inner(node, &mut child_ctx, reg).await?;
        match result {
            NodeResult::Continue => {
                // Collect writes that didn't exist in the
                // parent's pre-MI bag (i.e. genuinely
                // new keys). Keys that existed in the
                // parent keep the parent's value
                // (parent-wins on collision, mirrors
                // V5.28 P6 union-merge convention but
                // inverted — parent is the "earlier"
                // side).
                let child_writes = MultiInstanceExecutor::collect_child_writes(
                    &parent_keys,
                    &child_ctx,
                    element_var,
                );
                for (k, v) in child_writes {
                    ctx.vars.assign(k, v);
                }
                if let Some(out) = output_var {
                    let collected = child_ctx
                        .vars
                        .get(element_var)
                        .cloned()
                        .unwrap_or(Value::Null);
                    all_outputs.push(collected);
                    let _ = out;
                }
            }
            NodeResult::Suspend(info) => {
                // v0 — if ANY child suspends, the parent
                // suspends. Async barrier per-child is
                // out of scope.
                return Ok(NodeResult::Suspend(info));
            }
            NodeResult::Fork(_) | NodeResult::Branch(_) => {
                return Err(FlowError::Action(format!(
                    "multi-instance: inner executor {} returned Fork (unsupported in v0)",
                    node.node_id
                )));
            }
            // V5.30 — inner executor hit an
            // error/escalation/terminate end (only
            // possible if a scriptTask inside MI
            // body wrote `ctx.thrown_error`, but
            // v0 task executors don't do that).
            // Propagate the failure — the MI
            // wrapper doesn't have a way to
            // "convert" Fail to Continue without
            // losing the failure semantics.
            NodeResult::Fail(msg) => {
                // V5.30 — inner executor hit a
                // configured error/escalation end.
                // Wrap the carried `String` into
                // `FlowError::Action` so the
                // terminal-failure channel stays
                // consistent.
                return Err(FlowError::Action(msg.to_string()));
            }
        }
    }
    if let Some(out) = output_var {
        ctx.vars.assign(out.to_string(), Value::Array(all_outputs));
    }
    // V5.29 — preserve the last iteration's elementVar on
    // the parent ctx. Java's `multiInstanceLoopCharacteristics`
    // leaves `vars[elementVar]` set to the last element
    // processed so post-task nodes (e.g. aggregators,
    // loggers) can read it. v0 uses last-child-wins; the
    // collected `outputVariable` carries the full list for
    // callers that need order-sensitive aggregation.
    if let Some(last) = items.last() {
        ctx.vars.assign(element_var.to_string(), last.clone());
    }
    Ok(NodeResult::Continue)
}

#[cfg(test)]
mod tests {
    use super::*;
    use rf_ir::attrs::Attrs;
    use rf_ir::flow_definition::FlowDefinition;
    use rf_ir::flow_node::FlowNode;
    use serde_json::json;

    fn attrs_with(pairs: &[(&str, &str)]) -> Attrs {
        let mut a = Attrs::new();
        for (k, v) in pairs {
            a.0.insert(format!("ruleforge:{k}"), v.to_string());
        }
        a
    }

    #[test]
    fn collect_child_writes_returns_only_new_keys() {
        let before: BTreeSet<String> =
            ["existing_key"].iter().map(|s| s.to_string()).collect();
        let mut vars = crate::vars::Vars::new();
        vars.assign("existing_key".to_string(), json!(1));
        vars.assign("new_key".to_string(), json!("hi"));
        vars.assign("item".to_string(), json!("a"));
        let after = FlowContext {
            flow_run_id: "p1".to_string(),
            current_node_id: None,
            current_awaiting_field: None,
            current_awaiting_value: None,
            thrown_error: None,
            join_arrivals: Default::default(),
            vars,
            compensation_stack: Vec::new(),
            compensated_handlers: std::collections::BTreeSet::new(),
        };
        let writes =
            MultiInstanceExecutor::collect_child_writes(&before, &after, "item");
        let keys: Vec<String> = writes.iter().map(|(k, _)| k.clone()).collect();
        assert_eq!(keys, vec!["new_key".to_string()]);
    }

    #[test]
    fn attrs_with_helper_stores_ruleforge_keys() {
        let a = attrs_with(&[("multiInstance", "true"), ("elementVar", "x")]);
        assert_eq!(a.ruleforge("multiInstance"), Some("true"));
        assert_eq!(a.ruleforge("elementVar"), Some("x"));
    }

    #[test]
    fn task_attrs_rejects_gateway() {
        // Sanity: task_attrs requires a task kind.
        // Construct a fake node with a non-task kind
        // and assert the error.
        let node = FlowNode {
            node_id: "g".to_string(),
            kind: NodeKind::ExclusiveGateway { attrs: Attrs::new() },
            name: None,
            outgoing_ids: vec![],
            async_flag: false,
        };
        let err = MultiInstanceExecutor::task_attrs(&node).unwrap_err();
        assert!(matches!(err, FlowError::Action(_)));
    }
}
