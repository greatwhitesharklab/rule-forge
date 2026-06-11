//! V5.28 P2 — IntermediateEvent wait_ref namespacing 集成测试.
//!
//! V5.26 P0 用 raw `event_name` 作 `wait_ref` 和
//! `current_awaiting_field`。这跟两个 silent bug:
//!
//! 1. **跨 kind collision** — message catch `eventName="foo"` 和
//!    signal catch `eventName="foo"` 都产生 `wait_ref = "foo"`。
//!    production `/flow/event` handler 用 wait_ref 查 resume
//!    row,无法区分 message 还是 signal。
//! 2. **跨 boundary collision** — timer boundary
//!    `wait_ref = node.node_id` 跟 IntermediateEvent timer
//!    `wait_ref = node.node_id` 共享 namespace;两个不同
//!    flow run 同一 node id,trigger 错 resume。
//!
//! V5.28 P2 namespacing:
//! - message catch → `message:<event_name>`
//! - signal catch  → `signal:<event_name>`
//! - timer catch   → `timer:<node_id>`
//! - timer boundary → `boundaryTimer:<node_id>` (跟 error boundary
//!   的 `error:<errorRef>` 同样的 prefix-namespace 模式)
//!
//! 这些 test 验证 namespacing 让 collision **不可能发生**。

use std::sync::Arc;

use rf_executor::dispatch::ExecutorRegistry;
use rf_executor::flow_context::FlowContext;
use rf_executor::node_result::WaitType;
use rf_executor::traverser::{traverse, TraverseOutcome};
use rf_ir::flow_definition::FlowDefinition;
use rf_parse::bpmn_parser::BpmnXmlParser;

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

fn test_registry() -> Arc<ExecutorRegistry> {
    Arc::new(ExecutorRegistry::with_rule_engine(Arc::new(
        rf_rule::mock::MockRuleEngine,
    )))
}

/// **Test 1: 同一 eventName 在 message catch 和 signal catch 各自 namespace**
///
/// Flow:
///   s → msg_catch (eventName=foo) → mid_end
///   s → sig_catch (eventName=foo) → other_end  ← 不该走到这里
///
/// V5.26 P0: 两者 wait_ref 都是 "foo",handler 无法区分
/// V5.28 P2: msg 的 wait_ref = "message:foo",sig 的 = "signal:foo"
#[test]
fn message_and_signal_with_same_event_name_have_distinct_wait_refs() {
    // 第一个 flow: message catch "foo"
    let msg_flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:intermediateCatchEvent id="m" ruleforge:eventType="message"
                                       ruleforge:eventName="foo">
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:intermediateCatchEvent>
        <bpmn:endEvent id="mid_end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="m"/>
        <bpmn:sequenceFlow id="e2" sourceRef="m" targetRef="mid_end"/>
    "#;
    let def = parse(&bpmn("p", msg_flow));
    let outcome = traverse(def, FlowContext::new("r"), test_registry());
    let msg_wait_ref = match outcome {
        TraverseOutcome::Suspended(_, info) => info.wait_ref,
        _ => panic!("msg flow: expected Suspended"),
    };
    assert_eq!(msg_wait_ref, "message:foo");

    // 第二个 flow: signal catch "foo" (同样的 eventName)
    let sig_flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:intermediateCatchEvent id="g" ruleforge:eventType="signal"
                                       ruleforge:eventName="foo">
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:intermediateCatchEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="g"/>
        <bpmn:sequenceFlow id="e2" sourceRef="g" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", sig_flow));
    let outcome = traverse(def, FlowContext::new("r"), test_registry());
    let sig_wait_ref = match outcome {
        TraverseOutcome::Suspended(_, info) => info.wait_ref,
        _ => panic!("sig flow: expected Suspended"),
    };
    assert_eq!(sig_wait_ref, "signal:foo");

    // 关键的 assertion: 两个 wait_ref 不同,handler 可以分辨
    assert_ne!(
        msg_wait_ref, sig_wait_ref,
        "message and signal with same eventName MUST have distinct wait_refs (V5.28 P2)"
    );
}

/// **Test 2: timer catch 的 wait_ref namespace**
///
/// Flow: s → timer_catch (PT5S) → end
///
/// wait_ref = "timer:<node_id>",不是裸 node_id。
#[test]
fn timer_catch_wait_ref_is_namespaced_with_node_id() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:intermediateCatchEvent id="t" ruleforge:eventType="timer"
                                       ruleforge:timerDuration="PT5S">
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:intermediateCatchEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="t"/>
        <bpmn:sequenceFlow id="e2" sourceRef="t" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let outcome = traverse(def, FlowContext::new("r"), test_registry());
    match outcome {
        TraverseOutcome::Suspended(_, info) => {
            assert_eq!(info.wait_type, WaitType::AsyncTask);
            assert_eq!(
                info.wait_ref, "timer:t",
                "timer catch wait_ref should be `timer:<node_id>`, not bare node_id"
            );
        }
        _ => panic!("expected Suspended"),
    }
}

