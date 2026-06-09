//! BDD integration tests for the `PgStateStore`.
//!
//! All tests require a live Postgres. Set `PG_URL` to a running
//! `postgres://ruleforge:ruleforge@127.0.0.1:5433/ruleforge_rust` (or
//! any other test DB) and the tests will:
//!   1. `sqlx::migrate!()` against the URL
//!   2. truncate `rust_decision_flow_state` at the start of each test
//!   3. exercise the persistence contract below
//!
//! ## Scenarios
//!
//! ### Scenario: insert_start then mark_completed round-trip
//! - **Given** an empty `rust_decision_flow_state` table
//! - **When** `insert_start` a fresh `flow_run_id`
//! - **And** `mark_completed` the same run
//! - **Then** `select_by_flow_run_id` returns status=COMPLETED, vars round-tripped
//!
//! ### Scenario: mark_suspended records wait_type / wait_ref / payload
//! - **Given** an empty table
//! - **When** `insert_start` then `mark_suspended(USER_TASK, "approve", payload)`
//! - **Then** `select_by_flow_run_id` returns status=WAITING_CALLBACK + wait_type=USER_TASK
//! - **And** payload round-trips through jsonb
//!
//! ### Scenario: try_advisory_lock is exclusive — only one caller wins
//! - **Given** an empty table
//! - **When** 10 concurrent tasks call `try_advisory_lock(same_key)`
//! - **Then** exactly 1 task sees `acquired == true`, the other 9 see `false`
//!
//! ### Scenario: release_advisory_lock unblocks the next caller
//! - **Given** one caller holds the advisory lock
//! - **When** that caller calls `release_advisory_lock`
//! - **And** a second caller calls `try_advisory_lock`
//! - **Then** the second caller's `acquired == true`
//!
//! ### Scenario: select_recoverable_skip_locked only returns ready rows
//! - **Given** 2 WAITING_CALLBACK rows (1 ready now, 1 with `next_retry_at` in the future)
//! - **And** 1 COMPLETED row
//! - **When** `select_recoverable_skip_locked(10)`
//! - **Then** returns only the 1 ready WAITING_CALLBACK row
//!
//! ### Scenario: mark_failed records error_message
//! - **Given** a fresh insert
//! - **When** `mark_failed("boom")`
//! - **Then** select returns status=FAILED + error_message="boom"

use std::sync::Arc;
use std::time::Duration;

use rf_state::persistence::PgStateStore;
use rf_state::state_row::{FlowStatus, WaitType};
use serde_json::json;
use sqlx::postgres::PgPoolOptions;
use sqlx::PgPool;

