package com.ruleforge.console.service.impl;

import com.ruleforge.console.entity.ProjectEntity;
import com.ruleforge.console.repository.data.ProjectRepository;
import com.ruleforge.console.repository.data.RuntimeRepository;
import com.ruleforge.console.repository.model.ResourcePackage;
import com.ruleforge.console.service.PermissionService;
import com.ruleforge.console.config.GitConfig;
import com.ruleforge.console.service.RepositoryInterceptor;
import com.ruleforge.console.storage.GitStorageService;
import com.ruleforge.console.storage.ProjectStorageService;
import com.ruleforge.console.storage.XmlCanonicalizer;
import com.ruleforge.console.repository.data.FileRepository;
import com.ruleforge.console.repository.data.LockRepository;
import com.ruleforge.console.repository.data.PackageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;

/**
 * Feature: 修复 PackageController.loadPackages NPE
 *
 * 背景: 当前端用不存在的 project 名调 /ruleforge/packageeditor/loadPackages
 *   (e.g. Playwright 截 editor.html?type=package 时,project='demo' 但 demo 不存在)
 *   服务端走到 RuleForgeRepositoryServiceImpl:128-129:
 *     InputStream inputStream = readFile(filePath, version);
 *     String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8);  // NPE
 *   抛出 500,前端 bootbox 弹"加载数据失败"。
 *
 * 修复目标: 项目 res-package.xml 不存在时,返空列表(不抛 NPE)。
 *   - 旧行为: 500 NullPointerException
 *   - 新行为: 200 + [],控制台打 WARN 日志
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RuleForgeRepositoryServiceImpl.loadProjectResourcePackages - 修复 NPE")
class RuleForgeRepositoryServiceImplLoadPackagesTest {

    @Mock private PermissionService permissionService;
    @Mock private ProjectRepository projectRepository;
    @Mock private FileRepository fileRepository;
    @Mock private LockRepository lockRepository;
    @Mock private PackageRepository packageRepository;
    @Mock private RuntimeRepository runtimeRepository;
    @Mock private ProjectStorageService projectStorageService;
    @Mock private RepositoryInterceptor repositoryInterceptor;
    @Mock private GitStorageService gitStorageService;
    @Mock private GitConfig gitConfig;
    @Mock private XmlCanonicalizer xmlCanonicalizer;
    @Spy @InjectMocks private RuleForgeRepositoryServiceImpl service;

    private ProjectEntity demoProject() {
        ProjectEntity e = new ProjectEntity();
        e.setId(1L);
        e.setName("demo");
        return e;
    }

    @Nested
    @DisplayName("Scenario: 项目 res-package.xml 不存在")
    class ProjectFileMissing {

        // Given: project='demo' 在 DB 中存在
        //   但 readFile('/demo/.ruleforge/res-package.xml', null) 返 null
        // When:  调 service.loadProjectResourcePackages('demo')
        // Then:  返空 list, 不抛 NPE
        @Test
        @DisplayName("readFile 返 null 时,返空 list 而不是 NPE")
        void shouldReturnEmptyListWhenResPackageFileMissing() throws Exception {
            doReturn(demoProject()).when(projectRepository).findByName("demo");
            doReturn(null).when(service).readFile(any(), isNull());

            List<ResourcePackage> result = service.loadProjectResourcePackages("demo");

            assertThat(result).isNotNull().isEmpty();
        }

        // Given: project='demo:1.0' (指定 version)
        //   readFile(file, '1.0') 返 null
        // When:  调 service.loadProjectResourcePackages('demo', '1.0')
        // Then:  返空 list
        @Test
        @DisplayName("带 version 参数且文件不存在时,返空 list")
        void shouldReturnEmptyListWhenVersionedFileMissing() throws Exception {
            doReturn(demoProject()).when(projectRepository).findByName("demo");
            doReturn(null).when(service).readFile(any(), any());

            List<ResourcePackage> result = service.loadProjectResourcePackages("demo", "1.0");

            assertThat(result).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("Scenario: 项目 res-package.xml 存在但内容为空")
    class ProjectFileEmpty {

        // Given: project='demo' res-package.xml 存在但为空字符串
        // When:  调 service.loadProjectResourcePackages('demo')
        // Then:  返空 list(不抛 DocumentException 也不抛 NPE)
        @Test
        @DisplayName("空内容文件返空 list")
        void shouldReturnEmptyListWhenFileEmpty() throws Exception {
            doReturn(demoProject()).when(projectRepository).findByName("demo");
            InputStream empty = new java.io.ByteArrayInputStream(new byte[0]);
            doReturn(empty).when(service).readFile(any(), isNull());

            List<ResourcePackage> result = service.loadProjectResourcePackages("demo");

            assertThat(result).isNotNull().isEmpty();
        }
    }
}
