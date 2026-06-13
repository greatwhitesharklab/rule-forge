package com.ruleforge.builder.table;

import com.ruleforge.builder.resource.Resource;
import com.ruleforge.model.table.ScriptDecisionTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V5.43.5 — ScriptDecisionTable 行为降级 BDD 锁。
 *
 * <p>V5.43.5 删 {@code CellScriptDSLBuilder} + {@code ScriptDecisionTableRulesBuilder}
 * (老 .ul DSL 链路),老 ScriptDecisionTable(.xml 决策表一种)走 V5.42 DRL eval() 替代。
 * 行为降级范围:
 * <ul>
 *   <li>删 {@code CellScriptDSLBuilder} / {@code ScriptDecisionTableRulesBuilder} 整文件
 *       — 这两个 class 不再被 classloader 找到</li>
 *   <li>KnowledgeBuilder 遇到 ScriptDecisionTable 资源时,走"老 DSL 链"已被删 — 走
 *       V5.42 DRL eval() 替代(暂未实现 ScriptDecisionTable → DRL 转换器,
 *       V5.44 单独 PR 补回)</li>
 *   <li>KnowledgeBuilder 上下文里 {@code scriptDecisionTableRulesBuilder} setter / field
 *       被移除(bean 在 ruleforge-core-context.xml 也删)</li>
 * </ul>
 *
 * <p><b>V5.43.8 — 路线 B 收口</b>:ResourceMigrationRequiredException / LegacyXmlMigrator
 * 已删,本测试不再测"守卫抛"逻辑(全新项目不兼容老格式),仅保留"class 删干净"快照。
 *
 * @since 5.43
 */
@DisplayName("V5.43.5 — ScriptDecisionTable 行为降级(class 删 + 守卫抛)")
class ScriptDecisionTableBehaviorTest {

    @Test
    @DisplayName("CellScriptDSLBuilder / ScriptDecisionTableRulesBuilder 已删")
    void oldBuildersGone() {
        for (String fqn : List.of(
            "com.ruleforge.builder.table.CellScriptDSLBuilder",
            "com.ruleforge.builder.table.ScriptDecisionTableRulesBuilder"
        )) {
            try {
                Class.forName(fqn);
                throw new AssertionError(
                    "V5.43.5 删的 class '" + fqn + "' 仍可被 classloader 找到 — 删不彻底");
            } catch (ClassNotFoundException expected) {
                // 期望:删干净
            }
        }
    }
}
