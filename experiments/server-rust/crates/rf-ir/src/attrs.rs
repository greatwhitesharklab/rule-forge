//! Qualified-name attribute bag.
//!
//! BPMN extension attributes (ruleforge:*, flowable:*) are kept in a `BTreeMap`
//! keyed by qualified name (e.g. `"ruleforge:taskType"`) so the executor can
//! look them up via [`Attrs::ruleforge`] / [`Attrs::flowable`].
//!
//! BTreeMap is chosen over HashMap so `Attrs: PartialEq + Eq`, which makes
//! IR-level assertions in tests cheap.

use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};

pub const NS_RULEFORGE: &str = "http://ruleforge.com/schema";
pub const NS_FLOWABLE: &str = "http://flowable.org/bpmn";

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
pub struct Attrs(pub BTreeMap<String, String>);

impl Attrs {
    pub fn new() -> Self {
        Self(BTreeMap::new())
    }

    /// Look up by fully-qualified name (e.g. `"ruleforge:taskType"`).
    pub fn get(&self, qualified_name: &str) -> Option<&str> {
        self.0.get(qualified_name).map(String::as_str)
    }

    /// Look up a `ruleforge:` attribute by its local name.
    pub fn ruleforge(&self, local: &str) -> Option<&str> {
        self.get(&format!("ruleforge:{local}"))
    }

    /// Look up a `flowable:` attribute by its local name.
    pub fn flowable(&self, local: &str) -> Option<&str> {
        self.get(&format!("flowable:{local}"))
    }
}
