//! `rf-http` binary entry point.
//!
//! Phase 6 surface (Phase 5 routes + pg-backed state):
//! - `POST /ruleforge/evaluate`        run a flow synchronously
//! - `POST /ruleforge/flow/decision`   resume a suspended flow
//! - `POST /ruleforge/flow/invalidate` drop a flow_id from the cache
//! - `GET  /ruleforge/flow/load`       proxy to the Java console
//! - `GET  /health`                    liveness probe
//!
//! Wiring:
//! - `FlowDefinitionRepo` caches parsed BPMN, with `HttpFlowLoader`
//!   hitting the Java console on miss.
//! - `ExecutorRegistry` wires `MockRuleEngine` for v0 (Phase 7+ could
//!   swap in a real engine).
//! - If `PG_URL` is set, builds a `PgInflightStore` and a recovery
//!   loop. Otherwise falls back to the in-memory `MemInflightStore`.

use std::net::SocketAddr;
use std::str::FromStr;
use std::sync::Arc;
use std::time::Duration;

use anyhow::{Context, Result};
use clap::Parser;
use rf_executor::dispatch::ExecutorRegistry;
use rf_executor::flow_context::FlowContext;
use rf_executor::traverser::{traverse, TraverseOutcome};
use rf_http::flow_def_repo::{FlowDefinitionRepo, HttpFlowLoader};
use rf_http::inflight::{InflightStore, PgInflightStore};
use rf_http::routes::{decision, evaluate, health, invalidate, load};
use rf_http::state::AppState;
use rf_rule::mock::MockRuleEngine;
use rf_state::persistence::PgStateStore;
use rf_state::recovery::RecoveryLoop;
use rf_state::serialization::SuspendPayload;
use rf_state::Recover;
use tracing::{info, warn};
use tracing_subscriber::EnvFilter;

#[derive(Debug, Parser)]
#[command(name = "rf-http", about = "RuleForge Rust flow executor — HTTP front")]
struct Cli {
    /// Listen port (default 8281, mirrors Java executor on 8280).
    #[arg(long, env = "HTTP_PORT", default_value = "8281")]
    http_port: u16,

    /// Console URL to fetch BPMN XML from.
    /// (e.g. `http://localhost:8180`).
    #[arg(long, env = "CONSOLE_URL", default_value = "http://localhost:8180")]
    console_url: String,

    /// Postgres URL for the Rust state store.
    /// (Phase 6: e.g. `postgres://ruleforge:ruleforge@localhost:5432/ruleforge_rust`).
    #[arg(long, env = "PG_URL", default_value = "")]
    pg_url: String,

    /// Worker ID for recovery loop logging.
    #[arg(long, env = "WORKER_ID", default_value = "rust-flow-1")]
    worker_id: String,

    /// Recovery sweep interval (seconds). Default 30s, mirrors the
    /// Java `@Scheduled(fixedRate = 30_000)`.
    #[arg(long, env = "RECOVERY_INTERVAL_SECS", default_value = "30")]
    recovery_interval_secs: u64,
}

#[tokio::main]
async fn main() -> Result<()> {
    // tracing — respect RUST_LOG, default to info for our crates
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::try_from_default_env().unwrap_or_else(|_| {
            EnvFilter::new("info,rf_http=debug,rf_executor=debug,rf_state=debug")
        }))
        .init();

    let cli = Cli::parse();
    info!(?cli, "rf-http starting");

    let loader = Arc::new(HttpFlowLoader {
        base_url: cli.console_url.clone(),
        client: reqwest::Client::builder()
            .build()
            .context("build reqwest client")?,
    });
    let repo = Arc::new(FlowDefinitionRepo::new(loader));
    let registry = Arc::new(ExecutorRegistry::with_rule_engine(Arc::new(MockRuleEngine)));

    // Pick the in-flight store: pg-backed if `pg_url` is set, else
    // in-memory (dev fallback).
    let (inflight, state_store_opt) = if cli.pg_url.is_empty() {
        warn!("PG_URL is empty — using in-memory inflight store; state lost on restart");
        (
            Arc::new(rf_http::inflight::MemInflightStore::new()) as Arc<dyn InflightStore>,
            None,
        )
    } else {
        let state = Arc::new(
            PgStateStore::connect(&cli.pg_url)
                .await
                .context("connect pg")?,
        );
        rf_state::migrate(state.pool())
            .await
            .context("run migrations")?;
        info!(pg_url = %cli.pg_url, "pg-backed inflight store ready");
        (
            Arc::new(PgInflightStore::new(Arc::clone(&state), Arc::clone(&repo)))
                as Arc<dyn InflightStore>,
            Some(state),
        )
    };

    // Recovery loop: only if pg is configured.
    if let Some(ref state) = state_store_opt {
        let recover = Arc::new(HttpRecover {
            repo: Arc::clone(&repo),
            registry: Arc::clone(&registry),
            state: Arc::clone(state),
        });
        RecoveryLoop::new(
            Arc::clone(state),
            recover,
            cli.worker_id.clone(),
            Duration::from_secs(cli.recovery_interval_secs),
        )
        .start();
    }

    let state = AppState::with_inflight(
        repo,
        registry,
        inflight,
        cli.worker_id.clone(),
        cli.console_url.clone(),
        cli.pg_url.clone(),
    );

    let app = axum::Router::new()
        .route("/health", axum::routing::get(health::health))
        .route(
            "/ruleforge/evaluate",
            axum::routing::post(evaluate::evaluate),
        )
        .route(
            "/ruleforge/flow/decision",
            axum::routing::post(decision::decide),
        )
        .route(
            "/ruleforge/flow/invalidate",
            axum::routing::post(invalidate::invalidate),
        )
        .route("/ruleforge/flow/load", axum::routing::get(load::load))
        .with_state(state);

    let addr = SocketAddr::from_str(&format!("0.0.0.0:{}", cli.http_port))
        .with_context(|| format!("invalid HTTP_PORT={}", cli.http_port))?;
    info!(%addr, "listening");

    let listener = tokio::net::TcpListener::bind(&addr)
        .await
        .with_context(|| format!("bind {}", addr))?;
    axum::serve(listener, app).await.context("axum::serve")?;

    Ok(())
}

