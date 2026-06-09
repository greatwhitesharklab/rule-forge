//! BDD integration tests for `POST /ruleforge/evaluate`.
//!
//! ## Scenarios
//!
//! ### Scenario: tiny start-end flow returns COMPLETED
//! - **Given** a stub flow loader returns `START → END` BPMN
//! - **When** POST /ruleforge/evaluate
//! - **Then** response 200 with `{"result": "COMPLETED", "vars": {...}}`
//!
//! ### Scenario: rule serviceTask with mock engine populates vars
//! - **Given** a flow `START → rule → END`
//! - **And** request includes `vars.applicant = {age: 25, income: 12000}`
//! - **When** POST /ruleforge/evaluate
//! - **Then** response.vars.approved is true
//! - **And** response.vars.creditLimit > 5000
//!
//! ### Scenario: userTask flow returns PENDING
//! - **Given** a flow with a userTask step
//! - **When** POST /ruleforge/evaluate
//! - **Then** response.result is "PENDING"
//! - **And** response.waitRef is the userTask's decisionField
//!
//! ### Scenario: unknown flow returns 404
//! - **Given** the stub loader has no `flow_id`
//! - **When** POST /ruleforge/evaluate
//! - **Then** response 404

use std::sync::Arc;

use axum::body::{to_bytes, Body};
use axum::http::{Request, StatusCode};
use rf_executor::dispatch::ExecutorRegistry;
use rf_http::flow_def_repo::{FlowDefinitionRepo, StubFlowLoader};
use rf_http::routes::evaluate::evaluate;
use rf_http::state::AppState;
use serde_json::{json, Value};
use tower::ServiceExt;

fn tiny_bpmn() -> &'static str {
    r#"<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:ruleforge="http://ruleforge.com/schema"
                  targetNamespace="http://ruleforge.com/schema">
  <bpmn:process id="tiny">
    <bpmn:startEvent id="s"/>
    <bpmn:sequenceFlow id="e" sourceRef="s" targetRef="end"/>
    <bpmn:endEvent id="end"/>
  </bpmn:process>
</bpmn:definitions>"#
}

fn rule_bpmn() -> &'static str {
    r#"<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:ruleforge="http://ruleforge.com/schema"
                  targetNamespace="http://ruleforge.com/schema">
  <bpmn:process id="rule_flow">
    <bpmn:startEvent id="s"><bpmn:outgoing>e1</bpmn:outgoing></bpmn:startEvent>
    <bpmn:serviceTask id="rule" ruleforge:taskType="rule">
      <bpmn:outgoing>e2</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="rule"/>
    <bpmn:sequenceFlow id="e2" sourceRef="rule" targetRef="end"/>
    <bpmn:endEvent id="end"/>
  </bpmn:process>
</bpmn:definitions>"#
}

fn user_task_bpmn() -> &'static str {
    r#"<?xml version="1.0" encoding="UTF-8"?>
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
</bpmn:definitions>"#
}

fn build_state(loader: StubFlowLoader) -> AppState {
    let repo = Arc::new(FlowDefinitionRepo::new(Arc::new(loader)));
    let registry = Arc::new(ExecutorRegistry::with_rule_engine(Arc::new(
        rf_rule::mock::MockRuleEngine,
    )));
    AppState::new(repo, registry, "test-worker", "http://localhost:8180", "")
}

fn build_router(state: AppState) -> axum::Router {
    axum::Router::new()
        .route("/ruleforge/evaluate", axum::routing::post(evaluate))
        .with_state(state)
}

async fn body_json(resp: axum::response::Response) -> Value {
    let body = to_bytes(resp.into_body(), 1024 * 1024).await.unwrap();
    serde_json::from_slice(&body).unwrap()
}

#[tokio::test]
async fn given_tiny_flow_when_evaluate_then_completed() {
    let state = build_state(StubFlowLoader::with_flow("tiny", tiny_bpmn()));
    let app = build_router(state);
    let resp = app
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
    assert_eq!(resp.status(), StatusCode::OK);
    let body = body_json(resp).await;
    assert_eq!(body["result"], "COMPLETED");
    assert!(body["flow_run_id"].is_string());
    assert!(body["vars"].is_object());
}

#[tokio::test]
async fn given_rule_flow_when_evaluate_then_vars_populated() {
    let state = build_state(StubFlowLoader::with_flow("rule_flow", rule_bpmn()));
    let app = build_router(state);
    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/evaluate")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({
                        "flow_id": "rule_flow",
                        "applicant": {"age": 25, "income": 12_000}
                    }))
                    .unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::OK);
    let body = body_json(resp).await;
    assert_eq!(body["result"], "COMPLETED");
    assert_eq!(body["vars"]["approved"], json!(true));
    let limit = body["vars"]["creditLimit"].as_i64().unwrap();
    assert!(limit > 5000, "creditLimit={limit}");
}

#[tokio::test]
async fn given_user_task_flow_when_evaluate_then_pending() {
    let state = build_state(StubFlowLoader::with_flow(
        "user_task_flow",
        user_task_bpmn(),
    ));
    let app = build_router(state);
    let resp = app
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
    assert_eq!(body["result"], "PENDING");
    assert_eq!(body["wait_ref"], "approve");
    assert!(body["payload"]["node_id"] == "u1");
}

#[tokio::test]
async fn given_unknown_flow_when_evaluate_then_404() {
    let state = build_state(StubFlowLoader::new());
    let app = build_router(state);
    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/evaluate")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({"flow_id": "missing"})).unwrap(),
                ))
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(resp.status(), StatusCode::NOT_FOUND);
}
