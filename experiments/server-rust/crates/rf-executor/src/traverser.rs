//! Type-state traverser.
//!
//! The four states encode the legal transitions of a running decision flow.
//! Compile-time enforcement: you cannot `.resume()` a `Traverser<Completed>`
//! because that impl block doesn't exist.
//!
//! ```text
//!       ┌──── start() ────► Running ── step*() ──► Completed
//!       │                    │   │
//!       │                    │   └─ suspend(info) ─► Suspended ── resume() ──► Running
//!       │                    │
//!       │                    └─ fail() ─► Failed
//! ```
//!
//! Compare Java: `state.status` is a `String` and the same `FlowNodeRunner`
//! code path handles all transitions, with `instanceof` checks scattered
//! through the codebase to enforce "completed can't be resumed". The
//! type-state makes that check at compile time.

use std::collections::HashSet;
use std::marker::PhantomData;
use std::sync::Arc;

use rf_ir::flow_definition::FlowDefinition;

use crate::dispatch::{dispatch, ExecutorRegistry};
use crate::error::FlowError;
use crate::flow_context::FlowContext;
use crate::next_node::next_node;
use crate::node_result::{NodeResult, SuspendInfo};

const MAX_STEPS: usize = 1000;

/// Public entry — single-call traversal. Loops `step()` until done,
/// suspended, or failed. Wraps the type-state for callers that don't
/// want to drive the state machine by hand.
pub fn traverse(
    def: Arc<FlowDefinition>,
    ctx: FlowContext,
    reg: Arc<ExecutorRegistry>,
) -> TraverseOutcome {
    let mut t = Traverser::<Running>::begin(def, ctx, reg);
    loop {
        match t.step() {
            Ok(StepOutcome::Continue(next)) => t = next,
            Ok(StepOutcome::Done(done)) => return TraverseOutcome::Completed(done),
            Ok(StepOutcome::Suspended(suspended, info)) => {
                return TraverseOutcome::Suspended(suspended, info)
            }
            Err((failed, err)) => return TraverseOutcome::Failed(failed, err),
        }
    }
}

#[derive(Debug)]
pub enum TraverseOutcome {
    Completed(Traverser<Completed>),
    Suspended(Traverser<Suspended>, SuspendInfo),
    Failed(Traverser<Failed>, FlowError),
}

impl TraverseOutcome {
    pub fn context(&self) -> &FlowContext {
        match self {
            TraverseOutcome::Completed(t) => t.ctx(),
            TraverseOutcome::Suspended(t, _) => t.ctx(),
            TraverseOutcome::Failed(t, _) => t.ctx(),
        }
    }
    pub fn into_context(self) -> FlowContext {
        match self {
            TraverseOutcome::Completed(t) => t.ctx,
            TraverseOutcome::Suspended(t, _) => t.ctx,
            TraverseOutcome::Failed(t, _) => t.ctx,
        }
    }
}

// ── state markers ─────────────────────────────────────────────────────────

#[derive(Debug)]
pub struct Pending;
#[derive(Debug)]
pub struct Running;
#[derive(Debug)]
pub struct Suspended;
#[derive(Debug)]
pub struct Completed;
#[derive(Debug)]
pub struct Failed;

#[derive(Debug)]
pub struct Traverser<State> {
    pub def: Arc<FlowDefinition>,
    pub ctx: FlowContext,
    pub reg: Arc<ExecutorRegistry>,
    /// Next node id to visit. `None` means traversal finished.
    pub next: Option<String>,
    /// Tracks which node ids have been stepped on this run, for loop
    /// detection. Java uses the same trick at line 79.
    visited: HashSet<String>,
    _state: PhantomData<State>,
}

impl<S> Traverser<S> {
    pub fn def(&self) -> &FlowDefinition {
        &self.def
    }
    pub fn ctx(&self) -> &FlowContext {
        &self.ctx
    }
    pub fn ctx_mut(&mut self) -> &mut FlowContext {
        &mut self.ctx
    }
    pub fn visited(&self) -> &HashSet<String> {
        &self.visited
    }
}

