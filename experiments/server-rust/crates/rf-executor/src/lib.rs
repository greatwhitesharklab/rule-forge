//! Decision flow executor.
//!
//! Phase 4 — type-state traverser + 4-segment routing + 5 concrete
//! NodeExecutors. Phase 5 wires HTTP; Phase 6 adds pg persistence.

#![allow(dead_code)]

pub mod condition;
pub mod dispatch;
pub mod error;
pub mod executors;
pub mod flow_context;
pub mod next_node;
pub mod node_executor;
pub mod node_result;
pub mod rule_engine;
pub mod traverser;
pub mod vars;

pub use dispatch::ExecutorRegistry;
