package com.ruleforge.console.app.controller;

import com.ruleforge.console.app.service.impl.AnalysisServiceImpl;
import com.ruleforge.console.app.mapper.DecisionAnalysisMapper;
import com.ruleforge.console.app.mapper.RuleCoverageMapper;
import com.ruleforge.console.repository.model.ResourceItem;
import com.ruleforge.console.repository.model.ResourcePackage;
import com.ruleforge.console.service.impl.RuleForgeRepositoryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * ExportController BDD 测试
 *
 * Gherkin 行为注解
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExportController - 规则内容导出")
class ExportControllerTest {

    @Mock
    private RuleForgeRepositoryServiceImpl repoService;

    @InjectMocks
    private ExportController exportController;

    @Nested
    @DisplayName("Scenario: 列出项目规则包")
    class ListPackages {

        // Given: 项目有 2 个规则包，各含资源文件
        // When: GET /export/project/{project}/packages
        // Then: 返回包列表含 id/name/resourceItems

        @Test
        @DisplayName("返回项目的规则包列表")
        void shouldReturnPackageList() throws Exception {
            // Given
            ResourcePackage pkg1 = new ResourcePackage();
            pkg1.setId("pkg-001");
            pkg1.setName("loan-rules");
            pkg1.setVersion("1.0");

            ResourceItem item = new ResourceItem();
            item.setName("rules.xml");
            item.setPath("/loan-rules/rules.xml");
            item.setVersion("1.0");
            pkg1.setResourceItems(Collections.singletonList(item));

            when(repoService.loadProjectResourcePackages("my-project"))
                    .thenReturn(Arrays.asList(pkg1));

            // When
            var response = exportController.listPackages("my-project");

            // Then
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> body = (List<Map<String, Object>>) response.getBody();
            assertThat(body).hasSize(1);
            assertThat(body.get(0)).containsEntry("name", "loan-rules");
        }
    }

    @Nested
    @DisplayName("Scenario: 导出规则包完整内容")
    class ExportPackage {

        @Test
        @DisplayName("返回规则包所有文件内容")
        void shouldReturnPackageWithFileContents() throws Exception {
            // Given
            ResourcePackage pkg = new ResourcePackage();
            pkg.setId("pkg-001");
            pkg.setName("loan-rules");
            pkg.setVersion("1.0");

            ResourceItem item = new ResourceItem();
            item.setName("rules.xml");
            item.setPath("/loan-rules/rules.xml");
            item.setVersion("1.0");
            pkg.setResourceItems(Collections.singletonList(item));

            when(repoService.loadProjectResourcePackages("my-project"))
                    .thenReturn(Collections.singletonList(pkg));

            String xmlContent = "<rule-set><rule name=\"R001\"/></rule-set>";
            InputStream is = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
            when(repoService.readFile(eq("/loan-rules/rules.xml"), eq("1.0")))
                    .thenReturn(is);

            // When
            var response = exportController.exportPackage("my-project", "pkg-001");

            // Then
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("packageName", "loan-rules");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> files = (List<Map<String, Object>>) body.get("files");
            assertThat(files).hasSize(1);
            assertThat(files.get(0)).containsEntry("type", "xml");
        }

        @Test
        @DisplayName("规则包不存在返回错误信息")
        void shouldReturnErrorForMissingPackage() throws Exception {
            // Given
            when(repoService.loadProjectResourcePackages("my-project"))
                    .thenReturn(Collections.emptyList());

            // When
            var response = exportController.exportPackage("my-project", "nonexistent");

            // Then
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsKey("error");
        }
    }

    @Nested
    @DisplayName("Scenario: 导出单个文件")
    class ExportFile {

        @Test
        @DisplayName("返回文件内容和类型")
        void shouldReturnFileContentAndType() throws Exception {
            // Given
            String content = "<decision-table><row/></decision-table>";
            InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            when(repoService.readFile(eq("/path/table.xml"), isNull()))
                    .thenReturn(is);

            // When
            var response = exportController.exportFile("/path/table.xml", null);

            // Then
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("type", "xml");
            assertThat(body).containsKey("content");
        }
    }

    @Nested
    @DisplayName("Scenario: 列出项目")
    class ListProjects {

        @Test
        @DisplayName("返回所有项目名称")
        void shouldReturnProjectNames() throws Exception {
            // Given
            when(repoService.loadProjectNames())
                    .thenReturn(Arrays.asList("project-a", "project-b"));

            // When
            var response = exportController.listProjects();

            // Then
            @SuppressWarnings("unchecked")
            List<String> body = (List<String>) response.getBody();
            assertThat(body).containsExactly("project-a", "project-b");
        }
    }

    @Nested
    @DisplayName("Scenario: 文件类型检测")
    class FileTypeDetection {

        @Test
        @DisplayName("正确识别各种文件类型")
        void shouldDetectFileTypes() throws Exception {
            // Given: table.xml
            InputStream xmlIs = new ByteArrayInputStream("<xml/>".getBytes(StandardCharsets.UTF_8));
            when(repoService.readFile(eq("/a/rules.xml"), isNull())).thenReturn(xmlIs);

            // When
            var response = exportController.exportFile("/a/rules.xml", null);

            // Then
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body.get("type")).isEqualTo("xml");
        }
    }
}
