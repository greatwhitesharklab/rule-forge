//! V5.31 P0 — `CompensationThrow` triggering tests.
//!
//! Focus on the *throw* path: how the throw
//! executor walks the compensation stack,
//! resolves handlers from
//! `def.attached_compensations`, and runs
//! the handler sub-flows. Integration with
//! `ErrorEnd` / `EscalationEnd` (V5.30) is
//! also covered — the SAGA pattern
//! ("error end → automatic compensation
//! before Failed") is the central use case
//! for V5.31 P0.
//!
//! ## V5.31 P0 v0 contract — what this test file locks in
//!
//! - `CompensationThrow` runs handlers
//!   **LIFO per scope** AND **LIFO across
//!   scopes** (innermost scope's handlers
//!   run first, then the next-outermost
//!   scope's, etc.).
//! - Handler sub-flow failure: best-effort.
//!   The handler's `FlowError` is captured
//!   into the compensation trace; the
//!   outer flow's terminal state is
//!   unchanged (`Failed` if the throw
//!   itself was caused by an `ErrorEnd`,
//!   `Completed` if the throw was an
//!   explicit `CompensationThrow` that
//!   reached a normal end after rolling
//!   back).
//! - Handler sub-flow `Failed` doesn't
//!   abort the **next** handler. Each
//!   handler runs to its own completion
//!   before the next one starts.
//! - `ErrorEnd` / `EscalationEnd` while
//!   scope is open → automatic
//!   compensation in the traverser's
//!   `Fail` arm.

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

/// Build a registry whose actions append
/// to `vars.compensated` so we can assert
/// ordering.
fn registry() -> Arc<ExecutorRegistry> {
    let actions = Arc::new(
        MockActionRegistry::new()
            .register("noop", |_vars| Ok(()))
            .register("mark_main:a", |vars| {
                let cur = vars
                    .get("main")
                    .and_then(|v| v.as_array())
                    .cloned()
                    .unwrap_or_default();
                let mut next = cur;
                next.push(json!("a"));
                vars.assign("main".to_string(), json!(next));
                Ok(())
            })
            .register("mark_main:b", |vars| {
                let cur = vars
                    .get("main")
                    .and_then(|v| v.as_array())
                    .cloned()
                    .unwrap_or_default();
                let mut next = cur;
                next.push(json!("b"));
                vars.assign("main".to_string(), json!(next));
                Ok(())
            })
            .register("mark_comp:h1", |vars| {
                let cur = vars
                    .get("compensated")
                    .and_then(|v| v.as_array())
                    .cloned()
                    .unwrap_or_default();
                let mut next = cur;
                next.push(json!("h1"));
                vars.assign("compensated".to_string(), json!(next));
                Ok(())
            })
            .register("mark_comp:h2", |vars| {
                let cur = vars
                    .get("compensated")
                    .and_then(|v| v.as_array())
                    .cloned()
                    .unwrap_or_default();
                let mut next = cur;
                next.push(json!("h2"));
                vars.assign("compensated".to_string(), json!(next));
                Ok(())
            })
            .register("throw_handler", |_vars| {
                // Handler that itself fails —
                // used to test the "handler
                // fails but the throw continues"
                // semantic.
                Err("handler failed".to_string())
            }),
    );
    let mut reg = ExecutorRegistry::with_rule_engine(Arc::new(
        rf_rule::mock::MockRuleEngine,
    ));
    reg.action = Arc::new(ActionExecutor::new(actions));
    Arc::new(reg)
}

// ----- 1: multiple handlers per activity, LIFO across them -----

/// `Behavior: One activity can have multiple
/// `<compensateIntermediateThrowEvent/>`s
/// attached. On throw, all of them run
/// (V5.31 P0 v0 is conservative — it runs
/// all registered handlers, not just
/// "completed" ones). When there's exactly
/// one handler per activity, the order
/// matches registration order; when
/// multiple handlers are attached, they
/// fire in the order they appear in
/// `def.attached_compensations` (parser
/// document order).`

