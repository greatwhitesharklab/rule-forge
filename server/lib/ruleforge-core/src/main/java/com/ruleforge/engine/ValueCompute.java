//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.ruleforge.engine;
import com.ruleforge.engine.Context;

import com.ruleforge.config.PropertyConfigurer;
import com.ruleforge.Utils;
import com.ruleforge.action.ActionValue;
import com.ruleforge.action.ExecuteMethodAction;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.function.Argument;
import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.model.rule.AbstractValue;
import com.ruleforge.model.rule.ArithmeticType;
import com.ruleforge.model.rule.CommonFunctionValue;
import com.ruleforge.model.rule.ComplexArithmetic;
import com.ruleforge.model.rule.ConstantValue;
import com.ruleforge.model.rule.MethodValue;
import com.ruleforge.model.rule.ParameterValue;
import com.ruleforge.model.rule.ParenValue;
import com.ruleforge.model.rule.SimpleValue;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.ValueType;
import com.ruleforge.model.rule.VariableCategoryValue;
import com.ruleforge.model.rule.VariableValue;
import com.ruleforge.model.rule.lhs.CommonFunctionParameter;
import com.ruleforge.runtime.EngineContext;
import com.ruleforge.runtime.function.WorkingMemoryFunctionContext;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ValueCompute {
    public static final String BEAN_ID = "ruleforge.valueCompute";
    private static final String EXPR_PRE = "${";
    private static final String EXPR_SUFF = "}";

    /**
     * V5.86 — className → Class&lt;?&gt; 缓存。{@link #findObject(String, Object, Context)}
     * per-fact 字符串 compare 转 Class 引用比较。
     * <p>ConcurrentHashMap 是因为 {@code ValueCompute} 是 Spring 单例,跨线程共享。null value
     * 用 {@link #CLASS_NOT_FOUND} sentinel 表示 Class.forName 失败。
     */
    private final Map<String, Class<?>> classNameCache = new ConcurrentHashMap<>();
    private static final Class<?> CLASS_NOT_FOUND = Class.class;  // sentinel: 不会跟任何真实 class 相等

    public ValueCompute() {
    }

    public Object complexValueCompute(Value value, Object matchedObject, Context context, List<Object> allMatchedObjects) {
        return this.compute(value, context, matchedObject, allMatchedObjects);
    }

    public Object complexArithmeticCompute(Object matchedFact, Context context, List<Object> allMatchedObjects, ComplexArithmetic arithmetic, Object leftValue) {
        StringBuffer expr = new StringBuffer();
        this.addToExpr(expr, leftValue);

        AbstractValue rightValue;
        for (; arithmetic != null; arithmetic = rightValue.getArithmetic()) {
            ArithmeticType type = arithmetic.getType();
            this.addToExpr(expr, type);
            rightValue = (AbstractValue) arithmetic.getValue();
            if (rightValue instanceof ParenValue) {
                ParenValue pv = (ParenValue) rightValue;
                Object obj = this.compute(pv.getValue(), context, matchedFact, allMatchedObjects);
                this.addToExpr(expr, obj);
            } else {
                Object rightObj = this.fetchValue(rightValue, context, matchedFact, allMatchedObjects);
                if (rightObj == null) {
                    rightObj = "null";
                }

                this.addToExpr(expr, rightObj);
            }
        }

        return context.parseExpression(expr.toString());
    }

    private Object compute(Value value, Context context, Object matchedFact, List<Object> allMatchedObjects) {
        Object leftObj = this.fetchValue(value, context, matchedFact, allMatchedObjects);
        ComplexArithmetic arithmetic = value.getArithmetic();
        if (arithmetic == null) {
            return leftObj;
        } else {
            StringBuffer expr = new StringBuffer();
            this.addToExpr(expr, leftObj);

            AbstractValue rightValue;
            for (; arithmetic != null; arithmetic = rightValue.getArithmetic()) {
                ArithmeticType type = arithmetic.getType();
                this.addToExpr(expr, type);
                rightValue = (AbstractValue) arithmetic.getValue();
                if (rightValue instanceof ParenValue) {
                    ParenValue pv = (ParenValue) rightValue;
                    Object obj = this.compute(pv.getValue(), context, matchedFact, allMatchedObjects);
                    this.addToExpr(expr, obj);
                } else {
                    Object rightObj = this.fetchValue(rightValue, context, matchedFact, allMatchedObjects);
                    if (rightObj == null) {
                        rightObj = "null";
                    }

                    this.addToExpr(expr, rightObj);
                }
            }

            return context.parseExpression(expr.toString());
        }
    }

    private void addToExpr(StringBuffer expr, Object obj) {
        if (obj instanceof ArithmeticType) {
            expr.append(obj.toString());
        } else if (obj instanceof Number) {
            expr.append(obj.toString());
        } else {
            String data = obj.toString();
            if (data.indexOf(32) > -1) {
                expr.append("\"").append(obj).append("\"");
            } else {
                expr.append(data);
            }
        }

    }

    private Object fetchValue(Value value, Context context, Object matchedFact, List<Object> allMatchedObjects) {
        Object left = null;
        ValueType type = value.getValueType();
        if (type.equals(ValueType.Input)) {
            left = ((SimpleValue) value).getContent();
        } else if (type.equals(ValueType.Constant)) {
            ConstantValue cv = (ConstantValue) value;
            left = this.getConstantValue(cv.getConstantName());
        } else {
            String categoryName;
            if (type.equals(ValueType.VariableCategory)) {
                VariableCategoryValue vc = (VariableCategoryValue) value;
                categoryName = vc.getVariableCategory();
                return this.findObject(context, matchedFact, categoryName, allMatchedObjects);
            }

            Object object;
            String property;
            if (type.equals(ValueType.Parameter)) {
                ParameterValue pv = (ParameterValue) value;
                categoryName = "参数";
                object = this.findObject(context, matchedFact, categoryName, allMatchedObjects);
                if (object == null) {
                    return null;
                }

                property = pv.getVariableName();
                left = Utils.getObjectProperty(object, property);
            } else if (type.equals(ValueType.Method)) {
                MethodValue mv = (MethodValue) value;
                ExecuteMethodAction action = new ExecuteMethodAction();
                action.setBeanId(mv.getBeanId());
                action.setBeanLabel(mv.getBeanLabel());
                action.setMethodName(mv.getMethodName());
                action.setMethodLabel(mv.getMethodLabel());
                action.setParameters(mv.getParameters());
                ActionValue actionValue = action.execute(context, matchedFact, allMatchedObjects);
                if (actionValue != null) {
                    left = actionValue.getValue();
                } else {
                    left = null;
                }
            } else if (type.equals(ValueType.CommonFunction)) {
                CommonFunctionValue v = (CommonFunctionValue) value;
                CommonFunctionParameter functionParameter = v.getParameter();
                Value propertyValue = functionParameter.getObjectParameter();
                object = this.complexValueCompute(propertyValue, matchedFact, context, allMatchedObjects);
                FunctionDescriptor fun = EngineContext.findFunctionDescriptor(v.getName());
                Argument arg = fun.getArgument();
                property = null;
                if (arg.isNeedProperty()) {
                    property = functionParameter.getProperty();
                }

                left = fun.doFunction(object, property, new WorkingMemoryFunctionContext(context.getWorkingMemory()));
            } else if (type.equals(ValueType.Paren)) {
                ParenValue parenValue = (ParenValue) value;
                left = this.compute(parenValue.getValue(), context, matchedFact, allMatchedObjects);
            } else {
                VariableValue vv = (VariableValue) value;
                categoryName = vv.getVariableCategory();
                object = this.findObject(context, matchedFact, categoryName, allMatchedObjects);
                if (object == null) {
                    return null;
                }

                property = vv.getVariableName();
                left = Utils.getObjectProperty(object, property);
            }
        }

        return left;
    }

    private String getConstantValue(String constant) {
        if (constant.startsWith("${") && constant.endsWith("}")) {
            String key = constant.substring(2, constant.length() - 1);
            return PropertyConfigurer.getProperty(key);
        } else {
            return constant;
        }
    }

    private Object findObject(Context context, Object matchedFact, String categoryName, List<Object> allMatchedObjects) {
        String className = context.getVariableCategoryClass(categoryName);
        Object object = this.findObject(className, matchedFact, context);
        if (allMatchedObjects != null && object != null && !allMatchedObjects.contains(object)) {
            allMatchedObjects.add(object);
        }

        return object;
    }

    public Object findObject(String className, Object matchedFact, Context context) {
        if (className.equals(HashMap.class.getName())) {
            return context.getWorkingMemory().getParameters();
        }

        // V5.86 — 优先用 cache 后的 Class 引用比较,避免 per-fact 字符串 equals 开销
        Class<?> targetClass = classNameCache.get(className);
        if (targetClass == null) {
            targetClass = loadClass(className);
            classNameCache.put(className, targetClass == null ? CLASS_NOT_FOUND : targetClass);
        }
        if (targetClass == CLASS_NOT_FOUND) {
            targetClass = null;
        }

        if (matchedFact instanceof Collection) {
            Collection coll = (Collection) matchedFact;
            // V5.96 — Iterator var123 → enhanced for
            for (Object obj : coll) {
                if (targetClass != null) {
                    if (targetClass.isInstance(obj)) {
                        return obj;
                    }
                } else if (obj.getClass().getName().equals(className)) {
                    return obj;
                }

                if (obj instanceof GeneralEntity) {
                    GeneralEntity entity = (GeneralEntity) obj;
                    if (targetClass != null) {
                        if (targetClass.isInstance(obj)) {
                            return obj;
                        }
                    } else if (entity.getTargetClass().equals(className)) {
                        return obj;
                    }
                }
            }
        } else {
            if (targetClass != null) {
                if (targetClass.isInstance(matchedFact)) {
                    return matchedFact;
                }
            } else if (matchedFact.getClass().getName().equals(className)) {
                return matchedFact;
            }

            if (matchedFact instanceof GeneralEntity) {
                GeneralEntity entity = (GeneralEntity) matchedFact;
                if (targetClass != null) {
                    if (targetClass.isInstance(matchedFact)) {
                        return matchedFact;
                    }
                } else if (entity.getTargetClass().equals(className)) {
                    return matchedFact;
                }
            }
        }

        Map<String, Object> allFactsMap = context.getWorkingMemory().getAllFactsMap();
        return allFactsMap.get(className);
    }

    /** V5.86 — Class.forName 一次性加载,失败返 null(fallback 字符串 compare)。 */
    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
