package com.ruleforge.console.service.impl;

import com.ruleforge.console.config.GitConfig;
import com.ruleforge.console.entity.FileEntity;
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
import com.ruleforge.console.storage.BranchContext;
import com.ruleforge.console.storage.GitStorageService;
import com.ruleforge.console.storage.ProjectStorageService;
import com.ruleforge.console.storage.XmlCanonicalizer;
import com.ruleforge.console.storage.impl.GitStorageServiceImpl;
import com.ruleforge.console.storage.model.GitOperationException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 5.10-A BDD: RuleForgeRepositoryServiceImpl 真 Git 集成测试
 *
 * 与 {@link com.ruleforge.console.storage.GitStorageServiceImplTest} 的区别:
 *  - 那个测 GitStorageServiceImpl 本身(GitStorageService 单层)
 *  - 这个测 RuleForgeRepositoryServiceImpl 的 saveFile → dualWriteToGit → JGit 真写盘 → tryReadFromGit 读回
 *
 * 目标:锁住 saveFile 走 dualWriteToGit 路径,防止以后 refactor 把它静默绕过
 *
 * Feature: RuleForgeRepositoryServiceImpl 真 Git 集成
 *
 * Rule: saveFile 走 main 分支,DB 写成功 + Git commit 成功
 * Rule: per-user branch 在 BranchContext.setBranch 后,saveFile 写到 user/{name} 分支
 * Rule: readFile 在 DB fileContent=null 时,自动 fallback 到 Git
 * Rule: Git 写失败时,sha 返 null,DB 写入仍视为成功
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RuleForgeRepositoryServiceImpl 真 Git 集成 (5.10-A)")
class RuleForgeRepositoryServiceImplGitStorageIntegrationTest {

    @TempDir
    Path gitBaseDir;

    // ============== 真实 Git 组件 (每个 test 重建,test 间隔离) ==============
    private GitConfig realGitConfig;
    private GitStorageService realGitStorage;

    // ============== Mocked 依赖(DB 那一层) ==============
    @Mock private PermissionService permissionService;
    @Mock private ProjectRepository projectRepository;
    @Mock private FileRepository fileRepository;
    @Mock private LockRepository lockRepository;
    @Mock private PackageRepository packageRepository;
    @Mock private RuntimeRepository runtimeRepository;
    @Mock private ProjectStorageService projectStorageService;
    @Mock private RepositoryInterceptor repositoryInterceptor;
    @Mock private XmlCanonicalizer xmlCanonicalizer;
    @Mock private GitDualwriteFailureRepository dualwriteFailureRepository;

    private RuleForgeRepositoryServiceImpl service;
    private MeterRegistry meterRegistry;
    /** 单 test 用的 project 名,scenario 各自指定 → test 间隔离 */
    private String project;
    private String filePath;

    private static final User TEST_USER = makeUser("alice");

    @BeforeEach
    void setUp() throws Exception {
        // 1. 每个 test 新 GitConfig + 新 GitStorageServiceImpl,新 project
        realGitConfig = new GitConfig();
        realGitConfig.setBase(gitBaseDir.toString());
        realGitStorage = new GitStorageServiceImpl(realGitConfig);

        // 子类 @BeforeEach 之后会设 project + initRepo
        // 这里先 build service,project 暂时用一个占位
        this.project = placeholderProject();
        this.filePath = "/" + this.project + "/rules.xml";

        // 2. 装真 GitStorageService,DB 全 mock
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
                dualwriteFailureRepository,
                meterRegistry
        );

