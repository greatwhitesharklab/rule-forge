package com.ruleforge.decision.lazy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.decision.exception.AsyncDataSourcePendingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API 数据源提供者实现
 * 通过 HTTP 请求获取字段值
 * 适配风控数据源服务接口
 */
@Slf4j
public class RestDataSourceProvider implements DataSourceProvider {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final Map<String, String> defaultHeaders;

    /**
     * 构造函数
     *
     * @param restTemplate Spring RestTemplate
     * @param baseUrl 基础URL（如 http://localhost:8084/nova-risk-datasource）
     */
    public RestDataSourceProvider(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.objectMapper = new ObjectMapper();
        this.defaultHeaders = new HashMap<>();
        this.defaultHeaders.put("Content-Type", "application/json");
    }

    /**
     * 添加默认请求头
     */
    public void addDefaultHeader(String key, String value) {
        this.defaultHeaders.put(key, value);
    }

    @Override
    public Object fetchFieldValue(String entityId, String clazz, String fieldName) {
        try {
            // 构建请求URL: /api/v1/risk-datasource/field/query
            String url = String.format("%s/api/v1/risk-datasource/field/query", baseUrl);

            log.debug("Fetching field value from: {}, userId={}, dataSource={}, fieldName={}",
                    url, entityId, clazz, fieldName);

            // 从请求上下文获取 loanZone/orbitCode
            DecisionContext decisionContext = DecisionContext.current();
            String loanZone = decisionContext != null ? decisionContext.getLoanZone() : null;
            String orbitCode = decisionContext != null ? decisionContext.getOrbitCode() : null;

            // 构建请求体
            RiskDataFieldQueryRequest requestBody = RiskDataFieldQueryRequest.builder()
                    .userId(entityId)
                    .dataSource(clazz)
                    .fieldName(fieldName)
                    .loanZone(loanZone)
                    .orbitCode(orbitCode)
                    .build();

            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            defaultHeaders.forEach(headers::add);

            HttpEntity<RiskDataFieldQueryRequest> requestEntity = new HttpEntity<>(requestBody, headers);

            // 发送 POST 请求
            ResponseEntity<RiskDataFieldQueryResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                RiskDataFieldQueryResponse.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("Failed to fetch field value: HTTP {}", response.getStatusCode());
                return null;
            }

            RiskDataFieldQueryResponse responseBody = response.getBody();
            if (responseBody == null) {
                log.warn("Empty response body for field: {}", fieldName);
                return null;
            }

            // 检测异步状态
            if (Boolean.TRUE.equals(responseBody.getAsyncPending())) {
                throw new AsyncDataSourcePendingException(
                        responseBody.getAsyncDataSourceId(),
                        entityId,
                        clazz,
                        fieldName,
                        Boolean.TRUE.equals(responseBody.getAsyncTaskTriggered())
                );
            }

            // 提取 fieldValue
            Object fieldValue = responseBody.getFieldValue();
            log.debug("Field value fetched: fieldName={}, value={}, dataType={}, fromCache={}",
                    fieldName, fieldValue, responseBody.getDataType(), responseBody.getFromCache());

            return fieldValue;

        } catch (AsyncDataSourcePendingException e) {
            // 异步等待异常需要向上传播，中止决策流程
            throw e;
        } catch (Exception e) {
            log.error("Error fetching field value for userId={}, dataSource={}, fieldName={}",
                    entityId, clazz, fieldName, e);
            return null;
        }
    }
}
