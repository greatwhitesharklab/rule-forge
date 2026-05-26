package com.ruleforge.dsl;

import com.ruleforge.builder.resource.Resource;
import com.ruleforge.dsl.builder.ActionContextBuilder;
import com.ruleforge.dsl.builder.ContextBuilder;
import com.ruleforge.dsl.builder.CriteriaContextBuilder;
import com.ruleforge.dsl.builder.LibraryContextBuilder;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.rule.Library;
import com.ruleforge.model.rule.LibraryType;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.RuleSet;
import com.ruleforge.builder.RulesRebuilder;
import org.junit.jupiter.api.*;
import java.util.ArrayList;
import java.util.Collection;
import java.lang.reflect.Field;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("脚本式规则集(UL)构建器")
class DSLRuleSetBuilderTest {

    private DSLRuleSetBuilder builder;

    @BeforeEach
    void setUp() throws Exception {
        builder = new DSLRuleSetBuilder();
        builder.setRulesRebuilder(mock(RulesRebuilder.class));
        // Set up contextBuilders with real implementations since we're not in Spring context
        Field field = DSLRuleSetBuilder.class.getDeclaredField("contextBuilders");
        field.setAccessible(true);
        Collection<ContextBuilder> contextBuilders = new ArrayList<>();
        contextBuilders.add(new CriteriaContextBuilder());
        contextBuilders.add(new ActionContextBuilder());
        contextBuilders.add(new LibraryContextBuilder());
        field.set(builder, contextBuilders);
    }

    @Nested
    @DisplayName("UL脚本解析")
    class ParseUlScript {

        @Test
        @DisplayName("Given 包含规则定义的UL脚本内容 When 调用build方法 Then 应返回包含规则的RuleSet对象")
        void shouldBuildRuleSetFromUlScript() {
            // Given
            String ulScript = "rule \"test-rule\" if parameter.age > 18 then out(\"adult\") end";

            // When
            RuleSet ruleSet = builder.build(ulScript);

            // Then
            assertThat(ruleSet).isNotNull();
        }

        @Test
        @DisplayName("Given UL脚本文件后缀为.ul When 调用support方法 Then 应返回true")
        void shouldSupportUlFileExtension() {
            // Given
            Resource resource = mock(Resource.class);
            when(resource.getPath()).thenReturn("/test/rules/test.ul");

            // When
            boolean supported = builder.support(resource);

            // Then
            assertThat(supported).isTrue();
        }

        @Test
        @DisplayName("Given 非UL后缀的文件资源 When 调用support方法 Then 应返回false")
        void shouldNotSupportNonUlFileExtension() {
            // Given
            Resource resource = mock(Resource.class);
            when(resource.getPath()).thenReturn("/test/rules/test.xml");

            // When
            boolean supported = builder.support(resource);

            // Then
            assertThat(supported).isFalse();
        }

