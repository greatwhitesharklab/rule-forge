//! BDD integration tests for `/flow/event` over the
//! `PgInflightStore`.
//!
//! Mirrors `inflight_pg_test.rs` (which covers `/flow/decision` + the
//! userTask-suspend path) for the IntermediateEvent path. The pg
//! round-trip is what makes V5.26 production-real: a worker restart
//! must not lose a flow that's waiting for an external event.
//!
//! ## Scenarios
//!
//! ### Scenario: IE flow suspends with IntermediateEvent node_type
//! - **Given** a flow `START → intermediateCatchEvent(message, "approval") → END`
//! - **When** POST /ruleforge/evaluate
//! - **Then** pg row has
//!   - `current_node_type = "IntermediateEvent"` (NOT "UserTask" —
//!     the V5.26 P2 fix)
//!   - `wait_type = ASYNC_DATA`
//!   - `wait_ref = "approval"`
//!   - `status = WAITING_CALLBACK`
//!
//! ### Scenario: /flow/event resumes the row to COMPLETED
//! - **Given** row in WAITING_CALLBACK with wait_type=ASYNC_DATA
//! - **When** POST /ruleforge/flow/event
//! - **Then** row.status=COMPLETED, vars contain the payload
//!
//! ### Scenario: a fresh worker resumes from pg (cold cache)
//! - **Given** row in pg, in-memory inflight empty
//! - **When** POST /flow/event from a fresh router/process
//! - **Then** resume completes (pg row is the source of truth)
//!
//! ### Scenario: recovery sweep skips AsyncData rows
//! - **Given** a row in WAITING_CALLBACK with wait_type=ASYNC_DATA,
//!   next_retry_at=NULL
//! - **When** recovery sweep runs
//! - **Then** the row is NOT picked up (filter excludes ASYNC_DATA)
//!
//! Skipped when pg is unreachable (mirrors inflight_pg_test.rs).

use std::sync::Arc;
use std::time::Duration;

use axum::body::{to_bytes, Body};
use axum::http::{Request, StatusCode};
use axum::routing::post;
use axum::Router;
use rf_executor::dispatch::ExecutorRegistry;
use rf_http::flow_def_repo::{FlowDefinitionRepo, StubFlowLoader};
use rf_http::inflight::PgInflightStore;
use rf_http::routes::evaluate::evaluate;
use rf_http::routes::event::deliver;
use rf_http::state::AppState;
use rf_state::migrate;
use rf_state::persistence::PgStateStore;
use rf_state::state_row::{DecisionFlowState, FlowStatus, WaitType};
use serde_json::{json, Value};
use sqlx::postgres::PgPoolOptions;
use sqlx::PgPool;
use tower::ServiceExt;

const MESSAGE_CATCH_BPMN: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:ruleforge="http://ruleforge.com/schema"
                  targetNamespace="http://ruleforge.com/schema">
  <bpmn:process id="ie_message_flow">
    <bpmn:startEvent id="s"><bpmn:outgoing>e1</bpmn:outgoing></bpmn:startEvent>
    <bpmn:intermediateCatchEvent id="ic" ruleforge:eventType="message"
                                 ruleforge:eventName="approval">
      <bpmn:outgoing>e2</bpmn:outgoing>
    </bpmn:intermediateCatchEvent>
    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="ic"/>
    <bpmn:sequenceFlow id="e2" sourceRef="ic" targetRef="end"/>
    <bpmn:endEvent id="end"/>
  </bpmn:process>
</bpmn:definitions>"#;

