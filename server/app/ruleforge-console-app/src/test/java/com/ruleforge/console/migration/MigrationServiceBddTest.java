package com.ruleforge.console.migration;

import com.ruleforge.console.config.GitConfig;
import com.ruleforge.console.entity.FileVersionEntity;
import com.ruleforge.console.entity.ProjectEntity;
import com.ruleforge.console.repository.data.FileRepository;
import com.ruleforge.console.repository.data.ProjectRepository;
import com.ruleforge.console.storage.GitStorageService;
import com.ruleforge.console.storage.impl.GitStorageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 5.10-B BDD: 老项目 DB→Git migration tool
 *
 * 与 {@link com.ruleforge.console.service.impl.RuleForgeRepositoryServiceImplGitStorageIntegrationTest} 同模板:
 * mock FileRepository / ProjectRepository at interface + 真 GitStorageServiceImpl(spy 出来供 verify) + @TempDir
 *
 * Feature: 把 V5.10-A 之前的项目 (gr_file_version.fileContent 有内容但 gitCommitSha=NULL
 *          且 .git 不存在) 回填到 Git 仓
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MigrationService 老项目 DB→Git (5.10-B)")
class MigrationServiceBddTest {

    @TempDir
    Path gitBaseDir;

    private GitConfig realGitConfig;
    private GitStorageService realGitStorage;     // spy — 走真 JGit,但能 verify()
    private MigrationService service;

    @Mock private ProjectRepository projectRepository;
    @Mock private FileRepository fileRepository;

    @BeforeEach
    void setUp() throws Exception {
        realGitConfig = new GitConfig();
        realGitConfig.setBase(gitBaseDir.toString());
        // spy 真 GitStorageServiceImpl,让 verify(...) 能在它身上调
        realGitStorage = Mockito.spy(new GitStorageServiceImpl(realGitConfig));

        service = new MigrationService(
                projectRepository,
                fileRepository,
                realGitStorage,
                realGitConfig
        );

        when(projectRepository.findByName(anyString())).thenAnswer(inv -> {
            ProjectEntity p = new ProjectEntity();
            p.setId(System.nanoTime());
            p.setName(inv.getArgument(0));
            return p;
        });
    }

    // ==========================================================================
    // Scenario 1: happy path
    // ==========================================================================
    @Nested
    @DisplayName("Scenario 1: happy path — 单 project 单 version")
    class HappyPath {

        @Test
        @DisplayName("Given 老项目 + 1 行 fileContent 有值 + gitCommitSha=NULL + .git 不存在, "
                + "When migrate, Then 1 repo 创建 + 1 commit + 1 SHA 更新")
        void singleProject_singleVersion_migrated() throws Exception {
            // Given
            ProjectEntity legacy = mkProject(1L, "legacy-rules");
            when(projectRepository.findAll()).thenReturn(List.of(legacy));

            FileVersionEntity v1 = mkVersion(1L, "/legacy-rules/rules.xml", "1", 1L,
                    "<rules><r id='1'/></rules>", null);
            when(fileRepository.findVersionsByProjectId(1L)).thenReturn(List.of(v1));

            // When
            MigrationReport report = service.migrate(new MigrationRequest(null, false));

            // Then — 聚合
            assertThat(report.getTotalProjects()).isEqualTo(1);
            assertThat(report.getProjectsMigrated()).isEqualTo(1);
            assertThat(report.getProjectsSkippedClean()).isZero();
            assertThat(report.getProjectsFailed()).isZero();
            assertThat(report.getTotalVersionsSeen()).isEqualTo(1);
            assertThat(report.getVersionsMigrated()).isEqualTo(1);
            assertThat(report.getVersionsSkippedAlreadyMigrated()).isZero();
            assertThat(report.getVersionsSkippedNullContent()).isZero();
            assertThat(report.getVersionsFailed()).isZero();

            // Then — ProjectResult
            assertThat(report.getProjectResults()).hasSize(1);
            ProjectResult pr = report.getProjectResults().get(0);
            assertThat(pr.getProjectName()).isEqualTo("legacy-rules");
            assertThat(pr.getStatus()).isEqualTo(MigrationReport.ProjectStatus.MIGRATED);
            assertThat(pr.getVersionsMigrated()).isEqualTo(1);
            assertThat(pr.getErrors()).isEmpty();

            // Then — Git side effects
            assertThat(realGitStorage.repoExists("legacy-rules")).isTrue();
            String gitPath = "legacy-rules/rules.xml";
            String content = realGitStorage.readFile("legacy-rules", "main", gitPath);
            assertThat(content).isEqualTo("<rules><r id='1'/></rules>");

            // Then — DB side effect
            verify(fileRepository, times(1))
                    .updateGitCommitSha(eq("/legacy-rules/rules.xml"), eq("1"), anyString());
        }
    }

