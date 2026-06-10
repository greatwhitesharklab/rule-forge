package com.ruleforge.console.service.impl;

import com.ruleforge.console.config.GitConfig;
import com.ruleforge.console.model.User;
import com.ruleforge.console.repository.data.FileRepository;
import com.ruleforge.console.repository.data.LockRepository;
import com.ruleforge.console.repository.data.PackageRepository;
import com.ruleforge.console.repository.data.ProjectRepository;
import com.ruleforge.console.repository.data.RuntimeRepository;
import com.ruleforge.console.entity.FileEntity;
import com.ruleforge.console.service.PermissionService;
import com.ruleforge.console.service.RepositoryInterceptor;
import com.ruleforge.console.storage.GitStorageService;
import com.ruleforge.console.storage.ProjectStorageService;
import com.ruleforge.console.storage.XmlCanonicalizer;
import com.ruleforge.console.repository.model.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V5.24 — createFile 自动建 parent folder(沿用 uruleV1 JCR-style 体验)。
 *
 * <p>动机:之前 createFile 在 parent folder 不存在时直接 NPE(parentFile.getProjectId())。
 * LLM agent / CLI 调 {@code rf file create <path>} 时被迫先手动建每一层 folder,
 * 跟 uruleV1 的 {@code /frame/createFile} 不一样。本测试套验证:
 * <ol>
 *   <li>parent 已存在 → 跳过 ensureParentFolders,直接走 createFileNode 主路径</li>
 *   <li>中间缺一层 → 自动建中间 folder</li>
 *   <li>缺多层 → 逐层向上建</li>
 *   <li>ancestor 完全不存在(连 project root 都没有)→ 抛 RuleException</li>
 *   <li>并发场景:race 期间另一线程建好 → 跳过,继续走主路径</li>
 * </ol>
 *
 * <p>关键设计 — ensureParentFolders 返回新建/已存在的 FileEntity(连同 projectId),
 * caller 不用再 re-fetch,这样在 mock 测试里能精确知道每次 query 的返回值。
 */
@ExtendWith(MockitoExtension.class)
class RuleForgeRepositoryServiceImplEnsureParentFoldersTest {

    @Mock private FileRepository fileRepository;
    @Mock private LockRepository lockRepository;
    @Mock private PackageRepository packageRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private RuntimeRepository runtimeRepository;
    @Mock private PermissionService permissionService;
    @Mock private ProjectStorageService projectStorageService;
    @Mock private RepositoryInterceptor repositoryInterceptor;
    @Mock private GitStorageService gitStorageService;
    @Mock private GitConfig gitConfig;
    @Mock private XmlCanonicalizer xmlCanonicalizer;
    @Mock private User adminUser;

    @Spy @InjectMocks
    private RuleForgeRepositoryServiceImpl service;

    @BeforeEach
    void setUp() {
        // admin 权限(用 lenient 因为有些 scenario 不调用 createFile)
        org.mockito.Mockito.lenient().when(permissionService.isAdmin()).thenReturn(true);
        org.mockito.Mockito.lenient().when(adminUser.getUsername()).thenReturn("admin");
        // 默认 findRelationsByDescendant 返空 list,避免后续 stream forEach NPE
        org.mockito.Mockito.lenient().when(fileRepository.findRelationsByDescendant(any(Long.class)))
            .thenReturn(java.util.Collections.emptyList());
        // 模拟 MyBatis-Plus auto-fill: insert 后回填 id
        java.util.concurrent.atomic.AtomicLong idSeq = new java.util.concurrent.atomic.AtomicLong(1000L);
        org.mockito.Mockito.lenient().doAnswer(inv -> {
            FileEntity f = inv.getArgument(0);
            if (f.getId() == null) {
                f.setId(idSeq.getAndIncrement());
            }
            return null;
        }).when(fileRepository).insert(any(FileEntity.class));
    }

    private FileEntity mockFile(String path, long projectId, int typeOrdinal) {
        FileEntity f = new FileEntity();
        f.setId(System.nanoTime() + path.hashCode()); // unique-ish id,规避 null pointer
        f.setFilePath(path);
        f.setProjectId(projectId);
        f.setFileType(typeOrdinal);
        return f;
    }

