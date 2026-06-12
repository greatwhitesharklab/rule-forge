package com.ruleforge.ir.dmn;

import com.ruleforge.model.table.DecisionTable;
import com.ruleforge.model.table.HitPolicy;
import com.ruleforge.model.table.TableDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * V5.40.4 — DmnResourceDispatcher BDD。
 *
 * <p>3 BDD 分 2 组:路径校验 / 基本 dispatch 产出 DecisionTable。
 *
 * <p>Dispatcher 自身**不**调 DecisionTableRulesBuilder(那个依赖 Spring 注入的
 * CellContentBuilder,留给 KnowledgeBuilder 调),所以 V5.40.4 测试只验证
 * "DMN content → DecisionTable" 这一段。
 */
@DisplayName("DmnResourceDispatcher BDD")
class DmnResourceDispatcherTest {

    @Nested
    @DisplayName("Group 1 — 路径校验")
    class PathValidation {

        @Test
        @DisplayName("Given 非 .dmn 路径(如 .xml),When dispatch,Then 抛 IllegalArgumentException")
        void non_dmn_path_rejected() {
            DmnResourceDispatcher dispatcher = new DmnResourceDispatcher();
            try {
                dispatcher.dispatch("rules/decision.xml", "<xml/>");
                fail("Should reject non-.dmn paths");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains(".dmn"),
                    "Error should mention .dmn requirement, got: " + expected.getMessage());
            }
        }

        @Test
        @DisplayName("Given .dmn 路径 + 空 content,When dispatch,Then 抛 IllegalArgumentException")
        void empty_content_rejected() {
            DmnResourceDispatcher dispatcher = new DmnResourceDispatcher();
            try {
                dispatcher.dispatch("rules/decision.dmn", "");
                fail("Should reject empty content");
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
    }

    @Nested
    @DisplayName("Group 2 — 基本 dispatch:DMN → DecisionTable")
    class BasicDispatch {

        @Test
        @DisplayName("Given .dmn fixture (CustomerTier),When dispatch,Then table.dialect=DMN, hitPolicy=FIRST, variableName=Customer Tier, 3 columns, 3 rows")
        void dispatch_produces_correct_decision_table() {
            DecisionTable table =
                new DmnResourceDispatcher().dispatch(
                    "rules/customer-tier.dmn",
                    readResource("/ir/fixtures/simple-table.dmn"));

            assertNotNull(table);
            assertSame(TableDialect.DMN, table.getDialect());
            assertSame(HitPolicy.FIRST, table.getHitPolicy());
            assertEquals("Customer Tier", table.getVariableName());
            assertEquals(3, table.getColumns().size());
            assertEquals(3, table.getRows().size());
        }
    }

    private static String readResource(String resourcePath) {
        try (InputStream in = DmnResourceDispatcherTest.class.getResourceAsStream(resourcePath);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Failed to read " + resourcePath + ": " + e.getMessage(), e);
        }
    }
}
