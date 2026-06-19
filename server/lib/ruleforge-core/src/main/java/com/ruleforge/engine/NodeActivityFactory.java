package com.ruleforge.engine;
import com.ruleforge.runtime.rete.AndActivity;
import com.ruleforge.runtime.rete.CriteriaActivity;
import com.ruleforge.runtime.rete.ObjectTypeActivity;
import com.ruleforge.runtime.rete.OrActivity;
import com.ruleforge.runtime.rete.TerminalActivity;
import com.ruleforge.engine.Activity;

import com.ruleforge.model.rete.AndNode;
import com.ruleforge.model.rete.CriteriaNode;
import com.ruleforge.model.rete.Line;
import com.ruleforge.model.rete.ObjectTypeNode;
import com.ruleforge.model.rete.OrNode;
import com.ruleforge.model.rete.ReteNode;
import com.ruleforge.model.rete.TerminalNode;
import com.ruleforge.model.rule.lhs.Criteria;

import java.util.Map;

/**
 * Node → Activity 工厂:TD-2.2 把 {@code model.rete.*Node.newActivity(context)} 集中迁到这里,
 * 节点类只描述结构,Activity 创建统一在 runtime 侧。
 *
 * <p>dispatch 用 {@code instanceof} 而非 visitor(节点类型固定 5 种,instanceof 可读性更好);
 * 新增节点时这里加 case + 新 {@code Activity} 子类即可。
 */
public final class NodeActivityFactory {
    private NodeActivityFactory() {
    }

    /** 入口:按节点类型分派。同节点同 context 复用已有 Activity(原各 Node.newActivity 的 context 缓存语义)。 */
    public static Activity create(ReteNode node, Map<Object, Object> context) {
        if (node == null) {
            throw new IllegalArgumentException("node must not be null");
        }
        // V5.100.9 — 砍 containsKey+get 双 lookup(context.put 只存非 null Activity → get+null-check 安全)
        Activity cached = (Activity) context.get(node);
        if (cached != null) {
            return cached;
        }
        Activity activity;
        if (node instanceof TerminalNode) {
            activity = createTerminal((TerminalNode) node);
        } else if (node instanceof ObjectTypeNode) {
            activity = createObjectType((ObjectTypeNode) node, context);
        } else if (node instanceof CriteriaNode) {
            activity = createCriteria((CriteriaNode) node, context);
        } else if (node instanceof AndNode) {
            activity = createAnd((AndNode) node, context);
        } else if (node instanceof OrNode) {
            activity = createOr((OrNode) node, context);
        } else {
            throw new IllegalArgumentException("Unsupported ReteNode type: " + node.getClass().getName());
        }
        context.put(node, activity);
        return activity;
    }

    private static TerminalActivity createTerminal(TerminalNode node) {
        return new TerminalActivity(node.getRule());
    }

    private static ObjectTypeActivity createObjectType(ObjectTypeNode node, Map<Object, Object> context) {
        ObjectTypeActivity activity;
        Class<?> targetClass = null;
        if (!node.getObjectTypeClass().equals(ObjectTypeNode.NON_CLASS)) {
            try {
                targetClass = Class.forName(node.getObjectTypeClass());
            } catch (ClassNotFoundException e) {
                // 留 null,fallback 到 String 构造器(targetClass exact 匹配)
            }
        }
        if (targetClass != null) {
            activity = new ObjectTypeActivity(targetClass);
        } else {
            activity = new ObjectTypeActivity(node.getObjectTypeClass());
        }
        for (Line line : node.getLines()) {
            activity.addPath(line.newPath(context));
        }
        return activity;
    }

    private static CriteriaActivity createCriteria(CriteriaNode node, Map<Object, Object> context) {
        // V5.95 — 把 node.isDebug() 传播到 Criteria.debug 字段,让 Criteria.evaluate
        // 4 个 addTipMsg + cleanTipMsg 走 if (this.debug) 门控。NodeActivityFactory
        // 是唯一 production CriteriaActivity 构造点,所有 Rule.debug 状态从此流入。
        Criteria criteria = node.getCriteria();
        criteria.setDebug(node.isDebug());
        CriteriaActivity activity = new CriteriaActivity(criteria, node.isDebug());
        for (Line line : node.getLines()) {
            activity.addPath(line.newPath(context));
        }
        return activity;
    }

    private static AndActivity createAnd(AndNode node, Map<Object, Object> context) {
        AndActivity activity = new AndActivity();
        for (Line line : node.getLines()) {
            activity.addPath(line.newPath(context));
        }
        return activity;
    }

    private static OrActivity createOr(OrNode node, Map<Object, Object> context) {
        OrActivity activity = new OrActivity();
        for (Line line : node.getLines()) {
            activity.addPath(line.newPath(context));
        }
        return activity;
    }
}
