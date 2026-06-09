//! BDD integration tests for `PgInflightStore` exercised through the
//! HTTP layer.
//!
//! ## Scenarios
//!
//! ### Scenario: suspending a flow writes a WAITING_CALLBACK row
//! - **Given** a flow `START → userTask(decisionField=approve) → ...`
//! - **When** POST /ruleforge/evaluate
//! - **Then** response.result = PENDING
//! - **And** pg `rust_decision_flow_state` has a row with
//!   `status=WAITING_CALLBACK`, `wait_type=USER_TASK`,
//!   `wait_ref="approve"`
//!
//! ### Scenario: resuming via /flow/decision marks the row COMPLETED
//! - **Given** a flow is suspended in pg (status=WAITING_CALLBACK)
//! - **When** POST /ruleforge/flow/decision?flowRunId=...&decision=yes
//! - **Then** response.result = COMPLETED
//! - **And** pg row status=COMPLETED
//! - **And** inflight.len() == 0 (no more WAITING_CALLBACK rows)
//!
//! ### Scenario: simulating a worker restart reads the suspended row back
//! - **Given** a row in pg with status=WAITING_CALLBACK
//! - **And** a fresh `PgInflightStore` (cold `MemInflightStore` empty)
//! - **When** POST /ruleforge/flow/decision?flowRunId=...&decision=yes
//! - **Then** the cold store finds the row via pg
//! - **And** resume completes
//!
//! The tests are gated on a live `PG_URL` (default
//! `postgres://ruleforge:ruleforge@127.0.0.1:5433/ruleforge_rust`).
//! In CI / sandboxed envs without pg they print a message and pass
//! through (the unit tests in `decision_test.rs` cover the
//! in-memory path).

use std::sync::Arc;

use axum::body::{to_bytes, Body};
use axum::http::{Request, StatusCode};
use axum::routing::post;
use axum::Router;
use rf_executor::dispatch::ExecutorRegistry;
use rf_http::flow_def_repo::{FlowDefinitionRepo, StubFlowLoader};
use rf_http::inflight::PgInflightStore;
use rf_http::routes::decision::decide;
use rf_http::routes::evaluate::evaluate;
use rf_http::state::AppState;
use rf_state::migrate;
use rf_state::persistence::PgStateStore;
use rf_state::state_row::FlowStatus;
use serde_json::{json, Value};
use sqlx::postgres::PgPoolOptions;
use sqlx::PgPool;
use std::time::Duration;
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

async fn try_pool() -> Option<PgPool> {
    let url = std::env::var("PG_URL")
        .unwrap_or_else(|_| "postgres://ruleforge:ruleforge@127.0.0.1:5433/ruleforge_rust".into());
    let pool = PgPoolOptions::new()
        .max_connections(4)
        .acquire_timeout(Duration::from_secs(2))
        .connect(&url)
        .await
        .ok()?;
    Some(pool)
}

async fn fresh_table(pool: &PgPool) {
    sqlx::query("TRUNCATE TABLE rust_decision_flow_state RESTART IDENTITY")
        .execute(pool)
        .await
        .expect("truncate");
}

async fn body_json(resp: axum::response::Response) -> Value {
    let body = to_bytes(resp.into_body(), 1024 * 1024).await.unwrap();
    serde_json::from_slice(&body).unwrap()
}

/// Spin up the full app with a `PgInflightStore`. Skips if pg is down.
async fn try_app() -> Option<(AppState, Router, PgPool)> {
    let pool = try_pool().await?;
    migrate(&pool).await.expect("migrate");
    fresh_table(&pool).await;

    let loader = Arc::new(StubFlowLoader::with_flow("user_task_flow", USER_TASK_BPMN));
    let repo = Arc::new(FlowDefinitionRepo::new(loader));
    let registry = Arc::new(ExecutorRegistry::with_rule_engine(Arc::new(
        rf_rule::mock::MockRuleEngine,
    )));
    let state_store = Arc::new(PgStateStore::new(pool.clone()));
    let pg_inflight = Arc::new(PgInflightStore::new(
        Arc::clone(&state_store),
        Arc::clone(&repo),
    ));
    let state = AppState::with_inflight(
        Arc::clone(&repo),
        Arc::clone(&registry),
        pg_inflight,
        "test-worker",
        "http://localhost:8180",
        "postgres://...",
    );
    let app = Router::new()
        .route("/ruleforge/evaluate", post(evaluate))
        .route("/ruleforge/flow/decision", post(decide))
        .with_state(state.clone());
    Some((state, app, pool))
}

