package com.ruleforge.model.rule.lhs;

import com.ruleforge.Utils;
import com.ruleforge.action.ActionValue;
import com.ruleforge.action.ExecuteMethodAction;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.ComplexArithmetic;
import com.ruleforge.model.rule.Op;
import com.ruleforge.model.rule.Value;
import com.ruleforge.runtime.rete.EvaluationContext;
import com.ruleforge.runtime.rete.ValueCompute;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * @author Jacky.gao
 * 2014年12月29日
 */
public class Criteria extends BaseCriterion implements BaseCriteria {
    @JsonIgnore
    private String id;
    private Op op;
    private Left left;
    private Value value;
    // V5.95 — debug 门控 addTipMsg 字段。默认 false (production-safe),由 NodeActivityFactory
    // 在构造 CriteriaActivity 时从 Rule.debug 传入。evaluate 走 if (this.debug) 门控
    // 4 个 addTipMsg + cleanTipMsg — debug=false 时省 4 string concat + 4 StringBuilder.append。
    @JsonIgnore
    private boolean debug;

    public Criteria() {
    }

    public EvaluateResponse evaluate(EvaluationContext context, Object obj, List<Object> allMatchedObjects) {
        Datatype datatype = null;
        Object leftResult = null;
        // V5.95 — debug 门控:debug=false 时跳过所有 4 个 addTipMsg + 1 个 cleanTipMsg。
        // 4 个 addTipMsg 的结果在 evaluate 末尾被 cleanTipMsg 抹掉,debug=false 时
        // 整个序列是纯浪费(string concat + StringBuilder.append 反复执行)。
        // 跟 V5.88 CriteriaActivity.logMessage 早返同模式,同一个 debug flag 门控两处。
        if (this.debug) {
            context.addTipMsg("计算条件：" + this.getId());
        }
        ValueCompute valueCompute = context.getValueCompute();
        LeftPart leftPart = this.left.getLeftPart();
        String leftId = this.left.getId();
        if (this.debug) {
            context.addTipMsg("左值：" + leftId);
        }
        String valueId;
        // V5.94 — 用 getPartValue 直接判断 cache hit,砍 partValueExist 双 lookup。
        // HashMap.get 对 "missing" / "null-stored" 都返 null,所以 cache-hit 判定
        // 为 cached != null。副作用:null-stored 值会重算,对 VariableLeftPart 是
        // 幂等的(getObjectProperty 纯函数),其他 LeftPart 类型 production 实践
        // 中 null-stored 罕见(规则匹配非空值),且重算语义等价 — 见
        // docs/notes/v594-partvalue-double-lookup.md。锁 V5.94 BDD 测试契约。
        Object cachedLeft = context.getPartValue(leftId);
        if (cachedLeft != null) {
            leftResult = cachedLeft;
            if (leftPart instanceof VariableLeftPart) {
                datatype = ((VariableLeftPart) leftPart).getDatatype();
            }
        } else {
            Object leftValue = null;
            if (leftPart instanceof VariableLeftPart) {
                VariableLeftPart varPart = (VariableLeftPart) leftPart;
                datatype = varPart.getDatatype();
                if (varPart.getVariableName() == null) {
                    throw new RuleException("Criteria left variableName [" + varPart.getVariableName() + "] can not be null.");
                }

                valueId = context.getVariableCategoryClass(varPart.getVariableCategory());
                Object targetObj = context.getValueCompute().findObject(valueId, obj, context);
                if (targetObj == null) {
                    throw new RuleException("Object [" + valueId + "] not exist.");
                }

                leftValue = Utils.getObjectProperty(targetObj, varPart.getVariableName());
            } else if (leftPart instanceof MethodLeftPart) {
                MethodLeftPart methodPart = (MethodLeftPart) leftPart;
                ExecuteMethodAction methodAction = new ExecuteMethodAction();
                methodAction.setBeanId(methodPart.getBeanId());
                methodAction.setBeanLabel(methodPart.getBeanLabel());
                methodAction.setMethodLabel(methodPart.getMethodLabel());
                methodAction.setMethodName(methodPart.getMethodName());
                methodAction.setParameters(methodPart.getParameters());
                ActionValue actionValue = methodAction.execute(context, obj, allMatchedObjects);
                if (actionValue == null) {
                    leftValue = null;
                } else {
                    leftValue = actionValue.getValue();
                }
            } else if (leftPart instanceof ExistLeftPart) {
                ExistLeftPart existPart = (ExistLeftPart) leftPart;
                leftValue = existPart.evaluate(context, obj, allMatchedObjects);
            } else if (leftPart instanceof AllLeftPart) {
                AllLeftPart allPart = (AllLeftPart) leftPart;
                leftValue = allPart.evaluate(context, obj, allMatchedObjects);
            } else if (leftPart instanceof CollectLeftPart) {
                CollectLeftPart collectPart = (CollectLeftPart) leftPart;
                leftValue = collectPart.evaluate(context, obj, allMatchedObjects);
            } else if (leftPart instanceof CommonFunctionLeftPart) {
                CommonFunctionLeftPart part = (CommonFunctionLeftPart) leftPart;
                leftValue = part.evaluate(context, obj, allMatchedObjects);
            } else if (leftPart instanceof FromLeftPart) {
                // V5.52.1:DRL from $stream / from collect(...) / from accumulate(...) 路由
                // 走 FromLeftPart.evaluate 三分支(stream/collect/accumulate)。
                FromLeftPart fromPart = (FromLeftPart) leftPart;
                leftValue = fromPart.evaluate(context, obj, allMatchedObjects);
            }

            leftResult = leftValue;
            ComplexArithmetic arithmetic = this.left.getArithmetic();
            if (arithmetic != null) {
                leftResult = valueCompute.complexArithmeticCompute(obj, context, allMatchedObjects, arithmetic, leftValue);
            }

            context.storePartValue(leftId, leftResult);
        }

        EvaluateResponse response = new EvaluateResponse();
        response.setLeftResult(leftResult);
        Object right = null;
        if (this.value != null) {
            valueId = this.value.getId();
            if (this.debug) {
                context.addTipMsg("右值：" + valueId);
            }
            // V5.94 — 同 leftId 模式,getPartValue 直接判断 cache hit,砍双 lookup。
            Object cachedRight = context.getPartValue(valueId);
            if (cachedRight != null) {
                right = cachedRight;
                response.setRightResult(right);
            } else {
                right = valueCompute.complexValueCompute(this.value, obj, context, allMatchedObjects);
                response.setRightResult(right);
                context.storePartValue(valueId, right);
            }
        }

        if (datatype == null) {
            datatype = Utils.getDatatype(leftResult);
        }

        if (this.debug) {
            context.addTipMsg("执行比较：" + this.op.toString());
        }
        boolean result = context.getAssertorEvaluator().evaluate(leftResult, right, datatype, this.op);
        response.setResult(result);
        // V5.95 — cleanTipMsg 始终调用(无 debug 门控),保证 tipMsgBuilder 状态
        // reset。debug=false 时 builder 已空(no addTipMsg called),cleanTipMsg 是
        // no-op,但保留调用维持下游 ActivationImpl.execute addTipMsg 行为契约
        // (append 不会错加 ">>" 前缀)。锁 [[v595-criteria-addtipmsg-debug-gate]] BDD。
        context.cleanTipMsg();
        return response;
    }

    public String getId() {
        if (this.id == null) {
            this.id = this.left.getId();
            this.id = this.id + "【" + this.op.toString() + "】";
            if (this.value != null) {
                this.id = this.id + this.value.getId();
            }
        }

        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    // V5.95 — debug 门控 addTipMsg 字段访问器。NodeActivityFactory 在构造
    // CriteriaActivity 时调 setDebug(node.isDebug()) 传播 debug flag。
    public boolean isDebug() {
        return this.debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public Op getOp() {
        return this.op;
    }

    public void setOp(Op op) {
        this.op = op;
    }

    public Left getLeft() {
        return this.left;
    }

    public void setLeft(Left left) {
        this.left = left;
    }

    public Value getValue() {
        return this.value;
    }

    public void setValue(Value value) {
        this.value = value;
    }
}
