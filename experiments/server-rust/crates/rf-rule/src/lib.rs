//! Rule engine implementations.
//!
//! `RuleEngine` trait + `RuleResults` live in `rf-executor` (the trait needs
//! `FlowContext` and is invoked by `RuleNodeExecutor` — both in `rf-executor`).
//! This crate holds concrete impls: `MockRuleEngine` (Phase 4), and a future
//! `RemoteRuleEngine` that talks to the Java executor via HTTP (Phase 7+).

#![allow(dead_code)]

pub mod mock;
