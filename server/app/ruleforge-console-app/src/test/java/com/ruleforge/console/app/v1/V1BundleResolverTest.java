package com.ruleforge.console.app.v1;

import com.ruleforge.console.entity.FileEntity;
import com.ruleforge.console.entity.ProjectEntity;
import com.ruleforge.console.repository.data.FileRepository;
import com.ruleforge.console.repository.data.ProjectRepository;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import com.ruleforge.exception.RuleException;
import com.ruleforge.v1.ast.NodeBase;
import com.ruleforge.v1.ast.RuleAsset;
import com.ruleforge.v1.ast.library.Libraries;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Feature: V7.6 V1 原生发布 — 闭包解析(V1BundleResolver)
 *
 * <p>背景:V1 决策流({@code .v1flow.json})的规则节点用 {@code ruleRef} 引用独立规则文件
 * (V7.5),库引用独立 {@code .v1lib.json}(V7.4.1)。发布要冻结"可执行闭包"——
 * asset + 各 ruleRef 规则文件 + 项目库(vl/cl/pl)整体打包成 bundle,executor 拉到即跑。
 *
 * <p>V1BundleResolver 把前端没端到端接的引用解析(ruleRef / 库)搬到服务端:读 flow →
 * 遍历 ruleRef → 读规则文件 → 装进 ruleFiles;扫项目 .v1lib.json → Libraries。
 */
@DisplayName("V1BundleResolver — 决策流闭包解析")
class V1BundleResolverTest {

    private static final String FLOW_PATH = "/p/V1决策流/loan.v1flow.json";
    private static final String RULE_REF = "/p/V1规则集/pre.v1rs.json";
    private static final String VL_PATH = "/p/V1库/loan.v1lib.json";

    private static final String FLOW_JSON = "{"
            + "\"version\":\"1.0\",\"id\":\"asset1\",\"name\":\"loan\","
            + "\"flow\":{\"id\":\"flow1\",\"name\":\"Flow\",\"version\":\"1.0\",\"flowElements\":["
            + "{\"type\":\"startEvent\",\"id\":\"start\",\"name\":\"Start\"},"
            + "{\"type\":\"serviceTask\",\"id\":\"rs1\",\"name\":\"Precheck\",\"implementation\":\"RuleSet:rs1\"},"
            + "{\"type\":\"sequenceFlow\",\"id\":\"e1\",\"sourceRef\":\"start\",\"targetRef\":\"rs1\"}"
            + "]},"
            + "\"nodes\":{\"rs1\":{\"id\":\"rs1\",\"type\":\"RuleSet\",\"name\":\"Precheck\",\"ruleRef\":\"" + RULE_REF + "\"}},"
            + "\"schema\":{\"name\":\"Loan\",\"fields\":[{\"name\":\"age\",\"type\":\"NUMBER\"}]}"
            + "}";

    private static final String RULE_JSON = "{\"id\":\"rs1\",\"type\":\"RuleSet\",\"name\":\"Precheck\",\"rules\":[]}";

    private static final String VL_JSON = "{\"type\":\"VARIABLE\",\"name\":\"Loan\",\"entries\":["
            + "{\"key\":\"age\",\"dataType\":\"NUMBER\",\"label\":\"年龄\"}]}";

    @Nested
    @DisplayName("resolve(flowPath) — 完整闭包")
    class ResolveClosure {

