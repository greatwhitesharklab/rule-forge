package com.ruleforge.datasource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5.23 — End-to-end happy path test, all in-process.
 *
 * <p>Proves the critical chain works WITHOUT docker / Spring / git:
 * <ol>
 *   <li>LLM-style Java source (extends BaseApiDataSource) → {@link JavaSourceCompiler}</li>
 *   <li>Compiled bytes loaded via {@link ClassLoaderPool}-style URLClassLoader defineClass</li>
 *   <li>Bean registered in {@link DataSourceRegistry}</li>
 *   <li>Registry.fetch(name, inputs) actually invokes the bean's fetch()</li>
 *   <li>Audit log receives success / failure calls</li>
 * </ol>
 *
 * <p>Decision-flow integration (DataSourceNodeExecutor + DataSourceClient) is tested
 * separately in ruleforge-decision module; here we test the lib primitives in concert.
 */
@DisplayName("V5.23 — 端到端:Java 源码 → 编译 → 加载 → 注册 → fetch")
class DataSourceEndToEndTest {

    private static final String FQCN = "com.ruleforge.console.datasource.generated.E2ECreditScore";
    private static final String CLASS_NAME = "E2ECreditScore";
    private static final String SOURCE = """
            package com.ruleforge.console.datasource.generated;

            import com.ruleforge.datasource.BaseApiDataSource;
            import com.ruleforge.datasource.Vars;

            public class E2ECreditScore extends BaseApiDataSource {
                @Override
                public String getName() { return "credit_score"; }

                @Override
                public java.util.Map<String, String> getSchema() {
                    java.util.Map<String, String> s = new java.util.LinkedHashMap<>();
                    s.put("score", "number");
                    s.put("tier", "string");
                    return s;
                }

                @Override
                public Vars fetch(Vars inputs) {
                    // 真实场景会去调第三方 API;这里我们把 applicantId 末位 0-2 → GOOD, 3-5 → FAIR, 其它 → POOR
                    Object idObj = inputs.get("applicantId");
                    String id = idObj == null ? "" : String.valueOf(idObj);
                    char last = id.isEmpty() ? '0' : id.charAt(id.length() - 1);
                    int bucket = last - '0';
                    Vars out = new Vars();
                    if (bucket >= 0 && bucket <= 2) {
                        out.put("score", 720);
                        out.put("tier", "GOOD");
                    } else if (bucket >= 3 && bucket <= 5) {
                        out.put("score", 650);
                        out.put("tier", "FAIR");
                    } else {
                        out.put("score", 500);
                        out.put("tier", "POOR");
                    }
                    return out;
                }
            }
            """;

    private JavaSourceCompiler compiler;
    private DataSourceRegistry registry;
    private RecordingAuditLog audit;

    @BeforeEach
    void setUp() {
        compiler = new JavaSourceCompiler();
        audit = new RecordingAuditLog();
        registry = new DataSourceRegistry(audit);
    }

    @Nested
    @DisplayName("Scenario: 完整链路")
    class FullChain {

