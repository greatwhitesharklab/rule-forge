package com.ruleforge.dsl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * V5.44.1 — DSLRuleSetBuilder 拆出独立 jar 后的 classpath / 打包快照 BDD。
 *
 * <p>本测试在 ruleforge-dsl 模块的 test scope 跑,锁 2 件事:
 * <ol>
 *   <li>ruleforge-dsl jar 含 DSLRuleSetBuilder + 7 个 dsl/builder/* class</li>
 *   <li>DSLRuleSetBuilder + dsl/builder/* 在测试 classpath 里能找到(本模块的 test scope
 *       包含本模块 main 产物,Class.forName 走 maven 自带 testClasspath 即可)</li>
 * </ol>
 *
 * <p>ruleforge-core 不再含 com.ruleforge.dsl.* 的断言见 DslDeadCodeDeleteTest(在
 * ruleforge-core module 的 test 目录)— V5.43.6 写过 V5.43 删的 5 个 dead code
 * class 消失的快照,V5.44.1 整个 com.ruleforge.dsl.* 整包都搬走,那个快照的
 * 第二个 `keptDslClassesStillExist` block 已被 V5.44.1 取消(它锁的是 V5.43.6
 * 决定**保留**的类,现在保留的方式变了 — 搬到 ruleforge-dsl jar)。
 *
 * @since 5.44
 */
@DisplayName("V5.44.1 — ruleforge-dsl jar 含 DSL 链全部 class")
class DslJarExtractTest {

    /**
     * Locate this module's compiled jar in {@code target/}. The test runs with
     * {@code user.dir} pointing at the module's own dir (e.g.
     * {@code server/lib/ruleforge-dsl}), so we look at {@code ./target/}.
     */
    private File findLocalJar() {
        File moduleDir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        File targetDir = new File(moduleDir, "target");
        if (!targetDir.isDirectory()) {
            fail("target/ 不存在: " + targetDir + " — run `mvn -pl lib/ruleforge-dsl package -DskipTests` first");
        }
        File[] jars = targetDir.listFiles((dir, name) ->
            name.startsWith("ruleforge-dsl-") && name.endsWith(".jar")
                && !name.contains("sources") && !name.contains("javadoc"));
        if (jars == null || jars.length == 0) {
            fail("ruleforge-dsl jar 不在 " + targetDir);
        }
        return jars[0];
    }

