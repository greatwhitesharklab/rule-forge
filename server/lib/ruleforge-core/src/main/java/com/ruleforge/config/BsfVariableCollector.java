package com.ruleforge.config;
import com.ruleforge.action.BsfVariableProvider;

import com.ruleforge.engine.Context;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Jacky.gao
 * @since 2015年3月18日
 *
 * <p>V6.13.4a: 去除 {@code ApplicationContextAware} — 改 {@code @Component} + 构造注入
 * {@link Collection<BsfVariableProvider>}。
 *
 * <p><b>Breaking change</b>: variableMap 删除了 {@code "applicationContext"} entry —
 * 之前是把 Spring ApplicationContext 直接塞进 BSF 脚本变量,暴露 Spring 内部状态给业务脚本。
 * 若有脚本依赖此变量请改用其他方式获取 bean (推荐 {@code SpringBean} action)。
 */
@Component
public class BsfVariableCollector {
    public static final String BEAN_ID = "ruleforge.bsfVariableCollector";
    private final Collection<BsfVariableProvider> providers;

    public BsfVariableCollector(Collection<BsfVariableProvider> providers) {
        this.providers = providers;
    }

    public Map<String, Object> getVariableMap(Context context) {
        Map<String, Object> variableMap = new HashMap<>();
        variableMap.put("workingMemory", context.getWorkingMemory());
        for (BsfVariableProvider provider : providers) {
            Map<String, Object> map = provider.provide();
            if (map == null) {
                continue;
            }
            variableMap.putAll(map);
        }
        return variableMap;
    }
}