    // ==========================================================================
    // Scenario 2: skip already-migrated rows
    // ==========================================================================
    @Nested
    @DisplayName("Scenario 2: skip already-migrated rows")
    class SkipAlreadyMigrated {

        @Test
        @DisplayName("Given 同一项目下 1 个 null-SHA + 1 个 non-null-SHA, "
                + "When migrate, Then 仅迁移 null-SHA 那行")
        void onlyNullShaRowsAreMigrated() throws Exception {
            // Given
            ProjectEntity legacy = mkProject(1L, "legacy-rules");
            when(projectRepository.findAll()).thenReturn(List.of(legacy));

            FileVersionEntity nullRow = mkVersion(1L, "/legacy-rules/rules.xml", "1", 1L,
                    "<rules>v1</rules>", null);
            FileVersionEntity migratedRow = mkVersion(1L, "/legacy-rules/rules.xml", "2", 2L,
                    "<rules>v2</rules>", "abc123def");
            when(fileRepository.findVersionsByProjectId(1L))
                    .thenReturn(List.of(nullRow, migratedRow));

            // When
            MigrationReport report = service.migrate(new MigrationRequest(null, false));

            // Then
            assertThat(report.getVersionsMigrated()).isEqualTo(1);
            assertThat(report.getVersionsSkippedAlreadyMigrated()).isEqualTo(1);
            assertThat(report.getTotalVersionsSeen()).isEqualTo(2);

            // Then — non-null-SHA 那行不应被 updateGitCommitSha 再调
            verify(fileRepository, never())
                    .updateGitCommitSha(eq("/legacy-rules/rules.xml"), eq("2"), anyString());
            // null-SHA 那行调一次
            verify(fileRepository, times(1))
                    .updateGitCommitSha(eq("/legacy-rules/rules.xml"), eq("1"), anyString());
        }
    }

    // ==========================================================================
    // Scenario 3: dry-run
    // ==========================================================================
    @Nested
    @DisplayName("Scenario 3: dry-run")
    class DryRun {

        @Test
        @DisplayName("Given 跟 happy path 一样的项目, When dryRun=true, "
                + "Then writeFile/commit/updateGitCommitSha 一次都不调,但 versionsMigrated 仍报 N")
        void dryRunProducesNoSideEffects() throws Exception {
            // Given
            ProjectEntity legacy = mkProject(1L, "legacy-rules");
            when(projectRepository.findAll()).thenReturn(List.of(legacy));

            FileVersionEntity v1 = mkVersion(1L, "/legacy-rules/rules.xml", "1", 1L,
                    "<rules><r id='1'/></rules>", null);
            when(fileRepository.findVersionsByProjectId(1L)).thenReturn(List.of(v1));

            // When
            MigrationReport report = service.migrate(new MigrationRequest(null, true));

            // Then — 报告 "将迁移 N"
            assertThat(report.isDryRun()).isTrue();
            assertThat(report.getVersionsMigrated()).isEqualTo(1);

            // Then — Git 端无任何写
            assertThat(realGitStorage.repoExists("legacy-rules")).isFalse();
            verify(realGitStorage, never()).writeFile(anyString(), anyString(), anyString(), anyString());
            verify(realGitStorage, never()).commit(anyString(), anyString(), anyString(), anyString());

            // Then — DB 端无 SHA 更新
            verify(fileRepository, never())
                    .updateGitCommitSha(anyString(), anyString(), anyString());
        }
    }

    // ==========================================================================
    // Scenario 4: idempotent re-run
    // ==========================================================================
    @Nested
    @DisplayName("Scenario 4: idempotent re-run")
    class IdempotentReRun {

