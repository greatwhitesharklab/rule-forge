//! `rf-http` binary entry point.
//!
//! Phase 5 surface:
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
//! - `InflightStore` keeps suspended flow contexts in memory (Phase 6
//!   swaps this out for pg `rust_decision_flow_state`).

use std::net::SocketAddr;
use std::str::FromStr;
use std::sync::Arc;

use anyhow::Context;
use clap::Parser;
use rf_executor::dispatch::ExecutorRegistry;
use rf_http::flow_def_repo::{FlowDefinitionRepo, HttpFlowLoader};
use rf_http::routes::{decision, evaluate, health, invalidate, load};
use rf_http::state::AppState;
use rf_rule::mock::MockRuleEngine;
use tracing::info;
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
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
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
    let state = AppState::new(
        repo,
        registry,
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