        @Test
        @DisplayName("GIVEN flow 有 ruleRef + 项目有 vl 库 WHEN resolve THEN bundle 含 asset + ruleFiles + libraries.vl")
        void resolvesRuleRefAndLibrary() throws Exception {
            RuleForgeRepositoryService repo = mock(RuleForgeRepositoryService.class);
            FileRepository fileRepo = mock(FileRepository.class);
            ProjectRepository projectRepo = mock(ProjectRepository.class);
            when(repo.readFile(FLOW_PATH)).thenReturn(stream(FLOW_JSON));
            when(repo.readFile(RULE_REF)).thenReturn(stream(RULE_JSON));
            when(repo.readFile(VL_PATH)).thenReturn(stream(VL_JSON));
            ProjectEntity project = new ProjectEntity();
            project.setId(7L);
            when(projectRepo.findByNameSelectId("p")).thenReturn(project);
            when(fileRepo.findByProjectId(7L)).thenReturn(List.of(fileEntity(VL_PATH), fileEntity("/p/其他.txt")));

            V1BundleResolver resolver = new V1BundleResolver(repo, fileRepo, projectRepo);

            V1PublishedBundle bundle = resolver.resolve(FLOW_PATH);

            // THEN asset 装入(flow 名 = loan)
            RuleAsset asset = bundle.getAsset();
            assertThat(asset).isNotNull();
            assertThat(asset.getName()).isEqualTo("loan");
            // THEN ruleRef 解析进 ruleFiles(key = ruleRef 路径,值是 RuleSet 节点)
            Map<String, NodeBase> ruleFiles = bundle.getRuleFiles();
            assertThat(ruleFiles).containsKey(RULE_REF);
            assertThat(ruleFiles.get(RULE_REF).getType()).isEqualTo("RuleSet");
            // THEN vl 库装入 Libraries.vl
            Libraries libs = bundle.getLibraries();
            assertThat(libs).isNotNull();
            assertThat(libs.getVl()).isNotNull();
            assertThat(libs.getVl().getName()).isEqualTo("Loan");
        }

        @Test
        @DisplayName("GIVEN ruleRef 指向不存在的文件(readFile 抛异常)WHEN resolve THEN 抛 RuleException")
        void missingRuleRefThrows() throws Exception {
            RuleForgeRepositoryService repo = mock(RuleForgeRepositoryService.class);
            FileRepository fileRepo = mock(FileRepository.class);
            ProjectRepository projectRepo = mock(ProjectRepository.class);
            when(repo.readFile(FLOW_PATH)).thenReturn(stream(FLOW_JSON));
            when(repo.readFile(RULE_REF)).thenThrow(new RuleException("文件不存在"));
            when(projectRepo.findByNameSelectId(any())).thenReturn(null);
            when(fileRepo.findByProjectId(any())).thenReturn(Collections.emptyList());

            V1BundleResolver resolver = new V1BundleResolver(repo, fileRepo, projectRepo);

            assertThatThrownBy(() -> resolver.resolve(FLOW_PATH))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining(RULE_REF);
        }

        @Test
        @DisplayName("GIVEN 项目无 .v1lib.json WHEN resolve THEN libraries 为 null(不阻断,asset 仍装入)")
        void noLibrariesYieldsNull() throws Exception {
            RuleForgeRepositoryService repo = mock(RuleForgeRepositoryService.class);
            FileRepository fileRepo = mock(FileRepository.class);
            ProjectRepository projectRepo = mock(ProjectRepository.class);
            when(repo.readFile(FLOW_PATH)).thenReturn(stream(FLOW_JSON));
            when(repo.readFile(RULE_REF)).thenReturn(stream(RULE_JSON));
            ProjectEntity project = new ProjectEntity();
            project.setId(7L);
            when(projectRepo.findByNameSelectId("p")).thenReturn(project);
            when(fileRepo.findByProjectId(7L)).thenReturn(Collections.emptyList());

            V1BundleResolver resolver = new V1BundleResolver(repo, fileRepo, projectRepo);

            V1PublishedBundle bundle = resolver.resolve(FLOW_PATH);

            assertThat(bundle.getLibraries()).isNull();
            assertThat(bundle.getAsset()).isNotNull();
        }
    }

    @Nested
    @DisplayName("projectNameOf(flowPath) — 项目名抽取")
    class ProjectNameOf {
        @Test
        @DisplayName("GIVEN /proj/V1决策流/x.v1flow.json THEN proj")
        void leadingSlash() {
            assertThat(V1BundleResolver.projectNameOf("/proj/V1决策流/x.v1flow.json")).isEqualTo("proj");
        }

        @Test
        @DisplayName("GIVEN 无前导斜杠 THEN 首段")
        void noLeadingSlash() {
            assertThat(V1BundleResolver.projectNameOf("proj/x.json")).isEqualTo("proj");
        }

        @Test
        @DisplayName("GIVEN 空串 THEN 空串")
        void empty() {
            assertThat(V1BundleResolver.projectNameOf("")).isEmpty();
        }
    }

    private static InputStream stream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    private static FileEntity fileEntity(String path) {
        FileEntity f = new FileEntity();
        f.setFilePath(path);
        return f;
    }
}