        @Test
        @DisplayName("Given LLM 风格 Java 源码 When compile + load + register + fetch Then 拿到正确结果 + 审计 OK")
        void shouldRunFullChain() throws Exception {
            // 1) Compile
            JavaSourceCompiler.CompileResult cr = compiler.compile(SOURCE);
            assertThat(cr.success).as("compile should succeed; err=" + cr.error).isTrue();
            assertThat(cr.publicClassName).isEqualTo(CLASS_NAME);
            assertThat(cr.classBytes).isNotEmpty();
            // magic bytes
            assertThat(cr.classBytes[0] & 0xFF).isEqualTo(0xCA);
            assertThat(cr.classBytes[1] & 0xFF).isEqualTo(0xFE);
            assertThat(cr.classBytes[2] & 0xFF).isEqualTo(0xBA);
            assertThat(cr.classBytes[3] & 0xFF).isEqualTo(0xBE);

            // 2) Load via isolated classloader (同 DataSourceApplyService 模式)
            Class<?> clazz = loadClass(FQCN, cr.classBytes);
            assertThat(clazz.getName()).isEqualTo(FQCN);
            assertThat(BaseApiDataSource.class.isAssignableFrom(clazz)).isTrue();

            // 3) Instantiate + register
            BaseApiDataSource ds = (BaseApiDataSource) clazz.getDeclaredConstructor().newInstance();
            assertThat(ds.getName()).isEqualTo("credit_score");
            registry.register(ds);

            // 4) Fetch — 走完 DataSourceRegistry 的 audit + rate-limit + 调底层
            Vars in = new Vars();
            in.put("applicantId", "A001"); // 末位 '1' → bucket 1 → GOOD
            Vars out = registry.fetch("credit_score", in);
            assertThat(out).isNotNull();
            assertThat(out.<Integer>getInt("score")).isEqualTo(720);
            assertThat(out.getStr("tier")).isEqualTo("GOOD");

            // 5) Audit 收到 OK
            assertThat(audit.records).hasSize(1);
            assertThat(audit.records.get(0).dataSourceName).isEqualTo("credit_score");
            assertThat(audit.records.get(0).success).isTrue();
            assertThat(audit.records.get(0).durationMs).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Given DS 抛 RuntimeException When fetch Then 抛 + 审计记 success=false + error")
        void shouldAuditFailureOnUpstreamThrow() throws Exception {
            // Register a DS that always throws
            String badSrc = """
                    package com.ruleforge.console.datasource.generated;
                    import com.ruleforge.datasource.BaseApiDataSource;
                    import com.ruleforge.datasource.Vars;
                    public class E2EBad extends BaseApiDataSource {
                        @Override public String getName() { return "bad"; }
                        @Override public java.util.Map<String, String> getSchema() { return java.util.Map.of(); }
                        @Override public Vars fetch(Vars inputs) {
                            throw new RuntimeException("upstream unavailable");
                        }
                    }
                    """;
            registerFromSource("E2EBad", "bad", badSrc);

            Vars in = new Vars();
            in.put("x", 1);
            Throwable thrown = null;
            try {
                registry.fetch("bad", in);
            } catch (Throwable t) {
                thrown = t;
            }
            assertThat(thrown).isNotNull();
            assertThat(thrown).hasMessageContaining("upstream unavailable");
            // 失败 path 走 audit
            assertThat(audit.records).hasSize(1);
            assertThat(audit.records.get(0).dataSourceName).isEqualTo("bad");
            assertThat(audit.records.get(0).success).isFalse();
            assertThat(audit.records.get(0).errorMessage).contains("upstream unavailable");
        }

        @Test
        @DisplayName("Given 未知 dataSource name When fetch Then 抛 IllegalArgumentException(不写 audit — 调用没到 DS)")
        void shouldThrowOnUnknownName() {
            Vars in = new Vars();
            in.put("applicantId", "X");
            Throwable thrown = null;
            try {
                registry.fetch("nonexistent", in);
            } catch (Throwable t) {
                thrown = t;
            }
            assertThat(thrown).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent");
            // 不知道 name 之前就 throw 了,没到 fetch 路径,所以不写 audit
            assertThat(audit.records).isEmpty();
        }

        @Test
        @DisplayName("Given 注册两个 DS(name 不同) When fetch 各取一次 Then 都跑通 + audit 2 条")
        void shouldSupportMultipleDataSources() throws Exception {
            // 先把 credit_score(E2ECreditScore)注册上,这是 setUp 没有的步骤
            String src1 = SOURCE;
            registerFromSource("E2ECreditScore", "credit_score", src1);

            String src2 = SOURCE.replace("E2ECreditScore", "E2EIncomeCheck")
                                .replace("\"credit_score\"", "\"income_check\"")
                                .replace("applicantId", "monthlyIncome");
            // 简化:同一个 fetch 逻辑,只要能跑通
            registerFromSource("E2EIncomeCheck", "income_check", src2);

            Vars in1 = new Vars(); in1.put("applicantId", "A007");  // 末位 7 → POOR
            Vars in2 = new Vars(); in2.put("monthlyIncome", "B003"); // 末位 3 → FAIR

            assertThat(registry.fetch("credit_score", in1).<Integer>getInt("score")).isEqualTo(500);
            assertThat(registry.fetch("income_check", in2).<Integer>getInt("score")).isEqualTo(650);

            // 2 次 audit
            assertThat(audit.records).hasSize(2);
            assertThat(audit.records).extracting(r -> r.dataSourceName)
                .containsExactly("credit_score", "income_check");
        }
    }

    // ========== helpers ==========

    /** Mirrors DataSourceApplyService.DefiningClassLoader — but exposed here for tests. */
    private static Class<?> loadClass(String fqcn, byte[] bytes) {
        java.net.URLClassLoader base = new java.net.URLClassLoader(
            new java.net.URL[0], DataSourceEndToEndTest.class.getClassLoader());
        DefiningClassLoader loader = new DefiningClassLoader(
            new java.net.URL[0], DataSourceEndToEndTest.class.getClassLoader());
        try {
            return loader.define(fqcn, bytes);
        } finally {
            try { base.close(); } catch (Exception ignored) {}
        }
    }

    /** Exposes protected {@code defineClass} for runtime class definition. */
    static final class DefiningClassLoader extends java.net.URLClassLoader {
        DefiningClassLoader(java.net.URL[] urls, ClassLoader parent) { super(urls, parent); }
        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    private void registerFromSource(String className, String dsName, String source) throws Exception {
        JavaSourceCompiler.CompileResult cr = compiler.compile(source);
        assertThat(cr.success).as("compile " + className).isTrue();
        Class<?> c = loadClass("com.ruleforge.console.datasource.generated." + className, cr.classBytes);
        BaseApiDataSource ds = (BaseApiDataSource) c.getDeclaredConstructor().newInstance();
        registry.register(ds);
    }

    /** Test impl of DataSourceAuditLog — captures calls for assertion. */
    static class RecordingAuditLog implements DataSourceAuditLog {
        static class Record {
            final String dataSourceName;
            final boolean success;
            final long durationMs;
            final String errorMessage;
            final int inputCount;
            final int outputCount;
            Record(String n, boolean s, long d, String e, int i, int o) {
                dataSourceName = n; success = s; durationMs = d; errorMessage = e;
                inputCount = i; outputCount = o;
            }
        }
        final List<Record> records = new java.util.ArrayList<>();
        @Override
        public void record(String dataSourceName, Vars inputs, Vars outputs,
                           long durationMs, boolean success, String errorMessage) {
            records.add(new Record(
                dataSourceName, success, durationMs, errorMessage,
                inputs == null ? 0 : inputs.size(),
                outputs == null ? 0 : outputs.size()));
        }
    }
}