        @Test
        @DisplayName("Given 刚跑过 happy path, When 再跑一次, "
                + "Then versionsMigrated=0 + Git log 不增加 commit")
        void secondRunIsNoOp() throws Exception {
            // Given — 第一次跑
            ProjectEntity legacy = mkProject(1L, "legacy-rules");
            when(projectRepository.findAll()).thenReturn(List.of(legacy));

            FileVersionEntity v1 = mkVersion(1L, "/legacy-rules/rules.xml", "1", 1L,
                    "<rules>v1</rules>", null);
            when(fileRepository.findVersionsByProjectId(1L)).thenReturn(List.of(v1));

            MigrationReport first = service.migrate(new MigrationRequest(null, false));
            assertThat(first.getVersionsMigrated()).isEqualTo(1);
            String firstHeadSha = realGitStorage.getRevisionSha("legacy-rules", "main");

            // 关键:清空 invocations,这样第二次的 verify 只看第二跑
            Mockito.clearInvocations(fileRepository);

            // Given — 第二次跑:mock 这时 v1.gitCommitSha 已被"DB 写回",返非 null
            FileVersionEntity v1After = mkVersion(1L, "/legacy-rules/rules.xml", "1", 1L,
                    "<rules>v1</rules>", firstHeadSha);
            when(fileRepository.findVersionsByProjectId(1L)).thenReturn(List.of(v1After));

            // When
            MigrationReport second = service.migrate(new MigrationRequest(null, false));

            // Then
            assertThat(second.getVersionsMigrated()).isZero();
            assertThat(second.getVersionsSkippedAlreadyMigrated()).isEqualTo(1);

            // Then — Git HEAD SHA 不变
            String secondHeadSha = realGitStorage.getRevisionSha("legacy-rules", "main");
            assertThat(secondHeadSha).isEqualTo(firstHeadSha);

            // Then — 第二次不再写
            verify(fileRepository, never())
                    .updateGitCommitSha(anyString(), anyString(), anyString());
        }
    }

    // ==========================================================================
    // Scenario 5: per-project failure isolation
    // ==========================================================================
    @Nested
    @DisplayName("Scenario 5: per-project failure isolation")
    class PerProjectFailureIsolation {

        @Test
        @DisplayName("Given projA 健康 + projB findVersionsByProjectId 抛 RuntimeException, "
                + "When migrate, Then projA 完成 + projB 标 FAILED + 异常不向上抛")
        void failedProjectDoesNotBlockOthers() throws Exception {
            // Given
            ProjectEntity projA = mkProject(10L, "projA");
            ProjectEntity projB = mkProject(20L, "projB");
            when(projectRepository.findAll()).thenReturn(List.of(projA, projB));

            // projA 健康
            FileVersionEntity vA = mkVersion(10L, "/projA/rules.xml", "1", 1L, "<r/>", null);
            when(fileRepository.findVersionsByProjectId(10L)).thenReturn(List.of(vA));
            // projB 抛
            when(fileRepository.findVersionsByProjectId(20L))
                    .thenThrow(new RuntimeException("simulated DB failure"));

            // When — 跑完应不抛
            MigrationReport report;
            try {
                report = service.migrate(new MigrationRequest(null, false));
            } catch (Exception e) {
                throw new AssertionError("migrate() should not throw on per-project failure", e);
            }

            // Then
            assertThat(report.getTotalProjects()).isEqualTo(2);
            assertThat(report.getProjectsMigrated()).isEqualTo(1);
            assertThat(report.getProjectsFailed()).isEqualTo(1);
            assertThat(report.getVersionsMigrated()).isEqualTo(1);

            // Then — projA 在 results 里 MIGRATED
            ProjectResult pa = report.getProjectResults().stream()
                    .filter(r -> "projA".equals(r.getProjectName()))
                    .findFirst().orElseThrow();
            assertThat(pa.getStatus()).isEqualTo(MigrationReport.ProjectStatus.MIGRATED);

            // Then — projB 在 results 里 FAILED
            ProjectResult pb = report.getProjectResults().stream()
                    .filter(r -> "projB".equals(r.getProjectName()))
                    .findFirst().orElseThrow();
            assertThat(pb.getStatus()).isEqualTo(MigrationReport.ProjectStatus.FAILED);
            assertThat(pb.getErrors()).isNotEmpty();
            assertThat(pb.getErrors().get(0).getErrorType()).isEqualTo("RuntimeException");
            assertThat(pb.getErrors().get(0).getMessage()).contains("simulated DB failure");
        }
    }

    // ==========================================================================
    // Scenario 6: per-version failure isolation
    // ==========================================================================
    @Nested
    @DisplayName("Scenario 6: per-version failure isolation")
    class PerVersionFailureIsolation {

