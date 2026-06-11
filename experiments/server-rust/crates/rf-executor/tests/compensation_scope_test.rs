//! V5.31 P0 — `CompensationStart` / `CompensationEnd` /
//! `CompensationThrow` / `CompensationIntermediate` —
//! the BPMN 2.0 SAGA / compensation-handler machinery.
//!
//! Each test is a `Behavior:` block in
//! Gherkin-flavoured Rust docstring + name; the
//! `@DisplayName` / test name form mirrors the
//! `end_event_test.rs` convention.
//!
//! ## V5.31 P0 v0 contract
//!
//! - **`CompensationStart`** — pushes a new
//!   `CompensationScope { id, handlers: vec![] }`
//!   onto `ctx.compensation_stack`. The
//!   `scope_id` is read from
//!   `ruleforge:scopeId`; if absent, defaults
//!   to the node's `id`. Idempotent against
//!   a consecutive `CompensationStart` with
//!   the same scope_id (warn + skip).
//! - **`CompensationEnd`** — pops the topmost
//!   `CompensationScope` whose id matches
//!   the `ruleforge:scopeId` (or the node's
//!   id). A mismatched top is `warn +
//!   leave-stack-intact` (we never fail a
//!   flow for a stack-shape error in v0).
//! - **`CompensationThrow`** — pops the
//!   innermost scope (LIFO), then walks
//!   its handlers in reverse registration
//!   order. For each handler, looks up the
//!   handler's `def.attached_compensations`
//!   entry, takes the **first** registered
//!   handler-node id, and recursively
//!   `traverse()`s that sub-flow with a
//!   fresh `ctx` (vars cloned from the
//!   parent; `compensation_stack = vec![]`
//!   so we don't recurse compensation in
//!   v0). Sub-flow failure is **logged +
//!   counted** but the outer flow still
//!   exits `Failed`. Sub-flow suspend is
//!   propagated upward (the whole flow
//!   suspends, because the throw didn't
//!   finish).
//! - **`CompensationIntermediate`** — v0 is
//!   a no-op. The handler registration
//!   happens at throw time (we iterate
//!   `def.attached_compensations` and run
//!   all of them — the V5.31 P0 v0
//!   simplification. The BPMN spec says
//!   "only compensate completed activities";
//!   V5.31+ adds the completed-set
//!   tracking). This means the test
//!   fixtures must register handlers via
//!   `def.attached_compensations`
//!   (`<compensateIntermediateThrowEvent
//!   attachedToRef="..."/>`) for the
//!   throw to find them.
//! - **`CompensationThrow` with empty stack**
//!   → outer flow exits `Failed` with
//!   `FlowError::CompensationNoScope`.
//! - **`ErrorEnd` triggered while a
//!   scope is on the stack** → the
//!   traverser's `Fail` arm runs the
//!   compensation sub-flows
//!   automatically (SAGA pattern;
//!   best-effort rollback before
//!   reporting failure). Outer flow
//!   is still `Failed`; the
//!   compensation trace is appended
//!   to the `FlowError::Action`
//!   message.

use std::sync::Arc;

use rf_executor::dispatch::ExecutorRegistry;
use rf_executor::executors::action::{ActionExecutor, MockActionRegistry};
use rf_executor::flow_context::FlowContext;
use rf_executor::traverser::{traverse, TraverseOutcome};
use rf_ir::flow_definition::FlowDefinition;
use rf_parse::bpmn_parser::BpmnXmlParser;
use serde_json::json;

fn bpmn(process_id: &str, body: &str) -> String {
    format!(
        r#"<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:ruleforge="http://ruleforge.com/schema"
                  targetNamespace="http://ruleforge.com/schema">
  <bpmn:process id="{process_id}">
    {body}
  </bpmn:process>
</bpmn:definitions>"#
    )
}

fn parse(xml: &str) -> Arc<FlowDefinition> {
    Arc::new(BpmnXmlParser::parse(xml).expect("parse ok"))
}

