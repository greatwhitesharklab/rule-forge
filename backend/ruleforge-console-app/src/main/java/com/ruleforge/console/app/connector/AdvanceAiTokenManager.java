package com.ruleforge.console.app.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.entity.Datasource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Advance AI Token 管理器
 * 负责 HMAC-SHA256 签名认证和 token 缓存
 */
@Slf4j
@Component
public class AdvanceAiTokenManager {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String cachedToken;
    private volatile long tokenExpireTime = 0L;
    private volatile String cachedBaseUrl;
    private final Object tokenLock = new Object();

    private static final String TOKEN_API_URL = "/openapi/auth/ticket/v1/generate-token";

    public AdvanceAiTokenManager(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 获取有效的 access token
     */
    public String getAccessToken(Datasource datasource) {
        String configJson = datasource.getConfigJson();
        try {
            JsonNode config = objectMapper.readTree(configJson);
            String baseUrl = config.path("baseUrl").asText();

            // 如果数据源配置变了，清除缓存
            if (!baseUrl.equals(cachedBaseUrl)) {
                synchronized (tokenLock) {
                    cachedToken = null;
                    tokenExpireTime = 0L;
                    cachedBaseUrl = baseUrl;
                }
            }

            if (cachedToken != null && System.currentTimeMillis() < tokenExpireTime) {
                return cachedToken;
            }

            synchronized (tokenLock) {
                if (cachedToken != null && System.currentTimeMillis() < tokenExpireTime) {
                    return cachedToken;
                }
                return generateNewToken(config);
            }
        } catch (Exception e) {
            log.error("解析 Advance AI 配置失败", e);
            throw new RuntimeException("解析 Advance AI 配置失败: " + e.getMessage(), e);
        }
    }

    private String generateNewToken(JsonNode config) {
        try {
            String baseUrl = config.path("baseUrl").asText();
            String accessKey = config.path("accessKey").asText();
            String secretKey = config.path("secretKey").asText();
            int periodSecond = config.path("tokenValiditySeconds").asInt(3600);
            int bufferMinutes = config.path("tokenExpireBufferMinutes").asInt(5);

            String timestamp = String.valueOf(System.currentTimeMillis());
            String signature = generateSignature(accessKey, secretKey, timestamp);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("accessKey", accessKey);
            requestBody.put("timestamp", timestamp);
            requestBody.put("signature", signature);
            requestBody.put("periodSecond", periodSecond);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String url = baseUrl + TOKEN_API_URL;
            log.info("生成新的 Advance AI access token: url={}", url);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode jsonNode = objectMapper.readTree(response.getBody());
                if ("SUCCESS".equals(jsonNode.path("code").asText())) {
                    JsonNode data = jsonNode.path("data");
                    String token = data.path("token").asText();
                    long expiredTime = data.path("expiredTime").asLong();

                    this.cachedToken = token;
                    this.tokenExpireTime = expiredTime - bufferMinutes * 60 * 1000L;

                    log.info("成功获取 Advance AI access token，过期时间: {}",
                            Instant.ofEpochMilli(expiredTime));
                    return token;
                } else {
                    throw new RuntimeException("获取 token 失败: " + jsonNode.path("message").asText());
                }
            } else {
                throw new RuntimeException("API 调用失败，状态码: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("生成 Advance AI access token 失败", e);
            throw new RuntimeException("生成 access token 失败: " + e.getMessage(), e);
        }
    }

    private String generateSignature(String accessKey, String secretKey, String timestamp) {
        try {
            String combined = accessKey + secretKey + timestamp;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("生成签名失败", e);
        }
    }

    /**
     * 清除 token 缓存
     */
    public void clearTokenCache() {
        synchronized (tokenLock) {
            this.cachedToken = null;
            this.tokenExpireTime = 0L;
        }
    }
}
