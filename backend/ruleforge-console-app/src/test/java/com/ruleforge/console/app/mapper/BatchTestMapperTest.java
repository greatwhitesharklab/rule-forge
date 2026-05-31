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
 * BatchTestSessionMapper + BatchTestRowMapper SQL 集成测试
 *
 * 使用 H2 内存数据库（MySQL 兼容模式）验证批量测试 CRUD 操作。
 * 使用 JDBC 进行数据准备（INSERT），通过 MyBatis Mapper 测试自定义 @Select/@Update 方法。
 */
public class BatchTestMapperTest {

    private static SqlSessionFactory sqlSessionFactory;
    private static JdbcConnectionPool dataSource;

    @BeforeAll
    static void setUp() throws Exception {
        dataSource = JdbcConnectionPool.create(
                "jdbc:h2:mem:batch_test;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");

        try (Connection conn = dataSource.getConnection()) {
            Statement stmt = conn.createStatement();
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS nd_batch_test_session (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    project VARCHAR(100) NOT NULL,
                    package_id VARCHAR(100) NOT NULL,
                    files TEXT,
                    flow_id VARCHAR(255),
                    status VARCHAR(20) DEFAULT 'UPLOADED',
                    total_rows INT DEFAULT 0,
                    error_count INT DEFAULT 0,
                    progress DOUBLE DEFAULT 0,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS nd_batch_test_row (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    session_id BIGINT NOT NULL,
                    row_index INT NOT NULL,
                    input_data TEXT NOT NULL,
                    output_data TEXT,
                    error_message VARCHAR(500),
                    status VARCHAR(20) DEFAULT 'PENDING'
                )
            """);
            conn.commit();
        }

        UnpooledDataSource mybatisDs = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:batch_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), mybatisDs);
        Configuration config = new Configuration(environment);
        config.addMapper(BatchTestSessionMapper.class);
        config.addMapper(BatchTestRowMapper.class);
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
            stmt.execute("TRUNCATE TABLE nd_batch_test_row");
            stmt.execute("TRUNCATE TABLE nd_batch_test_session");
            conn.commit();
        }
    }

    /** Helper: 插入 session，返回自增 ID */
    private long insertSession(String project, String packageId, String files, String flowId,
                               String status, int totalRows, int errorCount, double progress) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO nd_batch_test_session (project, package_id, files, flow_id, status, total_rows, error_count, progress) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, project);
            ps.setString(2, packageId);
            ps.setString(3, files);
            ps.setString(4, flowId);
            ps.setString(5, status);
            ps.setInt(6, totalRows);
            ps.setInt(7, errorCount);
            ps.setDouble(8, progress);
            ps.executeUpdate();
            var rs = ps.getGeneratedKeys();
            rs.next();
            long id = rs.getLong(1);
            conn.commit();
            return id;
        }
    }

    /** Helper: 插入 row，返回自增 ID */
    private long insertRow(long sessionId, int rowIndex, String inputData, String status) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO nd_batch_test_row (session_id, row_index, input_data, status) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, sessionId);
            ps.setInt(2, rowIndex);
            ps.setString(3, inputData);
            ps.setString(4, status);
            ps.executeUpdate();
            var rs = ps.getGeneratedKeys();
            rs.next();
            long id = rs.getLong(1);
            conn.commit();
            return id;
        }
    }

    // ========== Scenario: 批量测试会话 CRUD ==========

    @Nested
    @DisplayName("Scenario: 批量测试会话 CRUD")
    class SessionCRUD {

        @Test
        @DisplayName("插入会话后查询，默认 status=UPLOADED")
        void shouldCreateAndRetrieveSession() throws Exception {
            long id = insertSession("luzcred", "withdrawal", "luzcred/withdrawal,1",
                    "withdrawal-flow", "UPLOADED", 0, 0, 0.0);

            try (SqlSession session = sqlSessionFactory.openSession()) {
                BatchTestSessionMapper mapper = session.getMapper(BatchTestSessionMapper.class);
                Map<String, Object> loaded = mapper.selectMapById(id);

                assertThat(loaded).isNotNull();
                assertThat(ciStr(loaded, "project")).isEqualTo("luzcred");
                assertThat(ciStr(loaded, "package_id")).isEqualTo("withdrawal");
                assertThat(ciStr(loaded, "status")).isEqualTo("UPLOADED");
                assertThat(ciLong(loaded, "total_rows")).isEqualTo(0);
            }
        }

        @Test
        @DisplayName("更新会话状态为 RUNNING → COMPLETED")
        void shouldUpdateSessionStatus() throws Exception {
            long id = insertSession("luzcred", "withdrawal", null, null, "UPLOADED", 0, 0, 0.0);

            try (SqlSession session = sqlSessionFactory.openSession(true)) {
                BatchTestSessionMapper mapper = session.getMapper(BatchTestSessionMapper.class);

                // 更新为 RUNNING
                int updated = mapper.updateStatus(id, "RUNNING", 0.5, 0);
                assertThat(updated).isEqualTo(1);

                Map<String, Object> running = mapper.selectMapById(id);
                assertThat(ciStr(running, "status")).isEqualTo("RUNNING");
                assertThat(ciDouble(running, "progress")).isEqualTo(0.5);

                // 更新为 COMPLETED
                mapper.updateStatus(id, "COMPLETED", 1.0, 2);
                Map<String, Object> completed = mapper.selectMapById(id);
                assertThat(ciStr(completed, "status")).isEqualTo("COMPLETED");
                assertThat(ciDouble(completed, "progress")).isEqualTo(1.0);
                assertThat(ciLong(completed, "error_count")).isEqualTo(2);
            }
        }
    }

    // ========== Scenario: 批量测试行批量插入 ==========

    @Nested
    @DisplayName("Scenario: 批量测试行批量插入")
    class BatchInsertRows {

        @Test
        @DisplayName("批量插入 5 行并查询验证")
        void shouldBatchInsertRows() throws Exception {
            long sessionId = insertSession("luzcred", "withdrawal", null, null, "UPLOADED", 5, 0, 0.0);

            for (int i = 1; i <= 5; i++) {
                insertRow(sessionId, i, "{\"客户信息\":{\"name\":\"user" + i + "\"}}", "PENDING");
            }

            try (SqlSession session = sqlSessionFactory.openSession()) {
                BatchTestRowMapper mapper = session.getMapper(BatchTestRowMapper.class);

                // 按 session_id 查询
                List<Map<String, Object>> rows = mapper.selectBySessionId(sessionId);

                assertThat(rows).hasSize(5);
                assertThat(ciLong(rows.get(0), "row_index")).isEqualTo(1);
                assertThat(ciLong(rows.get(4), "row_index")).isEqualTo(5);
                assertThat(ciStr(rows.get(0), "status")).isEqualTo("PENDING");
            }
        }
    }

    // ========== Scenario: 批量测试行更新结果 ==========

    @Nested
    @DisplayName("Scenario: 批量测试行更新结果")
    class UpdateRowResult {

        @Test
        @DisplayName("更新第 2 行为 SUCCESS，其他行保持 PENDING")
        void shouldUpdateRowResult() throws Exception {
            long sessionId = insertSession("luzcred", "withdrawal", null, null, "UPLOADED", 3, 0, 0.0);

            long row1 = insertRow(sessionId, 1, "{\"data\":\"row1\"}", "PENDING");
            long row2 = insertRow(sessionId, 2, "{\"data\":\"row2\"}", "PENDING");
            long row3 = insertRow(sessionId, 3, "{\"data\":\"row3\"}", "PENDING");

            try (SqlSession session = sqlSessionFactory.openSession(true)) {
                BatchTestRowMapper mapper = session.getMapper(BatchTestRowMapper.class);

                // 更新第 2 行
                mapper.updateResult(row2, "SUCCESS", "{\"结果\":\"通过\"}", null);

                // 验证第 2 行
                Map<String, Object> updated = mapper.selectMapById(row2);
                assertThat(ciStr(updated, "status")).isEqualTo("SUCCESS");
                assertThat(ciStr(updated, "output_data")).isEqualTo("{\"结果\":\"通过\"}");

                // 验证第 1、3 行未变
                Map<String, Object> first = mapper.selectMapById(row1);
                assertThat(ciStr(first, "status")).isEqualTo("PENDING");
                Map<String, Object> third = mapper.selectMapById(row3);
                assertThat(ciStr(third, "status")).isEqualTo("PENDING");
            }
        }
    }

    // ========== Scenario: 批量测试行错误记录 ==========

    @Nested
    @DisplayName("Scenario: 批量测试行错误记录")
    class RowError {

        @Test
        @DisplayName("记录运行时错误信息")
        void shouldRecordRowError() throws Exception {
            long sessionId = insertSession("luzcred", "withdrawal", null, null, "UPLOADED", 1, 0, 0.0);
            long rowId = insertRow(sessionId, 1, "{\"data\":\"bad\"}", "PENDING");

            try (SqlSession session = sqlSessionFactory.openSession(true)) {
                BatchTestRowMapper mapper = session.getMapper(BatchTestRowMapper.class);

                mapper.updateResult(rowId, "ERROR", null, "类型转换失败");

                Map<String, Object> loaded = mapper.selectMapById(rowId);
                assertThat(ciStr(loaded, "status")).isEqualTo("ERROR");
                assertThat(ciStr(loaded, "error_message")).isEqualTo("类型转换失败");
                assertThat(ciStr(loaded, "output_data")).isNull();
            }
        }
    }

    // ========== Scenario: 查询会话进度 ==========

    @Nested
    @DisplayName("Scenario: 查询会话进度")
    class SessionProgress {

        @Test
        @DisplayName("按状态统计行数：7 SUCCESS + 2 ERROR + 1 PENDING")
        void shouldQuerySessionProgress() throws Exception {
            long sessionId = insertSession("luzcred", "withdrawal", null, null, "UPLOADED", 10, 0, 0.0);

            for (int i = 1; i <= 7; i++) {
                insertRow(sessionId, i, "{\"r\":" + i + "}", "SUCCESS");
            }
            for (int i = 8; i <= 9; i++) {
                insertRow(sessionId, i, "{\"r\":" + i + "}", "ERROR");
            }
            insertRow(sessionId, 10, "{\"r\":10}", "PENDING");

            try (SqlSession session = sqlSessionFactory.openSession()) {
                BatchTestRowMapper mapper = session.getMapper(BatchTestRowMapper.class);

                List<Map<String, Object>> stats = mapper.countByStatus(sessionId);
                assertThat(stats).hasSize(3);

                int successCount = 0, errorCount = 0, pendingCount = 0;
                for (Map<String, Object> stat : stats) {
                    String status = ciStr(stat, "status");
                    long cnt = ciLong(stat, "cnt");
                    switch (status) {
                        case "SUCCESS" -> successCount = (int) cnt;
                        case "ERROR" -> errorCount = (int) cnt;
                        case "PENDING" -> pendingCount = (int) cnt;
                    }
                }
                assertThat(successCount).isEqualTo(7);
                assertThat(errorCount).isEqualTo(2);
                assertThat(pendingCount).isEqualTo(1);
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

    private static double ciDouble(Map<String, Object> map, String key) {
        Object v = ci(map, key);
        return v != null ? ((Number) v).doubleValue() : 0.0;
    }

    private static String ciStr(Map<String, Object> map, String key) {
        Object v = ci(map, key);
        return v != null ? v.toString() : null;
    }
}
