package com.ruleforge.datasource.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.datasource.jcompiler.ClassLoaderPool;
import com.ruleforge.datasource.jcompiler.IJavaDataSource;
import com.ruleforge.datasource.jcompiler.JavaSourceCompiler;
import com.ruleforge.datasource.entity.Datasource;
import com.ruleforge.datasource.entity.DatasourceLog;
import com.ruleforge.datasource.repository.DatasourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * V5.23 — Connector that invokes an LLM-generated (or hand-written) Java
 * {@link IJavaDataSource} class loaded at runtime.
 *
 * <p>The compiled .class bytes are stored in the {@code nd_datasource.config_json}
 * field as base64, alongside the class name. This connector:
 * <ol>
 *   <li>Decodes the base64 to bytes</li>
 *   <li>Loads the class via {@link ClassLoaderPool} (per-datasource isolated loader)</li>
 *   <li>Instantiates and calls {@link IJavaDataSource#fetchField}</li>
 *   <li>Records the call in {@code nd_datasource_log} (mirroring
 *       {@code AdvanceAiConnector.logApiCall} pattern)</li>
 * </ol>
 *
 * <p>The connector is auto-discovered by {@code DatasourceServiceImpl} via
 * {@code @Component} and routed by {@code getConnectorType() = "AI_JAVA"}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiJavaDataSourceConnector implements DataSourceConnector {

    public static final String CONNECTOR_TYPE = "AI_JAVA";

    /** config_json keys: */
    public static final String CFG_CLASS_NAME = "className";
    public static final String CFG_CLASS_BYTES = "classBytesBase64";
    /** Optional: end-to-end timeout in ms; defaults to 5000. */
    public static final String CFG_TIMEOUT_MS = "timeoutMs";

    private static final long DEFAULT_TIMEOUT_MS = 5000L;
    private static final long DEFAULT_CACHE_TTL_HOURS = 120;

    private final DatasourceRepository datasourceRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ClassLoaderPool classLoaderPool = new ClassLoaderPool();
    private final JavaSourceCompiler compiler = new JavaSourceCompiler();

    @Override
    public String getConnectorType() {
        return CONNECTOR_TYPE;
    }

    @Override
    public Object fetchFieldValue(Datasource datasource, String entityId, String clazz,
                                  String fieldName, Map<String, String> context) {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        try {
            JsonNode config = objectMapper.readTree(datasource.getConfigJson());
            String className = config.path(CFG_CLASS_NAME).asText(null);
            String bytesBase64 = config.path(CFG_CLASS_BYTES).asText(null);

            if (className == null || className.isEmpty() || bytesBase64 == null || bytesBase64.isEmpty()) {
                log.warn("AI_JAVA connector: missing {} or {} in config_json (datasourceId={})",
                    CFG_CLASS_NAME, CFG_CLASS_BYTES, datasource.getId());
                logCall(datasource.getId(), entityId, fieldName, null, null, null, "ERROR",
                    "missing className/classBytesBase64", System.currentTimeMillis() - startTime, requestId);
                return null;
            }

            byte[] classBytes;
            try {
                classBytes = Base64.getDecoder().decode(bytesBase64);
            } catch (IllegalArgumentException e) {
                log.warn("AI_JAVA connector: invalid base64 in classBytesBase64 (datasourceId={}): {}",
                    datasource.getId(), e.getMessage());
                logCall(datasource.getId(), entityId, fieldName, null, null, null, "ERROR",
                    "invalid base64: " + e.getMessage(), System.currentTimeMillis() - startTime, requestId);
                return null;
            }

            // Verify magic bytes — defense against truncated/corrupted payloads
            if (classBytes.length < 4
                || (classBytes[0] & 0xFF) != 0xCA || (classBytes[1] & 0xFF) != 0xFE
                || (classBytes[2] & 0xFF) != 0xBA || (classBytes[3] & 0xFF) != 0xBE) {
                log.warn("AI_JAVA connector: bad magic bytes (datasourceId={}, bytes={})",
                    datasource.getId(), classBytes.length);
                logCall(datasource.getId(), entityId, fieldName, null, null, null, "ERROR",
                    "bad magic bytes", System.currentTimeMillis() - startTime, requestId);
                return null;
            }

            IJavaDataSource instance;
            try {
                instance = classLoaderPool.getOrLoadInstance(datasource.getId(), className, classBytes);
            } catch (Throwable t) {
                log.warn("AI_JAVA connector: cannot load class {} (datasourceId={}): {}",
                    className, datasource.getId(), t.getMessage());
                logCall(datasource.getId(), entityId, fieldName, null, null, null, "ERROR",
                    "class load failed: " + t.getMessage(), System.currentTimeMillis() - startTime, requestId);
                return null;
            }

            Object value = instance.fetchField(entityId, fieldName, context);
            long responseTime = System.currentTimeMillis() - startTime;

            logCall(datasource.getId(), entityId, fieldName,
                "{\"entityId\":\"" + entityId + "\",\"fieldName\":\"" + fieldName + "\"}",
                String.valueOf(value), 200, "SUCCESS", null, responseTime, requestId);

            return value;

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            log.error("AI_JAVA connector: unexpected error (datasourceId={}, field={})",
                datasource.getId(), fieldName, e);
            logCall(datasource.getId(), entityId, fieldName, null, null, null, "ERROR",
                e.getMessage(), responseTime, requestId);
            return null;
        }
    }

    @Override
    public boolean testConnection(Datasource datasource) {
        // "Test connection" for AI_JAVA = verify the stored class can be loaded.
        try {
            JsonNode config = objectMapper.readTree(datasource.getConfigJson());
            String className = config.path(CFG_CLASS_NAME).asText(null);
            String bytesBase64 = config.path(CFG_CLASS_BYTES).asText(null);
            if (className == null || className.isEmpty() || bytesBase64 == null || bytesBase64.isEmpty()) {
                return false;
            }
            byte[] classBytes = Base64.getDecoder().decode(bytesBase64);
            classLoaderPool.getOrLoadInstance(datasource.getId(), className, classBytes);
            return true;
        } catch (Throwable t) {
            log.warn("AI_JAVA testConnection failed: {}", t.getMessage());
            return false;
        }
    }

    /**
     * Drop the cached class loader for a datasource — call when the user updates
     * config_json (recompiles a new .class). Safe to call from anywhere; the pool
     * is thread-safe.
     */
    public void evict(Long datasourceId) {
        if (datasourceId != null) {
            classLoaderPool.close(datasourceId);
        }
    }

    // ===== Audit (mirrors AdvanceAiConnector.logApiCall) =====

    private void logCall(Long datasourceId, String entityId, String apiEndpoint,
                        String requestData, String responseData, Integer httpStatus,
                        String status, String errorMessage, long responseTimeMs, String requestId) {
        try {
            DatasourceLog logEntry = new DatasourceLog();
            logEntry.setUserId(entityId);
            logEntry.setDatasourceId(datasourceId);
            logEntry.setDataSource(CONNECTOR_TYPE);
            logEntry.setApiEndpoint(apiEndpoint);
            logEntry.setRequestMethod("INVOKE");
            logEntry.setRequestData(truncate(requestData, 65535));
            logEntry.setResponseData(truncate(responseData, 65535));
            logEntry.setHttpStatus(httpStatus);
            logEntry.setStatus(status);
            logEntry.setErrorMessage(errorMessage != null ? truncate(errorMessage, 1024) : null);
            logEntry.setResponseTimeMs(responseTimeMs);
            logEntry.setRequestId(requestId);
            datasourceRepository.insertDatasourceLog(logEntry);
        } catch (Exception e) {
            log.error("AI_JAVA connector: failed to record call log", e);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
