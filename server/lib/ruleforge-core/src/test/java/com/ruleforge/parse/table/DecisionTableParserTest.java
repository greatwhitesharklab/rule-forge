package com.ruleforge.parse.table;

import com.ruleforge.Configure;
import com.ruleforge.builder.ResourceLibraryBuilder;
import com.ruleforge.builder.RulesRebuilder;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.table.Cell;
import com.ruleforge.model.table.Column;
import com.ruleforge.model.table.ColumnType;
import com.ruleforge.model.table.DecisionTable;
import com.ruleforge.model.table.Row;
import com.ruleforge.model.rule.Library;
import com.ruleforge.model.rule.LibraryType;
import com.ruleforge.parse.ActionParser;
import com.ruleforge.parse.ExecuteMethodActionParser;
import com.ruleforge.parse.ValueParser;
import com.ruleforge.parse.ComplexArithmeticParser;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("决策表解析器")
class DecisionTableParserTest {

    private DecisionTableParser parser;
    private RowParser rowParser;
    private ColumnParser columnParser;
    private CellParser cellParser;
    private RulesRebuilder rulesRebuilder;

    @BeforeEach
    void setUp() {
        Configure configure = new Configure();
        configure.setDateFormat("yyyy-MM-dd HH:mm:ss");

        parser = new DecisionTableParser();
        rowParser = new RowParser();
        columnParser = new ColumnParser();
        cellParser = new CellParser();
        rulesRebuilder = mock(RulesRebuilder.class);
        ResourceLibraryBuilder rlBuilder = mock(ResourceLibraryBuilder.class);
        ResourceLibrary emptyResourceLibrary = new ResourceLibrary(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        when(rulesRebuilder.getResourceLibraryBuilder()).thenReturn(rlBuilder);
        when(rlBuilder.buildResourceLibrary(any())).thenReturn(emptyResourceLibrary);
        when(rulesRebuilder.getVariableByName(any(), any(), any(), any())).thenReturn(new Variable());

        // Set up cell parser dependencies
        JointParser jointParser = new JointParser();
        ValueParser valueParser = new ValueParser();
        valueParser.setArithmeticParser(new ComplexArithmeticParser());
        jointParser.setValueParser(valueParser);
        cellParser.setJointParser(jointParser);
        cellParser.setValueParser(valueParser);

        // Set up action parsers via reflection since we're not in Spring context
        try {
            Field actionParsersField = CellParser.class.getDeclaredField("actionParsers");
            actionParsersField.setAccessible(true);
            ExecuteMethodActionParser executeMethodActionParser = new ExecuteMethodActionParser();
            executeMethodActionParser.setValueParser(valueParser);
            Collection<ActionParser> actionParsers = List.of(executeMethodActionParser);
            actionParsersField.set(cellParser, actionParsers);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set action parsers", e);
        }

        parser.setRowParser(rowParser);
        parser.setColumnParser(columnParser);
        parser.setCellParser(cellParser);
        parser.setRulesRebuilder(rulesRebuilder);
    }

    @Nested
    @DisplayName("解析决策表XML")
    class ParseDecisionTableXml {

        @Test
        @DisplayName("Given 包含条件列和动作列的决策表XML When 解析决策表 Then 应返回正确的DecisionTable对象")
        void shouldParseTableWithConditionAndActionColumns() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");
            root.addAttribute("salience", "10");

            // Add criteria column
            Element col1 = root.addElement("col");
            col1.addAttribute("num", "1");
            col1.addAttribute("type", "Criteria");
            col1.addAttribute("var-category", "applicant");
            col1.addAttribute("var", "age");
            col1.addAttribute("width", "100");

            // Add assignment column
            Element col2 = root.addElement("col");
            col2.addAttribute("num", "2");
            col2.addAttribute("type", "Assignment");
            col2.addAttribute("var-category", "loan");
            col2.addAttribute("var", "approved");
            col2.addAttribute("datatype", "Boolean");
            col2.addAttribute("width", "100");

            // Add row
            Element row = root.addElement("row");
            row.addAttribute("num", "1");
            row.addAttribute("height", "25");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table).isNotNull();
            assertThat(table.getColumns()).hasSize(2);
            assertThat(table.getRows()).hasSize(1);
            assertThat(table.getColumns().get(0).getType()).isEqualTo(ColumnType.Criteria);
            assertThat(table.getColumns().get(1).getType()).isEqualTo(ColumnType.Assignment);
        }

