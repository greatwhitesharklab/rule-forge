package com.ruleforge.parse.decisiontree;

import com.ruleforge.Configure;
import com.ruleforge.builder.ResourceLibraryBuilder;
import com.ruleforge.builder.RulesRebuilder;
import com.ruleforge.model.decisiontree.ActionTreeNode;
import com.ruleforge.model.decisiontree.ConditionTreeNode;
import com.ruleforge.model.decisiontree.DecisionTree;
import com.ruleforge.model.decisiontree.VariableTreeNode;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.rule.Library;
import com.ruleforge.model.rule.LibraryType;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.jupiter.api.*;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("决策树解析器")
class DecisionTreeParserTest {

    private DecisionTreeParser createParser() {
        Configure configure = new Configure();
        configure.setDateFormat("yyyy-MM-dd HH:mm:ss");
        DecisionTreeParser parser = new DecisionTreeParser();

        RulesRebuilder rulesRebuilder = mock(RulesRebuilder.class);
        ResourceLibraryBuilder rlBuilder = mock(ResourceLibraryBuilder.class);
        ResourceLibrary emptyResourceLibrary = new ResourceLibrary(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        when(rulesRebuilder.getResourceLibraryBuilder()).thenReturn(rlBuilder);
        when(rlBuilder.buildResourceLibrary(any())).thenReturn(emptyResourceLibrary);
        when(rulesRebuilder.getVariableByName(any(), any(), any(), any())).thenReturn(new Variable());
        parser.setRulesRebuilder(rulesRebuilder);

        VariableTreeNodeParser varNodeParser = new VariableTreeNodeParser();
        ConditionTreeNodeParser condNodeParser = new ConditionTreeNodeParser();
        ActionTreeNodeParser actionNodeParser = new ActionTreeNodeParser();

        com.ruleforge.parse.LeftParser leftParser = new com.ruleforge.parse.LeftParser();
        com.ruleforge.parse.ValueParser valueParser = new com.ruleforge.parse.ValueParser();
        valueParser.setArithmeticParser(new com.ruleforge.parse.ComplexArithmeticParser());
        leftParser.setValueParser(valueParser);
        leftParser.setComplexArithmeticParser(new com.ruleforge.parse.ComplexArithmeticParser());
        leftParser.setSimpleArithmeticParser(new com.ruleforge.parse.SimpleArithmeticParser());

        varNodeParser.setLeftParser(leftParser);
        varNodeParser.setConditionTreeNodeParser(condNodeParser);
        condNodeParser.setValueParser(valueParser);
        condNodeParser.setActionTreeNodeParser(actionNodeParser);
        condNodeParser.setVariableTreeNodeParser(varNodeParser);

        parser.setVariableTreeNodeParser(varNodeParser);
        return parser;
    }

    @Nested
    @DisplayName("解析决策树XML")
    class ParseDecisionTreeXml {

        @Test
        @DisplayName("Given 包含变量节点和条件节点的决策树XML When 解析决策树 Then 应返回正确的DecisionTree对象")
        void shouldParseTreeWithVariableAndConditionNodes() {
            // Given
            String xml = "<decision-tree>" +
                    "  <variable-tree-node>" +
                    "    <condition-tree-node>" +
                    "    </condition-tree-node>" +
                    "  </variable-tree-node>" +
                    "</decision-tree>";
            DecisionTreeParser parser = createParser();

            Element root = parseXml(xml);

            // When
            DecisionTree tree = parser.parse(root);

            // Then
            assertThat(tree).isNotNull();
            assertThat(tree.getVariableTreeNode()).isNotNull();
            assertThat(tree.getVariableTreeNode().getConditionTreeNodes()).isNotEmpty();
        }

        @Test
        @DisplayName("Given 包含动作节点的决策树XML When 解析决策树 Then 动作节点应正确添加到树结构中")
        void shouldParseTreeWithActionNodes() {
            // Given
            String xml = "<decision-tree>" +
                    "  <variable-tree-node>" +
                    "    <condition-tree-node>" +
                    "      <action-tree-node>" +
                    "      </action-tree-node>" +
                    "    </condition-tree-node>" +
                    "  </variable-tree-node>" +
                    "</decision-tree>";
            DecisionTreeParser parser = createParser();

            Element root = parseXml(xml);

            // When
            DecisionTree tree = parser.parse(root);

            // Then
            assertThat(tree).isNotNull();
            assertThat(tree.getVariableTreeNode()).isNotNull();
            assertThat(tree.getVariableTreeNode().getConditionTreeNodes().get(0).getActionTreeNodes()).isNotEmpty();
        }

        @Test
        @DisplayName("Given 包含多层嵌套的决策树结构XML When 解析决策树 Then 应正确解析嵌套的父子关系")
        void shouldParseTreeWithNestedStructure() {
            // Given
            String xml = "<decision-tree>" +
                    "  <variable-tree-node>" +
                    "    <condition-tree-node>" +
                    "      <condition-tree-node>" +
                    "      </condition-tree-node>" +
                    "    </condition-tree-node>" +
                    "  </variable-tree-node>" +
                    "</decision-tree>";
            DecisionTreeParser parser = createParser();

            Element root = parseXml(xml);

            // When
            DecisionTree tree = parser.parse(root);

            // Then
            assertThat(tree).isNotNull();
            assertThat(tree.getVariableTreeNode()).isNotNull();
            assertThat(tree.getVariableTreeNode().getConditionTreeNodes().get(0).getConditionTreeNodes()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("解析决策树属性")
    class ParseDecisionTreeAttributes {

        @Test
        @DisplayName("Given 带有salience属性的决策树XML When 解析决策树 Then salience属性应正确设置")
        void shouldParseTreeWithSalience() {
            // Given
            String xml = "<decision-tree salience=\"100\">" +
                    "  <variable-tree-node/>" +
                    "</decision-tree>";
            DecisionTreeParser parser = createParser();

            Element root = parseXml(xml);

            // When
            DecisionTree tree = parser.parse(root);

            // Then
            assertThat(tree).isNotNull();
            assertThat(tree.getSalience()).isEqualTo(100);
        }

        @Test
        @DisplayName("Given 带有生效日期和过期日期的决策树XML When 解析决策树 Then 日期属性应正确解析")
        void shouldParseTreeWithEffectiveAndExpiresDate() {
            // Given
            String xml = "<decision-tree effective-date=\"2024-01-01 00:00:00\" expires-date=\"2024-12-31 00:00:00\">" +
                    "  <variable-tree-node/>" +
                    "</decision-tree>";
            DecisionTreeParser parser = createParser();

            Element root = parseXml(xml);

            // When
            DecisionTree tree = parser.parse(root);

            // Then
            assertThat(tree).isNotNull();
            assertThat(tree.getEffectiveDate()).isNotNull();
            assertThat(tree.getExpiresDate()).isNotNull();
        }
    }

    @Nested
    @DisplayName("解析库引用")
    class ParseLibraryReferences {

        @Test
        @DisplayName("Given 包含变量库引用的决策树XML When 解析决策树 Then 变量库应正确添加到libraries列表")
        void shouldParseTreeWithVariableLibrary() {
            // Given
            String xml = "<decision-tree>" +
                    "  <import-variable-library path=\"/path/to/var.lib\"/>" +
                    "  <variable-tree-node/>" +
                    "</decision-tree>";
            DecisionTreeParser parser = createParser();

            Element root = parseXml(xml);

            // When
            DecisionTree tree = parser.parse(root);

            // Then
            assertThat(tree).isNotNull();
            assertThat(tree.getLibraries()).isNotEmpty();
            assertThat(tree.getLibraries().get(0).getType()).isEqualTo(LibraryType.Variable);
        }

        @Test
        @DisplayName("Given 包含常量库引用的决策树XML When 解析决策树 Then 常量库应正确添加到libraries列表")
        void shouldParseTreeWithConstantLibrary() {
            // Given
            String xml = "<decision-tree>" +
                    "  <import-constant-library path=\"/path/to/const.lib\"/>" +
                    "  <variable-tree-node/>" +
                    "</decision-tree>";
            DecisionTreeParser parser = createParser();

            Element root = parseXml(xml);

            // When
            DecisionTree tree = parser.parse(root);

            // Then
            assertThat(tree).isNotNull();
            assertThat(tree.getLibraries()).isNotEmpty();
            assertThat(tree.getLibraries().get(0).getType()).isEqualTo(LibraryType.Constant);
        }

        @Test
        @DisplayName("Given 包含动作库引用的决策树XML When 解析决策树 Then 动作库应正确添加到libraries列表")
        void shouldParseTreeWithActionLibrary() {
            // Given
            String xml = "<decision-tree>" +
                    "  <import-action-library path=\"/path/to/action.lib\"/>" +
                    "  <variable-tree-node/>" +
                    "</decision-tree>";
            DecisionTreeParser parser = createParser();

            Element root = parseXml(xml);

            // When
            DecisionTree tree = parser.parse(root);

            // Then
            assertThat(tree).isNotNull();
            assertThat(tree.getLibraries()).isNotEmpty();
            assertThat(tree.getLibraries().get(0).getType()).isEqualTo(LibraryType.Action);
        }

        @Test
        @DisplayName("Given 包含参数库引用的决策树XML When 解析决策树 Then 参数库应正确添加到libraries列表")
        void shouldParseTreeWithParameterLibrary() {
            // Given
            String xml = "<decision-tree>" +
                    "  <import-parameter-library path=\"/path/to/param.lib\"/>" +
                    "  <variable-tree-node/>" +
                    "</decision-tree>";
            DecisionTreeParser parser = createParser();

            Element root = parseXml(xml);

            // When
            DecisionTree tree = parser.parse(root);

            // Then
            assertThat(tree).isNotNull();
            assertThat(tree.getLibraries()).isNotEmpty();
            assertThat(tree.getLibraries().get(0).getType()).isEqualTo(LibraryType.Parameter);
        }
    }

    @Nested
    @DisplayName("解析布尔属性")
    class ParseBooleanAttributes {

        @Test
        @DisplayName("Given enabled属性为true的决策树XML When 解析决策树 Then enabled属性应设置为true")
        void shouldParseTreeWithEnabledTrue() {
            // Given
            String xml = "<decision-tree enabled=\"true\">" +
                    "  <variable-tree-node/>" +
                    "</decision-tree>";
            DecisionTreeParser parser = createParser();

            Element root = parseXml(xml);

            // When
            DecisionTree tree = parser.parse(root);

            // Then
            assertThat(tree).isNotNull();
            assertThat(tree.getEnabled()).isTrue();
        }

        @Test
        @DisplayName("Given debug属性为true的决策树XML When 解析决策树 Then debug属性应设置为true")
        void shouldParseTreeWithDebugTrue() {
            // Given
            String xml = "<decision-tree debug=\"true\">" +
                    "  <variable-tree-node/>" +
                    "</decision-tree>";
            DecisionTreeParser parser = createParser();

            Element root = parseXml(xml);

            // When
            DecisionTree tree = parser.parse(root);

            // Then
            assertThat(tree).isNotNull();
            assertThat(tree.getDebug()).isTrue();
        }
    }

    @Nested
    @DisplayName("解析备注")
    class ParseRemark {

        @Test
        @DisplayName("Given 包含remark元素的决策树XML When 解析决策树 Then remark属性应正确设置")
        void shouldParseTreeWithRemark() {
            // Given
            String xml = "<decision-tree>" +
                    "  <remark>Test remark</remark>" +
                    "  <variable-tree-node/>" +
                    "</decision-tree>";
            DecisionTreeParser parser = createParser();

            Element root = parseXml(xml);

            // When
            DecisionTree tree = parser.parse(root);

            // Then
            assertThat(tree).isNotNull();
            assertThat(tree.getRemark()).isEqualTo("Test remark");
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
}
