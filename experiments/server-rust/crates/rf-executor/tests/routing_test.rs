//! Phase 4 routing tests — verify the 5 NodeExecutors do what they claim.
//!
//! Builds end-to-end fixtures with real BPMN → real parser → real
//! `ExecutorRegistry::with_rule_engine(MockRuleEngine)` → real
//! `traverse()`. The 4-segment routing math was tested with stubs in
//! `traverser_test.rs`; this file tests the executors themselves.

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

/// Default `ExecutorRegistry` plus `MockRuleEngine`.
fn rule_registry() -> Arc<ExecutorRegistry> {
    Arc::new(ExecutorRegistry::with_rule_engine(Arc::new(
        rf_rule::mock::MockRuleEngine,
    )))
}

// ── RuleExecutor ──────────────────────────────────────────────────────────

#[test]
fn rule_executor_writes_approved_and_credit_limit_for_qualified_applicant() {
    // start → rule serviceTask → end
    let def = parse(&bpmn(
        "p",
        r#"<bpmn:startEvent id="s">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:startEvent>
           <bpmn:serviceTask id="rule" ruleforge:taskType="rule">
             <bpmn:outgoing>e2</bpmn:outgoing>
           </bpmn:serviceTask>
           <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="rule"/>
           <bpmn:sequenceFlow id="e2" sourceRef="rule" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    ));
    let mut ctx = FlowContext::new("r");
    ctx.vars
        .insert("applicant", json!({"age": 25, "income": 12_000}));
    let outcome = traverse(def, ctx, rule_registry());
    let ctx = outcome.into_context();
    assert!(ctx.vars.get("approved") == Some(&json!(true)));
    let limit = ctx
        .vars
        .get("creditLimit")
        .and_then(|v| v.as_i64())
        .expect("creditLimit set");
    // age 25 → (25-18)*100 = 700 + 5000 = 5700
    assert_eq!(limit, 5_700);
}

#[test]
fn rule_executor_rejects_underage_applicant() {
    let def = parse(&bpmn(
        "p",
        r#"<bpmn:startEvent id="s">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:startEvent>
           <bpmn:serviceTask id="rule" ruleforge:taskType="rule">
             <bpmn:outgoing>e2</bpmn:outgoing>
           </bpmn:serviceTask>
           <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="rule"/>
           <bpmn:sequenceFlow id="e2" sourceRef="rule" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    ));
    let mut ctx = FlowContext::new("r");
    ctx.vars
        .insert("applicant", json!({"age": 16, "income": 12_000}));
    let outcome = traverse(def, ctx, rule_registry());
    let ctx = outcome.into_context();
    assert_eq!(ctx.vars.get("approved"), Some(&json!(false)));
    assert_eq!(ctx.vars.get("creditLimit"), Some(&json!(0)));
}

#[test]
fn rule_executor_records_fireable_and_matched_rules() {
    // After the rule runs, vars should contain _fireable_rules +
    // _matched_rules so the state row's audit columns can persist them.
    let def = parse(&bpmn(
        "p",
        r#"<bpmn:startEvent id="s">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:startEvent>
           <bpmn:serviceTask id="rule" ruleforge:taskType="rule">
             <bpmn:outgoing>e2</bpmn:outgoing>
           </bpmn:serviceTask>
           <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="rule"/>
           <bpmn:sequenceFlow id="e2" sourceRef="rule" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    ));
    let outcome = traverse(def, FlowContext::new("r"), rule_registry());
    let ctx = outcome.into_context();
    let fired = ctx
        .vars
        .get("_fireable_rules")
        .expect("_fireable_rules set");
    assert!(fired.is_array());
    assert!(!fired.as_array().unwrap().is_empty());
}

// ── ActionExecutor ─────────────────────────────────────────────────────────

#[test]
fn action_executor_runs_registered_action() {
    // Build a registry with a single "stamp" action that writes a var.
    let reg = Arc::new(MockActionRegistry::new().register("stamp", |vars| {
        vars.insert("stamped", json!(true));
        Ok(())
    }));
    let mut er = (*rule_registry()).clone();
    er.action = Arc::new(ActionExecutor::new(reg));
    let er = Arc::new(er);

    let def = parse(&bpmn(
        "p",
        r#"<bpmn:startEvent id="s">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:startEvent>
           <bpmn:serviceTask id="a" ruleforge:taskType="action" ruleforge:method="stamp">
             <bpmn:outgoing>e2</bpmn:outgoing>
           </bpmn:serviceTask>
           <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="a"/>
           <bpmn:sequenceFlow id="e2" sourceRef="a" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    ));
    let outcome = traverse(def, FlowContext::new("r"), er);
    let ctx = outcome.into_context();
    assert_eq!(ctx.vars.get("stamped"), Some(&json!(true)));
}

#[test]
fn action_executor_errors_when_method_attr_missing() {
    // No `ruleforge:method` attr → FlowError::Action on the first step.
    let def = parse(&bpmn(
        "p",
        r#"<bpmn:startEvent id="s">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:startEvent>
           <bpmn:serviceTask id="a" ruleforge:taskType="action">
             <bpmn:outgoing>e2</bpmn:outgoing>
           </bpmn:serviceTask>
           <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="a"/>
           <bpmn:sequenceFlow id="e2" sourceRef="a" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    ));
    let outcome = traverse(def, FlowContext::new("r"), rule_registry());
    assert!(matches!(outcome, TraverseOutcome::Failed(_, _)));
}

