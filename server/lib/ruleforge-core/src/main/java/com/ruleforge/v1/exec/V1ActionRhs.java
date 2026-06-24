package com.ruleforge.v1.exec;

import com.ruleforge.action.AbstractAction;
import com.ruleforge.action.ActionType;
import com.ruleforge.action.ActionValue;
import com.ruleforge.engine.Context;
import com.ruleforge.exception.RuleException;
import com.ruleforge.v1.ast.Action;
import com.ruleforge.v1.ast.Schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * V1 Action → RETE RHS 翻译器(W1-4)。
 *
 * <p>把 V1 AST 的结构化 Action(5 种)翻译成 ruleforge-core {@link com.ruleforge.action.Action}
 * 列表(塞进 Rule.rhs),命中时对 fact(Map)执行副作用。
 *
 * <p>用 V1-native action 实现(直接操作 Map fact),不映射老 VariableAssignAction
 * (那个强耦合 variableCategory/library 机制)。V1 fact 是 schema 定义的 Map/GeneralEntity。
 *
 * <p>Action 语义(design doc Block 4):
 * <ul>
 *   <li>{@code SET_VARIABLE}(target, value|ref) — fact[target] = 字面量 或 fact[ref] 字段值</li>
 *   <li>{@code ADD_SCORE}(target, value) — fact[target] += value(数值累加)</li>
 *   <li>{@code SET_DECISION}(value) — fact[decisionField] = value(Decision 节点读)</li>
 *   <li>{@code REJECT}(reason) — fact[decisionField]="reject" + fact["_rejected"]=true + reason;
 *       Flow runner 检查 _rejected 终止流程</li>
 *   <li>{@code FLAG}(reason) — fact[flagsField] 列表追加 reason</li>
 * </ul>
 *
 * <p>decisionField 默认 "decision";flagsField 默认 "flags"(schema 可含)。Action 永远不含 CEL
 * — value 是字面量或字段引用(只读)。
 */
public final class V1ActionRhs {

    /** V1 fact 里标记 REJECT 已触发的 key(Flow runner 检查终止)。 */
    public static final String REJECTED_FLAG = "_rejected";
    public static final String REJECT_REASON = "_rejectReason";
    public static final String DEFAULT_DECISION_FIELD = "decision";
    public static final String DEFAULT_FLAGS_FIELD = "flags";

    private V1ActionRhs() {
    }

    /** V1 actions → RETE RHS action 列表。decisionField 默认 "decision"。 */
    public static List<com.ruleforge.action.Action> translate(List<Action> v1Actions, Schema schema) {
        return translate(v1Actions, schema, DEFAULT_DECISION_FIELD, DEFAULT_FLAGS_FIELD);
    }

    /** V1 actions → RETE RHS action 列表(显式 decisionField/flagsField)。 */
    public static List<com.ruleforge.action.Action> translate(List<Action> v1Actions, Schema schema,
                                                              String decisionField, String flagsField) {
        List<com.ruleforge.action.Action> reteActions = new ArrayList<>();
        if (v1Actions == null) {
            return reteActions;
        }
        for (Action v1 : v1Actions) {
            reteActions.add(translateOne(v1, decisionField, flagsField));
        }
        return reteActions;
    }

    private static com.ruleforge.action.Action translateOne(Action v1, String decisionField, String flagsField) {
        switch (v1.getType()) {
            case SET_VARIABLE:
                return new SetVariableAction(v1);
            case ADD_SCORE:
                return new AddScoreAction(v1);
            case SET_DECISION:
                return new SetDecisionAction(v1, decisionField);
            case REJECT:
                return new RejectAction(v1, decisionField);
            case FLAG:
                return new FlagAction(v1, flagsField);
            default:
                throw new RuleException("未知 V1 action 类型: " + v1.getType());
        }
    }

