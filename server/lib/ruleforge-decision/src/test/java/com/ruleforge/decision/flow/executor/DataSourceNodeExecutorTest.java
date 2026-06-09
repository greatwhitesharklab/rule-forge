package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.NodeType;
import com.ruleforge.decision.flow.registry.DataSourceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V5.23 — DataSourceNodeExecutor 行为规范。
 *
 * <p>覆盖:
 * <ul>
 *   <li>基础路径:读 ruleforge:dataSource 调 client,merge 输出回 vars</li>
 *   <li>inputVar 取子 map</li>
 *   <li>outputVar 写子 map</li>
 *   <li>缺失 dataSource attr → 抛</li>
 *   <li>DataSourceClient 抛 → 透传包 FlowExecutionException</li>
 *   <li>client 不可用 → 抛("没启用 datasource 模块")</li>
 * </ul>
 */
@DisplayName("DataSourceNodeExecutor — 决策流 ↔ DataSource 桥接")
class DataSourceNodeExecutorTest {

    private DataSourceClient client;
    private DataSourceNodeExecutor executor;

    @BeforeEach
    void setUp() {
        client = mock(DataSourceClient.class);
        ObjectProvider<DataSourceClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        executor = new DataSourceNodeExecutor(provider);
    }

    @Nested
    @DisplayName("Scenario: 基础 fetch 路径")
    class BasicFetch {

        @Test
        @DisplayName("Given 节点 + 整个 vars inputs When execute Then client.fetch 被调 + 输出 merge 回 vars")
        void shouldFetchAndMergeOutputs() throws Exception {
            // Given
            FlowNode node = dsNode("ds1", "credit_score", null, null);
            FlowContext ctx = newCtx(Map.of("applicantId", "A001"));
            when(client.fetch(eq("credit_score"), any())).thenReturn(Map.of("score", 720, "tier", "GOLD"));

            // When
            executor.execute(node, ctx);

            // Then
            assertThat(ctx.getVars()).containsEntry("score", 720).containsEntry("tier", "GOLD");
            assertThat(ctx.getVars()).containsEntry("applicantId", "A001"); // 原有字段保留
            verify(client).fetch(eq("credit_score"), any());
        }

        @Test
        @DisplayName("Given inputVar='applicant' When execute Then client 收到的是 vars.applicant 子 map")
        void shouldExtractInputVar() throws Exception {
            // Given
            FlowNode node = dsNode("ds1", "credit_score", "applicant", null);
            Map<String, Object> applicant = Map.of("id", "A001", "age", 30);
            Map<String, Object> vars = new HashMap<>();
            vars.put("applicant", applicant);
            vars.put("unrelated", "noise");
            FlowContext ctx = newCtx(vars);
            when(client.fetch(eq("credit_score"), any())).thenReturn(Map.of("score", 700));

            // When
            executor.execute(node, ctx);

            // Then — client 收到的是 applicant 子 map
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
            verify(client).fetch(eq("credit_score"), cap.capture());
            Map<String, Object> sent = cap.getValue();
            assertThat(sent).containsEntry("id", "A001").containsEntry("age", 30);
            // 'unrelated' 不该传过去
            assertThat(sent).doesNotContainKey("unrelated");
        }

        @Test
        @DisplayName("Given outputVar='creditResult' When execute Then 整 outputs 写到 vars[outputVar]")
        void shouldWriteToOutputVar() throws Exception {
            // Given
            FlowNode node = dsNode("ds1", "credit_score", null, "creditResult");
            FlowContext ctx = newCtx(Map.of("a", 1));
            when(client.fetch(eq("credit_score"), any())).thenReturn(Map.of("score", 700, "tier", "GOLD"));

            // When
            executor.execute(node, ctx);

            // Then
            assertThat(ctx.getVars()).containsKey("creditResult");
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) ctx.getVars().get("creditResult");
            assertThat(result).containsEntry("score", 700).containsEntry("tier", "GOLD");
            // 没 merge 到根
            assertThat(ctx.getVars()).doesNotContainKey("score").doesNotContainKey("tier");
        }
    }

    @Nested
    @DisplayName("Scenario: 错误路径")
    class ErrorPaths {

        @Test
        @DisplayName("Given 节点缺 ruleforge:dataSource When execute Then 抛 FlowExecutionException")
        void shouldRejectMissingDataSource() {
            FlowNode node = node("ds1", NodeType.SERVICE_TASK, Map.of(
                "ruleforge:taskType", "data_source"
                // 没 ruleforge:dataSource
            ));
            FlowContext ctx = newCtx(Map.of());

            assertThatThrownBy(() -> executor.execute(node, ctx))
                .isInstanceOf(FlowExecutionException.class)
                .hasMessageContaining("ruleforge:dataSource");
        }

        @Test
        @DisplayName("Given client.fetch 抛异常 When execute Then 包 FlowExecutionException 透传")
        void shouldPropagateClientException() throws Exception {
            FlowNode node = dsNode("ds1", "credit_score", null, null);
            FlowContext ctx = newCtx(Map.of());
            when(client.fetch(eq("credit_score"), any()))
                .thenThrow(new RuntimeException("upstream 503"));

            assertThatThrownBy(() -> executor.execute(node, ctx))
                .isInstanceOf(FlowExecutionException.class)
                .hasMessageContaining("credit_score")
                .hasMessageContaining("upstream 503");
        }

        @Test
        @DisplayName("Given client 不可用(没启用 datasource 模块)When execute Then 抛")
        void shouldRejectWhenClientUnavailable() throws Exception {
            ObjectProvider<DataSourceClient> empty = mock(ObjectProvider.class);
            when(empty.getIfAvailable()).thenReturn(null);
            DataSourceNodeExecutor ex = new DataSourceNodeExecutor(empty);

            FlowNode node = dsNode("ds1", "credit_score", null, null);
            FlowContext ctx = newCtx(Map.of());

            assertThatThrownBy(() -> ex.execute(node, ctx))
                .isInstanceOf(FlowExecutionException.class)
                .hasMessageContaining("DataSourceClient not configured");
            // 关键:不应该静默通过(否则部署事故难发现)
            verify(client, never()).fetch(anyString(), any());
        }
    }

    // ========== helpers ==========

    private static FlowContext newCtx(Map<String, Object> vars) {
        FlowContext ctx = new FlowContext();
        ctx.setFlowRunId("fr-test");
        ctx.getVars().putAll(vars);
        return ctx;
    }

    private static FlowNode node(String id, NodeType type, Map<String, String> attrs) {
        return new FlowNode(id, type, null, attrs, null, null, null, false);
    }

    private static FlowNode dsNode(String id, String dsName, String inputVar, String outputVar) {
        java.util.LinkedHashMap<String, String> attrs = new java.util.LinkedHashMap<>();
        attrs.put("ruleforge:taskType", "data_source");
        attrs.put("ruleforge:dataSource", dsName);
        if (inputVar != null) attrs.put("ruleforge:inputVar", inputVar);
        if (outputVar != null) attrs.put("ruleforge:outputVar", outputVar);
        return node(id, NodeType.SERVICE_TASK, attrs);
    }
}
