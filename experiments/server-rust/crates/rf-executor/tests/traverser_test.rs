//! 5-node fixture tests — exercises the 4-segment routing via traverse().
//!
//! `serviceTask ruleforge:taskType="rule"` nodes are wired to
//! `MockRuleEngine` (handles missing applicant gracefully — sets
//! `approved=false, creditLimit=0` and returns Ok). The
//! `userTask` suspension path is covered separately in
//! `user_task_suspend_test.rs`.
//!
//! `useTask` routing-on-resume was tested here in Phase 3 (when dispatch
//! was a Continue stub); the real Suspend path is in
//! `user_task_suspend_test.rs`.

use std::sync::Arc;

use rf_executor::dispatch::ExecutorRegistry;
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

/// Registry that wires `MockRuleEngine` into the rule slot. The other
/// four executors use their defaults (Action/Action: empty registry,
/// Script: rhai no-op, Gateway: Continue, UserTask: real Suspend).
fn test_registry() -> Arc<ExecutorRegistry> {
    Arc::new(ExecutorRegistry::with_rule_engine(Arc::new(
        rf_rule::mock::MockRuleEngine,
    )))
}

#[test]
fn trivial_start_end_completes() {
    let def = parse(&bpmn(
        "p",
        r#"<bpmn:startEvent id="s"/>
           <bpmn:sequenceFlow id="e" sourceRef="s" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    ));
    let outcome = traverse(def, FlowContext::new("r1"), test_registry());
    assert!(matches!(outcome, TraverseOutcome::Completed(_)));
}

#[test]
fn single_outgoing_skips_routing() {
    // gateway with only one outgoing — should walk straight through
    let def = parse(&bpmn(
        "p",
        r#"<bpmn:startEvent id="s"/>
           <bpmn:exclusiveGateway id="g"/>
           <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="g"/>
           <bpmn:sequenceFlow id="e2" sourceRef="g" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    ));
    let outcome = traverse(def, FlowContext::new("r1"), test_registry());
    assert!(matches!(outcome, TraverseOutcome::Completed(_)));
}

#[test]
fn condition_true_routes_to_yes() {
    // start → gateway → e_yes (age>=18) → end_yes / e_no (default) → end_no
    let def = parse(&bpmn(
        "p",
        r#"<bpmn:startEvent id="s">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:startEvent>
           <bpmn:exclusiveGateway id="g">
             <bpmn:outgoing>e_yes</bpmn:outgoing>
             <bpmn:outgoing>e_no</bpmn:outgoing>
           </bpmn:exclusiveGateway>
           <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="g"/>
           <bpmn:sequenceFlow id="e_yes" sourceRef="g" targetRef="end_yes">
             <bpmn:conditionExpression>${age &gt;= 18}</bpmn:conditionExpression>
           </bpmn:sequenceFlow>
           <bpmn:sequenceFlow id="e_no" sourceRef="g" targetRef="end_no"/>
           <bpmn:endEvent id="end_yes"/>
           <bpmn:endEvent id="end_no"/>"#,
    ));
    let mut ctx = FlowContext::new("r1");
    ctx.vars.insert("age", json!(20));
    let outcome = traverse(def, ctx, test_registry());
    let ctx = outcome.into_context();
    assert_eq!(ctx.current_node_id.as_deref(), Some("end_yes"));
}

#[test]
fn condition_false_falls_through_to_default() {
    let def = parse(&bpmn(
        "p",
        r#"<bpmn:startEvent id="s">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:startEvent>
           <bpmn:exclusiveGateway id="g">
             <bpmn:outgoing>e_yes</bpmn:outgoing>
             <bpmn:outgoing>e_no</bpmn:outgoing>
           </bpmn:exclusiveGateway>
           <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="g"/>
           <bpmn:sequenceFlow id="e_yes" sourceRef="g" targetRef="end_yes">
             <bpmn:conditionExpression>${age &gt;= 18}</bpmn:conditionExpression>
           </bpmn:sequenceFlow>
           <bpmn:sequenceFlow id="e_no" sourceRef="g" targetRef="end_no"/>
           <bpmn:endEvent id="end_yes"/>
           <bpmn:endEvent id="end_no"/>"#,
    ));
    let mut ctx = FlowContext::new("r1");
    ctx.vars.insert("age", json!(15));
    let outcome = traverse(def, ctx, test_registry());
    let ctx = outcome.into_context();
    assert_eq!(ctx.current_node_id.as_deref(), Some("end_no"));
}

