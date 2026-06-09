package com.ruleforge.console.batchtest.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * console-app 调 executor-app /test/datasource/fetch 的 HTTP 客户端(V5.8.0)
 *
 * 跨模块调 DatasourceRoutingProvider(executor 内部组件)走 HTTP 边界,
 * 避免把 DatasourceRoutingProvider 拆到独立子模块带来的依赖图复杂化。
 *
 * 端点:
 *   POST ${executor.url}/test/datasource/fetch
 *   body: { datasourceId, clazz, entityIds, fieldNames }
 *   returns: { rows: { entityId: { fieldName: value } }, count }
 *
 * 配置:
 *   ruleforge.exec.url — executor 的 base URL(默认 http://localhost:8280)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutorDatasourceClient {

    private final ObjectMapper objectMapper;

    @Value("${ruleforge.exec.url:http://localhost:8280}")
    private String executorUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 批量拉取:给 entityIds × fieldNames,返回 entityId → field → value 的 map
     */
    public Map<String, Map<String, Object>> fetchFields(
            Long datasourceId, String clazz, List<String> entityIds, List<String> fieldNames) {
        if (entityIds == null || entityIds.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("datasourceId", datasourceId);
            body.put("clazz", clazz);
            body.put("entityIds", entityIds);
            body.put("fieldNames", fieldNames);

            String json = objectMapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(executorUrl + "/test/datasource/fetch"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(60))  // 批量可能要 30s+
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.error("Executor datasource fetch failed: status={} body={}",
                        resp.statusCode(), resp.body());
                throw new RuntimeException("Executor 返非 2xx: " + resp.statusCode());
            }
            Map<String, Object> parsed = objectMapper.readValue(resp.body(), new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> rows = (Map<String, Map<String, Object>>) parsed.get("rows");
            return rows != null ? rows : Map.of();
        } catch (Exception e) {
            log.error("Executor datasource fetch 出错 datasourceId={} clazz={}", datasourceId, clazz, e);
            throw new RuntimeException("调 executor 失败: " + e.getMessage(), e);
        }
    }
}
