//! Process variable bag.
//!
//! Type-safe newtype around `serde_json::Value` so the executor can never
//! accidentally stuff an arbitrary `Object` (a Java reflex) into the flow
//! context. All values are JSON; nested objects are `Value::Object` and
//! lookups support dot-paths like `applicant.age`.
//!
//! BTreeMap (not HashMap) so `Vars: PartialEq` — useful for snapshot
//! tests and for the persistence layer's row_vars round-trip.

use std::collections::BTreeMap;

use serde::Serialize;
use serde_json::Value;

#[derive(Debug, Clone, Default, PartialEq)]
pub struct Vars(pub BTreeMap<String, Value>);

impl Vars {
    pub fn new() -> Self {
        Self(BTreeMap::new())
    }

    /// Insert a literal `serde_json::Value`. Most callers in the executor
    /// (Phase 4 onwards) will use this — e.g. `MockRuleEngine` writes
    /// `vars.insert("approved", Value::Bool(true))`.
    pub fn insert(&mut self, key: impl Into<String>, val: Value) -> Option<Value> {
        self.0.insert(key.into(), val)
    }

    /// Insert by serialising any `Serialize` value. Convenient for
    /// `insert("age", 20_i64)` from test fixtures.
    pub fn insert_serialized<T: Serialize>(
        &mut self,
        key: impl Into<String>,
        val: T,
    ) -> Result<(), serde_json::Error> {
        let v = serde_json::to_value(val)?;
        self.0.insert(key.into(), v);
        Ok(())
    }

    pub fn get(&self, key: &str) -> Option<&Value> {
        self.0.get(key)
    }

    pub fn get_str(&self, key: &str) -> Option<&str> {
        self.0.get(key).and_then(Value::as_str)
    }

    pub fn get_i64(&self, key: &str) -> Option<i64> {
        self.0.get(key).and_then(Value::as_i64)
    }

    pub fn get_bool(&self, key: &str) -> Option<bool> {
        self.0.get(key).and_then(Value::as_bool)
    }

    /// Resolve a dot-separated path like `applicant.age` directly against
    /// the BTreeMap. Returns a reference into `self.0` so the caller
    /// doesn't pay for cloning. Mirrors Java
    /// `ConditionEvaluator.resolveVariable()`.
    pub fn resolve_path(&self, path: &str) -> Option<&Value> {
        let mut iter = path.split('.');
        let first = iter.next()?;
        let mut cur = self.0.get(first)?;
        for segment in iter {
            cur = cur.as_object()?.get(segment)?;
        }
        Some(cur)
    }

    pub fn as_object(&self) -> &BTreeMap<String, Value> {
        &self.0
    }

    pub fn into_inner(self) -> BTreeMap<String, Value> {
        self.0
    }
}

/// Walk a `Value::Object` root following a dot-separated path. Used by
/// `ConditionEvaluator` to resolve a token against a freshly built root.
pub fn resolve_path<'a>(root: &'a Value, path: &str) -> Option<&'a Value> {
    let mut cur = root;
    for segment in path.split('.') {
        cur = cur.as_object()?.get(segment)?;
    }
    Some(cur)
}
