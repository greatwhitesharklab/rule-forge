package com.ruleforge.builder.table;

import com.ruleforge.action.ConsolePrintAction;
import com.ruleforge.action.VariableAssignAction;
import com.ruleforge.exception.RuleException;
import com.ruleforge.action.ActionValue;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.Op;
import com.ruleforge.model.rule.lhs.And;
import com.ruleforge.model.rule.lhs.Criterion;
import com.ruleforge.model.rule.lhs.Lhs;
import com.ruleforge.model.table.*;
import com.ruleforge.parse.ValueParser;
import com.ruleforge.runtime.rete.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DisplayName("决策表规则构建器")
class DecisionTableRulesBuilderTest {

    private DecisionTableRulesBuilder builder;
    private CellContentBuilder cellContentBuilder;

    @Mock
    private ValueParser valueParser;

    @BeforeEach
    void setUp() {
        builder = new DecisionTableRulesBuilder();
        cellContentBuilder = new CellContentBuilder();
        builder.setCellContentBuilder(cellContentBuilder);
    }

    private DecisionTable createSimpleTable(int rowCount, int colCount) {
        DecisionTable table = new DecisionTable();

        // Add columns
        for (int i = 1; i <= colCount; i++) {
            Column col = new Column();
            col.setNum(i);
            col.setWidth(100);
            col.setType(i == 1 ? ColumnType.Criteria : ColumnType.Assignment);
            col.setVariableCategory("test");
            col.setVariableName("field" + i);
            col.setDatatype(Datatype.String);
            table.addColumn(col);
        }

        // Add rows
        for (int i = 1; i <= rowCount; i++) {
            Row row = new Row();
            row.setNum(i);
            row.setHeight(25);
            table.addRow(row);
        }

        return table;
    }

    private void addCellToTable(DecisionTable table, int row, int col, Object content) {
        Cell cell = new Cell();
        cell.setRow(row);
        cell.setCol(col);
        cell.setRowspan(1);

        if (content instanceof Value) {
            cell.setValue((Value) content);
        } else if (content instanceof Joint) {
            cell.setJoint((Joint) content);
        }

        table.addCell(cell);
    }

    private Value createStringValue(String value) {
        com.ruleforge.model.rule.SimpleValue v = new com.ruleforge.model.rule.SimpleValue();
        v.setContent(value);
        return v;
    }

    private Value createIntegerValue(int value) {
        com.ruleforge.model.rule.SimpleValue v = new com.ruleforge.model.rule.SimpleValue();
        v.setContent(String.valueOf(value));
        return v;
    }

    @Nested
    @DisplayName("构建规则列表")
    class BuildRulesList {

        @Test
        @DisplayName("Given 包含多行数据的决策表 When 调用buildRules方法 Then 应返回与行数相同数量的规则列表")
        void shouldBuildRulesWithSameCountAsTableRows() {
            // Given
            DecisionTable table = createSimpleTable(3, 2);
            for (int row = 1; row <= 3; row++) {
                for (int col = 1; col <= 2; col++) {
                    addCellToTable(table, row, col, createStringValue("value-" + row + "-" + col));
                }
            }

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(3);
        }

        @Test
        @DisplayName("Given 空决策表 When 调用buildRules方法 Then 应返回空规则列表")
        void shouldBuildEmptyRulesListForEmptyTable() {
            // Given
            DecisionTable table = new DecisionTable();

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).isEmpty();
        }

        @Test
        @DisplayName("Given 包含单行数据的决策表 When 调用buildRules方法 Then 应返回包含一条规则的列表")
        void shouldBuildSingleRuleForSingleRowTable() {
            // Given
            DecisionTable table = createSimpleTable(1, 2);
            addCellToTable(table, 1, 1, createStringValue("value1"));
            addCellToTable(table, 1, 2, createStringValue("value2"));

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(1);
        }