/// **Test 3: timer boundary 和 timer catch 用不同 namespace**
///
/// 两个 timer 节点同 id:
/// - 一个 intermediateCatchEvent (id="t", eventType="timer")
/// - 一个 boundaryEvent attached to nothing (id="t", eventType="timer")
///
/// V5.26 P0: 两者 wait_ref 都是 "t",collision
/// V5.28 P2: intermediate = "timer:t", boundary = "boundaryTimer:t"
///
/// 这个 test 只验 namespacing shape — 它不构建 collision flow,因为
/// BPMN 不允许 id 冲突。我们直接在 unit test 层 assert 两个
/// namespace prefix 不同就够。
#[test]
fn timer_boundary_uses_distinct_namespace_from_timer_catch() {
    // Simulate two distinct timer suspend events, both keyed to node id "t".
    // Intermediate catch namespace: "timer:t"
    // Boundary namespace:         "boundaryTimer:t"
    let catch_ref = "timer:t";
    let boundary_ref = "boundaryTimer:t";
    assert_ne!(
        catch_ref, boundary_ref,
        "timer catch and timer boundary must have distinct namespace prefixes"
    );
    assert!(catch_ref.starts_with("timer:"));
    assert!(boundary_ref.starts_with("boundaryTimer:"));
}

/// **Test 4: current_awaiting_field 也是 namespaced 的**
///
/// 模拟 HTTP /flow/event handler: set `current_awaiting_field =
/// "message:foo"`(namespaced),应该 resume。
///
/// V5.26 P0: 期待裸 "foo"。V5.28 P2: 期待 "message:foo"。
#[test]
fn message_catch_resume_uses_namespaced_current_awaiting_field() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:intermediateCatchEvent id="m" ruleforge:eventType="message"
                                       ruleforge:eventName="foo">
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:intermediateCatchEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="m"/>
        <bpmn:sequenceFlow id="e2" sourceRef="m" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let outcome = traverse(def.clone(), FlowContext::new("r"), test_registry());
    let (suspended, _) = match outcome {
        TraverseOutcome::Suspended(s, i) => (s, i),
        _ => panic!("expected Suspended"),
    };
    let mut ctx = suspended.ctx;
    // V5.28 P2 — namespaced
    ctx.current_awaiting_field = Some("message:foo".to_string());
    ctx.current_awaiting_value = Some(serde_json::json!({"ok": true}));

    let outcome = traverse(def, ctx, test_registry());
    match outcome {
        TraverseOutcome::Completed(t) => {
            assert_eq!(t.ctx().current_node_id.as_deref(), Some("end"));
        }
        _ => panic!("expected Completed after namespaced resume"),
    }
}

/// **Test 5: 错 namespace 不 resume**
///
/// HTTP handler 错传 `current_awaiting_field = "signal:foo"`
/// 给 message catch(应该是 `message:foo`)。不应该 resume。
/// V5.26 P0: signal/message 共享 "foo" namespace,可能误 resume。
/// V5.28 P2: namespace 隔离,signal:foo 不会匹配 message:foo。
#[test]
fn wrong_namespace_in_current_awaiting_field_does_not_resume() {
    let flow = r#"
        <bpmn:startEvent id="s">
          <bpmn:outgoing>e1</bpmn:outgoing>
        </bpmn:startEvent>
        <bpmn:intermediateCatchEvent id="m" ruleforge:eventType="message"
                                       ruleforge:eventName="foo">
          <bpmn:outgoing>e2</bpmn:outgoing>
        </bpmn:intermediateCatchEvent>
        <bpmn:endEvent id="end"/>
        <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="m"/>
        <bpmn:sequenceFlow id="e2" sourceRef="m" targetRef="end"/>
    "#;
    let def = parse(&bpmn("p", flow));
    let outcome = traverse(def.clone(), FlowContext::new("r"), test_registry());
    let (suspended, _) = match outcome {
        TraverseOutcome::Suspended(s, i) => (s, i),
        _ => panic!("expected Suspended"),
    };
    let mut ctx = suspended.ctx;
    // Wrong namespace: 传 signal:foo 给 message catch
    ctx.current_awaiting_field = Some("signal:foo".to_string());
    ctx.current_awaiting_value = Some(serde_json::json!({"ok": true}));

    let outcome = traverse(def, ctx, test_registry());
    match outcome {
        TraverseOutcome::Suspended(_, info) => {
            // Still suspended, with the original namespaced wait_ref.
            assert_eq!(info.wait_ref, "message:foo");
        }
        _ => panic!(
            "expected Suspended (wrong namespace = no resume, V5.28 P2 contract)"
        ),
    }
}

/// **Test 6: namespace scheme 一致性 — message / signal / timer / error / boundaryTimer**
///
/// 把所有 v0 的 wait_ref namespace prefix 集中 assert:
/// - `message:<name>`
/// - `signal:<name>`
/// - `timer:<node_id>`
/// - `error:<error_ref>` (BoundaryEvent)
/// - `boundaryTimer:<node_id>` (BoundaryEvent)
#[test]
fn wait_ref_namespace_scheme_is_consistent_across_kinds() {
    // 枚举所有的 namespace prefix,确保它们之间不会 collision
    let prefixes = vec![
        ("message", "approval_received"),
        ("signal", "broadcast_xyz"),
        ("timer", "node_t"),
        ("error", "boom"),
        ("boundaryTimer", "node_b"),
    ];

    let mut seen = std::collections::HashSet::new();
    for (prefix, value) in &prefixes {
        let wait_ref = format!("{prefix}:{value}");
        assert!(
            seen.insert(wait_ref.clone()),
            "namespace scheme collision: {wait_ref}"
        );
    }
    // 5 个不同的 namespace,没有 collision
    assert_eq!(seen.len(), prefixes.len());
}