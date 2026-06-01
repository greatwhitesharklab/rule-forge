package com.ruleforge.decision.lazy;

import com.ruleforge.decision.connector.DataSourceConnector;
import com.ruleforge.decision.entity.Datasource;
import com.ruleforge.decision.service.IDatasourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源路由 Provider
 * 实现 DataSourceProvider 接口，按 clazz 路由到不同的数据源连接器
 * 替代原来的单一 RestDataSourceProvider
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceRoutingProvider implements DataSourceProvider {

    private final IDatasourceService datasourceService;
    private final List<DataSourceConnector> connectors;

    // clazz → (Datasource + Connector) 缓存
    private final ConcurrentHashMap<String, ResolvedRoute> routeCache = new ConcurrentHashMap<>();

    // 轻量 TTL 缓存（位于 connector 的 DB 缓存之上）
    private final ConcurrentHashMap<CacheKey, CacheEntry> fieldValueCache = new ConcurrentHashMap<>();

    @Override
    public Object fetchFieldValue(String entityId, String clazz, String fieldName) {
        try {
            // 1. 解析路由
            ResolvedRoute route = resolveRoute(clazz);
            if (route == null) {
                log.warn("未找到 clazz={} 的数据源映射，返回 -999", clazz);
                return -999;
            }

            // 2. 字段名映射
            String remoteField = fieldName;
            String mappedField = datasourceService.resolveRemoteField(
                    route.datasource().getId(), clazz, fieldName);
            if (mappedField != null) {
                remoteField = mappedField;
                log.debug("字段映射: {} → {} (clazz={})", fieldName, remoteField, clazz);
            }

            // 3. 检查轻量 TTL 缓存
            if (Boolean.TRUE.equals(route.datasource().getCacheEnabled())) {
                Object cached = getFromCache(entityId, clazz, remoteField);
                if (cached != null) {
                    return cached;
                }
            }

            // 4. 委托 connector 执行
            Map<String, String> context = buildContext();
            Object value = route.connector().fetchFieldValue(
                    route.datasource(), entityId, clazz, remoteField, context);

            // 5. 写入轻量缓存
            if (Boolean.TRUE.equals(route.datasource().getCacheEnabled()) && value != null) {
                putToCache(entityId, clazz, remoteField, value, route.datasource());
            }

            return value;

        } catch (Exception e) {
            log.error("路由获取字段值失败: clazz={}, fieldName={}", clazz, fieldName, e);
            return -999;
        }
    }

    // ===== 路由解析 =====

    private ResolvedRoute resolveRoute(String clazz) {
        return routeCache.computeIfAbsent(clazz, this::doResolveRoute);
    }

    private ResolvedRoute doResolveRoute(String clazz) {
        Datasource ds = datasourceService.resolveDatasource(clazz);
        if (ds == null) {
            return null;
        }
        DataSourceConnector connector = resolveConnector(ds.getType());
        if (connector == null) {
            log.error("未找到 type={} 的连接器", ds.getType());
            return null;
        }
        return new ResolvedRoute(ds, connector);
    }

    private DataSourceConnector resolveConnector(String type) {
        if (connectors == null) return null;
        return connectors.stream()
                .filter(c -> c.getConnectorType().equals(type))
                .findFirst()
                .orElse(null);
    }

    private Map<String, String> buildContext() {
        Map<String, String> context = new HashMap<>();
        DecisionContext ctx = DecisionContext.current();
        if (ctx != null) {
            context.put("loanZone", ctx.getLoanZone());
            context.put("orbitCode", ctx.getOrbitCode());
        }
        return context;
    }

    // ===== 轻量 TTL 缓存 =====

    private Object getFromCache(String entityId, String clazz, String fieldName) {
        CacheKey key = new CacheKey(entityId, clazz, fieldName);
        CacheEntry entry = fieldValueCache.get(key);
        if (entry != null) {
            if (System.currentTimeMillis() < entry.expireAt) {
                return entry.value;
            }
            fieldValueCache.remove(key);
        }
        return null;
    }

    private void putToCache(String entityId, String clazz, String fieldName,
                            Object value, Datasource ds) {
        int ttlSeconds = (ds.getCacheTtlHours() != null ? ds.getCacheTtlHours() : 120) * 3600;
        // 只缓存 ttl 的前 80%，因为 connector 层也有 DB 缓存
        long expireAt = System.currentTimeMillis() + (long)(ttlSeconds * 0.8) * 1000;
        CacheKey key = new CacheKey(entityId, clazz, fieldName);
        fieldValueCache.put(key, new CacheEntry(value, expireAt));
    }

    /**
     * 清除路由缓存（数据源配置变更时调用）
     */
    public void evictRouteCache() {
        routeCache.clear();
        fieldValueCache.clear();
    }

    // ===== 内部记录 =====

    private record ResolvedRoute(Datasource datasource, DataSourceConnector connector) {}
    private record CacheKey(String entityId, String clazz, String fieldName) {}
    private record CacheEntry(Object value, long expireAt) {}
}
