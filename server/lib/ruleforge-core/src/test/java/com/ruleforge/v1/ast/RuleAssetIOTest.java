package com.ruleforge.v1.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V7.0.0 W1-1 — V1 AST Jackson 多态读写 BDD。
 *
 * <p>锁 V1 AST 设计契约(见 docs/design/v1-ruleforge-ast-draft.md):
 * <ul>
 *   <li>JSON {@code "type"} 字段路由多态反序列化(NodeBase 5 子类 + FlowElement 5 子类)</li>
 *   <li>{@code nodes} Map&lt;String, NodeBase&gt; 平铺,各节点 type 正确</li>
 *   <li>{@code flow.flowElements} 含 startEvent/serviceTask/endEvent/sequenceFlow 4 类</li>
 *   <li>round-trip:read → write → read 稳定(序列化不丢字段)</li>
 *   <li>Action 结构化:type/target/value/reason 各字段;value 字面量保留类型</li>
 * </ul>
 */
@DisplayName("V7.0.0 W1-1 — V1 AST Jackson 多态读写")
class RuleAssetIOTest {

    /** 从测试资源加载 loan_approval.json。 */
    private RuleAsset loadSample() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("loan_approval.json")) {
            assertThat(in).as("loan_approval.json 测试资源存在").isNotNull();
            return RuleAssetIO.read(in);
        }
    }

    @Nested
    @DisplayName("Given loan_approval.json When read Then 顶层 + schema 正确")
    class TopLevelAndSchema {

        // Given 资产 version=1.0 When read Then version/id/name 保留
        @Test
        void read_保留顶层字段() throws Exception {
            RuleAsset asset = loadSample();
            assertThat(asset.getVersion()).isEqualTo("1.0");
            assertThat(asset.getId()).isEqualTo("loan_approval");
            assertThat(asset.getName()).isEqualTo("贷款审批流程");
        }

        @Test
        void read_schema_字段名_类型_标签() throws Exception {
            // Given LoanApplication schema When read Then 7 字段 + age=NUMBER + blacklisted=BOOLEAN
            RuleAsset asset = loadSample();
            assertThat(asset.getSchema().getName()).isEqualTo("LoanApplication");
            assertThat(asset.getSchema().getFields())
                    .extracting(SchemaField::getName, SchemaField::getType, SchemaField::getLabel)
                    .contains(
                            tuple("age", V1DataType.NUMBER, "年龄"),
                            tuple("blacklisted", V1DataType.BOOLEAN, "黑名单"),
                            tuple("flags", V1DataType.LIST, "风险标记"));
        }

        // assertj tuple 静态导入替代 — 避免 org.assertj.core.groups.Tuple 全限定
        private org.assertj.core.groups.Tuple tuple(Object... values) {
            return org.assertj.core.api.Assertions.tuple(values);
        }
    }

    @Nested
    @DisplayName("Given nodes Map When read Then 各节点 type 多态路由正确")
    class NodePolymorphism {

        @Test
        void start_节点路由成_StartNode() throws Exception {
            // Given start 节点 type=Start When read Then 是 StartNode + schema 字段
            RuleAsset asset = loadSample();
            NodeBase start = asset.getNodes().get("start");
            assertThat(start).isInstanceOf(StartNode.class);
            assertThat(((StartNode) start).getSchema()).isEqualTo("LoanApplication");
        }

        @Test
        void ruleset_节点路由成_RuleSetNode_含_rules_actions() throws Exception {
            // Given precheck type=RuleSet When read Then RuleSetNode + FIRST_MATCH + 2 rules
            RuleAsset asset = loadSample();
            NodeBase precheck = asset.getNodes().get("precheck");
            assertThat(precheck).isInstanceOf(RuleSetNode.class);
            RuleSetNode rs = (RuleSetNode) precheck;
            assertThat(rs.getHitPolicy()).isEqualTo(HitPolicy.FIRST_MATCH);
            assertThat(rs.getRules()).hasSize(2);
            // 第 1 条 rule:priority + condition + REJECT action
            Rule blacklist = rs.getRules().get(0);
            assertThat(blacklist.getPriority()).isEqualTo(100);
            assertThat(blacklist.getCondition()).isEqualTo("blacklisted == true");
            assertThat(blacklist.getActions()).hasSize(1);
            Action act = blacklist.getActions().get(0);
            assertThat(act.getType()).isEqualTo(ActionType.REJECT);
            assertThat(act.getReason()).isEqualTo("BLACKLIST");
        }

        @Test
        void scorecard_节点路由成_ScoreCardNode_含_cards_bands() throws Exception {
            // Given risk type=ScoreCard When read Then ScoreCardNode + output=riskScore + SUM + age_card 2 bands
            RuleAsset asset = loadSample();
            NodeBase risk = asset.getNodes().get("risk");
            assertThat(risk).isInstanceOf(ScoreCardNode.class);
            ScoreCardNode sc = (ScoreCardNode) risk;
            assertThat(sc.getOutput()).isEqualTo("riskScore");
            assertThat(sc.getAggregation()).isEqualTo(ScoreAggregation.SUM);
            assertThat(sc.getCards()).hasSize(1);
            Card ageCard = sc.getCards().get(0);
            assertThat(ageCard.getField()).isEqualTo("age");
            assertThat(ageCard.getBands())
                    .extracting(Band::getCondition, Band::getScore)
                    .containsExactly(
                            tuple("age < 25", 20.0),
                            tuple("age >= 25", 50.0));
        }

        @Test
        void decisiontable_节点路由成_DecisionTableNode_含_inputs_outputs_rows() throws Exception {
            // Given pricing type=DecisionTable When read Then 2 inputs + 1 output + 2 rows
            RuleAsset asset = loadSample();
            NodeBase pricing = asset.getNodes().get("pricing");
            assertThat(pricing).isInstanceOf(DecisionTableNode.class);
            DecisionTableNode dt = (DecisionTableNode) pricing;
            assertThat(dt.getHitPolicy()).isEqualTo(TableHitPolicy.FIRST);
            assertThat(dt.getInputs()).extracting(Column::getName)
                    .containsExactly("riskScore", "score");
            assertThat(dt.getOutputs()).extracting(Column::getName).containsExactly("decision");
            assertThat(dt.getRows()).hasSize(2);
            // r1 行:conditions 含通配 "*" + outputs ["approve"]
            TableRow r1 = dt.getRows().get(0);
            assertThat(r1.getConditions()).containsExactly("riskScore < 30", "*");
            assertThat(r1.getOutputs()).containsExactly("approve");
        }

        @Test
        void decision_节点路由成_DecisionNode() throws Exception {
            // Given decision type=Decision When read Then DecisionNode + outputs + defaultOutput
            RuleAsset asset = loadSample();
            NodeBase decision = asset.getNodes().get("decision");
            assertThat(decision).isInstanceOf(DecisionNode.class);
            DecisionNode dn = (DecisionNode) decision;
            assertThat(dn.getOutputs()).containsExactly("approve", "review", "reject");
            assertThat(dn.getDecisionField()).isEqualTo("decision");
            assertThat(dn.getDefaultOutput()).isEqualTo("review");
        }

        private org.assertj.core.groups.Tuple tuple(Object... values) {
            return org.assertj.core.api.Assertions.tuple(values);
        }
    }

    @Nested
    @DisplayName("Given flow.flowElements When read Then BPMN 元素多态路由正确")
    class FlowPolymorphism {

        @Test
        void flowElements_含_4_类_BPMN_元素() throws Exception {
            // Given flow When read Then startEvent/serviceTask/endEvent/sequenceFlow 各路由成对应类
            RuleAsset asset = loadSample();
            assertThat(asset.getFlow().getFlowElements())
                    .extracting(FlowElement::getType)
                    .contains("startEvent", "serviceTask", "endEvent", "sequenceFlow");
        }

        @Test
        void serviceTask_implementation_引用节点() throws Exception {
            // Given t_pre serviceTask When read Then implementation="RuleSet:precheck"
            RuleAsset asset = loadSample();
            ServiceTask tPre = asset.getFlow().getFlowElements().stream()
                    .filter(e -> e.getId().equals("t_pre"))
                    .map(e -> (ServiceTask) e)
                    .findFirst().orElseThrow();
            assertThat(tPre.getImplementation()).isEqualTo("RuleSet:precheck");
        }

        @Test
        void startEvent_保留_position_presentation_only() throws Exception {
            // Given start 带 position When read Then position 读出(运行时忽略但需持久化)
            RuleAsset asset = loadSample();
            StartEvent start = asset.getFlow().getFlowElements().stream()
                    .filter(e -> e.getId().equals("start"))
                    .map(e -> (StartEvent) e)
                    .findFirst().orElseThrow();
            assertThat(start.getPosition()).isNotNull();
            assertThat(start.getPosition().getX()).isEqualTo(50.0);
        }
    }

    @Nested
    @DisplayName("Given RuleAsset When write Then round-trip 稳定")
    class RoundTrip {

        @Test
        void read_write_read_节点数_不变() throws Exception {
            // Given asset When write→json then read→asset2 Then nodes/flowElements 数不变 + 决策值稳定
            RuleAsset asset = loadSample();
            String json = RuleAssetIO.write(asset);
            RuleAsset asset2 = RuleAssetIO.read(json);
            assertThat(asset2.getNodes()).hasSameSizeAs(asset.getNodes());
            assertThat(asset2.getFlow().getFlowElements()).hasSameSizeAs(asset.getFlow().getFlowElements());
            // 多态 type 在 round-trip 后仍正确
            assertThat(asset2.getNodes().get("precheck")).isInstanceOf(RuleSetNode.class);
            assertThat(asset2.getNodes().get("risk")).isInstanceOf(ScoreCardNode.class);
        }

        @Test
        void write_保留_action_value_字面量类型() throws Exception {
            // Given SET_VARIABLE action value=0.18 When round-trip Then value 仍是 double
            // 构造一个带 SET_VARIABLE 的 asset 验证 value 类型保留
            RuleAsset asset = loadSample();
            Rule rule = ((RuleSetNode) asset.getNodes().get("precheck")).getRules().get(0);
            Action setVar = new Action(ActionType.SET_VARIABLE);
            setVar.setTarget("rate");
            setVar.setValue(0.18);
            rule.setActions(java.util.Collections.singletonList(setVar));

            String json = RuleAssetIO.write(asset);
            RuleAsset asset2 = RuleAssetIO.read(json);
            Action roundTripped = ((RuleSetNode) asset2.getNodes().get("precheck")).getRules().get(0).getActions().get(0);
            assertThat(roundTripped.getType()).isEqualTo(ActionType.SET_VARIABLE);
            assertThat(roundTripped.getTarget()).isEqualTo("rate");
            assertThat(roundTripped.getValue()).isInstanceOf(Double.class);
            assertThat((Double) roundTripped.getValue()).isEqualTo(0.18);
        }
    }
}
