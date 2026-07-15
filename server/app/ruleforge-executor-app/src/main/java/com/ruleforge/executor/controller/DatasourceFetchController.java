package com.ruleforge.executor.controller;

import com.ruleforge.decision.lazy.DatasourceRoutingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * V7.23 — 从 executor TestController 拆出的数据源批量拉取端点。
 *
 * <p>老 TestController 的 {@code /test/do}(老 RETE 单测)和 {@code /test/knowledge}(缓存失效)
 * 在 V7.23 删除(死代码,全仓无调用方)。本控制器只保留 {@code POST /test/datasource/fetch}
 * — BatchTest DATASOURCE 模式(console-app {@code ExecutorDatasourceClient})在用,不走规则引擎。
 *
 * <p>路由路径保持 {@code /test/datasource/fetch} 不变,避免改 console-app 调用方。
 */
@Slf4j
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class DatasourceFetchController {

    private final DatasourceRoutingProvider datasourceRoutingProvider;

    /**
     * V5.8.0: 数据源批量拉取(给 console-app BatchTest V5.8.0 FLOW+DATASOURCE 模式用)
     *
     * POST /test/datasource/fetch
     * body: {
     *   datasourceId: Long,
     *   clazz: String,
     *   entityIds: [String, ...],
     *   fieldNames: [String, ...]
     * }
     * returns: { rows: { entityId: { fieldName: value, ... }, ... }, count: int }
     *
     * 不走 ${ruleforge.root.path} 前缀 — 这个端点是 executor 内部给 console 调的,
     * 对外不暴露给最终用户。
     */
    @PostMapping("/datasource/fetch")
    public Map<String, Object> fetchDatasource(@RequestBody Map<String, Object> req) {
        Long datasourceId = ((Number) req.get("datasourceId")).longValue();
        String clazz = (String) req.get("clazz");
        @SuppressWarnings("unchecked")
        List<String> entityIds = (List<String>) req.get("entityIds");
        @SuppressWarnings("unchecked")
        List<String> fieldNames = (List<String>) req.get("fieldNames");

        Map<String, Map<String, Object>> result = datasourceRoutingProvider.fetchFieldsForEntities(
                datasourceId, clazz, entityIds, fieldNames);

        return Map.of("rows", result, "count", result.size());
    }
}