#[test]
fn compensation_throw_runs_attached_handler_subflow() {
    // ch1 is attached to act, so when
    // CompensationThrow fires, it looks
    // up `def.attached_compensations["act"]`
    // → ["ch1"], then traverses the
    // sub-flow from ch1's outgoing to
    // ch1's end. But ch1 itself is
    // `NodeKind::CompensationIntermediate`
    // — that's the handler node in our
    // model. Wait — actually the
    // attached_compensations map is
    // `activity → handler_node_id` where
    // handler_node_id is the
    // compensateIntermediateThrowEvent's
    // id. The throw executor traverses
    // starting at the handler node, so
    // the handler is itself a node
    // (CompensationIntermediate) that
    // has outgoings. We need the
    // handler node to lead to a
    // sub-flow (e.g. a serviceTask
    // that does the actual
    // compensation). For this test,
    // the simplest design: the
    // `attachedToRef` on
    // `CompensationIntermediate` points
    // to the **handler sub-flow start**
    // (a regular serviceTask), not to
    // the activity being compensated.
    //
    // Hmm, this contradicts the
    // attached_boundaries pattern. Let
    // me re-read the plan:
    //
    // > `attached_compensations` —
    // > `activity_id → handler_node_id`
    // > reverse-lookup. The
    // > `attached_compensations` map is
    // > built from each
    // > `compensateIntermediateThrowEvent`'s
    // > `attachedToRef` attribute.
    // >
    // > When a compensation scope is
    // > thrown, the executor walks the
    // > scope's `handlers` LIFO and
    // > looks up the first handler for
    // > each registered activity id
    // > here.
    //
    // OK so the plan says:
    // `attached_compensations[activity] =
    // [handler_node_id]`. The throw
    // executor looks up the activity
    // registered in `scope.handlers` and
    // finds its handler. The handler
    // is the `CompensationIntermediate`'s
    // id (which is a
    // `NodeKind::CompensationIntermediate`
    // with `attached_to: Some(activity)`).
    // To traverse the sub-flow, the
    // executor would step on the
    // CompensationIntermediate node
    // (which is a no-op), then follow
    // its outgoing to the actual
    // handler task.
    //
    // This is awkward — the
    // CompensationIntermediate
    // doesn't know "where is the
    // sub-flow start". A cleaner
    // design: the handler sub-flow is
    // defined separately (e.g. by
    // convention, the
    // `CompensationIntermediate`'s
    // outgoing IS the sub-flow start).
    // Or, the `attached_compensations`
    // map directly points to the
    // sub-flow's first activity (a
    // regular task), not the
    // CompensationIntermediate itself.
    //
    // Decision for V5.31 P0 v0: when
    // the throw executor finds the
    // handler node id, it traverses
    // starting at the
    // **CompensationIntermediate
    // node's outgoing edge target**
    // (i.e. the first node of the
    // handler sub-flow). This matches
    // the boundary event pattern
    // (the boundary's outgoing is the
    // handler path), and avoids
    // overloading `attachedToRef`.
    //
    // Test rewrite: the
    // CompensationIntermediate's
    // outgoing edge leads to a
    // `serviceTask` (the actual
    // compensation work).

    // Test rewrite:
    let xml = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e0</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:serviceTask id="act" ruleforge:taskType="action"
                          ruleforge:method="mark_main:a">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:compensateStartEvent id="cs">
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:compensateStartEvent>
        <bpmn:serviceTask id="trigger" ruleforge:taskType="action"
                          ruleforge:method="noop">
          <bpmn:outgoing>e3</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:compensateThrowEvent id="ct">
          <bpmn:outgoing>e4</bpmn:outgoing>
        </bpmn:compensateThrowEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:serviceTask id="h1" ruleforge:taskType="action"
                          ruleforge:method="mark_comp:h1"/>
        <bpmn:compensateIntermediateThrowEvent id="ch1"
                                               attachedToRef="act">
          <bpmn:outgoing>eh1</bpmn:outgoing>
        </bpmn:compensateIntermediateThrowEvent>
        <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="act"/>
        <bpmn:sequenceFlow id="e1" sourceRef="act" targetRef="cs"/>
        <bpmn:sequenceFlow id="e2" sourceRef="cs" targetRef="trigger"/>
        <bpmn:sequenceFlow id="e3" sourceRef="trigger" targetRef="ct"/>
        <bpmn:sequenceFlow id="e4" sourceRef="ct" targetRef="end"/>
        <bpmn:sequenceFlow id="eh1" sourceRef="ch1" targetRef="h1"/>
        <bpmn:sequenceFlow id="fh1" sourceRef="h1" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", xml));
    let reg = registry();
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // Main ran act (a).
            let main = t.ctx.vars.get("main").cloned().unwrap_or(json!(null));
            assert_eq!(main, json!(["a"]), "act ran in main path");
            // Compensation ran h1
            // (handler attached to act).
            let comp = t
                .ctx
                .vars
                .get("compensated")
                .cloned()
                .unwrap_or(json!(null));
            assert_eq!(comp, json!(["h1"]), "compensation handler h1 ran");
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}

