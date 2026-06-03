package com.ruleforge.console.batchtest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: SubjectResult — BatchTestSubject.execute() 出参 (V5.8.0)
 *
 * 工厂方法行为约定:
 *   - success(obj, latency)             → isSuccess true,httpStatus/errorCode/errorMessage 都为 null
 *   - successWithStatus(obj, latency, status) → isSuccess true,httpStatus 等于传入值
 *   - failure(code, message, latency)   → isSuccess false,output 为 null
 */
@DisplayName("SubjectResult - BatchTestSubject 出参 record (V5.8.0)")
class SubjectResultTest {

    @Nested
    @DisplayName("Scenario: success(没 httpStatus)")
    class Success {

        // Given SubjectResult.success(obj, 100)
        // Then isSuccess() == true, output == obj, latencyMs == 100,
        //      httpStatus == null, errorCode == null
        @Test
        @DisplayName("success(obj, 100) → 全 null 错误字段")
        void shouldCreateSuccessResultWithoutHttpStatus() {
            Object obj = Map.of("k", "v");

            SubjectResult r = SubjectResult.success(obj, 100L);

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.output()).isSameAs(obj);
            assertThat(r.latencyMs()).isEqualTo(100L);
            assertThat(r.httpStatus()).isNull();
            assertThat(r.errorCode()).isNull();
            assertThat(r.errorMessage()).isNull();
        }
    }

    @Nested
    @DisplayName("Scenario: successWithStatus(DATASOURCE 模式带 httpStatus)")
    class SuccessWithStatus {

        // Given SubjectResult.successWithStatus(obj, 100, 200)
        // Then isSuccess() == true, httpStatus == 200
        @Test
        @DisplayName("successWithStatus(obj, 100, 200) → httpStatus 等于 200")
        void shouldCreateSuccessResultWithHttpStatus() {
            Object obj = "payload";

            SubjectResult r = SubjectResult.successWithStatus(obj, 100L, 200);

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.output()).isEqualTo("payload");
            assertThat(r.latencyMs()).isEqualTo(100L);
            assertThat(r.httpStatus()).isEqualTo(200);
            assertThat(r.errorCode()).isNull();
            assertThat(r.errorMessage()).isNull();
        }
    }

    @Nested
    @DisplayName("Scenario: failure")
    class Failure {

        // Given SubjectResult.failure("X", "msg", 50)
        // Then isSuccess() == false, errorCode == "X", errorMessage == "msg", latencyMs == 50
        @Test
        @DisplayName("failure(X, msg, 50) → isSuccess false + output null")
        void shouldCreateFailureResult() {
            SubjectResult r = SubjectResult.failure("X", "msg", 50L);

            assertThat(r.isSuccess()).isFalse();
            assertThat(r.errorCode()).isEqualTo("X");
            assertThat(r.errorMessage()).isEqualTo("msg");
            assertThat(r.latencyMs()).isEqualTo(50L);
            assertThat(r.output()).isNull();
            assertThat(r.httpStatus()).isNull();
        }
    }
}
