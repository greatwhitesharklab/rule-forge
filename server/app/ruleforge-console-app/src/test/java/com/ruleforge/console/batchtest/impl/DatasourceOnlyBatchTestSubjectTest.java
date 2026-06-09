package com.ruleforge.console.batchtest.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.entity.BatchTestSessionEntity;
import com.ruleforge.console.batchtest.SubjectExecutionContext;
import com.ruleforge.console.batchtest.SubjectResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Feature: DatasourceOnlyBatchTestSubject — DATASOURCE mode subject (V5.8.2+)
 *
 * 行为约定:
 *   - 成功拉到字段 → SubjectResult.successWithStatus(output, latency, 200)
 *     output 含 response/entityNotFound/clazz/fieldName/datasourceId
 *   - value == "-999" → SubjectResult.failure("ENTITY_NOT_FOUND", ...)
 *   - params.datasourceId 缺失 → "INVALID_CONFIG"
 *   - row 缺 entityId 或 fieldName → "INVALID_ROW"
 *   - executorClient 抛异常 → "FETCH_ERROR"
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DatasourceOnlyBatchTestSubject - DATASOURCE 模式 subject (V5.8.2+)")
class DatasourceOnlyBatchTestSubjectTest {

    @Mock private ExecutorDatasourceClient executorClient;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private DatasourceOnlyBatchTestSubject subject;

    private SubjectExecutionContext buildCtx(Map<String, Object> input, Map<String, Object> params) {
        return new SubjectExecutionContext(1L, 100L, input, params);
    }

    private Map<String, Object> defaultRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("entityId", "1");
        row.put("fieldName", "name");
        row.put("clazz", "T");
        return row;
    }

    private Map<String, Object> defaultParams() {
        Map<String, Object> p = new HashMap<>();
        p.put("datasourceId", 42L);
        return p;
    }

    @Nested
    @DisplayName("Scenario: 成功拉到字段")
    class SuccessfulFetch {

        // Given executorClient.fetchFields returns Map{"1" -> {"name" -> "John"}}
        // When subject.execute(ctx) with row={entityId:"1", fieldName:"name", clazz:"T"}, datasourceId=42
        // Then result.isSuccess() == true
        // And result.output() contains key "response" with value "John"
        @Test
        @DisplayName("拿到字段 → success + httpStatus 200,output.response = 字段值")
        void shouldReturnSuccessWithFieldValue() {
            Map<String, Object> innerRow = new HashMap<>();
            innerRow.put("name", "John");
            Map<String, Map<String, Object>> responses = Map.of("1", innerRow);
            when(executorClient.fetchFields(eq(42L), eq("T"), anyList(), anyList()))
                    .thenReturn(responses);

            SubjectResult result = subject.execute(buildCtx(defaultRow(), defaultParams()));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.httpStatus()).isEqualTo(200);
            assertThat(result.errorCode()).isNull();
            assertThat(result.output()).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> output = (Map<String, Object>) result.output();
            assertThat(output).containsEntry("response", "John");
            assertThat(output).containsEntry("entityNotFound", false);
            assertThat(output).containsEntry("clazz", "T");
            assertThat(output).containsEntry("fieldName", "name");
            assertThat(output).containsEntry("datasourceId", 42L);
        }
    }

    @Nested
    @DisplayName("Scenario: 未找到 entity (value = -999 哨兵)")
    class EntityNotFound {

        // Given executorClient returns Map{"1" -> {"name" -> "-999"}}
        // Then result.isSuccess() == false
        // And errorCode == "ENTITY_NOT_FOUND"
        @Test
        @DisplayName("value == \"-999\" → failure(ENTITY_NOT_FOUND)")
        void shouldFailWhenSentinelReturned() {
            Map<String, Object> innerRow = new HashMap<>();
            innerRow.put("name", "-999");
            Map<String, Map<String, Object>> responses = Map.of("1", innerRow);
            when(executorClient.fetchFields(any(), any(), anyList(), anyList()))
                    .thenReturn(responses);

            SubjectResult result = subject.execute(buildCtx(defaultRow(), defaultParams()));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.errorCode()).isEqualTo("ENTITY_NOT_FOUND");
            assertThat(result.errorMessage()).contains("entityId=1");
        }
    }

    @Nested
    @DisplayName("Scenario: 缺少 datasourceId")
    class MissingDatasourceId {

        // Given ctx.params is empty map
        // Then errorCode == "INVALID_CONFIG"
        @Test
        @DisplayName("params.datasourceId 缺失 → failure(INVALID_CONFIG)")
        void shouldFailWhenDatasourceIdMissing() {
            SubjectResult result = subject.execute(
                    buildCtx(defaultRow(), new HashMap<>()));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.errorCode()).isEqualTo("INVALID_CONFIG");
            assertThat(result.errorMessage()).contains("datasourceId");
        }
    }

    @Nested
    @DisplayName("Scenario: row 缺 entityId")
    class MissingEntityId {

        // Given row without entityId
        // Then errorCode == "INVALID_ROW"
        @Test
        @DisplayName("row 缺 entityId → failure(INVALID_ROW)")
        void shouldFailWhenEntityIdMissing() {
            Map<String, Object> row = new HashMap<>();
            row.put("fieldName", "name");
            row.put("clazz", "T");

            SubjectResult result = subject.execute(buildCtx(row, defaultParams()));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.errorCode()).isEqualTo("INVALID_ROW");
        }

        // Given row without fieldName
        // Then errorCode == "INVALID_ROW"
        @Test
        @DisplayName("row 缺 fieldName → failure(INVALID_ROW)")
        void shouldFailWhenFieldNameMissing() {
            Map<String, Object> row = new HashMap<>();
            row.put("entityId", "1");
            row.put("clazz", "T");

            SubjectResult result = subject.execute(buildCtx(row, defaultParams()));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.errorCode()).isEqualTo("INVALID_ROW");
        }
    }

    @Nested
    @DisplayName("Scenario: HTTP 异常")
    class HttpException {

        // Given executorClient.fetchFields throws RuntimeException
        // Then errorCode == "FETCH_ERROR"
        @Test
        @DisplayName("executorClient 抛 RuntimeException → failure(FETCH_ERROR)")
        void shouldFailWhenExecutorThrows() {
            when(executorClient.fetchFields(any(), any(), anyList(), anyList()))
                    .thenThrow(new RuntimeException("boom"));

            SubjectResult result = subject.execute(buildCtx(defaultRow(), defaultParams()));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.errorCode()).isEqualTo("FETCH_ERROR");
            assertThat(result.errorMessage()).isEqualTo("boom");
        }
    }

    @Nested
    @DisplayName("Scenario: getType 返 DATASOURCE")
    class GetType {

        @Test
        @DisplayName("getType() == BatchTestSessionEntity.SUBJECT_DATASOURCE")
        void shouldReturnDatasourceType() {
            assertThat(subject.getType()).isEqualTo(BatchTestSessionEntity.SUBJECT_DATASOURCE);
            assertThat(subject.getType()).isEqualTo("DATASOURCE");
        }
    }
}