async fn try_pool() -> Option<PgPool> {
    let url = std::env::var("PG_URL")
        .unwrap_or_else(|_| "postgres://ruleforge:ruleforge@127.0.0.1:5433/ruleforge_rust".into());
    PgPoolOptions::new()
        .max_connections(4)
        .acquire_timeout(Duration::from_secs(2))
        .connect(&url)
        .await
        .ok()
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

async fn select_row(pool: &PgPool, flow_run_id: &str) -> DecisionFlowState {
    sqlx::query_as::<_, DecisionFlowState>(
        "SELECT id, flow_id, flow_run_id, user_id, order_no, status,
                current_node_id, current_node_type, next_retry_at,
                wait_ref, wait_type, flow_xml_version, row_vars,
                row_entity_snapshot, output_model, progress,
                error_message, locked_by, locked_at, locked_until,
                retry_count, total_execution_ms, fireable_rules,
                matched_rules, create_time, update_time
         FROM rust_decision_flow_state WHERE flow_run_id = $1",
    )
    .bind(flow_run_id)
    .fetch_one(pool)
    .await
    .expect("row exists")
}

fn build_app(pool: PgPool) -> (AppState, Router) {
    let loader = Arc::new(StubFlowLoader::with_flow("ie_message_flow", MESSAGE_CATCH_BPMN));
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
        "test-worker",
        "http://localhost:8180",
        "postgres://...",
    );
    let app = Router::new()
        .route("/ruleforge/evaluate", post(evaluate))
        .route("/ruleforge/flow/event", post(deliver))
        .with_state(state.clone());
    (state, app)
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
                    serde_json::to_vec(&json!({"flow_id": "ie_message_flow"})).unwrap(),
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
async fn given_ie_flow_when_evaluate_then_pg_row_has_intermediate_event_node_type() {
    let Some(pool) = try_pool().await else {
        eprintln!("PG_URL not reachable, skipping");
        return;
    };
    migrate(&pool).await.expect("migrate");
    fresh_table(&pool).await;
    let (_state, app) = build_app(pool.clone());

    let flow_run_id = suspend(&app).await;
    let row = select_row(&pool, &flow_run_id).await;

    assert_eq!(row.status, FlowStatus::WaitingCallback);
    assert_eq!(row.flow_id, "ie_message_flow");
    assert_eq!(row.wait_type, Some(WaitType::AsyncData));
    assert_eq!(row.wait_ref.as_deref(), Some("approval"));
    // The V5.26 P2 fix — was hardcoded "UserTask", should be the
    // actual BPMN element type for the suspending node.
    assert_eq!(
        row.current_node_type.as_deref(),
        Some("IntermediateEvent"),
        "expected current_node_type='IntermediateEvent', got {:?}",
        row.current_node_type
    );
    assert_eq!(row.current_node_id.as_deref(), Some("ic"));
}

#[tokio::test]
async fn given_ie_suspended_when_event_delivered_then_pg_row_marks_completed() {
    let Some(pool) = try_pool().await else {
        return;
    };
    migrate(&pool).await.expect("migrate");
    fresh_table(&pool).await;
    let (_state, app) = build_app(pool.clone());

    let flow_run_id = suspend(&app).await;

    let resp = app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/flow/event")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({
                        "flowRunId": flow_run_id,
                        "eventName": "approval",
                        "payload": { "approved": true, "reviewer": "alice" }
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
    assert_eq!(body["vars"]["approval"]["approved"], json!(true));

    let row = select_row(&pool, &flow_run_id).await;
    assert_eq!(row.status, FlowStatus::Completed);
    assert_eq!(
        row.current_node_type.as_deref(),
        Some("EndEvent"),
        "completion path should label the terminal node"
    );
    // The completion writes row_vars from the traverser.
    let vars = row.row_vars.expect("row_vars present on completion");
    assert_eq!(vars["approval"]["approved"], json!(true));
}

#[tokio::test]
async fn given_ie_pg_row_when_fresh_worker_resumes_via_event_then_completes() {
    // Simulates a worker restart: the row is in pg, but this
    // process's in-memory inflight is empty. The fresh
    // `PgInflightStore` must re-hydrate the suspended run from pg
    // and the /flow/event delivery must find it.
    let Some(pool) = try_pool().await else {
        return;
    };
    migrate(&pool).await.expect("migrate");
    fresh_table(&pool).await;
    let (_state, app) = build_app(pool.clone());

    let flow_run_id = suspend(&app).await;
    // Drop the original app (simulating process exit).
    drop(app);

    // Fresh router / worker / state — same pg DB, no in-memory cache.
    let (_state2, fresh_app) = build_app(pool.clone());
    let resp = fresh_app
        .oneshot(
            Request::builder()
                .method("POST")
                .uri("/ruleforge/flow/event")
                .header("content-type", "application/json")
                .body(Body::from(
                    serde_json::to_vec(&json!({
                        "flowRunId": flow_run_id,
                        "eventName": "approval",
                        "payload": { "approved": true }
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

    let row = select_row(&pool, &flow_run_id).await;
    assert_eq!(row.status, FlowStatus::Completed);
}

#[tokio::test]
async fn recovery_sweep_skips_async_data_rows() {
    // The V5.26 P2 fix: `select_recoverable_skip_locked` excludes
    // wait_type=ASYNC_DATA so message/signal catches aren't
    // re-driven by the time-based recovery loop. If we did pick
    // them up, they'd just re-suspend forever (busy loop).
    let Some(pool) = try_pool().await else {
        return;
    };
    migrate(&pool).await.expect("migrate");
    fresh_table(&pool).await;
    let state_store = PgStateStore::new(pool.clone());

    // Insert a row that LOOKS like a real IE suspension:
    // status=WAITING_CALLBACK, wait_type=ASYNC_DATA, next_retry_at=NULL
    sqlx::query(
        r#"INSERT INTO rust_decision_flow_state
              (flow_id, flow_run_id, status, current_node_id,
               current_node_type, wait_type, wait_ref, output_model)
           VALUES ($1, $2, 'WAITING_CALLBACK', $3, $4, $5, $6, $7)"#,
    )
    .bind("ie_message_flow")
    .bind("ghost-run-1")
    .bind("ic")
    .bind("IntermediateEvent")
    .bind(WaitType::AsyncData)
    .bind("approval")
    .bind(serde_json::json!({}))
    .execute(&pool)
    .await
    .expect("insert");

    let picked = state_store
        .select_recoverable_skip_locked(20)
        .await
        .expect("sweep");
    assert!(
        picked.iter().all(|r| r.flow_run_id != "ghost-run-1"),
        "recovery sweep must NOT pick up ASYNC_DATA rows; picked: {:?}",
        picked.iter().map(|r| &r.flow_run_id).collect::<Vec<_>>()
    );

    // And the userTask path still works (sanity check that the
    // filter didn't accidentally exclude everything).
    sqlx::query(
        r#"INSERT INTO rust_decision_flow_state
              (flow_id, flow_run_id, status, current_node_id,
               current_node_type, wait_type, wait_ref, output_model)
           VALUES ($1, $2, 'WAITING_CALLBACK', $3, $4, $5, $6, $7)"#,
    )
    .bind("user_task_flow")
    .bind("ghost-run-2")
    .bind("u1")
    .bind("UserTask")
    .bind(WaitType::UserTask)
    .bind("approve")
    .bind(serde_json::json!({}))
    .execute(&pool)
    .await
    .expect("insert userTask row");

    let picked = state_store
        .select_recoverable_skip_locked(20)
        .await
        .expect("sweep");
    assert!(
        picked.iter().any(|r| r.flow_run_id == "ghost-run-2"),
        "recovery sweep should still pick up USER_TASK rows"
    );
}
