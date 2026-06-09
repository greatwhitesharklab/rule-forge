//! BDD integration tests for `POST /ruleforge/flow/decision`.
//!
//! ## Scenarios
//!
//! ### Scenario: resume a suspended userTask flow with "yes"
//! - **Given** a flow is suspended at userTask(decisionField=approve)
//! - **When** POST /ruleforge/flow/decision?flowRunId=...&decision=yes
//! - **Then** response 200 with `{"result": "COMPLETED", "vars": {...}}`
//! - **And** `vars.approve == "yes"`
//!
//! ### Scenario: resume a suspended userTask flow with "no"
//! - **Given** a flow is suspended at userTask
//! - **When** POST /ruleforge/flow/decision?flowRunId=...&decision=no
//! - **Then** response 200 with `{"result": "COMPLETED", "vars": {...}}`
//! - **And** `vars.approve == "no"`
//!
//! ### Scenario: unknown flowRunId returns 404
//! - **Given** the inflight store has no entry
//! - **When** POST /ruleforge/flow/decision?flowRunId=ghost&decision=yes
//! - **Then** response 404

use std::sync::Arc;

use axum::body::{to_bytes, Body};
use axum::http::{Request, StatusCode};
use axum::routing::post;
use axum::Router;
use rf_executor::dispatch::ExecutorRegistry;
use rf_http::flow_def_repo::{FlowDefinitionRepo, StubFlowLoader};
use rf_http::inflight::{InflightFlow, InflightStore, MemInflightStore};
use rf_http::routes::decision::decide;
use rf_http::routes::evaluate::evaluate;
use rf_http::state::AppState;
use serde_json::{json, Value};
use tower::ServiceExt;

const USER_TASK_BPMN: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:ruleforge="http://ruleforge.com/schema"
                  targetNamespace="http://ruleforge.com/schema">
  <bpmn:process id="user_task_flow">
    <bpmn:startEvent id="s"><bpmn:outgoing>e1</bpmn:outgoing></bpmn:startEvent>
    <bpmn:userTask id="u1" ruleforge:decisionField="approve">
      <bpmn:outgoing>e_yes</bpmn:outgoing>
      <bpmn:outgoing>e_no</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="u1"/>
    <bpmn:sequenceFlow id="e_yes" sourceRef="u1" targetRef="end_yes"
                       ruleforge:decisionValue="yes"/>
    <bpmn:sequenceFlow id="e_no" sourceRef="u1" targetRef="end_no"
                       ruleforge:decisionValue="no"/>
    <bpmn:endEvent id="end_yes"/>
    <bpmn:endEvent id="end_no"/>
  </bpmn:process>
</bpmn:definitions>"#;

fn build_state() -> AppState {
    let loader = Arc::new(StubFlowLoader::with_flow("user_task_flow", USER_TASK_BPMN));
    let repo = Arc::new(FlowDefinitionRepo::new(loader));
    let registry = Arc::new(ExecutorRegistry::with_rule_engine(Arc::new(
        rf_rule::mock::MockRuleEngine,
    )));
    AppState::new(repo, registry, "test-worker", "http://localhost:8180", "")
}

fn build_router(state: AppState) -> Router {
    Router::new()
        .route("/ruleforge/evaluate", post(evaluate))
        .route("/ruleforge/flow/decision", post(decide))
        .with_state(state)
}

async fn body_json(resp: axum::response::Response) -> Value {
    let body = to_bytes(resp.into_body(), 1024 * 1024).await.unwrap();
    serde_json::from_slice(&body).unwrap()
}

/// Drive a flow to suspension so we have an entry in the inflight
/// store, then return the flow_run_id.
async fn suspend_a_flow(app: &Router) -> String {
    let resp = app
        .clone()
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/evaluate")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({"flow_id": "user_task_flow"})).unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let body = body_json(resp).await;
    body["flow_run_id"].as_str().unwrap().to_string()
}

#[tokio::test]
async fn given_suspended_flow_when_decide_yes_then_completed() {
    let state = build_state();
    let app = build_router(state.clone());
    let flow_run_id = suspend_a_flow(&app).await;

    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri(format!(
                    "/ruleforge/flow/decision?flowRunId={flow_run_id}&decision=yes"
                ))
                .header("content-type", "application/json")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let body = body_json(resp).await;
    assert_eq!(body["result"], "COMPLETED");
    assert_eq!(body["vars"]["approve"], json!("yes"));
    // inflight should be cleared on completion
    assert_eq!(state.inflight.len().await, 0);
}

#[tokio::test]
async fn given_suspended_flow_when_decide_no_then_completed() {
    let state = build_state();
    let app = build_router(state.clone());
    let flow_run_id = suspend_a_flow(&app).await;

    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri(format!(
                    "/ruleforge/flow/decision?flowRunId={flow_run_id}&decision=no"
                ))
                .header("content-type", "application/json")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let body = body_json(resp).await;
    assert_eq!(body["result"], "COMPLETED");
    assert_eq!(body["vars"]["approve"], json!("no"));
}

#[tokio::test]
async fn given_unknown_flow_run_id_when_decide_then_404() {
    let state = build_state();
    let app = build_router(state);
    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/flow/decision?flowRunId=ghost&decision=yes")
                .header("content-type", "application/json")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::NOT_FOUND);
}

// Exercise the InflightStore API directly (the HTTP /decision path
// exercises it too, but a unit test on the API is cheap insurance
// against future refactors).
#[tokio::test]
async fn inflight_store_put_get_remove() {
    let store = MemInflightStore::new();
    assert_eq!(store.len().await, 0);
    store
        .put(
            "r".to_string(),
            InflightFlow {
                def: Arc::new(
                    rf_parse::bpmn_parser::BpmnXmlParser::parse(
                        r#"<?xml version="1.0"?>
                    <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                      targetNamespace="http://x">
                      <bpmn:process id="p">
                        <bpmn:startEvent id="s"/>
                        <bpmn:endEvent id="e"/>
                      </bpmn:process>
                    </bpmn:definitions>"#,
                    )
                    .unwrap(),
                ),
                ctx: rf_executor::flow_context::FlowContext::new("r"),
                suspend_info: None,
            },
        )
        .await;
    assert_eq!(store.len().await, 1);
    assert!(store.get("r").await.is_some());
    assert!(store.remove("r").await.is_some());
    assert_eq!(store.len().await, 0);
}
