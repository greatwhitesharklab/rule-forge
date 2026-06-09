package com.ruleforge.console.migration;

import com.ruleforge.console.config.GitConfig;
import com.ruleforge.console.entity.FileVersionEntity;
import com.ruleforge.console.entity.ProjectEntity;
import com.ruleforge.console.repository.data.FileRepository;
import com.ruleforge.console.repository.data.ProjectRepository;
import com.ruleforge.console.storage.GitStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 5.10-B: 老项目 DB→Git migration 核心服务.
 *
 * 扫 gr_project,按 versionNumReal 升序把所有未迁移 (gitCommitSha IS NULL 且 fileContent IS NOT NULL) 的
 * gr_file_version 行 commit 到 Git main,再把 SHA 写回 DB.
 *
 * Skip 规则 (idempotent, 重跑安全):
 *   - fileContent IS NULL     → 跳过(无内容可写)
 *   - gitCommitSha IS NOT NULL → 跳过(已迁移)
 *
 * 失败隔离:per-project + per-version 两层 try/catch,异常吞进 report,
 * 整个 run 不向上抛.
 *
 * 故意不注入 XmlCanonicalizer:老项目要字节级保留 DB 内容. Live save path
 * 用 canonicalizer 是因为它是 "用户正在编辑",格式归一没问题;migration 是
 * "历史快照",原始为王.
 *
 * @see <a href="/home/fredgu/.claude/plans/hashed-beaming-canyon.md">5.10-B plan</a>
 */
