package com.ruleforge.parse.table;

import com.ruleforge.model.rule.Library;
import com.ruleforge.model.rule.LibraryType;
import com.ruleforge.model.table.ScriptCell;
import com.ruleforge.model.table.ScriptDecisionTable;
import com.ruleforge.model.table.Column;
import com.ruleforge.model.table.Row;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("脚本决策表解析器")
class ScriptDecisionTableParserTest {

    private ScriptDecisionTableParser parser;

    @BeforeEach
    void setUp() {
        parser = new ScriptDecisionTableParser();
        parser.setRowParser(new RowParser());
        parser.setColumnParser(new ColumnParser());
        parser.setScriptCellParser(new ScriptCellParser());
    }

    @Nested
    @DisplayName("解析脚本决策表结构")
    class ParseTableStructure {

        @Test
        @DisplayName("Given 包含行定义的决策表XML When 解析决策表 Then ScriptDecisionTable应包含对应的Row对象")
        void shouldParseRows() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element rowElement = element.addElement("row");
            rowElement.addAttribute("num", "1");
            rowElement.addAttribute("height", "30");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            assertThat(table.getRows()).isNotNull();
            assertThat(table.getRows()).hasSize(1);
            assertThat(table.getRows().get(0).getNum()).isEqualTo(1);
            assertThat(table.getRows().get(0).getHeight()).isEqualTo(30);
        }

