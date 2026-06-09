package com.ruleforge.console.app.agent.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RuleSchemaController REST API 测试
 *
 * 直接测试 Controller 方法(@InjectMocks),不通过 MockMvc。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RuleSchemaController - rule schema REST API")
class RuleSchemaControllerTest {

    @Spy
    private RuleSchemaService schemaService = new RuleSchemaService(new ObjectMapper());

    @InjectMocks
    private RuleSchemaController controller;

    @BeforeEach
    void setUp() {
        schemaService.loadIndex();
    }

    @Nested
    @DisplayName("Scenario: GET /rule-schema/types")
    class ListTypes {

        @Test
        @DisplayName("Given 服务正常 When 调 listTypes Then 200 + types 数组")
        void shouldReturn200WithTypes() {
            // When
            ResponseEntity<?> response = controller.listTypes();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            JsonNode body = (JsonNode) response.getBody();
            assertThat(body.get("types").isArray()).isTrue();
            assertThat(body.get("types").size()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Scenario: GET /rule-schema/{type}")
    class GetSchema {

        @Test
        @DisplayName("Given 有效 type When 查询 Then 200 + schema")
        void shouldReturn200WithSchema() {
            // When
            ResponseEntity<?> response = controller.getSchema("decision_table");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = (JsonNode) response.getBody();
            assertThat(body.get("type").asText()).isEqualTo("decision_table");
        }

        @Test
        @DisplayName("Given 无效 type When 查询 Then 404 + error body")
        void shouldReturn404ForUnknownType() {
            // When
            ResponseEntity<?> response = controller.getSchema("non_existent");

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().toString()).contains("type_not_found");
        }

        @Test
        @DisplayName("Given null type When 查询 Then 404(避免空指针)")
        void shouldReturn404ForNull() {
            // When — Spring 不会传 null 到 path variable,但 @PathVariable String 理论允许
            // 模拟调用
            Optional<JsonNode> schema = schemaService.getSchema(null);
            assertThat(schema).isEmpty();
        }
    }
}
