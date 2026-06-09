package com.ruleforge.executor.app.datasource;

import com.ruleforge.datasource.BaseApiDataSource;
import com.ruleforge.datasource.DataSourceRegistry;
import com.ruleforge.datasource.JavaSourceCompiler;
import com.ruleforge.datasource.Vars;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.executor.DataSourceNodeExecutor;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.NodeType;
import com.ruleforge.decision.flow.registry.DataSourceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * V5.23 Phase 7b — Cross-module integration test for the data source ↔ decision
 * flow chain. Lives in {@code ruleforge-executor-app} because it's the only
 * module on the classpath that has both {@code ruleforge-decision} and
 * {@code ruleforge-datasource} as dependencies.
 *
 * <p>Drives the full path the user exercises in production:
 * <ol>
 *   <li>Compile a {@code BaseApiDataSource} subclass from raw source
 *       ({@link JavaSourceCompiler})</li>
 *   <li>Load via isolated {@code URLClassLoader} (mirrors
 *       {@code DataSourceLoader} on executor startup)</li>
 *   <li>Register into {@link DataSourceRegistry}</li>
 *   <li>Wrap as {@link DataSourceClient} (mirrors
 *       {@link DefaultDataSourceClient})</li>
 *   <li>Drive a {@link DataSourceNodeExecutor} as the BPMN engine would</li>
 *   <li>Verify outputs make it back into {@code FlowContext.vars}</li>
 * </ol>
 *
 * <p>No Spring, no docker, no git. Pure lib primitives in concert.
 */
@DisplayName("V5.23 — 端到端:compile → load → registry → client → 决策节点 → vars")
class DataSourceToDecisionFlowE2ETest {

    private static final String SOURCE = """
            package com.ruleforge.console.datasource.generated;
            import com.ruleforge.datasource.BaseApiDataSource;
            import com.ruleforge.datasource.Vars;
            public class E2ECredit extends BaseApiDataSource {
                @Override public String getName() { return "e2e_credit"; }
                @Override public java.util.Map<String, String> getSchema() {
                    return java.util.Map.of("score", "number", "decision", "string");
                }
                @Override public Vars fetch(Vars inputs) {
                    Object ageObj = inputs.get("age");
                    int age = ageObj == null ? 0 : Integer.parseInt(String.valueOf(ageObj));
                    Vars out = new Vars();
                    if (age >= 18) {
                        out.put("score", 100);
                        out.put("decision", "APPROVE");
                    } else {
                        out.put("score", 0);
                        out.put("decision", "REJECT");
                    }
                    return out;
                }
            }
            """;

    @Test
    @DisplayName("Given compile + load + register + BPMN 节点 When 调 executor Then 输出 merge 到 FlowContext.vars")
    void shouldDriveDataSourceThroughDecisionNode() throws Exception {
        // 1) Compile
        JavaSourceCompiler compiler = new JavaSourceCompiler();
        JavaSourceCompiler.CompileResult cr = compiler.compile(SOURCE);
        assertThat(cr.success).as("compile should succeed; err=" + cr.error).isTrue();
        assertThat(cr.publicClassName).isEqualTo("E2ECredit");

        // 2) Load via isolated classloader (mirrors DataSourceLoader.loadClass)
        Class<?> clazz = new DefiningClassLoader(
            new URL[0], getClass().getClassLoader())
            .define("com.ruleforge.console.datasource.generated.E2ECredit", cr.classBytes);
        assertThat(BaseApiDataSource.class.isAssignableFrom(clazz)).isTrue();
        BaseApiDataSource ds = (BaseApiDataSource) clazz.getDeclaredConstructor().newInstance();

        // 3) Register into registry (no-op audit for test)
        DataSourceRegistry registry = new DataSourceRegistry((n, i, o, ms, ok, e) -> {});
        registry.register(ds);

        // 4) Wire DataSourceClient — same as DefaultDataSourceClient (production)
        DataSourceClient client = new DefaultDataSourceClient(registry);
        ObjectProvider<DataSourceClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        DataSourceNodeExecutor executor = new DataSourceNodeExecutor(provider);

        // 5) Build FlowNode + FlowContext mimicking a BPMN `data_source` step
        Map<String, String> attrs = new HashMap<>();
        attrs.put("ruleforge:taskType", "data_source");
        attrs.put("ruleforge:dataSource", "e2e_credit");
        FlowNode node = new FlowNode("ds_node", NodeType.SERVICE_TASK, "fetch credit",
            attrs, null, null, null, false);
        FlowContext ctx = new FlowContext();
        ctx.setFlowRunId("fr-e2e-1");
        ctx.getVars().put("applicantId", "A001");
        ctx.getVars().put("age", 25);

        // 6) Drive
        executor.execute(node, ctx);

        // 7) Verify outputs merged into root vars
        assertThat(ctx.getVars()).containsEntry("score", 100);
        assertThat(ctx.getVars()).containsEntry("decision", "APPROVE");
        // 原有 vars 保留
        assertThat(ctx.getVars()).containsEntry("applicantId", "A001");
        assertThat(ctx.getVars()).containsEntry("age", 25);
    }