async fn suspend(app: &Router) -> String {
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
    assert_eq!(body["result"], "PENDING");
    body["flow_run_id"].as_str().unwrap().to_string()
}

#[tokio::test]
async fn given_user_task_flow_when_evaluate_then_pg_row_suspended() {
    let Some((state, app, pool)) = try_app().await else {
        eprintln!("PG_URL not reachable, skipping");
        return;
    };
    let flow_run_id = suspend(&app).await;
    let row = sqlx::query_as::<_, rf_state::state_row::DecisionFlowState>(
        "SELECT id, flow_id, flow_run_id, user_id, order_no, status,
                current_node_id, current_node_type, next_retry_at,
                wait_ref, wait_type, flow_xml_version, row_vars,
                row_entity_snapshot, output_model, progress,
                error_message, locked_by, locked_at, locked_until,
                retry_count, total_execution_ms, fireable_rules,
                matched_rules, create_time, update_time
         FROM rust_decision_flow_state WHERE flow_run_id = $1",
    )
    .bind(&flow_run_id)
    .fetch_one(&pool)
    .await
    .expect("row exists");
    assert_eq!(row.status, FlowStatus::WaitingCallback);
    assert_eq!(row.flow_id, "user_task_flow");
    assert_eq!(row.wait_ref.as_deref(), Some("approve"));
    assert_eq!(row.wait_type, Some(rf_state::state_row::WaitType::UserTask));
    assert!(state.inflight.len().await >= 1);
}

#[tokio::test]
async fn given_suspended_pg_row_when_decide_then_row_marks_completed() {
    let Some((state, app, pool)) = try_app().await else {
        return;
    };
    let flow_run_id = suspend(&app).await;
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
    let row = sqlx::query_as::<_, rf_state::state_row::DecisionFlowState>(
        "SELECT id, flow_id, flow_run_id, user_id, order_no, status,
                current_node_id, current_node_type, next_retry_at,
                wait_ref, wait_type, flow_xml_version, row_vars,
                row_entity_snapshot, output_model, progress,
                error_message, locked_by, locked_at, locked_until,
                retry_count, total_execution_ms, fireable_rules,
                matched_rules, create_time, update_time
         FROM rust_decision_flow_state WHERE flow_run_id = $1",
    )
    .bind(&flow_run_id)
    .fetch_one(&pool)
    .await
    .expect("row exists");
    assert_eq!(row.status, FlowStatus::Completed);
    // No WAITING_CALLBACK rows left for THIS flow_run_id. (A
    // parallel test may have its own suspended row, so we don't
    // assert on the global count — only on the row we just drove.)
    let still_suspended: bool = sqlx::query_scalar(
        "SELECT EXISTS(SELECT 1 FROM rust_decision_flow_state
                       WHERE flow_run_id = $1 AND status = 'WAITING_CALLBACK')",
    )
    .bind(&flow_run_id)
    .fetch_one(&pool)
    .await
    .unwrap();
    assert!(
        !still_suspended,
        "this flow_run_id should not be WAITING_CALLBACK"
    );
    let _ = state; // appease unused warning
}

#[tokio::test]
async fn given_suspended_pg_row_when_fresh_store_then_resume_still_finds_it() {
    // Simulates a worker restart: row is in pg, but the in-memory
    // store on this process is empty. The fresh `PgInflightStore`
    // must re-hydrate the suspended run from pg.
    let Some((_, app, pool)) = try_app().await else {
        return;
    };
    let flow_run_id = suspend(&app).await;
    // Drop the app + state (simulating process exit). Build a fresh
    // router with a brand-new `PgInflightStore` backed by the same
    // pg DB.
    drop(app);
    let loader = Arc::new(StubFlowLoader::with_flow("user_task_flow", USER_TASK_BPMN));
    let repo = Arc::new(FlowDefinitionRepo::new(loader));
    let registry = Arc::new(ExecutorRegistry::with_rule_engine(Arc::new(
        rf_rule::mock::MockRuleEngine,
    )));
    let state_store = Arc::new(PgStateStore::new(pool));
    let pg_inflight = Arc::new(PgInflightStore::new(
        Arc::clone(&state_store),
        Arc::clone(&repo),
    ));
    let state = AppState::with_inflight(
        Arc::clone(&repo),
        Arc::clone(&registry),
        pg_inflight,
        "fresh-worker",
        "http://localhost:8180",
        "postgres://...",
    );
    let fresh_app = Router::new()
        .route("/ruleforge/flow/decision", post(decide))
        .with_state(state);
    let resp = fresh_app
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