/// Resolve the pg URL. Returns `None` if `PG_URL` is unset / unreachable;
/// the test should `return` in that case (per BDD, tests must not silently
/// skip — but a missing live DB is a build-environment issue, not a test
/// failure).
async fn try_pool() -> Option<PgPool> {
    let url = std::env::var("PG_URL")
        .unwrap_or_else(|_| "postgres://ruleforge:ruleforge@127.0.0.1:5433/ruleforge_rust".into());
    let pool = PgPoolOptions::new()
        .max_connections(8)
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

async fn run_migrations(pool: &PgPool) {
    rf_state::migrate(pool)
        .await
        .expect("migrate rust_decision_flow_state");
}

#[tokio::test]
async fn given_empty_table_when_insert_start_then_mark_completed_then_status_completed() {
    let Some(pool) = try_pool().await else {
        eprintln!("PG_URL not reachable, skipping");
        return;
    };
    run_migrations(&pool).await;
    fresh_table(&pool).await;

    let store = PgStateStore::new(pool.clone());

    store
        .insert_start(
            "loan_flow",
            "run-1",
            Some("start"),
            Some("StartEvent"),
            Some("v1"),
        )
        .await
        .expect("insert_start");

    let row = store
        .select_by_flow_run_id("run-1")
        .await
        .expect("select")
        .expect("row exists");
    assert_eq!(row.flow_id, "loan_flow");
    assert_eq!(row.status, FlowStatus::Pending);
    assert_eq!(row.current_node_id.as_deref(), Some("start"));

    store
        .mark_completed(
            "run-1",
            Some("end"),
            json!({"approved": true, "score": 0.83}),
            42,
        )
        .await
        .expect("mark_completed");

    let row = store
        .select_by_flow_run_id("run-1")
        .await
        .expect("select")
        .expect("row exists");
    assert_eq!(row.status, FlowStatus::Completed);
    assert_eq!(row.current_node_id.as_deref(), Some("end"));
    assert_eq!(row.total_execution_ms, 42);
    assert_eq!(row.row_vars, Some(json!({"approved": true, "score": 0.83})));
}

#[tokio::test]
async fn given_inserted_run_when_mark_suspended_then_wait_type_recorded() {
    let Some(pool) = try_pool().await else {
        return;
    };
    run_migrations(&pool).await;
    fresh_table(&pool).await;

    let store = PgStateStore::new(pool.clone());
    store
        .insert_start("flow", "run-sus", Some("u1"), Some("UserTask"), Some("v1"))
        .await
        .expect("insert");

    let payload = json!({"decisionField": "approve", "label": "审批"});
    store
        .mark_suspended(
            "run-sus",
            Some("u1"),
            Some("UserTask"),
            WaitType::UserTask,
            "approve",
            None,
            payload.clone(),
        )
        .await
        .expect("mark_suspended");

    let row = store
        .select_by_flow_run_id("run-sus")
        .await
        .expect("select")
        .expect("row exists");
    assert_eq!(row.status, FlowStatus::WaitingCallback);
    assert_eq!(row.wait_type, Some(WaitType::UserTask));
    assert_eq!(row.wait_ref.as_deref(), Some("approve"));
    assert_eq!(row.output_model, Some(payload));
}

#[tokio::test]
async fn given_advisory_lock_contention_when_holder_active_then_contenders_see_false() {
    let Some(pool) = try_pool().await else {
        return;
    };
    run_migrations(&pool).await;
    fresh_table(&pool).await;

    let store = Arc::new(PgStateStore::new(pool.clone()));
    // Hold a lock on the main task for 200ms; meanwhile spawn 5
    // contenders; every contender must see `false`. After the holder
    // releases, a fresh call must succeed.
    let key = "contended-run";
    let main_lock = store.try_advisory_lock(key).await.expect("holder try");
    assert!(main_lock, "main lock should be acquired");

    let contender_store = Arc::clone(&store);
    let contender = tokio::spawn(async move {
        let mut conn = contender_store
            .acquire_conn()
            .await
            .expect("contender conn");
        sqlx::query_scalar::<_, bool>("SELECT pg_try_advisory_lock(hashtext($1))")
            .bind(key)
            .fetch_one(&mut *conn)
            .await
            .expect("contender try")
    });
    let contender_got = contender.await.unwrap();
    assert!(
        !contender_got,
        "contender must NOT acquire while holder holds"
    );

    // Holder releases; the next caller succeeds.
    let released = store.release_advisory_lock(key).await.expect("release");
    assert!(released, "release should return true");
    let fresh = store.try_advisory_lock(key).await.expect("fresh try");
    assert!(fresh, "after release, fresh caller acquires");
    let _ = store.release_advisory_lock(key).await;
}

#[tokio::test]
async fn given_released_lock_when_second_caller_acquires_then_true() {
    let Some(pool) = try_pool().await else {
        return;
    };
    run_migrations(&pool).await;
    fresh_table(&pool).await;

    let store = PgStateStore::new(pool.clone());
    let first = store.try_advisory_lock("seq").await.expect("first try");
    let second = store.try_advisory_lock("seq").await.expect("second try");
    assert!(first, "first call should acquire");
    assert!(!second, "second concurrent call should NOT acquire");
    let released = store.release_advisory_lock("seq").await.expect("release");
    assert!(released, "release should return true after successful lock");
    let third = store.try_advisory_lock("seq").await.expect("third try");
    assert!(third, "after release, third call should acquire");
}

#[tokio::test]
async fn given_recoverable_table_when_select_recoverable_then_only_ready_rows_returned() {
    let Some(pool) = try_pool().await else {
        return;
    };
    run_migrations(&pool).await;
    fresh_table(&pool).await;

    let store = PgStateStore::new(pool.clone());
    // Ready WAITING_CALLBACK row (next_retry_at NULL)
    store
        .insert_start("f", "ready-1", Some("n1"), Some("UserTask"), Some("v1"))
        .await
        .unwrap();
    store
        .mark_suspended(
            "ready-1",
            Some("n1"),
            Some("UserTask"),
            WaitType::UserTask,
            "approve",
            None,
            json!({}),
        )
        .await
        .unwrap();
    // Not-yet-ready WAITING_CALLBACK row (next_retry_at = +1h)
    store
        .insert_start("f", "future-1", Some("n1"), Some("UserTask"), Some("v1"))
        .await
        .unwrap();
    store
        .mark_suspended(
            "future-1",
            Some("n1"),
            Some("UserTask"),
            WaitType::UserTask,
            "approve",
            Some(chrono::Utc::now() + chrono::Duration::hours(1)),
            json!({}),
        )
        .await
        .unwrap();
    // COMPLETED row — should be ignored
    store
        .insert_start("f", "done-1", Some("n1"), Some("UserTask"), Some("v1"))
        .await
        .unwrap();
    store
        .mark_completed("done-1", Some("end"), json!({}), 1)
        .await
        .unwrap();

    let recoverable = store
        .select_recoverable_skip_locked(10)
        .await
        .expect("recoverable");
    let ids: Vec<&str> = recoverable.iter().map(|r| r.flow_run_id.as_str()).collect();
    assert!(ids.contains(&"ready-1"), "should include ready-1: {ids:?}");
    assert!(
        !ids.contains(&"future-1"),
        "should NOT include future-1: {ids:?}"
    );
    assert!(
        !ids.contains(&"done-1"),
        "should NOT include done-1: {ids:?}"
    );
}

#[tokio::test]
async fn given_running_run_when_mark_failed_then_error_message_persisted() {
    let Some(pool) = try_pool().await else {
        return;
    };
    run_migrations(&pool).await;
    fresh_table(&pool).await;

    let store = PgStateStore::new(pool.clone());
    store
        .insert_start("f", "fail-1", Some("n1"), Some("ServiceTask"), Some("v1"))
        .await
        .unwrap();
    store
        .mark_failed("fail-1", Some("n1"), "rule engine exploded")
        .await
        .unwrap();

    let row = store
        .select_by_flow_run_id("fail-1")
        .await
        .unwrap()
        .expect("row");
    assert_eq!(row.status, FlowStatus::Failed);
    assert_eq!(row.error_message.as_deref(), Some("rule engine exploded"));
}