    @Nested
    @DisplayName("Scenario: createFile 自动建 parent folder chain")
    class AutoCreateParents {

        @Test
        @DisplayName("Given parent folder 已存在 When createFile Then 不调 ensureParentFolders 内部 insert")
        void givenParentExists_whenCreateFile_thenNoInsert() throws Exception {
            // Given: /proj/a 是 folder, /proj/a/b.dt.xml 的 parent 就是 /proj/a
            when(fileRepository.findByFilePathNeType("/proj/a", Type.project.ordinal()))
                .thenReturn(mockFile("/proj/a", 1L, Type.folder.ordinal()));
            when(fileRepository.findByFilePathSelectId("/proj/a/b.dt.xml")).thenReturn(null); // 不存在

            // When
            service.createFile("/proj/a/b.dt.xml", "<xml/>", adminUser);

            // Then: 应该只 insert 1 次(给 b.dt.xml),folder 没新建
            verify(fileRepository, times(1)).insert(any(FileEntity.class));
        }

        @Test
        @DisplayName("Given 中间缺一层 folder (/proj/a/b/c.dt.xml,a 在,b 不在) When createFile Then 自动建 /proj/a/b")
        void givenMissingMidFolder_whenCreateFile_thenAutoCreate() throws Exception {
            // Given: /proj/a 存在(folder),/proj/a/b 不存在
            // file = /proj/a/b/c.dt.xml,parentPath = /proj/a/b
            // call sequence:
            //   1) createFileNode 查 /proj/a/b  → null (parent missing)
            //   2) ensureParentFolders 查 /proj/a/b → null (decide to recurse)
            //   3) ensureParentFolders 查 /proj/a  → 存在 (ancestor, 触发 insert)
            //   4) ensureParentFolders race re-check /proj/a/b → null (insert 进行中)
            //   → insert folder b, return it
            when(fileRepository.findByFilePathNeType("/proj/a/b", Type.project.ordinal()))
                .thenReturn(null)   // 1) createFileNode parent lookup
                .thenReturn(null)   // 2) ensureParentFolders 查自身 → 决定要递归
                .thenReturn(null);  // 4) race re-check after ancestor exists
            when(fileRepository.findByFilePathNeType("/proj/a", Type.project.ordinal()))
                .thenReturn(mockFile("/proj/a", 1L, Type.folder.ordinal())); // 3) ancestor lookup

            when(fileRepository.findByFilePathSelectId("/proj/a/b/c.dt.xml")).thenReturn(null);

            // When
            service.createFile("/proj/a/b/c.dt.xml", "<xml/>", adminUser);

            // Then: insert 2 次(folder b + file c.dt.xml)
            verify(fileRepository, times(2)).insert(any(FileEntity.class));
            verify(repositoryInterceptor, times(1)).createFile(eq("/proj/a/b"), eq(null));
            verify(repositoryInterceptor, times(1)).createFile(eq("/proj/a/b/c.dt.xml"), eq("<xml/>"));
        }

