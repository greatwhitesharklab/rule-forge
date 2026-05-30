package com.ruleforge.console.app.connector;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruleforge.console.app.entity.Datasource;
import com.ruleforge.console.app.entity.DatasourceLog;
import com.ruleforge.console.app.mapper.DatasourceLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: Advance AI 数据源连接器
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdvanceAiConnector - Advance AI 数据源连接器")
class AdvanceAiConnectorTest {

    @Mock private AdvanceAiTokenManager tokenManager;
    @Mock private DatasourceLogMapper datasourceLogMapper;
    @Mock private RestTemplate restTemplate;

    private AdvanceAiConnector connector;

    @BeforeEach
    void setUp() {
        connector = new AdvanceAiConnector(tokenManager, datasourceLogMapper, restTemplate);
    }

    private Datasource buildDatasource() {
        Datasource ds = new Datasource();
        ds.setId(1L);
        ds.setType("ADVANCE_AI");
        ds.setConfigJson("{\"baseUrl\":\"https://mex-api.advance.ai\",\"accessKey\":\"ak\",\"secretKey\":\"sk\"}");
        ds.setEnabled(true);
        ds.setTimeoutMs(30000);
        ds.setCacheEnabled(true);
        ds.setCacheTtlHours(120);
        return ds;
    }

    private DatasourceLog buildCachedLog(String responseData) {
        DatasourceLog log = new DatasourceLog();
        log.setId(1L);
        log.setResponseData(responseData);
        log.setCreatedAt(new Date()); // 刚创建，未过期
        log.setStatus("SUCCESS");
        return log;
    }

    @Nested
    @DisplayName("Scenario: 字段→API 端点路由")
    class ApiEndpointRouting {

        @Test
        @DisplayName("advance_gd_x_19 路由到多平台检测")
        void shouldRouteGdFieldsToMultiPlatform() {
            // When
            String result = connector.resolveApiEndpoint("advance_gd_x_19");
            // Then
            assertThat(result).isEqualTo("MULTI_PLATFORM_DETECTION");
        }

        @Test
        @DisplayName("advance_mpod_28 路由到逾期检测")
        void shouldRouteMpodFieldsToOverdue() {
            assertThat(connector.resolveApiEndpoint("advance_mpod_28")).isEqualTo("MULTI_PLATFORM_OVERDUE_DETECTION");
        }

        @Test
        @DisplayName("advance_fakecurp_check_result 路由到假证检测")
        void shouldRouteFakecurpToForgery() {
            assertThat(connector.resolveApiEndpoint("advance_fakecurp_check_result")).isEqualTo("ID_FORGERY_DETECTION");
        }

        @Test
        @DisplayName("is_adv_blacklisted 路由到黑名单")
        void shouldRouteBlacklistToNegativeList() {
            assertThat(connector.resolveApiEndpoint("is_adv_blacklisted")).isEqualTo("NEGATIVE_LIST_CHECK");
        }

        @Test
        @DisplayName("adv_credit_score 路由到信用评分")
        void shouldRouteCreditScore() {
            assertThat(connector.resolveApiEndpoint("adv_credit_score")).isEqualTo("CREDIT_SCORE");
        }

        @Test
        @DisplayName("advance_curp_name_similarity 路由到 CURP 查询")
        void shouldRouteCurpToCurpCheck() {
            assertThat(connector.resolveApiEndpoint("advance_curp_name_similarity")).isEqualTo("CURP_INFO_CHECK");
        }
    }

    @Nested
    @DisplayName("Scenario: 字段名映射")
    class FieldNameMapping {

        @Test
        @DisplayName("advance_gd_x_19 → GD_X_19")
        void shouldMapGdFieldToUppercase() {
            assertThat(connector.mapFieldName("advance_gd_x_19")).isEqualTo("GD_X_19");
        }

        @Test
        @DisplayName("is_adv_blacklisted → isHit")
        void shouldMapBlacklistToIsHit() {
            assertThat(connector.mapFieldName("is_adv_blacklisted")).isEqualTo("isHit");
        }

        @Test
        @DisplayName("adv_credit_score → score")
        void shouldMapCreditScoreToScore() {
            assertThat(connector.mapFieldName("adv_credit_score")).isEqualTo("score");
        }

        @Test
        @DisplayName("advance_mpod_28 → MPOD_28")
        void shouldMapMpodField() {
            assertThat(connector.mapFieldName("advance_mpod_28")).isEqualTo("MPOD_28");
        }

        @Test
        @DisplayName("advance_fakecurp_check_result → FAKECURP_CHECK_RESULT")
        void shouldMapFakecurpField() {
            assertThat(connector.mapFieldName("advance_fakecurp_check_result")).isEqualTo("FAKECURP_CHECK_RESULT");
        }
    }

    @Nested
    @DisplayName("Scenario: 响应解析")
    class ResponseExtraction {

