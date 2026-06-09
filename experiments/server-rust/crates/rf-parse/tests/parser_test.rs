//! BPMN parser test suite — 20 cases covering happy path, gateway routing
//! edge cases, and every error path in `IrError`.

use rf_ir::ir_error::IrError;
use rf_ir::node_kind::{NodeKind, TaskType};
use rf_parse::bpmn_parser::BpmnXmlParser;

const NS_BPMN: &str = "http://www.omg.org/spec/BPMN/20100524/MODEL";
const NS_RULEFORGE: &str = "http://ruleforge.com/schema";
const NS_FLOWABLE: &str = "http://flowable.org/bpmn";

/// Wrap a `<process>` body in valid `<bpmn:definitions>` root with all three
/// namespaces declared, so the parser's namespace check passes and tests
/// can focus on the element under test.
fn bpmn(process_id: &str, body: &str) -> String {
    format!(
        r#"<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="{NS_BPMN}"
                  xmlns:ruleforge="{NS_RULEFORGE}"
                  xmlns:flowable="{NS_FLOWABLE}"
                  targetNamespace="{NS_RULEFORGE}">
  <bpmn:process id="{process_id}" name="{process_id}-name">
    {body}
  </bpmn:process>
</bpmn:definitions>"#
    )
}

// ─── happy path ────────────────────────────────────────────────────────────

#[test]
fn parses_start_and_end_only() {
    let xml = bpmn(
        "p1",
        r#"<bpmn:startEvent id="start"/>
           <bpmn:endEvent id="end"/>"#,
    );
    let def = BpmnXmlParser::parse(&xml).expect("parse ok");
    assert_eq!(def.process_id, "p1");
    assert_eq!(def.start, "start");
    assert_eq!(def.ends, vec!["end"]);
    assert_eq!(def.nodes.len(), 2);
    assert!(def.edges.is_empty());
    assert_eq!(def.source_xml_hash.len(), 64); // sha256 hex
}

#[test]
fn parses_service_task_rule() {
    let xml = bpmn(
        "p1",
        r#"<bpmn:startEvent id="s"/>
           <bpmn:serviceTask id="r1" name="审批规则"
                             ruleforge:taskType="rule"
                             ruleforge:file="loan.glx"
                             ruleforge:project="loan">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:serviceTask>
           <bpmn:sequenceFlow id="e1" sourceRef="r1" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    );
    let def = BpmnXmlParser::parse(&xml).unwrap();
    let r1 = &def.nodes["r1"];
    assert_eq!(
        r1.kind,
        NodeKind::ServiceTask {
            task_type: TaskType::Rule,
            attrs: rf_ir::attrs::Attrs(
                [
                    ("ruleforge:file".to_string(), "loan.glx".to_string()),
                    ("ruleforge:project".to_string(), "loan".to_string()),
                    ("ruleforge:taskType".to_string(), "rule".to_string()),
                ]
                .into_iter()
                .collect()
            )
        }
    );
    assert_eq!(r1.outgoing_ids, vec!["e1"]);
    assert_eq!(r1.name.as_deref(), Some("审批规则"));
}

#[test]
fn parses_service_task_action() {
    let xml = bpmn(
        "p1",
        r#"<bpmn:startEvent id="s"/>
           <bpmn:serviceTask id="a1"
                             ruleforge:taskType="action"
                             ruleforge:bean="loanService"
                             ruleforge:method="doApprove">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:serviceTask>
           <bpmn:sequenceFlow id="e1" sourceRef="a1" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    );
    let def = BpmnXmlParser::parse(&xml).unwrap();
    assert!(matches!(
        def.nodes["a1"].kind,
        NodeKind::ServiceTask {
            task_type: TaskType::Action,
            ..
        }
    ));
}

