//! axum HTTP service — wires the Rust flow executor behind HTTP.
//!
//! Phase 5 surface:
//! - `POST /ruleforge/evaluate`        run a flow synchronously
//! - `POST /ruleforge/flow/decision`   resume a suspended (userTask) flow
//! - `POST /ruleforge/flow/event`      deliver a message/signal to a suspended flow
//! - `POST /ruleforge/flow/invalidate` drop a flow_id from the cache
//! - `GET  /ruleforge/flow/load`       proxy to the Java console (raw XML)
//! - `GET  /health`                    liveness probe
//!
//! State storage is in-memory (Phase 5) — see `inflight::InflightStore`.
#![allow(dead_code)]

pub mod flow_def_repo;
pub mod inflight;
pub mod routes;
pub mod state;

// Re-export so route handlers / tests can `use rf_http::InflightStore`.
pub use inflight::{InflightFlow, InflightStore, MemInflightStore, PgInflightStore};
