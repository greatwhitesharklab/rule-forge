package com.ruleforge.console.repository.model;

import java.util.Date;
import java.util.List;

public class ResourcePackage {

    private String id;
    private String name;
    private String project;
    private String version;
    private String testVersion;
    private Date createDate;
    private List<ResourceItem> resourceItems;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTestVersion() {
        return testVersion;
    }

    public void setTestVersion(String testVersion) {
        this.testVersion = testVersion;
    }

    public Date getCreateDate() {
        return createDate;
    }

    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    public List<ResourceItem> getResourceItems() {
        return resourceItems;
    }

    public void setResourceItems(List<ResourceItem> resourceItems) {
        this.resourceItems = resourceItems;
    }
}
