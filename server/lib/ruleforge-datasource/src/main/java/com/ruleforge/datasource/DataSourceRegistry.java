package com.ruleforge.datasource;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V5.23 — Registry of available {@link BaseApiDataSource} instances, indexed by name.
 *
 * <p>Decision engine calls {@link #fetch(String, Vars)}. The registry looks up the data
 * source by name, applies framework interceptors (rate limit / circuit breaker / retry /
 * cache / audit), and returns the parsed result.
 *
 * <h2>Registration</h2>
 * <p>Two patterns:
 * <ul>
 *   <li>Spring auto-discovery — subclasses annotated with {@code @Component("zm_credit")}
 *       are registered on startup</li>
 *   <li>Programmatic registration via {@link #register(BaseApiDataSource)} — used when a
 *       data source is loaded from a compiled .class at runtime (V5.23 LLM-generated flow)</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * <p>The internal map is a {@link ConcurrentHashMap}. Reads ({@link #fetch}) are lock-free.
 * Writes ({@link #register}, {@link #unregister}) are atomic per name.
 */
public class DataSourceRegistry {

    private final Map<String, BaseApiDataSource> sources = new ConcurrentHashMap<>();
    private final DataSourceAuditLog auditLog;  // nullable; null = no audit

    public DataSourceRegistry() {
        this(null);
    }

    public DataSourceRegistry(DataSourceAuditLog auditLog) {
        this.auditLog = auditLog;
    }

    // ====== registration ======

    /**
     * Register a data source. If a DS with the same name already exists, it is replaced
     * (used for hot-reload of LLM-generated data sources).
     */
    public void register(BaseApiDataSource ds) {
        if (ds == null) throw new IllegalArgumentException("ds is null");
        sources.put(ds.getName(), ds);
    }

    public void unregister(String name) {
        sources.remove(name);
    }

    public boolean contains(String name) {
        return sources.containsKey(name);
    }

    public Set<String> listNames() {
        return Set.copyOf(sources.keySet());
    }

    public BaseApiDataSource get(String name) {
        return sources.get(name);
    }

    // ====== fetch (V5.23 Phase 3: passthrough, framework interceptors added in Phase 4) ======

    /**
     * Look up a data source by name and call {@link BaseApiDataSource#fetch(Vars)}.
     *
     * <p>Phase 3 of V5.23 implements this as a passthrough. Phase 4 will wrap with:
     * <ol>
     *   <li>Rate limiter (Resilience4j)</li>
     *   <li>Circuit breaker (Resilience4j)</li>
     *   <li>Retry (Resilience4j)</li>
     *   <li>Cache (Caffeine)</li>
     *   <li>Audit log (callback to app-supplied {@link DataSourceAuditLog})</li>
     * </ol>
     *
     * @throws IllegalArgumentException if the data source is not registered
     * @throws ApiCallException if the upstream call fails
     */
    public Vars fetch(String name, Vars inputs) {
        BaseApiDataSource ds = sources.get(name);
        if (ds == null) {
            throw new IllegalArgumentException("Data source not registered: " + name);
        }
        long start = System.currentTimeMillis();
        boolean success = false;
        String errorMsg = null;
        Vars result = null;
        try {
            result = ds.fetch(inputs);
            success = true;
            return result;
        } catch (RuntimeException e) {
            errorMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
            throw e;
        } finally {
            if (auditLog != null) {
                try {
                    auditLog.record(name, inputs, result,
                        System.currentTimeMillis() - start, success, errorMsg);
                } catch (RuntimeException ignored) {
                    // 审计失败不影响主调用
                }
            }
        }
    }
}
