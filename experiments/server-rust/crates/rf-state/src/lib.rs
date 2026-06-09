//! Postgres-backed decision flow state + recovery loop.
//!
//! Phase 6 surface:
//! - `PgStateStore` — sqlx CRUD on `rust_decision_flow_state`
//! - `DecisionFlowState` row type + `FlowStatus` / `WaitType` enums
//! - `RecoveryLoop` — 30s sweep that re-drives WAITING_CALLBACK /
//!   PENDING_ASYNC rows
//! - `SuspendPayload` — stable contract between the writer
//!   (rf-state) and the reader (rf-http) for the jsonb `output_model`
//!   column
//!
//! Lock primitive: `pg_try_advisory_lock(hashtext(flow_run_id))` for
//! the single-key CAS; `FOR UPDATE SKIP LOCKED` for the recovery
//! sweep. Compare Java V5.19 timestamp CAS — same semantics, pg
//! gives us a real lock primitive instead of clock arithmetic.

pub mod persistence;
pub mod recovery;
pub mod serialization;
pub mod state_row;

use sqlx::migrate::MigrateError;
use sqlx::{PgPool, Postgres};

/// Run the migrations in `crates/rf-state/migrations/`. Called once at
/// startup by the binary. Idempotent — sqlx tracks applied
/// migrations in `_sqlx_migrations` and skips re-runs.
pub async fn migrate(pool: &PgPool) -> Result<(), MigrateError> {
    sqlx::migrate!("./migrations").run(pool).await
}

/// Re-export for tests.
pub use persistence::PgStateStore;
pub use recovery::{Recover, RecoveryLoop};
pub use state_row::{DecisionFlowState, FlowStatus, WaitType};

// Make the `Postgres` driver name re-exported so callers that need to
// pull a `sqlx::query_as::<_, T, _>` with an explicit DB generic can
// do so without importing the inner module.
#[allow(dead_code)]
pub type PgDb = Postgres;