#[test]
fn parses_service_task_package_and_rules_package() {
    let xml = bpmn(
        "p1",
        r#"<bpmn:startEvent id="s"/>
           <bpmn:serviceTask id="p1n" ruleforge:taskType="package"
                             ruleforge:packageId="PKG-001">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:serviceTask>
           <bpmn:serviceTask id="rp1" ruleforge:taskType="rulesPackage"
                             ruleforge:rulesList="R1,R2">
             <bpmn:outgoing>e2</bpmn:outgoing>
           </bpmn:serviceTask>
           <bpmn:sequenceFlow id="e1" sourceRef="p1n" targetRef="rp1"/>
           <bpmn:sequenceFlow id="e2" sourceRef="rp1" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    );
    let def = BpmnXmlParser::parse(&xml).unwrap();
    assert!(matches!(
        def.nodes["p1n"].kind,
        NodeKind::ServiceTask {
            task_type: TaskType::Package,
            ..
        }
    ));
    assert!(matches!(
        def.nodes["rp1"].kind,
        NodeKind::ServiceTask {
            task_type: TaskType::RulesPackage,
            ..
        }
    ));
}

#[test]
fn parses_script_task_with_format_and_source() {
    let xml = bpmn(
        "p1",
        r#"<bpmn:startEvent id="s"/>
           <bpmn:scriptTask id="sc1" name="compute">
             <bpmn:outgoing>e1</bpmn:outgoing>
             <bpmn:script scriptFormat="rhai">vars.x = 1 + 2;</bpmn:script>
           </bpmn:scriptTask>
           <bpmn:sequenceFlow id="e1" sourceRef="sc1" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    );
    let def = BpmnXmlParser::parse(&xml).unwrap();
    match &def.nodes["sc1"].kind {
        NodeKind::ScriptTask { format, source, .. } => {
            assert_eq!(format, "rhai");
            assert_eq!(source, "vars.x = 1 + 2;");
        }
        other => panic!("expected ScriptTask, got {other:?}"),
    }
}

#[test]
fn script_task_defaults_format_to_groovy() {
    // Mirrors Java: if no scriptFormat attribute, default to "groovy".
    let xml = bpmn(
        "p1",
        r#"<bpmn:startEvent id="s"/>
           <bpmn:scriptTask id="sc1">
             <bpmn:outgoing>e1</bpmn:outgoing>
             <bpmn:script>x = 1</bpmn:script>
           </bpmn:scriptTask>
           <bpmn:sequenceFlow id="e1" sourceRef="sc1" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    );
    let def = BpmnXmlParser::parse(&xml).unwrap();
    match &def.nodes["sc1"].kind {
        NodeKind::ScriptTask { format, .. } => assert_eq!(format, "groovy"),
        other => panic!("expected ScriptTask, got {other:?}"),
    }
}

#[test]
fn parses_user_task_with_decision_field() {
    let xml = bpmn(
        "p1",
        r#"<bpmn:startEvent id="s"/>
           <bpmn:userTask id="u1" name="人工审批"
                          ruleforge:decisionType="binary"
                          ruleforge:decisionField="manualApprove">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:userTask>
           <bpmn:sequenceFlow id="e1" sourceRef="u1" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    );
    let def = BpmnXmlParser::parse(&xml).unwrap();
    match &def.nodes["u1"].kind {
        NodeKind::UserTask {
            decision_type,
            decision_field,
            ..
        } => {
            assert_eq!(decision_type, "binary");
            assert_eq!(decision_field, "manualApprove");
        }
        other => panic!("expected UserTask, got {other:?}"),
    }
}

#[test]
fn user_task_defaults_decision_type_to_binary() {
    let xml = bpmn(
        "p1",
        r#"<bpmn:startEvent id="s"/>
           <bpmn:userTask id="u1" ruleforge:decisionField="approve">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:userTask>
           <bpmn:sequenceFlow id="e1" sourceRef="u1" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    );
    let def = BpmnXmlParser::parse(&xml).unwrap();
    match &def.nodes["u1"].kind {
        NodeKind::UserTask {
            decision_type,
            decision_field,
            ..
        } => {
            assert_eq!(decision_type, "binary");
            assert_eq!(decision_field, "approve");
        }
        other => panic!("expected UserTask, got {other:?}"),
    }
}

