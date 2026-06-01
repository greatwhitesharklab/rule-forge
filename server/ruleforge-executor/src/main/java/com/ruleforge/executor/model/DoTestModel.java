package com.ruleforge.executor.model;

import java.util.List;
import java.util.Map;

public class DoTestModel {
    private List<Map<String, Object>> data;
    private String flowId;
    private String files;
    private String project;
    private String packageId;

    public List<Map<String, Object>> getData() { return data; }
    public void setData(List<Map<String, Object>> data) { this.data = data; }
    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId; }
    public String getFiles() { return files; }
    public void setFiles(String files) { this.files = files; }
    public String getProject() { return project; }
    public void setProject(String project) { this.project = project; }
    public String getPackageId() { return packageId; }
    public void setPackageId(String packageId) { this.packageId = packageId; }
}