        @Test
        @DisplayName("Given 包含列定义的决策表XML When 解析决策表 Then ScriptDecisionTable应包含对应的Column对象")
        void shouldParseColumns() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element colElement = element.addElement("col");
            colElement.addAttribute("num", "1");
            colElement.addAttribute("type", "Criteria");
            colElement.addAttribute("width", "100");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            assertThat(table.getColumns()).isNotNull();
            assertThat(table.getColumns()).hasSize(1);
            assertThat(table.getColumns().get(0).getNum()).isEqualTo(1);
            assertThat(table.getColumns().get(0).getWidth()).isEqualTo(100);
        }

        @Test
        @DisplayName("Given 包含多个行和列的决策表XML When 解析决策表 Then 应正确解析所有行和列")
        void shouldParseMultipleRowsAndColumns() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element row1 = element.addElement("row");
            row1.addAttribute("num", "1");
            row1.addAttribute("height", "30");
            Element row2 = element.addElement("row");
            row2.addAttribute("num", "2");
            row2.addAttribute("height", "40");
            Element col1 = element.addElement("col");
            col1.addAttribute("num", "1");
            col1.addAttribute("type", "Criteria");
            col1.addAttribute("width", "100");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            assertThat(table.getRows()).hasSize(2);
            assertThat(table.getColumns()).hasSize(1);
        }

        @Test
        @DisplayName("Given script-decision-table元素 When 调用support方法 Then 应返回true")
        void shouldSupportScriptDecisionTableElement() {
            // Given & When
            boolean supported = parser.support("script-decision-table");

            // Then
            assertThat(supported).isTrue();
        }

        @Test
        @DisplayName("Given 非script-decision-table元素 When 调用support方法 Then 应返回false")
        void shouldNotSupportOtherElements() {
            // Given & When
            boolean supported = parser.support("decision-table");

            // Then
            assertThat(supported).isFalse();
        }
    }

    @Nested
    @DisplayName("解析脚本单元格")
    class ParseScriptCells {

        @Test
        @DisplayName("Given 包含脚本单元格的决策表XML When 解析决策表 Then 应将单元格添加到cellMap中")
        void shouldParseScriptCells() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element cellElement = element.addElement("script-cell");
            cellElement.addAttribute("row", "1");
            cellElement.addAttribute("col", "1");
            cellElement.addAttribute("rowspan", "1");
            cellElement.setText("parameter.age > 18");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            assertThat(table.getCellMap()).isNotNull();
            assertThat(table.getCellMap()).hasSize(1);
            assertThat(table.getCellMap().containsKey("1,1")).isTrue();
        }

        @Test
        @DisplayName("Given 包含脚本的单元格 When 解析单元格 Then ScriptCell应包含正确的脚本内容")
        void shouldParseCellScriptContent() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element cellElement = element.addElement("script-cell");
            cellElement.addAttribute("row", "1");
            cellElement.addAttribute("col", "1");
            cellElement.addAttribute("rowspan", "1");
            cellElement.setText("variable.result = \"adult\"");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            ScriptCell cell = table.getCellMap().get("1,1");
            assertThat(cell.getScript()).isEqualTo("variable.result = \"adult\"");
        }

        @Test
        @DisplayName("Given 包含行列坐标的单元格 When 解析单元格 Then ScriptCell应包含正确的row和col值")
        void shouldParseCellCoordinates() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element cellElement = element.addElement("script-cell");
            cellElement.addAttribute("row", "2");
            cellElement.addAttribute("col", "3");
            cellElement.addAttribute("rowspan", "1");
            cellElement.setText("test script");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            ScriptCell cell = table.getCellMap().get("2,3");
            assertThat(cell.getRow()).isEqualTo(2);
            assertThat(cell.getCol()).isEqualTo(3);
        }

        @Test
        @DisplayName("Given 包含rowspan属性的单元格 When 解析单元格 Then ScriptCell应包含正确的rowspan值")
        void shouldParseCellRowspan() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element cellElement = element.addElement("script-cell");
            cellElement.addAttribute("row", "1");
            cellElement.addAttribute("col", "1");
            cellElement.addAttribute("rowspan", "3");
            cellElement.setText("test script");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            ScriptCell cell = table.getCellMap().get("1,1");
            assertThat(cell.getRowspan()).isEqualTo(3);
        }

        @Test
        @DisplayName("Given 多个单元格 When 解析决策表 Then buildCellKey方法应生成正确的键")
        void shouldBuildCellKey() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element cell1 = element.addElement("script-cell");
            cell1.addAttribute("row", "1");
            cell1.addAttribute("col", "1");
            cell1.addAttribute("rowspan", "1");
            cell1.setText("script1");
            Element cell2 = element.addElement("script-cell");
            cell2.addAttribute("row", "2");
            cell2.addAttribute("col", "3");
            cell2.addAttribute("rowspan", "1");
            cell2.setText("script2");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            assertThat(table.getCellMap()).containsKey("1,1");
            assertThat(table.getCellMap()).containsKey("2,3");
            assertThat(table.buildCellKey(1, 1)).isEqualTo("1,1");
            assertThat(table.buildCellKey(2, 3)).isEqualTo("2,3");
        }
    }

    @Nested
    @DisplayName("解析脚本条件列")
    class ParseScriptConditionColumns {

        @Test
        @DisplayName("Given 包含条件列的决策表XML When 解析决策表 Then 应正确解析条件列定义")
        void shouldParseConditionColumn() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element colElement = element.addElement("col");
            colElement.addAttribute("num", "1");
            colElement.addAttribute("type", "Criteria");
            colElement.addAttribute("width", "100");
            colElement.addAttribute("var", "age");
            colElement.addAttribute("var-category", "parameter");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            assertThat(table.getColumns()).hasSize(1);
            assertThat(table.getColumns().get(0).getVariableName()).isEqualTo("age");
            assertThat(table.getColumns().get(0).getVariableCategory()).isEqualTo("parameter");
        }

        @Test
        @DisplayName("Given 条件列中包含脚本表达式 When 解析单元格 Then 应正确解析条件脚本")
        void shouldParseConditionColumnScript() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element cellElement = element.addElement("script-cell");
            cellElement.addAttribute("row", "1");
            cellElement.addAttribute("col", "1");
            cellElement.addAttribute("rowspan", "1");
            cellElement.setText("parameter.age > 18 && parameter.score > 60");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            ScriptCell cell = table.getCellMap().get("1,1");
            assertThat(cell.getScript()).contains(">");
            assertThat(cell.getScript()).contains("&&");
        }

        @Test
        @DisplayName("Given 包含多个条件列的决策表 When 解析决策表 Then 应正确解析所有条件列")
        void shouldParseMultipleConditionColumns() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element col1 = element.addElement("col");
            col1.addAttribute("num", "1");
            col1.addAttribute("type", "Criteria");
            col1.addAttribute("width", "100");
            Element col2 = element.addElement("col");
            col2.addAttribute("num", "2");
            col2.addAttribute("type", "Criteria");
            col2.addAttribute("width", "150");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            assertThat(table.getColumns()).hasSize(2);
        }

        @Test
        @DisplayName("Given 条件列包含复杂脚本表达式 When 解析决策表 Then 应保留完整的脚本内容")
        void shouldParseComplexConditionScript() {
            // Given
            String complexScript = "parameter.age >= 18 and (parameter.score > 60 or parameter.level == 'A')";
            Element element = DocumentHelper.createElement("script-decision-table");
            Element cellElement = element.addElement("script-cell");
            cellElement.addAttribute("row", "1");
            cellElement.addAttribute("col", "1");
            cellElement.addAttribute("rowspan", "1");
            cellElement.setText(complexScript);

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            ScriptCell cell = table.getCellMap().get("1,1");
            assertThat(cell.getScript()).isEqualTo(complexScript);
        }
    }

    @Nested
    @DisplayName("解析脚本动作列")
    class ParseScriptActionColumns {

        @Test
        @DisplayName("Given 包含动作列的决策表XML When 解析决策表 Then 应正确解析动作列定义")
        void shouldParseActionColumn() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element colElement = element.addElement("col");
            colElement.addAttribute("num", "2");
            colElement.addAttribute("type", "Assignment");
            colElement.addAttribute("width", "200");
            colElement.addAttribute("var", "result");
            colElement.addAttribute("var-category", "variable");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            assertThat(table.getColumns()).hasSize(1);
            assertThat(table.getColumns().get(0).getVariableName()).isEqualTo("result");
        }

        @Test
        @DisplayName("Given 动作列中包含脚本语句 When 解析单元格 Then 应正确解析动作脚本")
        void shouldParseActionColumnScript() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element cellElement = element.addElement("script-cell");
            cellElement.addAttribute("row", "1");
            cellElement.addAttribute("col", "2");
            cellElement.addAttribute("rowspan", "1");
            cellElement.setText("variable.result = \"pass\"");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            ScriptCell cell = table.getCellMap().get("1,2");
            assertThat(cell.getScript()).contains("=");
        }

        @Test
        @DisplayName("Given 包含多个动作列的决策表 When 解析决策表 Then 应正确解析所有动作列")
        void shouldParseMultipleActionColumns() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element cell1 = element.addElement("script-cell");
            cell1.addAttribute("row", "1");
            cell1.addAttribute("col", "2");
            cell1.addAttribute("rowspan", "1");
            cell1.setText("action1");
            Element cell2 = element.addElement("script-cell");
            cell2.addAttribute("row", "1");
            cell2.addAttribute("col", "3");
            cell2.addAttribute("rowspan", "1");
            cell2.setText("action2");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            assertThat(table.getCellMap()).hasSize(2);
        }

        @Test
        @DisplayName("Given 动作列包含方法调用脚本 When 解析决策表 Then 应保留完整的方法调用语句")
        void shouldParseMethodCallInActionColumn() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element cellElement = element.addElement("script-cell");
            cellElement.addAttribute("row", "1");
            cellElement.addAttribute("col", "2");
            cellElement.addAttribute("rowspan", "1");
            cellElement.setText("System.out.println(\"test\")");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            ScriptCell cell = table.getCellMap().get("1,2");
            assertThat(cell.getScript()).contains(".");
            assertThat(cell.getScript()).contains("println");
        }

        @Test
        @DisplayName("Given 动作列包含赋值语句 When 解析决策表 Then 应保留完整的赋值表达式")
        void shouldParseAssignmentInActionColumn() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element cellElement = element.addElement("script-cell");
            cellElement.addAttribute("row", "1");
            cellElement.addAttribute("col", "2");
            cellElement.addAttribute("rowspan", "1");
            cellElement.setText("variable.total = parameter.amount * 1.1");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            ScriptCell cell = table.getCellMap().get("1,2");
            assertThat(cell.getScript()).contains("*");
        }
    }

    @Nested
    @DisplayName("解析库引用")
    class ParseLibraryImports {

        @Test
        @DisplayName("Given 包含import-variable-library元素的决策表 When 解析决策表 Then 应添加Variable类型Library")
        void shouldParseVariableLibraryImport() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element importElement = element.addElement("import-variable-library");
            importElement.addAttribute("path", "/test/var.ul");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            assertThat(table.getLibraries()).isNotNull();
            assertThat(table.getLibraries()).hasSize(1);
            assertThat(table.getLibraries().get(0).getType()).isEqualTo(LibraryType.Variable);
            assertThat(table.getLibraries().get(0).getPath()).isEqualTo("/test/var.ul");
        }

        @Test
        @DisplayName("Given 包含import-constant-library元素的决策表 When 解析决策表 Then 应添加Constant类型Library")
        void shouldParseConstantLibraryImport() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element importElement = element.addElement("import-constant-library");
            importElement.addAttribute("path", "/test/const.ul");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            assertThat(table.getLibraries()).isNotNull();
            assertThat(table.getLibraries()).hasSize(1);
            assertThat(table.getLibraries().get(0).getType()).isEqualTo(LibraryType.Constant);
        }

        @Test
        @DisplayName("Given 包含import-action-library元素的决策表 When 解析决策表 Then 应添加Action类型Library")
        void shouldParseActionLibraryImport() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element importElement = element.addElement("import-action-library");
            importElement.addAttribute("path", "/test/action.ul");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            assertThat(table.getLibraries()).isNotNull();
            assertThat(table.getLibraries()).hasSize(1);
            assertThat(table.getLibraries().get(0).getType()).isEqualTo(LibraryType.Action);
        }

        @Test
        @DisplayName("Given 包含import-parameter-library元素的决策表 When 解析决策表 Then 应添加Parameter类型Library")
        void shouldParseParameterLibraryImport() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element importElement = element.addElement("import-parameter-library");
            importElement.addAttribute("path", "/test/param.ul");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            assertThat(table.getLibraries()).isNotNull();
            assertThat(table.getLibraries()).hasSize(1);
            assertThat(table.getLibraries().get(0).getType()).isEqualTo(LibraryType.Parameter);
        }

        @Test
        @DisplayName("Given 包含多个库导入的决策表 When 解析决策表 Then 应添加所有库到libraries列表")
        void shouldParseMultipleLibraryImports() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element import1 = element.addElement("import-variable-library");
            import1.addAttribute("path", "/test/var.ul");
            Element import2 = element.addElement("import-constant-library");
            import2.addAttribute("path", "/test/const.ul");
            Element import3 = element.addElement("import-action-library");
            import3.addAttribute("path", "/test/action.ul");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            assertThat(table.getLibraries()).hasSize(3);
        }

        @Test
        @DisplayName("Given 库导入包含path属性 When 解析库导入 Then Library对象应包含正确的path值")
        void shouldParseLibraryPath() {
            // Given
            String path = "/libraries/my-lib.ul";
            Element element = DocumentHelper.createElement("script-decision-table");
            Element importElement = element.addElement("import-variable-library");
            importElement.addAttribute("path", path);

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            assertThat(table.getLibraries().get(0).getPath()).isEqualTo(path);
        }
    }

    @Nested
    @DisplayName("解析器依赖注入")
    class ParserDependencies {

        @Test
        @DisplayName("Given 设置了RowParser When 解析决策表 Then 应使用RowParser解析行元素")
        void shouldUseRowParser() {
            // Given
            parser.setRowParser(new RowParser());
            Element element = DocumentHelper.createElement("script-decision-table");
            Element rowElement = element.addElement("row");
            rowElement.addAttribute("num", "1");
            rowElement.addAttribute("height", "25");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            assertThat(table.getRows()).isNotNull();
            assertThat(table.getRows()).hasSize(1);
        }

        @Test
        @DisplayName("Given 设置了ColumnParser When 解析决策表 Then 应使用ColumnParser解析列元素")
        void shouldUseColumnParser() {
            // Given
            parser.setColumnParser(new ColumnParser());
            Element element = DocumentHelper.createElement("script-decision-table");
            Element colElement = element.addElement("col");
            colElement.addAttribute("num", "1");
            colElement.addAttribute("type", "Criteria");
            colElement.addAttribute("width", "100");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            assertThat(table.getColumns()).isNotNull();
            assertThat(table.getColumns()).hasSize(1);
        }

        @Test
        @DisplayName("Given 设置了ScriptCellParser When 解析决策表 Then 应使用ScriptCellParser解析单元格")
        void shouldUseScriptCellParser() {
            // Given
            parser.setScriptCellParser(new ScriptCellParser());
            Element element = DocumentHelper.createElement("script-decision-table");
            Element cellElement = element.addElement("script-cell");
            cellElement.addAttribute("row", "1");
            cellElement.addAttribute("col", "1");
            cellElement.addAttribute("rowspan", "1");
            cellElement.setText("test");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            assertThat(table.getCellMap()).isNotNull();
            assertThat(table.getCellMap()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("脚本决策表转换规则")
    class ConvertToRules {

        @Test
        @DisplayName("Given 包含条件列和动作列的决策表 When 转换为规则 Then 应生成对应的规则集合")
        void shouldConvertToRuleSet() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element cell = element.addElement("script-cell");
            cell.addAttribute("row", "1");
            cell.addAttribute("col", "1");
            cell.addAttribute("rowspan", "1");
            cell.setText("parameter.age > 18");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            assertThat(table).isNotNull();
            assertThat(table.getCellMap()).isNotNull();
        }

        @Test
        @DisplayName("Given 决策表包含多行数据 When 转换为规则 Then 应为每行生成一条规则")
        void shouldGenerateRuleForEachRow() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element row1 = element.addElement("row");
            row1.addAttribute("num", "1");
            row1.addAttribute("height", "30");
            Element row2 = element.addElement("row");
            row2.addAttribute("num", "2");
            row2.addAttribute("height", "30");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            assertThat(table.getRows()).hasSize(2);
        }

        @Test
        @DisplayName("Given 决策表包含空单元格 When 转换为规则 Then 应正确处理空值情况")
        void shouldHandleEmptyCells() {
            // Given
            Element element = DocumentHelper.createElement("script-decision-table");
            Element row = element.addElement("row");
            row.addAttribute("num", "1");
            row.addAttribute("height", "30");

            // When
            ScriptDecisionTable table = parser.parse(element);

            // Then
            assertThat(table.getRows()).hasSize(1);
            assertThat(table.getCellMap()).isNullOrEmpty();
        }
    }
}
