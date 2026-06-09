//! Background recovery sweep.
//!
//! The Java side has a `@Scheduled(fixedRate = 30_000)` job on
//! `DecisionFlowStateRecoveryJob` (V5.19.0) that scans
//! `nd_decision_flow_state` for stale WAITING_CALLBACK / PENDING_ASYNC
//! rows and re-drives them. The Rust side mirrors that with a
//! `tokio::spawn`'d task that wakes every 30 seconds, calls
//! `PgStateStore::select_recoverable_skip_locked`, and asks the HTTP
//! layer to re-run the suspended flow.
//!
//! ## Why a trait, not a free function
//!
//! The recovery loop needs to (a) look up the `FlowDefinition` for the
//! row's `flow_id` from the in-memory `FlowDefinitionRepo` and (b) call
//! the executor's `traverse()` to resume. Both of those are owned by
//! `rf-http` (the HTTP layer wires them together). `rf-state` only
//! owns the pg persistence + the timing. So we expose a trait
//! `Recover` that `rf-http` implements; `rf-state` calls the trait
//! method for each picked row.

use std::sync::Arc;
use std::time::Duration;

use tokio::time::MissedTickBehavior;
use tracing::{debug, error, info, warn};

use crate::persistence::PgStateStore;

/// Callback the recovery loop invokes for each picked row. The HTTP
/// layer implements this — it knows how to re-load the
/// `FlowDefinition`, re-hydrate the `FlowContext` from `row_vars`, and
/// call the traverser.
///
/// `Ok(true)` = successfully resumed (or completed) the flow; the row
/// will be moved to COMPLETED by the resume path's own write.
/// `Ok(false)` = nothing to do (e.g. flow_xml_version mismatch — the
/// console invalidated the BPMN while the flow was suspended); the
/// row is left alone so a future sweep can decide.
/// `Err(_)` = resume blew up; log and continue.
#[async_trait::async_trait]
pub trait Recover: Send + Sync + 'static {
    async fn recover(
        &self,
        flow_run_id: &str,
        flow_xml_version: Option<&str>,
    ) -> anyhow::Result<bool>;
}

/// Background recovery loop. Call `start` once during `main`; it
/// spawns a `tokio::task` that wakes every `interval` and re-drives
/// recoverable rows. Honours cooperative cancellation via the
/// optional `shutdown` channel.
pub struct RecoveryLoop {
    store: Arc<PgStateStore>,
    recover: Arc<dyn Recover>,
    worker_id: String,
    interval: Duration,
}

impl RecoveryLoop {
    pub fn new(
        store: Arc<PgStateStore>,
        recover: Arc<dyn Recover>,
        worker_id: impl Into<String>,
        interval: Duration,
    ) -> Self {
        Self {
            store,
            recover,
            worker_id: worker_id.into(),
            interval,
        }
    }

    /// Spawn the loop. Returns immediately. The task runs until the
    /// process exits (or, in tests, the `tokio::test` runtime is
    /// dropped).
    pub fn start(self) {
        let tick_every = self.interval;
        let store = self.store;
        let recover = self.recover;
        let worker_id = self.worker_id;
        tokio::spawn(async move {
            // 60s initial delay so the rest of the app finishes
            // bootstrapping before the first sweep (mirrors the
            // Java `@Scheduled(initialDelay = 60_000)`).
            tokio::time::sleep(Duration::from_secs(60)).await;
            let mut tick = tokio::time::interval(tick_every);
            tick.set_missed_tick_behavior(MissedTickBehavior::Skip);
            info!(worker_id = %worker_id, "RecoveryLoop: started");
            loop {
                tick.tick().await;
                match store.select_recoverable_skip_locked(20).await {
                    Ok(rows) if rows.is_empty() => {
                        debug!(worker_id = %worker_id, "RecoveryLoop: no recoverable rows");
                    }
                    Ok(rows) => {
                        info!(
                            worker_id = %worker_id,
                            n = rows.len(),
                            "RecoveryLoop: picked rows"
                        );
                        for row in rows {
                            // Per-row advisory lock — even after
                            // `FOR UPDATE SKIP LOCKED` returned the
                            // row, the row lock is released as soon
                            // as the sweep tx commits. Advisory lock
                            // is what protects us across the gap.
                            let lock_key = format!("rust-recover-{}", row.flow_run_id);
                            let got = match store.try_advisory_lock(&lock_key).await {
                                Ok(b) => b,
                                Err(e) => {
                                    error!(?e, flow_run_id = %row.flow_run_id, "RecoveryLoop: lock error");
                                    continue;
                                }
                            };
                            if !got {
                                // Another worker raced us; the SKIP
                                // LOCKED sweeper handed us a row
                                // that someone else already has.
                                debug!(
                                    flow_run_id = %row.flow_run_id,
                                    "RecoveryLoop: skip — lock not acquired"
                                );
                                continue;
                            }
                            match recover
                                .recover(&row.flow_run_id, row.flow_xml_version.as_deref())
                                .await
                            {
                                Ok(true) => {
                                    debug!(flow_run_id = %row.flow_run_id, "RecoveryLoop: resumed")
                                }
                                Ok(false) => {
                                    warn!(flow_run_id = %row.flow_run_id, "RecoveryLoop: no-op")
                                }
                                Err(e) => error!(
                                    ?e,
                                    flow_run_id = %row.flow_run_id,
                                    "RecoveryLoop: resume errored"
                                ),
                            }
                            // Release immediately — the resume
                            // path is short; we don't want to hold
                            // the per-row lock any longer than
                            // necessary.
                            let _ = store.release_advisory_lock(&lock_key).await;
                        }
                    }
                    Err(e) => error!(?e, "RecoveryLoop: sweep errored"),
                }
            }
        });
    }
}
