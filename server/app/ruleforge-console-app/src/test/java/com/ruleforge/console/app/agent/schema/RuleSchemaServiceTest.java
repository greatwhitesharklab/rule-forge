package com.ruleforge.console.app.agent.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RuleSchemaService 单元测试
 *
 * BDD 风格:Given/When/Then 通过 @Nested @DisplayName 表达
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RuleSchemaService - rule schema 加载/缓存/查询")
class RuleSchemaServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ObjectMapper unusedMock; // 占位防止 service 用真实 mapper 初始化时拉文件

    private RuleSchemaService service;

    @BeforeEach
    void setUp() {
        // 真实 ObjectMapper 注入,确保 classpath 资源可读
        service = new RuleSchemaService(objectMapper);
        service.loadIndex();
    }

    @Nested
    @DisplayName("Scenario: 启动时加载 type 索引")
    class LoadIndexOnStartup {

        @Test
        @DisplayName("Given classpath 有 _index.json When 启动 Then 索引包含 9 个 type")
        void shouldLoadAllTypes() {
            // Given & When
            ObjectNode result = service.listTypes();

            // Then
            JsonNode types = result.get("types");
            assertThat(types).isNotNull();
            assertThat(types.isArray()).isTrue();
            assertThat(types.size()).isGreaterThanOrEqualTo(9);

            // 按 priority 排序,decision_table 应该是第一个
            JsonNode first = types.get(0);
            assertThat(first.get("type").asText()).isEqualTo("decision_table");
            assertThat(first.get("priority").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("decision_table 在 supported v522 列表里")
        void shouldIncludeDecisionTableInSupportedTypes() {
            // When
            List<String> supported = service.supportedV522Types();

            // Then
            assertThat(supported).contains("decision_table");
        }

        @Test
        @DisplayName("crosstab 不在 supported v522 列表(占位)")
        void shouldExcludeCrosstab() {
            // When
            List<String> supported = service.supportedV522Types();

            // Then
            assertThat(supported).doesNotContain("crosstab");
        }
    }

    @Nested
    @DisplayName("Scenario: 客户端查询单类型 schema")
    class GetSingleSchema {

        @Test
        @DisplayName("Given 请求 decision_table schema When 查询 Then 返完整 JSON")
        void shouldReturnFullSchema() {
            // When
            Optional<JsonNode> schema = service.getSchema("decision_table");

            // Then
            assertThat(schema).isPresent();
            JsonNode root = schema.get();
            assertThat(root.get("type").asText()).isEqualTo("decision_table");
            assertThat(root.get("jsonStructure")).isNotNull();
            assertThat(root.get("operators")).isNotNull();
            assertThat(root.get("example")).isNotNull();
            assertThat(root.get("tips")).isNotNull();
        }

        @Test
        @DisplayName("decision_table schema 包含示例 cellMap")
        void shouldContainExampleCellMap() {
            // When
            JsonNode schema = service.getSchema("decision_table").orElseThrow();

            // Then
            JsonNode example = schema.get("example");
            JsonNode cellMap = example.get("cellMap");
            assertThat(cellMap).isNotNull();
            assertThat(cellMap.has("r1,c1")).as("示例 cellMap 应包含 r1,c1").isTrue();
        }

        @Test
        @DisplayName("ul schema 包含 UEL 语法参考")
        void shouldContainUelSyntaxForUl() {
            // When
            JsonNode schema = service.getSchema("ul").orElseThrow();

            // Then
            assertThat(schema.get("uelSyntax")).isNotNull();
            assertThat(schema.get("example").get("rules")).isNotNull();
            assertThat(schema.get("example").get("rules").size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("Given 不存在的 type When 查询 Then 返空")
        void shouldReturnEmptyForUnknownType() {
            // When
            Optional<JsonNode> schema = service.getSchema("non_existent_type");

            // Then
            assertThat(schema).isEmpty();
        }

        @Test
        @DisplayName("Given null type When 查询 Then 返空(不抛异常)")
        void shouldReturnEmptyForNullType() {
            // When
            Optional<JsonNode> schema = service.getSchema(null);

            // Then
            assertThat(schema).isEmpty();
        }
    }

    @Nested
    @DisplayName("Scenario: 性能 — schema 缓存")
    class SchemaCache {

        @Test
        @DisplayName("连续两次查同一 type 第二次走缓存,无 IO")
        void shouldCacheSchema() {
            // When
            Optional<JsonNode> first = service.getSchema("decision_table");
            Optional<JsonNode> second = service.getSchema("decision_table");

            // Then — 都是同一个对象引用(缓存命中)
            assertThat(first).isPresent();
            assertThat(second).isPresent();
            assertThat(first.get()).isSameAs(second.get());
        }
    }
}
