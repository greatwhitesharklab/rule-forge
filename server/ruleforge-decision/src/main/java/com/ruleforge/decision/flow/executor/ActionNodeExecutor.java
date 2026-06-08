package com.ruleforge.decision.flow.executor;

import com.ruleforge.Utils;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Action 节点执行器(替代原 ActionServiceTaskDelegate)。
 * <p>
 * 反射调 Spring bean:
 * 1. 读 ruleforge:bean / ruleforge:method
 * 2. 优先调 method(Map),结果 Map 写回 ctx.vars
 * 3. 否则调无参 method
 */
@Slf4j
@Component
public class ActionNodeExecutor implements NodeExecutor {

    @Override
    public String supportedType() {
        return "SERVICE_TASK:action";
    }

    @Override
    public void execute(FlowNode node, FlowContext context) throws Exception {
        String beanId = node.attr("ruleforge", "bean");
        String methodName = node.attr("ruleforge", "method");

        if (beanId == null || methodName == null) {
            throw new FlowExecutionException(
                "Action node missing ruleforge:bean or ruleforge:method at " + node.getNodeId());
        }

        Object bean;
        try {
            bean = Utils.getApplicationContext().getBean(beanId);
        } catch (Exception e) {
            throw new FlowExecutionException("Action bean not found: " + beanId, e);
        }

        // 优先 method(Map),无则 method()
        Method mapMethod = findMethod(bean.getClass(), methodName, Map.class);
        if (mapMethod != null) {
            Object result = mapMethod.invoke(bean, context.getVars());
            if (result instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapResult = (Map<String, Object>) result;
                context.getVars().putAll(mapResult);
            }
            return;
        }

        Method noArgMethod = findMethod(bean.getClass(), methodName);
        if (noArgMethod != null) {
            noArgMethod.invoke(bean);
            return;
        }

        throw new FlowExecutionException(
            "Action method not found: " + beanId + "." + methodName + "(Map) or " + methodName + "()");
    }

    private Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        for (Method m : clazz.getMethods()) {
            if (!m.getName().equals(name)) continue;
            if (paramTypes.length == 0) {
                if (m.getParameterCount() == 0) return m;
            } else if (m.getParameterCount() == paramTypes.length) {
                Class<?>[] methodParamTypes = m.getParameterTypes();
                boolean match = true;
                for (int i = 0; i < paramTypes.length; i++) {
                    if (!paramTypes[i].isAssignableFrom(methodParamTypes[i])) {
                        match = false;
                        break;
                    }
                }
                if (match) return m;
            }
        }
        return null;
    }
}