        @Test
        @DisplayName("Given 1 项目 3 个版本, v3 commit() 抛异常, "
                + "When migrate, Then v1+v2 成功 + v3 计入 versionsFailed + 含 exception 类名")
        void failedVersionDoesNotBlockOthers() throws Exception {
            // Given
            ProjectEntity proj = mkProject(1L, "proj");
            when(projectRepository.findAll()).thenReturn(List.of(proj));

            FileVersionEntity v1 = mkVersion(1L, "/proj/rules.xml", "1", 1L, "<r>v1</r>", null);
            FileVersionEntity v2 = mkVersion(1L, "/proj/rules.xml", "2", 2L, "<r>v2</r>", null);
            FileVersionEntity v3 = mkVersion(1L, "/proj/rules.xml", "3", 3L, "<r>v3</r>", null);
            when(fileRepository.findVersionsByProjectId(1L)).thenReturn(List.of(v1, v2, v3));

            // spy:对 v3 的 commit() 抛异常
            doThrow(new RuntimeException("simulated commit failure on v3"))
                    .when(realGitStorage).commit(eq("proj"), eq("main"), contains("v3"), eq("migration-tool"));

            // When
            MigrationReport report = service.migrate(new MigrationRequest(null, false));

            // Then
            assertThat(report.getVersionsMigrated()).isEqualTo(2);
            assertThat(report.getVersionsFailed()).isEqualTo(1);
            assertThat(report.getTotalVersionsSeen()).isEqualTo(3);

            // Then — v1 v2 SHA 写回,v3 没写
            verify(fileRepository, times(1))
                    .updateGitCommitSha(eq("/proj/rules.xml"), eq("1"), anyString());
            verify(fileRepository, times(1))
                    .updateGitCommitSha(eq("/proj/rules.xml"), eq("2"), anyString());
            verify(fileRepository, never())
                    .updateGitCommitSha(eq("/proj/rules.xml"), eq("3"), anyString());

            // Then — v3 错误信息含 exception 类名
            ProjectResult pr = report.getProjectResults().get(0);
            VersionError v3Err = pr.getErrors().stream()
                    .filter(e -> "3".equals(e.getVersionNum()))
                    .findFirst().orElseThrow();
            assertThat(v3Err.getErrorType()).isEqualTo("RuntimeException");
            assertThat(v3Err.getMessage()).contains("simulated commit failure on v3");
        }
    }

    // ==========================================================================
    // Scenario 7: no projects
    // ==========================================================================
    @Nested
    @DisplayName("Scenario 7: no projects")
    class NoProjects {

        @Test
        @DisplayName("Given findAll() 返空, When migrate, "
                + "Then totalProjects=0 + 无 initRepo 调用 + 无异常")
        void emptyProjectListIsSuccess() throws Exception {
            // Given
            when(projectRepository.findAll()).thenReturn(Collections.emptyList());

            // When
            MigrationReport report;
            try {
                report = service.migrate(new MigrationRequest(null, false));
            } catch (Exception e) {
                throw new AssertionError("migrate() should not throw on empty list", e);
            }

            // Then
            assertThat(report.getTotalProjects()).isZero();
            assertThat(report.getProjectsMigrated()).isZero();
            assertThat(report.getVersionsMigrated()).isZero();
            assertThat(report.getGlobalErrors()).isEmpty();
            assertThat(report.getProjectResults()).isEmpty();

            // Then — initRepo 不该被调
            verify(realGitStorage, never()).initRepo(anyString());
        }
    }

    // ==========================================================================
    // Scenario 8: multi-version chronological order
    // ==========================================================================
    @Nested
    @DisplayName("Scenario 8: multi-version chronological order")
    class MultiVersionChronologicalOrder {