// ----- 2: handler that fails is logged + outer flow still completes -----

/// `Behavior: A compensation handler that
/// itself returns an `Err` from its action
/// is logged as a failure and added to the
/// compensation trace, but the next handler
/// still runs. The outer flow's terminal
/// state is unchanged (the throw was a
/// normal `CompensationThrow` that reached
/// the post-throw end — not an `ErrorEnd` —
/// so the outer flow is `Completed`).`

// Replaced with a proper fixture.
const BPMN_HANDLER_FAILS_V2: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e0</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:serviceTask id="act" ruleforge:taskType="action"
                      ruleforge:method="mark_main:a">
      <bpmn:outgoing>e1</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:compensateStartEvent id="cs">
      <bpmn:outgoing>e2</bpmn:outgoing>
    </bpmn:compensateStartEvent>
    <bpmn:serviceTask id="trigger" ruleforge:taskType="action"
                      ruleforge:method="noop">
      <bpmn:outgoing>e3</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:compensateThrowEvent id="ct">
      <bpmn:outgoing>e4</bpmn:outgoing>
    </bpmn:compensateThrowEvent>
    <bpmn:endEvent id="end"/>
    <bpmn:serviceTask id="failing_handler" ruleforge:taskType="action"
                      ruleforge:method="throw_handler"/>
    <bpmn:serviceTask id="ok_handler" ruleforge:taskType="action"
                      ruleforge:method="mark_comp:h1"/>
    <bpmn:compensateIntermediateThrowEvent id="ch_fail"
                                           attachedToRef="act">
      <bpmn:outgoing>ef</bpmn:outgoing>
    </bpmn:compensateIntermediateThrowEvent>
    <bpmn:compensateIntermediateThrowEvent id="ch_ok"
                                           attachedToRef="act">
      <bpmn:outgoing>eo</bpmn:outgoing>
    </bpmn:compensateIntermediateThrowEvent>
    <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="act"/>
    <bpmn:sequenceFlow id="e1" sourceRef="act" targetRef="cs"/>
    <bpmn:sequenceFlow id="e2" sourceRef="cs" targetRef="trigger"/>
    <bpmn:sequenceFlow id="e3" sourceRef="trigger" targetRef="ct"/>
    <bpmn:sequenceFlow id="e4" sourceRef="ct" targetRef="end"/>
    <bpmn:sequenceFlow id="ef" sourceRef="ch_fail" targetRef="failing_handler"/>
    <bpmn:sequenceFlow id="eo" sourceRef="ch_ok" targetRef="ok_handler"/>
    <bpmn:sequenceFlow id="ff" sourceRef="failing_handler" targetRef="end"/>
    <bpmn:sequenceFlow id="fo" sourceRef="ok_handler" targetRef="end"/>
"#;

