package com.ruleforge.console.service.impl;

import com.ruleforge.console.config.GitConfig;
import com.ruleforge.console.model.PackageConfig;
import com.ruleforge.console.repository.data.FileRepository;
import com.ruleforge.console.repository.data.LockRepository;
import com.ruleforge.console.repository.data.PackageRepository;
import com.ruleforge.console.repository.data.ProjectRepository;
import com.ruleforge.console.repository.data.RuntimeRepository;
import com.ruleforge.console.service.PermissionService;
import com.ruleforge.console.service.RepositoryInterceptor;
import com.ruleforge.console.storage.GitStorageService;
import com.ruleforge.console.storage.ProjectStorageService;
import com.ruleforge.console.storage.XmlCanonicalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * V5.9.0 100% audit fix: loadPackageConfigs 之前在 empty project / 缺文件 / XML 解析失败
 * 全部 NPE → 500。现在 3 个 path 都返空 PackageConfig + WARN,前端拿 200 不会触发
 * bootbox。
 *
 * Gherkin 语义:
 *  Given: 一个空/无效 project name 传给 loadPackageConfigs
 *  When:  服务调用
 *  Then: 返 PackageConfig 实例(不是 null、不是抛异常)
 */
@ExtendWith(MockitoExtension.class)
class RuleForgeRepositoryServiceImplLoadPackageConfigsTest {

    @Mock
    private FileRepository fileRepository;
    @Mock
    private LockRepository lockRepository;
    @Mock
    private PackageRepository packageRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private RuntimeRepository runtimeRepository;
    @Mock
    private PermissionService permissionService;
    @Mock
    private ProjectStorageService projectStorageService;
    @Mock
    private RepositoryInterceptor repositoryInterceptor;
    @Mock
    private GitStorageService gitStorageService;
    @Mock
    private GitConfig gitConfig;
    @Mock
    private XmlCanonicalizer xmlCanonicalizer;

    @Spy
    @InjectMocks
    private RuleForgeRepositoryServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        // 默认 mock: readFile 返 null (即文件不存在) — 配合各 scenario 走不同 path
    }

    @Nested
    class Feature_LoadPackageConfigs_handles_invalid_inputs {

        @Test
        void Given_empty_project_name_When_loadPackageConfigs_Then_returns_empty_config() throws Exception {
            // Given: null / "" / "   " 都视为 empty
            // When/Then:
            PackageConfig r1 = service.loadPackageConfigs(null);
            PackageConfig r2 = service.loadPackageConfigs("");
            PackageConfig r3 = service.loadPackageConfigs("   ");

            // Then: 全部返非 null PackageConfig(空实例),不抛异常
            assertNotNull(r1);
            assertNotNull(r2);
            assertNotNull(r3);
        }

        @Test
        void Given_file_not_found_When_loadPackageConfigs_Then_returns_empty_config() throws Exception {
            // Given: readFile 返 null (文件不存在)
            // When: 调用
            // Then: 返非 null PackageConfig
            // (需要用 spy 拦截 readFile 调用,因为 readFile 是 private)
            doReturn(null).when(service).readFile(any(String.class));

            PackageConfig r = service.loadPackageConfigs("nonexistent_proj");
            assertNotNull(r);
        }

        @Test
        void Given_invalid_xml_content_When_loadPackageConfigs_Then_returns_empty_config() throws Exception {
            // Given: 文件存在但内容是 invalid XML
            InputStream badXml = new ByteArrayInputStream("<<<not-xml>>>".getBytes());
            doReturn(badXml).when(service).readFile(any(String.class));

            // When/Then: 不抛 DocumentException,返空 PackageConfig
            PackageConfig r = service.loadPackageConfigs("bad_xml_proj");
            assertNotNull(r);
        }

        @Test
        void Given_valid_empty_xml_When_loadPackageConfigs_Then_returns_empty_config() throws Exception {
            // Given: 空 XML root (无 attribute)
            InputStream emptyXml = new ByteArrayInputStream(
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?><package-config></package-config>"
                            .getBytes());
            doReturn(emptyXml).when(service).readFile(any(String.class));

            // When/Then
            PackageConfig r = service.loadPackageConfigs("good_proj");
            assertNotNull(r);
        }
    }
}