#[test]
fn parses_exclusive_gateway_with_condition() {
    let xml = bpmn(
        "p1",
        r#"<bpmn:startEvent id="s"/>
           <bpmn:exclusiveGateway id="g1" name="age>=18?">
             <bpmn:outgoing>e_yes</bpmn:outgoing>
             <bpmn:outgoing>e_no</bpmn:outgoing>
           </bpmn:exclusiveGateway>
           <bpmn:sequenceFlow id="e_yes" sourceRef="g1" targetRef="end">
             <bpmn:conditionExpression>${vars.age &gt;= 18}</bpmn:conditionExpression>
           </bpmn:sequenceFlow>
           <bpmn:sequenceFlow id="e_no" sourceRef="g1" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    );
    let def = BpmnXmlParser::parse(&xml).unwrap();
    assert!(matches!(
        def.nodes["g1"].kind,
        NodeKind::ExclusiveGateway { .. }
    ));
    let yes = def.edges.iter().find(|e| e.id == "e_yes").unwrap();
    assert_eq!(yes.condition.as_deref(), Some("${vars.age >= 18}"));
    assert!(!yes.is_default);
    let no = def.edges.iter().find(|e| e.id == "e_no").unwrap();
    assert!(no.condition.is_none());
    assert!(no.is_default);
}

#[test]
fn parses_exclusive_gateway_with_percent() {
    let xml = bpmn(
        "p1",
        r#"<bpmn:startEvent id="s"/>
           <bpmn:exclusiveGateway id="g1">
             <bpmn:outgoing>e_a</bpmn:outgoing>
             <bpmn:outgoing>e_b</bpmn:outgoing>
           </bpmn:exclusiveGateway>
           <bpmn:sequenceFlow id="e_a" sourceRef="g1" targetRef="end"
                              ruleforge:percent="70"/>
           <bpmn:sequenceFlow id="e_b" sourceRef="g1" targetRef="end"
                              ruleforge:percent="30"/>
           <bpmn:endEvent id="end"/>"#,
    );
    let def = BpmnXmlParser::parse(&xml).unwrap();
    let a = def.edges.iter().find(|e| e.id == "e_a").unwrap();
    let b = def.edges.iter().find(|e| e.id == "e_b").unwrap();
    assert_eq!(a.percent, Some(70));
    assert_eq!(b.percent, Some(30));
    assert!(!a.is_default && !b.is_default); // percent → not default
}

#[test]
fn parses_parallel_gateway() {
    let xml = bpmn(
        "p1",
        r#"<bpmn:startEvent id="s"/>
           <bpmn:parallelGateway id="join"/>
           <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="join"/>
           <bpmn:sequenceFlow id="e2" sourceRef="join" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    );
    let def = BpmnXmlParser::parse(&xml).unwrap();
    assert!(matches!(
        def.nodes["join"].kind,
        NodeKind::ParallelGateway { .. }
    ));
}

#[test]
fn parses_intermediate_event() {
    let xml = bpmn(
        "p1",
        r#"<bpmn:startEvent id="s"/>
           <bpmn:intermediateCatchEvent id="ie">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:intermediateCatchEvent>
           <bpmn:sequenceFlow id="e1" sourceRef="ie" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    );
    let def = BpmnXmlParser::parse(&xml).unwrap();
    assert!(matches!(
        def.nodes["ie"].kind,
        NodeKind::IntermediateEvent { .. }
    ));
}

