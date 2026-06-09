//! BDD integration tests for `POST /ruleforge/flow/invalidate`.
//!
//! ## Scenarios
//!
//! ### Scenario: invalidate a cached flow returns result=true
//! - **Given** a flow is loaded in the cache
//! - **When** POST /ruleforge/flow/invalidate?flowId=tiny
//! - **Then** response 200 with `{"result": true, "flowId": "tiny"}`
//!
//! ### Scenario: invalidate a non-cached flow returns result=false
//! - **Given** the cache has no `flowId`
//! - **When** POST /ruleforge/flow/invalidate?flowId=ghost
//! - **Then** response 200 with `{"result": false, "flowId": "ghost"}`

use std::sync::Arc;

use axum::body::{to_bytes, Body};
use axum::http::{Request, StatusCode};
use axum::routing::{get, post};
use axum::Router;
use rf_executor::dispatch::ExecutorRegistry;
use rf_http::flow_def_repo::{FlowDefinitionRepo, StubFlowLoader};
use rf_http::routes::evaluate::evaluate;
use rf_http::routes::invalidate::invalidate;
use rf_http::state::AppState;
use serde_json::{json, Value};
use tower::ServiceExt;

const TINY_BPMN: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  targetNamespace="http://ruleforge.com/schema">
  <bpmn:process id="tiny">
    <bpmn:startEvent id="s"/>
    <bpmn:sequenceFlow id="e" sourceRef="s" targetRef="end"/>
    <bpmn:endEvent id="end"/>
  </bpmn:process>
</bpmn:definitions>"#;

fn build_state() -> AppState {
    let loader = Arc::new(StubFlowLoader::with_flow("tiny", TINY_BPMN));
    let repo = Arc::new(FlowDefinitionRepo::new(loader));
    let registry = Arc::new(ExecutorRegistry::default());
    AppState::new(repo, registry, "test-worker", "http://localhost:8180", "")
}

fn build_router(state: AppState) -> Router {
    Router::new()
        .route("/ruleforge/evaluate", post(evaluate))
        .route("/ruleforge/flow/invalidate", post(invalidate))
        .route("/health", get(rf_http::routes::health::health))
        .with_state(state)
}

async fn body_json(resp: axum::response::Response) -> Value {
    let body = to_bytes(resp.into_body(), 1024 * 1024).await.unwrap();
    serde_json::from_slice(&body).unwrap()
}

#[tokio::test]
async fn given_cached_flow_when_invalidate_then_result_true() {
    let state = build_state();
    let app = build_router(state.clone());

    // First, warm the cache via /evaluate.
    let _ = app
        .clone()
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/evaluate")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({"flow_id": "tiny"})).unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(state.repo.cache_size(), 1);

    // Now invalidate.
    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/flow/invalidate?flowId=tiny")
                .header("content-type", "application/json")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let body = body_json(resp).await;
    assert_eq!(body["result"], json!(true));
    assert_eq!(body["flowId"], json!("tiny"));
    assert_eq!(state.repo.cache_size(), 0);
}

#[tokio::test]
async fn given_uncached_flow_when_invalidate_then_result_false() {
    let state = build_state();
    let app = build_router(state);
    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/flow/invalidate?flowId=ghost")
                .header("content-type", "application/json")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let body = body_json(resp).await;
    assert_eq!(body["result"], json!(false));
    assert_eq!(body["flowId"], json!("ghost"));
}