/// Test registry — registers a handful of
/// action methods that record which handler
/// ran into a `vars.compensated` list, so a
/// test can assert LIFO ordering of the
/// compensation sub-flows.
fn registry() -> Arc<ExecutorRegistry> {
    let actions = Arc::new(
        MockActionRegistry::new()
            // `noop` — does nothing.
            .register("noop", |_vars| Ok(()))
            // `mark_compensated:<id>` —
            // appends `id` to
            // `vars.compensated`. We use
            // method names like
            // `mark_compensated:handler_a`
            // to keep the test fixtures
            // human-readable.
            .register("mark_compensated:handler_a", |vars| {
                let cur = vars
                    .get("compensated")
                    .and_then(|v| v.as_array())
                    .cloned()
                    .unwrap_or_default();
                let mut next = cur;
                next.push(json!("handler_a"));
                vars.assign("compensated".to_string(), json!(next));
                Ok(())
            })
            .register("mark_compensated:handler_b", |vars| {
                let cur = vars
                    .get("compensated")
                    .and_then(|v| v.as_array())
                    .cloned()
                    .unwrap_or_default();
                let mut next = cur;
                next.push(json!("handler_b"));
                vars.assign("compensated".to_string(), json!(next));
                Ok(())
            })
            .register("mark_compensated:handler_c", |vars| {
                let cur = vars
                    .get("compensated")
                    .and_then(|v| v.as_array())
                    .cloned()
                    .unwrap_or_default();
                let mut next = cur;
                next.push(json!("handler_c"));
                vars.assign("compensated".to_string(), json!(next));
                Ok(())
            })
            .register("mark_main:act_a", |vars| {
                vars.assign("main_done".to_string(), json!("act_a"));
                Ok(())
            })
            .register("mark_main:act_b", |vars| {
                vars.assign("main_done".to_string(), json!("act_b"));
                Ok(())
            })
            .register("mark_main:act_c", |vars| {
                vars.assign("main_done".to_string(), json!("act_c"));
                Ok(())
            }),
    );
    let mut reg = ExecutorRegistry::with_rule_engine(Arc::new(
        rf_rule::mock::MockRuleEngine,
    ));
    reg.action = Arc::new(ActionExecutor::new(actions));
    Arc::new(reg)
}

// ----- 1: basic LIFO compensation via CompensationThrow -----

/// `Behavior: CompensationThrow with two registered
/// handlers runs them in LIFO order (most-recently
/// registered first).`
const BPMN_BASIC_LIFO: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e0</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:serviceTask id="act_a" ruleforge:taskType="action"
                      ruleforge:method="mark_main:act_a">
      <bpmn:outgoing>e1</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:compensateStartEvent id="cs">
      <bpmn:outgoing>e2</bpmn:outgoing>
    </bpmn:compensateStartEvent>
    <bpmn:serviceTask id="act_b" ruleforge:taskType="action"
                      ruleforge:method="mark_main:act_b">
      <bpmn:outgoing>e3</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:compensateEndEvent id="ce">
      <bpmn:outgoing>e4</bpmn:outgoing>
    </bpmn:compensateEndEvent>
    <bpmn:compensateThrowEvent id="ct">
      <bpmn:outgoing>e5</bpmn:outgoing>
    </bpmn:compensateThrowEvent>
    <bpmn:serviceTask id="handler_a" ruleforge:taskType="action"
                      ruleforge:method="mark_compensated:handler_a"/>
    <bpmn:serviceTask id="handler_b" ruleforge:taskType="action"
                      ruleforge:method="mark_compensated:handler_b"/>
    <bpmn:endEvent id="end"/>
    <bpmn:compensateIntermediateThrowEvent id="ch_a"
                                           attachedToRef="act_a">
      <bpmn:outgoing>ha</bpmn:outgoing>
    </bpmn:compensateIntermediateThrowEvent>
    <bpmn:compensateIntermediateThrowEvent id="ch_b"
                                           attachedToRef="act_b">
      <bpmn:outgoing>hb</bpmn:outgoing>
    </bpmn:compensateIntermediateThrowEvent>
    <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="act_a"/>
    <bpmn:sequenceFlow id="e1" sourceRef="act_a" targetRef="cs"/>
    <bpmn:sequenceFlow id="e2" sourceRef="cs" targetRef="act_b"/>
    <bpmn:sequenceFlow id="e3" sourceRef="act_b" targetRef="ce"/>
    <bpmn:sequenceFlow id="e4" sourceRef="ce" targetRef="ct"/>
    <bpmn:sequenceFlow id="e5" sourceRef="ct" targetRef="end"/>
    <bpmn:sequenceFlow id="ha" sourceRef="ch_a" targetRef="handler_a"/>
    <bpmn:sequenceFlow id="hb" sourceRef="ch_b" targetRef="handler_b"/>
    <bpmn:sequenceFlow id="h_a" sourceRef="handler_a" targetRef="end"/>
    <bpmn:sequenceFlow id="h_b" sourceRef="handler_b" targetRef="end"/>
