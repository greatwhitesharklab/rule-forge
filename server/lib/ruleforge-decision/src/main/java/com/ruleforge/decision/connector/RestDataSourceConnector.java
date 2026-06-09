package com.ruleforge.decision.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.decision.entity.Datasource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 通用 REST 数据源连接器
 * 通过 config_json 中的配置调用任意 REST API
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestDataSourceConnector implements DataSourceConnector {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getConnectorType() {
        return "REST_API";
    }

    @Override
    public Object fetchFieldValue(Datasource datasource, String entityId, String clazz,
                                  String fieldName, Map<String, String> context) {
        try {
            JsonNode config = objectMapper.readTree(datasource.getConfigJson());
            String baseUrl = config.path("baseUrl").asText();
            String endpoint = config.path("endpoint").asText();
            String method = config.path("method").asText("POST");
            String responseFieldPath = config.path("responseFieldPath").asText(fieldName);

            String url = baseUrl + endpoint;

            // 构建请求体
            Map<String, Object> requestBody = Map.of(
                    "userId", entityId,
                    "dataSource", clazz,
                    "fieldName", fieldName
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 添加自定义 headers
            JsonNode headersNode = config.path("headers");
            if (headersNode.isObject()) {
                headersNode.fields().forEachRemaining(e -> headers.set(e.getKey(), e.getValue().asText()));
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.debug("REST API 调用: url={}, entityId={}, fieldName={}", url, entityId, fieldName);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.valueOf(method), entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("REST API 返回异常: status={}", response.getStatusCode());
                return null;
            }

            // 从响应中提取字段值
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode fieldNode = root.path(responseFieldPath);
            if (fieldNode.isMissingNode()) {
                // 尝试从 data 节点查找
                fieldNode = root.path("data").path(responseFieldPath);
            }
            return fieldNode.isMissingNode() || fieldNode.isNull() ? null : parseJsonValue(fieldNode);

        } catch (Exception e) {
            log.error("REST API 获取字段值失败: datasourceId={}, fieldName={}", datasource.getId(), fieldName, e);
            return null;
        }
    }

    @Override
    public boolean testConnection(Datasource datasource) {
        try {
            JsonNode config = objectMapper.readTree(datasource.getConfigJson());
            String baseUrl = config.path("baseUrl").asText();
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl, HttpMethod.HEAD, null, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("REST 连接测试失败: datasourceId={}", datasource.getId(), e);
            return false;
        }
    }

    private Object parseJsonValue(JsonNode node) {
        if (node.isNull() || node.isMissingNode()) return null;
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble() || node.isFloat()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
        return node.asText();
    }
}
