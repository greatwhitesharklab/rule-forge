package com.ruleforge.action;

import com.ruleforge.runtime.rete.Context;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Jacky.gao
 * @since 2015年3月18日
 */
public class BsfVariableCollector implements ApplicationContextAware {
    public static final String BEAN_ID = "ruleforge.bsfVariableCollector";
    private Collection<BsfVariableProvider> providers;
    private ApplicationContext applicationContext;

    public Map<String, Object> getVariableMap(Context context) {
        Map<String, Object> variableMap = new HashMap<>();
        variableMap.put("workingMemory", context.getWorkingMemory());
        variableMap.put("applicationContext", applicationContext);
        for (BsfVariableProvider provider : providers) {
            Map<String, Object> map = provider.provide();
            if (map == null) {
                continue;
            }
            variableMap.putAll(map);
        }
        return variableMap;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        providers = applicationContext.getBeansOfType(BsfVariableProvider.class).values();
        this.applicationContext = applicationContext;
    }
}
