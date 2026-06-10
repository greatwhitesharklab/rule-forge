//! `POST /ruleforge/flow/event` — resume a flow suspended at an
//! `intermediateCatchEvent`.
//!
//! ## BDD scenarios
//!
//! ### Scenario: message catch resumes when event name matches
//! - **Given** a flow is suspended at
//!   `intermediateCatchEvent(eventType=message, eventName=approval_received)`
//! - **When** POST /ruleforge/flow/event with
//!   `{"flow_run_id": "...", "event_name": "approval_received", "payload": {...}}`
//! - **Then** response 200 with `{"result": "COMPLETED", "vars": {...}}`
//! - **And** `vars.approval_received == {<payload>}`
//!
//! ### Scenario: signal catch resumes
//! - Same as above but `eventType=signal` and `eventName=broadcast_xyz`.
//!
//! ### Scenario: mismatched event name returns 409
//! - **Given** a flow is suspended at
//!   `intermediateCatchEvent(eventName=approval_received)`
//! - **When** POST /ruleforge/flow/event with `event_name=different_event`
//! - **Then** response 409 with
//!   `{"error": "flow is awaiting event 'approval_received', got 'different_event'"}`
//!
//! ### Scenario: unknown flowRunId returns 404
//! - **Given** the inflight store has no entry for `flowRunId`
//! - **When** POST /ruleforge/flow/event
//! - **Then** response 404 with `{"error": "no such flowRunId: ..."}`
//!
//! ### Scenario: timer-suspended flow returns 409
//! - **Given** a flow is suspended at
//!   `intermediateCatchEvent(eventType=timer)`
//! - **When** POST /ruleforge/flow/event
//! - **Then** response 409 — timers are re-driven by the recovery loop,
//!   not by external event delivery.
//!
//! Compare Java `FlowEventController.deliver` (planned for V5.26 —
//! not yet in Java). The Rust port closes the suspend→resume loop for
//! `AsyncData` waits; `AsyncTask` waits stay on the recovery loop.

use std::sync::Arc;

use axum::extract::State;
use axum::http::StatusCode;
use axum::response::IntoResponse;
use axum::Json;
use rf_executor::node_result::WaitType;
use rf_executor::traverser::{traverse, TraverseOutcome};
use serde::Deserialize;
use serde_json::Value;
use tracing::debug;

use crate::inflight::InflightFlow;
use crate::routes::evaluate::EvaluateResponse;
use crate::state::AppState;

#[derive(Debug, Deserialize)]
pub struct EventRequest {
    /// Java callers use `?flowRunId=...` for `/decision`; for `/event`
    /// the body is JSON so we use the underscored variant. The axum
    /// JSON extractor maps the wire form (`flowRunId` OR `flow_run_id`)
    /// to this field via the rename below.
    #[serde(rename = "flowRunId", alias = "flow_run_id")]
    pub flow_run_id: String,
    /// Name of the event being delivered. Must match
    /// `ctx.current_awaiting_field` (which the
    /// `IntermediateEventExecutor` set to `eventName` at suspend time).
    #[serde(rename = "eventName", alias = "event_name")]
    pub event_name: String,
    /// Payload to attach. Becomes `vars[eventName] = payload` so
    /// downstream rules / gateways can read it, and
    /// `current_awaiting_value` so the executor treats this as a
    /// continuation on the next traverse.
    #[serde(default)]
    pub payload: Value,
}

pub async fn deliver(
    State(state): State<AppState>,
    Json(req): Json<EventRequest>,
) -> impl IntoResponse {
    debug!(
        flow_run_id = %req.flow_run_id,
        event_name = %req.event_name,
        "deliver event"
    );

    let Some(inflight) = state.inflight.get(&req.flow_run_id).await else {
        return (
            StatusCode::NOT_FOUND,
            Json(serde_json::json!({
                "error": format!("no such flowRunId: {}", req.flow_run_id),
            })),
        )
            .into_response();
    };

    // The IntermediateEventExecutor suspends with wait_type =
    // AsyncData for message/signal. A flow suspended at a timer
    // catch uses AsyncTask and is re-driven by the recovery loop —
    // we reject event delivery for those.
    if inflight.suspend_info.as_ref().map(|i| i.wait_type) == Some(WaitType::AsyncTask) {
        return (
            StatusCode::CONFLICT,
            Json(serde_json::json!({
                "error": "flow is suspended at a timer; timers are re-driven by the recovery loop, not by /flow/event",
                "wait_type": "AsyncTask",
            })),
        )
            .into_response();
    }

    // The event name on the request must match the awaiting field
    // the executor set at suspend time. Mismatch = caller is
    // delivering a different event than the one the flow is
    // waiting for.
    let awaiting = inflight.ctx.current_awaiting_field.as_deref().unwrap_or("");
    if awaiting != req.event_name {
        return (
            StatusCode::CONFLICT,
            Json(serde_json::json!({
                "error": format!(
                    "flow is awaiting event '{awaiting}', got '{}'",
                    req.event_name
                ),
                "awaiting_event": awaiting,
            })),
        )
            .into_response();
    }

    // Resume path: write the payload into vars[eventName] (so
    // downstream rules / gateways can reference it) and set
    // current_awaiting_value so the IntermediateEventExecutor's
    // resume check (`is_some()`) returns Continue.
    let mut ctx = inflight.ctx;
    ctx.vars
        .insert(req.event_name.clone(), req.payload.clone());
    ctx.current_awaiting_value = Some(req.payload.clone());

    let outcome = traverse(Arc::clone(&inflight.def), ctx, Arc::clone(&state.registry));

    match outcome {
        TraverseOutcome::Completed(t) => {
            state.inflight.remove(&req.flow_run_id).await;
            let ctx = t.ctx;
            let map: serde_json::Map<String, Value> = ctx.vars.into_inner().into_iter().collect();
            Json(EvaluateResponse::Completed {
                flow_run_id: req.flow_run_id,
                vars: serde_json::Value::Object(map),
                current_node_id: ctx.current_node_id,
            })
            .into_response()
        }
        TraverseOutcome::Suspended(t, info) => {
            // The flow hit another suspension (e.g. userTask downstream
            // of the event). Re-stash the inflight entry pointing at
            // the new suspension.
            state
                .inflight
                .update(
                    &req.flow_run_id,
                    InflightFlow {
                        def: inflight.def,
                        ctx: t.ctx,
                        suspend_info: Some(info.clone()),
                    },
                )
                .await;
            Json(EvaluateResponse::Pending {
                flow_run_id: req.flow_run_id,
                wait_ref: info.wait_ref,
                payload: info.payload,
            })
            .into_response()
        }
        TraverseOutcome::Failed(t, err) => {
            state.inflight.remove(&req.flow_run_id).await;
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
