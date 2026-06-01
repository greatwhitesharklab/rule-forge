package com.ruleforge.builder;

import com.ruleforge.builder.resource.ResourceBuilder;
import com.ruleforge.builder.resource.ResourceProvider;
import com.ruleforge.exception.RuleException;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Collection;

/**
 * @author Jacky.gao
 * 2015年2月16日
 */
@SuppressWarnings("rawtypes")
public abstract class AbstractBuilder implements ApplicationContextAware {
    protected Collection<ResourceProvider> providers;
    protected ApplicationContext applicationContext;
    protected Collection<ResourceBuilder> resourceBuilders;

    public ResourceBase newResourceBase() {
        return new ResourceBase(providers);
    }

    protected Element parseResource(String content) {
        try {
            Document document = DocumentHelper.parseText(content);
            return document.getRootElement();
        } catch (DocumentException e) {
            throw new RuleException(e);
        }
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        resourceBuilders = applicationContext.getBeansOfType(ResourceBuilder.class).values();
        providers = applicationContext.getBeansOfType(ResourceProvider.class).values();
        this.applicationContext = applicationContext;
        applicationContext.getBeansWithAnnotation(SuppressWarnings.class);
    }
}