    @Test
    @DisplayName("Given outputVar 模式 When 调 executor Then 整 outputs 写到子 map,根 vars 不污染")
    void shouldSupportOutputVar() throws Exception {
        // Setup (same chain, just different BPMN attrs)
        JavaSourceCompiler.CompileResult cr = new JavaSourceCompiler().compile(SOURCE);
        Class<?> clazz = new DefiningClassLoader(new URL[0], getClass().getClassLoader())
            .define("com.ruleforge.console.datasource.generated.E2ECredit", cr.classBytes);
        BaseApiDataSource ds = (BaseApiDataSource) clazz.getDeclaredConstructor().newInstance();
        DataSourceRegistry registry = new DataSourceRegistry((n, i, o, ms, ok, e) -> {});
        registry.register(ds);
        DataSourceClient client = new DefaultDataSourceClient(registry);
        ObjectProvider<DataSourceClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        DataSourceNodeExecutor executor = new DataSourceNodeExecutor(provider);

        Map<String, String> attrs = new HashMap<>();
        attrs.put("ruleforge:taskType", "data_source");
        attrs.put("ruleforge:dataSource", "e2e_credit");
        attrs.put("ruleforge:outputVar", "creditResult");
        FlowNode node = new FlowNode("ds_node2", NodeType.SERVICE_TASK, null,
            attrs, null, null, null, false);
        FlowContext ctx = new FlowContext();
        ctx.getVars().put("age", 16); // < 18 → REJECT

        executor.execute(node, ctx);

        // Outputs go into vars[creditResult] sub map
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) ctx.getVars().get("creditResult");
        assertThat(result).containsEntry("decision", "REJECT").containsEntry("score", 0);
        // Root vars don't get polluted with score/decision
        assertThat(ctx.getVars()).doesNotContainKey("score").doesNotContainKey("decision");
        // Other vars untouched
        assertThat(ctx.getVars()).containsEntry("age", 16);
    }

    @Test
    @DisplayName("Given compile 出来的 .class When 加载 Then magic bytes 正确(0xCAFEBABE)")
    void shouldProduceValidClassBytes() throws Exception {
        JavaSourceCompiler.CompileResult cr = new JavaSourceCompiler().compile(SOURCE);
        assertThat(cr.classBytes).isNotEmpty();
        // Java .class 文件 magic bytes: 0xCAFEBABE
        assertThat(cr.classBytes[0] & 0xFF).isEqualTo(0xCA);
        assertThat(cr.classBytes[1] & 0xFF).isEqualTo(0xFE);
        assertThat(cr.classBytes[2] & 0xFF).isEqualTo(0xBA);
        assertThat(cr.classBytes[3] & 0xFF).isEqualTo(0xBE);
    }

    /** Exposes protected {@code defineClass} for runtime class definition. */
    static final class DefiningClassLoader extends URLClassLoader {
        DefiningClassLoader(URL[] urls, ClassLoader parent) { super(urls, parent); }
        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
