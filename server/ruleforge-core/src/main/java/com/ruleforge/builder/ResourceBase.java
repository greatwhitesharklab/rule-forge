package com.ruleforge.builder;

import com.ruleforge.builder.resource.Resource;
import com.ruleforge.builder.resource.ResourceProvider;
import com.ruleforge.exception.RuleException;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Jacky.gao
 * @author fred
 * 2014年12月22日
 */
public class ResourceBase {
    private Collection<ResourceProvider> providers;
    @Getter
    private List<Resource> resources = new ArrayList<>();

    protected ResourceBase(Collection<ResourceProvider> providers) {
        this.providers = providers;
    }

    public ResourceBase addResource(String path, String version) {
        return addResource(path, version, null, false);
    }

    public ResourceBase addResource(String path, String version, boolean isContainSnapshot) {
        return addResource(path, version, null, isContainSnapshot);
    }

    public ResourceBase addResource(String path, String version, String projectVersion) {
        return addResource(path, version, projectVersion, false);
    }

    public ResourceBase addResource(String path, String version, String projectVersion, boolean isContainSnapshot) {
        boolean support = false;
        for (ResourceProvider provider : providers) {
            if (provider.support(path)) {
                support = true;
                resources.add(provider.provide(path, version, projectVersion, isContainSnapshot));
                break;
            }
        }
        if (!support) {
            throw new RuleException("Unsupport rule file source : " + path);
        }
        return this;
    }

}
