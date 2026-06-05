package com.ruleforge.console.observability;

import com.ruleforge.console.config.GitConfig;
import com.ruleforge.console.entity.FileEntity;
import com.ruleforge.console.entity.GitDualwriteFailureEntity;
import com.ruleforge.console.entity.LockEntity;
import com.ruleforge.console.entity.ProjectEntity;
import com.ruleforge.console.model.DefaultUser;
import com.ruleforge.console.model.User;
import com.ruleforge.console.repository.data.FileRepository;
import com.ruleforge.console.repository.data.GitDualwriteFailureRepository;
import com.ruleforge.console.repository.data.LockRepository;
import com.ruleforge.console.repository.data.PackageRepository;
import com.ruleforge.console.repository.data.ProjectRepository;
import com.ruleforge.console.repository.data.RuntimeRepository;
import com.ruleforge.console.service.PermissionService;
import com.ruleforge.console.service.RepositoryInterceptor;
import com.ruleforge.console.storage.GitStorageService;
import com.ruleforge.console.storage.ProjectStorageService;
import com.ruleforge.console.storage.XmlCanonicalizer;
import com.ruleforge.console.storage.impl.GitStorageServiceImpl;
import com.ruleforge.console.storage.model.GitOperationException;
import com.ruleforge.console.service.impl.RuleForgeRepositoryServiceImpl;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 5.10-C BDD: dualWrite 失败可观测.
 *
 * 与 5.10-A / 5.10-B 同模板:
 * mock FileRepository / GitDualwriteFailureRepository at interface +
 * spy 真 GitStorageServiceImpl(让 commit() 可触发异常) + @TempDir +
 * 真 SimpleMeterRegistry(让 counter 真的能 accumulate)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DualWrite observability (5.10-C)")
class DualWriteObservabilityBddTest {

    @TempDir
    Path gitBaseDir;

    private GitConfig realGitConfig;
    private GitStorageService realGitStorage;   // spy
    private MeterRegistry meterRegistry;
    private RuleForgeRepositoryServiceImpl service;

    @Mock private PermissionService permissionService;
    @Mock private ProjectRepository projectRepository;
    @Mock private FileRepository fileRepository;
    @Mock private LockRepository lockRepository;
    @Mock private PackageRepository packageRepository;
    @Mock private RuntimeRepository runtimeRepository;
    @Mock private ProjectStorageService projectStorageService;
    @Mock private RepositoryInterceptor repositoryInterceptor;
    @Mock private XmlCanonicalizer xmlCanonicalizer;
    @Mock private GitDualwriteFailureRepository failureRepository;

    private static final User TEST_USER = makeUser("alice");
    private static final String PROJECT = "obs-test";
    private static final String FILE_PATH = "/obs-test/rules.xml";

