package com.ruleforge.console.app.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 8: MySQL → ClickHouse 历史数据回填工具.
 *
 * <p>用法: {@code java -jar ruleforge-console-app.jar --spring.profiles.active=backfill}
 *
 * <p>按 id 批量读 MySQL {@code nd_decision_flow_log} (在 app_db),写入 ClickHouse。
 * ClickHouse 用 {@code ReplacingMergeTree} 按 id 去重,重跑安全。
 *
 * <p><b>架构说明(为何 raw JDBC,不用 mapper):</b>
 * <ul>
 *   <li>{@code DecisionFlowLog} entity 在 executor-app,console-app 不应跨模块借实体</li>
 *   <li>这是<b>一次性批处理</b>工具,MyBatis-Plus 抽象无价值,raw JDBC 更直白</li>
 *   <li>自包含 = 改 schema 只动这一个文件,不动共享 entity</li>
 * </ul>
 *
 * <p><b>范围:</b>只 backfill {@code nd_decision_flow_log}。
 *  {@code nd_decision_rule_log} 当前不 backfill(需要先评估数据量 + 重新设计分页策略)。
 */
@Slf4j
@Component
@Profile("backfill")
@RequiredArgsConstructor
public class ClickHouseBackfillRunner implements CommandLineRunner {

    private static final int BATCH_SIZE = 1000;

    // nd_decision_flow_log 在 app_db(V5.16 migration-app 创建),不是 ruleforge_db
    private final DataSource appDataSource;
    // ClickHouse 数据源(DataSourceConfig.@Bean("clickhouseDataSource"))
    @Qualifier("clickhouseDataSource")
    private final DataSource clickhouseDataSource;

    @Override
    public void run(String... args) {
        log.info("=== ClickHouse backfill start ===");

        // V5.16+ 防御:V5.16 创建的 MySQL nd_decision_flow_log schema 是简版
        // (15 列: project_id / package_id / status / exec_ms / ...),
        // 而本 runner 假设的是 24 列的富版(包含 rule_package_path /
        // total_matched_rules 等性能分析字段)。
        // 当前 V5.16 schema 不满足 backfill 前提,启动时主动 fail-fast,
        // operator 看到明确日志不会浪费时间在 SQL 调试上。
        // 真正能 backfill 要等 nd_decision_flow_log 富化(SLA P3)。
        if (!checkMysqlSchemaCompatible()) {
            log.error("=== ClickHouse backfill ABORTED: MySQL nd_decision_flow_log schema 不匹配 ===");
            log.error("本 runner 假设 24 列富版 schema(rule_package_path / order_no / total_matched_rules 等),");
            log.error("实际 V5.16 schema 是 15 列简版(project_id / package_id / status / exec_ms 等)。");
            log.error("两套 schema 字段名/数量都对不上,本 runner 无法做有意义的数据搬运。");
            log.error("解决方案: 等 nd_decision_flow_log 富化(预计 V5.18+),或单独写一个 schema mapping runner。");
            log.error("当前 CH analytics 数据由 executor-app 写入时双写产生(DecisionLogServiceImpl),不走 backfill。");
            return;
        }

        long totalFlow = 0;
        long lastId = 0;

        try (Connection ch = clickhouseDataSource.getConnection();
             PreparedStatement insertPs = ch.prepareStatement(FLOW_LOG_INSERT_SQL)) {

            while (true) {
                List<FlowLogRow> batch = readFlowLogBatch(lastId);
                if (batch.isEmpty()) {
                    break;
                }
                // addBatch + executeBatch — 一个 page (1000 行) 一次 round-trip,
                // 比逐行 executeUpdate() 快 ~100x。ClickHouse JDBC 驱动把整 batch
                // 打成单个 native protocol packet。
                for (FlowLogRow row : batch) {
                    try {
                        bindFlowLog(insertPs, row);
                        insertPs.addBatch();
                    } catch (Exception e) {
                        log.warn("Bind flow_log failed (skipping): id={}: {}", row.id, e.getMessage());
                    }
                }
                int flowCount;
                try {
                    int[] results = insertPs.executeBatch();
                    flowCount = results.length;
                } catch (java.sql.BatchUpdateException e) {
                    // 部分行失败:clickhouse-jdbc 报 BatchUpdateException,继续往后跑
                    // (ReplacingMergeTree 天然幂等,失败的行下次重跑会自动补)
                    log.warn("Backfill batch partial failure (will retry on next run): {}", e.getMessage());
                    flowCount = 0;
                }
                insertPs.clearBatch();
                totalFlow += flowCount;
                lastId = batch.get(batch.size() - 1).id;
                log.info("Backfill progress: synced {} flow logs (lastId={})", totalFlow, lastId);
            }
            log.info("=== ClickHouse backfill done: {} flow logs ===", totalFlow);
        } catch (Exception e) {
            log.error("Backfill failed", e);
        }
    }

