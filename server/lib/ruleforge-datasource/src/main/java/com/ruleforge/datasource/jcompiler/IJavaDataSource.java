package com.ruleforge.datasource.jcompiler;

import java.util.Map;

/**
 * V5.23 — SPI for LLM-generated (or hand-written) Java data sources.
 *
 * <p>Any class implementing this interface can be compiled at runtime via
 * {@link JavaSourceCompiler}, stored in {@code nd_datasource.config_json} as
 * base64 .class bytes, loaded via {@link ClassLoaderPool}, and invoked by
 * {@code AiJavaDataSourceConnector} on every {@code fetchFieldValue} call.
 *
 * <p>Contract mirrors the {@code DataSourceConnector} per-field protocol so
 * the rule engine's existing call sites work unchanged.
 */
public interface IJavaDataSource {

    /**
     * Logical name for diagnostics (logs / UI); not used for routing.
     * The connector's routing key is the datasource row's {@code name} field.
     */
    String getName();

    /**
     * Optional schema declaration. Free-form; exposed via the connector for
     * UI hints. {@code Map<fieldName, typeString>} where typeString is one of
     * "number" / "string" / "boolean" / "object" (advisory only).
     */
    default Map<String, String> getSchema() {
        return Map.of();
    }

    /**
     * Per-field lookup. Same shape as
     * {@code DataSourceConnector.fetchFieldValue(datasource, entityId, clazz,
     * fieldName, context)} — the connector maps the connector call onto this
     * method.
     *
     * @param entityId  unique entity identifier (e.g. userId)
     * @param fieldName logical field name to fetch
     * @param context   request context (loanZone, orbitCode, custom keys)
     * @return field value (String / Number / Boolean / null). Must be JSON-serializable.
     */
    Object fetchField(String entityId, String fieldName, Map<String, String> context) throws Exception;
}
