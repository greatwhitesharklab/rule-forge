//! `POST /ruleforge/flow/invalidate?flowId=xxx` — drop a flow from the cache.
//!
//! Called by the Java console after `BpmnFlowController.save` /
//! `BpmnFlowController.deploy` so the Rust executor reloads the new XML
//! on the next `/ruleforge/evaluate`.
//!
//! ## BDD scenarios
//!
//! ### Scenario: invalidate an existing flow
//! - **Given** a flow is loaded in the cache
//! - **When** POST /ruleforge/flow/invalidate?flowId=...
//! - **Then** response 200 with `{"result": true, "flowId": "..."}`
//! - **And** the next /ruleforge/evaluate re-loads the XML
//!
//! ### Scenario: invalidate a non-cached flow
//! - **Given** the cache has no `flowId`
//! - **When** POST /ruleforge/flow/invalidate
//! - **Then** response 200 with `{"result": false, "flowId": "..."}`

use axum::extract::{Query, State};
use axum::http::StatusCode;
use axum::response::IntoResponse;
use axum::Json;
use serde::Deserialize;
use serde_json::json;
use tracing::info;

use crate::state::AppState;

#[derive(Debug, Deserialize)]
pub struct InvalidateQuery {
    /// Java callers use `?flowId=...`; keep the camelCase URL.
    #[serde(rename = "flowId")]
    pub flow_id: String,
}

pub async fn invalidate(
    State(state): State<AppState>,
    Query(q): Query<InvalidateQuery>,
) -> impl IntoResponse {
    let removed = state.repo.invalidate(&q.flow_id);
    info!(flow_id = %q.flow_id, removed, "invalidate");
    // Always 200 — Java console doesn't care if the flow was cached
    // or not, and 200 with `result: false` is the canonical "ok, no-op".
    (
        StatusCode::OK,
        Json(json!({
            "result": removed,
            "flowId": q.flow_id,
        })),
    )
}
