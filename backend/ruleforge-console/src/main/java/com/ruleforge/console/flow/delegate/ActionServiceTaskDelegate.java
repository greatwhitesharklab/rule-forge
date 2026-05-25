package com.ruleforge.console.flow.delegate;

import com.ruleforge.Utils;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;

@Component
@Slf4j
public class ActionServiceTaskDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        String beanId = execution.getCurrentFlowElement().getAttributeValue(
                "http://ruleforge.com/schema", "bean");
        String methodName = execution.getCurrentFlowElement().getAttributeValue(
                "http://ruleforge.com/schema", "method");

        if (beanId == null || beanId.isEmpty()) {
            log.warn("No action bean specified for service task: {}", execution.getCurrentActivityId());
            return;
        }

        ApplicationContext ctx = Utils.getApplicationContext();
        Object bean = ctx.getBean(beanId);
        if (bean == null) {
            log.error("Action bean not found: {}", beanId);
            return;
        }

        try {
            if (methodName != null && !methodName.isEmpty()) {
                Method method = findMethod(bean.getClass(), methodName, Map.class);
                if (method != null) {
                    Object result = method.invoke(bean, execution.getVariables());
                    if (result instanceof Map) {
                        execution.setVariables((Map<String, Object>) result);
                    }
                } else {
                    Method noArgMethod = bean.getClass().getMethod(methodName);
                    noArgMethod.invoke(bean);
                }
            }
        } catch (Exception e) {
            log.error("Failed to execute action bean: {}.{}", beanId, methodName, e);
            throw new RuntimeException("Action execution failed: " + beanId + "." + methodName, e);
        }
    }

    private Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
