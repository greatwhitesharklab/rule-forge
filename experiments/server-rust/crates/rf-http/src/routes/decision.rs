//! `POST /ruleforge/flow/decision` — resume a suspended flow.
//!
//! ## BDD scenarios
//!
//! ### Scenario: userTask "yes" decision routes to the yes branch
//! - **Given** a flow is suspended at a userTask with `decisionField=approve`
//! - **When** POST /ruleforge/flow/decision?flowRunId=...&decision=yes
//! - **Then** response 200 with `{"result": "COMPLETED", "vars": {...}}`
//!   And `vars.approve == "yes"`
//!
//! ### Scenario: unknown flowRunId returns 404
//! - **Given** the inflight store has no entry for `flowRunId`
//! - **When** POST /ruleforge/flow/decision
//! - **Then** response 404 with `{"error": "no such flowRunId: ..."}`
//!
//! Compare Java `FlowDecisionController.decide`: same path, same
//! semantics, same `flowRunId` + `decision` query params. The Java
//! version also writes to `nd_decision_flow_state` (Phase 6 in Rust).

use std::sync::Arc;

use axum::extract::{Query, State};
use axum::http::StatusCode;
use axum::response::IntoResponse;
use axum::Json;
use rf_executor::traverser::{traverse, TraverseOutcome};
use serde::Deserialize;
use serde_json::Value;
use tracing::debug;

use crate::inflight::InflightFlow;
use crate::routes::evaluate::EvaluateResponse;
use crate::state::AppState;

#[derive(Debug, Deserialize)]
pub struct DecisionQuery {
    /// Java callers use `?flowRunId=...&decision=...`; we keep the
    /// camelCase in the URL to stay drop-in compatible.
    #[serde(rename = "flowRunId")]
    pub flow_run_id: String,
    /// Java uses `"0"` / `"1"` for binary decisions; the userTask
    /// node's outgoing edges declare `ruleforge:decisionValue="0"` or
    /// `"1"` accordingly. For this port we accept the same — and
    /// also plain strings ("yes" / "no") since the test fixtures use
    /// those.
    pub decision: String,
}

pub async fn decide(
    State(state): State<AppState>,
    Query(q): Query<DecisionQuery>,
) -> impl IntoResponse {
    debug!(flow_run_id = %q.flow_run_id, decision = %q.decision, "decide");

    let Some(inflight) = state.inflight.get(&q.flow_run_id).await else {
        return (
            StatusCode::NOT_FOUND,
            Json(serde_json::json!({
                "error": format!("no such flowRunId: {}", q.flow_run_id),
            })),
        )
            .into_response();
    };

    // Stash the decision into ctx.vars (under the awaiting field) and
    // also set current_awaiting_value so the gateway's `next_node`
    // segment-1 (userTask decisionValue) picks the right edge.
    let mut ctx = inflight.ctx;
    let field = ctx.current_awaiting_field.clone().unwrap_or_default();
    if !field.is_empty() {
        ctx.vars
            .insert(field.clone(), Value::String(q.decision.clone()));
    }
    ctx.current_awaiting_value = Some(Value::String(q.decision.clone()));
    // field is already set by the userTask executor; leave it alone.

    let outcome = traverse(Arc::clone(&inflight.def), ctx, Arc::clone(&state.registry));

    match outcome {
        TraverseOutcome::Completed(t) => {
            state.inflight.remove(&q.flow_run_id).await;
            let ctx = t.ctx;
            let map: serde_json::Map<String, Value> = ctx.vars.into_inner().into_iter().collect();
            Json(EvaluateResponse::Completed {
                flow_run_id: q.flow_run_id,
                vars: serde_json::Value::Object(map),
                current_node_id: ctx.current_node_id,
            })
            .into_response()
        }
        TraverseOutcome::Suspended(t, info) => {
            // Multi-step userTask chain (rare but possible). Update
            // the inflight entry to point at the new suspension.
            state
                .inflight
                .update(
                    &q.flow_run_id,
                    InflightFlow {
                        def: inflight.def,
                        ctx: t.ctx,
                        suspend_info: Some(info.clone()),
                    },
                )
                .await;
            Json(EvaluateResponse::Pending {
                flow_run_id: q.flow_run_id,
                wait_ref: info.wait_ref,
                payload: info.payload,
            })
            .into_response()
        }
        TraverseOutcome::Failed(t, err) => {
            state.inflight.remove(&q.flow_run_id).await;
            let ctx = t.ctx;
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                Json(serde_json::json!({
                    "error": format!("{err}"),
                    "current_node_id": ctx.current_node_id,
                })),
            )
                .into_response()
        }
    }
}