        @Test
        @DisplayName("Given 同一 filePath 的 3 个版本 (versionNumReal=1,2,3), "
                + "When migrate, Then writeFile+commit 按 v1→v2→v3 顺序,Git 末尾内容 = v3")
        void versionsCommittedInChronologicalOrder() throws Exception {
            // Given
            ProjectEntity legacy = mkProject(1L, "legacy");
            when(projectRepository.findAll()).thenReturn(List.of(legacy));

            FileVersionEntity v1 = mkVersion(1L, "/legacy/rules.xml", "1", 1L, "<r>v1</r>", null);
            FileVersionEntity v2 = mkVersion(1L, "/legacy/rules.xml", "2", 2L, "<r>v2</r>", null);
            FileVersionEntity v3 = mkVersion(1L, "/legacy/rules.xml", "3", 3L, "<r>v3</r>", null);
            when(fileRepository.findVersionsByProjectId(1L)).thenReturn(List.of(v1, v2, v3));

            // When
            MigrationReport report = service.migrate(new MigrationRequest(null, false));

            // Then — 三个版本都迁了
            assertThat(report.getVersionsMigrated()).isEqualTo(3);
            assertThat(report.getProjectResults().get(0).getStatus())
                    .isEqualTo(MigrationReport.ProjectStatus.MIGRATED);

            // Then — commit 调用 3 次
            verify(realGitStorage, times(3))
                    .commit(eq("legacy"), eq("main"), anyString(), eq("migration-tool"));

            // Then — Git 末尾内容 = v3
            String finalContent = realGitStorage.readFile("legacy", "main", "legacy/rules.xml");
            assertThat(finalContent).isEqualTo("<r>v3</r>");

            // Then — commit 顺序:v1 → v2 → v3 (按 versionNum 包含的字串验)
            org.mockito.InOrder inOrder = Mockito.inOrder(realGitStorage);
            inOrder.verify(realGitStorage).commit(eq("legacy"), eq("main"),
                    argThat(msg -> msg != null && msg.contains("v1")), eq("migration-tool"));
            inOrder.verify(realGitStorage).commit(eq("legacy"), eq("main"),
                    argThat(msg -> msg != null && msg.contains("v2")), eq("migration-tool"));
            inOrder.verify(realGitStorage).commit(eq("legacy"), eq("main"),
                    argThat(msg -> msg != null && msg.contains("v3")), eq("migration-tool"));
        }
    }

    // ==========================================================================
    // Scenario 9: skip blank / too-short content
    // ==========================================================================
    @Nested
    @DisplayName("Scenario 9: skip blank / too-short content")
    class SkipBlankContent {

        @Test
        @DisplayName("Given 1 project with blank + short + healthy versions, "
                + "When migrate, Then only healthy is committed, blanks counted as skippedNullContent")
        void blankAndShortContentSkipped() throws Exception {
            // Given
            ProjectEntity proj = mkProject(1L, "proj");
            when(projectRepository.findAll()).thenReturn(List.of(proj));

            FileVersionEntity blank = mkVersion(1L, "/proj/a.xml", "1", 1L, "   \n\t  ", null);
            FileVersionEntity shorty = mkVersion(1L, "/proj/b.xml", "2", 2L, ".", null);        // 1 char < 2
            FileVersionEntity healthy = mkVersion(1L, "/proj/c.xml", "3", 3L,
                    "<rules><r id='1'>hello</r></rules>", null);                                  // 30 chars
            when(fileRepository.findVersionsByProjectId(1L))
                    .thenReturn(List.of(blank, shorty, healthy));

            // When
            MigrationReport report = service.migrate(new MigrationRequest(null, false));

            // Then — only healthy migrated
            assertThat(report.getVersionsMigrated()).isEqualTo(1);
            assertThat(report.getVersionsSkippedNullContent()).isEqualTo(2);
            assertThat(report.getTotalVersionsSeen()).isEqualTo(3);

            // Then — no commit for blank or short
            verify(realGitStorage, times(1))
                    .commit(eq("proj"), eq("main"), contains("v3"), eq("migration-tool"));
            verify(realGitStorage, never())
                    .commit(eq("proj"), eq("main"), contains("v1"), eq("migration-tool"));
            verify(realGitStorage, never())
                    .commit(eq("proj"), eq("main"), contains("v2"), eq("migration-tool"));

            // Then — only 1 SHA update
            verify(fileRepository, times(1))
                    .updateGitCommitSha(anyString(), anyString(), anyString());
        }
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================
    private ProjectEntity mkProject(Long id, String name) {
        ProjectEntity p = new ProjectEntity();
        p.setId(id);
        p.setName(name);
        return p;
    }

    private FileVersionEntity mkVersion(Long projectId, String filePath, String versionNum,
                                        Long versionNumReal, String content, String gitCommitSha) {
        FileVersionEntity v = new FileVersionEntity();
        v.setId(System.nanoTime());
        v.setFileId(System.nanoTime());
        v.setProjectId(projectId);
        v.setFilePath(filePath);
        v.setFileName(filePath.substring(filePath.lastIndexOf('/') + 1));
        v.setVersionNum(versionNum);
        v.setVersionNumReal(versionNumReal);
        v.setFileContent(content);
        v.setGitCommitSha(gitCommitSha);
        return v;
    }
}