/// `Recover` impl — re-drives a suspended flow. Mirrors the
/// `/flow/decision` resume path minus the user-supplied `decision`
/// query (the row's `wait_ref` is the field; for USER_TASK the
/// decision itself comes from the row's `output_model.payload` if
/// it was already filled in, otherwise we just re-attach the
/// suspension to the in-memory inflight store so a fresh
/// `/flow/decision?decision=...` can complete it).
///
/// Phase 7: the row carries everything we need to resume — the
/// `flow_id` re-fetches the def, `row_vars` re-hydrates the
/// executor's `Vars`, and `current_awaiting_field` (derived from
/// `wait_ref`) tells the userTask resume path it's a continuation.
struct HttpRecover {
    repo: Arc<FlowDefinitionRepo>,
    registry: Arc<ExecutorRegistry>,
    state: Arc<PgStateStore>,
}

#[async_trait::async_trait]
impl Recover for HttpRecover {
    async fn recover(
        &self,
        flow_run_id: &str,
        flow_xml_version: Option<&str>,
    ) -> anyhow::Result<bool> {
        let Some(row) = self.state.select_by_flow_run_id(flow_run_id).await? else {
            warn!(%flow_run_id, "RecoveryLoop: row vanished between sweep + lock");
            return Ok(false);
        };
        // flow_xml_version mismatch: console saved a new BPMN while
        // the flow was suspended. Java's recovery job gives up
        // (DecisionFlowStateService.recover sets status=FAILED with
        // "version mismatch"). We do the same.
        if row.flow_xml_version.as_deref() != flow_xml_version {
            warn!(
                %flow_run_id,
                row_version = ?row.flow_xml_version,
                expected = ?flow_xml_version,
                "RecoveryLoop: flow_xml_version mismatch — marking FAILED"
            );
            self.state
                .mark_failed(
                    flow_run_id,
                    row.current_node_id.as_deref(),
                    "flow_xml_version mismatch",
                )
                .await?;
            return Ok(true);
        }
        // Re-load the def. After a worker restart the cache is empty
        // and this triggers an HTTP fetch via `HttpFlowLoader`.
        let def = match self.repo.get_or_load(&row.flow_id).await {
            Ok(d) => d,
            Err(e) => {
                warn!(?e, flow_id = %row.flow_id, "RecoveryLoop: def lookup failed");
                return Ok(false);
            }
        };
        // Re-hydrate the FlowContext from row_vars.
        let mut ctx = FlowContext::new(flow_run_id);
        if let Some(v) = row.row_vars.as_ref().and_then(serde_json::Value::as_object) {
            for (k, vv) in v {
                ctx.vars.insert(k.clone(), vv.clone());
            }
        }
        // The userTask executor sets current_awaiting_field to the
        // wait_ref (= decisionField). Re-attach it so the resume
        // path returns Continue on the next step.
        if let Some(ref info) = row.output_model {
            if let Ok(p) = SuspendPayload::from_value(info.clone()) {
                ctx.current_awaiting_field = Some(p.wait_ref);
            }
        }
        // Call the traverser — it will either continue, suspend
        // again, or fail. We mirror the on-`/evaluate` outcomes.
        let outcome = traverse(Arc::clone(&def), ctx, Arc::clone(&self.registry));
        match outcome {
            TraverseOutcome::Completed(t) => {
                let map: serde_json::Map<String, serde_json::Value> =
                    t.ctx.vars.into_inner().into_iter().collect();
                self.state
                    .mark_completed(
                        flow_run_id,
                        t.ctx.current_node_id.as_deref(),
                        serde_json::Value::Object(map),
                        0,
                    )
                    .await?;
                info!(%flow_run_id, "RecoveryLoop: completed");
                Ok(true)
            }
            TraverseOutcome::Suspended(t, info) => {
                // Re-suspend with the new info. Same effect as
                // Phase 5's inflight store; pg row is rewritten.
                self.state
                    .mark_suspended(
                        flow_run_id,
                        Some(t.ctx.current_node_id.as_deref().unwrap_or("")),
                        Some("UserTask"),
                        info.wait_type.into(),
                        &info.wait_ref,
                        info.next_retry_at,
                        info.payload,
                    )
                    .await?;
                info!(%flow_run_id, "RecoveryLoop: re-suspended");
                Ok(true)
            }
            TraverseOutcome::Failed(t, err) => {
                let msg = format!("{err}");
                self.state
                    .mark_failed(flow_run_id, t.ctx.current_node_id.as_deref(), &msg)
                    .await?;
                warn!(%flow_run_id, ?err, "RecoveryLoop: failed");
                Ok(true)
            }
        }
    }
}