#[test]
fn extracts_ruleforge_and_flowable_attrs() {
    let xml = bpmn(
        "p1",
        r#"<bpmn:startEvent id="s"/>
           <bpmn:serviceTask id="r1"
                             ruleforge:taskType="rule"
                             ruleforge:file="x.glx"
                             ruleforge:async="true"
                             flowable:async="true"
                             flowable:exclusive="false">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:serviceTask>
           <bpmn:sequenceFlow id="e1" sourceRef="r1" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    );
    let def = BpmnXmlParser::parse(&xml).unwrap();
    let r1 = &def.nodes["r1"];
    if let NodeKind::ServiceTask { attrs, .. } = &r1.kind {
        assert_eq!(attrs.ruleforge("file"), Some("x.glx"));
        assert_eq!(attrs.flowable("async"), Some("true"));
        assert_eq!(attrs.flowable("exclusive"), Some("false"));
    } else {
        panic!("expected ServiceTask");
    }
    // async_flag only honours ruleforge:async — matches Java line 132.
    assert!(r1.async_flag);
}

#[test]
fn async_flag_is_optional_and_defaults_false() {
    let xml = bpmn(
        "p1",
        r#"<bpmn:startEvent id="s"/>
           <bpmn:serviceTask id="r1" ruleforge:taskType="rule">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:serviceTask>
           <bpmn:sequenceFlow id="e1" sourceRef="r1" targetRef="end"/>
           <bpmn:endEvent id="end"/>"#,
    );
    let def = BpmnXmlParser::parse(&xml).unwrap();
    assert!(!def.nodes["r1"].async_flag);
}

#[test]
fn full_5_node_fixture() {
    // Mirrors the Phase 3 integration fixture: START → rule → gateway → end1
    // (with the ruleforge:decisionValue path) / end2 (default).
    let xml = bpmn(
        "loan-decision",
        r#"<bpmn:startEvent id="start"/>
           <bpmn:serviceTask id="rule1" ruleforge:taskType="rule" ruleforge:file="loan.glx">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:serviceTask>
           <bpmn:exclusiveGateway id="gw">
             <bpmn:outgoing>e_yes</bpmn:outgoing>
             <bpmn:outgoing>e_no</bpmn:outgoing>
           </bpmn:exclusiveGateway>
           <bpmn:sequenceFlow id="e1" sourceRef="rule1" targetRef="gw"/>
           <bpmn:sequenceFlow id="e_yes" sourceRef="gw" targetRef="end_yes"
                              ruleforge:decisionValue="approved"/>
           <bpmn:sequenceFlow id="e_no" sourceRef="gw" targetRef="end_no"/>
           <bpmn:endEvent id="end_yes" name="approved"/>
           <bpmn:endEvent id="end_no" name="rejected"/>"#,
    );
    let def = BpmnXmlParser::parse(&xml).unwrap();
    assert_eq!(def.process_id, "loan-decision");
    assert_eq!(def.start, "start");
    // `ends` is collected from a BTreeMap iteration — sorted by node id.
    assert_eq!(def.ends, vec!["end_no", "end_yes"]);
    assert_eq!(def.nodes.len(), 5);
    assert_eq!(def.edges.len(), 3);
    let e_yes = def.edges.iter().find(|e| e.id == "e_yes").unwrap();
    assert_eq!(e_yes.attrs.ruleforge("decisionValue"), Some("approved"));
}

// ─── error paths ───────────────────────────────────────────────────────────

#[test]
fn rejects_empty_xml() {
    assert!(matches!(BpmnXmlParser::parse(""), Err(IrError::Empty)));
}

#[test]
fn rejects_invalid_xml() {
    let err = BpmnXmlParser::parse("not <valid> xml").unwrap_err();
    assert!(matches!(err, IrError::Invalid(_)));
}

#[test]
fn rejects_missing_root_namespace() {
    let xml = r#"<?xml version="1.0"?>
<definitions xmlns="http://example.com">
  <process id="p1"/>
</definitions>"#;
    let err = BpmnXmlParser::parse(xml).unwrap_err();
    assert!(matches!(err, IrError::MissingNamespace(_)));
}