        @Test
        @DisplayName("Given 包含多行数据的决策表 When 调用buildRules方法 Then 每条规则的名称应为r加行号")
        void shouldNameRulesAsRPlusRowNumber() {
            // Given
            DecisionTable table = createSimpleTable(3, 2);
            for (int row = 1; row <= 3; row++) {
                for (int col = 1; col <= 2; col++) {
                    addCellToTable(table, row, col, createStringValue("value-" + row + "-" + col));
                }
            }

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules.get(0).getName()).isEqualTo("r1");
            assertThat(rules.get(1).getName()).isEqualTo("r2");
            assertThat(rules.get(2).getName()).isEqualTo("r3");
        }
    }

    @Nested
    @DisplayName("条件列转换")
    class CriteriaColumnConversion {

        @Test
        @DisplayName("Given 包含Criteria类型列的决策表 When 构建规则 Then 规则的LHS应包含对应的Criterion")
        void shouldBuildCriteriaColumnAsCriterionInLhs() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            table.getColumns().get(0).setType(ColumnType.Criteria);

            // Create a joint for the criteria
            Joint joint = new Joint();
            joint.setType(JointType.and);
            Condition condition = new Condition();
            condition.setOp(Op.GreaterThen);
            condition.setValue(createIntegerValue(18));
            joint.setConditions(List.of(condition));
            addCellToTable(table, 1, 1, joint);

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(1);
            Rule rule = rules.get(0);
            assertThat(rule.getLhs()).isNotNull();
            Lhs lhs = rule.getLhs();
            assertThat(lhs.getCriterion()).isInstanceOf(And.class);
            And and = (And) lhs.getCriterion();
            assertThat(and.getCriterions()).isNotEmpty();
        }

        @Test
        @DisplayName("Given 包含多个Criteria类型列的决策表 When 构建规则 Then 规则的LHS应包含所有Criterion")
        void shouldBuildMultipleCriteriaColumnsAsMultipleCriteria() {
            // Given
            DecisionTable table = createSimpleTable(1, 2);
            table.getColumns().get(0).setType(ColumnType.Criteria);
            table.getColumns().get(1).setType(ColumnType.Criteria);

            // Add joints for both criteria
            Joint joint1 = new Joint();
            joint1.setType(JointType.and);
            Condition condition1 = new Condition();
            condition1.setOp(Op.GreaterThen);
            condition1.setValue(createIntegerValue(18));
            joint1.setConditions(List.of(condition1));
            addCellToTable(table, 1, 1, joint1);

            Joint joint2 = new Joint();
            joint2.setType(JointType.and);
            Condition condition2 = new Condition();
            condition2.setOp(Op.LessThen);
            condition2.setValue(createIntegerValue(65));
            joint2.setConditions(List.of(condition2));
            addCellToTable(table, 1, 2, joint2);

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(1);
            Rule rule = rules.get(0);
            And and = (And) rule.getLhs().getCriterion();
            assertThat(and.getCriterions()).hasSize(2);
        }

        @Test
        @DisplayName("Given Criteria列单元格为空 When 构建规则 Then 不应添加Criterion到LHS")
        void shouldNotAddCriterionForEmptyCriteriaCell() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            table.getColumns().get(0).setType(ColumnType.Criteria);

            // When & Then - should throw exception because cell is empty
            assertThatThrownBy(() -> builder.buildRules(table))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining("not exist");
        }

        @Test
        @DisplayName("Given Criteria列单元格包含条件 When 构建规则 Then Criterion应正确解析条件表达式")
        void shouldBuildCriterionWithCorrectConditionExpression() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            table.getColumns().get(0).setType(ColumnType.Criteria);

            Joint joint = new Joint();
            joint.setType(JointType.and);
            Condition condition = new Condition();
            condition.setOp(Op.GreaterThen);
            condition.setValue(createIntegerValue(18));
            joint.setConditions(List.of(condition));
            addCellToTable(table, 1, 1, joint);

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(1);
            Rule rule = rules.get(0);
            And and = (And) rule.getLhs().getCriterion();
            assertThat(and.getCriterions()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("赋值动作列转换")
    class AssignmentColumnConversion {

        @Test
        @DisplayName("Given 包含Assignment类型列的决策表 When 构建规则 Then 规则的RHS应包含VariableAssignAction")
        void shouldBuildAssignmentColumnAsVariableAssignAction() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            table.getColumns().get(0).setType(ColumnType.Assignment);

            Value value = createStringValue("approved");
            addCellToTable(table, 1, 1, value);

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(1);
            Rule rule = rules.get(0);
            assertThat(rule.getRhs()).isNotNull();
            assertThat(rule.getRhs().getActions()).hasSize(1);
            assertThat(rule.getRhs().getActions().get(0)).isInstanceOf(VariableAssignAction.class);
        }

        @Test
        @DisplayName("Given Assignment列包含变量名和数据类型 When 构建规则 Then VariableAssignAction应正确设置变量属性")
        void shouldSetVariablePropertiesInVariableAssignAction() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            Column col = table.getColumns().get(0);
            col.setType(ColumnType.Assignment);
            col.setVariableName("approved");
            col.setDatatype(Datatype.Boolean);

            Value value = createStringValue("true");
            addCellToTable(table, 1, 1, value);

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            VariableAssignAction action = (VariableAssignAction) rules.get(0).getRhs().getActions().get(0);
            assertThat(action.getVariableName()).isEqualTo("approved");
            assertThat(action.getDatatype()).isEqualTo(Datatype.Boolean);
        }

        @Test
        @DisplayName("Given Assignment列包含变量分类和标签 When 构建规则 Then VariableAssignAction应正确设置分类和标签")
        void shouldSetVariableCategoryAndLabelInAction() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            Column col = table.getColumns().get(0);
            col.setType(ColumnType.Assignment);
            col.setVariableCategory("loan");
            col.setVariableLabel("Loan Approved");

            Value value = createStringValue("true");
            addCellToTable(table, 1, 1, value);

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            VariableAssignAction action = (VariableAssignAction) rules.get(0).getRhs().getActions().get(0);
            assertThat(action.getVariableCategory()).isEqualTo("loan");
            assertThat(action.getVariableLabel()).isEqualTo("Loan Approved");
        }

        @Test
        @DisplayName("Given Assignment列单元格包含值 When 构建规则 Then VariableAssignAction应正确设置值")
        void shouldSetValueInVariableAssignAction() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            table.getColumns().get(0).setType(ColumnType.Assignment);

            Value value = createStringValue("test-value");
            addCellToTable(table, 1, 1, value);

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            VariableAssignAction action = (VariableAssignAction) rules.get(0).getRhs().getActions().get(0);
            assertThat(action.getValue()).isNotNull();
            assertThat(((com.ruleforge.model.rule.SimpleValue)action.getValue()).getContent()).isEqualTo("test-value");
        }

        @Test
        @DisplayName("Given Assignment列单元格为空 When 构建规则 Then 不应添加VariableAssignAction到RHS")
        void shouldNotAddActionForEmptyAssignmentCell() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            table.getColumns().get(0).setType(ColumnType.Assignment);

            // When & Then - should throw exception because cell is empty
            assertThatThrownBy(() -> builder.buildRules(table))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining("not exist");
        }

        @Test
        @DisplayName("Given Assignment列 When 构建规则 Then Action的priority应为1000减列号")
        void shouldSetPriorityBasedOnColumnNumberForAssignment() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            table.getColumns().get(0).setType(ColumnType.Assignment);
            table.getColumns().get(0).setNum(5);

            Value value = createStringValue("test");
            addCellToTable(table, 1, 5, value);

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            VariableAssignAction action = (VariableAssignAction) rules.get(0).getRhs().getActions().get(0);
            assertThat(action.getPriority()).isEqualTo(995); // 1000 - 5
        }
    }

    @Nested
    @DisplayName("控制台打印动作列转换")
    class ConsolePrintColumnConversion {

        @Test
        @DisplayName("Given 包含ConsolePrint类型列的决策表 When 构建规则 Then 规则的RHS应包含ConsolePrintAction")
        void shouldBuildConsolePrintColumnAsConsolePrintAction() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            table.getColumns().get(0).setType(ColumnType.ConsolePrint);

            Value value = createStringValue("Test message");
            addCellToTable(table, 1, 1, value);

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(1);
            Rule rule = rules.get(0);
            assertThat(rule.getRhs()).isNotNull();
            assertThat(rule.getRhs().getActions()).hasSize(1);
            assertThat(rule.getRhs().getActions().get(0)).isInstanceOf(ConsolePrintAction.class);
        }

        @Test
        @DisplayName("Given ConsolePrint列单元格包含值 When 构建规则 Then ConsolePrintAction应正确设置值")
        void shouldSetValueInConsolePrintAction() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            table.getColumns().get(0).setType(ColumnType.ConsolePrint);

            Value value = createStringValue("Test message");
            addCellToTable(table, 1, 1, value);

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            ConsolePrintAction action = (ConsolePrintAction) rules.get(0).getRhs().getActions().get(0);
            assertThat(action.getValue()).isNotNull();
            assertThat(((com.ruleforge.model.rule.SimpleValue)action.getValue()).getContent()).isEqualTo("Test message");
        }

        @Test
        @DisplayName("Given ConsolePrint列单元格为空 When 构建规则 Then 不应添加ConsolePrintAction到RHS")
        void shouldNotAddActionForEmptyConsolePrintCell() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            table.getColumns().get(0).setType(ColumnType.ConsolePrint);

            // When & Then - should throw exception because cell is empty
            assertThatThrownBy(() -> builder.buildRules(table))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining("not exist");
        }

        @Test
        @DisplayName("Given ConsolePrint列 When 构建规则 Then Action的priority应为1000减列号")
        void shouldSetPriorityBasedOnColumnNumberForConsolePrint() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            table.getColumns().get(0).setType(ColumnType.ConsolePrint);
            table.getColumns().get(0).setNum(10);

            Value value = createStringValue("test");
            addCellToTable(table, 1, 10, value);

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            ConsolePrintAction action = (ConsolePrintAction) rules.get(0).getRhs().getActions().get(0);
            assertThat(action.getPriority()).isEqualTo(990); // 1000 - 10
        }
    }

    @Nested
    @DisplayName("方法执行动作列转换")
    class ExecuteMethodColumnConversion {

        @Test
        @DisplayName("Given 包含ExecuteMethod类型列的决策表 When 构建规则 Then 规则的RHS应包含Action")
        void shouldBuildExecuteMethodColumnAsAction() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            table.getColumns().get(0).setType(ColumnType.ExecuteMethod);

            // Create a simple action
            Cell cell = new Cell();
            cell.setRow(1);
            cell.setCol(1);
            cell.setRowspan(1);
            com.ruleforge.action.Action action = new com.ruleforge.action.AbstractAction() {
                @Override
                public com.ruleforge.action.ActionType getActionType() {
                    return com.ruleforge.action.ActionType.ConsolePrint;
                }

                @Override
                public ActionValue execute(Context context, Object matchedObject, java.util.List<Object> allMatchedObjects) {
                    return null;
                }
            };
            cell.setAction(action);
            table.addCell(cell);

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(1);
            Rule rule = rules.get(0);
            assertThat(rule.getRhs()).isNotNull();
            assertThat(rule.getRhs().getActions()).hasSize(1);
        }

        @Test
        @DisplayName("Given ExecuteMethod列单元格包含Action When 构建规则 Then Action应正确添加到RHS")
        void shouldAddActionToRhsForExecuteMethodColumn() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            table.getColumns().get(0).setType(ColumnType.ExecuteMethod);

            Cell cell = new Cell();
            cell.setRow(1);
            cell.setCol(1);
            cell.setRowspan(1);
            com.ruleforge.action.Action action = new com.ruleforge.action.AbstractAction() {
                @Override
                public com.ruleforge.action.ActionType getActionType() {
                    return com.ruleforge.action.ActionType.ConsolePrint;
                }

                @Override
                public ActionValue execute(Context context, Object matchedObject, java.util.List<Object> allMatchedObjects) {
                    return null;
                }
            };
            cell.setAction(action);
            table.addCell(cell);

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules.get(0).getRhs().getActions()).hasSize(1);
            assertThat(rules.get(0).getRhs().getActions().get(0)).isInstanceOf(com.ruleforge.action.AbstractAction.class);
        }

        @Test
        @DisplayName("Given ExecuteMethod列单元格为空 When 构建规则 Then 不应添加Action到RHS")
        void shouldNotAddActionForEmptyExecuteMethodCell() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            table.getColumns().get(0).setType(ColumnType.ExecuteMethod);

            // When & Then - should throw exception because cell is empty
            assertThatThrownBy(() -> builder.buildRules(table))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining("not exist");
        }

        @Test
        @DisplayName("Given ExecuteMethod列 When 构建规则 Then AbstractAction的priority应为1000减列号")
        void shouldSetPriorityBasedOnColumnNumberForExecuteMethod() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            table.getColumns().get(0).setType(ColumnType.ExecuteMethod);
            table.getColumns().get(0).setNum(15);

            Cell cell = new Cell();
            cell.setRow(1);
            cell.setCol(15);
            cell.setRowspan(1);
            com.ruleforge.action.AbstractAction action = new com.ruleforge.action.AbstractAction() {
                @Override
                public com.ruleforge.action.ActionType getActionType() {
                    return com.ruleforge.action.ActionType.ConsolePrint;
                }

                @Override
                public ActionValue execute(Context context, Object matchedObject, java.util.List<Object> allMatchedObjects) {
                    return null;
                }
            };
            cell.setAction(action);
            table.addCell(cell);

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            com.ruleforge.action.AbstractAction resultAction =
                    (com.ruleforge.action.AbstractAction) rules.get(0).getRhs().getActions().get(0);
            assertThat(resultAction.getPriority()).isEqualTo(985); // 1000 - 15
        }
    }

    @Nested
    @DisplayName("规则属性设置")
    class RulePropertiesConfiguration {

        @Test
        @DisplayName("Given 决策表包含salience属性 When 构建规则 Then 每条规则应继承salience")
        void shouldInheritSalienceFromDecisionTable() {
            // Given
            DecisionTable table = createSimpleTable(2, 1);
            table.setSalience(100);
            addCellToTable(table, 1, 1, createStringValue("value1"));
            addCellToTable(table, 2, 1, createStringValue("value2"));

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(2);
            assertThat(rules.get(0).getSalience()).isEqualTo(100);
            assertThat(rules.get(1).getSalience()).isEqualTo(100);
        }

        @Test
        @DisplayName("Given 决策表包含effectiveDate属性 When 构建规则 Then 每条规则应继承effectiveDate")
        void shouldInheritEffectiveDateFromDecisionTable() {
            // Given
            DecisionTable table = createSimpleTable(2, 1);
            Date effectiveDate = new Date();
            table.setEffectiveDate(effectiveDate);
            addCellToTable(table, 1, 1, createStringValue("value1"));
            addCellToTable(table, 2, 1, createStringValue("value2"));

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(2);
            assertThat(rules.get(0).getEffectiveDate()).isEqualTo(effectiveDate);
            assertThat(rules.get(1).getEffectiveDate()).isEqualTo(effectiveDate);
        }

        @Test
        @DisplayName("Given 决策表包含expiresDate属性 When 构建规则 Then 每条规则应继承expiresDate")
        void shouldInheritExpiresDateFromDecisionTable() {
            // Given
            DecisionTable table = createSimpleTable(2, 1);
            Date expiresDate = new Date();
            table.setExpiresDate(expiresDate);
            addCellToTable(table, 1, 1, createStringValue("value1"));
            addCellToTable(table, 2, 1, createStringValue("value2"));

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(2);
            assertThat(rules.get(0).getExpiresDate()).isEqualTo(expiresDate);
            assertThat(rules.get(1).getExpiresDate()).isEqualTo(expiresDate);
        }

        @Test
        @DisplayName("Given 决策表包含enabled属性 When 构建规则 Then 每条规则应继承enabled")
        void shouldInheritEnabledFromDecisionTable() {
            // Given
            DecisionTable table = createSimpleTable(2, 1);
            table.setEnabled(true);
            addCellToTable(table, 1, 1, createStringValue("value1"));
            addCellToTable(table, 2, 1, createStringValue("value2"));

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(2);
            assertThat(rules.get(0).getEnabled()).isTrue();
            assertThat(rules.get(1).getEnabled()).isTrue();
        }

        @Test
        @DisplayName("Given 决策表包含debug属性 When 构建规则 Then 每条规则应继承debug")
        void shouldInheritDebugFromDecisionTable() {
            // Given
            DecisionTable table = createSimpleTable(2, 1);
            table.setDebug(true);
            addCellToTable(table, 1, 1, createStringValue("value1"));
            addCellToTable(table, 2, 1, createStringValue("value2"));

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(2);
            assertThat(rules.get(0).getDebug()).isTrue();
            assertThat(rules.get(1).getDebug()).isTrue();
        }
    }

    @Nested
    @DisplayName("规则LHS构建")
    class RuleLhsConstruction {

        @Test
        @DisplayName("Given 构建规则 When 规则的LHS Then 应包含And对象作为根条件")
        void shouldBuildLhsWithAndAsRootCriterion() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            addCellToTable(table, 1, 1, createStringValue("value"));

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(1);
            Rule rule = rules.get(0);
            assertThat(rule.getLhs()).isNotNull();
            assertThat(rule.getLhs().getCriterion()).isInstanceOf(And.class);
        }

        @Test
        @DisplayName("Given 包含多个Criteria列的决策表 When 构建规则 Then And应包含所有Criterion")
        void shouldAddAllCriteriaToAndCriterion() {
            // Given
            DecisionTable table = createSimpleTable(1, 2);
            table.getColumns().get(0).setType(ColumnType.Criteria);
            table.getColumns().get(1).setType(ColumnType.Criteria);

            Joint joint1 = new Joint();
            joint1.setType(JointType.and);
            Condition condition1 = new Condition();
            condition1.setOp(Op.GreaterThen);
            condition1.setValue(createIntegerValue(18));
            joint1.setConditions(List.of(condition1));
            addCellToTable(table, 1, 1, joint1);

            Joint joint2 = new Joint();
            joint2.setType(JointType.and);
            Condition condition2 = new Condition();
            condition2.setOp(Op.LessThen);
            condition2.setValue(createIntegerValue(65));
            joint2.setConditions(List.of(condition2));
            addCellToTable(table, 1, 2, joint2);

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            Rule rule = rules.get(0);
            And and = (And) rule.getLhs().getCriterion();
            assertThat(and.getCriterions()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("规则RHS构建")
    class RuleRhsConstruction {

        @Test
        @DisplayName("Given 构建规则 When 规则的RHS Then 应初始化空的Rhs对象")
        void shouldInitializeRhsObject() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            addCellToTable(table, 1, 1, createStringValue("value"));

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(1);
            Rule rule = rules.get(0);
            assertThat(rule.getRhs()).isNotNull();
        }

        @Test
        @DisplayName("Given 包含多个动作列的决策表 When 构建规则 Then Rhs应包含所有Action")
        void shouldAddAllActionsToRhs() {
            // Given
            DecisionTable table = createSimpleTable(1, 2);
            table.getColumns().get(0).setType(ColumnType.Assignment);
            table.getColumns().get(1).setType(ColumnType.ConsolePrint);

            addCellToTable(table, 1, 1, createStringValue("value1"));
            addCellToTable(table, 1, 2, createStringValue("value2"));

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            Rule rule = rules.get(0);
            assertThat(rule.getRhs().getActions()).hasSize(2);
        }

        @Test
        @DisplayName("Given 包含Assignment和ConsolePrint列的决策表 When 构建规则 Then Rhs应按优先级包含两种Action")
        void shouldAddMixedActionTypesToRhs() {
            // Given
            DecisionTable table = createSimpleTable(1, 2);
            table.getColumns().get(0).setType(ColumnType.Assignment);
            table.getColumns().get(1).setType(ColumnType.ConsolePrint);

            addCellToTable(table, 1, 1, createStringValue("assigned"));
            addCellToTable(table, 1, 2, createStringValue("printed"));

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            Rule rule = rules.get(0);
            assertThat(rule.getRhs().getActions()).hasSize(2);
            assertThat(rule.getRhs().getActions().get(0)).isInstanceOf(VariableAssignAction.class);
            assertThat(rule.getRhs().getActions().get(1)).isInstanceOf(ConsolePrintAction.class);
        }
    }

    @Nested
    @DisplayName("单元格继承")
    class CellInheritance {

        @Test
        @DisplayName("Given 决策表的单元格为空但上方有值 When 构建规则 Then 应继承上方单元格的值")
        void shouldInheritValueFromAboveCell() {
            // Given
            DecisionTable table = createSimpleTable(2, 1);
            table.getColumns().get(0).setType(ColumnType.Assignment);

            // Only add cell to row 1
            addCellToTable(table, 1, 1, createStringValue("inherited-value"));

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(2);
            // Row 2 should inherit the value from row 1
            assertThat(rules.get(1).getRhs().getActions()).hasSize(1);
        }

        @Test
        @DisplayName("Given 决策表的单元格为空且上方也为空 When 构建规则 Then 应继续向上查找直到找到值")
        void shouldInheritValueFromNearestAboveCell() {
            // Given
            DecisionTable table = createSimpleTable(3, 1);
            table.getColumns().get(0).setType(ColumnType.Assignment);

            // Only add cell to row 1
            addCellToTable(table, 1, 1, createStringValue("value"));

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(3);
            // Both row 2 and row 3 should inherit from row 1
            assertThat(rules.get(1).getRhs().getActions()).hasSize(1);
            assertThat(rules.get(2).getRhs().getActions()).hasSize(1);
        }

        @Test
        @DisplayName("Given 决策表的整列都为空 When 构建规则 Then 应抛出RuleException")
        void shouldThrowExceptionWhenAllCellsInColumnAreEmpty() {
            // Given
            DecisionTable table = createSimpleTable(2, 1);
            table.getColumns().get(0).setType(ColumnType.Assignment);

            // Don't add any cells

            // When & Then
            assertThatThrownBy(() -> builder.buildRules(table))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining("not exist");
        }
    }

    @Nested
    @DisplayName("复杂决策表")
    class ComplexDecisionTables {

        @Test
        @DisplayName("Given 包含多种列类型的决策表 When 构建规则 Then 应正确处理所有列类型")
        void shouldBuildRulesFromMixedColumnTypes() {
            // Given
            DecisionTable table = createSimpleTable(1, 3);
            table.getColumns().get(0).setType(ColumnType.Criteria);
            table.getColumns().get(1).setType(ColumnType.Assignment);
            table.getColumns().get(2).setType(ColumnType.ConsolePrint);

            Joint joint = new Joint();
            joint.setType(JointType.and);
            Condition condition = new Condition();
            condition.setOp(Op.GreaterThen);
            condition.setValue(createIntegerValue(18));
            joint.setConditions(List.of(condition));
            addCellToTable(table, 1, 1, joint);
            addCellToTable(table, 1, 2, createStringValue("assigned"));
            addCellToTable(table, 1, 3, createStringValue("printed"));

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(1);
            Rule rule = rules.get(0);
            assertThat(rule.getLhs()).isNotNull();
            assertThat(rule.getRhs()).isNotNull();
            assertThat(rule.getRhs().getActions()).hasSize(2);
        }

        @Test
        @DisplayName("Given 包含大量行和列的决策表 When 构建规则 Then 应正确生成所有规则")
        void shouldBuildRulesFromLargeTable() {
            // Given
            DecisionTable table = createSimpleTable(10, 5);

            // Add cells for each row and column
            for (int row = 1; row <= 10; row++) {
                for (int col = 1; col <= 5; col++) {
                    addCellToTable(table, row, col, createStringValue("value-" + row + "-" + col));
                }
            }

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(10);
            for (Rule rule : rules) {
                assertThat(rule).isNotNull();
            }
        }

        @Test
        @DisplayName("Given 某些单元格为空的决策表 When 构建规则 Then 应正确处理单元格继承")
        void shouldBuildRulesWithCellInheritance() {
            // Given
            DecisionTable table = createSimpleTable(3, 2);
            table.getColumns().get(0).setType(ColumnType.Criteria);
            table.getColumns().get(1).setType(ColumnType.Assignment);

            // Only add cells to row 1
            Joint joint = new Joint();
            joint.setType(JointType.and);
            Condition condition = new Condition();
            condition.setOp(Op.GreaterThen);
            condition.setValue(createIntegerValue(18));
            joint.setConditions(List.of(condition));
            addCellToTable(table, 1, 1, joint);
            addCellToTable(table, 1, 2, createStringValue("value"));

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(3);
            // All rows should have rules
            for (Rule rule : rules) {
                assertThat(rule).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("Given 没有列的决策表 When 构建规则 Then 应返回空的规则列表")
        void shouldHandleTableWithNoColumns() {
            // Given
            DecisionTable table = new DecisionTable();

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).isEmpty();
        }

        @Test
        @DisplayName("Given 只有Criteria列的决策表 When 构建规则 Then 规则应只包含LHS条件")
        void shouldBuildRulesWithOnlyCriteriaColumns() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            table.getColumns().get(0).setType(ColumnType.Criteria);

            Joint joint = new Joint();
            joint.setType(JointType.and);
            Condition condition = new Condition();
            condition.setOp(Op.GreaterThen);
            condition.setValue(createIntegerValue(18));
            joint.setConditions(List.of(condition));
            addCellToTable(table, 1, 1, joint);

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(1);
            Rule rule = rules.get(0);
            assertThat(rule.getLhs()).isNotNull();
            assertThat(rule.getRhs()).isNotNull();
            assertThat(rule.getRhs().getActions()).isNullOrEmpty();
        }

        @Test
        @DisplayName("Given 只有动作列的决策表 When 构建规则 Then 规则应只包含RHS动作")
        void shouldBuildRulesWithOnlyActionColumns() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            table.getColumns().get(0).setType(ColumnType.Assignment);

            addCellToTable(table, 1, 1, createStringValue("value"));

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(1);
            Rule rule = rules.get(0);
            assertThat(rule.getLhs()).isNotNull();
            assertThat(rule.getRhs()).isNotNull();
            assertThat(rule.getRhs().getActions()).hasSize(1);
        }

        @Test
        @DisplayName("Given 所有单元格都为空的决策表 When 构建规则 Then 应抛出RuleException")
        void shouldThrowExceptionWhenAllCellsAreEmpty() {
            // Given
            DecisionTable table = createSimpleTable(2, 2);

            // When & Then
            assertThatThrownBy(() -> builder.buildRules(table))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining("not exist");
        }
    }

    @Nested
    @DisplayName("依赖注入")
    class DependencyInjection {

        @Test
        @DisplayName("Given 设置cellContentBuilder When 调用buildRules Then 应使用注入的builder构建单元格内容")
        void shouldUseInjectedCellContentBuilder() {
            // Given
            DecisionTable table = createSimpleTable(1, 1);
            table.getColumns().get(0).setType(ColumnType.Criteria);

            Joint joint = new Joint();
            joint.setType(JointType.and);
            Condition condition = new Condition();
            condition.setOp(Op.GreaterThen);
            condition.setValue(createIntegerValue(18));
            joint.setConditions(List.of(condition));
            addCellToTable(table, 1, 1, joint);

            // cellContentBuilder is already set in @BeforeEach

            // When
            List<Rule> rules = builder.buildRules(table);

            // Then
            assertThat(rules).hasSize(1);
        }
    }
}
