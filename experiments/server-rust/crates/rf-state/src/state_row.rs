//! `rust_decision_flow_state` row + status / wait-type enums.
//!
//! The schema lives in `migrations/20260608000001_rust_decision_flow_state.sql`.
//! This module is the Rust mirror of that DDL — same column names, same
//! value semantics, 1:1. The `FlowStatus` / `WaitType` newtypes prevent
//! typos when the SQL `CHECK` constraints and the Rust enum drift apart.

use std::fmt;

use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use sqlx::FromRow;

/// Status of a flow run. Mirrors the SQL `CHECK` constraint on
/// `rust_decision_flow_state.status`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum FlowStatus {
    Pending,
    Running,
    PendingAsync,
    WaitingCallback,
    Completed,
    Failed,
}

impl FlowStatus {
    /// Wire form — what we write to the column. Stable string,
    /// uppercase, matches the SQL `CHECK` list.
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Pending => "PENDING",
            Self::Running => "RUNNING",
            Self::PendingAsync => "PENDING_ASYNC",
            Self::WaitingCallback => "WAITING_CALLBACK",
            Self::Completed => "COMPLETED",
            Self::Failed => "FAILED",
        }
    }

    /// Parse a status string. Returns `None` for unknown values.
    /// Named `parse_str` (not `from_str`) to avoid the
    /// `should_implement_trait` clippy warning — we deliberately
    /// don't implement `FromStr` because the input is owned by the
    /// pg column type, not arbitrary user input.
    pub fn parse_str(s: &str) -> Option<Self> {
        Some(match s {
            "PENDING" => Self::Pending,
            "RUNNING" => Self::Running,
            "PENDING_ASYNC" => Self::PendingAsync,
            "WAITING_CALLBACK" => Self::WaitingCallback,
            "COMPLETED" => Self::Completed,
            "FAILED" => Self::Failed,
            _ => return None,
        })
    }
}

impl fmt::Display for FlowStatus {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

/// Type of suspension. Mirrors the SQL `CHECK` constraint on
/// `rust_decision_flow_state.wait_type`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum WaitType {
    UserTask,
    AsyncData,
    AsyncTask,
}

impl WaitType {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::UserTask => "USER_TASK",
            Self::AsyncData => "ASYNC_DATA",
            Self::AsyncTask => "ASYNC_TASK",
        }
    }

    /// Parse a wait-type string. Named `parse_str` (not `from_str`)
    /// to avoid the `should_implement_trait` clippy warning.
    pub fn parse_str(s: &str) -> Option<Self> {
        Some(match s {
            "USER_TASK" => Self::UserTask,
            "ASYNC_DATA" => Self::AsyncData,
            "ASYNC_TASK" => Self::AsyncTask,
            _ => return None,
        })
    }
}

// Bridging `rf_state::WaitType` ↔ `rf_executor::node_result::WaitType`.
// They have the same variant set by design (we mirror the executor's
// enum into the state row), but Rust requires us to spell the
// conversion. Re-exporting the executor's enum as the canonical one
// would couple `rf-state` to the executor's data shape, which is
// the wrong direction; the conversion lives here as the bridge.
impl From<rf_executor::node_result::WaitType> for WaitType {
    fn from(w: rf_executor::node_result::WaitType) -> Self {
        match w {
            rf_executor::node_result::WaitType::UserTask => Self::UserTask,
            rf_executor::node_result::WaitType::AsyncData => Self::AsyncData,
            rf_executor::node_result::WaitType::AsyncTask => Self::AsyncTask,
        }
    }
}

impl From<WaitType> for rf_executor::node_result::WaitType {
    fn from(w: WaitType) -> Self {
        match w {
            WaitType::UserTask => Self::UserTask,
            WaitType::AsyncData => Self::AsyncData,
            WaitType::AsyncTask => Self::AsyncTask,
        }
    }
}

impl fmt::Display for WaitType {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.as_str())
    }
}

