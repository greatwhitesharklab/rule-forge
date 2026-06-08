//! `GET /ruleforge/flow/load?file=xxx` — proxy to the Java console.
//!
//! This is the same path the console itself serves, so we just pass
//! through. We don't cache here (the `evaluate` path does that via
//! `FlowDefinitionRepo`). Useful for "what BPMN does the console think
//! this is?" debugging from a browser.
//!
//! ## BDD scenarios
//!
//! ### Scenario: load an existing flow
//! - **Given** the console has a file `my_flow.bpmn`
//! - **When** GET /ruleforge/flow/load?file=my_flow.bpmn
//! - **Then** response 200 with the raw BPMN XML
//!
//! ### Scenario: console is down
//! - **Given** the console URL is unreachable
//! - **When** GET /ruleforge/flow/load?file=...
//! - **Then** response 502 with `{"error": "console unreachable: ..."}`

use axum::extract::{Query, State};
use axum::http::StatusCode;
use axum::response::IntoResponse;
use axum::Json;
use reqwest::redirect::Policy;
use serde::Deserialize;
use serde_json::json;
use tracing::debug;

use crate::state::AppState;

#[derive(Debug, Deserialize)]
pub struct LoadQuery {
    /// Java callers use `?file=...`; the field stays `file` (not
    /// `flow_id`) since the URL is the same as the Java
    /// `BpmnFlowController.loadBpmn?file=...` endpoint.
    pub file: String,
}

pub async fn load(State(state): State<AppState>, Query(q): Query<LoadQuery>) -> impl IntoResponse {
    // Phase 5: just hit the console directly. Phase 6 might wrap
    // this in a cache + retry, but for now straight pass-through.
    let client = reqwest::Client::builder()
        .redirect(Policy::limited(3))
        .build()
        .expect("build reqwest client");
    let url = format!("{}/ruleforge/flow/load", state.console_url);
    debug!(%url, file = %q.file, "load: fetching from console");
    let resp = match client.get(&url).query(&[("file", &q.file)]).send().await {
        Ok(r) => r,
        Err(e) => {
            return (
                StatusCode::BAD_GATEWAY,
                Json(json!({
                    "error": format!("console unreachable: {e}"),
                })),
            )
                .into_response();
        }
    };
    if !resp.status().is_success() {
        let status = resp.status();
        let body = resp.text().await.unwrap_or_default();
        return (
            StatusCode::from_u16(status.as_u16()).unwrap_or(StatusCode::BAD_GATEWAY),
            Json(json!({
                "error": format!("console returned {status}: {body}"),
            })),
        )
            .into_response();
    }
    let text = match resp.text().await {
        Ok(t) => t,
        Err(e) => {
            return (
                StatusCode::BAD_GATEWAY,
                Json(json!({"error": format!("read body: {e}")})),
            )
                .into_response();
        }
    };
    (
        StatusCode::OK,
        [(axum::http::header::CONTENT_TYPE, "text/xml;charset=UTF-8")],
        text,
    )
        .into_response()
}
