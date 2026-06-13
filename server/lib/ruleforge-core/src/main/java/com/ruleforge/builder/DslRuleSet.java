package com.ruleforge.builder;

import com.ruleforge.builder.resource.Resource;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.rule.RuleSet;

/**
 * V5.44.1 — ruleforge-core 端的轻量接口,定义 .ul 老 DSL 链的最小契约。
 *
 * <p>引入动机:KnowledgeBuilder 字段 {@code dslRuleSetBuilder} 需要 type-safe 引用,
 * 但 ruleforge-core 不再依赖 ruleforge-dsl jar(后者是 V5.44.1 新拆的独立 jar,
 * 依赖方向是 core → dsl → core 会形成循环)。最干净的解法是:core 端只持有
 * interface 引用,DSLRuleSetBuilder 真正实现挪到 ruleforge-dsl。
 *
 * <p>实现方: {@code com.ruleforge.dsl.DSLRuleSetBuilder} (ruleforge-dsl jar)。
 *
 * @since 5.44
 */
public interface DslRuleSet {

    /**
     * V5.43 收口后仅 {@code .ul} 后缀返回 true;.xml rule 老路径全部走 .xml 分支,
     * 不进 DSL chain(避免走老解析路径)。
     */
    boolean support(Resource resource);

    /**
     * 解析 .ul 文本 → {@link RuleSet}(含 rules + libraries)。
     *
     * @throws RuleException 解析失败时
     */
    RuleSet build(String script) throws RuleException;
}
