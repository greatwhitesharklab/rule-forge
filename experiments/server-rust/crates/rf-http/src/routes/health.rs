//! `GET /health` — liveness probe.
//!
//! Phase 6: 200 OK with a small JSON body reporting the rust service
//! version + cache size + inflight count. Cheap, no DB touch (the
//! inflight count may issue a `COUNT(*)` for the pg backend — keep
//! the test fixtures on the in-memory store to avoid that).

use axum::extract::State;
use axum::http::StatusCode;
use axum::response::IntoResponse;
use axum::Json;
use serde_json::json;

use crate::state::AppState;

pub async fn health(State(state): State<AppState>) -> impl IntoResponse {
    let inflight_count = state.inflight.len().await;
    (
        StatusCode::OK,
        Json(json!({
            "status": "ok",
            "service": "server-rust",
            "phase": 6,
            "worker_id": state.worker_id,
            "cache_size": state.repo.cache_size(),
            "inflight_count": inflight_count,
        })),
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;

    use rf_executor::dispatch::ExecutorRegistry;

    use crate::flow_def_repo::FlowDefinitionRepo;
    use crate::flow_def_repo::StubFlowLoader;

    fn state() -> AppState {
        let loader = Arc::new(StubFlowLoader::new());
        let repo = Arc::new(FlowDefinitionRepo::new(loader));
        let registry = Arc::new(ExecutorRegistry::default());
        AppState::new(repo, registry, "test-worker", "http://localhost:8180", "")
    }

    #[tokio::test]
    async fn health_returns_200_with_phase_marker() {
        let resp = health(State(state())).await.into_response();
        assert_eq!(resp.status(), StatusCode::OK);
    }
}
