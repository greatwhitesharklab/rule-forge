//! `Vars` ↔ jsonb + `SuspendInfo` ↔ jsonb helpers.
//!
//! The executor's `Vars` is `BTreeMap<String, serde_json::Value>`; the
//! pg column `row_vars` is `jsonb`. The conversion is structural — we
//! re-shape into a `serde_json::Map<String, Value>` for the jsonb wire
//! and back into a `BTreeMap` on the way in. No custom serde derives
//! needed; the newtype is transparent.
//!
//! `SuspendInfo` is slightly trickier — it's an `rf_executor` type that
//! we don't want `rf-state` to depend on at the structural level
//! (would couple the crates via the data shape). Instead we serialize
//! the suspend info as a 4-key `serde_json::Value`:
//! ```json
//! {
//!   "waitType": "USER_TASK",
//!   "waitRef":  "approve",
//!   "payload":  { "decisionField": "approve", "label": "审批" },
//!   "nextRetryAt": null
//! }
//! ```
//! and the HTTP layer (which knows the executor) is the one that
//! re-hydrates the `SuspendInfo` from the jsonb blob.

use serde_json::{Map, Value};

use crate::state_row::WaitType;

/// Shape of the `output_model` jsonb column when a flow is suspended.
/// Stable contract between `rf-state` (writer) and `rf-http` (reader).
#[derive(Debug, Clone)]
pub struct SuspendPayload {
    pub wait_type: WaitType,
    pub wait_ref: String,
    pub payload: Value,
    pub next_retry_at: Option<chrono::DateTime<chrono::Utc>>,
}

impl SuspendPayload {
    pub fn to_value(&self) -> Value {
        let mut m = Map::new();
        m.insert(
            "waitType".to_string(),
            Value::String(self.wait_type.to_string()),
        );
        m.insert("waitRef".to_string(), Value::String(self.wait_ref.clone()));
        m.insert("payload".to_string(), self.payload.clone());
        m.insert(
            "nextRetryAt".to_string(),
            match self.next_retry_at {
                Some(t) => Value::String(t.to_rfc3339()),
                None => Value::Null,
            },
        );
        Value::Object(m)
    }

    pub fn from_value(v: Value) -> Result<Self, String> {
        let obj = v.as_object().ok_or("output_model is not an object")?;
        let wait_type = obj
            .get("waitType")
            .and_then(Value::as_str)
            .and_then(WaitType::parse_str)
            .ok_or_else(|| "missing/invalid waitType".to_string())?;
        let wait_ref = obj
            .get("waitRef")
            .and_then(Value::as_str)
            .ok_or_else(|| "missing waitRef".to_string())?
            .to_string();
        let payload = obj.get("payload").cloned().unwrap_or(Value::Null);
        let next_retry_at = obj
            .get("nextRetryAt")
            .and_then(Value::as_str)
            .and_then(|s| chrono::DateTime::parse_from_rfc3339(s).ok())
            .map(|d| d.with_timezone(&chrono::Utc));
        Ok(Self {
            wait_type,
            wait_ref,
            payload,
            next_retry_at,
        })
    }
}

/// Convert a `serde_json::Value` (probably a `Value::Object` produced
/// by the executor) into a jsonb-ready `serde_json::Value`. No-op for
/// `Value::Object`; pass-through for scalars.
pub fn vars_to_value(v: Value) -> Value {
    v
}

/// Inverse of `vars_to_value` — a jsonb value back into a
/// `serde_json::Value` ready for `Vars::insert_serialized` calls. Also
/// a no-op.
pub fn value_to_vars(v: Value) -> Value {
    v
}