@Service
public class MigrationService {

    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);
    private static final String BRANCH = "main";
    private static final String AUTHOR = "migration-tool";
    private static final int MAX_ERROR_MESSAGE_LEN = 200;
    /** 内容最短字节数 — trim 后少于此值视为空壳行,跳过以免产生空 commit 污染 Git 历史. */
    private static final int MIN_CONTENT_LENGTH = 2;

    private final ProjectRepository projectRepository;
    private final FileRepository fileRepository;
    private final GitStorageService gitStorageService;
    private final GitConfig gitConfig;

    public MigrationService(ProjectRepository projectRepository,
                            FileRepository fileRepository,
                            GitStorageService gitStorageService,
                            GitConfig gitConfig) {
        this.projectRepository = projectRepository;
        this.fileRepository = fileRepository;
        this.gitStorageService = gitStorageService;
        this.gitConfig = gitConfig;
    }

    /**
     * 跑一次 migration. 永远不抛异常(per-row 失败吞进 report).
     * 全局 setup 失败(如 DB 不可达)会进 report.globalErrors 且让 affected=0 仍返回.
     */
    public MigrationReport migrate(MigrationRequest req) {
        MigrationReport report = new MigrationReport();
        report.setStartedAt(Instant.now());
        report.setDryRun(req.isDryRun());
        report.setRequestedProjectNames(req.getProjectNames() == null
                ? Collections.emptyList()
                : new ArrayList<>(req.getProjectNames()));

        try {
            List<ProjectEntity> allProjects = projectRepository.findAll();
            List<ProjectEntity> targetProjects = filterProjects(allProjects, req.getProjectNames());
            report.setTotalProjects(targetProjects.size());

            for (ProjectEntity project : targetProjects) {
                ProjectResult pr = migrateOneProject(project, report);
                report.getProjectResults().add(pr);
            }
        } catch (Exception e) {
            // 全局 setup 失败(几乎不该发生 — per-project 已经隔离)
            String msg = e.getClass().getSimpleName() + ": " + safeMessage(e.getMessage());
            log.error("Migration global failure", e);
            report.getGlobalErrors().add(msg);
        }

        report.setFinishedAt(Instant.now());
        report.setDurationMs(Duration.between(report.getStartedAt(), report.getFinishedAt()).toMillis());
        return report;
    }

    private ProjectResult migrateOneProject(ProjectEntity project, MigrationReport report) {
        ProjectResult pr = new ProjectResult(project.getName(), project.getId());

        try {
            // 1. 拿所有版本
            List<FileVersionEntity> versions = fileRepository.findVersionsByProjectId(project.getId());
            report.setTotalVersionsSeen(report.getTotalVersionsSeen() + versions.size());

            // 2. 逐版本处理
            for (FileVersionEntity v : versions) {
                try {
                    processOneVersion(project.getName(), v, report, pr);
                } catch (Exception e) {
                    String type = e.getClass().getSimpleName();
                    String msg = safeMessage(e.getMessage());
                    log.error("Migration version failed: project={} path={} v={}",
                            project.getName(), v.getFilePath(), v.getVersionNum(), e);
                    pr.getErrors().add(new VersionError(v.getFilePath(), v.getVersionNum(), type, msg));
                    pr.setVersionsFailed(pr.getVersionsFailed() + 1);
                    report.setVersionsFailed(report.getVersionsFailed() + 1);
                }
            }

            // 3. 定项目 status
            pr.setStatus(decideProjectStatus(pr));
            bumpProjectStatusCounter(report, pr.getStatus());
        } catch (Exception e) {
            // project-level catch (e.g. findVersionsByProjectId 抛 DataAccessException)
            String type = e.getClass().getSimpleName();
            String msg = safeMessage(e.getMessage());
            log.error("Migration project failed: project={}", project.getName(), e);
            pr.setStatus(MigrationReport.ProjectStatus.FAILED);
            pr.getErrors().add(new VersionError("(project-level)", "-", type, msg));
            bumpProjectStatusCounter(report, pr.getStatus());
        }
        return pr;
    }

    private void processOneVersion(String projectName, FileVersionEntity v,
                                   MigrationReport report, ProjectResult pr) {
        // skip 1: 无内容或内容太短(空壳行)
        if (v.getFileContent() == null || v.getFileContent().trim().length() < MIN_CONTENT_LENGTH) {
            log.debug("Migration skip (content too short): project={} path={} v={} len={}",
                    projectName, v.getFilePath(), v.getVersionNum(),
                    v.getFileContent() == null ? 0 : v.getFileContent().length());
            report.setVersionsSkippedNullContent(report.getVersionsSkippedNullContent() + 1);
            pr.setVersionsSkipped(pr.getVersionsSkipped() + 1);
            return;
        }
        // skip 2: 已迁移
        if (v.getGitCommitSha() != null && !v.getGitCommitSha().isBlank()) {
            report.setVersionsSkippedAlreadyMigrated(report.getVersionsSkippedAlreadyMigrated() + 1);
            pr.setVersionsSkipped(pr.getVersionsSkipped() + 1);
            return;
        }

        // dry-run: 仍计 versionsMigrated 报告 "将迁移 N",但不动 Git / DB
        if (report.isDryRun()) {
            report.setVersionsMigrated(report.getVersionsMigrated() + 1);
            pr.setVersionsMigrated(pr.getVersionsMigrated() + 1);
            return;
        }

        // ensure repo exists
        if (!gitStorageService.repoExists(projectName)) {
            gitStorageService.initRepo(projectName);
        }

        // write + commit
        String gitPath = stripLeadingSlash(v.getFilePath());
        gitStorageService.writeFile(projectName, BRANCH, gitPath, v.getFileContent());

        String commitMessage = "Migration: " + v.getFilePath() + " v" + v.getVersionNum();
        String sha = gitStorageService.commit(projectName, BRANCH, commitMessage, AUTHOR);
        // best-effort push
        try {
            gitStorageService.push(projectName);
        } catch (Exception pushEx) {
            log.debug("Migration push failed (non-fatal): project={} {}", projectName, pushEx.getMessage());
        }

        // record SHA
        fileRepository.updateGitCommitSha(v.getFilePath(), v.getVersionNum(), sha);

        report.setVersionsMigrated(report.getVersionsMigrated() + 1);
        pr.setVersionsMigrated(pr.getVersionsMigrated() + 1);
    }

    private MigrationReport.ProjectStatus decideProjectStatus(ProjectResult pr) {
        if (pr.getVersionsMigrated() > 0) {
            return MigrationReport.ProjectStatus.MIGRATED;
        }
        if (pr.getVersionsFailed() > 0) {
            return MigrationReport.ProjectStatus.FAILED;
        }
        return MigrationReport.ProjectStatus.SKIPPED_CLEAN;
    }

    private void bumpProjectStatusCounter(MigrationReport report, MigrationReport.ProjectStatus status) {
        switch (status) {
            case MIGRATED -> report.setProjectsMigrated(report.getProjectsMigrated() + 1);
            case SKIPPED_CLEAN -> report.setProjectsSkippedClean(report.getProjectsSkippedClean() + 1);
            case FAILED -> report.setProjectsFailed(report.getProjectsFailed() + 1);
        }
    }

    private List<ProjectEntity> filterProjects(List<ProjectEntity> all, List<String> names) {
        if (names == null || names.isEmpty()) {
            return all;
        }
        Set<String> nameSet = new HashSet<>(names);
        List<ProjectEntity> out = new ArrayList<>();
        for (ProjectEntity p : all) {
            if (nameSet.contains(p.getName())) {
                out.add(p);
            }
        }
        return out;
    }

    private static String stripLeadingSlash(String path) {
        if (path == null) return "";
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private static String safeMessage(String msg) {
        if (msg == null) return "(no message)";
        return msg.length() > MAX_ERROR_MESSAGE_LEN
                ? msg.substring(0, MAX_ERROR_MESSAGE_LEN) + "..."
                : msg;
    }
}