        // 3. 通用 stub
        when(permissionService.projectPackageHasWritePermission(anyString())).thenReturn(true);
        when(permissionService.fileHasWritePermission(anyString())).thenReturn(true);
        when(projectRepository.findByName(anyString())).thenAnswer(inv ->
                buildProject(inv.getArgument(0)));
        when(lockRepository.findByResource(anyString())).thenReturn(null);
        when(lockRepository.insert(any(LockEntity.class))).thenAnswer(inv -> {
            LockEntity e = inv.getArgument(0);
            e.setId(System.nanoTime());
            return e;
        });
        when(fileRepository.findByFilePath(anyString())).thenAnswer(inv ->
                buildFile(project, inv.getArgument(0)));
        when(fileRepository.findLatestByFileId(any(), anyBoolean())).thenReturn(null);
        when(fileRepository.findLatestReleaseByFilePathFull(anyString())).thenReturn(null);
        when(xmlCanonicalizer.canonicalize(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    private String placeholderProject() {
        return "test-proj-" + System.nanoTime();
    }

    @AfterEach
    void tearDown() {
        BranchContext.clear();
    }

    // ==========================================================================
    // Scenario 1: main 分支写
    // ==========================================================================
    @Nested
    @DisplayName("Scenario 1: main 分支显式写 (BranchContext=main)")
    class MainBranchWrite {

        @BeforeEach
        void pinMainBranch() {
            project = "main-write-" + System.nanoTime();
            filePath = "/" + project + "/rules.xml";
            realGitStorage.initRepo(project);
            // 显式锁 main,因为生产代码默认走 user/{author}
            BranchContext.setBranch("main");
        }

        @Test
        @DisplayName("Given 项目已 init + BranchContext=main, When saveFile, Then .git 出现 1 commit + readFile 读回一致")
        void saveFileCreatesGitCommit() throws Exception {
            // Given
            String content = "<rules><r id='1'/></rules>";

            // When
            String version = service.saveFile(filePath, content, true, "init", testUser());

            // Then
            assertThat(version).isNotNull();
            assertThat(realGitStorage.repoExists(project)).isTrue();

            String gitPath = project + "/rules.xml";
            String mainContent = realGitStorage.readFile(project, "main", gitPath);
            assertThat(mainContent).isEqualTo(content);

            // 验证 dualWrite 走 main 分支,没走 user 分支
            assertThat(realGitStorage.readFile(project, "user/alice", gitPath)).isNull();
        }

        @Test
        @DisplayName("Given 同一个 filePath, When saveFile 调两次, Then Git 出现 2 个 commit,readFile 返最新")
        void twoSavesCreateTwoCommits() throws Exception {
            // Given
            String v1 = "<rules>v1</rules>";
            String v2 = "<rules>v2</rules>";

            // When
            service.saveFile(filePath, v1, true, "v1", testUser());
            service.saveFile(filePath, v2, false, "v2", testUser());

            // Then
            String gitPath = project + "/rules.xml";
            String mainContent = realGitStorage.readFile(project, "main", gitPath);
            assertThat(mainContent).isEqualTo(v2);
        }
    }

    // ==========================================================================
    // Scenario 2: per-user branch
    // ==========================================================================
    @Nested
    @DisplayName("Scenario 2: per-user branch (BranchContext)")
    class PerUserBranch {

        @BeforeEach
        void initProject() {
            project = "user-branch-" + System.nanoTime();
            filePath = "/" + project + "/rules.xml";
            realGitStorage.initRepo(project);
        }

        @Test
        @DisplayName("Given BranchContext=user/alice, When saveFile, Then 写到 user/alice 分支,main 不动")
        void branchContextDirectsWrites() throws Exception {
            // Given
            BranchContext.setBranch("user/alice");
            String content = "<rules>alice-edit</rules>";

            // When
            service.saveFile(filePath, content, true, "alice edit", testUser());

            // Then
            String gitPath = project + "/rules.xml";
            assertThat(realGitStorage.readFile(project, "user/alice", gitPath))
                    .isEqualTo(content);
            assertThat(realGitStorage.readFile(project, "main", gitPath))
                    .isNull();
        }

        @Test
        @DisplayName("Given BranchContext 没设, When saveFile author=alice, Then dualWrite 内部用 BranchContext.forUser(alice)")
        void authorUsedAsBranchWhenNoContext() throws Exception {
            // Given — @AfterEach already clear 了
            assertThat(BranchContext.getBranch()).isNull();
            String content = "<rules>auto-user-branch</rules>";

            // When
            service.saveFile(filePath, content, true, "first", testUser());

            // Then — author "alice" → BranchContext.forUser → "user/alice"
            String gitPath = project + "/rules.xml";
            assertThat(realGitStorage.readFile(project, "user/alice", gitPath))
                    .isEqualTo(content);
        }
    }

    // ==========================================================================
    // Scenario 3: readFile Git fallback
    // ==========================================================================
    @Nested
    @DisplayName("Scenario 3: readFile Git fallback")
    class ReadFileFallback {

        @BeforeEach
        void pinMainBranch() {
            project = "read-fb-" + System.nanoTime();
            filePath = "/" + project + "/rules.xml";
            realGitStorage.initRepo(project);
            // readFile 走 BranchContext.getBranch() ?? "main",所以不设默认读 main
            BranchContext.setBranch("main");
        }

        @Test
        @DisplayName("Given 先 save 进 Git (BranchContext=main), When readFile, Then 从 Git main 读回 + 内容一致")
        void readFileFallsBackToGitWhenDbContentNull() throws Exception {
            // Given — 先 save 进 Git
            String content = "<rules>fallback-content</rules>";
            service.saveFile(filePath, content, true, "init", testUser());

            // When — readFile 时,DB 仍能查到 entity 但 content=null(模拟 Git-first storage 模式)
            // (默认 mock findByFilePathForRead 返 null,tryReadFromGit 命中)
            InputStream stream = service.readFile(filePath, "latest", null, true);

            // Then
            assertThat(stream).isNotNull();
            String read = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            stream.close();
            assertThat(read).isEqualTo(content);
        }

        @Test
        @DisplayName("Given DB 和 Git 都没有这个文件, When readFile, Then 返 null(不抛 NPE)")
        void readFileReturnsNullWhenNotInGitOrDb() throws Exception {
            // Given — 项目存在但没文件
            String nonExistentPath = "/" + project + "/never-saved.xml";
            when(fileRepository.findByFilePathForRead(any(), any(), any(), anyBoolean()))
                    .thenReturn(null);

            // When
            InputStream stream = service.readFile(nonExistentPath, "latest", null, true);

            // Then
            assertThat(stream).isNull();
        }
    }

    // ==========================================================================
    // Scenario 4: Git 写失败
    // ==========================================================================
    @Nested
    @DisplayName("Scenario 4: Git 写失败处理")
    class GitWriteFailure {

        @BeforeEach
        void initProject() {
            project = "git-fail-" + System.nanoTime();
            filePath = "/" + project + "/rules.xml";
            realGitStorage.initRepo(project);
        }

        @Test
        @DisplayName("Given BranchContext 指向非法 branch 名, When saveFile, Then JGit 抛 InvalidRefNameException 被 dualWriteToGit 吞掉,saveFile 仍返 version")
        void dualWriteFailureDoesNotBlockSave() throws Exception {
            // Given — JGit 拒收 `..` 字符,writeFile 会抛 InvalidRefNameException
            BranchContext.setBranch("user/with..invalid..dots");

            // When + Then: saveFile 应正常返回 version,不抛
            String version = service.saveFile(filePath, "<rules>x</rules>", true, "test", testUser());
            assertThat(version).isNotNull();
        }
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================
    private static ProjectEntity buildProject(String name) {
        ProjectEntity p = new ProjectEntity();
        p.setId(1L);
        p.setName(name);
        return p;
    }

    private static FileEntity buildFile(String project, String path) {
        FileEntity f = new FileEntity();
        f.setId(10L);
        f.setName("rules.xml");
        f.setFilePath(path);
        f.setProjectId(1L);
        return f;
    }

    private static User makeUser(String name) {
        DefaultUser u = new DefaultUser();
        u.setUsername(name);
        return u;
    }

    // expose to inner @Nested classes
    private User testUser() { return TEST_USER; }
}