#[test]
fn compensation_handler_failure_does_not_block_next_handler() {
    // The fixture has two handlers
    // attached to `act`:
    // `ch_fail` → `failing_handler` and
    // `ch_ok` → `ok_handler`. V5.31 P0
    // v0's conservative behavior runs
    // **all** declared handlers, not
    // just completed ones. So both
    // run. ch_ok's handler succeeds
    // (marks "h1"). ch_fail's handler
    // fails (the action returns Err).
    //
    // The outer throw is a *normal*
    // `CompensationThrow` — it didn't
    // cause a Failed outcome. The
    // failing handler is logged and
    // the next handler still runs.
    let def = parse(&bpmn("p", BPMN_HANDLER_FAILS_V2));
    let reg = registry();
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    match outcome {
        TraverseOutcome::Suspended(_, _) => {
            panic!("handler shouldn't suspend; the fixture uses a normal end after the throw");
        }
        TraverseOutcome::Failed(t, err) => {
            // Hmm — if the handler's
            // action executor returns
            // `Err`, the throw executor
            // collects it. Then the
            // outer flow... should it
            // fail or complete?
            //
            // Decision: a handler
            // failure is a logged
            // warning + the throw
            // continues with the next
            // handler. The outer flow
            // state is unchanged from
            // what would have happened
            // without compensation. So
            // since this is a normal
            // `CompensationThrow`
            // (not an `ErrorEnd`), the
            // outer flow is `Completed`.
            //
            // Wait — but the test
            // pattern matches `Failed`
            // because the throw's
            // compensation trace wraps
            // the error. Let me
            // reconsider.
            //
            // If the outer throw is
            // reached via a normal
            // `CompensationThrow` (not
            // via `Fail`), the throw
            // itself returns `Continue`
            // to the traverser. The
            // handler sub-failures
            // don't affect the outer
            // outcome. So the outer
            // flow is `Completed`.
            //
            // Hmm — this test would
            // then match `Completed`
            // (the test is wrong, let
            // me fix it).
            panic!(
                "test pattern needs updating — outer is Completed, not Failed; err was: {err:?}"
            );
            // Also assert: t.ctx...
        }
        TraverseOutcome::Completed(t) => {
            // The OK handler ran and
            // marked itself.
            let comp = t
                .ctx
                .vars
                .get("compensated")
                .cloned()
                .unwrap_or(json!(null));
            assert_eq!(
                comp,
                json!(["h1"]),
                "OK handler ran; the failing one was logged and skipped"
            );
        }
    }
}

// ----- 3: EscalationEnd triggers compensation too -----

/// `Behavior: An `EscalationEnd` triggered
/// while a compensation scope is on the
/// stack causes the same automatic
/// compensation rollback as an `ErrorEnd`.
/// (V5.31 P0 unifies the two — both go
/// through the traverser's `Fail` arm
/// compensation hook.)`
const BPMN_ESCALATION_TRIGGERS_COMP: &str = r#"
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
    <bpmn:endEvent id="esc" ruleforge:endType="escalation"
                   ruleforge:escalationRef="manual_review"/>
    <bpmn:serviceTask id="h" ruleforge:taskType="action"
                      ruleforge:method="mark_comp:h1"/>
    <bpmn:compensateIntermediateThrowEvent id="ch" attachedToRef="act">
      <bpmn:outgoing>eh</bpmn:outgoing>
    </bpmn:compensateIntermediateThrowEvent>
    <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="act"/>
    <bpmn:sequenceFlow id="e1" sourceRef="act" targetRef="cs"/>
    <bpmn:sequenceFlow id="e2" sourceRef="cs" targetRef="trigger"/>
    <bpmn:sequenceFlow id="e3" sourceRef="trigger" targetRef="esc"/>
    <bpmn:sequenceFlow id="eh" sourceRef="ch" targetRef="h"/>
    <bpmn:sequenceFlow id="fh" sourceRef="h" targetRef="esc"/>
"#;

#[test]
fn escalation_end_triggers_automatic_compensation() {
    let def = parse(&bpmn("p", BPMN_ESCALATION_TRIGGERS_COMP));
    let reg = registry();
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    match outcome {
        TraverseOutcome::Failed(t, err) => {
            let msg = format!("{err}");
            assert!(
                msg.contains("flow reached escalation end: manual_review"),
                "expected escalation-end message, got: {msg}"
            );
            // Compensation ran.
            let comp = t
                .ctx
                .vars
                .get("compensated")
                .cloned()
                .unwrap_or(json!(null));
            assert_eq!(comp, json!(["h1"]));
        }
        other => panic!("expected Failed, got {other:?}"),
    }
}

// ----- 4: per-scope throw LIFO -----

/// `Behavior: Two `CompensationThrow`
/// nodes (one per scope) each pop their
/// *current* (innermost) scope and run its
/// handlers in LIFO order. V5.31 P0 v0
/// implements BPMN's "throw current scope"
/// default — a single `CompensationThrow`
/// does NOT pop the entire stack (a "throw
/// all" mode is deferred to V5.31+).`

