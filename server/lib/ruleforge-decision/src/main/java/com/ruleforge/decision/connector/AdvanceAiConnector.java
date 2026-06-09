package com.ruleforge.decision.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.decision.entity.Datasource;
import com.ruleforge.decision.entity.DatasourceLog;
import com.ruleforge.decision.repository.DatasourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advance AI 数据源连接器
 * 直接调用 Advance AI 的 6 个 API 端点，封装认证、缓存、日志、字段映射
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdvanceAiConnector implements DataSourceConnector {

    private final AdvanceAiTokenManager tokenManager;
    private final DatasourceRepository datasourceRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ===== API 端点常量 =====
    private static final String MULTI_PLATFORM = "/mex/openapi/verification/v3/multi-platform";
    private static final String OVERDUE_DETECTION = "/intl/openapi/verification/v3/detection/multi-platform-overdue";
    private static final String ID_FORGERY_DETECTION = "/mex/openapi/face-identity/v1/id-forgery-detection";
    private static final String CURP_INFO_CHECK = "/mex/openapi/verification/v1/id-check";
    private static final String NEGATIVE_LIST_CHECK = "/mex/openapi/verification/v1/negative-list-check";
    private static final String CREDIT_SCORE = "/mex/openapi/score/v2/credit-score";

    // ===== 端点标识 =====
    private static final String EP_MULTI_PLATFORM = "MULTI_PLATFORM_DETECTION";
    private static final String EP_OVERDUE = "MULTI_PLATFORM_OVERDUE_DETECTION";
    private static final String EP_ID_FORGERY = "ID_FORGERY_DETECTION";
    private static final String EP_CURP_CHECK = "CURP_INFO_CHECK";
    private static final String EP_NEGATIVE_LIST = "NEGATIVE_LIST_CHECK";
    private static final String EP_CREDIT_SCORE = "CREDIT_SCORE";

    // ===== 字段名映射正则 =====
    private static final Pattern ADVANCE_GD_PATTERN = Pattern.compile("^advance_(gd_x_\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADVANCE_GENERAL_PATTERN = Pattern.compile("^advance_(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADVANCE_MPOD_PATTERN = Pattern.compile("^advance_mpod_(\\d+)$", Pattern.CASE_INSENSITIVE);

    private static final long DEFAULT_CACHE_TTL_HOURS = 120;

    @Override
    public String getConnectorType() {
        return "ADVANCE_AI";
    }

    @Override
    public Object fetchFieldValue(Datasource datasource, String entityId, String clazz,
                                  String fieldName, Map<String, String> context) {
        log.debug("Advance AI fetchFieldValue: entityId={}, clazz={}, fieldName={}", entityId, clazz, fieldName);

        try {
            // 1. 确定调用哪个 API 端点
            String apiEndpoint = resolveApiEndpoint(fieldName);

            // 2. 查询缓存
            DatasourceLog cachedLog = queryCache(datasource.getId(), entityId, apiEndpoint);
            String responseJson;
            boolean newQuery;
            long cacheTtlHours = datasource.getCacheTtlHours() != null ? datasource.getCacheTtlHours() : DEFAULT_CACHE_TTL_HOURS;

            if (cachedLog != null && !isCacheExpired(cachedLog, cacheTtlHours)) {
                responseJson = cachedLog.getResponseData();
                newQuery = false;
                log.debug("使用缓存数据: entityId={}, endpoint={}", entityId, apiEndpoint);
            } else {
                // 3. 调用 Advance AI API
                responseJson = callAdvanceAiApi(datasource, entityId, apiEndpoint, context);
                newQuery = true;
            }

            // 4. 映射字段名并提取字段值
            Object fieldValue = null;
            if (responseJson != null) {
                String mappedFieldName = mapFieldName(fieldName);
                fieldValue = extractFieldValue(responseJson, mappedFieldName, apiEndpoint);
            }

            return fieldValue;

        } catch (Exception e) {
            log.error("Advance AI 获取字段值失败: entityId={}, fieldName={}", entityId, fieldName, e);
            return null;
        }
    }

    @Override
    public boolean testConnection(Datasource datasource) {
        try {
            String token = tokenManager.getAccessToken(datasource);
            return token != null && !token.isEmpty();
        } catch (Exception e) {
            log.error("Advance AI 连接测试失败", e);
            return false;
        }
    }

    // ===== API 端点路由 =====

    String resolveApiEndpoint(String fieldName) {
        if (fieldName == null) {
            return EP_MULTI_PLATFORM;
        }
        // MPOD 逾期检测
        if (ADVANCE_MPOD_PATTERN.matcher(fieldName).matches()) {
            return EP_OVERDUE;
        }
        // 假证检测
        if ("advance_fakecurp_check_result".equalsIgnoreCase(fieldName)) {
            return EP_ID_FORGERY;
        }
        // 黑名单
        if ("is_adv_blacklisted".equalsIgnoreCase(fieldName)) {
            return EP_NEGATIVE_LIST;
        }
        // 信用评分
        if ("adv_credit_score".equalsIgnoreCase(fieldName)) {
            return EP_CREDIT_SCORE;
        }
        // CURP 姓名比对
        if ("advance_curp_name_similarity".equalsIgnoreCase(fieldName)) {
            return EP_CURP_CHECK;
        }
        // 默认多平台检测
        return EP_MULTI_PLATFORM;
    }

    // ===== 字段名映射 =====

    String mapFieldName(String engineFieldName) {
        // 黑名单特殊映射
        if ("is_adv_blacklisted".equalsIgnoreCase(engineFieldName)) {
            return "isHit";
        }
        // 信用评分特殊映射
        if ("adv_credit_score".equalsIgnoreCase(engineFieldName)) {
            return "score";
        }
        // advance_gd_x_19 -> GD_X_19
        Matcher gdMatcher = ADVANCE_GD_PATTERN.matcher(engineFieldName);
        if (gdMatcher.matches()) {
            return gdMatcher.group(1).toUpperCase();
        }
        // advance_mpod_28 -> MPOD_28
        Matcher mpodMatcher = ADVANCE_MPOD_PATTERN.matcher(engineFieldName);
        if (mpodMatcher.matches()) {
            return "MPOD_" + mpodMatcher.group(1);
        }
        // advance_fakecurp_check_result -> FAKECURP_CHECK_RESULT
        Matcher generalMatcher = ADVANCE_GENERAL_PATTERN.matcher(engineFieldName);
        if (generalMatcher.matches()) {
            return generalMatcher.group(1).toUpperCase();
        }
        return engineFieldName;
    }

    // ===== 响应解析 =====

    Object extractFieldValue(String responseJson, String fieldPath, String apiEndpoint) {
        try {
            JsonNode rootNode = objectMapper.readTree(responseJson);
            JsonNode dataNode = rootNode.path("data");
            if (dataNode.isMissingNode()) {
                return null;
            }

            // 特殊处理：黑名单 isHit
            if ("isHit".equals(fieldPath)) {
                JsonNode isHitNode = dataNode.path("isHit");
                if (!isHitNode.isMissingNode()) {
                    return parseJsonValue(isHitNode);
                }
                return false;
            }

            // 特殊处理：假证检测 — 检查 rejectReason
            if ("FAKECURP_CHECK_RESULT".equalsIgnoreCase(fieldPath)) {
                JsonNode resultNode = dataNode.path("result");
                if (!resultNode.isMissingNode()) {
                    return "fail".equalsIgnoreCase(resultNode.asText());
                }
                JsonNode rejectReasonNode = dataNode.path("rejectReason");
                boolean hasRejectReason = !rejectReasonNode.isMissingNode()
                        && rejectReasonNode.isObject()
                        && (rejectReasonNode.has("face_tampering") || rejectReasonNode.has("text_tampering"));
                if (hasRejectReason) {
                    return true;
                }
                return false;
            }

            // 通用：从 data 节点提取字段
            JsonNode fieldNode = dataNode.path(fieldPath);
            if (!fieldNode.isMissingNode()) {
                return parseJsonValue(fieldNode);
            }

            return null;
        } catch (Exception e) {
            log.error("提取字段值失败: fieldPath={}, endpoint={}", fieldPath, apiEndpoint, e);
            return null;
        }
    }

    private Object parseJsonValue(JsonNode node) {
        if (node.isNull() || node.isMissingNode()) return null;
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble() || node.isFloat()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isBigDecimal()) return node.decimalValue();
        return node.asText();
    }

    // ===== API 调用 =====

    private String callAdvanceAiApi(Datasource datasource, String entityId,
                                    String apiEndpoint, Map<String, String> context) {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        try {
            JsonNode config = objectMapper.readTree(datasource.getConfigJson());
            String baseUrl = config.path("baseUrl").asText();
            String accessToken = tokenManager.getAccessToken(datasource);

            // 从 context 取用户资料
            String idNumber = context.getOrDefault("idNumber", "");
            String phoneNumber = context.getOrDefault("phoneNumber", "");
            String name = context.getOrDefault("name", "");

            String url;
            HttpEntity<?> entity;
            String requestData;

            switch (apiEndpoint) {
                case EP_MULTI_PLATFORM -> {
                    url = baseUrl + MULTI_PLATFORM;
                    Map<String, Object> body = new HashMap<>();
                    body.put("idNumber", idNumber);
                    body.put("phoneNumber", phoneNumber);
                    body.put("name", name);
                    body.put("includeSelf", "exclude");
                    requestData = objectMapper.writeValueAsString(body);
                    entity = buildJsonEntity(body, accessToken);
                }
                case EP_OVERDUE -> {
                    url = baseUrl + OVERDUE_DETECTION;
                    Map<String, Object> body = new HashMap<>();
                    body.put("phoneNumber", phoneNumber);
                    body.put("countryCode", "+52");
                    body.put("name", name);
                    body.put("idNumber", idNumber);
                    requestData = objectMapper.writeValueAsString(body);
                    entity = buildJsonEntity(body, accessToken);
                }
                case EP_ID_FORGERY -> {
                    url = baseUrl + ID_FORGERY_DETECTION;
                    String cardImageBase64 = context.getOrDefault("cardImageBase64", "");
                    MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
                    formData.add("cardImageBase64", cardImageBase64);
                    requestData = "{\"cardImageBase64\":\"...\"}";
                    entity = buildMultipartEntity(formData, accessToken);
                }
                case EP_CURP_CHECK -> {
                    url = baseUrl + CURP_INFO_CHECK;
                    Map<String, String> body = Map.of("curp", idNumber);
                    requestData = objectMapper.writeValueAsString(body);
                    entity = buildJsonEntity(body, accessToken);
                }
                case EP_NEGATIVE_LIST -> {
                    url = baseUrl + NEGATIVE_LIST_CHECK;
                    Map<String, String> body = new HashMap<>();
                    body.put("curp", idNumber);
                    body.put("phoneNumber", phoneNumber);
                    body.put("productCode", "M303");
                    requestData = objectMapper.writeValueAsString(body);
                    entity = buildJsonEntity(body, accessToken);
                }
                case EP_CREDIT_SCORE -> {
                    url = baseUrl + CREDIT_SCORE;
                    String phone = phoneNumber;
                    if (phone != null && phone.length() == 10 && !phone.startsWith("+52")) {
                        phone = "+52" + phone;
                    }
                    Map<String, Object> body = new HashMap<>();
                    body.put("name", name);
                    body.put("curp", idNumber);
                    body.put("phoneNumber", phone);
                    requestData = objectMapper.writeValueAsString(body);
                    entity = buildJsonEntity(body, accessToken);
                }
                default -> throw new IllegalArgumentException("不支持的 API 端点: " + apiEndpoint);
            }

            log.debug("调用 Advance AI API: endpoint={}, entityId={}", apiEndpoint, entityId);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            String responseBody = response.getBody();
            long responseTime = System.currentTimeMillis() - startTime;

            // 记录成功日志
            logApiCall(datasource.getId(), entityId, apiEndpoint, requestData,
                    responseBody, response.getStatusCode().value(), "SUCCESS", null, responseTime, requestId);

            return responseBody;

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("Advance AI API 调用失败: endpoint={}, entityId={}", apiEndpoint, entityId, e);
            logApiCall(datasource.getId(), entityId, apiEndpoint, null,
                    null, null, "ERROR", e.getMessage(), responseTime, requestId);
            throw new RuntimeException("Advance AI API 调用失败: " + e.getMessage(), e);
        }
    }

    private HttpEntity<?> buildJsonEntity(Object body, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-ACCESS-TOKEN", accessToken);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<?> buildMultipartEntity(MultiValueMap<String, Object> formData, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-ACCESS-TOKEN", accessToken);
        return new HttpEntity<>(formData, headers);
    }

    // ===== 缓存查询 =====

    private DatasourceLog queryCache(Long datasourceId, String userId, String apiEndpoint) {
        return datasourceRepository.findCachedLog(datasourceId, userId, apiEndpoint);
    }

    private boolean isCacheExpired(DatasourceLog logEntry, long ttlHours) {
        if (logEntry.getCreatedAt() == null) {
            return true;
        }
        long hoursPassed = ChronoUnit.HOURS.between(
                logEntry.getCreatedAt().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime(),
                LocalDateTime.now());
        return hoursPassed >= ttlHours;
    }

    // ===== 日志记录 =====

    private void logApiCall(Long datasourceId, String userId, String apiEndpoint,
                            String requestData, String responseData, Integer httpStatus,
                            String status, String errorMessage, long responseTimeMs, String requestId) {
        try {
            DatasourceLog logEntry = new DatasourceLog();
            logEntry.setUserId(userId);
            logEntry.setDatasourceId(datasourceId);
            logEntry.setDataSource("ADVANCE_AI");
            logEntry.setApiEndpoint(apiEndpoint);
            logEntry.setRequestMethod("POST");
            logEntry.setRequestData(truncate(requestData, 65535));
            logEntry.setResponseData(responseData);
            logEntry.setHttpStatus(httpStatus);
            logEntry.setStatus(status);
            logEntry.setErrorMessage(errorMessage != null ? truncate(errorMessage, 1024) : null);
            logEntry.setResponseTimeMs(responseTimeMs);
            logEntry.setRequestId(requestId);
            datasourceRepository.insertDatasourceLog(logEntry);
        } catch (Exception e) {
            log.error("记录数据源调用日志失败", e);
        }
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }
}
