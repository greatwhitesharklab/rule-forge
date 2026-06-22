package com.ruleforge.config;

import org.springframework.core.io.support.PropertiesLoaderSupport;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;

/**
 * V6.13.4a: 去除 {@code ApplicationContextAware} — 改 {@code @Component} + 构造注入
 * {@link List<PropertiesLoaderSupport>}。构造里反射调 {@code mergeProperties} 把所有 properties
 * 合并进静态 {@code props}。静态 {@code getProperty(key)} API 不变(1 处 caller 在
 * {@code com.ruleforge.engine.ValueCompute},后续 V6.13.4a2 改 ctor 注入本类替代静态)。
 */
@Component
public class PropertyConfigurer {
    private static final Properties props = new Properties();

    public PropertyConfigurer(List<PropertiesLoaderSupport> supports) {
        if (supports == null) {
            return;
        }
        // V5.96 — Iterator var123 → enhanced for
        for (PropertiesLoaderSupport support : supports) {
            this.doMethod(support);
        }
    }

    public static String getProperty(String key) {
        return props.getProperty(key);
    }

    private void doMethod(PropertiesLoaderSupport support) {
        try {
            Method method = PropertiesLoaderSupport.class.getDeclaredMethod("mergeProperties");
            method.setAccessible(true);
            Object obj = method.invoke(support);
            props.putAll((Properties) obj);
        } catch (Exception var4) {
            throw new RuntimeException(var4);
        }
    }
}
