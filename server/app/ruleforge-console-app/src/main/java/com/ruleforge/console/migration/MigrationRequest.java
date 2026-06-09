package com.ruleforge.console.migration;

import java.util.List;

/**
 * 5.10-B: 入参 DTO.
 *
 * projectNames 为 null 或空 = 迁移所有项目。
 * dryRun=true 时只出报告,不写 Git 不更新 DB。
 */
public class MigrationRequest {

    private List<String> projectNames;
    private boolean dryRun;

    public MigrationRequest() {
    }

    public MigrationRequest(List<String> projectNames, boolean dryRun) {
        this.projectNames = projectNames;
        this.dryRun = dryRun;
    }

    public List<String> getProjectNames() {
        return projectNames;
    }

    public void setProjectNames(List<String> projectNames) {
        this.projectNames = projectNames;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}
