package com.ruleforge.console.flow.delegate;

import org.flowable.bpmn.model.ScriptTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Feature: 脚本节点执行
 *
 * ScriptServiceTaskDelegate 负责执行 BPMN scriptTask 节点中的脚本内容。
 * 脚本内容由 FlowXmlConverter 从旧格式 <script> 节点的文本内容转换而来，
 * 存储在 BPMN 元素的 bpmn:script 子元素中。
 *
 * 支持的脚本语言：Groovy（默认）、JavaScript、Python 等 JSR-223 引擎。
 */
@DisplayName("ScriptServiceTaskDelegate - 脚本节点执行")
class ScriptServiceTaskDelegateTest {

    private ScriptServiceTaskDelegate delegate;
    private ScriptEngineManager engineManager;
    private ScriptEngine scriptEngine;

    @BeforeEach
    void setUp() {
        delegate = new ScriptServiceTaskDelegate();
        engineManager = mock(ScriptEngineManager.class);
        scriptEngine = mock(ScriptEngine.class);
        when(engineManager.getEngineByName("groovy")).thenReturn(scriptEngine);
        when(engineManager.getEngineByName("javascript")).thenReturn(scriptEngine);
        delegate.setScriptEngineManager(engineManager);
    }

    private DelegateExecution createExecution(String script, String scriptFormat, Map<String, Object> variables) {
        DelegateExecution execution = mock(DelegateExecution.class);
        ScriptTask task = new ScriptTask();
        task.setScript(script);
        if (scriptFormat != null) {
            task.setScriptFormat(scriptFormat);
        }
        when(execution.getCurrentFlowElement()).thenReturn(task);
        when(execution.getCurrentActivityId()).thenReturn("scriptTask_1");
        when(execution.getVariables()).thenReturn(variables != null ? variables : new HashMap<>());
        return execution;
    }

    @Nested
    @DisplayName("正常脚本执行")
    class NormalScriptExecution {

        @Test
        @DisplayName("执行简单 Groovy 脚本并写回结果")
        void shouldExecuteGroovyScriptAndWriteBackResults() throws Exception {
            // Given BPMN 中有 scriptTask，包含脚本 "parameters.put('score', 85)"
            //     And 脚本语言为 Groovy
            //     And DelegateExecution 包含流程变量
            //     And ScriptEngine 执行脚本，parameters 中添加 score=85
            doAnswer(invocation -> {
                ScriptContext ctx = invocation.getArgument(1);
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) ctx.getAttribute("parameters");
                params.put("score", 85);
                return null;
            }).when(scriptEngine).eval(eq("parameters.put('score', 85)"), any(ScriptContext.class));

            Map<String, Object> variables = new HashMap<>();
            DelegateExecution execution = createExecution(
                "parameters.put('score', 85)", "groovy", variables);

            // When Flowable 执行到该 scriptTask
            delegate.execute(execution);

            // Then 应执行脚本并写回参数
            verify(scriptEngine).eval(eq("parameters.put('score', 85)"), any(ScriptContext.class));
            verify(execution).setVariables(any(Map.class));
        }