#[test]
fn rejects_missing_process() {
    let xml = format!(
        r#"<?xml version="1.0"?>
<bpmn:definitions xmlns:bpmn="{NS_BPMN}">
  <bpmn:message id="m1"/>
</bpmn:definitions>"#
    );
    let err = BpmnXmlParser::parse(&xml).unwrap_err();
    assert!(matches!(err, IrError::NoProcess));
}

#[test]
fn rejects_missing_process_id() {
    let xml = format!(
        r#"<?xml version="1.0"?>
<bpmn:definitions xmlns:bpmn="{NS_BPMN}">
  <bpmn:process>
    <bpmn:startEvent id="s"/>
    <bpmn:endEvent id="e"/>
  </bpmn:process>
</bpmn:definitions>"#
    );
    let err = BpmnXmlParser::parse(&xml).unwrap_err();
    assert!(matches!(err, IrError::MissingProcessId));
}

#[test]
fn rejects_duplicate_node_id() {
    let xml = bpmn(
        "p1",
        r#"<bpmn:startEvent id="dup"/>
           <bpmn:startEvent id="dup"/>
           <bpmn:endEvent id="e"/>"#,
    );
    let err = BpmnXmlParser::parse(&xml).unwrap_err();
    assert!(matches!(err, IrError::DuplicateNodeId(_, _)));
}

#[test]
fn rejects_no_start_event() {
    let xml = bpmn("p1", r#"<bpmn:endEvent id="e"/>"#);
    let err = BpmnXmlParser::parse(&xml).unwrap_err();
    assert!(matches!(err, IrError::NoStartEvent(_)));
}

#[test]
fn rejects_no_end_event() {
    let xml = bpmn("p1", r#"<bpmn:startEvent id="s"/>"#);
    let err = BpmnXmlParser::parse(&xml).unwrap_err();
    assert!(matches!(err, IrError::NoEndEvent(_)));
}

#[test]
fn rejects_multiple_start_events() {
    let xml = bpmn(
        "p1",
        r#"<bpmn:startEvent id="s1"/>
           <bpmn:startEvent id="s2"/>
           <bpmn:endEvent id="e"/>"#,
    );
    let err = BpmnXmlParser::parse(&xml).unwrap_err();
    assert!(matches!(err, IrError::MultipleStartEvents(_)));
}

#[test]
fn rejects_unknown_task_type() {
    // serviceTask with ruleforge:taskType="mystery" should fail at parse time,
    // not silently route to NoOp like Java.
    let xml = bpmn(
        "p1",
        r#"<bpmn:startEvent id="s"/>
           <bpmn:serviceTask id="r1" ruleforge:taskType="mystery">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:serviceTask>
           <bpmn:sequenceFlow id="e1" sourceRef="r1" targetRef="e"/>
           <bpmn:endEvent id="e"/>"#,
    );
    let err = BpmnXmlParser::parse(&xml).unwrap_err();
    assert!(matches!(err, IrError::UnknownTaskType(_, _)));
}

#[test]
fn unknown_local_element_is_skipped_not_rejected() {
    // Mirrors Java behaviour: unknown element types are logged + skipped,
    // the rest of the flow still parses. Useful for forward-compat with new
    // BPMN elements the executor doesn't yet support.
    let xml = bpmn(
        "p1",
        r#"<bpmn:startEvent id="s"/>
           <bpmn:businessRuleTask id="brt" ruleforge:file="x"/>
           <bpmn:serviceTask id="r1" ruleforge:taskType="rule">
             <bpmn:outgoing>e1</bpmn:outgoing>
           </bpmn:serviceTask>
           <bpmn:sequenceFlow id="e1" sourceRef="r1" targetRef="e"/>
           <bpmn:endEvent id="e"/>"#,
    );
    let def = BpmnXmlParser::parse(&xml).unwrap();
    // brt skipped, but s/r1/e parsed fine
    assert!(!def.nodes.contains_key("brt"));
    assert!(def.nodes.contains_key("r1"));
}