        @Test
        @DisplayName("从多平台响应提取 GD_X_19 字段")
        void shouldExtractGdFieldFromResponse() {
            // Given
            String json = "{\"code\":\"SUCCESS\",\"data\":{\"GD_X_19\":0.123}}";

            // When
            Object value = connector.extractFieldValue(json, "GD_X_19", "MULTI_PLATFORM_DETECTION");

            // Then
            assertThat(value).isEqualTo(0.123);
        }

        @Test
        @DisplayName("从黑名单响应提取 isHit 字段")
        void shouldExtractIsHitFromResponse() {
            // Given
            String json = "{\"code\":\"SUCCESS\",\"data\":{\"isHit\":true}}";

            // When
            Object value = connector.extractFieldValue(json, "isHit", "NEGATIVE_LIST_CHECK");

            // Then
            assertThat(value).isEqualTo(true);
        }

        @Test
        @DisplayName("黑名单响应无 isHit 字段返回 false")
        void shouldReturnFalseWhenNoIsHit() {
            // Given
            String json = "{\"code\":\"SUCCESS\",\"data\":{}}";

            // When
            Object value = connector.extractFieldValue(json, "isHit", "NEGATIVE_LIST_CHECK");

            // Then
            assertThat(value).isEqualTo(false);
        }

        @Test
        @DisplayName("从信用评分响应提取 score 字段")
        void shouldExtractScoreFromResponse() {
            // Given
            String json = "{\"code\":\"SUCCESS\",\"data\":{\"score\":677.0}}";

            // When
            Object value = connector.extractFieldValue(json, "score", "CREDIT_SCORE");

            // Then
            assertThat(value).isEqualTo(677.0);
        }

        @Test
        @DisplayName("字段不存在时返回 null")
        void shouldReturnNullWhenFieldMissing() {
            // Given
            String json = "{\"code\":\"SUCCESS\",\"data\":{\"OTHER_FIELD\":42}}";

            // When
            Object value = connector.extractFieldValue(json, "NONEXISTENT", "MULTI_PLATFORM_DETECTION");

            // Then
            assertThat(value).isNull();
        }
    }

    @Nested
    @DisplayName("Scenario: 缓存命中")
    class CacheHit {

        @Test
        @DisplayName("缓存有效时直接从缓存返回字段值")
        void shouldReturnFromCacheWhenValid() {
            // Given
            Datasource ds = buildDatasource();
            String cachedResponse = "{\"code\":\"SUCCESS\",\"data\":{\"GD_X_19\":0.456}}";
            DatasourceLog cachedLog = buildCachedLog(cachedResponse);
            when(datasourceLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(cachedLog);

            Map<String, String> context = new HashMap<>();

            // When
            Object value = connector.fetchFieldValue(ds, "user1", "com.test.Model", "advance_gd_x_19", context);

            // Then — 使用缓存值，不调 API
            assertThat(value).isEqualTo(0.456);
            verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(String.class));
        }
    }

    @Nested
    @DisplayName("Scenario: API 调用（缓存未命中）")
    class ApiCall {

        @Test
        @DisplayName("缓存无数据时调用 API 并记录日志")
        void shouldCallApiAndLogWhenCacheMiss() {
            // Given
            Datasource ds = buildDatasource();
            when(datasourceLogMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(tokenManager.getAccessToken(ds)).thenReturn("test-token");
            String apiResponse = "{\"code\":\"SUCCESS\",\"data\":{\"GD_X_1\":0.5}}";
            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(apiResponse, HttpStatus.OK));
            when(datasourceLogMapper.insert(any(DatasourceLog.class))).thenReturn(1);

            Map<String, String> context = new HashMap<>();

            // When
            Object value = connector.fetchFieldValue(ds, "user1", "com.test.Model", "advance_gd_x_1", context);

            // Then
            assertThat(value).isEqualTo(0.5);
            verify(restTemplate).exchange(anyString(), any(), any(), eq(String.class));

            // 日志被记录
            ArgumentCaptor<DatasourceLog> logCaptor = ArgumentCaptor.forClass(DatasourceLog.class);
            verify(datasourceLogMapper).insert(logCaptor.capture());
            DatasourceLog logged = logCaptor.getValue();
            assertThat(logged.getDataSource()).isEqualTo("ADVANCE_AI");
            assertThat(logged.getStatus()).isEqualTo("SUCCESS");
            assertThat(logged.getUserId()).isEqualTo("user1");
        }
    }

    @Nested
    @DisplayName("Scenario: connectorType")
    class ConnectorType {

        @Test
        @DisplayName("返回 ADVANCE_AI 类型")
        void shouldReturnAdvanceAiType() {
            assertThat(connector.getConnectorType()).isEqualTo("ADVANCE_AI");
        }
    }
}
