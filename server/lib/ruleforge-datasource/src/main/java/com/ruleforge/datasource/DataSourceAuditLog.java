package com.ruleforge.datasource;

/**
 * V5.23 — Audit log interface for data source calls.
 *
 * <p>The lib defines the contract; each app implements it against its own audit table
 * (e.g. {@code nd_data_source_call} in {@code app_db} for console-app/executor-app).
 *
 * <h2>Why interface, not a class</h2>
 * <p>The lib does NOT connect to any database (see CLAUDE.md module boundary). The
 * implementation must be supplied by the calling app via Spring DI.
 *
 * <h2>Performance</h2>
 * <p>Implementations should NOT throw — a failed audit write must not break the data source
 * call. Use try/catch + log.warn internally.
 *
 * <h2>PII</h2>
 * <p>Implementations should mask sensitive input fields (e.g. {@code cert_no},
 * {@code mobile}) before writing. The lib provides {@code inputs} and {@code outputs}
 * as {@link Vars} but cannot enforce masking — that's each app's responsibility.
 */
public interface DataSourceAuditLog {

    /**
     * @param dataSourceName the {@link BaseApiDataSource#getName()} of the called DS
     * @param inputs the input variables (consider masking PII)
     * @param outputs the output variables (consider masking PII)
     * @param durationMs how long the call took (including framework interceptors)
     * @param success whether the call returned successfully
     * @param errorMessage null if successful; error description if not
     */
    void record(String dataSourceName, Vars inputs, Vars outputs,
                long durationMs, boolean success, String errorMessage);
}