        @Test
        @DisplayName("Given 缺多层 folder (file=/proj/x/y/z/q.dt.xml,x 在,y/z 都不在) When createFile Then 逐层建 y, z")
        void givenMultipleMissingFolders_whenCreateFile_thenBuildAll() throws Exception {
            // Given: /proj/x 存在,/proj/x/y 和 /proj/x/y/z 都不存在
            // file = /proj/x/y/z/q.dt.xml,parentPath = /proj/x/y/z
            // call sequence:
            //   1) createFileNode 查 /proj/x/y/z → null
            //   2) ensureParentFolders 查 /proj/x/y/z → null (decide to recurse)
            //   3) ensureParentFolders 查 /proj/x/y → null (decide to recurse)
            //   4) ensureParentFolders 查 /proj/x → 存在 (ancestor)
            //   5) race re-check /proj/x/y → null (insert 进行中) → insert y
            //   6) race re-check /proj/x/y/z → null (insert 进行中) → insert z
            when(fileRepository.findByFilePathNeType("/proj/x/y/z", Type.project.ordinal()))
                .thenReturn(null)   // 1) createFileNode parent lookup
                .thenReturn(null)   // 2) ensureParentFolders 查自身
                .thenReturn(null);  // 6) race re-check /proj/x/y/z → insert z
            when(fileRepository.findByFilePathNeType("/proj/x/y", Type.project.ordinal()))
                .thenReturn(null)   // 3) ensureParentFolders 查自身
                .thenReturn(null);  // 5) race re-check /proj/x/y → insert y
            when(fileRepository.findByFilePathNeType("/proj/x", Type.project.ordinal()))
                .thenReturn(mockFile("/proj/x", 1L, Type.folder.ordinal())); // 4) ancestor

            when(fileRepository.findByFilePathSelectId("/proj/x/y/z/q.dt.xml")).thenReturn(null);

            // When
            service.createFile("/proj/x/y/z/q.dt.xml", "<xml/>", adminUser);

            // Then: insert 3 次(folder y + folder z + file q.dt.xml)
            verify(fileRepository, times(3)).insert(any(FileEntity.class));
            verify(repositoryInterceptor).createFile(eq("/proj/x/y"), eq(null));
            verify(repositoryInterceptor).createFile(eq("/proj/x/y/z"), eq(null));
            verify(repositoryInterceptor).createFile(eq("/proj/x/y/z/q.dt.xml"), eq("<xml/>"));
        }

        @Test
        @DisplayName("Given ancestor 完全不存在(连 /proj root 都没有) When createFile Then 抛 RuleException")
        void givenNoAncestor_whenCreateFile_thenThrows() throws Exception {
            // Given: parent 完全不存在,ensureParentFolders 递归到 root 都找不到
            // /proj/orphan.dt.xml 的 parentPath = /proj
            // call sequence:
            //   1) createFileNode 查 /proj → null (没 project type row)
            //   2) ensureParentFolders 查 /proj → null (走到 root 分支,再查一次确认)
            //   3) ensureParentFolders root 分支查 /proj → null → 抛
            when(fileRepository.findByFilePathNeType("/proj", Type.project.ordinal()))
                .thenReturn(null);   // 没 project type row

            when(fileRepository.findByFilePathSelectId("/proj/orphan.dt.xml")).thenReturn(null);

            // When/Then
            assertThatThrownBy(() -> service.createFile("/proj/orphan.dt.xml", "<xml/>", adminUser))
                .isInstanceOf(com.ruleforge.exception.RuleException.class)
                .hasMessageContaining("Cannot resolve ancestor");
        }

        @Test
        @DisplayName("Given parent 已被另一并发请求建好(race) When createFile Then 不重复建,继续走主路径")
        void givenConcurrentParentBuild_whenCreateFile_thenIdempotent() throws Exception {
            // Given: 第一次查 parent 不存在,但 race 期间另一线程建了。ensureParentFolders
            // 建 ancestor 之后,再查 parent 发现已存在 → 跳过,继续
            // call sequence:
            //   1) createFileNode 查 /proj/a/b → null
            //   2) ensureParentFolders 查 /proj/a/b → null (decide to recurse)
            //   3) ensureParentFolders 查 /proj/a → 存在 (ancestor)
            //   4) race re-check /proj/a/b → 已存在(被另一线程建了),直接 return
            when(fileRepository.findByFilePathNeType("/proj/a/b", Type.project.ordinal()))
                .thenReturn(null)   // 1) createFileNode parent lookup
                .thenReturn(null)   // 2) ensureParentFolders 查自身 → 决定要递归
                .thenReturn(mockFile("/proj/a/b", 1L, Type.folder.ordinal())); // 4) race re-check: 已经存在
            when(fileRepository.findByFilePathNeType("/proj/a", Type.project.ordinal()))
                .thenReturn(mockFile("/proj/a", 1L, Type.folder.ordinal())); // 3) ancestor

            when(fileRepository.findByFilePathSelectId("/proj/a/b/c.dt.xml")).thenReturn(null);

            // When
            service.createFile("/proj/a/b/c.dt.xml", "<xml/>", adminUser);

            // Then: race 后只 insert 文件本身(没 insert folder)
            verify(fileRepository, times(1)).insert(any(FileEntity.class));
        }
    }
}
