package com.ruleforge.decision.flow.registry;

import java.util.Map;

/**
 * V5.23 — Decision-engine-side interface for invoking a data source.
 *
 * <p>The {@code ruleforge-decision} lib does NOT depend on {@code ruleforge-datasource}
 * (per module boundary in CLAUDE.md). Instead, the lib defines the contract
 * ({@link #fetch}); each app (console-app, executor-app) provides an implementation
 * that delegates to its in-process {@code DataSourceRegistry}.
 *
 * <p>Outputs are returned as a {@code Map<String, Object>} so they can be merged
 * back into {@code FlowContext.vars} without a lib-level dependency on
 * {@code Vars} (which lives in the datasource lib).
 */
public interface DataSourceClient {

    /**
     * Fetch a data source by name with the given input variables.
     *
     * @param name the data source's {@code getName()} identifier
     * @param inputs input variables (typically {@code FlowContext.vars} or a subset)
     * @return outputs as a map of variables; null if the data source is unknown
     * @throws Exception on communication / parsing errors; the framework will record
     *                   the failure and decide whether to suspend or fail the flow
     */
    Map<String, Object> fetch(String name, Map<String, Object> inputs) throws Exception;
}
