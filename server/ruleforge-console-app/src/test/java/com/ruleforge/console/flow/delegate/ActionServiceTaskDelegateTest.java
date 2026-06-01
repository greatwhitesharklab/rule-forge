package com.ruleforge.console.flow.delegate;

import com.ruleforge.Utils;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Feature: 动作节点执行
 */
@DisplayName("ActionServiceTaskDelegate - 动作节点执行")
class ActionServiceTaskDelegateTest {

    private static final String RF_NS = "http://ruleforge.com/schema";

    private ActionServiceTaskDelegate delegate;
    private MockedStatic<Utils> utilsMock;
    private ApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        delegate = new ActionServiceTaskDelegate();
        utilsMock = mockStatic(Utils.class);
        applicationContext = mock(ApplicationContext.class);
        utilsMock.when(Utils::getApplicationContext).thenReturn(applicationContext);
    }

    @AfterEach
    void tearDown() {
        utilsMock.close();
    }

    private void addExtensionAttr(ServiceTask task, String name, String value) {
        ExtensionAttribute attr = new ExtensionAttribute(name, value);
        attr.setNamespace(RF_NS);
        attr.setNamespacePrefix("ruleforge");
        task.addAttribute(attr);
    }

    private DelegateExecution createExecution(String bean, String method, Map<String, Object> variables) {
        DelegateExecution execution = mock(DelegateExecution.class);
        ServiceTask task = new ServiceTask();
        if (bean != null) {
            addExtensionAttr(task, "bean", bean);
        }
        if (method != null) {
            addExtensionAttr(task, "method", method);
        }
        when(execution.getCurrentFlowElement()).thenReturn(task);
        when(execution.getCurrentActivityId()).thenReturn("testActivity");
        when(execution.getVariables()).thenReturn(variables != null ? variables : new HashMap<>());
        return execution;
    }

    public static class TestActionBean {
        public Map<String, Object> process(Map<String, Object> vars) {
            Map<String, Object> result = new HashMap<>(vars);
            result.put("processed", true);
            return result;
        }

        public void init() {
            // no-op
        }

        public void fail() {
            throw new IllegalStateException("intentional failure");
        }
    }

    @Nested
    @DisplayName("调用 Spring Bean 的方法")
    class InvokeBeanMethod {

        @Test
        @DisplayName("调用带 Map 参数的方法并写回返回值")
        void shouldInvokeMethodWithMapParamAndWriteBack() {
            // Given BPMN 中有 serviceTask，扩展属性 bean="myBean" method="process"
            // And Spring 容器中存在 myBean 并有 process(Map) 方法
            TestActionBean testBean = spy(new TestActionBean());
            when(applicationContext.getBean("myBean")).thenReturn(testBean);

            Map<String, Object> variables = new HashMap<>();
            variables.put("input", "test");
            DelegateExecution execution = createExecution("myBean", "process", variables);

            // When Flowable 执行到该 serviceTask
            delegate.execute(execution);

            // Then 应调用 myBean.process(流程变量)
            verify(testBean).process(any(Map.class));
            // And 若返回 Map 则写回流程变量
            verify(execution).setVariables(any(Map.class));
        }

        @Test
        @DisplayName("方法不存在时回退到无参方法")
        void shouldFallbackToNoArgMethod() {
            // Given BPMN 中有 serviceTask，扩展属性 bean="myBean" method="init"
            // And myBean 没有 process(Map) 方法但有 init() 无参方法
            TestActionBean testBean = spy(new TestActionBean());
            when(applicationContext.getBean("myBean")).thenReturn(testBean);

            DelegateExecution execution = createExecution("myBean", "init", new HashMap<>());

            // When Flowable 执行到该 serviceTask
            delegate.execute(execution);

            // Then 应调用 myBean.init()
            verify(testBean).init();
        }
    }

    @Nested
    @DisplayName("Bean 未指定或不存在")
    class BeanNotFound {

        @Test
        @DisplayName("bean 为空时应跳过执行")
        void shouldSkipWhenBeanIsEmpty() {
            // Given BPMN 中有 serviceTask，扩展属性 bean 为空
            DelegateExecution execution = createExecution("", "process", new HashMap<>());

            // When Flowable 执行到该 serviceTask
            delegate.execute(execution);

            // Then 应跳过执行
            verify(applicationContext, never()).getBean(anyString());
        }

        @Test
        @DisplayName("Bean 不存在时应抛出异常")
        void shouldThrowWhenBeanNotFound() {
            // Given BPMN 中有 serviceTask，扩展属性 bean="missingBean"
            // And Spring 容器中不存在该 Bean
            when(applicationContext.getBean("missingBean")).thenThrow(new RuntimeException("No bean"));

            DelegateExecution execution = createExecution("missingBean", "process", new HashMap<>());

            // When Flowable 执行到该 serviceTask
            // Then 应抛出异常
            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("方法执行失败")
    class MethodInvocationFailure {

        @Test
        @DisplayName("方法执行异常应包装为 RuntimeException")
        void shouldWrapInvocationException() {
            // Given BPMN 中有 serviceTask，扩展属性 bean="myBean" method="fail"
            // And myBean.fail() 执行时抛出异常
            TestActionBean testBean = new TestActionBean();
            when(applicationContext.getBean("myBean")).thenReturn(testBean);

            DelegateExecution execution = createExecution("myBean", "fail", new HashMap<>());

            // When Flowable 执行到该 serviceTask
            // Then 应抛出 RuntimeException，异常消息包含 bean 名和方法名
            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("myBean")
                .hasMessageContaining("fail");
        }
    }
}
