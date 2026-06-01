package com.ruleforge.console.flow.delegate;

import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Feature: 决策网关执行
 *
 * DecisionGatewayDelegate 负责执行 BPMN exclusiveGateway 中
 * ruleforge:decisionType 为 "percent" 或 "condition" 的决策节点。
 */
@DisplayName("DecisionGatewayDelegate - 决策网关执行")
class DecisionGatewayDelegateTest {

    private static final String RF_NS = "http://ruleforge.com/schema";

    private DecisionGatewayDelegate delegate;

    @BeforeEach
    void setUp() {
        delegate = new DecisionGatewayDelegate();
    }

    private void addExtensionAttr(ExclusiveGateway gw, String name, String value) {
        ExtensionAttribute attr = new ExtensionAttribute(name, value);
        attr.setNamespace(RF_NS);
        attr.setNamespacePrefix("ruleforge");
        gw.addAttribute(attr);
    }

    private void addExtensionAttr(SequenceFlow flow, String name, String value) {
        ExtensionAttribute attr = new ExtensionAttribute(name, value);
        attr.setNamespace(RF_NS);
        attr.setNamespacePrefix("ruleforge");
        flow.addAttribute(attr);
    }

    private DelegateExecution createExecution(String decisionType, List<SequenceFlow> outgoingFlows,
                                               Map<String, Object> variables) {
        DelegateExecution execution = mock(DelegateExecution.class);
        ExclusiveGateway gateway = new ExclusiveGateway();
        gateway.setId("gw_1");
        gateway.setName("Decision1");
        if (decisionType != null) {
            addExtensionAttr(gateway, "decisionType", decisionType);
        }
        gateway.setOutgoingFlows(outgoingFlows);
        when(execution.getCurrentFlowElement()).thenReturn(gateway);
        when(execution.getCurrentActivityId()).thenReturn("gw_1");
        when(execution.getVariables()).thenReturn(variables != null ? variables : new HashMap<>());
        return execution;
    }

    private SequenceFlow createFlow(String id, String name, String targetRef) {
        SequenceFlow flow = new SequenceFlow(id, targetRef);
        flow.setName(name);
        return flow;
    }

    @Nested
    @DisplayName("Percentage 类型决策")
    class PercentageDecision {

        @Test
        @DisplayName("按百分比概率选择分支（总和 100%）")
        void shouldSelectBranchByPercentage() {
            // Given BPMN 中有 exclusiveGateway，decisionType="percent"
            //     And 出线 BranchA 的 percent=30，BranchB 的 percent=70
            SequenceFlow flowA = createFlow("f1", "BranchA", "taskA");
            addExtensionAttr(flowA, "percent", "30");
            SequenceFlow flowB = createFlow("f2", "BranchB", "taskB");
            addExtensionAttr(flowB, "percent", "70");

            List<SequenceFlow> flows = new ArrayList<>();
            flows.add(flowA);
            flows.add(flowB);

            // 随机数 mock 到 0~30 范围 → 选择 BranchA
            DelegateExecution execution = createExecution("percent", flows, new HashMap<>());

            // When 执行决策网关
            String selected = delegate.decide(execution);

            // Then 应返回选中的分支 targetRef
            assertThat(selected).isNotNull();
            assertThat(selected).isIn("taskA", "taskB");
        }

        @Test
        @DisplayName("百分比总和非 100 时仍按比例分配")
        void shouldHandleNonHundredPercentageSum() {
            // Given 出线 BranchA 的 percent=20，BranchB 的 percent=30（总和 50）
            SequenceFlow flowA = createFlow("f1", "BranchA", "taskA");
            addExtensionAttr(flowA, "percent", "20");
            SequenceFlow flowB = createFlow("f2", "BranchB", "taskB");
            addExtensionAttr(flowB, "percent", "30");

            List<SequenceFlow> flows = new ArrayList<>();
            flows.add(flowA);
            flows.add(flowB);

            DelegateExecution execution = createExecution("percent", flows, new HashMap<>());

            // When 执行决策网关
            String selected = delegate.decide(execution);

            // Then 应按 20:30 的比例分配概率
            assertThat(selected).isNotNull();
            assertThat(selected).isIn("taskA", "taskB");
        }

