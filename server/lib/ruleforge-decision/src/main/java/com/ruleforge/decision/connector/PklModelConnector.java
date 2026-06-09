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
 * PKL 模型数据源连接器
 * 通过调用 Python model-service 获取模型预测结果
 * <p>
 * configJson 格式:
 * {
 *   "modelServiceUrl": "http://localhost:8501",
 *   "modelId": "credit_scoring_v1"
 * }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PklModelConnector implements DataSourceConnector {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getConnectorType() {
        return "PKL";
    }

    @Override
    public Object fetchFieldValue(Datasource datasource, String entityId, String clazz,
                                  String fieldName, Map<String, String> context) {
        try {
            JsonNode config = objectMapper.readTree(datasource.getConfigJson());
            String baseUrl = config.path("modelServiceUrl").asText();
            String modelId = config.path("modelId").asText();

            if (baseUrl.isEmpty() || modelId.isEmpty()) {
                log.warn("PKL model 配置不完整: modelServiceUrl={}, modelId={}", baseUrl, modelId);
                return null;
            }

            String url = baseUrl + "/predict";

            // 构建预测请求体
            Map<String, Object> body = Map.of(
                    "model_id", modelId,
                    "inputs", context
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            log.debug("PKL model 预测: url={}, modelId={}, fieldName={}", url, modelId, fieldName);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("PKL model 预测失败: status={}", response.getStatusCode());
                return null;
            }

            // 从 outputs 中提取字段值
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode fieldNode = root.path("outputs").path(fieldName);
            return fieldNode.isMissingNode() || fieldNode.isNull() ? null : parseJsonValue(fieldNode);

        } catch (Exception e) {
            log.error("PKL model 获取字段值失败: datasourceId={}, fieldName={}", datasource.getId(), fieldName, e);
            return null;
        }
    }

    @Override
    public boolean testConnection(Datasource datasource) {
        try {
            JsonNode config = objectMapper.readTree(datasource.getConfigJson());
            String baseUrl = config.path("modelServiceUrl").asText();
            String modelId = config.path("modelId").asText();

            if (baseUrl.isEmpty()) {
                return false;
            }

            // 检查服务健康状态
            ResponseEntity<String> health = restTemplate.exchange(
                    baseUrl + "/health", HttpMethod.GET, null, String.class);
            if (!health.getStatusCode().is2xxSuccessful()) {
                return false;
            }

            // 验证指定模型存在
            if (!modelId.isEmpty()) {
                ResponseEntity<String> modelResp = restTemplate.exchange(
                        baseUrl + "/models/" + modelId, HttpMethod.GET, null, String.class);
                return modelResp.getStatusCode().is2xxSuccessful();
            }

            return true;
        } catch (Exception e) {
            log.error("PKL model 连接测试失败: datasourceId={}", datasource.getId(), e);
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
