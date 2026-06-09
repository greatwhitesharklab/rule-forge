//! `POST /ruleforge/evaluate` — run a decision flow synchronously.
//!
//! ## BDD scenarios
//!
//! ### Scenario: tiny start-end flow returns COMPLETED
//! - **Given** a stub flow loader returns `START → END` BPMN
//! - **And** the request body has `flow_id` and an empty `vars` map
//! - **When** POST /ruleforge/evaluate
//! - **Then** response 200 with `{"result": "COMPLETED", "vars": {...}}`
//!
//! ### Scenario: userTask flow returns PENDING with waitRef
//! - **Given** a flow `START → userTask(decisionField=approve) → ...`
//! - **When** POST /ruleforge/evaluate
//! - **Then** response 200 with `{"result": "PENDING", "flowRunId": "...",
//!   "waitRef": "approve", "payload": {...}}`
//!
//! ### Scenario: unknown flow returns 404
//! - **Given** the stub loader has no `flow_id`
//! - **When** POST /ruleforge/evaluate
//! - **Then** response 404 with `{"error": "flow not found: ..."}`
//!
//! ### Scenario: malformed BPMN returns 502
//! - **Given** the stub loader returns unparseable XML
//! - **When** POST /ruleforge/evaluate
//! - **Then** response 502 with `{"error": "parse failed: ..."}`
//!
//! Compare Java `DecisionServiceImpl.executeDecisionFlow`: same inputs,
//! same outputs. The Rust port doesn't have the MySQL `nd_decision_flow_state`
//! side-effect yet (Phase 6). It also doesn't have the in-memory session
//! cache from the Java side — every `/evaluate` is a fresh traversal.

use std::sync::Arc;

use axum::extract::State;
use axum::http::StatusCode;
use axum::response::IntoResponse;
use axum::Json;
use rf_executor::flow_context::FlowContext;
use rf_executor::traverser::{traverse, TraverseOutcome};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use tracing::{debug, error, warn};

use crate::flow_def_repo::RepoError;
use crate::inflight::InflightFlow;
use crate::state::AppState;

#[derive(Debug, Deserialize)]
pub struct EvaluateRequest {
    pub flow_id: String,
    /// Input facts — `vars.applicant`, `vars.order`, etc. Anything the
    /// flow's `MockRuleEngine` (or future real engine) reads. The
    /// flow_context.vars starts with this map and the engine writes
    /// derived values (e.g. `approved`, `creditLimit`) back into it.
    #[serde(default)]
    pub vars: Value,
    /// Optional convenience fields Java accepts. Phase 5 just flattens
    /// them into the top of `vars` so the rest of the executor doesn't
    /// need to know about them.
    #[serde(default)]
    pub applicant: Option<Value>,
    #[serde(default)]
    pub order: Option<Value>,
    #[serde(default)]
    pub user_id: Option<String>,
    #[serde(default)]
    pub order_no: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(tag = "result", rename_all = "UPPERCASE")]
pub enum EvaluateResponse {
    Completed {
        flow_run_id: String,
        vars: Value,
        current_node_id: Option<String>,
    },
    Pending {
        flow_run_id: String,
        wait_ref: String,
        payload: Value,
    },
}

pub async fn evaluate(
    State(state): State<AppState>,
    Json(req): Json<EvaluateRequest>,
) -> impl IntoResponse {
    let flow_run_id = AppState::new_flow_run_id();
    debug!(%flow_run_id, flow_id = %req.flow_id, "evaluate: start");

    let def = match state.repo.get_or_load(&req.flow_id).await {
        Ok(d) => d,
        Err(RepoError::Loader(crate::flow_def_repo::FlowLoaderError::NotFound(_))) => {
            return (
                StatusCode::NOT_FOUND,
                Json(serde_json::json!({
                    "error": format!("flow not found: {}", req.flow_id),
                })),
            )
                .into_response();
        }
        Err(e) => {
            error!(?e, "evaluate: load failed");
            return (
                StatusCode::BAD_GATEWAY,
                Json(serde_json::json!({
                    "error": format!("{e}"),
                })),
            )
                .into_response();
        }
    };

    let mut ctx = FlowContext::new(&flow_run_id);
    // Seed ctx.vars with whatever the client sent. Top-level applicant/
    // order fields are merged at the top level (so `vars.applicant.age`
    // works the same as the Java convention).
    seed_vars(&mut ctx, &req);

    let outcome = traverse(Arc::clone(&def), ctx, Arc::clone(&state.registry));

    match outcome {
        TraverseOutcome::Completed(t) => {
            let ctx = t.ctx;
            let map: serde_json::Map<String, Value> = ctx.vars.into_inner().into_iter().collect();
            let vars = serde_json::Value::Object(map);
            Json(EvaluateResponse::Completed {
                flow_run_id,
                vars,
                current_node_id: ctx.current_node_id,
            })
            .into_response()
        }
        TraverseOutcome::Suspended(t, info) => {
            // Stash for `/flow/decision` to find. Phase 6: this
            // dispatches to the in-memory or pg-backed store based
            // on the AppState's choice.
            state
                .inflight
                .put(
                    flow_run_id.clone(),
                    InflightFlow {
                        def,
                        ctx: t.ctx,
                        suspend_info: Some(info.clone()),
                    },
                )
                .await;
            let payload = info.payload.clone();
            let wait_ref = info.wait_ref.clone();
            Json(EvaluateResponse::Pending {
                flow_run_id,
                wait_ref,
                payload,
            })
            .into_response()
        }
        TraverseOutcome::Failed(t, err) => {
            let ctx = t.ctx;
            warn!(?err, current_node_id = ?ctx.current_node_id, "evaluate: failed");
            // 500 + body with the error + the last good node id so the
            // frontend can show a useful message.
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

fn seed_vars(ctx: &mut FlowContext, req: &EvaluateRequest) {
    // The client sends `vars` as a map of `name -> value` (or sometimes
    // a non-object). Normalize: take the map, then overlay the
    // optional top-level `applicant` and `order` fields (Java
    // convention — see `DecisionServiceImpl.executeDecisionFlow`).
    if let Some(obj) = req.vars.as_object() {
        for (k, v) in obj {
            ctx.vars.insert(k.clone(), v.clone());
        }
    }
    if let Some(applicant) = &req.applicant {
        ctx.vars.insert("applicant".to_string(), applicant.clone());
    }
    if let Some(order) = &req.order {
        ctx.vars.insert("order".to_string(), order.clone());
    }
    if let Some(uid) = &req.user_id {
        ctx.vars
            .insert("user_id".to_string(), Value::String(uid.clone()));
    }
    if let Some(ono) = &req.order_no {
        ctx.vars
            .insert("order_no".to_string(), Value::String(ono.clone()));
    }
}
