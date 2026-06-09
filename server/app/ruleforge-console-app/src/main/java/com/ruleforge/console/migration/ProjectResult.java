package com.ruleforge.console.migration;

import java.util.ArrayList;
import java.util.List;

/**
 * 5.10-B: 单项目结果.
 */
public class ProjectResult {

    private String projectName;
    private Long projectId;
    private MigrationReport.ProjectStatus status;
    private int versionsMigrated;
    private int versionsSkipped;
    private int versionsFailed;
    private List<VersionError> errors = new ArrayList<>();

    public ProjectResult() {
    }

    public ProjectResult(String projectName, Long projectId) {
        this.projectName = projectName;
        this.projectId = projectId;
    }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public MigrationReport.ProjectStatus getStatus() { return status; }
    public void setStatus(MigrationReport.ProjectStatus status) { this.status = status; }
    public int getVersionsMigrated() { return versionsMigrated; }
    public void setVersionsMigrated(int versionsMigrated) { this.versionsMigrated = versionsMigrated; }
    public int getVersionsSkipped() { return versionsSkipped; }
    public void setVersionsSkipped(int versionsSkipped) { this.versionsSkipped = versionsSkipped; }
    public int getVersionsFailed() { return versionsFailed; }
    public void setVersionsFailed(int versionsFailed) { this.versionsFailed = versionsFailed; }
    public List<VersionError> getErrors() { return errors; }
    public void setErrors(List<VersionError> errors) { this.errors = errors; }
}
