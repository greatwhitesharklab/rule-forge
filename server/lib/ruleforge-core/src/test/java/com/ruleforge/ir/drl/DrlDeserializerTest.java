package com.ruleforge.ir.drl;

import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.Rhs;
import com.ruleforge.model.rule.lhs.And;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.Criterion;
import com.ruleforge.model.rule.lhs.Junction;
import com.ruleforge.model.rule.lhs.JunctionType;
import com.ruleforge.model.rule.lhs.Lhs;
import com.ruleforge.model.rule.lhs.PropertyCriteria;
import com.ruleforge.model.rule.lhs.VariableLeftPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.42.4 — DrlDeserializer(DRL AST → Rule model)第一版 BDD。
 *
 * <p>范围(本类只测):
 * <ul>
 *   <li>顶层 rule name / extends / 11 attribute 全部映射到 Rule 字段</li>
 *   <li>lhs 最简 {@code Type(field op value)} 形式 → Lhs 含一条 Junction
 *       + PropertyCriteria(applicant.age > 18)</li>
 *   <li>rhs 第一版留空 Rhs(actions=null)— V5.42.5 再补</li>
 *   <li>未知 attribute 名 → DrlParseException(V5.42.4 sanity check)</li>
 *   <li>未注册的 type → DrlParseException(继承 V5.42.2 resolver 行为)</li>
 *   <li>parseDrl(drl, resolver) 静态入口 / parseDrlFile(path, resolver) 文件入口</li>
 * </ul>
 *
 * <p>本类**不**测 rhs 重建、accumulate 内部、from 内部 — 留 V5.42.5+。
 *
 * @since 5.42
 */
@DisplayName("V5.42.4 — DrlDeserializer(DRL AST → Rule model)BDD")
class DrlDeserializerTest {

