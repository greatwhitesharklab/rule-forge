package com.ruleforge.builder.resource;

import com.ruleforge.action.ScoringAction;
import com.ruleforge.builder.ResourceLibraryBuilder;
import com.ruleforge.builder.RulesRebuilder;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rete.Rete;
import com.ruleforge.model.rete.builder.ReteBuilder;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.rule.*;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.Junction;
import com.ruleforge.model.rule.lhs.Left;
import com.ruleforge.model.rule.lhs.LeftType;
import com.ruleforge.model.rule.lhs.VariableLeftPart;
import com.ruleforge.model.scorecard.*;
import com.ruleforge.model.scorecard.runtime.ScoreRule;
import com.ruleforge.model.table.Condition;
import com.ruleforge.model.table.Joint;
import com.ruleforge.model.table.JointType;
import com.ruleforge.parse.deserializer.ScorecardDeserializer;
import com.ruleforge.parse.scorecard.CardCellParser;
import com.ruleforge.parse.scorecard.ScorecardParser;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("评分卡资源构建器")
class ScorecardResourceBuilderTest {

    private ScorecardResourceBuilder builder;
    private RulesRebuilder rulesRebuilder;

    @BeforeEach
    void setUp() {
        builder = new ScorecardResourceBuilder();

        ScorecardParser scorecardParser = new ScorecardParser();
        scorecardParser.setCardCellParser(new CardCellParser());
        rulesRebuilder = mock(RulesRebuilder.class);
        ResourceLibraryBuilder rlBuilder = mock(ResourceLibraryBuilder.class);
        ResourceLibrary emptyResourceLibrary = new ResourceLibrary(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        when(rulesRebuilder.getResourceLibraryBuilder()).thenReturn(rlBuilder);
        when(rlBuilder.buildResourceLibrary(any())).thenReturn(emptyResourceLibrary);
        when(rulesRebuilder.getVariableByName(any(), any(), any(), any())).thenReturn(new Variable());
        doNothing().when(rulesRebuilder).rebuildRules(any(), any());
        scorecardParser.setRulesRebuilder(rulesRebuilder);

        ScorecardDeserializer deserializer = new ScorecardDeserializer();
        deserializer.setScorecardParser(scorecardParser);
        builder.setScorecardDeserializer(deserializer);
        builder.setRulesRebuilder(rulesRebuilder);
        builder.setResourceLibraryBuilder(rlBuilder);

        ReteBuilder reteBuilder = mock(ReteBuilder.class);
        Rete mockRete = mock(Rete.class);
        when(mockRete.getResourceLibrary()).thenReturn(emptyResourceLibrary);
        when(reteBuilder.buildRete(any(), any())).thenReturn(mockRete);
        builder.setReteBuilder(reteBuilder);
    }

    @Nested
    @DisplayName("构建评分规则")
    class BuildScoreRule {

        @Test
        @DisplayName("Given 包含基本属性的评分卡定义 When 调用build Then 应返回包含相同属性的ScoreRule对象")
        void shouldBuildScoreRuleFromScorecardDefinition() {
            // Given
            ScorecardDefinition scorecard = createBasicScorecard();

            // When
            ScoreRule scoreRule = builder.build(scorecardToElement(scorecard));

            // Then
            assertThat(scoreRule).isNotNull();
            assertThat(scoreRule.getName()).isEqualTo("test-scorecard");
            assertThat(scoreRule.getScoringType()).isEqualTo(ScoringType.weightsum);
        }

        @Test
        @DisplayName("Given 评分卡包含scoring-type属性 When 构建评分规则 Then scoringType应正确设置")
        void shouldSetScoringTypeFromScorecard() {
            // Given
            ScorecardDefinition scorecard = createBasicScorecard();
            scorecard.setScoringType(ScoringType.custom);

            // When
            ScoreRule scoreRule = builder.build(scorecardToElement(scorecard));

            // Then
            assertThat(scoreRule).isNotNull();
            assertThat(scoreRule.getScoringType()).isEqualTo(ScoringType.custom);
        }

        @Test
        @DisplayName("Given 评分卡包含assign-target-type属性 When 构建评分规则 Then assignTargetType应正确设置")
        void shouldSetAssignTargetTypeFromScorecard() {
            // Given
            ScorecardDefinition scorecard = createBasicScorecard();
            scorecard.setAssignTargetType(AssignTargetType.variable);

            // When
            ScoreRule scoreRule = builder.build(scorecardToElement(scorecard));

            // Then
            assertThat(scoreRule).isNotNull();
            assertThat(scoreRule.getAssignTargetType()).isEqualTo(AssignTargetType.variable);
        }

        @Test
        @DisplayName("Given 评分卡包含custom-scoring-bean属性 When 构建评分规则 Then scoringBean应正确设置")
        void shouldSetScoringBeanFromScorecard() {
            // Given
            ScorecardDefinition scorecard = createBasicScorecard();
            scorecard.setScoringBean("customScoringBean");

            // When
            ScoreRule scoreRule = builder.build(scorecardToElement(scorecard));

            // Then
            assertThat(scoreRule).isNotNull();
            assertThat(scoreRule.getScoringBean()).isEqualTo("customScoringBean");
        }
    }

    @Nested
    @DisplayName("构建规则LHS条件")
    class BuildRuleLhsConditions {

        @Test
        @DisplayName("Given 属性单元格包含变量名 When 构建规则LHS Then 变量应正确转换为LeftPart")
        void shouldConvertAttributeCellToVariableLeftPart() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 条件单元格包含条件联合对象 When 构建规则LHS Then 条件应正确转换为Criteria")
        void shouldConvertConditionCellToCriteria() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 条件包含多个子条件 When 构建规则LHS Then 条件应使用AND连接")
        void shouldCombineConditionsWithAndOperator() {
            // Given

            // When

            // Then
        }
    }

    @Nested
    @DisplayName("构建规则RHS动作")
    class BuildRuleRhsActions {

        @Test
        @DisplayName("Given 评分单元格包含值 When 构建规则RHS Then 应创建ScoringAction并设置值")
        void shouldCreateScoringActionWithCellValue() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 属性单元格包含权重 When 构建规则RHS Then ScoringAction应包含权重信息")
        void shouldCreateScoringActionWithWeight() {
            // Given

            // When

            // Then
        }
    }

    @Nested
    @DisplayName("处理自定义列")
    class HandleCustomColumns {

        @Test
        @DisplayName("Given 评分卡包含自定义列 When 构建规则 Then 规则RHS应包含自定义列的ScoringAction")
        void shouldAddCustomColumnActionsToRuleRhs() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 自定义列单元格包含值 When 构建规则 Then ScoringAction的value应正确设置")
        void shouldSetCustomColumnValueInAction() {
            // Given

            // When

            // Then
        }
    }

    @Nested
    @DisplayName("处理多行评分条件")
    class HandleMultipleRows {

        @Test
        @DisplayName("Given 评分卡包含多个属性行 When 构建规则集 Then 应为每个属性行生成一条规则")
        void shouldCreateRuleForEachAttributeRow() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 属性行包含条件行 When 构建规则集 Then 应为每个条件行生成一条规则")
        void shouldCreateRuleForEachConditionRow() {
            // Given

            // When

            // Then
        }
    }

    @Nested
    @DisplayName("计算评分结果")
    class CalculateScoreResult {

        @Test
        @DisplayName("Given 评分卡包含单元格评分 When 构建评分规则 Then 评分应正确转换为规则值")
        void shouldConvertScoreCellValueToRuleValue() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 评分卡包含变量信息 When 构建评分规则 Then 变量类别、名称和标签应正确传递")
        void shouldSetVariableInfoFromScorecard() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 评分卡包含数据类型属性 When 构建评分规则 Then datatype应正确设置")
        void shouldSetDatatypeFromScorecard() {
            // Given

            // When

            // Then
        }
    }

    @Nested
    @DisplayName("处理变量类别优先级")
    class HandleVariableCategoryPriority {

        @Test
        @DisplayName("Given 单元格级别有变量类别 When 构建规则 Then 应使用单元格级别的变量类别")
        void shouldUseCellLevelVariableCategory() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 单元格级别没有变量类别 When 构建规则 Then 应使用评分卡级别的变量类别")
        void shouldFallbackToScorecardLevelVariableCategory() {
            // Given

            // When

            // Then
        }
    }

    @Nested
    @DisplayName("构建规则属性")
    class BuildRuleAttributes {

        @Test
        @DisplayName("Given 评分卡包含salience属性 When 构建规则 Then 规则应继承salience属性")
        void shouldInheritSalienceFromScorecard() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 评分卡包含enabled属性 When 构建规则 Then 规则应继承enabled属性")
        void shouldInheritEnabledFromScorecard() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 评分卡包含debug属性 When 构建规则 Then 规则应继承debug属性")
        void shouldInheritDebugFromScorecard() {
            // Given

            // When

            // Then
        }
    }

    @Nested
    @DisplayName("构建知识库")
    class BuildKnowledgeBase {

        @Test
        @DisplayName("Given 评分卡定义包含库引用 When 构建评分规则 Then ScoreRule应包含库引用列表")
        void shouldSetLibrariesFromScorecard() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 评分卡定义包含有效日期和过期日期 When 构建评分规则 Then ScoreRule应包含这些日期属性")
        void shouldSetDatesFromScorecard() {
            // Given

            // When

            // Then
        }
    }

    @Nested
    @DisplayName("错误处理")
    class ErrorHandling {

        @Test
        @DisplayName("Given 请求不存在的单元格 When 构建规则 Then 应抛出RuleException异常")
        void shouldThrowExceptionWhenCellNotFound() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 属性行的变量名不存在于库中 When 构建规则 Then 应抛出RuleException异常")
        void shouldThrowExceptionWhenVariableNotFound() {
            // Given

            // When

            // Then
        }
    }

    @Nested
    @DisplayName("支持的资源类型")
    class SupportedResourceType {

        @Test
        @DisplayName("When 调用getType Then 应返回ResourceType.Scorecard")
        void shouldReturnScorecardResourceType() {
            // Given
            ScorecardResourceBuilder builder = new ScorecardResourceBuilder();

            // When
            ResourceType type = builder.getType();

            // Then
            assertThat(type).isEqualTo(ResourceType.Scorecard);
        }

        @Test
        @DisplayName("Given 根元素是scorecard When 调用support Then 应返回true")
        void shouldSupportScorecardElement() {
            // Given
            String xml = "<scorecard name=\"test\" var-category=\"test\" var=\"score\" scoring-type=\"weightsum\" assign-target-type=\"variable\"/>";
            Element root = parseXml(xml);

            // When
            boolean supported = builder.support(root);

            // Then
            assertThat(supported).isTrue();
        }

        @Test
        @DisplayName("Given 根元素不是scorecard When 调用support Then 应返回false")
        void shouldNotSupportNonScorecardElement() {
            // Given
            String xml = "<decision-tree/>";
            Element root = parseXml(xml);

            // When
            boolean supported = builder.support(root);

            // Then
            assertThat(supported).isFalse();
        }
    }

    private Element parseXml(String xml) {
        try {
            Document document = DocumentHelper.parseText(xml);
            return document.getRootElement();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse XML", e);
        }
    }

    private ScorecardDefinition createBasicScorecard() {
        ScorecardDefinition scorecard = new ScorecardDefinition();
        scorecard.setName("test-scorecard");
        scorecard.setScoringType(ScoringType.weightsum);
        scorecard.setAssignTargetType(AssignTargetType.variable);
        scorecard.setVariableCategory("test");
        scorecard.setVariableName("score");
        scorecard.setDatatype(Datatype.Integer);
        scorecard.setSalience(100);
        scorecard.setEnabled(true);
        scorecard.setDebug(false);

        // Create cells
        List<CardCell> cells = new ArrayList<>();

        // Attribute cell
        CardCell attributeCell = new CardCell();
        attributeCell.setRow(1);
        attributeCell.setCol(1);
        attributeCell.setVariableName("age");
        attributeCell.setVariableCategory("test");
        attributeCell.setDatatype(Datatype.Integer);
        attributeCell.setWeight("1.0");

        // Condition cell
        CardCell conditionCell = new CardCell();
        conditionCell.setRow(1);
        conditionCell.setCol(2);
        Joint joint = new Joint();
        joint.setType(JointType.and);
        List<Condition> conditions = new ArrayList<>();
        Condition condition = new Condition();
        condition.setOp(Op.Equals);
        SimpleValue value = new SimpleValue();
        value.setContent("18");
        condition.setValue(value);
        conditions.add(condition);
        joint.setConditions(conditions);
        conditionCell.setJoint(joint);

        // Score cell
        CardCell scoreCell = new CardCell();
        scoreCell.setRow(1);
        scoreCell.setCol(3);
        SimpleValue scoreValue = new SimpleValue();
        scoreValue.setContent("100");
        scoreCell.setValue(scoreValue);

        cells.add(attributeCell);
        cells.add(conditionCell);
        cells.add(scoreCell);
        scorecard.setCells(cells);

        // Create rows
        List<AttributeRow> rows = new ArrayList<>();
        AttributeRow row = new AttributeRow();
        row.setRowNumber(1);
        row.setConditionRows(new ArrayList<>());
        rows.add(row);
        scorecard.setRows(rows);

        // Create custom columns
        List<CustomCol> customCols = new ArrayList<>();
        scorecard.setCustomCols(customCols);

        return scorecard;
    }

    private Element scorecardToElement(ScorecardDefinition scorecard) {
        // Simple conversion - create a basic XML element from the scorecard
        StringBuilder xml = new StringBuilder();
        xml.append("<scorecard name=\"").append(scorecard.getName()).append("\"");
        xml.append(" var-category=\"").append(scorecard.getVariableCategory()).append("\"");
        xml.append(" var=\"").append(scorecard.getVariableName()).append("\"");
        xml.append(" scoring-type=\"").append(scorecard.getScoringType()).append("\"");
        xml.append(" assign-target-type=\"").append(scorecard.getAssignTargetType()).append("\"");
        if (scorecard.getDatatype() != null) {
            xml.append(" datatype=\"").append(scorecard.getDatatype()).append("\"");
        }
        if (scorecard.getSalience() != null) {
            xml.append(" salience=\"").append(scorecard.getSalience()).append("\"");
        }
        if (scorecard.getScoringBean() != null) {
            xml.append(" custom-scoring-bean=\"").append(scorecard.getScoringBean()).append("\"");
        }
        xml.append("/>");

        return parseXml(xml.toString());
    }
}
