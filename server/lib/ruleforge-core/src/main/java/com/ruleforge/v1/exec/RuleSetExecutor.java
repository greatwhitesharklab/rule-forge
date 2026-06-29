package com.ruleforge.v1.exec;

import com.ruleforge.engine.RuleExecutionResponse;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSessionImpl;
import com.ruleforge.v1.ast.HitPolicy;
import com.ruleforge.v1.ast.RuleSetNode;
import com.ruleforge.v1.ast.Schema;

import java.util.List;
import java.util.Map;

/**
 * V7.8 — RuleSet 执行器:条件规则走 RETE + 无条件规则(空 / "true" condition)按 hitPolicy 应用。
 *
 * <p>修 V7.0 遗留: {@link RuleSetCompiler} 曾静默丢弃空 condition 规则(前端 RuleSetEditor 新建规则
 * 默认空 condition,用户建的「默认/兜底」规则永不触发且无报错)。
 *
 * <p><b>为什么不把无条件规则塞 RETE</b>: 空 Lhs 规则经 {@code ReteBuilder.buildBranch} 建 {@code __*__}
 * ObjectTypeNode,而 {@code ObjectTypeActivity.support} 的 {@code __*__} 分支只匹配字符串 {@code "__*__"},
 * 不匹配 V1 的 {@code GeneralEntity(schemaName)} fact → 不 fire。故无条件规则在 V1 层独立应用,
 * 不动核心引擎。
 *
 * <p><b>语义(按 hitPolicy)</b>:
 * <ul>
 *   <li>{@link HitPolicy#ALL_MATCH} — 无条件规则「始终应用」(base/setup 值),<b>先</b>于条件规则 fire
 *       应用(条件规则可覆盖 base);ADD_SCORE 类累加则 base + 条件叠加</li>
 *   <li>{@link HitPolicy#FIRST_MATCH} / {@link HitPolicy#PRIORITY} — 无条件规则作 <b>catch-all / else</b>:
 *       先 fire 条件规则,仅当<b>无条件规则未命中</b>({@code resp.getFiredRules().isEmpty()})<b>且未 reject</b>
 *       时才应用</li>
 * </ul>
 * <p>{@code _rejected=true} 时一律跳过无条件规则(reject 终止,catch-all 不覆盖)。
 *
 * <p>V1 action({@link V1ActionRhs} 的 5+INVOKE 个)全是 {@code AbstractAction},override
 * {@code execute(Context, Object, List)} 但从不引用 {@code context}(只操作 fact Map),故脱离 RETE
 * 以 {@code action.execute(null, fact, null)} 独立应用,语义与 RETE RHS 内执行等价。
 */
public final class RuleSetExecutor {

    private RuleSetExecutor() {
    }

    /** 执行 RuleSetNode:条件规则走 RETE,无条件规则按 hitPolicy 应用,原地修改 fact。 */
    public static void execute(RuleSetNode node, Schema schema, Map<String, Object> fact,
                               Map<String, Object> parameters) {
        HitPolicy policy = node.getHitPolicy() == null ? HitPolicy.FIRST_MATCH : node.getHitPolicy();
        List<Rule> conditional = RuleSetCompiler.compile(node, schema);
        List<com.ruleforge.v1.ast.Rule> unconditional = RuleSetCompiler.extractUnconditionalRules(node);
        if (conditional.isEmpty() && unconditional.isEmpty()) {
            return;
        }
        if (policy == HitPolicy.ALL_MATCH) {
            // base/setup 先应用,条件规则后 fire(可覆盖 base;ADD_SCORE 累加)
            applyUnconditional(unconditional, schema, fact);
            fireConditional(conditional, schema, fact, parameters);
            return;
        }
        // FIRST_MATCH / PRIORITY — catch-all:先 fire 条件规则,仅当未命中且未 reject 时应用
        boolean anyFired = fireConditional(conditional, schema, fact, parameters);
        if (!anyFired && !rejected(fact)) {
            applyUnconditional(unconditional, schema, fact);
        }
    }

    /** 条件规则走 RETE。返是否有规则命中(供 catch-all 判定)。空条件列表不建 session,直接返 false。 */
    private static boolean fireConditional(List<Rule> reteRules, Schema schema, Map<String, Object> fact,
                                           Map<String, Object> parameters) {
        if (reteRules.isEmpty()) {
            return false;
        }
        KnowledgePackage kp = V1KnowledgeBuilder.build(schema, reteRules);
        KnowledgeSessionImpl session = new KnowledgeSessionImpl(kp);
        session.insert(fact);
        RuleExecutionResponse resp = session.fireRules(parameters);
        return resp != null && resp.getFiredRules() != null && !resp.getFiredRules().isEmpty();
    }

    /** 无条件规则 actions 直接应用到 fact(脱离 RETE;V1 action 不用 context)。 */
    private static void applyUnconditional(List<com.ruleforge.v1.ast.Rule> unconditional, Schema schema,
                                           Map<String, Object> fact) {
        for (com.ruleforge.v1.ast.Rule r : unconditional) {
            List<com.ruleforge.action.Action> actions = V1ActionRhs.translate(r.getActions(), schema);
            for (com.ruleforge.action.Action a : actions) {
                a.execute(null, fact, null);
            }
        }
    }

    private static boolean rejected(Map<String, Object> fact) {
        return Boolean.TRUE.equals(fact.get(V1ActionRhs.REJECTED_FLAG));
    }
}