        @Test
        @DisplayName("脚本中可读取流程变量")
        void shouldAccessProcessVariablesInScript() throws Exception {
            // Given BPMN 中有 scriptTask
            //     And DelegateExecution 包含流程变量 input="hello"
            doAnswer(invocation -> {
                ScriptContext ctx = invocation.getArgument(1);
                @SuppressWarnings("unchecked")
                Map<String, Object> vars = (Map<String, Object>) ctx.getAttribute("variables");
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) ctx.getAttribute("parameters");
                params.put("result", vars.get("input"));
                return null;
            }).when(scriptEngine).eval(anyString(), any(ScriptContext.class));
            // Use anyString matcher to match the script content
            doAnswer(invocation -> {
                ScriptContext ctx = invocation.getArgument(1);
                @SuppressWarnings("unchecked")
                Map<String, Object> vars = (Map<String, Object>) ctx.getAttribute("variables");
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) ctx.getAttribute("parameters");
                params.put("result", vars.get("input"));
                return null;
            }).when(scriptEngine).eval(anyString(), any(ScriptContext.class));

            Map<String, Object> variables = new HashMap<>();
            variables.put("input", "hello");
            DelegateExecution execution = createExecution(
                "parameters.put('result', variables.get('input'))", "groovy", variables);

            // When 执行脚本
            delegate.execute(execution);

            // Then 脚本可访问 variables，结果应写回
            verify(execution).setVariables(argThat(map -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) map;
                return "hello".equals(m.get("result"));
            }));
        }

        @Test
        @DisplayName("脚本中可进行复杂计算")
        void shouldExecuteComplexCalculationScript() throws Exception {
            // Given BPMN 中有 scriptTask，包含脚本计算逻辑
            doAnswer(invocation -> {
                ScriptContext ctx = invocation.getArgument(1);
                @SuppressWarnings("unchecked")
                Map<String, Object> vars = (Map<String, Object>) ctx.getAttribute("variables");
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) ctx.getAttribute("parameters");
                double amount = ((Number) vars.get("amount")).doubleValue();
                double rate = ((Number) vars.get("rate")).doubleValue();
                params.put("interest", amount * rate);
                return null;
            }).when(scriptEngine).eval(anyString(), any(ScriptContext.class));

            Map<String, Object> variables = new HashMap<>();
            variables.put("amount", 1000);
            variables.put("rate", 0.05);
            DelegateExecution execution = createExecution(
                "parameters.put('interest', variables.get('amount') * variables.get('rate'))",
                "groovy", variables);

            // When 执行脚本
            delegate.execute(execution);

            // Then 计算结果应正确写回流程变量
            verify(execution).setVariables(argThat(map -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) map;
                Object interest = m.get("interest");
                return interest != null && ((Number) interest).doubleValue() == 50.0;
            }));
        }
    }

    @Nested
    @DisplayName("脚本语言指定")
    class ScriptLanguage {

        @Test
        @DisplayName("未指定脚本语言时默认使用 Groovy")
        void shouldDefaultToGroovyWhenNoLanguageSpecified() throws Exception {
            // Given BPMN 中有 scriptTask，scriptFormat 为空
            //     And 脚本内容为 Groovy 代码
            doAnswer(invocation -> {
                ScriptContext ctx = invocation.getArgument(1);
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) ctx.getAttribute("parameters");
                params.put("default", true);
                return null;
            }).when(scriptEngine).eval(anyString(), any(ScriptContext.class));

            Map<String, Object> variables = new HashMap<>();
            DelegateExecution execution = createExecution(
                "parameters.put('default', true)", null, variables);

            // When 执行脚本
            delegate.execute(execution);

            // Then 应使用 Groovy 引擎执行
            verify(engineManager).getEngineByName("groovy");
            verify(execution).setVariables(any(Map.class));
        }

        @Test
        @DisplayName("指定 JavaScript 脚本语言")
        void shouldUseJavaScriptEngineWhenSpecified() throws Exception {
            // Given BPMN 中有 scriptTask，scriptFormat="javascript"
            doAnswer(invocation -> {
                ScriptContext ctx = invocation.getArgument(1);
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (Map<String, Object>) ctx.getAttribute("parameters");
                params.put("jsResult", 42);
                return null;
            }).when(scriptEngine).eval(anyString(), any(ScriptContext.class));

            Map<String, Object> variables = new HashMap<>();
            DelegateExecution execution = createExecution(
                "parameters.put('jsResult', 42)", "javascript", variables);

            // When 执行脚本
            delegate.execute(execution);

            // Then 应使用 JavaScript 引擎执行
            verify(engineManager).getEngineByName("javascript");
            verify(execution).setVariables(any(Map.class));
        }
    }

    @Nested
    @DisplayName("脚本为空或缺失")
    class EmptyScript {

        @Test
        @DisplayName("脚本内容为空时应跳过执行")
        void shouldSkipWhenScriptContentIsEmpty() {
            // Given BPMN 中有 scriptTask，脚本内容为空字符串
            DelegateExecution execution = createExecution("", "groovy", new HashMap<>());

            // When Flowable 执行到该 scriptTask
            delegate.execute(execution);

            // Then 应跳过执行（不抛异常，不写回变量）
            verify(execution, never()).setVariables(any(Map.class));
        }

        @Test
        @DisplayName("脚本内容为 null 时应跳过执行")
        void shouldSkipWhenScriptContentIsNull() {
            // Given BPMN 中有 scriptTask，脚本内容为 null
            DelegateExecution execution = createExecution(null, "groovy", new HashMap<>());

            // When Flowable 执行到该 scriptTask
            delegate.execute(execution);

            // Then 应跳过执行
            verify(execution, never()).setVariables(any(Map.class));
        }
    }

    @Nested
    @DisplayName("脚本执行异常")
    class ScriptExecutionFailure {

        @Test
        @DisplayName("脚本语法错误应抛出 RuntimeException")
        void shouldThrowOnScriptSyntaxError() throws Exception {
            // Given BPMN 中有 scriptTask，包含语法错误的脚本
            when(scriptEngine.eval(anyString(), any(ScriptContext.class)))
                .thenThrow(new javax.script.ScriptException("syntax error"));

            DelegateExecution execution = createExecution(
                "invalid { groovy } code !!!", "groovy", new HashMap<>());

            // When Flowable 执行到该 scriptTask
            // Then 应抛出 RuntimeException
            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("scriptTask_1");
        }

        @Test
        @DisplayName("脚本运行时异常应包装为 RuntimeException")
        void shouldWrapRuntimeException() throws Exception {
            // Given BPMN 中有 scriptTask，脚本抛出运行时异常
            when(scriptEngine.eval(anyString(), any(ScriptContext.class)))
                .thenThrow(new RuntimeException("test error"));

            Map<String, Object> variables = new HashMap<>();
            DelegateExecution execution = createExecution(
                "throw new RuntimeException('test error')", "groovy", variables);

            // When 执行脚本
            // Then 应抛出 RuntimeException
            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("scriptTask_1");
        }
    }
}
