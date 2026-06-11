//! 10 concrete NodeExecutor implementations.
//!
//! - `RuleExecutor`   — delegates to `dyn RuleEngine` (mock for Phase 4)
//! - `ActionExecutor` — calls `fn(&mut Vars)` from a mock registry
//! - `ScriptExecutor` — stub: rhai supported as format="rhai", no-op for now
//! - `GatewayExecutor` — no-op (routing happens in `next_node`)
//! - `UserTaskExecutor` — the Phase 4 keystone: returns `NodeResult::Suspend`
//!   and writes `current_awaiting_field` for the next gateway's binary
//!   decision routing
//! - `IntermediateEventExecutor` — message / signal / timer catch events
//!   that suspend the flow until an external event arrives (V5.26 P0)
//! - `BoundaryEventExecutor` — error / timer boundary on an activity edge
//!   (V5.27 P0). Outgoing edges of the boundary are the handler path; the
//!   main flow continues via the activity's normal edge.
//! - `SubProcessExecutor` — call a sub-flow by id and rejoin (V5.27 P0).
//!   Recursive: it `traverse()`s the sub-flow with a fresh context and
//!   copies back the output vars when it completes.
//! - `StartEventExecutor` — V5.28 P7. Manual start = `Continue`; message
//!   start = `Suspend` with `message:<eventName>` wait_ref; timer start
//!   is `Unsupported` (the scheduler in `main.rs` runs timer flows
//!   directly without going through the dispatcher).
//! - `MultiInstanceExecutor` — V5.29. Wraps any task kind with
//!   `multiInstanceLoopCharacteristics` semantics. Parallel = in-memory
//!   for-loop on a cloned ctx, sequential = in-memory for-loop on the
//!   same ctx. Reuses V5.28 P6's `Fork` data type internally for the
//!   data-model contract (per-child `Vars` clone), but routes through
//!   `NodeResult::Continue` at the outer level (the wrapper runs the
//!   children synchronously — see the executor's docstring for why
//!   `NodeResult::Fork` would re-fire the wrapper recursively).
//! - `EndEventExecutor` — V5.30. Normal end = `Continue`; error /
//!   escalation / terminate end = `Fail(FlowError::...)` → traverser
//!   exits with `TraverseOutcome::Failed`. Mirrors `StartEventExecutor`'s
//!   shape (read `endType` attr → branch on `end_kind`).
//! - `CompensationStartExecutor` / `CompensationEndExecutor` /
//!   `CompensationThrowExecutor` / `CompensationIntermediateExecutor` —
//!   V5.31 P0. The BPMN 2.0 `<compensate*Event>` quartet that
//!   implements the SAGA-pattern compensation rollback.
//!   `CompensationThrowExecutor` uses `execute_with` to recursively
//!   traverse handler sub-flows via
//!   `crate::compensation::run_handlers`.

pub mod action;
pub mod boundary_event;
pub mod compensation_end;
pub mod compensation_intermediate;
pub mod compensation_start;
pub mod compensation_throw;
pub mod end_event;
pub mod gateway;
pub mod intermediate_event;
pub mod multi_instance;
pub mod rule;
pub mod script;
pub mod start_event;
pub mod sub_process;
pub mod user_task;

pub use action::{ActionExecutor, ActionFn, MockActionRegistry};
pub use boundary_event::{BoundaryEventError, BoundaryEventExecutor, BoundaryEventKind};
pub use compensation_end::CompensationEndExecutor;
pub use compensation_intermediate::CompensationIntermediateExecutor;
pub use compensation_start::CompensationStartExecutor;
pub use compensation_throw::CompensationThrowExecutor;
pub use end_event::EndEventExecutor;
// V5.30 — `EndEventKind` and `EndEventError` are
// defined in `rf_ir::node_kind` (parser-side
// mirror); the executor uses them via
// `from_attrs`. Re-export here so callers that
// only depend on `rf_executor` don't have to
// pull in `rf_ir` directly.
pub use rf_ir::node_kind::{EndEventError, EndEventKind};
pub use gateway::GatewayExecutor;
pub use intermediate_event::{IntermediateEventError, IntermediateEventExecutor, IntermediateEventKind};
pub use multi_instance::{MultiInstanceError, MultiInstanceExecutor};
pub use rule::RuleExecutor;
pub use script::ScriptExecutor;
pub use start_event::{StartEventError, StartEventExecutor, StartTrigger};
pub use sub_process::{SubProcessError, SubProcessExecutor};
pub use user_task::UserTaskExecutor;
