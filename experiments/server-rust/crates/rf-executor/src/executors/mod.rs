//! 5 concrete NodeExecutor implementations.
//!
//! - `RuleExecutor`   — delegates to `dyn RuleEngine` (mock for Phase 4)
//! - `ActionExecutor` — calls `fn(&mut Vars)` from a mock registry
//! - `ScriptExecutor` — stub: rhai supported as format="rhai", no-op for now
//! - `GatewayExecutor` — no-op (routing happens in `next_node`)
//! - `UserTaskExecutor` — the Phase 4 keystone: returns `NodeResult::Suspend`
//!   and writes `current_awaiting_field` for the next gateway's binary
//!   decision routing

pub mod action;
pub mod gateway;
pub mod rule;
pub mod script;
pub mod user_task;

pub use action::{ActionExecutor, ActionFn, MockActionRegistry};
pub use gateway::GatewayExecutor;
pub use rule::RuleExecutor;
pub use script::ScriptExecutor;
pub use user_task::UserTaskExecutor;