impl Traverser<Running> {
    pub fn begin(def: Arc<FlowDefinition>, ctx: FlowContext, reg: Arc<ExecutorRegistry>) -> Self {
        let start = def.start.clone();
        Self {
            def,
            ctx,
            reg,
            next: Some(start),
            visited: HashSet::new(),
            _state: PhantomData,
        }
    }

    /// Take a single step. The executor is intentionally synchronous-style
    /// (`async` for future-proofing when Phase 4 adds real async nodes, but
    /// Phase 3's `dispatch` is pure-CPU).
    ///
    /// The Err variant carries the failed traverser alongside the error
    /// because callers (the `traverse()` driver, the recovery loop in
    /// Phase 6) need the final `FlowContext` to persist `current_node_id`
    /// and `vars` to the state row. The size warning is intentional.
    #[allow(clippy::result_large_err)]
    pub fn step(mut self) -> Result<StepOutcome, (Traverser<Failed>, FlowError)> {
        let Some(node_id) = self.next.clone() else {
            return Ok(StepOutcome::Done(self.into_completed()));
        };

        if self.visited.len() > MAX_STEPS {
            return Err((self.into_failed(), FlowError::MaxStepsExceeded(MAX_STEPS)));
        }
        if !self.visited.insert(node_id.clone()) {
            return Err((self.into_failed(), FlowError::LoopDetected(node_id)));
        }

        let Some(node) = self.def.nodes.get(&node_id).cloned() else {
            return Err((self.into_failed(), FlowError::NodeNotFound(node_id)));
        };

        self.ctx.current_node_id = Some(node_id.clone());

        // The dispatch call is async — wrap in block_in_place for the
        // Phase 3 sync executor so we don't have to pull in tokio::task
        // for the test runtime.
        let result = match pollster::block_on(dispatch(&node, &mut self.ctx, &self.reg)) {
            Ok(r) => r,
            Err(e) => return Err((self.into_failed(), e)),
        };

        match result {
            NodeResult::Continue => match next_node(&self.def, &node, &self.ctx) {
                Ok(Some(next_id)) => {
                    self.next = Some(next_id);
                    Ok(StepOutcome::Continue(self))
                }
                Ok(None) => Ok(StepOutcome::Done(self.into_completed())),
                Err(e) => Err((self.into_failed(), e)),
            },
            NodeResult::Branch(target) => {
                self.next = Some(target);
                Ok(StepOutcome::Continue(self))
            }
            NodeResult::Suspend(info) => {
                let suspended = self.into_suspended();
                Ok(StepOutcome::Suspended(suspended, info))
            }
        }
    }

    pub fn into_suspended(self) -> Traverser<Suspended> {
        Traverser {
            def: self.def,
            ctx: self.ctx,
            reg: self.reg,
            next: self.next,
            visited: self.visited,
            _state: PhantomData,
        }
    }

    pub fn into_completed(self) -> Traverser<Completed> {
        Traverser {
            def: self.def,
            ctx: self.ctx,
            reg: self.reg,
            next: None,
            visited: self.visited,
            _state: PhantomData,
        }
    }

    pub fn into_failed(self) -> Traverser<Failed> {
        Traverser {
            def: self.def,
            ctx: self.ctx,
            reg: self.reg,
            next: None,
            visited: self.visited,
            _state: PhantomData,
        }
    }
}

impl Traverser<Suspended> {
    /// Resume from suspension. The caller (HTTP handler / recovery job) is
    /// responsible for re-populating `ctx.vars` from the persisted
    /// `row_vars` JSONB column before calling this.
    pub fn resume(self) -> Traverser<Running> {
        Traverser {
            def: self.def,
            ctx: self.ctx,
            reg: self.reg,
            next: self.next,
            visited: self.visited,
            _state: PhantomData,
        }
    }
}

#[derive(Debug)]
pub enum StepOutcome {
    Continue(Traverser<Running>),
    Done(Traverser<Completed>),
    Suspended(Traverser<Suspended>, SuspendInfo),
}
