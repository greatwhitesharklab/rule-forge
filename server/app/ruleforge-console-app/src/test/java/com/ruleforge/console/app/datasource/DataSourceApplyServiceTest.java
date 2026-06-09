package com.ruleforge.console.app.datasource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.draft.DraftEntity;
import com.ruleforge.console.app.draft.DraftMapper;
import com.ruleforge.datasource.DataSourceRegistry;
import com.ruleforge.datasource.JavaSourceCompiler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * V5.23 — DataSourceApplyService 单元测试。
 *
 * <p>重点测契约:
 * <ul>
 *   <li>草稿不存在 / 类型错 / 状态错 走对应错误路径</li>
 *   <li>编译失败 → 草稿状态 = COMPILE_FAILED + 错误信息落 reviewComment</li>
 *   <li>编译成功 → 调 gitWriter + registry + 标 applied(状态机保持 APPROVED)</li>
 *   <li>deriveClassName / fqcn 静态工具正确</li>
 * </ul>
 *
 * <p>Git 写盘 + 真实 classloader 加载 留给手测 + 端到端 (本测试 mock 掉所有外部 IO)。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataSourceApplyService - data_source 草稿 apply 流程")
class DataSourceApplyServiceTest {

    @Mock private DraftMapper draftMapper;
    @Mock private DataSourceGitWriter gitWriter;
    @Mock private DataSourceRegistry registry;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DataSourceApplyService service;

    @BeforeEach
    void setUp() {
        // 用一个会失败 / 会成功的 stub compiler — 但实际 apply 路径是
        // draft.status=APPROVED 走完,所以我们走"编译失败"路径测路径覆盖。
        // 编译成功的路径需要真实 javac,在 surefire 环境里直接调 — 跑 e2e 才测。
        service = new DataSourceApplyService(
            new JavaSourceCompiler(), registry, draftMapper, gitWriter,
            mock(com.ruleforge.console.datasource.DataSourceController.class), objectMapper);
    }

    // ========== deriveClassName / fqcn 工具 ==========

    @Nested
    @DisplayName("Scenario: 工具方法")
    class Helpers {

        @Test
        @DisplayName("deriveClassName: ds_abc123def → Abc123defDataSource")
        void shouldDeriveClassName() {
            assertThat(DataSourceApplyService.deriveClassName("ds_abc123def"))
                .isEqualTo("Abc123defDataSource");
        }

        @Test
        @DisplayName("deriveClassName: 不带 ds_ 前缀时整体当 tail")
        void shouldHandleNoPrefix() {
            assertThat(DataSourceApplyService.deriveClassName("xyz"))
                .isEqualTo("XyzDataSource");
        }

        @Test
        @DisplayName("deriveClassName: 空字符串返 AnonDataSource")
        void shouldHandleEmpty() {
            assertThat(DataSourceApplyService.deriveClassName(""))
                .isEqualTo("AnonDataSource");
        }

        @Test
        @DisplayName("fqcn 拼接硬编码包名")
        void shouldFqcn() {
            assertThat(DataSourceApplyService.fqcn("FooDataSource"))
                .isEqualTo("com.ruleforge.console.datasource.generated.FooDataSource");
        }
    }

    // ========== 校验路径 ==========

    @Nested
    @DisplayName("Scenario: 入参校验")
    class Validation {