    private List<String> classEntriesIn(File jar) throws Exception {
        List<String> classes = new ArrayList<>();
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> entries = jf.entries();
            while (entries.hasMoreElements()) {
                JarEntry e = entries.nextElement();
                String n = e.getName();
                if (n.endsWith(".class") && n.startsWith("com/ruleforge/dsl/")) {
                    classes.add(n);
                }
            }
        }
        return classes;
    }

    // ============================================================
    // === V5.44.1 BDD ===
    // ============================================================

    @Nested
    @DisplayName("Given V5.44.1 拆 jar,When 检查 ruleforge-dsl jar,Then 含 DSLRuleSetBuilder + 7 builder")
    class DslJarContents {

        @Test
        @DisplayName("ruleforge-dsl jar 含 DSLRuleSetBuilder + ANTLR 生成物 + 3 个 ContextBuilder")
        void dslJarHasAllClasses() throws Exception {
            File dslJar = findLocalJar();
            assertNotNull(dslJar, "ruleforge-dsl jar 必须存在");
            List<String> classes = classEntriesIn(dslJar);

            // DSLRuleSetBuilder + ANTLR 生成物
            assertTrue(classes.contains("com/ruleforge/dsl/DSLRuleSetBuilder.class"),
                "ruleforge-dsl jar 应含 DSLRuleSetBuilder,实际:" + classes);
            assertTrue(classes.contains("com/ruleforge/dsl/RuleParserLexer.class"),
                "ruleforge-dsl jar 应含 ANTLR 生成物 RuleParserLexer");
            assertTrue(classes.contains("com/ruleforge/dsl/RuleParserParser.class"),
                "ruleforge-dsl jar 应含 ANTLR 生成物 RuleParserParser");
            assertTrue(classes.contains("com/ruleforge/dsl/BuildRulesVisitor.class"),
                "ruleforge-dsl jar 应含 BuildRulesVisitor");
            assertTrue(classes.contains("com/ruleforge/dsl/ScriptDecisionTableErrorListener.class"),
                "ruleforge-dsl jar 应含 ScriptDecisionTableErrorListener");
            assertTrue(classes.contains("com/ruleforge/dsl/DSLUtils.class"),
                "ruleforge-dsl jar 应含 DSLUtils");
            // dsl/builder/ — 3 个 concrete ContextBuilder + 1 abstract + 1 interface + BuildUtils
            // + NamedConditionBuilder = 7 文件
            assertTrue(classes.contains("com/ruleforge/dsl/builder/ActionContextBuilder.class"),
                "ruleforge-dsl jar 应含 ActionContextBuilder");
            assertTrue(classes.contains("com/ruleforge/dsl/builder/CriteriaContextBuilder.class"),
                "ruleforge-dsl jar 应含 CriteriaContextBuilder");
            assertTrue(classes.contains("com/ruleforge/dsl/builder/LibraryContextBuilder.class"),
                "ruleforge-dsl jar 应含 LibraryContextBuilder");
            assertTrue(classes.contains("com/ruleforge/dsl/builder/NamedConditionBuilder.class"),
                "ruleforge-dsl jar 应含 NamedConditionBuilder");
        }

        @Test
        @DisplayName("ruleforge-dsl jar 含 RuleForgeDslAutoConfiguration(Spring 入口)")
        void dslJarHasAutoconfig() throws Exception {
            File dslJar = findLocalJar();
            List<String> classes = classEntriesIn(dslJar);
            assertTrue(classes.contains("com/ruleforge/dsl/RuleForgeDslAutoConfiguration.class"),
                "ruleforge-dsl jar 应含 RuleForgeDslAutoConfiguration(console-app / executor-app @Import 入口)");
        }

        @Test
        @DisplayName("DSLRuleSetBuilder + dsl/builder/* 在 ruleforge-dsl 模块 test classpath 都能 Class.forName")
        void classloadableInTestScope() {
            // 跑在 ruleforge-dsl 的 test scope,本模块 main 产物自然在 classpath
            for (String fqn : List.of(
                "com.ruleforge.dsl.DSLRuleSetBuilder",
                "com.ruleforge.dsl.RuleParserLexer",
                "com.ruleforge.dsl.RuleParserParser",
                "com.ruleforge.dsl.BuildRulesVisitor",
                "com.ruleforge.dsl.ScriptDecisionTableErrorListener",
                "com.ruleforge.dsl.DSLUtils",
                "com.ruleforge.dsl.RuleForgeDslAutoConfiguration",
                "com.ruleforge.dsl.builder.AbstractContextBuilder",
                "com.ruleforge.dsl.builder.ActionContextBuilder",
                "com.ruleforge.dsl.builder.BuildUtils",
                "com.ruleforge.dsl.builder.ContextBuilder",
                "com.ruleforge.dsl.builder.CriteriaContextBuilder",
                "com.ruleforge.dsl.builder.LibraryContextBuilder",
                "com.ruleforge.dsl.builder.NamedConditionBuilder"
            )) {
                try {
                    Class<?> cls = Class.forName(fqn);
                    assertNotNull(cls, fqn + " 不应为 null");
                    assertEquals(fqn.substring(fqn.lastIndexOf('.') + 1), cls.getSimpleName());
                } catch (ClassNotFoundException e) {
                    fail("V5.44.1 后 " + fqn + " 应在 ruleforge-dsl jar classpath");
                }
            }
        }
    }
}