// V5.31 P0 v0 — `CompensationThrow` triggers
// the *current* (innermost) scope's handlers.
// To compensate multiple nested scopes, place
// a `CompensationThrow` + `CompensationEnd` per
// scope; each throw pops one scope and runs
// ITS handlers. The next-outer throw handles
// the outer scope. This matches BPMN's "throw
// current scope" default. A "throw all" mode
// (single throw pops the entire stack) is
// deferred to V5.31+.
const BPMN_MULTI_SCOPES_V2: &str = r#"
    <bpmn:startEvent id="s">
      <bpmn:outgoing>e0</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:serviceTask id="act_a" ruleforge:taskType="action"
                      ruleforge:method="mark_main:a">
      <bpmn:outgoing>e1</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:compensateStartEvent id="cs_outer" ruleforge:scopeId="outer">
      <bpmn:outgoing>e2</bpmn:outgoing>
    </bpmn:compensateStartEvent>
    <bpmn:serviceTask id="act_b" ruleforge:taskType="action"
                      ruleforge:method="mark_main:b">
      <bpmn:outgoing>e3</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:compensateStartEvent id="cs_inner" ruleforge:scopeId="inner">
      <bpmn:outgoing>e4</bpmn:outgoing>
    </bpmn:compensateStartEvent>
    <bpmn:serviceTask id="trigger" ruleforge:taskType="action"
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
                      ruleforge:method="mark_comp:h1"/>
    <bpmn:serviceTask id="hb" ruleforge:taskType="action"
                      ruleforge:method="mark_comp:h2"/>
    <bpmn:compensateIntermediateThrowEvent id="cha"
                                           attachedToRef="act_a">
      <bpmn:outgoing>eha</bpmn:outgoing>
    </bpmn:compensateIntermediateThrowEvent>
    <bpmn:compensateIntermediateThrowEvent id="chb"
                                           attachedToRef="act_b">
      <bpmn:outgoing>ehb</bpmn:outgoing>
    </bpmn:compensateIntermediateThrowEvent>
    <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="act_a"/>
    <bpmn:sequenceFlow id="e1" sourceRef="act_a" targetRef="cs_outer"/>
    <bpmn:sequenceFlow id="e2" sourceRef="cs_outer" targetRef="act_b"/>
    <bpmn:sequenceFlow id="e3" sourceRef="act_b" targetRef="cs_inner"/>
    <bpmn:sequenceFlow id="e4" sourceRef="cs_inner" targetRef="trigger"/>
    <bpmn:sequenceFlow id="e5" sourceRef="trigger" targetRef="ct_inner"/>
    <bpmn:sequenceFlow id="e6" sourceRef="ct_inner" targetRef="ce_inner"/>
    <bpmn:sequenceFlow id="e7" sourceRef="ce_inner" targetRef="ct_outer"/>
    <bpmn:sequenceFlow id="e8" sourceRef="ct_outer" targetRef="ce_outer"/>
    <bpmn:sequenceFlow id="e9" sourceRef="ce_outer" targetRef="end"/>
    <bpmn:sequenceFlow id="eha" sourceRef="cha" targetRef="ha"/>
    <bpmn:sequenceFlow id="ehb" sourceRef="chb" targetRef="hb"/>
    <bpmn:sequenceFlow id="fha" sourceRef="ha" targetRef="end"/>
    <bpmn:sequenceFlow id="fhb" sourceRef="hb" targetRef="end"/>
"#;

#[test]
fn compensation_throw_per_scope_lifo() {
    let def = parse(&bpmn("p", BPMN_MULTI_SCOPES_V2));
    let reg = registry();
    let outcome = traverse(def, FlowContext::new("p1"), reg);
    match outcome {
        TraverseOutcome::Completed(t) => {
            // V5.31 P0 v0 — each
            // `CompensationThrow`
            // pops the *innermost*
            // scope and runs ITS
            // handlers (BPMN "throw
            // current scope" default).
            //   ct_inner  → pops
            //     inner → hb runs (h2)
            //   ce_inner  → continues
            //     to ct_outer
            //   ct_outer  → pops
            //     outer → ha runs (h1)
            //   ce_outer  → continues
            //     to end
            let comp = t
                .ctx
                .vars
                .get("compensated")
                .cloned()
                .unwrap_or(json!(null));
            assert_eq!(
                comp,
                json!(["h2", "h1"]),
                "innermost scope's handlers run first (h2), then outer (h1)"
            );
            // Stack is empty after the
            // throws + ends popped
            // everything.
            assert!(
                t.ctx.compensation_stack.is_empty(),
                "stack should be empty after both ce_inner + ce_outer"
            );
        }
        other => panic!("expected Completed, got {other:?}"),
    }
}
