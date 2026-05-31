package com.ruleforge.console.app.mapper;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcConnectionPool;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DecisionAnalysisMapper + RuleCoverageMapper SQL 集成测试
 *
 * 使用 H2 内存数据库（MySQL 兼容模式）验证聚合 SQL 正确性。
 * 参考 nova_decision 真实数据结构（602 flow logs, 1494 rule logs, 17 rules, 8 packages）。
 *
 * 注意：aggregateFlowLogTimeSeries 使用 MySQL DATE_FORMAT，H2 不支持，
 * 该方法在 AnalysisServiceImplTest 中通过 Mock Mapper 已充分测试。
 */
public class DecisionAnalysisMapperTest {

    private static SqlSessionFactory sqlSessionFactory;
    private static JdbcConnectionPool dataSource;

    @BeforeAll
    static void setUp() throws Exception {
        dataSource = JdbcConnectionPool.create(
                "jdbc:h2:mem:analysis_test;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");

        try (Connection conn = dataSource.getConnection()) {
            Statement stmt = conn.createStatement();
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS nd_decision_flow_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    created_at TIMESTAMP NOT NULL,
                    rule_package_path VARCHAR(255),
                    flow_id VARCHAR(255),
                    execution_status VARCHAR(50),
                    reject_code VARCHAR(100),
                    reject_reason VARCHAR(500),
                    total_time_ms DOUBLE,
                    execution_time_ms DOUBLE,
                    load_knowledge_time_ms DOUBLE,
                    is_gray BOOLEAN DEFAULT FALSE
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS nd_decision_rule_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    flow_log_id BIGINT NOT NULL,
                    rule_name VARCHAR(255),
                    rule_type VARCHAR(50),
                    duration_ms DOUBLE
                )
            """);
            conn.commit();
        }

        UnpooledDataSource mybatisDs = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:analysis_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), mybatisDs);
        Configuration config = new Configuration(environment);
        config.addMapper(DecisionAnalysisMapper.class);
        config.addMapper(RuleCoverageMapper.class);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(config);
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) dataSource.dispose();
    }

    @BeforeEach
    void insertTestData() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            Statement stmt = conn.createStatement();
            stmt.execute("TRUNCATE TABLE nd_decision_rule_log");
            stmt.execute("TRUNCATE TABLE nd_decision_flow_log");

            // Day 1: 2026-05-24 — luzcred/withdrawal (5) + luzcred/T4 (2)
            stmt.execute("INSERT INTO nd_decision_flow_log (created_at, rule_package_path, flow_id, execution_status, reject_code, reject_reason, total_time_ms, execution_time_ms, load_knowledge_time_ms) VALUES ('2026-05-24 10:00:00', 'luzcred/withdrawal', 'withdrawal-flow', 'SUCCESS', NULL, NULL, 30.0, 20.0, 5.0)");
            stmt.execute("INSERT INTO nd_decision_flow_log (created_at, rule_package_path, flow_id, execution_status, reject_code, reject_reason, total_time_ms, execution_time_ms, load_knowledge_time_ms) VALUES ('2026-05-24 10:05:00', 'luzcred/withdrawal', 'withdrawal-flow', 'SUCCESS', NULL, NULL, 25.0, 18.0, 3.0)");
            stmt.execute("INSERT INTO nd_decision_flow_log (created_at, rule_package_path, flow_id, execution_status, reject_code, reject_reason, total_time_ms, execution_time_ms, load_knowledge_time_ms) VALUES ('2026-05-24 11:00:00', 'luzcred/withdrawal', 'withdrawal-flow', 'REJECT', 'HIGH_RISK', '高风险客户', 40.0, 30.0, 5.0)");
            stmt.execute("INSERT INTO nd_decision_flow_log (created_at, rule_package_path, flow_id, execution_status, reject_code, reject_reason, total_time_ms, execution_time_ms, load_knowledge_time_ms) VALUES ('2026-05-24 11:30:00', 'luzcred/withdrawal', 'withdrawal-flow', 'REJECT', 'DEBT_RATIO', '负债率过高', 35.0, 25.0, 4.0)");
            stmt.execute("INSERT INTO nd_decision_flow_log (created_at, rule_package_path, flow_id, execution_status, reject_code, reject_reason, total_time_ms, execution_time_ms, load_knowledge_time_ms) VALUES ('2026-05-24 12:00:00', 'luzcred/T4', 't4-flow', 'SUCCESS', NULL, NULL, 50.0, 40.0, 5.0)");
            stmt.execute("INSERT INTO nd_decision_flow_log (created_at, rule_package_path, flow_id, execution_status, reject_code, reject_reason, total_time_ms, execution_time_ms, load_knowledge_time_ms) VALUES ('2026-05-24 12:30:00', 'luzcred/T4', 't4-flow', 'SUCCESS', NULL, NULL, 45.0, 35.0, 5.0)");

            // Day 2: 2026-05-25 (3)
            stmt.execute("INSERT INTO nd_decision_flow_log (created_at, rule_package_path, flow_id, execution_status, reject_code, reject_reason, total_time_ms, execution_time_ms, load_knowledge_time_ms) VALUES ('2026-05-25 10:00:00', 'luzcred/withdrawal', 'withdrawal-flow', 'SUCCESS', NULL, NULL, 28.0, 19.0, 4.0)");
            stmt.execute("INSERT INTO nd_decision_flow_log (created_at, rule_package_path, flow_id, execution_status, reject_code, reject_reason, total_time_ms, execution_time_ms, load_knowledge_time_ms) VALUES ('2026-05-25 14:00:00', 'luzcred/withdrawal', 'withdrawal-flow', 'SUCCESS', NULL, NULL, 32.0, 22.0, 5.0)");
            stmt.execute("INSERT INTO nd_decision_flow_log (created_at, rule_package_path, flow_id, execution_status, reject_code, reject_reason, total_time_ms, execution_time_ms, load_knowledge_time_ms) VALUES ('2026-05-25 15:00:00', 'luzcred/withdrawal', 'withdrawal-flow', 'REJECT', 'HIGH_RISK', '高风险客户', 45.0, 35.0, 5.0)");

            // Day 3: 2026-05-26 (1)
            stmt.execute("INSERT INTO nd_decision_flow_log (created_at, rule_package_path, flow_id, execution_status, reject_code, reject_reason, total_time_ms, execution_time_ms, load_knowledge_time_ms) VALUES ('2026-05-26 09:00:00', 'luzcred/withdrawal', 'withdrawal-flow', 'ERROR', NULL, NULL, 200.0, 180.0, 10.0)");

            // rule_log — 按 flow_log 自增 ID 顺序
            stmt.execute("INSERT INTO nd_decision_rule_log (flow_log_id, rule_name, rule_type, duration_ms) VALUES (1, '新客额度else', '规则集', 5.0)");
            stmt.execute("INSERT INTO nd_decision_rule_log (flow_log_id, rule_name, rule_type, duration_ms) VALUES (1, '免费模型规则else', '规则集', 3.0)");
            stmt.execute("INSERT INTO nd_decision_rule_log (flow_log_id, rule_name, rule_type, duration_ms) VALUES (2, '新客额度else', '规则集', 4.0)");
            stmt.execute("INSERT INTO nd_decision_rule_log (flow_log_id, rule_name, rule_type, duration_ms) VALUES (2, '免费模型规则else', '规则集', 2.0)");
            stmt.execute("INSERT INTO nd_decision_rule_log (flow_log_id, rule_name, rule_type, duration_ms) VALUES (3, 'age_check', '规则集', 8.0)");
            stmt.execute("INSERT INTO nd_decision_rule_log (flow_log_id, rule_name, rule_type, duration_ms) VALUES (5, 'advance_check', '决策表', 6.0)");
            stmt.execute("INSERT INTO nd_decision_rule_log (flow_log_id, rule_name, rule_type, duration_ms) VALUES (8, '新客额度else', '规则集', 5.0)");
            stmt.execute("INSERT INTO nd_decision_rule_log (flow_log_id, rule_name, rule_type, duration_ms) VALUES (10, 'error_handler', '脚本', 10.0)");

            conn.commit();
        }
    }

    /** Case-insensitive map lookup — H2 可能返回大写或小写列名 */
    private static Object ci(Map<String, Object> map, String key) {
        if (map.containsKey(key)) return map.get(key);
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) return e.getValue();
        }
        return null;
    }

    private static long ciLong(Map<String, Object> map, String key) {
        Object v = ci(map, key);
        return v != null ? ((Number) v).longValue() : 0;
    }

    private static double ciDouble(Map<String, Object> map, String key) {
        Object v = ci(map, key);
        return v != null ? ((Number) v).doubleValue() : 0.0;
    }

    private static String ciStr(Map<String, Object> map, String key) {
        Object v = ci(map, key);
        return v != null ? v.toString() : null;
    }

    private Date d(String s) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(s);
    }

    // ========== 包汇总统计 ==========

    @Nested
    @DisplayName("Scenario: 包汇总统计")
    class PackageSummary {

        @Test
        @DisplayName("返回包汇总，按调用量降序")
        void shouldReturnPackageSummary() throws Exception {
            try (SqlSession session = sqlSessionFactory.openSession()) {
                DecisionAnalysisMapper mapper = session.getMapper(DecisionAnalysisMapper.class);

                List<Map<String, Object>> result = mapper.aggregateFlowLogByPackage(
                        d("2026-05-24 00:00:00"), d("2026-05-26 23:59:59"));

                assertThat(result).hasSize(2);
                // withdrawal (8) 排第一，T4 (2) 排第二
                assertThat(ciStr(result.get(0), "rule_package_path")).isEqualTo("luzcred/withdrawal");
                assertThat(ciLong(result.get(0), "total_count")).isEqualTo(8);
                assertThat(ciStr(result.get(1), "rule_package_path")).isEqualTo("luzcred/T4");
                assertThat(ciLong(result.get(1), "total_count")).isEqualTo(2);
            }
        }
    }

    // ========== 拒绝码分布 ==========

    @Nested
    @DisplayName("Scenario: 拒绝码分布")
    class RejectDistribution {

        @Test
        @DisplayName("返回拒绝码 Top-N，按次数降序")
        void shouldReturnTopNRejectCodes() throws Exception {
            try (SqlSession session = sqlSessionFactory.openSession()) {
                DecisionAnalysisMapper mapper = session.getMapper(DecisionAnalysisMapper.class);

                List<Map<String, Object>> result = mapper.aggregateRejectDistribution(
                        d("2026-05-24 00:00:00"), d("2026-05-26 23:59:59"), null, 10);

                assertThat(result).hasSize(2);
                assertThat(ciStr(result.get(0), "reject_code")).isEqualTo("HIGH_RISK");
                assertThat(ciLong(result.get(0), "count")).isEqualTo(2);
                assertThat(ciStr(result.get(1), "reject_code")).isEqualTo("DEBT_RATIO");
                assertThat(ciLong(result.get(1), "count")).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("按包过滤拒绝码 — T4 包无拒绝")
        void shouldFilterRejectByPackage() throws Exception {
            try (SqlSession session = sqlSessionFactory.openSession()) {
                DecisionAnalysisMapper mapper = session.getMapper(DecisionAnalysisMapper.class);

                List<Map<String, Object>> result = mapper.aggregateRejectDistribution(
                        d("2026-05-24 00:00:00"), d("2026-05-24 23:59:59"), "luzcred/T4", 10);

                assertThat(result).isEmpty();
            }
        }
    }

    // ========== 包路径查询 ==========

    @Nested
    @DisplayName("Scenario: 包路径查询")
    class PackagePaths {

        @Test
        @DisplayName("返回所有去重包路径")
        void shouldReturnDistinctPackagePaths() throws Exception {
            try (SqlSession session = sqlSessionFactory.openSession()) {
                DecisionAnalysisMapper mapper = session.getMapper(DecisionAnalysisMapper.class);

                List<String> paths = mapper.findAllPackagePaths();

                assertThat(paths).containsExactlyInAnyOrder("luzcred/withdrawal", "luzcred/T4");
            }
        }
    }

    // ========== 偏差检测统计 ==========

    @Nested
    @DisplayName("Scenario: 偏差检测统计")
    class AnomalyStats {

        @Test
        @DisplayName("当前窗口统计包含正确的均值和总数")
        void shouldComputeCurrentWindowStats() throws Exception {
            try (SqlSession session = sqlSessionFactory.openSession()) {
                DecisionAnalysisMapper mapper = session.getMapper(DecisionAnalysisMapper.class);

                Map<String, Object> stats = mapper.computeCurrentWindowStats(
                        d("2026-05-24 00:00:00"), d("2026-05-26 23:59:59"), null);

                // 全部 10 条 flow logs
                assertThat(ciLong(stats, "total_count")).isEqualTo(10);
                // 3 条 REJECT / 10 = 0.3
                assertThat(ciDouble(stats, "reject_rate")).isBetween(0.2, 0.4);
                // 6 条 SUCCESS / 10 = 0.6
                assertThat(ciDouble(stats, "success_rate")).isBetween(0.5, 0.7);
            }
        }

        @Test
        @DisplayName("历史基线统计计算均值和标准差")
        void shouldComputeAnomalyBaseline() throws Exception {
            try (SqlSession session = sqlSessionFactory.openSession()) {
                DecisionAnalysisMapper mapper = session.getMapper(DecisionAnalysisMapper.class);

                // Baseline: Day1+Day2, Current: Day3+
                Map<String, Object> baseline = mapper.computeAnomalyBaseline(
                        d("2026-05-24 00:00:00"), d("2026-05-26 00:00:00"), null);

                // Day1: 7 条 (5S, 2R), Day2: 3 条 (2S, 1R) — 2 天聚合
                assertThat(ciDouble(baseline, "avg_success_rate")).isBetween(0.5, 0.9);
                assertThat(ci(baseline, "stddev_reject_rate")).isNotNull();
            }
        }
    }

    // ========== 规则覆盖率 ==========

    @Nested
    @DisplayName("Scenario: 规则覆盖率")
    class RuleCoverage {

        @Test
        @DisplayName("规则触发频率排名正确")
        void shouldReturnRuleFireFrequency() throws Exception {
            try (SqlSession session = sqlSessionFactory.openSession()) {
                RuleCoverageMapper mapper = session.getMapper(RuleCoverageMapper.class);

                List<Map<String, Object>> result = mapper.aggregateRuleFireFrequency(
                        d("2026-05-24 00:00:00"), d("2026-05-26 23:59:59"), null);

                assertThat(result).isNotEmpty();
                // 新客额度else: 3 次（flow_log_id 1,2,8）
                assertThat(ciStr(result.get(0), "rule_name")).isEqualTo("新客额度else");
                assertThat(ciLong(result.get(0), "fire_count")).isEqualTo(3);
            }
        }

        @Test
        @DisplayName("按包过滤规则触发")
        void shouldFilterByPackage() throws Exception {
            try (SqlSession session = sqlSessionFactory.openSession()) {
                RuleCoverageMapper mapper = session.getMapper(RuleCoverageMapper.class);

                List<Map<String, Object>> result = mapper.aggregateRuleFireFrequency(
                        d("2026-05-24 00:00:00"), d("2026-05-26 23:59:59"), "luzcred/T4");

                // T4 包只有 flow id=5 (advance_check)
                assertThat(result).hasSize(1);
                assertThat(ciStr(result.get(0), "rule_name")).isEqualTo("advance_check");
            }
        }

        @Test
        @DisplayName("全量曾触发规则名")
        void shouldReturnAllFiredRuleNames() throws Exception {
            try (SqlSession session = sqlSessionFactory.openSession()) {
                RuleCoverageMapper mapper = session.getMapper(RuleCoverageMapper.class);

                List<String> names = mapper.findAllFiredRuleNames();

                assertThat(names).containsExactlyInAnyOrder(
                        "新客额度else", "免费模型规则else", "age_check", "advance_check", "error_handler");
            }
        }
    }
}
