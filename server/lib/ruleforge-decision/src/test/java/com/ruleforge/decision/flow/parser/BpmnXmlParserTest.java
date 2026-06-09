package com.ruleforge.decision.flow.parser;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.ir.NodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BpmnXmlParser 行为规范:
 * Given 一段 BPMN 2.0 XML
 * When   调 parse(xml)
 * Then   返回不可变 FlowDefinition,startNode / endNodes / nodes / edges 都对得上
 */
@DisplayName("BpmnXmlParser 行为")
class BpmnXmlParserTest {

    private final BpmnXmlParser parser = new BpmnXmlParser();

    @Nested
    @DisplayName("基础解析")
    class Basic {

        @Test
        @DisplayName("Given 简单 START→SERVICE_TASK→END,When 解析,Then 三节点一连线一源xmlHash")
        void simpleFlow() {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema"
                                  id="def1">
                  <bpmn:process id="loan" name="贷款决策流">
                    <bpmn:startEvent id="start" name="开始"/>
                    <bpmn:serviceTask id="rule1" name="跑规则"
                        ruleforge:taskType="rule" ruleforge:file="score.drl"/>
                    <bpmn:endEvent id="end" name="结束"/>
                    <bpmn:sequenceFlow id="f1" sourceRef="start" targetRef="rule1"/>
                    <bpmn:sequenceFlow id="f2" sourceRef="rule1" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parse(xml);

            assertEquals("loan", def.getProcessId());
            assertEquals("贷款决策流", def.getName());
            assertEquals(3, def.getNodes().size());
            assertEquals("start", def.getStartNodeId());
            assertTrue(def.getEndNodeIds().contains("end"));
            assertEquals(2, def.getEdges().size());
            assertNotNull(def.getSourceXmlHash());
            assertEquals(64, def.getSourceXmlHash().length(), "SHA-256 应当 64 hex 字符");
        }

        @Test
        @DisplayName("Given 完整 userTask binary 决策,When 解析,Then extensionAttrs.ruleforge:decisionType 正确")
        void userTaskBinary() {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema">
                  <bpmn:process id="f1">
                    <bpmn:startEvent id="start"/>
                    <bpmn:userTask id="review" ruleforge:decisionType="binary"
                                   ruleforge:decisionField="approved"/>
                    <bpmn:endEvent id="ok"/>
                    <bpmn:endEvent id="ng"/>
                    <bpmn:sequenceFlow id="s1" sourceRef="start" targetRef="review"/>
                    <bpmn:sequenceFlow id="r1" sourceRef="review" targetRef="ok"
                        ruleforge:decisionValue="1"/>
                    <bpmn:sequenceFlow id="r2" sourceRef="review" targetRef="ng"
                        ruleforge:decisionValue="0"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parse(xml);
            assertEquals(NodeType.USER_TASK, def.getNode("review").getType());
            assertEquals("binary", def.getNode("review").attr("ruleforge", "decisionType"));
            assertEquals("approved", def.getNode("review").attr("ruleforge", "decisionField"));
            assertEquals("1", def.getEdge("r1").attr("ruleforge:decisionValue"));
        }

        @Test
        @DisplayName("Given sequenceFlow 带 percent,When 解析,Then percent 字段正确填")
        void percentEdge() {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema">
                  <bpmn:process id="f1">
                    <bpmn:startEvent id="start"/>
                    <bpmn:exclusiveGateway id="g1"/>
                    <bpmn:endEvent id="a"/>
                    <bpmn:endEvent id="b"/>
                    <bpmn:sequenceFlow id="s1" sourceRef="start" targetRef="g1"/>
                    <bpmn:sequenceFlow id="e1" sourceRef="g1" targetRef="a" ruleforge:percent="70"/>
                    <bpmn:sequenceFlow id="e2" sourceRef="g1" targetRef="b" ruleforge:percent="30"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parse(xml);
            assertEquals(Integer.valueOf(70), def.getEdge("e1").getPercent());
            assertEquals(Integer.valueOf(30), def.getEdge("e2").getPercent());
        }
    }

    @Nested
    @DisplayName("错误处理")
    class Errors {

        @Test
        @DisplayName("Given 空字符串,When 解析,Then 抛 FlowExecutionException")
        void empty() {
            assertThrows(FlowExecutionException.class, () -> parser.parse(""));
        }

        @Test
        @DisplayName("Given 没有 <process>,When 解析,Then 抛 FlowExecutionException")
        void noProcess() {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"/>
                """;
            assertThrows(FlowExecutionException.class, () -> parser.parse(xml));
        }

        @Test
        @DisplayName("Given 没有 startEvent,When 解析,Then 抛 FlowExecutionException")
        void noStart() {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="f1">
                    <bpmn:endEvent id="e1"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            assertThrows(FlowExecutionException.class, () -> parser.parse(xml));
        }

        @Test
        @DisplayName("Given 节点 id 重复,When 解析,Then 抛 FlowExecutionException")
        void duplicateId() {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="f1">
                    <bpmn:startEvent id="start"/>
                    <bpmn:endEvent id="dup"/>
                    <bpmn:endEvent id="dup"/>
                    <bpmn:sequenceFlow id="s1" sourceRef="start" targetRef="dup"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            assertThrows(FlowExecutionException.class, () -> parser.parse(xml));
        }
    }

    @Nested
    @DisplayName("辅助方法")
    class Helpers {

        @Test
        @DisplayName("Given 已知节点,When 查 node(id),Then 返回;查不存在的 id 返回 null")
        void getNode() {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="f1">
                    <bpmn:startEvent id="start"/>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="s1" sourceRef="start" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parse(xml);
            assertNotNull(def.getNode("start"));
            assertNotNull(def.getNode("end"));
            assertNull(def.getNode("ghost"));
        }
    }
}