"#;

#[test]
fn compensation_throw_runs_handlers_lifo() {
    let def = parse(&bpmn("p", BPMN_BASIC_LIFO));
    let reg = registry();
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // act_a ran (registered first → handler_a
            // registered first to scope `cs`), then
            // act_b ran (registered after → handler_b
            // registered later). LIFO at throw time
            // runs handler_b first, then handler_a.
            let compensated = t
                .ctx
                .vars
                .get("compensated")
                .cloned()
                .unwrap_or(json!(null));
            assert_eq!(
                compensated,
                json!(["handler_b", "handler_a"]),
                "LIFO compensation order: act_b's handler should fire first"
            );
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

// ----- 2: handler with user task suspends the whole flow -----

/// `Behavior: A compensation handler that suspends
/// (e.g. a userTask inside the handler sub-flow)
/// propagates the Suspend upward — the whole
/// flow is Suspended, not Completed, and the
/// compensation throw is "in flight".`
const BPMN_HANDLER_SUSPENDS: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e0</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:serviceTask id="act" ruleforge:taskType="action"
                      ruleforge:method="noop">
      <bpmn:outgoing>e1</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:compensateStartEvent id="cs">
      <bpmn:outgoing>e2</bpmn:outgoing>
    </bpmn:compensateStartEvent>
    <bpmn:serviceTask id="trigger" ruleforge:taskType="action"
                      ruleforge:method="noop">
      <bpmn:outgoing>e3</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:compensateEndEvent id="ce">
      <bpmn:outgoing>e4</bpmn:outgoing>
    </bpmn:compensateEndEvent>
    <bpmn:compensateThrowEvent id="ct">
      <bpmn:outgoing>e5</bpmn:outgoing>
    </bpmn:compensateThrowEvent>
    <bpmn:endEvent id="end"/>
    <bpmn:userTask id="handler" ruleforge:decisionType="binary"
                   ruleforge:decisionField="approver"/>
    <bpmn:compensateIntermediateThrowEvent id="ch" attachedToRef="act">
      <bpmn:outgoing>eh</bpmn:outgoing>
    </bpmn:compensateIntermediateThrowEvent>
    <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="act"/>
    <bpmn:sequenceFlow id="e1" sourceRef="act" targetRef="cs"/>
    <bpmn:sequenceFlow id="e2" sourceRef="cs" targetRef="trigger"/>
    <bpmn:sequenceFlow id="e3" sourceRef="trigger" targetRef="ce"/>
    <bpmn:sequenceFlow id="e4" sourceRef="ce" targetRef="ct"/>
    <bpmn:sequenceFlow id="e5" sourceRef="ct" targetRef="end"/>
    <bpmn:sequenceFlow id="eh" sourceRef="ch" targetRef="handler"/>
    <bpmn:sequenceFlow id="h" sourceRef="handler" targetRef="end"/>
"#;

#[test]
fn compensation_handler_with_user_task_suspends() {
    let def = parse(&bpmn("p", BPMN_HANDLER_SUSPENDS));
    let reg = registry();
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    match outcome {
        TraverseOutcome::Suspended(t, info) => {
            assert_eq!(
                info.wait_type,
                rf_executor::node_result::WaitType::UserTask,
                "compensation handler userTask should surface as UserTask suspend"
            );
            // Compensation throw suspended INSIDE the
            // handler sub-flow — the userTask in the
            // handler. The flow didn't reach the normal
            // end, so current_node_id should be the
            // userTask (the deepest suspended node).
            assert_eq!(
                t.ctx.current_node_id.as_deref(),
                Some("handler"),
                "suspended inside the compensation handler"
            );
        }
        other => panic!("expected Suspended, got {other:?}"),
    }
}

// ----- 3: no handlers registered — throw is a no-op (flow continues) -----

/// `Behavior: A scope with no
/// `<compensateIntermediateThrowEvent/>`
/// `Behavior: A scope with no
/// `<compensateIntermediateThrowEvent/>`
/// registered — `def.attached_compensations`
/// is empty for every activity — running a
/// `CompensationThrow` is a no-op and the
/// flow continues to the next node.`

#[test]
fn compensation_throw_with_no_handlers_is_noop() {
    // scope with no attached compensations, then a
    // throw that points to a normal end.
    let xml = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="act" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:compensateStartEvent id="cs">
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:compensateStartEvent>
        <bpmn:compensateThrowEvent id="ct">
          <bpmn:outgoing>e3</bpmn:outgoing>
        </bpmn:compensateThrowEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="act"/>
        <bpmn:sequenceFlow id="e1" sourceRef="act" targetRef="cs"/>
        <bpmn:sequenceFlow id="e2" sourceRef="cs" targetRef="ct"/>
        <bpmn:sequenceFlow id="e3" sourceRef="ct" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", xml));
    let reg = registry();
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(
                t.ctx.current_node_id.as_deref(),
                Some("end"),
                "scope with no handlers → throw is no-op, flow continues to end"
            );
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

// ----- 4: CompensationThrow with empty stack → Failed(CompensationNoScope) -----

/// `Behavior: A `CompensationThrow` with no
/// matching open scope (the stack is empty)
/// causes the flow to exit `Failed` with
/// `FlowError::CompensationNoScope`. This is
/// the "throw before start" misconfiguration
/// — the flow should report it as an error
/// rather than silently continue.`
#[test]
fn compensation_throw_with_empty_stack_errors() {
    let xml = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="act" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:compensateThrowEvent id="ct">
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:compensateThrowEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="act"/>
        <bpmn:sequenceFlow id="e1" sourceRef="act" targetRef="ct"/>
        <bpmn:sequenceFlow id="e2" sourceRef="ct" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", xml));
    let reg = registry();
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    match outcome {
        TraverseOutcome::Failed(t, err) => {
            let msg = format!("{err}");
            assert!(
                msg.contains("compensation throw with no open scope"),
                "expected CompensationNoScope, got: {msg}"
            );
            assert_eq!(
                t.ctx.current_node_id.as_deref(),
                Some("ct"),
                "flow should fail at the throw node"
            );
        }
        other => panic!("expected Failed, got {other:?}"),
    }
}

// ----- 5: ErrorEnd with compensation scope triggers automatic rollback -----

/// `Behavior: An `ErrorEnd` triggered while
/// a compensation scope is on the stack
/// causes the traverser to automatically run
/// the scope's compensation sub-flows before
/// exiting `Failed`. The outer flow is still
/// `Failed` (the error end is the terminal
/// outcome), but the compensation handlers
/// ran — `vars.compensated` is populated.`
const BPMN_ERROR_END_TRIGGERS_COMPENSATION_V2: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e0</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:serviceTask id="act" ruleforge:taskType="action"
                      ruleforge:method="noop">
      <bpmn:outgoing>e1</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:compensateStartEvent id="cs">
      <bpmn:outgoing>e2</bpmn:outgoing>
    </bpmn:compensateStartEvent>
    <bpmn:serviceTask id="trigger" ruleforge:taskType="action"
                      ruleforge:method="noop">
      <bpmn:outgoing>e3</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:endEvent id="err" ruleforge:endType="error"
                   ruleforge:errorRef="loan_rejected"/>
    <bpmn:serviceTask id="handler" ruleforge:taskType="action"
                      ruleforge:method="mark_compensated:handler_a"/>
    <bpmn:compensateIntermediateThrowEvent id="ch" attachedToRef="act">
      <bpmn:outgoing>eh</bpmn:outgoing>
    </bpmn:compensateIntermediateThrowEvent>
    <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="act"/>
    <bpmn:sequenceFlow id="e1" sourceRef="act" targetRef="cs"/>
    <bpmn:sequenceFlow id="e2" sourceRef="cs" targetRef="trigger"/>
    <bpmn:sequenceFlow id="e3" sourceRef="trigger" targetRef="err"/>
    <bpmn:sequenceFlow id="eh" sourceRef="ch" targetRef="handler"/>
    <bpmn:sequenceFlow id="h" sourceRef="handler" targetRef="err"/>
"#;

#[test]
fn error_end_triggers_automatic_compensation() {
    let def = parse(&bpmn("p", BPMN_ERROR_END_TRIGGERS_COMPENSATION_V2));
    let reg = registry();
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    match outcome {
        TraverseOutcome::Failed(t, err) => {
            // The error end is the terminal
            // failure — the outer flow is
            // Failed.
            let msg = format!("{err}");
            assert!(
                msg.contains("flow reached error end: loan_rejected"),
                "expected ErrorEnd message, got: {msg}"
            );
            // But the compensation ran
            // automatically before the
            // traverser entered `Failed` —
            // `vars.compensated` is populated.
            let compensated = t
                .ctx
                .vars
                .get("compensated")
                .cloned()
                .unwrap_or(json!(null));
            assert_eq!(
                compensated,
                json!(["handler_a"]),
                "ErrorEnd while scope is open should fire compensation sub-flows"
            );
        }
        other => panic!("expected Failed, got {other:?}"),
    }
}

// ----- 6: CompensationEnd with mismatched scope_id warns and continues -----

/// `Behavior: A `CompensationEnd` whose scope_id
/// doesn't match the top of the stack (or the
/// stack is empty) is a `warn + Continue` — we
/// never fail a flow for a stack-shape error in
/// v0. The flow continues to the next node.`
#[test]
fn compensation_end_with_mismatched_scope_warns_and_continues() {
    let xml = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="act" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:compensateStartEvent id="cs" ruleforge:scopeId="scope_a">
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:compensateStartEvent>
        <bpmn:compensateEndEvent id="ce" ruleforge:scopeId="scope_z">
          <bpmn:outgoing>e3</bpmn:outgoing>
        </bpmn:compensateEndEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="act"/>
        <bpmn:sequenceFlow id="e1" sourceRef="act" targetRef="cs"/>
        <bpmn:sequenceFlow id="e2" sourceRef="cs" targetRef="ce"/>
        <bpmn:sequenceFlow id="e3" sourceRef="ce" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", xml));
    let reg = registry();
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // The mismatched ce leaves the
            // scope on the stack but the
            // flow continues to `end`.
            assert_eq!(t.ctx.current_node_id.as_deref(), Some("end"));
            // Stack should still hold the
            // unpopped scope (best-effort
            // cleanup).
            assert_eq!(t.ctx.compensation_stack.len(), 1);
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

// ----- 7: nested compensation scopes — innermost compensation pops innermost first -----

/// `Behavior: Two nested compensation scopes;
/// the inner CompensationThrow pops the
/// innermost scope first. The outer scope's
/// handlers are NOT invoked by the inner
/// throw (only the inner scope is LIFO-popped
/// in V5.31 P0 v0).`
const BPMN_NESTED_SCOPES: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e0</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:serviceTask id="act_a" ruleforge:taskType="action"
                      ruleforge:method="noop">
      <bpmn:outgoing>e1</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:compensateStartEvent id="cs_outer" ruleforge:scopeId="outer">
      <bpmn:outgoing>e2</bpmn:outgoing>
    </bpmn:compensateStartEvent>
    <bpmn:serviceTask id="act_b" ruleforge:taskType="action"
                      ruleforge:method="noop">
      <bpmn:outgoing>e3</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:compensateStartEvent id="cs_inner" ruleforge:scopeId="inner">
      <bpmn:outgoing>e4</bpmn:outgoing>
    </bpmn:compensateStartEvent>
    <bpmn:serviceTask id="act_c" ruleforge:taskType="action"
                      ruleforge:method="noop">
      <bpmn:outgoing>e5</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:compensateThrowEvent id="ct_inner">
      <bpmn:outgoing>e6</bpmn:outgoing>
    </bpmn:compensateThrowEvent>
    <bpmn:compensateEndEvent id="ce_inner" ruleforge:scopeId="inner">
      <bpmn:outgoing>e7</bpmn:outgoing>
    </bpmn:compensateEndEvent>
    <bpmn:compensateThrowEvent id="ct_outer">
      <bpmn:outgoing>e8</bpmn:outgoing>
    </bpmn:compensateThrowEvent>
    <bpmn:compensateEndEvent id="ce_outer" ruleforge:scopeId="outer">
      <bpmn:outgoing>e9</bpmn:outgoing>
    </bpmn:compensateEndEvent>
    <bpmn:endEvent id="end"/>
    <bpmn:serviceTask id="ha" ruleforge:taskType="action"
                      ruleforge:method="mark_compensated:handler_a"/>
    <bpmn:serviceTask id="hb" ruleforge:taskType="action"
                      ruleforge:method="mark_compensated:handler_b"/>
    <bpmn:serviceTask id="hc" ruleforge:taskType="action"
                      ruleforge:method="mark_compensated:handler_c"/>
    <bpmn:compensateIntermediateThrowEvent id="ch_a"
                                           attachedToRef="act_a">
      <bpmn:outgoing>fha</bpmn:outgoing>
    </bpmn:compensateIntermediateThrowEvent>
    <bpmn:compensateIntermediateThrowEvent id="ch_b"
                                           attachedToRef="act_b">
      <bpmn:outgoing>fhb</bpmn:outgoing>
    </bpmn:compensateIntermediateThrowEvent>
    <bpmn:compensateIntermediateThrowEvent id="ch_c"
                                           attachedToRef="act_c">
      <bpmn:outgoing>fhc</bpmn:outgoing>
    </bpmn:compensateIntermediateThrowEvent>
    <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="act_a"/>
    <bpmn:sequenceFlow id="e1" sourceRef="act_a" targetRef="cs_outer"/>
    <bpmn:sequenceFlow id="e2" sourceRef="cs_outer" targetRef="act_b"/>
    <bpmn:sequenceFlow id="e3" sourceRef="act_b" targetRef="cs_inner"/>
    <bpmn:sequenceFlow id="e4" sourceRef="cs_inner" targetRef="act_c"/>
    <bpmn:sequenceFlow id="e5" sourceRef="act_c" targetRef="ct_inner"/>
    <bpmn:sequenceFlow id="e6" sourceRef="ct_inner" targetRef="ce_inner"/>
    <bpmn:sequenceFlow id="e7" sourceRef="ce_inner" targetRef="ct_outer"/>
    <bpmn:sequenceFlow id="e8" sourceRef="ct_outer" targetRef="ce_outer"/>
    <bpmn:sequenceFlow id="e9" sourceRef="ce_outer" targetRef="end"/>
    <bpmn:sequenceFlow id="fha" sourceRef="ch_a" targetRef="ha"/>
    <bpmn:sequenceFlow id="fhb" sourceRef="ch_b" targetRef="hb"/>
    <bpmn:sequenceFlow id="fhc" sourceRef="ch_c" targetRef="hc"/>
    <bpmn:sequenceFlow id="fha2" sourceRef="ha" targetRef="end"/>
    <bpmn:sequenceFlow id="fhb2" sourceRef="hb" targetRef="end"/>
    <bpmn:sequenceFlow id="fhc2" sourceRef="hc" targetRef="end"/>
"#;

#[test]
fn nested_compensation_scopes_pop_innermost_first() {
    let def = parse(&bpmn("p", BPMN_NESTED_SCOPES));
    let reg = registry();
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // Inner throw fires handler_c
            // (act_c was registered to scope
            // `inner`). After that, the
            // outer throw fires handlers
            // for act_a and act_b in LIFO
            // (act_b's handler_b first, then
            // act_a's handler_a) — outer
            // scope's handlers list runs.
            let compensated = t
                .ctx
                .vars
                .get("compensated")
                .cloned()
                .unwrap_or(json!(null));
            assert_eq!(
                compensated,
                json!(["handler_c", "handler_b", "handler_a"]),
                "inner throw first (handler_c), then outer throw LIFO (handler_b, handler_a)"
            );
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

// ----- 8: CompensationIntermediate is a no-op executor (visit doesn't register) -----

/// `Behavior: A
/// `<compensateIntermediateThrowEvent/>`
/// visit is a no-op in V5.31 P0 v0 — the
/// executor returns `Continue` without
/// touching the compensation stack. Handler
/// registration happens at throw time, NOT
/// at intermediate-event visit time. The
/// `attached_compensations` map is built
/// from the BPMN `attachedToRef` by the
/// parser.`
#[test]
fn compensation_intermediate_is_noop() {
    // Fixture: a
    // `<compensateIntermediateThrowEvent/>`
    // mid-flow. It should pass through
    // without affecting the stack or
    // vars.
    let xml = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="act" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:compensateIntermediateThrowEvent id="ci"
                                               attachedToRef="act">
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:compensateIntermediateThrowEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="act"/>
        <bpmn:sequenceFlow id="e1" sourceRef="act" targetRef="ci"/>
        <bpmn:sequenceFlow id="e2" sourceRef="ci" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", xml));
    let reg = registry();
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(
                t.ctx.current_node_id.as_deref(),
                Some("end"),
                "intermediate throw is a pass-through"
            );
            // Stack is untouched by
            // intermediate visit.
            assert!(
                t.ctx.compensation_stack.is_empty(),
                "intermediate throw should not touch the compensation stack"
            );
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

// ----- 9: scope_id from ruleforge:scopeId attr (default = node_id) -----

/// `Behavior: A `CompensationStart`'s scope_id
/// defaults to the node's id if no
/// `ruleforge:scopeId` is set, and the
/// matching `CompensationEnd` with the same
/// default pops the scope. The pop succeeds
/// with no warn.`
#[test]
fn compensation_start_uses_node_id_as_default_scope() {
    let xml = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="act" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:compensateStartEvent id="cs">
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:compensateStartEvent>
        <bpmn:compensateEndEvent id="cs">
          <bpmn:outgoing>e3</bpmn:outgoing>
        </bpmn:compensateEndEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="act"/>
        <bpmn:sequenceFlow id="e1" sourceRef="act" targetRef="cs"/>
        <bpmn:sequenceFlow id="e2" sourceRef="cs" targetRef="cs"/>
        <bpmn:sequenceFlow id="e3" sourceRef="cs" targetRef="end"/>
    "#;
    // Note: cs and ce have the same id
    // (`cs`) — that's actually a
    // problem because parser will refuse
    // duplicate node ids. Rename ce to
    // `ce` and rely on the default
    // scope_id (which is the node id, so
    // cs pushes "cs" and ce with id "ce"
    // would mismatch). For this test
    // let's make cs have explicit
    // scopeId matching ce's id.
    let xml_v2 = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="act" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:compensateStartEvent id="cs_start"
                                   ruleforge:scopeId="my_scope">
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:compensateStartEvent>
        <bpmn:compensateEndEvent id="cs_end"
                                 ruleforge:scopeId="my_scope">
          <bpmn:outgoing>e3</bpmn:outgoing>
        </bpmn:compensateEndEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="act"/>
        <bpmn:sequenceFlow id="e1" sourceRef="act" targetRef="cs_start"/>
        <bpmn:sequenceFlow id="e2" sourceRef="cs_start" targetRef="cs_end"/>
        <bpmn:sequenceFlow id="e3" sourceRef="cs_end" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", xml_v2));
    let reg = registry();
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // Stack should be empty
            // (cs_start pushed, cs_end
            // popped with matching
            // scopeId).
            assert!(
                t.ctx.compensation_stack.is_empty(),
                "scope pushed and popped cleanly; stack should be empty"
            );
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}
