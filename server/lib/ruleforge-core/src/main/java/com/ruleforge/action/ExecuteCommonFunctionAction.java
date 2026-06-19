package com.ruleforge.action;

import com.ruleforge.debug.MsgType;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.lhs.CommonFunctionParameter;
import com.ruleforge.runtime.EngineContext;
import com.ruleforge.runtime.function.WorkingMemoryFunctionContext;
import com.ruleforge.engine.Context;

import java.util.List;

/**
 * @author Jacky.gao
 * 2015年7月31日
 */
public class ExecuteCommonFunctionAction extends AbstractAction {
    private String name;
    private String label;
    private CommonFunctionParameter parameter;

    @Override
    public ActionValue execute(Context context, Object matchedObject, List<Object> allMatchedObjects) {
        FunctionDescriptor function = null;
        // V5.100.1 — 砍 containsKey + (findFunctionDescriptor | get) 双 lookup, 套 V5.93
        // 原则: `map.get(key) == null` 已能区分 absent vs null-value. 本场景 value 永为
        // FunctionDescriptor 对象 (非 null, EngineContext.init 唯一 put 是 put(name, fun)
        // + put(label, fun), 无 put(key, null) 风险), 所以等价. byName first (跟原顺序
        // 一致), byLabel fallback. findFunctionDescriptor 内部也是 `get(name) + throw
        // if null`, 跟 V5.93 模式 100% 一致 (用本地 get 替 findFunctionDescriptor 以保留
        // "not found 时 fallback 到 label" 行为, 不能直接用 findFunctionDescriptor 因为
        // 它会 throw). 节省 2 个 containsKey hash lookup (line 26 + line 28).
        function = EngineContext.getFunctionDescriptorMap().get(name);
        if (function == null) {
            function = EngineContext.getFunctionDescriptorLabelMap().get(label);
        }
        if (function == null) {
            throw new RuleException("Function[" + name + "] not exist.");
        }
        String info = (label == null) ? name : label;
        Value value = null;
        Object object = null;
        if (parameter != null) {
            value = parameter.getObjectParameter();
            object = context.getValueCompute().complexValueCompute(value, matchedObject, context, allMatchedObjects);
        }
        String property = null;
        if (function.getArgument() != null && function.getArgument().isNeedProperty()) {
            property = parameter.getProperty();
        }
        Object result = function.doFunction(object, property, new WorkingMemoryFunctionContext(context.getWorkingMemory()));
        info = info + (object == null ? "" : object);

        // 执行信息
        String msg = "*** 执行函数：" + info;
        context.logMsg(msg, MsgType.ExecuteFunction);

        if (result != null) {
            return new ActionValueImpl(name, result);
        } else {
            return null;
        }
    }

    @Override
    public ActionType getActionType() {
        return ActionType.ExecuteCommonFunction;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public CommonFunctionParameter getParameter() {
        return parameter;
    }

    public void setParameter(CommonFunctionParameter parameter) {
        this.parameter = parameter;
    }
}