#[test]
fn percent_routing_distributes_correctly() {
    // 70/30 percent split. Run 1000 times and check distribution.
    let def = parse(&bpmn(
        "p",
        r#"<bpmn:startEvent id="s">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:startEvent>
           <bpmn:exclusiveGateway id="g">
             <bpmn:outgoing>e_a</bpmn:outgoing>
             <bpmn:outgoing>e_b</bpmn:outgoing>
           </bpmn:exclusiveGateway>
           <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="g"/>
           <bpmn:sequenceFlow id="e_a" sourceRef="g" targetRef="end_a"
                              ruleforge:percent="70"/>
           <bpmn:sequenceFlow id="e_b" sourceRef="g" targetRef="end_b"
                              ruleforge:percent="30"/>
           <bpmn:endEvent id="end_a"/>
           <bpmn:endEvent id="end_b"/>"#,
    ));
    let reg = test_registry();
    let mut a_count = 0;
    let mut b_count = 0;
    for _ in 0..1000 {
        let outcome = traverse(def.clone(), FlowContext::new("r"), reg.clone());
        let node = outcome.into_context().current_node_id.unwrap();
        if node == "end_a" {
            a_count += 1;
        } else {
            b_count += 1;
        }
    }
    // 1000 samples, expected 700/300, std dev ~14. Allow ±10% wiggle.
    assert!(
        (650..=750).contains(&a_count),
        "a_count={a_count}, b_count={b_count}"
    );
    assert!(
        (250..=350).contains(&b_count),
        "a_count={a_count}, b_count={b_count}"
    );
}

#[test]
fn loop_is_detected() {
    // s → a → a (self-loop) → eventually trips the visited check
    let def = parse(&bpmn(
        "p",
        r#"<bpmn:startEvent id="s">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:startEvent>
           <bpmn:exclusiveGateway id="a">
             <bpmn:outgoing>e_back</bpmn:outgoing>
             <bpmn:outgoing>e_end</bpmn:outgoing>
           </bpmn:exclusiveGateway>
           <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="a"/>
           <bpmn:sequenceFlow id="e_back" sourceRef="a" targetRef="a"/>
           <bpmn:sequenceFlow id="e_end" sourceRef="a" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    ));
    let outcome = traverse(def, FlowContext::new("r"), test_registry());
    // e_back always picks itself (no condition, no percent), so we re-visit
    // "a" → LoopDetected → Failed outcome.
    assert!(matches!(outcome, TraverseOutcome::Failed(_, _)));
}

#[test]
fn max_steps_triggers_on_pure_linear_run() {
    // 1500 nodes in a chain, all linear — should hit MAX_STEPS=1000
    // before reaching the end. Build the XML programmatically.
    let mut body = String::from(
        r#"<bpmn:startEvent id="s">
             <bpmn:outgoing>e0</bpmn:outgoing>
           </bpmn:startEvent>"#,
    );
    let n = 1500;
    for i in 0..n - 2 {
        body.push_str(&format!(
            r#"<bpmn:serviceTask id="n{i}" ruleforge:taskType="rule">
                 <bpmn:outgoing>e{}</bpmn:outgoing>
               </bpmn:serviceTask>"#,
            i + 1
        ));
    }
    body.push_str(r#"<bpmn:endEvent id="end"/>"#);
    for i in 0..n - 1 {
        let src = if i == 0 {
            "s".to_string()
        } else {
            format!("n{}", i - 1)
        };
        let tgt = if i == n - 1 {
            "end".to_string()
        } else {
            format!("n{i}")
        };
        body.push_str(&format!(
            r#"<bpmn:sequenceFlow id="e{i}" sourceRef="{src}" targetRef="{tgt}"/>"#
        ));
    }
    let def = parse(&bpmn("p", &body));
    let outcome = traverse(def, FlowContext::new("r"), test_registry());
    assert!(matches!(outcome, TraverseOutcome::Failed(_, _)));
}