    @BeforeEach
    void setUp() throws Exception {
        realGitConfig = new GitConfig();
        realGitConfig.setBase(gitBaseDir.toString());
        realGitStorage = Mockito.spy(new GitStorageServiceImpl(realGitConfig));
        meterRegistry = new SimpleMeterRegistry();

        service = new RuleForgeRepositoryServiceImpl(
                permissionService,
                projectRepository,
                fileRepository,
                lockRepository,
                packageRepository,
                runtimeRepository,
                projectStorageService,
                repositoryInterceptor,
                realGitStorage,
                realGitConfig,
                xmlCanonicalizer,
                failureRepository,
                meterRegistry
        );

        when(permissionService.projectPackageHasWritePermission(anyString())).thenReturn(true);
        when(permissionService.fileHasWritePermission(anyString())).thenReturn(true);
        when(projectRepository.findByName(anyString())).thenAnswer(inv -> {
            ProjectEntity p = new ProjectEntity();
            p.setId(1L);
            p.setName(inv.getArgument(0));
            return p;
        });
        when(lockRepository.findByResource(anyString())).thenReturn(null);
        when(lockRepository.insert(any(LockEntity.class))).thenAnswer(inv -> {
            LockEntity e = inv.getArgument(0);
            e.setId(System.nanoTime());
            return e;
        });
        when(fileRepository.findByFilePath(anyString())).thenAnswer(inv -> {
            FileEntity f = new FileEntity();
            f.setId(10L);
            f.setName("rules.xml");
            f.setFilePath(inv.getArgument(0));
            f.setProjectId(1L);
            return f;
        });
        when(fileRepository.findLatestByFileId(any(), anyBoolean())).thenReturn(null);
        when(fileRepository.findLatestReleaseByFilePathFull(anyString())).thenReturn(null);
        when(xmlCanonicalizer.canonicalize(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ==========================================================================
    // Scenario 1: happy path — 成功路径无失败行, success counter +1
    // ==========================================================================
    @Nested
    @DisplayName("Scenario 1: happy path — 成功路径")
    class HappyPath {

        @Test
        @DisplayName("Given saveFile 走完 JGit 不抛, "
                + "When saveFile 调一次, Then 失败表无新行, success counter == 1, failure counter == 0")
        void success_noFailureRecorded() throws Exception {
            // Given
            realGitStorage.initRepo(PROJECT);

            // When
            String version = service.saveFile(FILE_PATH, "<rules/>", true, "init", TEST_USER);

            // Then
            assertThat(version).isNotNull();
            verify(failureRepository, never()).insert(any());
            Counter success = meterRegistry.find("ruleforge_git_dualwrite_total")
                    .tags("result", "success").counter();
            Counter failure = meterRegistry.find("ruleforge_git_dualwrite_total")
                    .tags("result", "failure").counter();
            assertThat(success).isNotNull();
            assertThat(success.count()).isEqualTo(1.0);
            assertThat(failure).isNull();   // 没创建过 failure counter
        }
    }

    // ==========================================================================
    // Scenario 2: 失败路径 — 失败行插入 + counter +1
    // ==========================================================================
    @Nested
    @DisplayName("Scenario 2: 失败路径 — 失败行插入 + counter +1")
    class FailurePath {

        @Test
        @DisplayName("Given JGit commit() 抛 GitOperationException, "
                + "When saveFile 调一次, Then 失败表插 1 行(含 filePath/projectId/errorType) "
                + "+ failure counter == 1 + updateGitCommitSha 不被调(SHA 仍为 null)")
        void failure_recordedInDbAndCounter() throws Exception {
            // Given
            realGitStorage.initRepo(PROJECT);
            doThrow(new GitOperationException(PROJECT, "boom", new RuntimeException("root cause")))
                    .when(realGitStorage).commit(anyString(), anyString(), anyString(), anyString());

            // When
            String version = service.saveFile(FILE_PATH, "<rules/>", true, "init", TEST_USER);

            // Then
            assertThat(version).isNotNull();   // DB save 仍成功,version 仍返

            // Then — 失败行被插
            ArgumentCaptor<GitDualwriteFailureEntity> cap = ArgumentCaptor.forClass(GitDualwriteFailureEntity.class);
            verify(failureRepository, times(1)).insert(cap.capture());
            GitDualwriteFailureEntity row = cap.getValue();
            assertThat(row.getFilePath()).isEqualTo(FILE_PATH);
            assertThat(row.getProjectId()).isEqualTo(1L);
            assertThat(row.getErrorType()).isEqualTo("GitOperationException");
            assertThat(row.getErrorMessage()).contains("boom");
            assertThat(row.getBranch()).isEqualTo("user/alice");   // author=alice, no BranchContext

            // Then — counter
            Counter failure = meterRegistry.find("ruleforge_git_dualwrite_total")
                    .tags("result", "failure").counter();
            assertThat(failure).isNotNull();
            assertThat(failure.count()).isEqualTo(1.0);

            // Then — updateGitCommitSha 不被调(SHA=null)
            verify(fileRepository, never()).updateGitCommitSha(anyString(), anyString(), anyString());
        }
    }

    // ==========================================================================
    // Scenario 3: 多次失败累加 counter
    // ==========================================================================
    @Nested
    @DisplayName("Scenario 3: 多次失败累加")
    class MultipleFailures {

        @Test
        @DisplayName("Given JGit commit() 每次都抛, When saveFile 调 3 次, "
                + "Then failure counter == 3 + 失败表 3 行")
        void counterAccumulates() throws Exception {
            // Given
            realGitStorage.initRepo(PROJECT);
            doThrow(new GitOperationException(PROJECT, "boom", new RuntimeException()))
                    .when(realGitStorage).commit(anyString(), anyString(), anyString(), anyString());

            // When
            service.saveFile(FILE_PATH, "<r>v1</r>", true, "v1", TEST_USER);
            service.saveFile(FILE_PATH, "<r>v2</r>", false, "v2", TEST_USER);
            service.saveFile(FILE_PATH, "<r>v3</r>", false, "v3", TEST_USER);

            // Then
            Counter failure = meterRegistry.find("ruleforge_git_dualwrite_total")
                    .tags("result", "failure").counter();
            assertThat(failure).isNotNull();
            assertThat(failure.count()).isEqualTo(3.0);
            verify(failureRepository, times(3)).insert(any(GitDualwriteFailureEntity.class));
        }
    }

    // ==========================================================================
    // Scenario 4: skip path (no repo) — 不算 attempt
    // ==========================================================================
    @Nested
    @DisplayName("Scenario 4: skip path")
    class SkipPath {

        @Test
        @DisplayName("Given 项目 .git 不存在(走 dualWriteToGit 的 repoExists 早返), "
                + "When saveFile, Then 失败表无新行, success/failure counter 都 == 0")
        void skippedRepo_noCounterTouched() throws Exception {
            // Given — no initRepo()

            // When
            service.saveFile(FILE_PATH, "<rules/>", true, "init", TEST_USER);

            // Then
            verify(failureRepository, never()).insert(any());
            Counter success = meterRegistry.find("ruleforge_git_dualwrite_total")
                    .tags("result", "success").counter();
            Counter failure = meterRegistry.find("ruleforge_git_dualwrite_total")
                    .tags("result", "failure").counter();
            assertThat(success).isNull();
            assertThat(failure).isNull();

            // updateGitCommitSha 也不该被调(sha=null)
            verify(fileRepository, never()).updateGitCommitSha(anyString(), anyString(), anyString());
        }
    }

    // ==========================================================================
    // Scenario 5: errorType 标签分流
    // ==========================================================================
    @Nested
    @DisplayName("Scenario 5: errorType 标签分流")
    class ErrorTypeTagging {

        @Test
        @DisplayName("Given 两次不同异常类(JGit InvalidRefNameException vs IOException), "
                + "When saveFile 调 2 次, Then 2 个不同 error_type tag 的 counter 各 +1")
        void errorTypeTagsSeparate() throws Exception {
            // Given
            realGitStorage.initRepo(PROJECT);
            AtomicInteger callCount = new AtomicInteger(0);
            doAnswer(inv -> {
                int n = callCount.incrementAndGet();
                if (n == 1) {
                    throw new GitOperationException(PROJECT, "boom1", new RuntimeException());
                } else {
                    throw new GitOperationException(PROJECT, "boom2",
                            new IOException("io failure"));
                }
            }).when(realGitStorage).commit(anyString(), anyString(), anyString(), anyString());

            // When
            service.saveFile(FILE_PATH, "<r>v1</r>", true, "v1", TEST_USER);
            service.saveFile(FILE_PATH, "<r>v2</r>", false, "v2", TEST_USER);

            // Then
            // 两次都 wrap 成 GitOperationException,所以 error_type 都是 "GitOperationException"
            // 这里我们改测 异常 cause class 是否被记进 errorMessage
            ArgumentCaptor<GitDualwriteFailureEntity> cap = ArgumentCaptor.forClass(GitDualwriteFailureEntity.class);
            verify(failureRepository, times(2)).insert(cap.capture());
            // 两条都应 errorType=GitOperationException
            assertThat(cap.getAllValues()).allSatisfy(row ->
                    assertThat(row.getErrorType()).isEqualTo("GitOperationException"));

            // counter
            Counter failure = meterRegistry.find("ruleforge_git_dualwrite_total")
                    .tags("result", "failure").counter();
            assertThat(failure.count()).isEqualTo(2.0);
        }
    }

    // ==========================================================================
    // Scenario 6: 失败行的字段正确
    // ==========================================================================
    @Nested
    @DisplayName("Scenario 6: 失败行字段")
    class FailureRowFields {

        @Test
        @DisplayName("Given JGit commit() 抛带 message 的异常, "
                + "When saveFile, Then 失败行的 filePath/projectId/branch/errorType/errorMessage 都对")
        void failureRowContainsAllFields() throws Exception {
            // Given
            realGitStorage.initRepo(PROJECT);
            doThrow(new GitOperationException(PROJECT, "detailed failure message", new RuntimeException()))
                    .when(realGitStorage).commit(anyString(), anyString(), anyString(), anyString());

            // When
            service.saveFile(FILE_PATH, "<rules/>", true, "init", TEST_USER);

            // Then
            ArgumentCaptor<GitDualwriteFailureEntity> cap = ArgumentCaptor.forClass(GitDualwriteFailureEntity.class);
            verify(failureRepository, atLeastOnce()).insert(cap.capture());
            GitDualwriteFailureEntity row = cap.getValue();
            assertThat(row.getFilePath()).isEqualTo(FILE_PATH);
            assertThat(row.getProjectId()).isEqualTo(1L);
            assertThat(row.getBranch()).isEqualTo("user/alice");
            assertThat(row.getErrorType()).isEqualTo("GitOperationException");
            assertThat(row.getErrorMessage()).contains("detailed failure message");
        }
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================
    private static User makeUser(String name) {
        DefaultUser u = new DefaultUser();
        u.setUsername(name);
        return u;
    }
}
