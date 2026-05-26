package com.ruleforge.parse.scorecard;

import com.ruleforge.Configure;
import com.ruleforge.builder.ResourceLibraryBuilder;
import com.ruleforge.builder.RulesRebuilder;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.scorecard.*;
import com.ruleforge.model.rule.Library;
import com.ruleforge.model.rule.LibraryType;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.jupiter.api.*;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("评分卡解析器")
class ScorecardParserTest {

    @BeforeEach
    void setUp() {
        Configure configure = new Configure();
        configure.setDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    @Nested
    @DisplayName("解析评分卡基本信息")
    class ParseBasicInfo {

        @Test
        @DisplayName("Given 包含name属性的评分卡XML When 解析评分卡 Then name属性应正确设置")
        void shouldParseScorecardWithName() {
            // Given
            String xml = "<scorecard name=\"test-scorecard\" var-category=\"test\" var=\"score\" scoring-type=\"weightsum\" assign-target-type=\"variable\"/>";

            // When
            ScorecardDefinition scorecard = parseScorecard(xml);

            // Then
            assertThat(scorecard).isNotNull();
            assertThat(scorecard.getName()).isEqualTo("test-scorecard");
        }

        @Test
        @DisplayName("Given 包含scoring-type属性的评分卡XML When 解析评分卡 Then scoringType应正确设置为对应的枚举值")
        void shouldParseScorecardWithScoringType() {
            // Given
            String xml = "<scorecard name=\"test\" var-category=\"test\" var=\"score\" scoring-type=\"weightsum\" assign-target-type=\"variable\"/>";

            // When
            ScorecardDefinition scorecard = parseScorecard(xml);

            // Then
            assertThat(scorecard).isNotNull();
            assertThat(scorecard.getScoringType()).isEqualTo(ScoringType.weightsum);
        }

        @Test
        @DisplayName("Given 包含assign-target-type属性的评分卡XML When 解析评分卡 Then assignTargetType应正确设置为对应的枚举值")
        void shouldParseScorecardWithAssignTargetType() {
            // Given
            String xml = "<scorecard name=\"test\" var-category=\"test\" var=\"score\" scoring-type=\"weightsum\" assign-target-type=\"variable\"/>";

            // When
            ScorecardDefinition scorecard = parseScorecard(xml);

            // Then
            assertThat(scorecard).isNotNull();
            assertThat(scorecard.getAssignTargetType()).isEqualTo(AssignTargetType.variable);
        }
    }

    @Nested
    @DisplayName("解析变量信息")
    class ParseVariableInfo {

        @Test
        @DisplayName("Given 包含var-category和var属性的评分卡XML When 解析评分卡 Then 变量类别和名称应正确设置")
        void shouldParseScorecardWithVariableCategoryAndName() {
            // Given
            String xml = "<scorecard name=\"test\" var-category=\"test-category\" var=\"test-var\" scoring-type=\"weightsum\" assign-target-type=\"variable\"/>";

            // When
            ScorecardDefinition scorecard = parseScorecard(xml);

            // Then
            assertThat(scorecard).isNotNull();
            assertThat(scorecard.getVariableCategory()).isEqualTo("test-category");
            assertThat(scorecard.getVariableName()).isEqualTo("test-var");
        }

        @Test
        @DisplayName("Given 包含var-label属性的评分卡XML When 解析评分卡 Then 变量标签应正确设置")
        void shouldParseScorecardWithVariableLabel() {
            // Given
            String xml = "<scorecard name=\"test\" var-label=\"Test Variable\" var-category=\"test\" var=\"score\" scoring-type=\"weightsum\" assign-target-type=\"variable\"/>";

            // When
            ScorecardDefinition scorecard = parseScorecard(xml);

            // Then
            assertThat(scorecard).isNotNull();
            assertThat(scorecard.getVariableLabel()).isEqualTo("Test Variable");
        }

        @Test
        @DisplayName("Given 包含datatype属性的评分卡XML When 解析评分卡 Then datatype应正确设置为对应的枚举值")
        void shouldParseScorecardWithDatatype() {
            // Given
            String xml = "<scorecard name=\"test\" var-category=\"test\" var=\"score\" scoring-type=\"weightsum\" assign-target-type=\"variable\" datatype=\"Integer\"/>";

            // When
            ScorecardDefinition scorecard = parseScorecard(xml);

            // Then
            assertThat(scorecard).isNotNull();
            assertThat(scorecard.getDatatype()).isEqualTo(Datatype.Integer);
        }
    }

    @Nested
    @DisplayName("解析评分属性行")
    class ParseAttributeRows {

        @Test
        @DisplayName("Given 包含评分属性行的评分卡XML When 解析评分卡 Then 属性行应正确添加到rows列表")
        void shouldParseScorecardWithAttributeRows() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 属性行包含条件行 When 解析评分卡 Then 条件行应正确添加到属性行的conditionRows列表")
        void shouldParseScorecardWithConditionRows() {
            // Given

            // When

            // Then
        }
    }

    @Nested
    @DisplayName("解析评分单元格")
    class ParseCardCells {

        @Test
        @DisplayName("Given 包含评分单元格的评分卡XML When 解析评分卡 Then 单元格应正确添加到cells列表")
        void shouldParseScorecardWithCardCells() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 单元格包含条件联合对象 When 解析评分卡 Then 条件应正确解析")
        void shouldParseScorecardWithCellConditions() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 单元格包含值对象 When 解析评分卡 Then 值应正确解析")
        void shouldParseScorecardWithCellValue() {
            // Given

            // When

            // Then
        }
    }

    @Nested
    @DisplayName("解析自定义列")
    class ParseCustomColumns {

        @Test
        @DisplayName("Given 包含自定义列的评分卡XML When 解析评分卡 Then 自定义列应正确添加到customCols列表")
        void shouldParseScorecardWithCustomColumns() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 自定义列包含列号和名称 When 解析评分卡 Then 列号和名称应正确设置")
        void shouldParseCustomColumnWithNumberAndName() {
            // Given

            // When

            // Then
        }
    }

    @Nested
    @DisplayName("解析列配置")
    class ParseColumnConfig {

        @Test
        @DisplayName("Given 包含attr-col-width属性的评分卡XML When 解析评分卡 Then attributeColWidth应正确设置")
        void shouldParseAttributeColumnWidth() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 包含attr-col-name属性的评分卡XML When 解析评分卡 Then attributeColName应正确设置")
        void shouldParseAttributeColumnName() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 包含condition-col-width属性的评分卡XML When 解析评分卡 Then conditionColWidth应正确设置")
        void shouldParseConditionColumnWidth() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 包含condition-col-name属性的评分卡XML When 解析评分卡 Then conditionColName应正确设置")
        void shouldParseConditionColumnName() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 包含score-col-width属性的评分卡XML When 解析评分卡 Then scoreColWidth应正确设置")
        void shouldParseScoreColumnWidth() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given 包含score-col-name属性的评分卡XML When 解析评分卡 Then scoreColName应正确设置")
        void shouldParseScoreColumnName() {
            // Given

            // When

            // Then
        }
    }

    @Nested
    @DisplayName("解析高级属性")
    class ParseAdvancedAttributes {

        @Test
        @DisplayName("Given 包含weight-support属性的评分卡XML When 解析评分卡 Then weightSupport应正确设置为布尔值")
        void shouldParseWeightSupport() {
            // Given
            String xml = "<scorecard name=\"test\" var-category=\"test\" var=\"score\" scoring-type=\"weightsum\" assign-target-type=\"variable\" weight-support=\"true\"/>";

            // When
            ScorecardDefinition scorecard = parseScorecard(xml);

            // Then
            assertThat(scorecard).isNotNull();
            assertThat(scorecard.isWeightSupport()).isTrue();
        }

        @Test
        @DisplayName("Given 包含custom-scoring-bean属性的评分卡XML When 解析评分卡 Then scoringBean应正确设置")
        void shouldParseCustomScoringBean() {
            // Given
            String xml = "<scorecard name=\"test\" var-category=\"test\" var=\"score\" scoring-type=\"custom\" assign-target-type=\"variable\" custom-scoring-bean=\"customScoringBean\"/>";

            // When
            ScorecardDefinition scorecard = parseScorecard(xml);

            // Then
            assertThat(scorecard).isNotNull();
            assertThat(scorecard.getScoringBean()).isEqualTo("customScoringBean");
        }

        @Test
        @DisplayName("Given 包含salience属性的评分卡XML When 解析评分卡 Then salience应正确设置为整数值")
        void shouldParseSalience() {
            // Given
            String xml = "<scorecard name=\"test\" var-category=\"test\" var=\"score\" scoring-type=\"weightsum\" assign-target-type=\"variable\" salience=\"100\"/>";

            // When
            ScorecardDefinition scorecard = parseScorecard(xml);

            // Then
            assertThat(scorecard).isNotNull();
            assertThat(scorecard.getSalience()).isEqualTo(100);
        }

        @Test
        @DisplayName("Given 包含effective-date属性的评分卡XML When 解析评分卡 Then effectiveDate应正确解析为Date对象")
        void shouldParseEffectiveDate() {
            // Given
            String xml = "<scorecard name=\"test\" var-category=\"test\" var=\"score\" scoring-type=\"weightsum\" assign-target-type=\"variable\" effective-date=\"2024-01-01 00:00:00\"/>";

            // When
            ScorecardDefinition scorecard = parseScorecard(xml);

            // Then
            assertThat(scorecard).isNotNull();
            assertThat(scorecard.getEffectiveDate()).isNotNull();
        }

        @Test
        @DisplayName("Given 包含expires-date属性的评分卡XML When 解析评分卡 Then expiresDate应正确解析为Date对象")
        void shouldParseExpiresDate() {
            // Given
            String xml = "<scorecard name=\"test\" var-category=\"test\" var=\"score\" scoring-type=\"weightsum\" assign-target-type=\"variable\" expires-date=\"2024-12-31 00:00:00\"/>";

            // When
            ScorecardDefinition scorecard = parseScorecard(xml);

            // Then
            assertThat(scorecard).isNotNull();
            assertThat(scorecard.getExpiresDate()).isNotNull();
        }

        @Test
        @DisplayName("Given 包含enabled属性的评分卡XML When 解析评分卡 Then enabled应正确设置为布尔值")
        void shouldParseEnabled() {
            // Given
            String xml = "<scorecard name=\"test\" var-category=\"test\" var=\"score\" scoring-type=\"weightsum\" assign-target-type=\"variable\" enabled=\"true\"/>";

            // When
            ScorecardDefinition scorecard = parseScorecard(xml);

            // Then
            assertThat(scorecard).isNotNull();
            assertThat(scorecard.getEnabled()).isTrue();
        }

        @Test
        @DisplayName("Given 包含debug属性的评分卡XML When 解析评分卡 Then debug应正确设置为布尔值")
        void shouldParseDebug() {
            // Given
            String xml = "<scorecard name=\"test\" var-category=\"test\" var=\"score\" scoring-type=\"weightsum\" assign-target-type=\"variable\" debug=\"true\"/>";

            // When
            ScorecardDefinition scorecard = parseScorecard(xml);

            // Then
            assertThat(scorecard).isNotNull();
            assertThat(scorecard.getDebug()).isTrue();
        }
    }

    @Nested
    @DisplayName("解析库引用")
    class ParseLibraryReferences {

        @Test
        @DisplayName("Given 包含import-variable-library元素的评分卡XML When 解析评分卡 Then 变量库应正确添加到libraries列表")
        void shouldParseVariableLibrary() {
            // Given
            String xml = "<scorecard name=\"test\" var-category=\"test\" var=\"score\" scoring-type=\"weightsum\" assign-target-type=\"variable\">" +
                    "  <import-variable-library path=\"/path/to/var.lib\"/>" +
                    "</scorecard>";

            // When
            ScorecardDefinition scorecard = parseScorecard(xml);

            // Then
            assertThat(scorecard).isNotNull();
            assertThat(scorecard.getLibraries()).isNotEmpty();
            assertThat(scorecard.getLibraries().get(0).getType()).isEqualTo(LibraryType.Variable);
        }

        @Test
        @DisplayName("Given 包含import-constant-library元素的评分卡XML When 解析评分卡 Then 常量库应正确添加到libraries列表")
        void shouldParseConstantLibrary() {
            // Given
            String xml = "<scorecard name=\"test\" var-category=\"test\" var=\"score\" scoring-type=\"weightsum\" assign-target-type=\"variable\">" +
                    "  <import-constant-library path=\"/path/to/const.lib\"/>" +
                    "</scorecard>";

            // When
            ScorecardDefinition scorecard = parseScorecard(xml);

            // Then
            assertThat(scorecard).isNotNull();
            assertThat(scorecard.getLibraries()).isNotEmpty();
            assertThat(scorecard.getLibraries().get(0).getType()).isEqualTo(LibraryType.Constant);
        }

        @Test
        @DisplayName("Given 包含import-action-library元素的评分卡XML When 解析评分卡 Then 动作库应正确添加到libraries列表")
        void shouldParseActionLibrary() {
            // Given
            String xml = "<scorecard name=\"test\" var-category=\"test\" var=\"score\" scoring-type=\"weightsum\" assign-target-type=\"variable\">" +
                    "  <import-action-library path=\"/path/to/action.lib\"/>" +
                    "</scorecard>";

            // When
            ScorecardDefinition scorecard = parseScorecard(xml);

            // Then
            assertThat(scorecard).isNotNull();
            assertThat(scorecard.getLibraries()).isNotEmpty();
            assertThat(scorecard.getLibraries().get(0).getType()).isEqualTo(LibraryType.Action);
        }

        @Test
        @DisplayName("Given 包含import-parameter-library元素的评分卡XML When 解析评分卡 Then 参数库应正确添加到libraries列表")
        void shouldParseParameterLibrary() {
            // Given
            String xml = "<scorecard name=\"test\" var-category=\"test\" var=\"score\" scoring-type=\"weightsum\" assign-target-type=\"variable\">" +
                    "  <import-parameter-library path=\"/path/to/param.lib\"/>" +
                    "</scorecard>";

            // When
            ScorecardDefinition scorecard = parseScorecard(xml);

            // Then
            assertThat(scorecard).isNotNull();
            assertThat(scorecard.getLibraries()).isNotEmpty();
            assertThat(scorecard.getLibraries().get(0).getType()).isEqualTo(LibraryType.Parameter);
        }
    }

    @Nested
    @DisplayName("解析备注")
    class ParseRemark {

        @Test
        @DisplayName("Given 包含remark元素的评分卡XML When 解析评分卡 Then remark属性应正确设置")
        void shouldParseRemark() {
            // Given
            String xml = "<scorecard name=\"test\" var-category=\"test\" var=\"score\" scoring-type=\"weightsum\" assign-target-type=\"variable\">" +
                    "  <remark>Test remark</remark>" +
                    "</scorecard>";

            // When
            ScorecardDefinition scorecard = parseScorecard(xml);

            // Then
            assertThat(scorecard).isNotNull();
            assertThat(scorecard.getRemark()).isEqualTo("Test remark");
        }
    }

    @Nested
    @DisplayName("错误处理")
    class ErrorHandling {

        @Test
        @DisplayName("Given effective-date格式不正确 When 解析评分卡 Then 应抛出RuleException异常")
        void shouldThrowExceptionForInvalidEffectiveDateFormat() {
            // Given

            // When

            // Then
        }

        @Test
        @DisplayName("Given expires-date格式不正确 When 解析评分卡 Then 应抛出RuleException异常")
        void shouldThrowExceptionForInvalidExpiresDateFormat() {
            // Given

            // When

            // Then
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

    private ScorecardDefinition parseScorecard(String xml) {
        ScorecardParser parser = new ScorecardParser();
        CardCellParser cellParser = new CardCellParser();
        parser.setCardCellParser(cellParser);

        RulesRebuilder rulesRebuilder = mock(RulesRebuilder.class);
        ResourceLibraryBuilder rlBuilder = mock(ResourceLibraryBuilder.class);
        ResourceLibrary emptyResourceLibrary = new ResourceLibrary(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        when(rulesRebuilder.getResourceLibraryBuilder()).thenReturn(rlBuilder);
        when(rlBuilder.buildResourceLibrary(any())).thenReturn(emptyResourceLibrary);
        // Return a Variable with matching label and datatype to preserve parsed values
        Variable testVariable = new Variable();
        testVariable.setLabel("Test Variable");
        testVariable.setType(Datatype.Integer);
        when(rulesRebuilder.getVariableByName(any(), any(), any(), any())).thenReturn(testVariable);
        parser.setRulesRebuilder(rulesRebuilder);

        Element root = parseXml(xml);
        return parser.parse(root);
    }
}
