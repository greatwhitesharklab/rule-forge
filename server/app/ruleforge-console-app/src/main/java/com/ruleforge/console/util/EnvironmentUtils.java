package com.ruleforge.console.util;

import com.ruleforge.console.DefaultEnvironmentProvider;
import com.ruleforge.console.EnvironmentProvider;
import com.ruleforge.console.servlet.RequestContext;
import com.ruleforge.console.model.User;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * @author Jacky.gao
 * 2015年1月6日
 *
 * <p>V6.13.4d: 去除 {@code ApplicationContextAware} — 改 {@code @Component} +
 * 构造注入 {@link Collection<EnvironmentProvider>}。
 *
 * <p>之前 {@code setApplicationContext} + static lookup 模式 = CLAUDE.md 红线
 * "核心不渗 Spring" 反模式;V6.13.3 NPE 暴露失败模式不易诊断(漏 {@code @ComponentScan}
 * → 100% fail at runtime,无早期信号)。
 *
 * <p>现在 Spring 通过 ctor 注入自动发现 {@link EnvironmentProvider} bean,
 * 跟 V6.13.4a {@code CacheUtils}/{@code PropertyConfigurer}/{@code BsfVariableCollector}
 * 同模式。无 fallback default — 默认 fallback 到 {@code DefaultEnvironmentProvider}
 * 仅当 0 个 {@code EnvironmentProvider} bean 注册时(为单元测试场景保留)。
 *
 * <p>{@code getLoginUser} / {@code getEnvironmentProvider} 从 static 改 instance,
 * caller 走构造注入。
 */
@Component
public class EnvironmentUtils {
    private final EnvironmentProvider environmentProvider;

    public EnvironmentUtils(Collection<EnvironmentProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            this.environmentProvider = new DefaultEnvironmentProvider();
        } else {
            this.environmentProvider = providers.iterator().next();
        }
    }

    public User getLoginUser(RequestContext context) {
        return environmentProvider.getLoginUser(context);
    }

    public EnvironmentProvider getEnvironmentProvider() {
        return environmentProvider;
    }
}