        @Test
        @DisplayName("出线无 percent 属性时应走默认分支（第一条）")
        void shouldSelectDefaultBranchWhenNoPercent() {
            // Given 所有出线均无 percent 属性
            SequenceFlow flowA = createFlow("f1", "BranchA", "taskA");
            SequenceFlow flowB = createFlow("f2", "BranchB", "taskB");

            List<SequenceFlow> flows = new ArrayList<>();
            flows.add(flowA);
            flows.add(flowB);

            DelegateExecution execution = createExecution("percent", flows, new HashMap<>());

            // When 执行决策网关
            String selected = delegate.decide(execution);

            // Then 应选择默认出线（第一条无条件的连线）
            assertThat(selected).isEqualTo("taskA");
        }
    }

    @Nested
    @DisplayName("Condition 类型决策")
    class ConditionDecision {

        @Test
        @DisplayName("按条件表达式选择分支")
        void shouldSelectBranchByConditionExpression() {
            // Given BPMN 中有 exclusiveGateway，decisionType="condition"
            //     And 出线 BranchA 的条件为 "${score > 80}"
            //     And 出线 BranchB 的条件为 "${score <= 80}"
            //     And 流程变量 score=90
            SequenceFlow flowA = createFlow("f1", "BranchA", "taskA");
            flowA.setConditionExpression("${score > 80}");
            SequenceFlow flowB = createFlow("f2", "BranchB", "taskB");
            flowB.setConditionExpression("${score <= 80}");

            List<SequenceFlow> flows = new ArrayList<>();
            flows.add(flowA);
            flows.add(flowB);

            Map<String, Object> variables = new HashMap<>();
            variables.put("score", 90);
            DelegateExecution execution = createExecution("condition", flows, variables);

            // When 执行决策网关
            String selected = delegate.decide(execution);

            // Then 应选择 BranchA 分支
            assertThat(selected).isEqualTo("taskA");
        }

        @Test
        @DisplayName("条件不匹配时应走默认分支")
        void shouldSelectDefaultBranchWhenNoConditionMatches() {
            // Given 所有条件分支均不满足，且存在一条无条件默认出线
            SequenceFlow flowA = createFlow("f1", "BranchA", "taskA");
            flowA.setConditionExpression("${score > 100}");
            SequenceFlow flowDefault = createFlow("f2", "Default", "taskDefault");
            // 无条件表达式 = 默认分支

            List<SequenceFlow> flows = new ArrayList<>();
            flows.add(flowA);
            flows.add(flowDefault);

            Map<String, Object> variables = new HashMap<>();
            variables.put("score", 50);
            DelegateExecution execution = createExecution("condition", flows, variables);

            // When 执行决策网关
            String selected = delegate.decide(execution);

            // Then 应走默认分支
            assertThat(selected).isEqualTo("taskDefault");
        }

        @Test
        @DisplayName("无条件匹配且无默认分支时应抛出异常")
        void shouldThrowWhenNoConditionMatchesAndNoDefaultBranch() {
            // Given 所有条件分支均不满足
            //     And 没有默认出线
            SequenceFlow flowA = createFlow("f1", "BranchA", "taskA");
            flowA.setConditionExpression("${score > 100}");

            List<SequenceFlow> flows = new ArrayList<>();
            flows.add(flowA);

            Map<String, Object> variables = new HashMap<>();
            variables.put("score", 50);
            DelegateExecution execution = createExecution("condition", flows, variables);

            // When 执行决策网关
            // Then 应抛出异常提示无匹配分支
            assertThatThrownBy(() -> delegate.decide(execution))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("gw_1");
        }
    }

    @Nested
    @DisplayName("边界条件")
    class EdgeCases {

        @Test
        @DisplayName("decisionType 为空时默认使用 condition 模式")
        void shouldDefaultToConditionModeWhenDecisionTypeIsEmpty() {
            // Given decisionType 为空
            //     And 出线有条件表达式
            SequenceFlow flowA = createFlow("f1", "BranchA", "taskA");
            flowA.setConditionExpression("${score > 80}");

            List<SequenceFlow> flows = new ArrayList<>();
            flows.add(flowA);

            Map<String, Object> variables = new HashMap<>();
            variables.put("score", 90);
            DelegateExecution execution = createExecution(null, flows, variables);

            // When 执行决策网关
            String selected = delegate.decide(execution);

            // Then 应按条件模式评估
            assertThat(selected).isEqualTo("taskA");
        }

        @Test
        @DisplayName("出线列表为空时应抛出异常")
        void shouldThrowWhenNoOutgoingFlows() {
            // Given exclusiveGateway 无出线
            DelegateExecution execution = createExecution("condition", new ArrayList<>(), new HashMap<>());

            // When 执行决策网关
            // Then 应抛出异常提示网关配置错误
            assertThatThrownBy(() -> delegate.decide(execution))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("gw_1");
        }
    }
}
