package com.ruleforge.console.app.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.entity.Datasource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JDBC 数据源连接器
 * 通过 SQL 查询获取字段值，内部管理 HikariCP 连接池
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcDataSourceConnector implements DataSourceConnector {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ConcurrentHashMap<Long, HikariDataSource> poolCache = new ConcurrentHashMap<>();

    @Override
    public String getConnectorType() {
        return "JDBC";
    }

    @Override
    public Object fetchFieldValue(Datasource datasource, String entityId, String clazz,
                                  String fieldName, Map<String, String> context) {
        try {
            JsonNode config = objectMapper.readTree(datasource.getConfigJson());
            String queryTemplate = config.path("queryTemplate").asText();

            // 替换占位符
            String sql = queryTemplate
                    .replace("${fieldName}", fieldName)
                    .replace("${entityId}", entityId);

            log.debug("JDBC 查询: datasourceId={}, sql={}", datasource.getId(), sql);

            HikariDataSource ds = getOrCreatePool(datasource.getId(), config);
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Object value = rs.getObject(1);
                        log.debug("JDBC 查询结果: fieldName={}, value={}", fieldName, value);
                        return value;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("JDBC 获取字段值失败: datasourceId={}, fieldName={}", datasource.getId(), fieldName, e);
            return null;
        }
    }

    @Override
    public boolean testConnection(Datasource datasource) {
        try {
            JsonNode config = objectMapper.readTree(datasource.getConfigJson());
            HikariDataSource ds = getOrCreatePool(datasource.getId(), config);
            try (Connection conn = ds.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT 1");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.error("JDBC 连接测试失败: datasourceId={}", datasource.getId(), e);
            return false;
        }
    }

    private HikariDataSource getOrCreatePool(Long datasourceId, JsonNode config) {
        return poolCache.computeIfAbsent(datasourceId, id -> {
            HikariConfig hc = new HikariConfig();
            hc.setJdbcUrl(config.path("url").asText());
            hc.setUsername(config.path("username").asText());
            hc.setPassword(config.path("password").asText());
            hc.setDriverClassName(config.path("driverClass").asText("com.mysql.cj.jdbc.Driver"));
            hc.setMaximumPoolSize(config.path("poolSize").asInt(5));
            hc.setMinimumIdle(1);
            hc.setPoolName("rf-ds-" + id);
            log.info("创建 JDBC 连接池: datasourceId={}, url={}", id, config.path("url").asText());
            return new HikariDataSource(hc);
        });
    }

    /**
     * 清除指定数据源的连接池
     */
    public void evictPool(Long datasourceId) {
        HikariDataSource ds = poolCache.remove(datasourceId);
        if (ds != null && !ds.isClosed()) {
            ds.close();
            log.info("关闭 JDBC 连接池: datasourceId={}", datasourceId);
        }
    }

    @PreDestroy
    public void cleanup() {
        poolCache.forEach((id, ds) -> {
            if (!ds.isClosed()) {
                ds.close();
                log.info("关闭 JDBC 连接池: datasourceId={}", id);
            }
        });
        poolCache.clear();
    }
}