    private List<FlowLogRow> readFlowLogBatch(long lastId) throws Exception {
        List<FlowLogRow> result = new ArrayList<>();
        try (Connection conn = appDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, user_id, order_no, flow_id, flow_version, " +
                     "  rule_package_path, rule_package_version, execution_status, " +
                     "  reject_reason, reject_code, node_names, " +
                     "  execution_time_ms, total_time_ms, load_knowledge_time_ms, flow_execution_time_ms, " +
                     "  total_matched_rules, total_fired_rules, total_loaded_fields, " +
                     "  error_message, error_stack_trace, " +
                     "  is_gray, gray_strategy_id, gray_git_tag, created_at " +
                     "FROM nd_decision_flow_log WHERE id > ? ORDER BY id ASC LIMIT " + BATCH_SIZE)) {
            ps.setLong(1, lastId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new FlowLogRow(
                            rs.getLong("id"),
                            rs.getString("user_id"),
                            rs.getString("order_no"),
                            rs.getString("flow_id"),
                            rs.getString("flow_version"),
                            rs.getString("rule_package_path"),
                            rs.getString("rule_package_version"),
                            rs.getString("execution_status"),
                            rs.getString("reject_reason"),
                            rs.getString("reject_code"),
                            rs.getString("node_names"),
                            rs.getObject("execution_time_ms", Long.class),
                            rs.getObject("total_time_ms", Long.class),
                            rs.getObject("load_knowledge_time_ms", Long.class),
                            rs.getObject("flow_execution_time_ms", Long.class),
                            rs.getObject("total_matched_rules", Integer.class),
                            rs.getObject("total_fired_rules", Integer.class),
                            rs.getObject("total_loaded_fields", Integer.class),
                            rs.getString("error_message"),
                            rs.getString("error_stack_trace"),
                            rs.getBoolean("is_gray"),
                            rs.getObject("gray_strategy_id", Long.class),
                            rs.getString("gray_git_tag"),
                            rs.getTimestamp("created_at")
                    ));
                }
            }
        }
        return result;
    }

    private void bindFlowLog(PreparedStatement ps, FlowLogRow r) throws Exception {
        ps.setLong(1, r.id);
        ps.setString(2, r.userId);
        ps.setString(3, r.orderNo);
        ps.setString(4, r.flowId);
        ps.setString(5, r.flowVersion);
        ps.setString(6, r.rulePackagePath);
        ps.setString(7, r.rulePackageVersion);
        ps.setString(8, r.executionStatus);
        ps.setString(9, r.rejectReason);
        ps.setString(10, r.rejectCode);
        ps.setString(11, r.nodeNames);
        setNullableLong(ps, 12, r.executionTimeMs);
        setNullableLong(ps, 13, r.totalTimeMs);
        setNullableLong(ps, 14, r.loadKnowledgeTimeMs);
        setNullableLong(ps, 15, r.flowExecutionTimeMs);
        setNullableInt(ps, 16, r.totalMatchedRules);
        setNullableInt(ps, 17, r.totalFiredRules);
        setNullableInt(ps, 18, r.totalLoadedFields);
        ps.setString(19, r.errorMessage);
        ps.setString(20, r.errorStackTrace);
        ps.setBoolean(21, r.isGray);
        setNullableLong(ps, 22, r.grayStrategyId);
        ps.setString(23, r.grayGitTag);
        ps.setTimestamp(24, r.createdAt);
    }

    private static void setNullableLong(PreparedStatement ps, int idx, Long value) throws Exception {
        if (value == null) {
            ps.setNull(idx, java.sql.Types.BIGINT);
        } else {
            ps.setLong(idx, value);
        }
    }

    private static void setNullableInt(PreparedStatement ps, int idx, Integer value) throws Exception {
        if (value == null) {
            ps.setNull(idx, java.sql.Types.INTEGER);
        } else {
            ps.setInt(idx, value);
        }
    }

    /**
     * 启动时校验 MySQL nd_decision_flow_log schema。
     * 期望:含 "rule_package_path" "total_matched_rules" "execution_time_ms" 等 24 列富版字段。
     * V5.16 简版 schema 不含这些字段,直接 false。
     */
    private boolean checkMysqlSchemaCompatible() {
        // 至少查 3 个标志性字段,够用就行
        String probe = "SELECT rule_package_path, total_matched_rules, execution_time_ms " +
                "FROM nd_decision_flow_log LIMIT 0";
        try (Connection conn = appDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(probe)) {
            ps.executeQuery();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * ClickHouse INSERT — 24 列必须和 CH DDL ({@code scripts/clickhouse-init.sql}) 完全一致。
     * 改 schema 时同时改这里。
     */
    private static final String FLOW_LOG_INSERT_SQL =
            "INSERT INTO nd_decision_flow_log " +
                    "(id, user_id, order_no, flow_id, flow_version, rule_package_path, rule_package_version, " +
                    " execution_status, reject_reason, reject_code, node_names, " +
                    " execution_time_ms, total_time_ms, load_knowledge_time_ms, flow_execution_time_ms, " +
                    " total_matched_rules, total_fired_rules, total_loaded_fields, " +
                    " error_message, error_stack_trace, " +
                    " is_gray, gray_strategy_id, gray_git_tag, created_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    /**
     * 本地 DTO — 镜像 {@code nd_decision_flow_log} 行的子集。
     * 不复用 executor-app 的 {@code DecisionFlowLog} entity:跨模块借实体 = 隐式契约。
     * schema 变了改这里 + {@link #FLOW_LOG_INSERT_SQL} 两处即可。
     */
    private record FlowLogRow(
            long id,
            String userId,
            String orderNo,
            String flowId,
            String flowVersion,
            String rulePackagePath,
            String rulePackageVersion,
            String executionStatus,
            String rejectReason,
            String rejectCode,
            String nodeNames,
            Long executionTimeMs,
            Long totalTimeMs,
            Long loadKnowledgeTimeMs,
            Long flowExecutionTimeMs,
            Integer totalMatchedRules,
            Integer totalFiredRules,
            Integer totalLoadedFields,
            String errorMessage,
            String errorStackTrace,
            boolean isGray,
            Long grayStrategyId,
            String grayGitTag,
            Timestamp createdAt
    ) {
    }
}
