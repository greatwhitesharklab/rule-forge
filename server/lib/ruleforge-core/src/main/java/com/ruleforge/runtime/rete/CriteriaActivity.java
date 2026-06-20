package com.ruleforge.runtime.rete;
import com.ruleforge.engine.Path;
import com.ruleforge.engine.EvaluationContext;
import com.ruleforge.engine.Context;

import com.ruleforge.debug.MsgType;
import com.ruleforge.model.rule.ValueType;
import com.ruleforge.model.rule.VariableValue;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.EvalLeftPart;
import com.ruleforge.model.rule.lhs.EvaluateResponse;
import com.ruleforge.model.rule.lhs.VariableLeftPart;

import java.util.ArrayList;
import java.util.List;

public class CriteriaActivity extends AbstractActivity {
    protected Criteria criteria;
    private boolean passed;
    private boolean debug;

    public CriteriaActivity(Criteria criteria, boolean debug) {
        this.criteria = criteria;
        this.debug = debug;
    }

    public List<FactTracker> enter(EvaluationContext context, Object obj, FactTracker tracker) {
        if (this.passed) {
            return null;
        } else if (this.joinNodeIsPassed()) {
            return null;
        } else {
            List<Object> allMatchedObjects = new ArrayList<>();
            EvaluateResponse response = null;
            String criteriaId = this.criteria.getId();
            Object storeValue = context.getCriteriaValue(criteriaId);
            if (storeValue != null) {
                response = (EvaluateResponse) storeValue;
            } else {
                response = this.criteria.evaluate(context, obj, allMatchedObjects);
                context.storeCriteriaValue(criteriaId, response);
            }

            boolean result = response.getResult();
            logMessage(response, context);
            if (!result) {
                this.passAndNode();
                return null;
            } else {
                this.passed = true;
                tracker.addObjectCriteria(obj, this.criteria);

                for (Object object : allMatchedObjects) {
                    tracker.addObjectCriteria(object, this.criteria);
                }

                return this.visitPaths(context, obj, tracker);
            }
        }
    }

    public void passAndNode() {
        this.doPassAndNode();
    }

    private void logMessage(EvaluateResponse response, Context context) {
        // V5.88 — 早返:debug=false 时不进 String.format / toString / MessageItem 分配。
        // JFR 35s 抓 2053 sample,reveal logMessage + String.format + StringBuilder 占 1570 sample(76%)。
        // production 通常 debug=false,这次早返预期 per-fact 节约 30-50%。
        if (!this.debug) {
            return;
        }
        String id = this.criteria.getId();
        String leftVariable = null;
        if (this.criteria.getLeft().getLeftPart() instanceof VariableLeftPart) {
            leftVariable = ((VariableLeftPart) this.criteria.getLeft().getLeftPart()).getVariableLabel();
        } else if (this.criteria.getLeft().getLeftPart() instanceof EvalLeftPart) {
            leftVariable = ((EvalLeftPart) this.criteria.getLeft().getLeftPart()).getExpression();
        }
        String leftVariableValue = response.getLeftResult() == null ? "null" : response.getLeftResult().toString();
        String rightVariable = "system";
        if (this.criteria.getValue() != null) {
            if (this.criteria.getValue().getValueType() == ValueType.Variable) {
                rightVariable = ((VariableValue) this.criteria.getValue()).getVariableLabel();
            } else {
                rightVariable = this.criteria.getValue().getValueType().name();
            }
        }
        String rightVariableValue = response.getRightResult() == null ? "null" : response.getRightResult().toString();

        String msg = String.format("^^^ 条件： %s => %s, 左值： %s, 右值： %s",
                id, response.getResult() ? "满足" : "不满足",
                leftVariableValue, rightVariableValue);

        context.logMsg(msg, MsgType.Condition, leftVariable, leftVariableValue, rightVariable, rightVariableValue);
    }

    public boolean joinNodeIsPassed() {
        // V6.9.2 — 收口 Fernflower 反编译 state machine (V6.2 visitPaths / V6.3 KnowledgeBase
        // / V6.4 LeftParser 同档)。 旧实现 4-level 嵌套 if/return/fall-through:
        //   paths==null → fall through → false
        //   size>1 → false
        //   size==1 → 递归 child
        //   size==0 → fall through → false
        // 简化后 early return + 单层 if, 行为 100% 等价, JFR CriteriaActivity.enter L30
        // per-fact hot path 调用点 JIT inlining 受益。 V5.96 立的 "skip 反编译 state machine"
        // 原则延续。
        List<Path> paths = this.getPaths();
        if (paths == null || paths.size() != 1) {
            return false;
        }
        Path path = paths.get(0);
        AbstractActivity activity = (AbstractActivity) path.getTo();
        return activity.joinNodeIsPassed();
    }

    public void reset() {
        this.passed = false;
    }
}