        @Test
        @DisplayName("Given 语法错误的UL脚本 When 调用build方法 Then 应抛出异常")
        void shouldThrowRuleExceptionOnSyntaxError() {
            // Given
            String invalidScript = "rule \"test\" if parameter. > then end";

            // When & Then
            assertThatThrownBy(() -> builder.build(invalidScript))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("Given 包含词法错误的UL脚本 When 调用build方法 Then 应抛出异常")
        void shouldThrowRuleExceptionOnLexerError() {
            // Given
            String invalidScript = "rule \"test\" @@# invalid syntax end";

            // When & Then
            assertThatThrownBy(() -> builder.build(invalidScript))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("脚本条件求值")
    class ScriptConditionEvaluation {

        @Test
        @DisplayName("Given UL脚本中包含简单条件表达式 When 解析脚本 Then 规则的LHS应包含对应的条件")
        void shouldParseSimpleConditionExpression() {
            // Given
            String script = "rule \"simple-rule\" if parameter.age > 18 then out(\"adult\") end";

            // When
            RuleSet ruleSet = builder.build(script);

            // Then
            assertThat(ruleSet.getRules()).isNotNull();
            assertThat(ruleSet.getRules()).hasSize(1);
        }

        @Test
        @DisplayName("Given UL脚本中包含复合条件表达式(AND/OR) When 解析脚本 Then 规则的LHS应包含正确的条件树")
        void shouldParseComplexConditionExpression() {
            // Given
            String script = "rule \"complex-rule\" if parameter.age > 18 and parameter.score > 60 then out(\"pass\") end";

            // When
            RuleSet ruleSet = builder.build(script);

            // Then
            assertThat(ruleSet.getRules()).hasSize(1);
            assertThat(ruleSet.getRules().get(0).getName()).isEqualTo("complex-rule");
        }

        @Test
        @DisplayName("Given UL脚本中包含比较运算符 When 解析脚本 Then 应正确解析比较条件")
        void shouldParseComparisonOperators() {
            // Given
            String script = "rule \"compare-rule\" if parameter.age >= 18 then out(\"valid\") end";

            // When
            RuleSet ruleSet = builder.build(script);

            // Then
            assertThat(ruleSet.getRules()).hasSize(1);
        }

        @Test
        @DisplayName("Given UL脚本中包含括号分组条件 When 解析脚本 Then 应正确解析条件优先级")
        void shouldParseGroupedConditions() {
            // Given
            String script = "rule \"group-rule\" if (parameter.age > 18 or parameter.age < 10) and parameter.score > 50 then out(\"special\") end";

            // When
            RuleSet ruleSet = builder.build(script);

            // Then
            assertThat(ruleSet.getRules()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("脚本动作执行")
    class ScriptActionExecution {

        @Test
        @DisplayName("Given UL脚本中包含赋值动作 When 解析脚本 Then 规则的RHS应包含赋值动作")
        void shouldParseAssignmentAction() {
            // Given
            String script = "rule \"assign-rule\" if parameter.age > 18 then variable.result = \"adult\" end";

            // When
            RuleSet ruleSet = builder.build(script);

            // Then
            assertThat(ruleSet.getRules()).hasSize(1);
            assertThat(ruleSet.getRules().get(0).getRhs()).isNotNull();
        }

        @Test
        @DisplayName("Given UL脚本中包含方法调用动作 When 解析脚本 Then 规则的RHS应包含方法调用动作")
        void shouldParseMethodInvocationAction() {
            // Given - using bean method invocation syntax
            String script = "rule \"method-rule\" if parameter.age > 18 then bean.method(\"adult\") end";

            // When
            RuleSet ruleSet = builder.build(script);

            // Then
            assertThat(ruleSet.getRules()).hasSize(1);
        }

        @Test
        @DisplayName("Given UL脚本中包含多个动作 When 解析脚本 Then 规则的RHS应包含所有动作")
        void shouldParseMultipleActions() {
            // Given
            String script = "rule \"multi-action-rule\" if parameter.age > 18 then out(\"adult\") variable.result = \"yes\" end";

            // When
            RuleSet ruleSet = builder.build(script);

            // Then
            assertThat(ruleSet.getRules()).hasSize(1);
        }

        @Test
        @DisplayName("Given UL脚本中包含控制台打印动作 When 解析脚本 Then 规则的RHS应包含ConsolePrint动作")
        void shouldParseConsolePrintAction() {
            // Given
            String script = "rule \"print-rule\" if parameter.age > 18 then out(\"Hello\") end";

            // When
            RuleSet ruleSet = builder.build(script);

            // Then
            assertThat(ruleSet.getRules()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("脚本变量和库")
    class ScriptVariablesAndLibraries {

        @Test
        @DisplayName("Given UL脚本中包含变量声明 When 解析脚本 Then 应正确解析变量定义")
        void shouldParseVariableDeclaration() {
            // Given
            String script = "rule \"var-rule\" if variable.status == \"active\" then out(\"active\") end";

            // When
            RuleSet ruleSet = builder.build(script);

            // Then
            assertThat(ruleSet.getRules()).hasSize(1);
        }

        @Test
        @DisplayName("Given UL脚本中包含变量赋值 When 解析脚本 Then 应正确解析赋值表达式")
        void shouldParseVariableAssignment() {
            // Given
            String script = "rule \"assign-var-rule\" if parameter.age > 18 then variable.result = parameter.age end";

            // When
            RuleSet ruleSet = builder.build(script);

            // Then
            assertThat(ruleSet.getRules()).hasSize(1);
        }

        @Test
        @DisplayName("Given UL脚本中导入变量库 When 构建规则集 Then RuleSet应包含Variable类型Library")
        void shouldImportVariableLibrary() {
            // Given
            String script = "importVariableLibrary \"/test/var.ul\" rule \"test\" if parameter.age > 18 then out(\"test\") end";

            // When
            RuleSet ruleSet = builder.build(script);

            // Then
            assertThat(ruleSet.getLibraries()).isNotNull();
            boolean hasVarLib = ruleSet.getLibraries().stream()
                    .anyMatch(lib -> lib.getType() == LibraryType.Variable);
            assertThat(hasVarLib).isTrue();
        }

        @Test
        @DisplayName("Given UL脚本中导入常量库 When 构建规则集 Then RuleSet应包含Constant类型Library")
        void shouldImportConstantLibrary() {
            // Given
            String script = "importConstantLibrary \"/test/const.ul\" rule \"test\" if parameter.age > 18 then out(\"test\") end";

            // When
            RuleSet ruleSet = builder.build(script);

            // Then
            assertThat(ruleSet.getLibraries()).isNotNull();
            boolean hasConstLib = ruleSet.getLibraries().stream()
                    .anyMatch(lib -> lib.getType() == LibraryType.Constant);
            assertThat(hasConstLib).isTrue();
        }

        @Test
        @DisplayName("Given UL脚本中导入动作库 When 构建规则集 Then RuleSet应包含Action类型Library")
        void shouldImportActionLibrary() {
            // Given
            String script = "importActionLibrary \"/test/action.ul\" rule \"test\" if parameter.age > 18 then out(\"test\") end";

            // When
            RuleSet ruleSet = builder.build(script);

            // Then
            assertThat(ruleSet.getLibraries()).isNotNull();
            boolean hasActionLib = ruleSet.getLibraries().stream()
                    .anyMatch(lib -> lib.getType() == LibraryType.Action);
            assertThat(hasActionLib).isTrue();
        }

        @Test
        @DisplayName("Given UL脚本中导入参数库 When 构建规则集 Then RuleSet应包含Parameter类型Library")
        void shouldImportParameterLibrary() {
            // Given
            String script = "importParameterLibrary \"/test/param.ul\" rule \"test\" if parameter.age > 18 then out(\"test\") end";

            // When
            RuleSet ruleSet = builder.build(script);

            // Then
            assertThat(ruleSet.getLibraries()).isNotNull();
            boolean hasParamLib = ruleSet.getLibraries().stream()
                    .anyMatch(lib -> lib.getType() == LibraryType.Parameter);
            assertThat(hasParamLib).isTrue();
        }
    }

    @Nested
    @DisplayName("规则重建")
    class RuleRebuilding {

        @Test
        @DisplayName("Given 包含库引用的规则集 When 调用rebuildRuleSet方法 Then 应重建规则中的库引用")
        void shouldRebuildRulesWithLibraries() {
            // Given
            String script = "importVariableLibrary \"/test/var.ul\" rule \"test\" if parameter.age > 18 then out(\"test\") end";
            RulesRebuilder mockRebuilder = mock(RulesRebuilder.class);
            builder.setRulesRebuilder(mockRebuilder);

            // When
            RuleSet ruleSet = builder.build(script);

            // Then
            assertThat(ruleSet).isNotNull();
            verify(mockRebuilder, times(1)).rebuildRulesForDSL(any(), any());
        }

        @Test
        @DisplayName("Given 规则集中包含多个规则 When 重建规则 Then 所有规则都应正确处理库引用")
        void shouldRebuildMultipleRules() {
            // Given
            String script = "importVariableLibrary \"/test/var.ul\" " +
                    "rule \"rule1\" if parameter.age > 18 then out(\"adult\") end " +
                    "rule \"rule2\" if parameter.score > 60 then out(\"pass\") end";
            RulesRebuilder mockRebuilder = mock(RulesRebuilder.class);
            builder.setRulesRebuilder(mockRebuilder);

            // When
            RuleSet ruleSet = builder.build(script);

            // Then
            assertThat(ruleSet.getRules()).hasSize(2);
            verify(mockRebuilder, times(1)).rebuildRulesForDSL(any(), any());
        }
    }

    @Nested
    @DisplayName("Spring集成")
    class SpringIntegration {

        @Test
        @DisplayName("Given DSLRuleSetBuilder的BEAN_ID When 查找Bean Then 应返回ruleforge.dslRuleSetBuilder")
        void shouldHaveCorrectBeanId() {
            // Given & When & Then
            assertThat(DSLRuleSetBuilder.BEAN_ID).isEqualTo("ruleforge.dslRuleSetBuilder");
        }
    }
}