/// One row of `rust_decision_flow_state`.
///
/// `row_vars` and `output_model` are `serde_json::Value` so we round-trip
/// complex JSONB blobs (the executor's `Vars` and the `SuspendInfo`
/// payload) without a separate deserialise step. `locked_at` /
/// `locked_until` are kept on the struct (even though the authoritative
/// lock is the advisory lock) so logs and `/health` can show the worker's
/// view of the row.
#[derive(Debug, Clone, FromRow)]
pub struct DecisionFlowState {
    pub id: i64,
    pub flow_id: String,
    pub flow_run_id: String,
    pub user_id: Option<String>,
    pub order_no: Option<String>,
    pub status: FlowStatus,
    pub current_node_id: Option<String>,
    pub current_node_type: Option<String>,
    pub next_retry_at: Option<DateTime<Utc>>,
    pub wait_ref: Option<String>,
    pub wait_type: Option<WaitType>,
    pub flow_xml_version: Option<String>,
    pub row_vars: Option<serde_json::Value>,
    pub row_entity_snapshot: Option<serde_json::Value>,
    pub output_model: Option<serde_json::Value>,
    pub progress: Option<f64>,
    pub error_message: Option<String>,
    pub locked_by: Option<String>,
    pub locked_at: Option<DateTime<Utc>>,
    pub locked_until: Option<DateTime<Utc>>,
    pub retry_count: i32,
    pub total_execution_ms: i64,
    pub fireable_rules: i32,
    pub matched_rules: i32,
    pub create_time: DateTime<Utc>,
    pub update_time: DateTime<Utc>,
}

// `From<String>` for the enum columns — sqlx postgres returns TEXT for
// VARCHAR. We can't `impl From<String> for FlowStatus` from a foreign
// crate, so we provide it as a method on the column type via `TryFrom`
// inside `pg_row` decoding (see `persistence.rs`).
//
// The `#[derive(FromRow)]` works on the `String`/`Option<String>` shape
// by default. We override the column decode via custom `Decode` impls.
impl<'r> sqlx::Decode<'r, sqlx::Postgres> for FlowStatus {
    fn decode(
        value: <sqlx::Postgres as sqlx::Database>::ValueRef<'r>,
    ) -> Result<Self, sqlx::error::BoxDynError> {
        let s = <&str as sqlx::Decode<sqlx::Postgres>>::decode(value)?;
        FlowStatus::parse_str(s).ok_or_else(|| format!("unknown FlowStatus: {s}").into())
    }
}

impl<'r> sqlx::Decode<'r, sqlx::Postgres> for WaitType {
    fn decode(
        value: <sqlx::Postgres as sqlx::Database>::ValueRef<'r>,
    ) -> Result<Self, sqlx::error::BoxDynError> {
        let s = <&str as sqlx::Decode<sqlx::Postgres>>::decode(value)?;
        WaitType::parse_str(s).ok_or_else(|| format!("unknown WaitType: {s}").into())
    }
}

// `Encode` so we can use `FlowStatus` as a bind parameter in `bind()`.
impl<'q> sqlx::Encode<'q, sqlx::Postgres> for FlowStatus {
    fn encode_by_ref(
        &self,
        buf: &mut sqlx::postgres::PgArgumentBuffer,
    ) -> Result<sqlx::encode::IsNull, sqlx::error::BoxDynError> {
        <&str as sqlx::Encode<sqlx::Postgres>>::encode(self.as_str(), buf)
    }
}

impl<'q> sqlx::Encode<'q, sqlx::Postgres> for WaitType {
    fn encode_by_ref(
        &self,
        buf: &mut sqlx::postgres::PgArgumentBuffer,
    ) -> Result<sqlx::encode::IsNull, sqlx::error::BoxDynError> {
        <&str as sqlx::Encode<sqlx::Postgres>>::encode(self.as_str(), buf)
    }
}

// `Type` so sqlx knows the Postgres type. We have to claim VARCHAR
// (not TEXT) because the column type in the migration is
// `VARCHAR(20)` — sqlx 0.8 refuses to decode a `VARCHAR` column as a
// Rust type whose `Type` impl says `TEXT`.
impl sqlx::Type<sqlx::Postgres> for FlowStatus {
    fn type_info() -> sqlx::postgres::PgTypeInfo {
        sqlx::postgres::PgTypeInfo::with_name("VARCHAR")
    }
    fn compatible(ty: &sqlx::postgres::PgTypeInfo) -> bool {
        use sqlx::TypeInfo;
        let n = ty.name();
        n == "VARCHAR" || n == "TEXT"
    }
}

impl sqlx::Type<sqlx::Postgres> for WaitType {
    fn type_info() -> sqlx::postgres::PgTypeInfo {
        sqlx::postgres::PgTypeInfo::with_name("VARCHAR")
    }
    fn compatible(ty: &sqlx::postgres::PgTypeInfo) -> bool {
        use sqlx::TypeInfo;
        let n = ty.name();
        n == "VARCHAR" || n == "TEXT"
    }
}