    /** 把 matchedObject 当 Map 操作(GeneralEntity/HashMap;不支持 POJO,V1 fact 必须是 Map)。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object matchedObject) {
        if (!(matchedObject instanceof Map)) {
            throw new RuleException("V1 action 要求 fact 是 Map(GeneralEntity/HashMap),实际: "
                    + (matchedObject == null ? "null" : matchedObject.getClass().getName()));
        }
        return (Map<String, Object>) matchedObject;
    }

    /** 取 action 的值:字面量 value 或 ref 字段引用(只读 fact[ref])。 */
    private static Object resolveValue(Action v1, Map<String, Object> fact) {
        if (v1.getRef() != null) {
            return fact.get(v1.getRef());
        }
        return v1.getValue();
    }

    // ===== 5 种 V1 action 实现(AbstractAction,execute 修改 Map fact) =====

    /** SET_VARIABLE:fact[target] = 字面量 或 fact[ref]。 */
    private static final class SetVariableAction extends AbstractAction {
        private final Action v1;
        SetVariableAction(Action v1) { this.v1 = v1; }
        @Override public ActionValue execute(Context context, Object matchedObject, List<Object> all) {
            Map<String, Object> fact = asMap(matchedObject);
            fact.put(v1.getTarget(), resolveValue(v1, fact));
            return null;
        }
        @Override public ActionType getActionType() { return ActionType.VariableAssign; }
    }

    /** ADD_SCORE:fact[target] += value(数值累加;target 缺省从 0 起)。 */
    private static final class AddScoreAction extends AbstractAction {
        private final Action v1;
        AddScoreAction(Action v1) { this.v1 = v1; }
        @Override public ActionValue execute(Context context, Object matchedObject, List<Object> all) {
            Map<String, Object> fact = asMap(matchedObject);
            double current = fact.get(v1.getTarget()) instanceof Number
                    ? ((Number) fact.get(v1.getTarget())).doubleValue() : 0.0;
            double add = v1.getValue() instanceof Number
                    ? ((Number) v1.getValue()).doubleValue() : 0.0;
            fact.put(v1.getTarget(), current + add);
            return null;
        }
        @Override public ActionType getActionType() { return ActionType.VariableAssign; }
    }

    /** SET_DECISION:fact[decisionField] = value。 */
    private static final class SetDecisionAction extends AbstractAction {
        private final Action v1;
        private final String decisionField;
        SetDecisionAction(Action v1, String decisionField) { this.v1 = v1; this.decisionField = decisionField; }
        @Override public ActionValue execute(Context context, Object matchedObject, List<Object> all) {
            Map<String, Object> fact = asMap(matchedObject);
            fact.put(decisionField, resolveValue(v1, fact));
            return null;
        }
        @Override public ActionType getActionType() { return ActionType.VariableAssign; }
    }

    /** REJECT:fact[decisionField]="reject" + _rejected=true + _rejectReason。
     *  Flow runner(W2)检查 _rejected 终止流程。 */
    private static final class RejectAction extends AbstractAction {
        private final Action v1;
        private final String decisionField;
        RejectAction(Action v1, String decisionField) { this.v1 = v1; this.decisionField = decisionField; }
        @Override public ActionValue execute(Context context, Object matchedObject, List<Object> all) {
            Map<String, Object> fact = asMap(matchedObject);
            fact.put(decisionField, "reject");
            fact.put(REJECTED_FLAG, true);
            fact.put(REJECT_REASON, v1.getReason());
            return null;
        }
        @Override public ActionType getActionType() { return ActionType.VariableAssign; }
    }

    /** FLAG:fact[flagsField] 列表追加 reason(列表缺省新建)。 */
    private static final class FlagAction extends AbstractAction {
        private final Action v1;
        private final String flagsField;
        FlagAction(Action v1, String flagsField) { this.v1 = v1; this.flagsField = flagsField; }
        @Override @SuppressWarnings("unchecked")
        public ActionValue execute(Context context, Object matchedObject, List<Object> all) {
            Map<String, Object> fact = asMap(matchedObject);
            Object existing = fact.get(flagsField);
            List<Object> flags = existing instanceof List
                    ? (List<Object>) existing : new ArrayList<>();
            flags.add(v1.getReason());
            fact.put(flagsField, flags);
            return null;
        }
        @Override public ActionType getActionType() { return ActionType.VariableAssign; }
    }
}
