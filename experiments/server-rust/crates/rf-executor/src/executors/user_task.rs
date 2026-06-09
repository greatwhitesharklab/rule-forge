//! UserTaskNodeExecutor — the Phase 4 keystone.
//!
//! The whole point of this executor is to convert what Java expresses
//! as `throw new AsyncNodeSuspendException(...)` into a value:
//! [`NodeResult::Suspend`]. The traverser hands the `SuspendInfo` to the
//! HTTP handler, which persists it to the state row and returns
//! `PENDING` to the caller. The frontend renders the
//! `payload.decisionField`; the user clicks; the system POSTs to
//! `/flow/decision`; the resume path writes the decision into vars
//! and re-runs the traverser.
//!
//! Compare Java `UserTaskNodeExecutor.execute`:
//! ```java
//! String field = node.attr("ruleforge:decisionField");
//! ctx.setCurrentAwaitingField(field);
//! throw new AsyncNodeSuspendException(
//!     "USER_TASK", field, /* nextRetryAt */ null, payload);
//! ```
//! Rust: same effect, no exception, no separate "user task" code path
//! in the traverser.

use async_trait::async_trait;
use rf_ir::flow_node::FlowNode;
use rf_ir::node_kind::NodeKind;
use serde_json::json;

use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::node_executor::NodeExecutor;
use crate::node_result::{NodeResult, SuspendInfo, WaitType};

pub struct UserTaskExecutor;

#[async_trait]
impl NodeExecutor for UserTaskExecutor {
    async fn execute(
        &self,
        node: &FlowNode,
        ctx: &mut FlowContext,
    ) -> Result<NodeResult, FlowError> {
        let NodeKind::UserTask {
            decision_type,
            decision_field,
            ..
        } = &node.kind
        else {
            return Err(FlowError::Unsupported(
                "UserTaskExecutor on non-userTask".to_string(),
            ));
        };

        if decision_field.is_empty() {
            return Err(FlowError::UserTaskRequiredField(node.node_id.clone()));
        }

        // Resume path: if the caller (HTTP /flow/decision handler)
        // already wrote `current_awaiting_value` and the awaiting
        // field matches, this is a continuation — just return
        // Continue so the gateway's `next_node` can route on the
        // decision value. Mirrors Java's "resume=true" flag in
        // `UserTaskNodeExecutor.execute`.
        if ctx.current_awaiting_field.as_deref() == Some(decision_field.as_str())
            && ctx.current_awaiting_value.is_some()
        {
            return Ok(NodeResult::Continue);
        }

        // First-time path: tell the next gateway which var to read
        // for binary routing. Mirrors Java
        // `ctx.setCurrentAwaitingField(field)` — the gateway's
        // `matchDecisionValue` reads it on the very next step.
        ctx.current_awaiting_field = Some(decision_field.clone());
        // current_awaiting_value is filled in at resume time by the
        // HTTP handler that calls `/flow/decision`.

        let payload = json!({
            "node_id": node.node_id,
            "decision_type": decision_type,
            "decision_field": decision_field,
        });

        Ok(NodeResult::Suspend(SuspendInfo {
            wait_type: WaitType::UserTask,
            wait_ref: decision_field.clone(),
            next_retry_at: None,
            payload,
        }))
    }
}
