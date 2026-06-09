package com.ruleforge.builder;

import com.ruleforge.action.ConsolePrintAction;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.decisiontree.*;
import com.ruleforge.model.rule.*;
import com.ruleforge.model.rule.lhs.And;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.Left;
import com.ruleforge.model.rule.lhs.Lhs;
import com.ruleforge.model.rule.lhs.LeftType;
import com.ruleforge.model.rule.lhs.VariableLeftPart;
import com.ruleforge.model.rule.SimpleValue;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("决策树规则构建器")
class DecisionTreeRulesBuilderTest {

    private DecisionTreeRulesBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new DecisionTreeRulesBuilder();
    }

    @Nested
    @DisplayName("构建规则集")
    class BuildRuleSet {

        @Test
        @DisplayName("Given 包含变量节点和条件节点的决策树 When 调用buildRules Then 应返回包含规则的RuleSet对象")
        void shouldBuildRuleSetFromDecisionTree() throws RuleException {
            // Given
            DecisionTree tree = new DecisionTree();
            tree.setSalience(10);
            tree.setEnabled(true);
            tree.setDebug(false);

            VariableTreeNode varNode = new VariableTreeNode();
            varNode.setNodeType(TreeNodeType.variable);

            ConditionTreeNode conditionNode = new ConditionTreeNode();
            conditionNode.setNodeType(TreeNodeType.condition);
            conditionNode.setOp(Op.Equals);
            conditionNode.setValue(createConstantValue("100"));
            conditionNode.setParentNode(varNode);

            ActionTreeNode actionNode = new ActionTreeNode();
            actionNode.setNodeType(TreeNodeType.action);
            List<com.ruleforge.action.Action> actions = new ArrayList<>();
            ConsolePrintAction action = new ConsolePrintAction();
            SimpleValue actionValue = new SimpleValue();
            actionValue.setContent("Test action");
            action.setValue(actionValue);
            actions.add(action);
            actionNode.setActions(actions);
            actionNode.setParentNode(conditionNode);

            List<ConditionTreeNode> conditionNodes = new ArrayList<>();
            conditionNodes.add(conditionNode);
            varNode.setConditionTreeNodes(conditionNodes);

            List<ActionTreeNode> actionNodes = new ArrayList<>();
            actionNodes.add(actionNode);
            conditionNode.setActionTreeNodes(actionNodes);

            tree.setVariableTreeNode(varNode);

            DecisionTreeRulesBuilder builder = new DecisionTreeRulesBuilder();

            // When
            RuleSet ruleSet = builder.buildRules(tree);

            // Then
            assertThat(ruleSet).isNotNull();
            assertThat(ruleSet.getRules()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("转换变量节点条件")
    class ConvertVariableNodeConditions {

        @Test
        @DisplayName("Given 变量节点包含left属性 When 构建规则LHS Then left应正确转换为规则条件")
        void shouldConvertVariableNodeToRuleLhs() throws RuleException {
            // Given
            DecisionTree tree = new DecisionTree();
            VariableTreeNode varNode = new VariableTreeNode();
            varNode.setNodeType(TreeNodeType.variable);

            Left left = new Left();
            VariableLeftPart leftPart = new VariableLeftPart();
            leftPart.setVariableName("age");
            left.setLeftPart(leftPart);
            left.setType(LeftType.variable);
            varNode.setLeft(left);

            ConditionTreeNode conditionNode = new ConditionTreeNode();
            conditionNode.setNodeType(TreeNodeType.condition);
            conditionNode.setOp(Op.Equals);
            conditionNode.setValue(createConstantValue("18"));
            conditionNode.setParentNode(varNode);

            List<ActionTreeNode> actionNodes = new ArrayList<>();
            ActionTreeNode actionNode = new ActionTreeNode();
            actionNode.setNodeType(TreeNodeType.action);
            actionNode.setParentNode(conditionNode);
            List<com.ruleforge.action.Action> actions = new ArrayList<>();
            ConsolePrintAction action = new ConsolePrintAction();
            SimpleValue value = new SimpleValue();
            value.setContent("Adult");
            action.setValue(value);
            actions.add(action);
            actionNode.setActions(actions);
            actionNodes.add(actionNode);
            conditionNode.setActionTreeNodes(actionNodes);

            List<ConditionTreeNode> conditionNodes = new ArrayList<>();
            conditionNodes.add(conditionNode);
            varNode.setConditionTreeNodes(conditionNodes);

            tree.setVariableTreeNode(varNode);

            DecisionTreeRulesBuilder builder = new DecisionTreeRulesBuilder();

            // When
            RuleSet ruleSet = builder.buildRules(tree);

            // Then
            assertThat(ruleSet).isNotNull();
            assertThat(ruleSet.getRules()).isNotEmpty();
            Rule rule = ruleSet.getRules().get(0);
            assertThat(rule.getLhs()).isNotNull();
            assertThat(rule.getLhs().getCriterion()).isInstanceOf(And.class);
        }

        @Test
        @DisplayName("Given 变量节点包含条件子节点 When 构建规则LHS Then 条件应使用AND连接")
        void shouldConvertConditionsWithAndOperator() throws RuleException {
            // Given
            DecisionTree tree = new DecisionTree();
            VariableTreeNode varNode = new VariableTreeNode();
            varNode.setNodeType(TreeNodeType.variable);

            ConditionTreeNode conditionNode = new ConditionTreeNode();
            conditionNode.setNodeType(TreeNodeType.condition);
            conditionNode.setOp(Op.Equals);
            conditionNode.setValue(createConstantValue("18"));
            conditionNode.setParentNode(varNode);

            ActionTreeNode actionNode = new ActionTreeNode();
            actionNode.setNodeType(TreeNodeType.action);
            actionNode.setParentNode(conditionNode);
            List<com.ruleforge.action.Action> actions = new ArrayList<>();
            ConsolePrintAction action = new ConsolePrintAction();
            SimpleValue value = new SimpleValue();
            value.setContent("Test");
            action.setValue(value);
            actions.add(action);
            actionNode.setActions(actions);

            List<ActionTreeNode> actionNodes = new ArrayList<>();
            actionNodes.add(actionNode);
            conditionNode.setActionTreeNodes(actionNodes);

            List<ConditionTreeNode> conditionNodes = new ArrayList<>();
            conditionNodes.add(conditionNode);
            varNode.setConditionTreeNodes(conditionNodes);

            tree.setVariableTreeNode(varNode);

            DecisionTreeRulesBuilder builder = new DecisionTreeRulesBuilder();

            // When
            RuleSet ruleSet = builder.buildRules(tree);

            // Then
            assertThat(ruleSet).isNotNull();
            assertThat(ruleSet.getRules()).isNotEmpty();
            Rule rule = ruleSet.getRules().get(0);
            assertThat(rule.getLhs()).isNotNull();
            assertThat(rule.getLhs().getCriterion()).isInstanceOf(And.class);
        }
    }

    @Nested
    @DisplayName("转换动作节点")
    class ConvertActionNodes {

        @Test
        @DisplayName("Given 动作节点包含动作列表 When 构建规则RHS Then 动作应正确添加到规则RHS")
        void shouldConvertActionNodeToRuleRhs() throws RuleException {
            // Given
            DecisionTree tree = new DecisionTree();
            VariableTreeNode varNode = new VariableTreeNode();
            varNode.setNodeType(TreeNodeType.variable);

            ConditionTreeNode conditionNode = new ConditionTreeNode();
            conditionNode.setNodeType(TreeNodeType.condition);
            conditionNode.setOp(Op.Equals);
            conditionNode.setValue(createConstantValue("100"));
            conditionNode.setParentNode(varNode);

            ActionTreeNode actionNode = new ActionTreeNode();
            actionNode.setNodeType(TreeNodeType.action);
            actionNode.setParentNode(conditionNode);
            List<com.ruleforge.action.Action> actions = new ArrayList<>();
            ConsolePrintAction action1 = new ConsolePrintAction();
            SimpleValue value1 = new SimpleValue();
            value1.setContent("Action 1");
            action1.setValue(value1);
            actions.add(action1);
            ConsolePrintAction action2 = new ConsolePrintAction();
            SimpleValue value2 = new SimpleValue();
            value2.setContent("Action 2");
            action2.setValue(value2);
            actions.add(action2);
            actionNode.setActions(actions);

            List<ActionTreeNode> actionNodes = new ArrayList<>();
            actionNodes.add(actionNode);
            conditionNode.setActionTreeNodes(actionNodes);

            List<ConditionTreeNode> conditionNodes = new ArrayList<>();
            conditionNodes.add(conditionNode);
            varNode.setConditionTreeNodes(conditionNodes);

            tree.setVariableTreeNode(varNode);

            DecisionTreeRulesBuilder builder = new DecisionTreeRulesBuilder();

            // When
            RuleSet ruleSet = builder.buildRules(tree);

            // Then
            assertThat(ruleSet).isNotNull();
            assertThat(ruleSet.getRules()).isNotEmpty();
            Rule rule = ruleSet.getRules().get(0);
            assertThat(rule.getRhs()).isNotNull();
            assertThat(rule.getRhs().getActions()).hasSize(2);
        }

        @Test
        @DisplayName("Given 多个动作节点 When 构建规则集 Then 应为每个动作节点生成一条规则")
        void shouldCreateRuleForEachActionNode() throws RuleException {
            // Given
            DecisionTree tree = new DecisionTree();
            VariableTreeNode varNode = new VariableTreeNode();
            varNode.setNodeType(TreeNodeType.variable);

            ConditionTreeNode conditionNode = new ConditionTreeNode();
            conditionNode.setNodeType(TreeNodeType.condition);
            conditionNode.setOp(Op.Equals);
            conditionNode.setValue(createConstantValue("100"));
            conditionNode.setParentNode(varNode);

            ActionTreeNode actionNode1 = new ActionTreeNode();
            actionNode1.setNodeType(TreeNodeType.action);
            actionNode1.setParentNode(conditionNode);
            List<com.ruleforge.action.Action> actions1 = new ArrayList<>();
            ConsolePrintAction action1 = new ConsolePrintAction();
            SimpleValue value1 = new SimpleValue();
            value1.setContent("Action 1");
            action1.setValue(value1);
            actions1.add(action1);
            actionNode1.setActions(actions1);

            ActionTreeNode actionNode2 = new ActionTreeNode();
            actionNode2.setNodeType(TreeNodeType.action);
            actionNode2.setParentNode(conditionNode);
            List<com.ruleforge.action.Action> actions2 = new ArrayList<>();
            ConsolePrintAction action2 = new ConsolePrintAction();
            SimpleValue value2 = new SimpleValue();
            value2.setContent("Action 2");
            action2.setValue(value2);
            actions2.add(action2);
            actionNode2.setActions(actions2);

            List<ActionTreeNode> actionNodes = new ArrayList<>();
            actionNodes.add(actionNode1);
            actionNodes.add(actionNode2);
            conditionNode.setActionTreeNodes(actionNodes);

            List<ConditionTreeNode> conditionNodes = new ArrayList<>();
            conditionNodes.add(conditionNode);
            varNode.setConditionTreeNodes(conditionNodes);

            tree.setVariableTreeNode(varNode);

            DecisionTreeRulesBuilder builder = new DecisionTreeRulesBuilder();

            // When
            RuleSet ruleSet = builder.buildRules(tree);

            // Then
            assertThat(ruleSet).isNotNull();
            assertThat(ruleSet.getRules()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("处理多层嵌套结构")
    class HandleNestedStructure {

        @Test
        @DisplayName("Given 包含多层嵌套的条件节点 When 构建规则LHS Then 应正确构建嵌套条件组合")
        void shouldBuildNestedConditionStructure() throws RuleException {
            // Given
            DecisionTree tree = new DecisionTree();
            VariableTreeNode varNode = new VariableTreeNode();
            varNode.setNodeType(TreeNodeType.variable);

            ConditionTreeNode conditionNode = new ConditionTreeNode();
            conditionNode.setNodeType(TreeNodeType.condition);
            conditionNode.setOp(Op.Equals);
            conditionNode.setValue(createConstantValue("100"));
            conditionNode.setParentNode(varNode);

            ConditionTreeNode nestedConditionNode = new ConditionTreeNode();
            nestedConditionNode.setNodeType(TreeNodeType.condition);
            nestedConditionNode.setOp(Op.GreaterThen);
            nestedConditionNode.setValue(createConstantValue("50"));
            nestedConditionNode.setParentNode(conditionNode);

            ActionTreeNode actionNode = new ActionTreeNode();
            actionNode.setNodeType(TreeNodeType.action);
            actionNode.setParentNode(nestedConditionNode);
            List<com.ruleforge.action.Action> actions = new ArrayList<>();
            ConsolePrintAction action = new ConsolePrintAction();
            SimpleValue value = new SimpleValue();
            value.setContent("Test");
            action.setValue(value);
            actions.add(action);
            actionNode.setActions(actions);

            List<ActionTreeNode> actionNodes = new ArrayList<>();
            actionNodes.add(actionNode);
            nestedConditionNode.setActionTreeNodes(actionNodes);

            List<ConditionTreeNode> nestedConditions = new ArrayList<>();
            nestedConditions.add(nestedConditionNode);
            conditionNode.setConditionTreeNodes(nestedConditions);

            List<ConditionTreeNode> conditionNodes = new ArrayList<>();
            conditionNodes.add(conditionNode);
            varNode.setConditionTreeNodes(conditionNodes);

            tree.setVariableTreeNode(varNode);

            DecisionTreeRulesBuilder builder = new DecisionTreeRulesBuilder();

            // When
            RuleSet ruleSet = builder.buildRules(tree);

            // Then
            assertThat(ruleSet).isNotNull();
            assertThat(ruleSet.getRules()).isNotEmpty();
        }

        @Test
        @DisplayName("Given 条件节点嵌套在变量节点下 When 构建规则LHS Then 应使用父变量节点的left属性")
        void shouldUseParentVariableNodeForNestedConditions() throws RuleException {
            // Given
            DecisionTree tree = new DecisionTree();
            VariableTreeNode varNode = new VariableTreeNode();
            varNode.setNodeType(TreeNodeType.variable);

            Left left = new Left();
            VariableLeftPart leftPart = new VariableLeftPart();
            leftPart.setVariableName("age");
            left.setLeftPart(leftPart);
            left.setType(LeftType.variable);
            varNode.setLeft(left);

            ConditionTreeNode conditionNode = new ConditionTreeNode();
            conditionNode.setNodeType(TreeNodeType.condition);
            conditionNode.setOp(Op.Equals);
            conditionNode.setValue(createConstantValue("18"));
            conditionNode.setParentNode(varNode);

            ActionTreeNode actionNode = new ActionTreeNode();
            actionNode.setNodeType(TreeNodeType.action);
            actionNode.setParentNode(conditionNode);
            List<com.ruleforge.action.Action> actions = new ArrayList<>();
            ConsolePrintAction action = new ConsolePrintAction();
            SimpleValue value = new SimpleValue();
            value.setContent("Adult");
            action.setValue(value);
            actions.add(action);
            actionNode.setActions(actions);

            List<ActionTreeNode> actionNodes = new ArrayList<>();
            actionNodes.add(actionNode);
            conditionNode.setActionTreeNodes(actionNodes);

            List<ConditionTreeNode> conditionNodes = new ArrayList<>();
            conditionNodes.add(conditionNode);
            varNode.setConditionTreeNodes(conditionNodes);

            tree.setVariableTreeNode(varNode);

            DecisionTreeRulesBuilder builder = new DecisionTreeRulesBuilder();

            // When
            RuleSet ruleSet = builder.buildRules(tree);

            // Then
            assertThat(ruleSet).isNotNull();
            assertThat(ruleSet.getRules()).isNotEmpty();
            Rule rule = ruleSet.getRules().get(0);
            assertThat(rule.getLhs()).isNotNull();
            assertThat(rule.getLhs().getCriterion()).isInstanceOf(And.class);
        }

        @Test
        @DisplayName("Given 条件节点嵌套在另一个条件节点下 When 构建规则LHS Then 两个条件都应添加到规则LHS")
        void shouldCombineNestedConditions() throws RuleException {
            // Given
            DecisionTree tree = new DecisionTree();
            VariableTreeNode varNode = new VariableTreeNode();
            varNode.setNodeType(TreeNodeType.variable);

            ConditionTreeNode conditionNode1 = new ConditionTreeNode();
            conditionNode1.setNodeType(TreeNodeType.condition);
            conditionNode1.setOp(Op.Equals);
            conditionNode1.setValue(createConstantValue("100"));
            conditionNode1.setParentNode(varNode);

            ConditionTreeNode conditionNode2 = new ConditionTreeNode();
            conditionNode2.setNodeType(TreeNodeType.condition);
            conditionNode2.setOp(Op.GreaterThen);
            conditionNode2.setValue(createConstantValue("50"));
            conditionNode2.setParentNode(conditionNode1);

            ActionTreeNode actionNode = new ActionTreeNode();
            actionNode.setNodeType(TreeNodeType.action);
            actionNode.setParentNode(conditionNode2);
            List<com.ruleforge.action.Action> actions = new ArrayList<>();
            ConsolePrintAction action = new ConsolePrintAction();
            SimpleValue value = new SimpleValue();
            value.setContent("Test");
            action.setValue(value);
            actions.add(action);
            actionNode.setActions(actions);

            List<ActionTreeNode> actionNodes = new ArrayList<>();
            actionNodes.add(actionNode);
            conditionNode2.setActionTreeNodes(actionNodes);

            List<ConditionTreeNode> nestedConditions = new ArrayList<>();
            nestedConditions.add(conditionNode2);
            conditionNode1.setConditionTreeNodes(nestedConditions);

            List<ConditionTreeNode> conditionNodes = new ArrayList<>();
            conditionNodes.add(conditionNode1);
            varNode.setConditionTreeNodes(conditionNodes);

            tree.setVariableTreeNode(varNode);

            DecisionTreeRulesBuilder builder = new DecisionTreeRulesBuilder();

            // When
            RuleSet ruleSet = builder.buildRules(tree);

            // Then
            assertThat(ruleSet).isNotNull();
            assertThat(ruleSet.getRules()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("转换不同条件操作符")
    class ConvertConditionOperators {

        @Test
        @DisplayName("Given 条件节点操作符为等于 When 构建规则LHS Then Criteria应使用等于操作符")
        void shouldConvertEqualsOperator() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 条件节点操作符为不等于 When 构建规则LHS Then Criteria应使用不等于操作符")
        void shouldConvertNotEqualsOperator() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 条件节点操作符为大于 When 构建规则LHS Then Criteria应使用大于操作符")
        void shouldConvertGreaterThanOperator() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 条件节点操作符为小于 When 构建规则LHS Then Criteria应使用小于操作符")
        void shouldConvertLessThanOperator() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 条件节点操作符为大于等于 When 构建规则LHS Then Criteria应使用大于等于操作符")
        void shouldConvertGreaterThanOrEqualOperator() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 条件节点操作符为小于等于 When 构建规则LHS Then Criteria应使用小于等于操作符")
        void shouldConvertLessThanOrEqualOperator() {
            // Given

            // When

            // Then
        }
    }

    @Nested
    @DisplayName("构建规则属性")
    class BuildRuleAttributes {

        @Test
        @DisplayName("Given 决策树包含salience属性 When 构建规则 Then 规则应继承salience属性")
        void shouldInheritSalienceFromDecisionTree() throws RuleException {
            // Given
            DecisionTree tree = new DecisionTree();
            tree.setSalience(100);
            VariableTreeNode varNode = new VariableTreeNode();
            varNode.setNodeType(TreeNodeType.variable);

            ConditionTreeNode conditionNode = new ConditionTreeNode();
            conditionNode.setNodeType(TreeNodeType.condition);
            conditionNode.setOp(Op.Equals);
            conditionNode.setValue(createConstantValue("100"));
            conditionNode.setParentNode(varNode);

            ActionTreeNode actionNode = new ActionTreeNode();
            actionNode.setNodeType(TreeNodeType.action);
            actionNode.setParentNode(conditionNode);
            List<com.ruleforge.action.Action> actions = new ArrayList<>();
            ConsolePrintAction action = new ConsolePrintAction();
            SimpleValue value = new SimpleValue();
            value.setContent("Test");
            action.setValue(value);
            actions.add(action);
            actionNode.setActions(actions);

            List<ActionTreeNode> actionNodes = new ArrayList<>();
            actionNodes.add(actionNode);
            conditionNode.setActionTreeNodes(actionNodes);

            List<ConditionTreeNode> conditionNodes = new ArrayList<>();
            conditionNodes.add(conditionNode);
            varNode.setConditionTreeNodes(conditionNodes);

            tree.setVariableTreeNode(varNode);

            DecisionTreeRulesBuilder builder = new DecisionTreeRulesBuilder();

            // When
            RuleSet ruleSet = builder.buildRules(tree);

            // Then
            assertThat(ruleSet).isNotNull();
            assertThat(ruleSet.getRules()).isNotEmpty();
            assertThat(ruleSet.getRules().get(0).getSalience()).isEqualTo(100);
        }

        @Test
        @DisplayName("Given 决策树包含enabled属性 When 构建规则 Then 规则应继承enabled属性")
        void shouldInheritEnabledFromDecisionTree() throws RuleException {
            // Given
            DecisionTree tree = new DecisionTree();
            tree.setEnabled(true);
            VariableTreeNode varNode = new VariableTreeNode();
            varNode.setNodeType(TreeNodeType.variable);

            ConditionTreeNode conditionNode = new ConditionTreeNode();
            conditionNode.setNodeType(TreeNodeType.condition);
            conditionNode.setOp(Op.Equals);
            conditionNode.setValue(createConstantValue("100"));
            conditionNode.setParentNode(varNode);

            ActionTreeNode actionNode = new ActionTreeNode();
            actionNode.setNodeType(TreeNodeType.action);
            actionNode.setParentNode(conditionNode);
            List<com.ruleforge.action.Action> actions = new ArrayList<>();
            ConsolePrintAction action = new ConsolePrintAction();
            SimpleValue value = new SimpleValue();
            value.setContent("Test");
            action.setValue(value);
            actions.add(action);
            actionNode.setActions(actions);

            List<ActionTreeNode> actionNodes = new ArrayList<>();
            actionNodes.add(actionNode);
            conditionNode.setActionTreeNodes(actionNodes);

            List<ConditionTreeNode> conditionNodes = new ArrayList<>();
            conditionNodes.add(conditionNode);
            varNode.setConditionTreeNodes(conditionNodes);

            tree.setVariableTreeNode(varNode);

            DecisionTreeRulesBuilder builder = new DecisionTreeRulesBuilder();

            // When
            RuleSet ruleSet = builder.buildRules(tree);

            // Then
            assertThat(ruleSet).isNotNull();
            assertThat(ruleSet.getRules()).isNotEmpty();
            assertThat(ruleSet.getRules().get(0).getEnabled()).isTrue();
        }

        @Test
        @DisplayName("Given 决策树包含debug属性 When 构建规则 Then 规则应继承debug属性")
        void shouldInheritDebugFromDecisionTree() throws RuleException {
            // Given
            DecisionTree tree = new DecisionTree();
            tree.setDebug(true);
            VariableTreeNode varNode = new VariableTreeNode();
            varNode.setNodeType(TreeNodeType.variable);

            ConditionTreeNode conditionNode = new ConditionTreeNode();
            conditionNode.setNodeType(TreeNodeType.condition);
            conditionNode.setOp(Op.Equals);
            conditionNode.setValue(createConstantValue("100"));
            conditionNode.setParentNode(varNode);

            ActionTreeNode actionNode = new ActionTreeNode();
            actionNode.setNodeType(TreeNodeType.action);
            actionNode.setParentNode(conditionNode);
            List<com.ruleforge.action.Action> actions = new ArrayList<>();
            ConsolePrintAction action = new ConsolePrintAction();
            SimpleValue value = new SimpleValue();
            value.setContent("Test");
            action.setValue(value);
            actions.add(action);
            actionNode.setActions(actions);

            List<ActionTreeNode> actionNodes = new ArrayList<>();
            actionNodes.add(actionNode);
            conditionNode.setActionTreeNodes(actionNodes);

            List<ConditionTreeNode> conditionNodes = new ArrayList<>();
            conditionNodes.add(conditionNode);
            varNode.setConditionTreeNodes(conditionNodes);

            tree.setVariableTreeNode(varNode);

            DecisionTreeRulesBuilder builder = new DecisionTreeRulesBuilder();

            // When
            RuleSet ruleSet = builder.buildRules(tree);

            // Then
            assertThat(ruleSet).isNotNull();
            assertThat(ruleSet.getRules()).isNotEmpty();
            assertThat(ruleSet.getRules().get(0).getDebug()).isTrue();
        }

        @Test
        @DisplayName("Given 决策树包含有效日期属性 When 构建规则 Then 规则应继承有效日期属性")
        void shouldInheritEffectiveDateFromDecisionTree() throws RuleException {
            // Given
            Date effectiveDate = new Date();
            DecisionTree tree = new DecisionTree();
            tree.setEffectiveDate(effectiveDate);
            VariableTreeNode varNode = new VariableTreeNode();
            varNode.setNodeType(TreeNodeType.variable);

            ConditionTreeNode conditionNode = new ConditionTreeNode();
            conditionNode.setNodeType(TreeNodeType.condition);
            conditionNode.setOp(Op.Equals);
            conditionNode.setValue(createConstantValue("100"));
            conditionNode.setParentNode(varNode);

            ActionTreeNode actionNode = new ActionTreeNode();
            actionNode.setNodeType(TreeNodeType.action);
            actionNode.setParentNode(conditionNode);
            List<com.ruleforge.action.Action> actions = new ArrayList<>();
            ConsolePrintAction action = new ConsolePrintAction();
            SimpleValue value = new SimpleValue();
            value.setContent("Test");
            action.setValue(value);
            actions.add(action);
            actionNode.setActions(actions);

            List<ActionTreeNode> actionNodes = new ArrayList<>();
            actionNodes.add(actionNode);
            conditionNode.setActionTreeNodes(actionNodes);

            List<ConditionTreeNode> conditionNodes = new ArrayList<>();
            conditionNodes.add(conditionNode);
            varNode.setConditionTreeNodes(conditionNodes);

            tree.setVariableTreeNode(varNode);

            DecisionTreeRulesBuilder builder = new DecisionTreeRulesBuilder();

            // When
            RuleSet ruleSet = builder.buildRules(tree);

            // Then
            assertThat(ruleSet).isNotNull();
            assertThat(ruleSet.getRules()).isNotEmpty();
            assertThat(ruleSet.getRules().get(0).getEffectiveDate()).isEqualTo(effectiveDate);
        }

        @Test
        @DisplayName("Given 决策树包含过期日期属性 When 构建规则 Then 规则应继承过期日期属性")
        void shouldInheritExpiresDateFromDecisionTree() throws RuleException {
            // Given
            Date expiresDate = new Date();
            DecisionTree tree = new DecisionTree();
            tree.setExpiresDate(expiresDate);
            VariableTreeNode varNode = new VariableTreeNode();
            varNode.setNodeType(TreeNodeType.variable);

            ConditionTreeNode conditionNode = new ConditionTreeNode();
            conditionNode.setNodeType(TreeNodeType.condition);
            conditionNode.setOp(Op.Equals);
            conditionNode.setValue(createConstantValue("100"));
            conditionNode.setParentNode(varNode);

            ActionTreeNode actionNode = new ActionTreeNode();
            actionNode.setNodeType(TreeNodeType.action);
            actionNode.setParentNode(conditionNode);
            List<com.ruleforge.action.Action> actions = new ArrayList<>();
            ConsolePrintAction action = new ConsolePrintAction();
            SimpleValue value = new SimpleValue();
            value.setContent("Test");
            action.setValue(value);
            actions.add(action);
            actionNode.setActions(actions);

            List<ActionTreeNode> actionNodes = new ArrayList<>();
            actionNodes.add(actionNode);
            conditionNode.setActionTreeNodes(actionNodes);

            List<ConditionTreeNode> conditionNodes = new ArrayList<>();
            conditionNodes.add(conditionNode);
            varNode.setConditionTreeNodes(conditionNodes);

            tree.setVariableTreeNode(varNode);

            DecisionTreeRulesBuilder builder = new DecisionTreeRulesBuilder();

            // When
            RuleSet ruleSet = builder.buildRules(tree);

            // Then
            assertThat(ruleSet).isNotNull();
            assertThat(ruleSet.getRules()).isNotEmpty();
            assertThat(ruleSet.getRules().get(0).getExpiresDate()).isEqualTo(expiresDate);
        }
    }

    @Nested
    @DisplayName("错误处理")
    class ErrorHandling {

        @Test
        @DisplayName("Given 不包含变量节点的决策树 When 调用buildRules Then 应抛出RuleException异常")
        void shouldThrowExceptionWhenNoVariableNode() {
            // Given
            DecisionTree tree = new DecisionTree();
            tree.setVariableTreeNode(null);
            DecisionTreeRulesBuilder builder = new DecisionTreeRulesBuilder();

            // When & Then
            assertThatThrownBy(() -> builder.buildRules(tree))
                    .isInstanceOf(RuleException.class);
        }

        @Test
        @DisplayName("Given 条件节点找不到父变量节点 When 构建规则LHS Then 应抛出RuleException异常")
        void shouldThrowExceptionWhenNoParentVariableNode() {
            // Given
            DecisionTree tree = new DecisionTree();
            VariableTreeNode varNode = new VariableTreeNode();
            varNode.setNodeType(TreeNodeType.variable);

            ConditionTreeNode conditionNode = new ConditionTreeNode();
            conditionNode.setNodeType(TreeNodeType.condition);
            conditionNode.setOp(Op.Equals);
            conditionNode.setValue(createConstantValue("100"));
            // Not setting parent node to simulate orphan condition

            ActionTreeNode actionNode = new ActionTreeNode();
            actionNode.setNodeType(TreeNodeType.action);
            actionNode.setParentNode(conditionNode);
            List<com.ruleforge.action.Action> actions = new ArrayList<>();
            ConsolePrintAction action = new ConsolePrintAction();
            SimpleValue value = new SimpleValue();
            value.setContent("Test");
            action.setValue(value);
            actions.add(action);
            actionNode.setActions(actions);

            List<ActionTreeNode> actionNodes = new ArrayList<>();
            actionNodes.add(actionNode);
            conditionNode.setActionTreeNodes(actionNodes);

            List<ConditionTreeNode> conditionNodes = new ArrayList<>();
            conditionNodes.add(conditionNode);
            varNode.setConditionTreeNodes(conditionNodes);

            tree.setVariableTreeNode(varNode);

            DecisionTreeRulesBuilder builder = new DecisionTreeRulesBuilder();

            // When & Then
            assertThatThrownBy(() -> builder.buildRules(tree))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining("Decision tree is invalid");
        }
    }

    @Nested
    @DisplayName("复制库引用")
    class CopyLibraryReferences {

        @Test
        @DisplayName("Given 决策树包含库引用列表 When 构建规则集 Then RuleSet应包含相同的库引用列表")
        void shouldCopyLibrariesToRuleSet() throws RuleException {
            // Given
            DecisionTree tree = new DecisionTree();
            tree.setSalience(10);

            VariableTreeNode varNode = new VariableTreeNode();
            varNode.setNodeType(TreeNodeType.variable);
            tree.setVariableTreeNode(varNode);

            List<Library> libraries = new ArrayList<>();
            libraries.add(new Library("/path/to/lib1", null, LibraryType.Variable));
            libraries.add(new Library("/path/to/lib2", null, LibraryType.Constant));
            tree.setLibraries(libraries);

            DecisionTreeRulesBuilder builder = new DecisionTreeRulesBuilder();

            // When
            RuleSet ruleSet = builder.buildRules(tree);

            // Then
            assertThat(ruleSet).isNotNull();
            assertThat(ruleSet.getLibraries()).hasSize(2);
        }
    }

    private Value createConstantValue(String value) {
        SimpleValue val = new SimpleValue();
        val.setContent(value);
        return val;
    }
}
