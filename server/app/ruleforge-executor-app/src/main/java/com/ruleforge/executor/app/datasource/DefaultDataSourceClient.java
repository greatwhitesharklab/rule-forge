package com.ruleforge.executor.app.datasource;

import com.ruleforge.datasource.DataSourceRegistry;
import com.ruleforge.datasource.Vars;
import com.ruleforge.decision.flow.registry.DataSourceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * V5.23 — Executor-side {@link DataSourceClient} impl. Mirrors the console-side
 * class but uses executor's in-process {@link DataSourceRegistry} (populated
 * at startup by {@link DataSourceLoader}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultDataSourceClient implements DataSourceClient {

    private final DataSourceRegistry registry;

    @Override
    public Map<String, Object> fetch(String name, Map<String, Object> inputs) throws Exception {
        Vars v = new Vars();
        if (inputs != null) {
            for (Map.Entry<String, Object> e : inputs.entrySet()) {
                v.put(e.getKey(), e.getValue());
            }
        }
        Vars out = registry.fetch(name, v);
        if (out == null) return null;
        return new HashMap<>(out.toMap());
    }
}
