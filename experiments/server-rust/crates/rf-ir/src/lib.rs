//! Immutable intermediate representation for RuleForge decision flows.
//!
//! Pure types — no IO, no async. The parser crate `rf-parse` produces
//! `FlowDefinition` from raw BPMN XML; the executor `rf-executor` consumes
//! it. Holding these in their own crate keeps `rf-parse` and `rf-executor`
//! from developing a cycle: both depend downward on `rf-ir`, neither
//! depends on the other.

#![allow(dead_code)] // types are used in later phases; warnings only on this crate

pub mod attrs;
pub mod flow_definition;
pub mod flow_node;
pub mod ir_error;
pub mod node_kind;
pub mod sequence_flow;