#[test]
fn action_executor_errors_when_method_not_registered() {
    let def = parse(&bpmn(
        "p",
        r#"<bpmn:startEvent id="s">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:startEvent>
           <bpmn:serviceTask id="a" ruleforge:taskType="action" ruleforge:method="ghost">
             <bpmn:outgoing>e2</bpmn:outgoing>
           </bpmn:serviceTask>
           <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="a"/>
           <bpmn:sequenceFlow id="e2" sourceRef="a" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    ));
    let outcome = traverse(def, FlowContext::new("r"), rule_registry());
    assert!(matches!(outcome, TraverseOutcome::Failed(_, _)));
}

// ── ScriptExecutor ────────────────────────────────────────────────────────

#[test]
fn script_executor_rhai_is_no_op_continue() {
    // Phase 4: rhai is accepted but not executed — proves the routing
    // shape is right and the executor returns Continue (so the flow
    // reaches end). Note: BPMN puts `scriptFormat` on the child
    // `<script>` element, not on `<scriptTask>` itself.
    let def = parse(&bpmn(
        "p",
        r#"<bpmn:startEvent id="s">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:startEvent>
           <bpmn:scriptTask id="scr">
             <bpmn:script scriptFormat="rhai">// do something</bpmn:script>
             <bpmn:outgoing>e2</bpmn:outgoing>
           </bpmn:scriptTask>
           <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="scr"/>
           <bpmn:sequenceFlow id="e2" sourceRef="scr" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    ));
    let outcome = traverse(def, FlowContext::new("r"), rule_registry());
    assert!(matches!(outcome, TraverseOutcome::Completed(_)));
}

#[test]
fn script_executor_non_rhai_format_errors() {
    // groovy/javascript etc. → FlowError::Unsupported in Phase 4
    // (Phase 8 documents the real V5.x uses groovy but the port
    // can't drag groovy in).
    let def = parse(&bpmn(
        "p",
        r#"<bpmn:startEvent id="s">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:startEvent>
           <bpmn:scriptTask id="scr">
             <bpmn:script scriptFormat="groovy">println 'hi'</bpmn:script>
             <bpmn:outgoing>e2</bpmn:outgoing>
           </bpmn:scriptTask>
           <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="scr"/>
           <bpmn:sequenceFlow id="e2" sourceRef="scr" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    ));
    let outcome = traverse(def, FlowContext::new("r"), rule_registry());
    assert!(matches!(outcome, TraverseOutcome::Failed(_, _)));
}

// ── 4-segment routing with concrete executors (end-to-end) ───────────────

#[test]
fn end_to_end_rule_then_condition_routes_to_yes_branch() {
    // rule → gateway → e_yes (approved==true) → end_yes / e_no → end_no
    let def = parse(&bpmn(
        "p",
        r#"<bpmn:startEvent id="s">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:startEvent>
           <bpmn:serviceTask id="rule" ruleforge:taskType="rule">
             <bpmn:outgoing>e2</bpmn:outgoing>
           </bpmn:serviceTask>
           <bpmn:exclusiveGateway id="g">
             <bpmn:outgoing>e_yes</bpmn:outgoing>
             <bpmn:outgoing>e_no</bpmn:outgoing>
           </bpmn:exclusiveGateway>
           <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="rule"/>
           <bpmn:sequenceFlow id="e2" sourceRef="rule" targetRef="g"/>
           <bpmn:sequenceFlow id="e_yes" sourceRef="g" targetRef="end_yes">
             <bpmn:conditionExpression>${approved == true}</bpmn:conditionExpression>
           </bpmn:sequenceFlow>
           <bpmn:sequenceFlow id="e_no" sourceRef="g" targetRef="end_no"/>
           <bpmn:endEvent id="end_yes"/>
           <bpmn:endEvent id="end_no"/>"#,
    ));
    let mut ctx = FlowContext::new("r");
    ctx.vars
        .insert("applicant", json!({"age": 25, "income": 12_000}));
    let outcome = traverse(def, ctx, rule_registry());
    let ctx = outcome.into_context();
    assert_eq!(ctx.current_node_id.as_deref(), Some("end_yes"));
}

#[test]
fn end_to_end_rule_then_condition_routes_to_no_branch_for_underage() {
    let def = parse(&bpmn(
        "p",
        r#"<bpmn:startEvent id="s">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:startEvent>
           <bpmn:serviceTask id="rule" ruleforge:taskType="rule">
             <bpmn:outgoing>e2</bpmn:outgoing>
           </bpmn:serviceTask>
           <bpmn:exclusiveGateway id="g">
             <bpmn:outgoing>e_yes</bpmn:outgoing>
             <bpmn:outgoing>e_no</bpmn:outgoing>
           </bpmn:exclusiveGateway>
           <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="rule"/>
           <bpmn:sequenceFlow id="e2" sourceRef="rule" targetRef="g"/>
           <bpmn:sequenceFlow id="e_yes" sourceRef="g" targetRef="end_yes">
             <bpmn:conditionExpression>${approved == true}</bpmn:conditionExpression>
           </bpmn:sequenceFlow>
           <bpmn:sequenceFlow id="e_no" sourceRef="g" targetRef="end_no"/>
           <bpmn:endEvent id="end_yes"/>
           <bpmn:endEvent id="end_no"/>"#,
    ));
    let mut ctx = FlowContext::new("r");
    ctx.vars
        .insert("applicant", json!({"age": 16, "income": 12_000}));
    let outcome = traverse(def, ctx, rule_registry());
    let ctx = outcome.into_context();
    assert_eq!(ctx.current_node_id.as_deref(), Some("end_no"));
}

// (ExecutorRegistry derives Clone in dispatch.rs — needed for tests
// that override one field while keeping the others.)
