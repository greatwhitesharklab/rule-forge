package com.ruleforge.executor.app.datasource;

import com.ruleforge.datasource.DataSourceAuditLog;
import com.ruleforge.datasource.Vars;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * V5.23 — Executor-app stub audit log.
 *
 * <p>Real impl writes to executor's app_db (separate from console-app's). For v0
 * (Phase 5) we log-only here; the per-app DB write can be added in a follow-up —
 * the interface contract is what matters (lib has no DB deps). See
 * {@code com.ruleforge.console.app.datasource.DataSourceAuditLogImpl} for the
 * real console-side implementation.
 */
@Slf4j
@Component
public class ExecutorDataSourceAuditLog implements DataSourceAuditLog {

    @Override
    public void record(String dataSourceName, Vars inputs, Vars outputs,
                       long durationMs, boolean success, String errorMessage) {
        if (success) {
            log.info("[DS-AUDIT] name={} duration={}ms ok inputs={} outputs={}",
                dataSourceName, durationMs, inputs.size(), outputs.size());
        } else {
            log.warn("[DS-AUDIT] name={} duration={}ms FAIL err={} inputs={}",
                dataSourceName, durationMs, errorMessage, inputs.size());
        }
    }
}
