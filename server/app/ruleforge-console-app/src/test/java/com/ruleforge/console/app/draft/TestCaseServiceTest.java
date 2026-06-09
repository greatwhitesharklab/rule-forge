package com.ruleforge.console.app.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * TestCaseService 单元测试 (V5.22.1)
 *
 * 校验:inputs 必须是非空 JSON object;list/delete 走 mapper。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TestCaseService - 草稿测试用例")
class TestCaseServiceTest {

    @Mock
    private TestCaseMapper testCaseMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TestCaseService service;

    @BeforeEach
    void setUp() {
        service = new TestCaseService(testCaseMapper, objectMapper);
    }

    @Nested
    @DisplayName("Scenario: addTestCase")
    class AddTestCase {

        @Test
        @DisplayName("Given 合法 inputs JSON object When add Then 写入 + 自动生成 testCaseId")
        void shouldAdd() {
            // Given
            String inputs = "{\"customer.age\":17,\"customer.monthlyIncome\":5000}";

            // When
            TestCaseEntity tc = service.addTestCase("drf_1", "under18", "test desc", inputs, "r1", "BA1", "MANUAL");

            // Then
            ArgumentCaptor<TestCaseEntity> captor = ArgumentCaptor.forClass(TestCaseEntity.class);
            verify(testCaseMapper).insert(captor.capture());
            TestCaseEntity saved = captor.getValue();
            assertThat(saved.getTestCaseId()).startsWith("tc_").hasSize(19);
            assertThat(saved.getDraftId()).isEqualTo("drf_1");
            assertThat(saved.getName()).isEqualTo("under18");
            assertThat(saved.getInputs()).isEqualTo(inputs);
            assertThat(saved.getExpectedRowId()).isEqualTo("r1");
            assertThat(saved.getCreatedBy()).isEqualTo("BA1");
            assertThat(saved.getSource()).isEqualTo("MANUAL");
        }

        @Test
        @DisplayName("Given source 为 null When add Then 默认 MANUAL")
        void shouldDefaultSourceManual() {
            String inputs = "{\"x\":1}";
            TestCaseEntity tc = service.addTestCase("d", "n", null, inputs, null, "u", null);
            assertThat(tc.getSource()).isEqualTo(TestCaseEntity.SOURCE_MANUAL);
        }

        @Test
        @DisplayName("Given inputs 为空 When add Then 抛 IllegalArgumentException")
        void shouldRejectEmptyInputs() {
            assertThatThrownBy(() -> service.addTestCase("d", "n", null, "", null, "u", "M"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("inputs 不能为空");
        }

        @Test
        @DisplayName("Given inputs 是 JSON array (非 object) When add Then 抛 IllegalArgumentException")
        void shouldRejectNonObjectInputs() {
            assertThatThrownBy(() -> service.addTestCase("d", "n", null, "[]", null, "u", "M"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("JSON object");
        }

        @Test
        @DisplayName("Given inputs 不是合法 JSON When add Then 抛 IllegalArgumentException")
        void shouldRejectInvalidJson() {
            assertThatThrownBy(() -> service.addTestCase("d", "n", null, "{not json", null, "u", "M"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不是合法 JSON");
        }
    }

    @Nested
    @DisplayName("Scenario: listByDraftId / deleteTestCase")
    class ListAndDelete {

        @Test
        @DisplayName("listByDraftId 走 mapper")
        void shouldList() {
            when(testCaseMapper.listByDraftId("d1")).thenReturn(List.of(new TestCaseEntity(), new TestCaseEntity()));
            assertThat(service.listByDraftId("d1")).hasSize(2);
            verify(testCaseMapper).listByDraftId("d1");
        }

        @Test
        @DisplayName("deleteTestCase 命中返 true")
        void shouldDeleteFound() {
            when(testCaseMapper.deleteByTestCaseId("tc_1")).thenReturn(1);
            assertThat(service.deleteTestCase("tc_1")).isTrue();
        }

        @Test
        @DisplayName("deleteTestCase 没命中返 false")
        void shouldDeleteNotFound() {
            when(testCaseMapper.deleteByTestCaseId("tc_404")).thenReturn(0);
            assertThat(service.deleteTestCase("tc_404")).isFalse();
        }
    }

    @Nested
    @DisplayName("Scenario: toDto")
    class ToDto {

        @Test
        @DisplayName("DTO 序列化 inputs 成对象 + 字段映射")
        void shouldSerialize() throws Exception {
            TestCaseEntity tc = new TestCaseEntity();
            tc.setTestCaseId("tc_1");
            tc.setDraftId("drf_1");
            tc.setName("name");
            tc.setDescription("desc");
            tc.setInputs("{\"a\":1,\"b\":\"x\"}");
            tc.setExpectedRowId("r1");
            tc.setCreatedBy("BA");
            tc.setSource("LLM");

            var dto = service.toDto(tc);
            assertThat(dto.get("testCaseId").asText()).isEqualTo("tc_1");
            assertThat(dto.get("name").asText()).isEqualTo("name");
            assertThat(dto.get("expectedRowId").asText()).isEqualTo("r1");
            // inputs 解析成对象
            assertThat(dto.get("inputs").isObject()).isTrue();
            assertThat(dto.get("inputs").get("a").asInt()).isEqualTo(1);
        }
    }
}
