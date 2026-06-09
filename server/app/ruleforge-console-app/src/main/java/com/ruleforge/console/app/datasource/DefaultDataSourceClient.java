package com.ruleforge.console.app.datasource;

import com.ruleforge.datasource.DataSourceRegistry;
import com.ruleforge.datasource.Vars;
import com.ruleforge.decision.flow.registry.DataSourceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * V5.23 — Console-side {@link DataSourceClient} impl, delegates to in-process
 * {@link DataSourceRegistry}. Decision engine calls this when a {@code SERVICE_TASK:data_source}
 * node is reached; the registry looks up the named data source and calls its {@code fetch}.
 *
 * <p>Same impl is used in executor-app. Audit logging flows through
 * {@code DataSourceRegistry} → {@code DataSourceAuditLog} (console's writes to
 * app_db, executor's is log-only stub for v0).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultDataSourceClient implements DataSourceClient {

    private final DataSourceRegistry registry;

    @Override
    public Map<String, Object> fetch(String name, Map<String, Object> inputs) throws Exception {
        // 把 inputs 装进 Vars(强类型容器,DS 实现期望这个)
        Vars v = new Vars();
        if (inputs != null) {
            for (Map.Entry<String, Object> e : inputs.entrySet()) {
                v.put(e.getKey(), e.getValue());
            }
        }
        Vars out = registry.fetch(name, v);
        if (out == null) return null;
        // Vars 内部就是 Map<String, Object> — 直接 toMap 拿
        return new HashMap<>(out.toMap());
    }
}
