package com.ruleforge.decision.connector;

import com.ruleforge.decision.entity.Datasource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: Advance AI Token 管理
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdvanceAiTokenManager - Token 认证管理")
class AdvanceAiTokenManagerTest {

    @Mock private RestTemplate restTemplate;

    private AdvanceAiTokenManager tokenManager;

    @BeforeEach
    void setUp() {
        tokenManager = new AdvanceAiTokenManager(restTemplate);
    }

    private Datasource buildDatasource(String baseUrl, String accessKey, String secretKey) {
        Datasource ds = new Datasource();
        ds.setId(1L);
        ds.setType("ADVANCE_AI");
        ds.setConfigJson("{\"baseUrl\":\"" + baseUrl + "\",\"accessKey\":\"" + accessKey
                + "\",\"secretKey\":\"" + secretKey + "\",\"tokenValiditySeconds\":3600,\"tokenExpireBufferMinutes\":5}");
        return ds;
    }

    private String buildTokenResponse(String token, long expiredTime) {
        return "{\"code\":\"SUCCESS\",\"message\":\"ok\",\"data\":{\"token\":\"" + token
                + "\",\"expiredTime\":" + expiredTime + "}}";
    }

    @Nested
    @DisplayName("Scenario: 获取 Token")
    class GetAccessToken {

        // Given 首次请求 token
        // When getAccessToken 被调用
        // Then 调用 API 获取 token 并缓存
        @Test
        @DisplayName("首次获取 token 成功")
        void shouldFetchNewToken() {
            // Given
            Datasource ds = buildDatasource("https://api.example.com", "ak123", "sk456");
            long futureExpiry = System.currentTimeMillis() + 3600000;
            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(buildTokenResponse("token-abc", futureExpiry), HttpStatus.OK));

            // When
            String token = tokenManager.getAccessToken(ds);

            // Then
            assertThat(token).isEqualTo("token-abc");
            verify(restTemplate).exchange(contains("generate-token"), any(), any(), eq(String.class));
        }

        // Given token 已缓存且未过期
        // When getAccessToken 被调用
        // Then 直接返回缓存 token，不再调 API
        @Test
        @DisplayName("缓存有效时复用 token")
        void shouldReuseCachedToken() {
            // Given
            Datasource ds = buildDatasource("https://api.example.com", "ak123", "sk456");
            long futureExpiry = System.currentTimeMillis() + 3600000;
            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(buildTokenResponse("token-cached", futureExpiry), HttpStatus.OK));

            // When — 连续调用两次
            String token1 = tokenManager.getAccessToken(ds);
            String token2 = tokenManager.getAccessToken(ds);

            // Then — 只调用一次 API
            assertThat(token1).isEqualTo("token-cached");
            assertThat(token2).isEqualTo("token-cached");
            verify(restTemplate, times(1)).exchange(anyString(), any(), any(), eq(String.class));
        }

        // Given API 返回失败
        // When getAccessToken 被调用
        // Then 抛出 RuntimeException
        @Test
        @DisplayName("API 返回失败时抛异常")
        void shouldThrowWhenApiFails() {
            // Given
            Datasource ds = buildDatasource("https://api.example.com", "ak123", "sk456");
            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("{\"code\":\"FAIL\",\"message\":\"auth error\"}", HttpStatus.OK));

            // When / Then
            assertThatThrownBy(() -> tokenManager.getAccessToken(ds))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("获取 token 失败");
        }

        // Given token 已缓存
        // When clearTokenCache 被调用后再获取
        // Then 重新调 API
        @Test
        @DisplayName("清除缓存后重新获取")
        void shouldRefetchAfterCacheClear() {
            // Given
            Datasource ds = buildDatasource("https://api.example.com", "ak123", "sk456");
            long futureExpiry = System.currentTimeMillis() + 3600000;
            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(buildTokenResponse("token-1", futureExpiry), HttpStatus.OK))
                    .thenReturn(new ResponseEntity<>(buildTokenResponse("token-2", futureExpiry), HttpStatus.OK));

            // When
            String token1 = tokenManager.getAccessToken(ds);
            tokenManager.clearTokenCache();
            String token2 = tokenManager.getAccessToken(ds);

            // Then
            assertThat(token1).isEqualTo("token-1");
            assertThat(token2).isEqualTo("token-2");
            verify(restTemplate, times(2)).exchange(anyString(), any(), any(), eq(String.class));
        }
    }
}