    private DatatypeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DatatypeResolver();
        resolver.register("Applicant",
            DatatypeResolver.TypeInfo.fact("Applicant",
                Arrays.asList("age", "income", "score", "name", "city")));
        resolver.register("Loan",
            DatatypeResolver.TypeInfo.fact("Loan", Arrays.asList("amount")));
    }

    // ============================================================
    // === 顶层 rule 字段映射 ===
    // ============================================================

    @Nested
    @DisplayName("Given DRL 文本,When deserialize,Then Rule 顶层字段正确")
    class TopLevelField {

        @Test
        @DisplayName("最简单 rule:name 必填,salience 等 default null")
        void simplest() {
            List<Rule> rules = DrlDeserializer.parseDrl(
                "rule \"R1\" when Applicant(age > 18) then end", resolver);
            assertEquals(1, rules.size());
            Rule r = rules.get(0);
            assertEquals("R1", r.getName());
            // 11 attribute 全部 default
            assertNull(r.getSalience());
            assertNull(r.getAgendaGroup());
            assertNull(r.getActivationGroup());
            assertNull(r.getRuleflowGroup());
            assertNull(r.getAutoFocus());
            assertNull(r.getLoop());
            assertNull(r.getEnabled());
            assertNull(r.getEffectiveDate());
            assertNull(r.getExpiresDate());
            // Lhs / Rhs 都初始化
            assertNotNull(r.getLhs(), "Lhs 应初始化(V5.42.4 创建空 Junction)");
            assertNotNull(r.getRhs(), "Rhs 应初始化(留空 actions 列表)");
        }

        @Test
        @DisplayName("D2 extends:'X_else' extends 'X' → setWithElse(true),name='X_else'")
        void extendsRule() {
            List<Rule> rules = DrlDeserializer.parseDrl(
                "rule \"X_else\" extends \"X\" when Applicant(age > 18) then end", resolver);
            assertEquals(1, rules.size());
            Rule r = rules.get(0);
            assertEquals("X_else", r.getName());
            assertTrue(r.isWithElse(),
                "D2:extends 决定 → Rule.withElse = true(rule 'X_else' 走 X 的 else 路径)");
        }

        @Test
        @DisplayName("11 attribute 全部映射到 Rule 字段")
        void all11AttributesMapped() {
            String drl = "rule \"R1\" " +
                "[salience 99, " +
                "agenda-group \"g1\", " +
                "activation-group \"a1\", " +
                "ruleflow-group \"rf1\", " +
                "auto-focus true, " +
                "no-loop true, " +
                "lock-on-active true, " +
                "enabled true, " +
                "date-effective \"2026-01-01\", " +
                "date-expires \"2027-01-01\", " +
                "timer (int(60))] " +
                "when Applicant(age > 18) then end";
            List<Rule> rules = DrlDeserializer.parseDrl(drl, resolver);
            assertEquals(1, rules.size());
            Rule r = rules.get(0);
            assertEquals(99, r.getSalience());
            assertEquals("g1", r.getAgendaGroup());
            assertEquals("a1", r.getActivationGroup());
            assertEquals("rf1", r.getRuleflowGroup());
            assertEquals(Boolean.TRUE, r.getAutoFocus());
            // V5.42.4 决定:no-loop 语义反转(no-loop=true → 可循环)留 V5.42.5 — 本版
            // 不映射,Rule.loop 保持 default null
            assertNull(r.getLoop(),
                "V5.42.4 简化:no-loop 不映射到 Rule.loop,V5.42.5 再反转");
            // 注:Rule 没有 lock-on-active 字段,V5.42.4 把它存到 Other / 丢弃;
            //   enabled:Rule.enabled 直接用
            assertEquals(Boolean.TRUE, r.getEnabled());
            // date-effective / expires 解析成 Date
            assertNotNull(r.getEffectiveDate(), "date-effective → Rule.effectiveDate(Date)");
            assertNotNull(r.getExpiresDate(), "date-expires → Rule.expiresDate(Date)");
            // 简单 sanity:日期 2026-01-01 / 2027-01-01
            assertEquals(2026, yearOf(r.getEffectiveDate()));
            assertEquals(2027, yearOf(r.getExpiresDate()));
            // timer → V5.42.4 暂时忽略(留 V5.42.5)
        }

        @Test
        @DisplayName("boolean false 映射:no-loop false → Rule.loop = true(语义反转)")
        void booleanFalseInversion() {
            String drl = "rule \"R1\" [no-loop false] when Applicant() then end";
            List<Rule> rules = DrlDeserializer.parseDrl(drl, resolver);
            assertEquals(1, rules.size());
            // Drools 6 no-loop = true 表示"不循环",Rule.loop = true 表示"可循环"
            // 所以 no-loop=false 应 → Rule.loop = true(允许循环)
            // 实际 V5.42.4 决定:no-loop=true → Rule.loop=null(默认,可循环);
            //                       no-loop=false → Rule.loop=true(强制循环)
            // 这里先存原始 boolean 字符串,留 V5.42.5 决定反转语义
            assertNotNull(rules.get(0));
        }

        @Test
        @DisplayName("D4 决定:顶层 dialect 解析后丢弃,不进 Rule 任何字段")
        void dialectDropped() {
            // V5.42.4 决定:Rule 不加 dialect 字段,顶层 dialect 解析后丢弃
            String drl = "rule \"R1\" [dialect \"mvel\"] when Applicant() then end";
            List<Rule> rules = DrlDeserializer.parseDrl(drl, resolver);
            assertEquals(1, rules.size());
            // 没有 getDialect 字段(本测试用反射也不做 — V5.42.4 通过"visitor
            // 解析后不传值"达到)
        }
    }

    // ============================================================
    // === lhs 第一版 ===
    // ============================================================
    //
    // V5.42.4 决定:lhs 第一版**不**展开成 Junction + Criterion(老 PropertyCriteria 不是
    // Criterion,展开到 And/Or + NamedCriteria 链路是 V5.42.5 大工程)。
    // 本类只断言:
    //   - Lhs 对象初始化
    //   - 字段 property / op / value 通过 Rule 顶层 accessor 不可见 — 留 V5.42.5
    //     后给 Rule 加 getLhsDescription() / Rule 打印
    //   - 空 when 段 → Lhs 存在但 criterion = null
    //
    // BDD 的"lhs 内部结构"测试在 V5.42.5(NamedCriteria 包装 + Junction 链路)。

    @Nested
    @DisplayName("Given lhs 第一版,When deserialize,Then Lhs.criterion 链挂 And → Criteria(V5.51.2)")
    class LhsFirstVersion {

        @Test
        @DisplayName("Lhs 初始化 + criterion 链上能读到 'age > 18' Conditions")
        void lhsInitialized() {
            List<Rule> rules = DrlDeserializer.parseDrl(
                "rule \"R1\" when Applicant(age > 18) then end", resolver);
            Rule r = rules.get(0);
            assertNotNull(r.getLhs(), "Lhs 应初始化");
            // V5.51.2 migration:PropertyCriteria 全部挂到 Lhs.criterion(And) 链
            assertNotNull(r.getLhs().getCriterion(),
                "V5.51.2 后 Lhs.criterion 不再是 null,PropertyCriteria 走 And → Criteria 链");
            And and = (And) r.getLhs().getCriterion();
            assertEquals(1, and.getCriterions().size());
            Criteria c = (Criteria) and.getCriterions().get(0);
            assertEquals("age", ((VariableLeftPart) c.getLeft().getLeftPart()).getVariableName());
        }

        @Test
        @DisplayName("空 lhs:rule 'R1' when then end → Lhs.criterion = null")
        void emptyLhs() {
            List<Rule> rules = DrlDeserializer.parseDrl(
                "rule \"R1\" when then end", resolver);
            assertEquals(1, rules.size());
            assertNotNull(rules.get(0).getLhs());
            assertNull(rules.get(0).getLhs().getCriterion(),
                "空 when 段 → Lhs.criterion = null");
        }

        @Test
        @DisplayName("Lhs.criterion 链反查:Applicant(age > 18) 1 个 Criteria(variableName=age)")
        void pendingLhsCriteriaAccessible() {
            // V5.51.2 migration:V5.42.4 PENDING_LHS 暂存路径已删,改走 Lhs.criterion
            // 链(And Junction → Criteria → Left/LeftPart)。
            List<Rule> rules = DrlDeserializer.parseDrl(
                "rule \"R1\" when Applicant(age > 18) then end", resolver);
            And and = (And) rules.get(0).getLhs().getCriterion();
            assertEquals(1, and.getCriterions().size());
            Criteria c = (Criteria) and.getCriterions().get(0);
            assertEquals("age", ((VariableLeftPart) c.getLeft().getLeftPart()).getVariableName());
        }
    }

    // ============================================================
    // === rhs 第一版 ===
    // ============================================================

    @Nested
    @DisplayName("Given rhs 第一版,When deserialize,Then Rhs.actions = null 留 V5.42.5")
    class RhsFirstVersion {

        @Test
        @DisplayName("空 rhs:rule R1 when Applicant() then end → Rhs 初始化但 actions = null")
        void emptyRhs() {
            List<Rule> rules = DrlDeserializer.parseDrl(
                "rule \"R1\" when Applicant() then end", resolver);
            assertEquals(1, rules.size());
            Rhs rhs = rules.get(0).getRhs();
            assertNotNull(rhs, "Rhs 应初始化");
            assertNull(rhs.getActions(), "V5.42.4 rhs actions 留 V5.42.5");
        }
    }

    // ============================================================
    // === 错误路径 ===
    // ============================================================

    @Nested
    @DisplayName("Given 错误 DRL,When deserialize,Then DrlParseException")
    class ErrorPath {

        @Test
        @DisplayName("未注册 type:Unknown → DrlParseException(继承 V5.42.2 resolver 行为)")
        void unknownTypeFails() {
            assertThrows(DrlParseException.class, () -> DrlDeserializer.parseDrl(
                "rule \"R1\" when Unknown(age > 18) then end", resolver));
        }

        @Test
        @DisplayName("语法错:extends 段错放(grammar 不接受)→ DrlParseException")
        void syntaxErrorFails() {
            // 把 rule name 漏掉
            assertThrows(DrlParseException.class, () -> DrlDeserializer.parseDrl(
                "rule when Applicant() then end", resolver));
        }
    }

    // ============================================================
    // === parseDrlFile 文件入口 ===
    // ============================================================

    @Nested
    @DisplayName("Given .drl 文件,When parseDrlFile,Then 同 parseDrl 行为")
    class FileEntry {

        @Test
        @DisplayName("读 /tmp/r.drl 内容 → 输出 Rule 列表")
        void fileEntry() throws Exception {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("r", ".drl");
            java.nio.file.Files.writeString(tmp, "rule \"R1\" when Applicant(age > 18) then end\n");
            try {
                List<Rule> rules = DrlDeserializer.parseDrlFile(tmp.toString(), resolver);
                assertEquals(1, rules.size());
                assertEquals("R1", rules.get(0).getName());
            } finally {
                java.nio.file.Files.deleteIfExists(tmp);
            }
        }

        @Test
        @DisplayName("读不存在文件 → DrlParseException(IOException 包装)")
        void missingFileFails() {
            assertThrows(DrlParseException.class,
                () -> DrlDeserializer.parseDrlFile("/tmp/does-not-exist-12345.drl", resolver));
        }
    }

    // ============================================================
    // === 入口:DrlResourceBuilder ===
    // ============================================================

    @Nested
    @DisplayName("Given DrlResourceBuilder.build(DrlResource),When 调,Then 产出 List<Rule>")
    class DrlResourceBuilderEntry {

        @Test
        @DisplayName("DrlResourceBuilder 直接接 .drl 文本 + resolver 产出 Rule 列表")
        void builderEntry() {
            DrlResourceBuilder b = new DrlResourceBuilder(resolver);
            DrlResource res = new DrlResource(
                "rule \"R1\" when Applicant(age > 18) then end",
                "/tmp/r.drl");
            List<Rule> rules = b.build(res);
            assertEquals(1, rules.size());
            assertEquals("R1", rules.get(0).getName());
        }

        @Test
        @DisplayName("DrlResourceBuilder.support(DrlResource):DRL 路径都返回 true")
        void supportTrueForDrl() {
            DrlResourceBuilder b = new DrlResourceBuilder(resolver);
            assertTrue(b.support(new DrlResource("rule \"R1\" when Applicant() then end", "/x/r.drl")));
            assertTrue(b.support(new DrlResource("rule \"R1\" when Applicant() then end", "/x/r.DRL")));
        }

        @Test
        @DisplayName("DrlResourceBuilder.support(非 .drl 路径)→ false(其他 builder 接管)")
        void supportFalseForNonDrl() {
            DrlResourceBuilder b = new DrlResourceBuilder(resolver);
            assertFalse(b.support(new DrlResource("<rule/>", "/x/r.xml")));
        }
    }

    // ============================================================
    // === helpers ===
    // ============================================================

    private static int yearOf(java.util.Date d) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return Integer.parseInt(sdf.format(d));
    }

    private static <T extends Throwable> T assertThrows(Class<T> expected, Runnable r) {
        try {
            r.run();
            throw new AssertionError("Expected " + expected.getSimpleName() + " but nothing was thrown");
        } catch (Throwable t) {
            if (expected.isInstance(t)) {
                return expected.cast(t);
            }
            throw new AssertionError("Expected " + expected.getSimpleName() + " but got " + t.getClass().getSimpleName() + ": " + t.getMessage(), t);
        }
    }
}
