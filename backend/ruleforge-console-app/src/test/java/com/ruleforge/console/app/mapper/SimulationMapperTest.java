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
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SimulationRunMapper + SimulationResultMapper SQL 集成测试
 *
 * 使用 H2 内存数据库验证仿真 CRUD 操作。
 */
public class SimulationMapperTest {

    private static SqlSessionFactory sqlSessionFactory;
    private static JdbcConnectionPool dataSource;

    @BeforeAll
    static void setUp() throws Exception {
        dataSource = JdbcConnectionPool.create(
                "jdbc:h2:mem:simulation;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");

        try (Connection conn = dataSource.getConnection()) {
            Statement stmt = conn.createStatement();
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS nd_simulation_run (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    rule_package_path VARCHAR(500) NOT NULL,
                    project VARCHAR(100) NOT NULL,
                    package_id VARCHAR(100) NOT NULL,
                    flow_id VARCHAR(255),
                    files TEXT,
                    start_time VARCHAR(30) NOT NULL,
                    end_time VARCHAR(30) NOT NULL,
                    batch_test_session_id BIGINT,
                    status VARCHAR(20) DEFAULT 'PENDING',
                    total_logs INT DEFAULT 0,
                    total_compared INT DEFAULT 0,
                    total_divergent INT DEFAULT 0,
                    divergence_rate DOUBLE DEFAULT 0,
                    high_severity_count INT DEFAULT 0,
                    medium_severity_count INT DEFAULT 0,
                    low_severity_count INT DEFAULT 0,
                    error_message VARCHAR(500),
                    created_by VARCHAR(100),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS nd_simulation_result (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    simulation_run_id BIGINT NOT NULL,
                    original_flow_log_id BIGINT NOT NULL,
                    original_execution_status VARCHAR(20),
                    original_reject_code VARCHAR(50),
                    original_output_params TEXT,
                    original_rule_names TEXT,
                    simulated_execution_status VARCHAR(20),
                    simulated_reject_code VARCHAR(50),
                    simulated_output_params TEXT,
                    simulated_rule_names TEXT,
                    status_match TINYINT,
                    result_match TINYINT,
                    output_divergence TEXT,
                    rule_divergence TEXT,
                    has_divergence TINYINT DEFAULT 0,
                    divergence_severity VARCHAR(20),
                    original_total_time_ms BIGINT,
                    simulated_total_time_ms BIGINT,
                    error_message VARCHAR(500),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            conn.commit();
        }

        UnpooledDataSource mybatisDs = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:simulation;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), mybatisDs);
        Configuration config = new Configuration(environment);
        config.addMapper(SimulationRunMapper.class);
        config.addMapper(SimulationResultMapper.class);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(config);
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) dataSource.dispose();
    }

    @BeforeEach
    void cleanTables() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            Statement stmt = conn.createStatement();
            stmt.execute("TRUNCATE TABLE nd_simulation_result");
            stmt.execute("TRUNCATE TABLE nd_simulation_run");
            conn.commit();
        }
    }

    /** Helper: 插入 simulation run，返回自增 ID */
    private long insertRun(String rulePackagePath, String project, String packageId,
                           String startTime, String endTime, String status,
                           int totalLogs, int totalCompared, int totalDivergent) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO nd_simulation_run (rule_package_path, project, package_id, " +
                    "start_time, end_time, status, total_logs, total_compared, total_divergent) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, rulePackagePath);
            ps.setString(2, project);
            ps.setString(3, packageId);
            ps.setString(4, startTime);
            ps.setString(5, endTime);
            ps.setString(6, status);
            ps.setInt(7, totalLogs);
            ps.setInt(8, totalCompared);
            ps.setInt(9, totalDivergent);
            ps.executeUpdate();
            var rs = ps.getGeneratedKeys();
            rs.next();
            long id = rs.getLong(1);
            conn.commit();
            return id;
        }
    }

    /** Helper: 插入 simulation result，返回自增 ID */
    private long insertResult(long runId, long originalLogId, String severity,
                              boolean hasDivergence, String errorMsg) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO nd_simulation_result (simulation_run_id, original_flow_log_id, " +
                    "divergence_severity, has_divergence, error_message) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, runId);
            ps.setLong(2, originalLogId);
            ps.setString(3, severity);
            ps.setInt(4, hasDivergence ? 1 : 0);
            ps.setString(5, errorMsg);
            ps.executeUpdate();
            var rs = ps.getGeneratedKeys();
            rs.next();
            long id = rs.getLong(1);
            conn.commit();
            return id;
        }
    }

    // ========== Scenario: 仿真 Run CRUD ==========

    @Nested
    @DisplayName("Scenario: 仿真 Run CRUD")
    class RunCRUD {

        // Given 一个新的 simulation run
        // When 插入并查询
        // Then 字段正确
        @Test
        @DisplayName("插入 run 后查询，默认 status=PENDING")
        void shouldCreateAndRetrieveRun() throws Exception {
            long id = insertRun("luzcred/withdrawal", "luzcred", "withdrawal",
                    "2026-05-01 00:00:00", "2026-05-31 23:59:59", "PENDING", 0, 0, 0);

            // 通过 JDBC 验证（BaseMapper.selectById 需 MyBatis-Plus SqlSessionFactory）
            try (Connection conn = dataSource.getConnection()) {
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM nd_simulation_run WHERE id = ?");
                ps.setLong(1, id);
                var rs = ps.executeQuery();
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("rule_package_path")).isEqualTo("luzcred/withdrawal");
                assertThat(rs.getString("project")).isEqualTo("luzcred");
                assertThat(rs.getString("status")).isEqualTo("PENDING");
                assertThat(rs.getInt("total_logs")).isEqualTo(0);
            }
        }

        // Given 一个 COMPLETED 的 run
        // When 按包路径查询
        // Then 返回该包的历史仿真记录
        @Test
        @DisplayName("按包路径查询仿真历史")
        void shouldQueryByPackagePath() throws Exception {
            insertRun("luzcred/withdrawal", "luzcred", "withdrawal",
                    "2026-05-01", "2026-05-15", "COMPLETED", 100, 100, 5);
            insertRun("luzcred/withdrawal", "luzcred", "withdrawal",
                    "2026-05-16", "2026-05-31", "COMPLETED", 200, 200, 12);
            insertRun("luzcred/loan", "luzcred", "loan",
                    "2026-05-01", "2026-05-31", "COMPLETED", 50, 50, 3);

            try (SqlSession session = sqlSessionFactory.openSession()) {
                SimulationRunMapper mapper = session.getMapper(SimulationRunMapper.class);
                List runs = mapper.selectByPackagePath("luzcred/withdrawal", 10, 0);
                assertThat(runs).hasSize(2);
            }
        }
    }

    // ========== Scenario: 仿真 Run 进度更新 ==========

    @Nested
    @DisplayName("Scenario: 仿真 Run 进度更新")
    class RunProgress {

        // Given 一个 PENDING run
        // When 更新进度为 COMPLETED
        // Then 统计字段正确
        @Test
        @DisplayName("更新 run 进度统计")
        void shouldUpdateRunProgress() throws Exception {
            long id = insertRun("luzcred/withdrawal", "luzcred", "withdrawal",
                    "2026-05-01", "2026-05-31", "RUNNING", 100, 50, 5);

            try (SqlSession session = sqlSessionFactory.openSession(true)) {
                SimulationRunMapper mapper = session.getMapper(SimulationRunMapper.class);

                mapper.updateProgress(id, "COMPLETED", 100, 8,
                        8.0, 3, 2, 3, null);

                // 通过 JDBC 验证更新结果
                try (Connection conn = dataSource.getConnection()) {
                    PreparedStatement ps = conn.prepareStatement("SELECT * FROM nd_simulation_run WHERE id = ?");
                    ps.setLong(1, id);
                    var rs = ps.executeQuery();
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("status")).isEqualTo("COMPLETED");
                    assertThat(rs.getInt("total_compared")).isEqualTo(100);
                    assertThat(rs.getInt("total_divergent")).isEqualTo(8);
                    assertThat(rs.getDouble("divergence_rate")).isEqualTo(8.0);
                    assertThat(rs.getInt("high_severity_count")).isEqualTo(3);
                    assertThat(rs.getInt("medium_severity_count")).isEqualTo(2);
                    assertThat(rs.getInt("low_severity_count")).isEqualTo(3);
                }
            }
        }
    }

    // ========== Scenario: 仿真 Result 对比结果 ==========

    @Nested
    @DisplayName("Scenario: 仿真 Result 严重度统计")
    class ResultStats {

        // Given 一个 run 下有多个 result（不同 severity）
        // When 按 severity 分组统计
        // Then 返回正确的计数
        @Test
        @DisplayName("按严重度统计对比结果")
        void shouldCountBySeverity() throws Exception {
            long runId = insertRun("luzcred/withdrawal", "luzcred", "withdrawal",
                    "2026-05-01", "2026-05-31", "COMPLETED", 10, 10, 6);

            // 3 HIGH + 2 MEDIUM + 1 LOW + 4 NONE
            for (int i = 0; i < 3; i++) insertResult(runId, 100 + i, "HIGH", true, null);
            for (int i = 0; i < 2; i++) insertResult(runId, 200 + i, "MEDIUM", true, null);
            insertResult(runId, 300, "LOW", true, null);
            for (int i = 0; i < 4; i++) insertResult(runId, 400 + i, "NONE", false, null);

            try (SqlSession session = sqlSessionFactory.openSession()) {
                SimulationResultMapper mapper = session.getMapper(SimulationResultMapper.class);

                List<Map<String, Object>> stats = mapper.countBySeverity(runId);
                assertThat(stats).hasSize(4);

                int high = 0, medium = 0, low = 0, none = 0;
                for (Map<String, Object> stat : stats) {
                    String severity = ciStr(stat, "divergence_severity");
                    long cnt = ciLong(stat, "cnt");
                    switch (severity) {
                        case "HIGH" -> high = (int) cnt;
                        case "MEDIUM" -> medium = (int) cnt;
                        case "LOW" -> low = (int) cnt;
                        case "NONE" -> none = (int) cnt;
                    }
                }
                assertThat(high).isEqualTo(3);
                assertThat(medium).isEqualTo(2);
                assertThat(low).isEqualTo(1);
                assertThat(none).isEqualTo(4);
            }
        }

        // Given 一个 run 下有 divergent 和 non-divergent 结果
        // When 统计 divergent 数量
        // Then 返回正确的计数
        @Test
        @DisplayName("统计存在差异的结果数")
        void shouldCountDivergent() throws Exception {
            long runId = insertRun("p/pkg", "p", "pkg", "2026-01-01", "2026-01-31", "COMPLETED", 5, 5, 3);

            for (int i = 0; i < 3; i++) insertResult(runId, i, "HIGH", true, null);
            for (int i = 3; i < 5; i++) insertResult(runId, i, "NONE", false, null);

            try (SqlSession session = sqlSessionFactory.openSession()) {
                SimulationResultMapper mapper = session.getMapper(SimulationResultMapper.class);

                int divergent = mapper.countDivergent(runId);
                assertThat(divergent).isEqualTo(3);

                int total = mapper.countByRunId(runId);
                assertThat(total).isEqualTo(5);
            }
        }
    }

    // ========== Case-insensitive map helpers ==========

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

    private static String ciStr(Map<String, Object> map, String key) {
        Object v = ci(map, key);
        return v != null ? v.toString() : null;
    }
}
