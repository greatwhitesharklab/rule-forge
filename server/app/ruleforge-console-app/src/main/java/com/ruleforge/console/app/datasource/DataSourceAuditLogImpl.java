package com.ruleforge.console.app.datasource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.datasource.DataSourceAuditLog;
import com.ruleforge.datasource.Vars;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * V5.23 — Console-app implementation of {@link DataSourceAuditLog}.
 *
 * <p>Writes one row to {@code nd_data_source_call} per call. PII fields ({@code cert_no},
 * {@code mobile}, {@code id_card}) are masked before serialization. A failure to write the
 * audit row is logged at WARN level and never propagated (audit failure must not break
 * the main data source call).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceAuditLogImpl implements DataSourceAuditLog {

    /** PII mask patterns — value replaced with first 4 chars + "****". */
    private static final Pattern PII_KEY = Pattern.compile(
        ".*(cert_no|id_?card|mobile|phone|ssn|身份证|手机号).*",
        Pattern.CASE_INSENSITIVE
    );

    private final DataSourceCallMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public void record(String dataSourceName, Vars inputs, Vars outputs,
                       long durationMs, boolean success, String errorMessage) {
        try {
            DataSourceCallEntity row = new DataSourceCallEntity();
            row.setDataSource(dataSourceName);
            row.setInputs(maskPii(inputs));
            row.setOutputs(maskPii(outputs));
            row.setDurationMs(durationMs);
            row.setSuccess(success);
            row.setErrorMessage(errorMessage == null ? null :
                errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage);
            mapper.insert(row);
        } catch (Exception e) {
            // 审计失败不影响主调用
            log.warn("nd_data_source_call insert failed for ds={}: {}", dataSourceName, e.getMessage());
        }
    }

    /**
     * Mask PII fields in {@link Vars} before serialization. Non-PII fields pass through
     * as JSON; PII field values become {@code "abc1****"}.
     */
    String maskPii(Vars v) {
        if (v == null || v.isEmpty()) return null;
        Vars masked = new Vars();
        for (var key : v.toMap().keySet()) {
            Object value = v.get(key);
            if (PII_KEY.matcher(key).matches() && value instanceof String s && s.length() > 4) {
                masked.put(key, s.substring(0, 4) + "****");
            } else {
                masked.put(key, value);
            }
        }
        try {
            return objectMapper.writeValueAsString(masked.toMap());
        } catch (JsonProcessingException e) {
            return null;  // 不阻塞主调用
        }
    }
}
