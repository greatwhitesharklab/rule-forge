package com.ruleforge.console.app.datasource;

import com.ruleforge.datasource.connector.AiJavaDataSourceConnector;
import com.ruleforge.datasource.entity.Datasource;
import com.ruleforge.datasource.service.IDatasourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V5.23 — AiJavaDataSourceService 行为规范。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiJavaDataSourceService — console 端编译并应用 Java data source")
class AiJavaDataSourceServiceTest {

    private static final String VALID_SOURCE = """
            package com.ruleforge.user;
            import com.ruleforge.datasource.jcompiler.IJavaDataSource;
            import java.util.Map;
            public class Phase7Credit implements IJavaDataSource {
                @Override public String getName() { return "p7"; }
                @Override public Object fetchField(String e, String f, Map<String, String> c) {
                    return "x".equals(f) ? 1 : null;
                }
            }
            """;

    @Mock private IDatasourceService datasourceService;
    @Mock private AiJavaDataSourceConnector aiJavaConnector;

    private AiJavaDataSourceService service;

    @BeforeEach
    void setUp() {
        service = new AiJavaDataSourceService(datasourceService, aiJavaConnector);
    }

    private Datasource aiJavaDs(Long id) {
        Datasource d = new Datasource();
        d.setId(id);
        d.setName("phase7");
        d.setType("AI_JAVA");
        d.setConfigJson("{}");
        d.setEnabled(true);
        return d;
    }

    @Nested
    @DisplayName("Scenario: 输入校验")
    class Validation {

        @Test
        @DisplayName("Given null id When apply Then failure + 'required'")
        void shouldRejectNullId() {
            AiJavaDataSourceService.ApplyResult r = service.apply(null, VALID_SOURCE);
            assertThat(r.success).isFalse();
            assertThat(r.message).contains("datasourceId");
        }

        @Test
        @DisplayName("Given 空 source When apply Then failure + 'required'")
        void shouldRejectEmptySource() {
            // 空 source 在 datasource 查询之前就拒了 — 不需要 stub
            AiJavaDataSourceService.ApplyResult r = service.apply(1L, "  ");
            assertThat(r.success).isFalse();
            assertThat(r.message).contains("javaSource");
        }

        @Test
        @DisplayName("Given source 不 implement IJavaDataSource When apply Then failure")
        void shouldRejectWrongInterface() {
            // interface 检查在 datasource 查询之前就拒了
            String src = "public class NotImplementing { }";
            AiJavaDataSourceService.ApplyResult r = service.apply(1L, src);
            assertThat(r.success).isFalse();
            assertThat(r.message).contains("IJavaDataSource");
        }

        @Test
        @DisplayName("Given 不存在的 datasourceId When apply Then failure + 'not found'")
        void shouldRejectMissingDatasource() {
            when(datasourceService.getDatasourceById(99L)).thenReturn(null);
            AiJavaDataSourceService.ApplyResult r = service.apply(99L, VALID_SOURCE);
            assertThat(r.success).isFalse();
            assertThat(r.message).contains("not found");
        }

        @Test
        @DisplayName("Given datasource.type != AI_JAVA When apply Then failure + 'must be AI_JAVA'")
        void shouldRejectWrongType() {
            Datasource ds = aiJavaDs(1L);
            ds.setType("REST_API");
            when(datasourceService.getDatasourceById(1L)).thenReturn(ds);
            AiJavaDataSourceService.ApplyResult r = service.apply(1L, VALID_SOURCE);
            assertThat(r.success).isFalse();
            assertThat(r.message).contains("AI_JAVA");
        }
    }

    @Nested
    @DisplayName("Scenario: 编译失败路径")
    class CompileFailurePath {

        @Test
        @DisplayName("Given 合法 source 但 javac 失败(语法错)When apply Then failure 含 'compile failed'")
        void shouldSurfaceCompileError() {
            Datasource ds = aiJavaDs(1L);
            when(datasourceService.getDatasourceById(1L)).thenReturn(ds);
            String bad = """
                    package com.ruleforge.user;
                    import com.ruleforge.datasource.jcompiler.IJavaDataSource;
                    public class Bad implements IJavaDataSource {
                        @Override public String getName() { return "x"; }
                        @Override public Object fetchField(String e, String f, java.util.Map<String, String> c) {
                            // 故意语法错 — 缺分号
                            return 1
                        }
                    }
                    """;
            AiJavaDataSourceService.ApplyResult r = service.apply(1L, bad);
            assertThat(r.success).isFalse();
            assertThat(r.message).contains("compile failed");
            // 不应触发更新或 evict
            verify(datasourceService, never()).updateDatasource(org.mockito.ArgumentMatchers.any());
            verify(aiJavaConnector, never()).evict(org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    @DisplayName("Scenario: 成功路径")
    class HappyPath {

        @Test
        @DisplayName("Given 合法 AI_JAVA datasource + 合法 source When apply Then 更新 config_json + evict cache")
        void shouldUpdateConfigJsonAndEvict() {
            Datasource ds = aiJavaDs(1L);
            when(datasourceService.getDatasourceById(1L)).thenReturn(ds);

            AiJavaDataSourceService.ApplyResult r = service.apply(1L, VALID_SOURCE);
            assertThat(r.success).as("err=" + r.message).isTrue();
            assertThat(r.className).isEqualTo("com.ruleforge.user.Phase7Credit");
            assertThat(r.classBytes).isGreaterThan(50);

            ArgumentCaptor<Datasource> cap = ArgumentCaptor.forClass(Datasource.class);
            verify(datasourceService).updateDatasource(cap.capture());
            Datasource updated = cap.getValue();
            // config_json 必须含 className + classBytesBase64
            assertThat(updated.getConfigJson()).contains("\"className\":\"com.ruleforge.user.Phase7Credit\"");
            assertThat(updated.getConfigJson()).contains("\"classBytesBase64\":");

            verify(aiJavaConnector).evict(1L);
        }
    }
}