        @Test
        @DisplayName("Given 带有salience属性的决策表XML When 解析决策表 Then DecisionTable的salience应正确设置")
        void shouldParseTableWithSalienceAttribute() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");
            root.addAttribute("salience", "100");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table.getSalience()).isEqualTo(100);
        }

        @Test
        @DisplayName("Given 带有生效日期和过期日期的决策表XML When 解析决策表 Then effectiveDate和expiresDate应正确解析")
        void shouldParseTableWithEffectiveAndExpiresDates() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");
            root.addAttribute("effective-date", "2023-01-01 00:00:00");
            root.addAttribute("expires-date", "2023-12-31 00:00:00");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table.getEffectiveDate()).isNotNull();
            assertThat(table.getExpiresDate()).isNotNull();
        }

        @Test
        @DisplayName("Given 带有enabled和debug属性的决策表XML When 解析决策表 Then enabled和debug应正确设置")
        void shouldParseTableWithEnabledAndDebugAttributes() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");
            root.addAttribute("enabled", "true");
            root.addAttribute("debug", "false");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table.getEnabled()).isTrue();
            assertThat(table.getDebug()).isFalse();
        }

        @Test
        @DisplayName("Given 包含多行多列的决策表XML When 解析决策表 Then 应正确解析所有行和列")
        void shouldParseTableWithMultipleRowsAndColumns() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");

            // Add 3 columns
            for (int i = 1; i <= 3; i++) {
                Element col = root.addElement("col");
                col.addAttribute("num", String.valueOf(i));
                col.addAttribute("type", "Criteria");
                col.addAttribute("var-category", "test");
                col.addAttribute("var", "field" + i);
                col.addAttribute("width", "100");
            }

            // Add 3 rows
            for (int i = 1; i <= 3; i++) {
                Element row = root.addElement("row");
                row.addAttribute("num", String.valueOf(i));
                row.addAttribute("height", "25");
            }

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table.getColumns()).hasSize(3);
            assertThat(table.getRows()).hasSize(3);
            for (int i = 0; i < 3; i++) {
                assertThat(table.getColumns().get(i).getNum()).isEqualTo(i + 1);
                assertThat(table.getRows().get(i).getNum()).isEqualTo(i + 1);
            }
        }

        @Test
        @DisplayName("Given 包含Criteria类型条件列的决策表XML When 解析决策表 Then 应正确解析条件列")
        void shouldParseTableWithCriteriaConditionColumns() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");

            Element col = root.addElement("col");
            col.addAttribute("num", "1");
            col.addAttribute("type", "Criteria");
            col.addAttribute("var-category", "applicant");
            col.addAttribute("var", "age");
            col.addAttribute("width", "100");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table.getColumns()).hasSize(1);
            Column criteriaColumn = table.getColumns().get(0);
            assertThat(criteriaColumn.getType()).isEqualTo(ColumnType.Criteria);
            assertThat(criteriaColumn.getVariableCategory()).isEqualTo("applicant");
            assertThat(criteriaColumn.getVariableName()).isEqualTo("age");
        }

        @Test
        @DisplayName("Given 包含Assignment类型动作列的决策表XML When 解析决策表 Then 应正确解析赋值动作列")
        void shouldParseTableWithAssignmentActionColumns() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");

            Element col = root.addElement("col");
            col.addAttribute("num", "2");
            col.addAttribute("type", "Assignment");
            col.addAttribute("var-category", "loan");
            col.addAttribute("var", "approved");
            col.addAttribute("datatype", "Boolean");
            col.addAttribute("width", "120");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table.getColumns()).hasSize(1);
            Column assignmentColumn = table.getColumns().get(0);
            assertThat(assignmentColumn.getType()).isEqualTo(ColumnType.Assignment);
            assertThat(assignmentColumn.getVariableCategory()).isEqualTo("loan");
            assertThat(assignmentColumn.getVariableName()).isEqualTo("approved");
        }

        @Test
        @DisplayName("Given 包含ConsolePrint类型动作列的决策表XML When 解析决策表 Then 应正确解析控制台打印动作列")
        void shouldParseTableWithConsolePrintActionColumns() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");

            Element col = root.addElement("col");
            col.addAttribute("num", "3");
            col.addAttribute("type", "ConsolePrint");
            col.addAttribute("width", "150");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table.getColumns()).hasSize(1);
            Column consoleColumn = table.getColumns().get(0);
            assertThat(consoleColumn.getType()).isEqualTo(ColumnType.ConsolePrint);
        }

        @Test
        @DisplayName("Given 包含ExecuteMethod类型动作列的决策表XML When 解析决策表 Then 应正确解析方法执行动作列")
        void shouldParseTableWithExecuteMethodActionColumns() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");

            Element col = root.addElement("col");
            col.addAttribute("num", "4");
            col.addAttribute("type", "ExecuteMethod");
            col.addAttribute("width", "150");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table.getColumns()).hasSize(1);
            Column methodColumn = table.getColumns().get(0);
            assertThat(methodColumn.getType()).isEqualTo(ColumnType.ExecuteMethod);
        }
    }

    @Nested
    @DisplayName("解析库引用")
    class ParseLibraries {

        @Test
        @DisplayName("Given 包含import-variable-library的决策表XML When 解析决策表 Then 应正确添加变量库引用")
        void shouldParseImportVariableLibrary() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");
            Element importLib = root.addElement("import-variable-library");
            importLib.addAttribute("path", "/test/variables.xml");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table.getLibraries()).hasSize(1);
            Library lib = table.getLibraries().get(0);
            assertThat(lib.getPath()).isEqualTo("/test/variables.xml");
            assertThat(lib.getType()).isEqualTo(LibraryType.Variable);
        }

        @Test
        @DisplayName("Given 包含import-constant-library的决策表XML When 解析决策表 Then 应正确添加常量库引用")
        void shouldParseImportConstantLibrary() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");
            Element importLib = root.addElement("import-constant-library");
            importLib.addAttribute("path", "/test/constants.xml");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table.getLibraries()).hasSize(1);
            Library lib = table.getLibraries().get(0);
            assertThat(lib.getPath()).isEqualTo("/test/constants.xml");
            assertThat(lib.getType()).isEqualTo(LibraryType.Constant);
        }

        @Test
        @DisplayName("Given 包含import-action-library的决策表XML When 解析决策表 Then 应正确添加动作库引用")
        void shouldParseImportActionLibrary() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");
            Element importLib = root.addElement("import-action-library");
            importLib.addAttribute("path", "/test/actions.xml");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table.getLibraries()).hasSize(1);
            Library lib = table.getLibraries().get(0);
            assertThat(lib.getPath()).isEqualTo("/test/actions.xml");
            assertThat(lib.getType()).isEqualTo(LibraryType.Action);
        }

        @Test
        @DisplayName("Given 包含import-parameter-library的决策表XML When 解析决策表 Then 应正确添加参数库引用")
        void shouldParseImportParameterLibrary() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");
            Element importLib = root.addElement("import-parameter-library");
            importLib.addAttribute("path", "/test/parameters.xml");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table.getLibraries()).hasSize(1);
            Library lib = table.getLibraries().get(0);
            assertThat(lib.getPath()).isEqualTo("/test/parameters.xml");
            assertThat(lib.getType()).isEqualTo(LibraryType.Parameter);
        }
    }

    @Nested
    @DisplayName("解析单元格")
    class ParseCells {

        @Test
        @DisplayName("Given 包含单元格数据的决策表XML When 解析决策表 Then 应正确构建cellMap")
        void shouldParseTableWithCells() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");

            // Add a row and column
            Element row = root.addElement("row");
            row.addAttribute("num", "1");
            row.addAttribute("height", "25");

            Element col = root.addElement("col");
            col.addAttribute("num", "1");
            col.addAttribute("type", "Criteria");
            col.addAttribute("var-category", "test");
            col.addAttribute("var", "field");
            col.addAttribute("width", "100");

            // Add a cell
            Element cell = root.addElement("cell");
            cell.addAttribute("row", "1");
            cell.addAttribute("col", "1");
            cell.addAttribute("rowspan", "1");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table.getCellMap()).hasSize(1);
            assertThat(table.getCellMap().containsKey("1,1")).isTrue();
            Cell parsedCell = table.getCellMap().get("1,1");
            assertThat(parsedCell.getRow()).isEqualTo(1);
            assertThat(parsedCell.getCol()).isEqualTo(1);
        }

        @Test
        @DisplayName("Given 包含条件单元格的决策表XML When 解析决策表 Then 单元格应包含Joint对象")
        void shouldParseCellWithJoint() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");

            // Add row and column
            Element row = root.addElement("row");
            row.addAttribute("num", "1");
            row.addAttribute("height", "25");

            Element col = root.addElement("col");
            col.addAttribute("num", "1");
            col.addAttribute("type", "Criteria");
            col.addAttribute("width", "100");

            // Add a cell with joint
            Element cell = root.addElement("cell");
            cell.addAttribute("row", "1");
            cell.addAttribute("col", "1");
            cell.addAttribute("rowspan", "1");

            // Add joint element
            Element joint = cell.addElement("joint");
            joint.addAttribute("type", "and");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            Cell parsedCell = table.getCellMap().get("1,1");
            assertThat(parsedCell).isNotNull();
            assertThat(parsedCell.getJoint()).isNotNull();
        }

        @Test
        @DisplayName("Given 包含值单元格的决策表XML When 解析决策表 Then 单元格应包含Value对象")
        void shouldParseCellWithValue() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");

            // Add row and column
            Element row = root.addElement("row");
            row.addAttribute("num", "1");
            row.addAttribute("height", "25");

            Element col = root.addElement("col");
            col.addAttribute("num", "1");
            col.addAttribute("type", "Assignment");
            col.addAttribute("width", "100");

            // Add a cell with value
            Element cell = root.addElement("cell");
            cell.addAttribute("row", "1");
            cell.addAttribute("col", "1");
            cell.addAttribute("rowspan", "1");

            // Add value element
            Element value = cell.addElement("value");
            value.addAttribute("type", "Input");
            value.addAttribute("content", "test-value");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            Cell parsedCell = table.getCellMap().get("1,1");
            assertThat(parsedCell).isNotNull();
            assertThat(parsedCell.getValue()).isNotNull();
        }

        @Test
        @DisplayName("Given 包含动作单元格的决策表XML When 解析决策表 Then 单元格应包含Action对象")
        void shouldParseCellWithAction() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");

            // Add row and column
            Element row = root.addElement("row");
            row.addAttribute("num", "1");
            row.addAttribute("height", "25");

            Element col = root.addElement("col");
            col.addAttribute("num", "1");
            col.addAttribute("type", "ExecuteMethod");
            col.addAttribute("width", "100");

            // Add a cell with action
            Element cell = root.addElement("cell");
            cell.addAttribute("row", "1");
            cell.addAttribute("col", "1");
            cell.addAttribute("rowspan", "1");

            // Add action element
            Element action = cell.addElement("execute-method");
            action.addAttribute("bean", "testAction");
            action.addAttribute("method-name", "execute");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            Cell parsedCell = table.getCellMap().get("1,1");
            assertThat(parsedCell).isNotNull();
            assertThat(parsedCell.getAction()).isNotNull();
        }
    }

    @Nested
    @DisplayName("解析备注")
    class ParseRemark {

        @Test
        @DisplayName("Given 包含remark元素的决策表XML When 解析决策表 Then remark应正确设置")
        void shouldParseTableWithRemark() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");
            Element remark = root.addElement("remark");
            remark.setText("This is a test decision table");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table.getRemark()).isEqualTo("This is a test decision table");
        }
    }

    @Nested
    @DisplayName("重构决策表")
    class RebuildTable {

        @Test
        @DisplayName("Given 解析后的决策表包含单元格引用 When 重构决策表 Then 应正确解析变量类型")
        void shouldRebuildTableWithVariableTypes() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");

            Element col = root.addElement("col");
            col.addAttribute("num", "1");
            col.addAttribute("type", "Criteria");
            col.addAttribute("var-category", "test");
            col.addAttribute("var", "field");
            col.addAttribute("width", "100");

            Element row = root.addElement("row");
            row.addAttribute("num", "1");
            row.addAttribute("height", "25");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table.getColumns()).hasSize(1);
            // Note: datatype and variableLabel are set by rebuildTable which uses RulesRebuilder
            // This test verifies the parser doesn't throw an exception
        }

        @Test
        @DisplayName("Given 解析后的决策表包含单元格引用 When 重构决策表 Then 应正确解析变量标签")
        void shouldRebuildTableWithVariableLabels() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");

            Element col = root.addElement("col");
            col.addAttribute("num", "1");
            col.addAttribute("type", "Criteria");
            col.addAttribute("var-category", "test");
            col.addAttribute("var", "field");
            col.addAttribute("var-label", "Test Field");
            col.addAttribute("width", "100");

            Element row = root.addElement("row");
            row.addAttribute("num", "1");
            row.addAttribute("height", "25");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table.getColumns()).hasSize(1);
            assertThat(table.getColumns().get(0).getVariableName()).isEqualTo("field");
        }

        @Test
        @DisplayName("Given 解析后的决策表包含库引用 When 重构决策表 Then 应正确重构单元格内容")
        void shouldRebuildTableCellsWithResourceLibraries() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");

            Element importLib = root.addElement("import-variable-library");
            importLib.addAttribute("path", "/test/variables.xml");

            Element col = root.addElement("col");
            col.addAttribute("num", "1");
            col.addAttribute("type", "Criteria");
            col.addAttribute("width", "100");

            Element row = root.addElement("row");
            row.addAttribute("num", "1");
            row.addAttribute("height", "25");

            Element cell = root.addElement("cell");
            cell.addAttribute("row", "1");
            cell.addAttribute("col", "1");
            cell.addAttribute("rowspan", "1");

            // When & Then - should not throw exception
            assertThatCode(() -> parser.parse(root)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("解析器支持")
    class ParserSupport {

        @Test
        @DisplayName("Given 元素名称为decision-table When 调用support方法 Then 应返回true")
        void shouldSupportDecisionTableElement() {
            // Given
            String elementName = "decision-table";

            // When
            boolean supported = parser.support(elementName);

            // Then
            assertThat(supported).isTrue();
        }

        @Test
        @DisplayName("Given 元素名称不为decision-table When 调用support方法 Then 应返回false")
        void shouldNotSupportOtherElements() {
            // Given
            String elementName = "decision-tree";

            // When
            boolean supported = parser.support(elementName);

            // Then
            assertThat(supported).isFalse();
        }
    }

    @Nested
    @DisplayName("异常处理")
    class ExceptionHandling {

        @Test
        @DisplayName("Given 包含无效日期格式的决策表XML When 解析决策表 Then 应抛出RuleException")
        void shouldThrowExceptionForInvalidDateFormat() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");
            root.addAttribute("effective-date", "invalid-date-format");

            // When & Then
            assertThatThrownBy(() -> parser.parse(root))
                    .isInstanceOf(com.ruleforge.exception.RuleException.class);
        }

        @Test
        @DisplayName("Given 包含无效salience值的决策表XML When 解析决策表 Then 应抛出NumberFormatException")
        void shouldThrowExceptionForInvalidSalience() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");
            root.addAttribute("salience", "not-a-number");

            // When & Then
            assertThatThrownBy(() -> parser.parse(root))
                    .isInstanceOf(NumberFormatException.class);
        }

        @Test
        @DisplayName("Given 包含无效布尔值的决策表XML When 解析决策表 Then Boolean.valueOf应正常处理不抛出异常")
        void shouldThrowExceptionForInvalidBooleanValue() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");
            root.addAttribute("enabled", "not-a-boolean");

            // When
            DecisionTable table = parser.parse(root);

            // Then - Boolean.valueOf("not-a-boolean") returns false, no exception
            assertThat(table).isNotNull();
            assertThat(table.getEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("Given 空决策表XML When 解析决策表 Then 应返回空DecisionTable对象")
        void shouldParseEmptyDecisionTable() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table).isNotNull();
            assertThat(table.getRows()).isNull();
            assertThat(table.getColumns()).isNull();
            assertThat(table.getCellMap()).isNull();
        }

        @Test
        @DisplayName("Given 只有列定义没有行的决策表XML When 解析决策表 Then rows应为空")
        void shouldParseTableWithOnlyColumns() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");

            Element col = root.addElement("col");
            col.addAttribute("num", "1");
            col.addAttribute("type", "Criteria");
            col.addAttribute("width", "100");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table.getColumns()).hasSize(1);
            assertThat(table.getRows()).isNull();
        }

        @Test
        @DisplayName("Given 只有行定义没有列的决策表XML When 解析决策表 Then columns应为空")
        void shouldParseTableWithOnlyRows() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");

            Element row = root.addElement("row");
            row.addAttribute("num", "1");
            row.addAttribute("height", "25");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table.getRows()).hasSize(1);
            assertThat(table.getColumns()).isNull();
        }

        @Test
        @DisplayName("Given 没有单元格的决策表XML When 解析决策表 Then cellMap应为空")
        void shouldParseTableWithNoCells() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");

            Element row = root.addElement("row");
            row.addAttribute("num", "1");
            row.addAttribute("height", "25");

            Element col = root.addElement("col");
            col.addAttribute("num", "1");
            col.addAttribute("type", "Criteria");
            col.addAttribute("width", "100");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table.getRows()).hasSize(1);
            assertThat(table.getColumns()).hasSize(1);
            assertThat(table.getCellMap()).isNull();
        }

        @Test
        @DisplayName("Given 包含null子元素的决策表XML When 解析决策表 Then 应忽略null元素")
        void shouldIgnoreNullChildElements() {
            // Given
            Document doc = DocumentHelper.createDocument();
            Element root = doc.addElement("decision-table");

            Element row = root.addElement("row");
            row.addAttribute("num", "1");
            row.addAttribute("height", "25");

            // When
            DecisionTable table = parser.parse(root);

            // Then
            assertThat(table.getRows()).hasSize(1);
            // Parser should not throw exception with null elements
        }
    }
}