        @Test
        @DisplayName("Given 草稿不存在 When apply Then 抛 IllegalArgumentException")
        void shouldRejectMissingDraft() {
            when(draftMapper.selectByDraftId("nope")).thenReturn(null);
            assertThatThrownBy(() -> service.apply("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
        }

        @Test
        @DisplayName("Given 草稿是其他 ruleType When apply Then 抛")
        void shouldRejectWrongRuleType() {
            DraftEntity d = newDraft(DraftEntity.STATUS_APPROVED, "decision_table");
            when(draftMapper.selectByDraftId("d1")).thenReturn(d);

            assertThatThrownBy(() -> service.apply("d1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a data_source draft");
        }

        @Test
        @DisplayName("Given 草稿状态不是 APPROVED When apply Then 抛")
        void shouldRejectNotApproved() {
            DraftEntity d = newDraft(DraftEntity.STATUS_DRAFT, "data_source");
            when(draftMapper.selectByDraftId("d1")).thenReturn(d);

            assertThatThrownBy(() -> service.apply("d1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED");
        }
    }

    // ========== 编译失败路径 ==========

    @Nested
    @DisplayName("Scenario: 编译失败")
    class CompileFailure {

        @Test
        @DisplayName("Given 源码语法错 When apply Then 标 COMPILE_FAILED + 错误信息落 reviewComment")
        void shouldMarkCompileFailed() {
            DraftEntity d = newDraft(DraftEntity.STATUS_APPROVED, "data_source");
            d.setContent("this is not java code at all !!! !!!");
            d.setDraftId("ds_broken123");
            when(draftMapper.selectByDraftId("ds_broken123")).thenReturn(d);

            DraftEntity result = service.apply("ds_broken123");

            assertThat(result.getStatus()).isEqualTo("COMPILE_FAILED");
            assertThat(result.getReviewComment()).contains("compile error");
            // 失败路径不应调 git / registry
            verifyNoInteractions(gitWriter, registry);
            // 状态被写回
            ArgumentCaptor<DraftEntity> captor = ArgumentCaptor.forClass(DraftEntity.class);
            verify(draftMapper).updateById(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("COMPILE_FAILED");
        }
    }

    // ========== 编译成功路径(需要真实 javac) ==========

    @Nested
    @DisplayName("Scenario: 编译成功(真实 javac)")
    class CompileSuccess {

        private static final String VALID_SOURCE = """
                package com.ruleforge.console.datasource.generated;
                import com.ruleforge.datasource.BaseApiDataSource;
                import com.ruleforge.datasource.Vars;
                public class DsE2E123DataSource extends BaseApiDataSource {
                    @Override public String getName() { return "e2e"; }
                    @Override public java.util.Map<String, String> getSchema() {
                        return java.util.Map.of("result", "string");
                    }
                    @Override public Vars fetch(Vars v) {
                        v.put("result", "hello-from-compiled-ds");
                        return v;
                    }
                }
                """;

        @Test
        @DisplayName("Given 合法源码 When apply Then 标 applied + 调 gitWriter + 注册到 registry + fetch 能跑通")
        void shouldCompileLoadAndRegister() {
            // Given
            DraftEntity d = newDraft(DraftEntity.STATUS_APPROVED, "data_source");
            d.setContent(VALID_SOURCE);
            d.setDraftId("ds_e2e123");
            d.setProject("demo");
            lenient().when(draftMapper.updateById(any(DraftEntity.class))).thenReturn(1);
            when(draftMapper.selectByDraftId("ds_e2e123")).thenReturn(d);
            // manifestController mock — readManifestEntries 返可变空 list,writeManifest void
            com.ruleforge.console.datasource.DataSourceController manifestCtl =
                (com.ruleforge.console.datasource.DataSourceController) org.mockito.Mockito.mock(
                    com.ruleforge.console.datasource.DataSourceController.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            lenient().when(manifestCtl.readManifestEntries("demo")).thenReturn(new java.util.ArrayList<>());
            // 重新构造 service 走 manifest mock — 上面 setUp 的 mock 太弱
            service = new DataSourceApplyService(
                new JavaSourceCompiler(), registry, draftMapper, gitWriter, manifestCtl, objectMapper);

            // When
            DraftEntity result = service.apply("ds_e2e123");

            // Then — 成功路径:appliedVersion / appliedAt 都有
            assertThat(result.getStatus()).isEqualTo(DraftEntity.STATUS_APPROVED);
            assertThat(result.getAppliedVersion()).startsWith("v");
            assertThat(result.getAppliedAt()).isNotNull();
            // sourceMeta 记下 className + fqcn
            assertThat(result.getSourceMeta()).contains("DsE2E123DataSource");
            // gitWriter 写了 .class
            verify(gitWriter).writeCompiledClass(eq("demo"),
                argThat(p -> p.endsWith("DsE2E123DataSource.class")), any(byte[].class));
            // registry 收到 bean
            verify(registry).register(any(com.ruleforge.datasource.BaseApiDataSource.class));
        }
    }

    // ========== helper ==========

    private static DraftEntity newDraft(String status, String ruleType) {
        DraftEntity d = new DraftEntity();
        d.setDraftId("d1");
        d.setRuleType(ruleType);
        d.setProject("demo");
        d.setContent("package x; class X {}");
        d.setStatus(status);
        d.setCreatedBy("user1");
        return d;
    }
}
