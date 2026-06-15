package com.ruleforge.console.util;

import com.ruleforge.console.DefaultEnvironmentProvider;
import com.ruleforge.console.EnvironmentProvider;
import com.ruleforge.console.servlet.RequestContext;
import com.ruleforge.console.model.User;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Collection;


/**
 * @author Jacky.gao
 * 2015年1月6日
 */
@Component
public class EnvironmentUtils implements ApplicationContextAware {
    private static ApplicationContext applicationContext;
    private static EnvironmentProvider environmentProvider;

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        EnvironmentUtils.applicationContext = ctx;
    }

    public static User getLoginUser(RequestContext context) {
        if (environmentProvider == null) {
            initEnvironmentProvider();
        }
        return environmentProvider.getLoginUser(context);
    }

    public static void initEnvironmentProvider() {
        Collection<EnvironmentProvider> providers = applicationContext.getBeansOfType(EnvironmentProvider.class).values();
        if (providers.isEmpty()) {
            environmentProvider = new DefaultEnvironmentProvider();
        } else {
            environmentProvider = providers.iterator().next();
        }
    }

    public static EnvironmentProvider getEnvironmentProvider() {
        if (environmentProvider == null) {
            initEnvironmentProvider();
        }
        return environmentProvider;
    }
}
