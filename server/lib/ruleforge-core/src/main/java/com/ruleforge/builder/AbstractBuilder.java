package com.ruleforge.builder;

import com.ruleforge.builder.resource.ResourceBuilder;
import com.ruleforge.builder.resource.ResourceProvider;
import com.ruleforge.exception.RuleException;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import com.ruleforge.plugin.EnginePluginRegistry;

import java.util.Collection;

/**
 * @author Jacky.gao
 * 2015年2月16日
 */
@SuppressWarnings("rawtypes")
public abstract class AbstractBuilder {
    protected Collection<ResourceProvider> providers;
    protected EnginePluginRegistry pluginRegistry;
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

    public void setPluginRegistry(EnginePluginRegistry pluginRegistry) {
        this.resourceBuilders = pluginRegistry.getResourceBuilders();
        this.providers = pluginRegistry.getResourceProviders();
        this.pluginRegistry = pluginRegistry;
        // V5.48: 删除原 dead code applicationContext.getBeansWithAnnotation(SuppressWarnings.class)
    }
}
