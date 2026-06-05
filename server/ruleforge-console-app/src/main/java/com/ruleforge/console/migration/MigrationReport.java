package com.ruleforge.console.migration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 5.10-B: 出参 DTO.
 *
 * Aggregate + per-project 拆解;失败按 per-project/per-version 两层隔离
 * 但异常不向上抛,所以这个 report 永远有值,无 throws 声明。
 */
public class MigrationReport {

    public enum ProjectStatus {
        /** 项目下至少 1 个版本被迁移 */
        MIGRATED,
        /** .git 已存在 且没有 null-SHA 行,什么都没做 */
        SKIPPED_CLEAN,
        /** 项目级异常,没完成 */
        FAILED
    }

    private String runId;
    private Instant startedAt;
    private Instant finishedAt;
    private long durationMs;
    private boolean dryRun;
    private List<String> requestedProjectNames;

    // aggregate
    private int totalProjects;
    private int projectsMigrated;
    private int projectsSkippedClean;
    private int projectsFailed;

    private int totalVersionsSeen;
    private int versionsMigrated;
    private int versionsSkippedAlreadyMigrated;
    private int versionsSkippedNullContent;
    private int versionsFailed;

    private List<ProjectResult> projectResults = new ArrayList<>();
    private List<String> globalErrors = new ArrayList<>();

    public MigrationReport() {
        this.runId = UUID.randomUUID().toString();
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
    public List<String> getRequestedProjectNames() { return requestedProjectNames; }
    public void setRequestedProjectNames(List<String> requestedProjectNames) { this.requestedProjectNames = requestedProjectNames; }

    public int getTotalProjects() { return totalProjects; }
    public void setTotalProjects(int totalProjects) { this.totalProjects = totalProjects; }
    public int getProjectsMigrated() { return projectsMigrated; }
    public void setProjectsMigrated(int projectsMigrated) { this.projectsMigrated = projectsMigrated; }
    public int getProjectsSkippedClean() { return projectsSkippedClean; }
    public void setProjectsSkippedClean(int projectsSkippedClean) { this.projectsSkippedClean = projectsSkippedClean; }
    public int getProjectsFailed() { return projectsFailed; }
    public void setProjectsFailed(int projectsFailed) { this.projectsFailed = projectsFailed; }

    public int getTotalVersionsSeen() { return totalVersionsSeen; }
    public void setTotalVersionsSeen(int totalVersionsSeen) { this.totalVersionsSeen = totalVersionsSeen; }
    public int getVersionsMigrated() { return versionsMigrated; }
    public void setVersionsMigrated(int versionsMigrated) { this.versionsMigrated = versionsMigrated; }
    public int getVersionsSkippedAlreadyMigrated() { return versionsSkippedAlreadyMigrated; }
    public void setVersionsSkippedAlreadyMigrated(int versionsSkippedAlreadyMigrated) { this.versionsSkippedAlreadyMigrated = versionsSkippedAlreadyMigrated; }
    public int getVersionsSkippedNullContent() { return versionsSkippedNullContent; }
    public void setVersionsSkippedNullContent(int versionsSkippedNullContent) { this.versionsSkippedNullContent = versionsSkippedNullContent; }
    public int getVersionsFailed() { return versionsFailed; }
    public void setVersionsFailed(int versionsFailed) { this.versionsFailed = versionsFailed; }

    public List<ProjectResult> getProjectResults() { return projectResults; }
    public void setProjectResults(List<ProjectResult> projectResults) { this.projectResults = projectResults; }
    public List<String> getGlobalErrors() { return globalErrors; }
    public void setGlobalErrors(List<String> globalErrors) { this.globalErrors = globalErrors; }
}
