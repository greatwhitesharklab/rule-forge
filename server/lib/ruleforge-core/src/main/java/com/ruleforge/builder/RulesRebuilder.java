package com.ruleforge.builder;

import com.ruleforge.action.Action;
import com.ruleforge.action.ConsolePrintAction;
import com.ruleforge.action.ExecuteMethodAction;
import com.ruleforge.action.VariableAssignAction;
import com.ruleforge.builder.rebuild.RulesRebuilderFacade;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.library.action.ActionLibrary;
import com.ruleforge.model.library.action.Method;
import com.ruleforge.model.library.action.SpringBean;
import com.ruleforge.model.library.constant.ConstantCategory;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.model.rule.*;
import com.ruleforge.model.rule.lhs.*;
import com.ruleforge.model.rule.loop.LoopEnd;
import com.ruleforge.model.rule.loop.LoopRule;
import com.ruleforge.model.rule.loop.LoopStart;
import com.ruleforge.model.rule.loop.LoopTarget;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.util.*;

/**
 * @author Jacky.gao
 * 2015年8月19日
 */
@Slf4j
@Setter
@Getter
public class RulesRebuilder {
    private ResourceLibraryBuilder resourceLibraryBuilder;

    /**
     * V5.48 — 5+1 facade 路由层。
     * <p>持有 5 个 {@link com.ruleforge.builder.rebuild.RuleTypeRebuilder}
     * (向导式/表/树/评分卡/DRL),{@link #rebuildRules} / {@link #rebuildRulesForDSL}
     * 的 per-rule 循环委托给它。DrlRuleRebuilder 放最后,fallback(supports 永远 true)。
     * <p>facade 是 stateless(只持有 5 个 rebuilder 引用),eager init 安全。
     */
    private final RulesRebuilderFacade facade = new RulesRebuilderFacade(this);

    public void rebuildRules(List<Library> libraries, List<Rule> rules) {
        rebuildRules(libraries, rules, false);
    }

    public void rebuildRules(List<Library> libraries, List<Rule> rules, boolean isContainSnapshot) {
        if (libraries == null) {
            return;
        }
        if (rules == null) {
            return;
        }
        ResourceLibrary resLibraries = this.resourceLibraryBuilder.buildResourceLibrary(libraries, isContainSnapshot);
        for (Rule rule : rules) {
            try {
                Map<String, String> namedMap = new HashMap<>();
                facade.dispatchRebuild(rule, resLibraries, namedMap, false);
            } catch (Exception e) {
                // P1 — 保留 root cause 链。V5.47 之前 throw new RuleException(errorMsg)
                // 不传 cause,生产侧 catch RuleException 后拿不到根因(DrlParseException /
                // Antlr BailingTokenStream 等被吞),日志分析极困难。修复后 messages
                // 字段保持原"规则【X】包含语法错误"格式(binary + 字符串级兼容),
                // 业务侧 catch(RuleException) 不变,新行为是 getCause() 不再为 null。
                String errorMsg = String.format("规则【%s】包含语法错误", rule.getName());
                log.error(errorMsg, e);
                throw new RuleException(errorMsg, e);
            }
        }
    }

    public void rebuildRulesForDSL(List<Library> libraries, List<Rule> rules) {
        if (libraries == null) {
            return;
        }
        if (rules == null) {
            return;
        }
        ResourceLibrary resLibraries = resourceLibraryBuilder.buildResourceLibrary(libraries);
        for (Rule rule : rules) {
            Map<String, String> namedMap = new HashMap<>();
            // DSL path 不走 try/catch(V5.47 老行为,DSL 错由调用方自己 catch)
            facade.dispatchRebuild(rule, resLibraries, namedMap, true);
        }
    }

    public void convertNamedJunctions(List<Rule> rules) {
        for (Rule rule : rules) {
            if (rule.getLhs() == null) {
                continue;
            }
            Criterion criterion = rule.getLhs().getCriterion();
            Criterion newCriterion = buildCriterion(criterion);
            rule.getLhs().setCriterion(newCriterion);
        }
    }

    private Criterion buildCriterion(Criterion criterion) {
        if (!(criterion instanceof NamedJunction) && !(criterion instanceof Junction)) {
            return criterion;
        }
        if (criterion instanceof NamedJunction) {
            NamedJunction jun = (NamedJunction) criterion;
            return buildNamedJunction(jun);
        } else if (criterion instanceof Junction) {
            buildJunction((Junction) criterion);
        }
        return criterion;
    }

    private void buildJunction(Junction jun) {
        List<Criterion> criterions = jun.getCriterions();
        List<Criterion> newCriterions = new ArrayList<Criterion>();
        for (Criterion c : criterions) {
            NamedCriteria namedCriteria = buildNamedJunction(c);
            if (namedCriteria != null) {
                newCriterions.add(namedCriteria);
            } else if (c instanceof Junction) {
                buildJunction((Junction) c);
            }
            newCriterions.add(c);
        }
        jun.setCriterions(newCriterions);
    }

    private NamedCriteria buildNamedJunction(Criterion criterion) {
        if (!(criterion instanceof NamedJunction)) {
            return null;
        }
        NamedJunction jun = (NamedJunction) criterion;
        NamedCriteria criteria = new NamedCriteria();
        criteria.setReferenceName(jun.getReferenceName());
        criteria.setParent(jun.getParent());
        criteria.setVariableCategory(jun.getVariableCategory());
        JunctionType junctionType = jun.getJunctionType();
        List<NamedItem> items = jun.getItems();
        List<CriteriaUnit> nextUnits = null;
        for (NamedItem item : items) {
            CriteriaUnit unit = new CriteriaUnit();
            unit.setJunctionType(junctionType);
            Criteria c = new Criteria();
            unit.setCriteria(c);
            c.setOp(item.getOp());
            Left left = new Left();
            left.setType(LeftType.NamedReference);
            VariableLeftPart leftPart = new VariableLeftPart();
            leftPart.setDatatype(item.getDatatype());
            leftPart.setVariableCategory(jun.getVariableCategory());
            leftPart.setVariableLabel(item.getVariableLabel());
            leftPart.setVariableName(item.getVariableName());
            left.setLeftPart(leftPart);
            c.setLeft(left);
            c.setValue(item.getValue());
            if (nextUnits == null) {
                criteria.setUnit(unit);
                nextUnits = new ArrayList<>();
                unit.setNextUnits(nextUnits);
            } else {
                nextUnits.add(unit);
            }
        }
        return criteria;
    }

    public void rebuildAction(Action action, ResourceLibrary resLibraries, Map<String, String> namedMap, boolean forDSL) {
        if (action == null) {
            return;
        }
        if (action instanceof VariableAssignAction) {
            List<VariableCategory> variableCategories = resLibraries.getVariableCategories();
            if (variableCategories == null) {
                return;
            }
            VariableAssignAction varAction = (VariableAssignAction) action;
            LeftType type = varAction.getType();
            if (type == null || !type.equals(LeftType.NamedReference)) {
                String variableCategory = varAction.getVariableCategory();
                String variableLabel = varAction.getVariableLabel();
                // 检查字段名是否为空
                if (variableLabel == null) {
                    throw new RuleException("变量字段名为空");
                }

                if (variableLabel.equals("return_value__")) {
                    varAction.setVariableName(variableLabel);
                    varAction.setDatatype(Datatype.Boolean);
                } else if (variableLabel.equals("return_to__")) {
                    varAction.setVariableName(variableLabel);
                    varAction.setDatatype(Datatype.String);
                } else {
                    String variableName = varAction.getVariableName();
                    if (forDSL) {
                        Variable var = getVariableByLabel(variableCategories, variableCategory, variableLabel, namedMap);
                        varAction.setVariableName(var.getName());
                        varAction.setDatatype(var.getType());
                    } else {
                        if (StringUtils.isNotBlank(variableName)) {
                            Variable var = getVariableByName(variableCategories, variableCategory, variableName, namedMap);
                            varAction.setVariableLabel(var.getLabel());
                            varAction.setDatatype(var.getType());
                        } else {
                            Variable var = getVariableByLabel(variableCategories, variableCategory, variableLabel, namedMap);
                            varAction.setVariableName(var.getName());
                            varAction.setDatatype(var.getType());
                        }
                    }
                }
            }
            if (type != null && type.equals(LeftType.NamedReference)) {
                String refName = varAction.getReferenceName();
                String variableCategory = namedMap.get(refName);
                if (variableCategory == null) {
                    refName = refName.substring(1, refName.length());
                    variableCategory = namedMap.get(refName);
                }
                if (variableCategory == null) {
                    throw new RuleException("Reference [" + refName + "] not define.");
                }
                if (forDSL) {
                    Variable var = getVariableByLabel(variableCategories, variableCategory, varAction.getVariableLabel(), namedMap);
                    varAction.setVariableName(var.getName());
                    varAction.setVariableCategory(variableCategory);
                    varAction.setDatatype(var.getType());
                } else {
                    String variableName = varAction.getVariableName();
                    if (StringUtils.isNotBlank(variableName)) {
                        Variable var = getVariableByLabel(variableCategories, variableCategory, variableName, namedMap);
                        varAction.setVariableLabel(var.getLabel());
                        varAction.setVariableCategory(variableCategory);
                        varAction.setDatatype(var.getType());
                    } else {
                        Variable var = getVariableByLabel(variableCategories, variableCategory, varAction.getVariableLabel(), namedMap);
                        varAction.setVariableName(var.getName());
                        varAction.setVariableCategory(variableCategory);
                        varAction.setDatatype(var.getType());
                    }
                }
            }
            Value value = ((VariableAssignAction) action).getValue();
            rebuildValue(value, resLibraries, namedMap, forDSL);
        } else if (action instanceof ConsolePrintAction) {
            ConsolePrintAction consoleAction = (ConsolePrintAction) action;
            Value value = consoleAction.getValue();
            rebuildValue(value, resLibraries, namedMap, forDSL);
        } else if (action instanceof ExecuteMethodAction) {
            List<ActionLibrary> actionLibraries = resLibraries.getActionLibraries();
            if (actionLibraries == null) {
                return;
            }
            ExecuteMethodAction methodAction = (ExecuteMethodAction) action;
            String beanLabel = methodAction.getBeanLabel();
            String methodLabel = methodAction.getMethodLabel();
            SpringBean targetBean = null;
            for (ActionLibrary al : actionLibraries) {
                List<SpringBean> beans = al.getSpringBeans();
                if (beans == null) {
                    continue;
                }
                for (SpringBean bean : beans) {
                    if (beanLabel.equals(bean.getName())) {
                        targetBean = bean;
                        break;
                    }
                }
                if (targetBean != null) break;
            }
            Method targetMethod = null;
            if (targetBean != null) {
                methodAction.setBeanId(targetBean.getId());
                List<Method> methods = targetBean.getMethods();
                if (methods == null) {
                    throw new RuleException("Bean [" + beanLabel + "] not define methods.");
                }
                for (Method method : methods) {
                    if (method.getName().equals(methodLabel)) {
                        targetMethod = method;
                        break;
                    }
                }
                if (targetMethod == null) {
                    throw new RuleException("Bean [" + beanLabel + "] method[" + methodLabel + "] not define.");
                }
                methodAction.setMethodName(targetMethod.getMethodName());
            }
            List<Parameter> parameters = methodAction.getParameters();
            rebuildParameters(resLibraries, parameters, targetMethod.getParameters(), namedMap, forDSL);
        }
    }

    private void rebuildCommonFunctionParameter(CommonFunctionParameter parameter, ResourceLibrary resLibraries, Map<String, String> namedMap, boolean forDSL) {
        String property = parameter.getProperty();
        if (StringUtils.isEmpty(property)) {
            return;
        }
        Value value = parameter.getObjectParameter();
        rebuildValue(value, resLibraries, namedMap, forDSL);
        String category = null;
        if (value instanceof VariableValue) {
            VariableValue vv = (VariableValue) value;
            category = vv.getVariableCategory();
        } else if (value instanceof VariableCategoryValue) {
            VariableCategoryValue vc = (VariableCategoryValue) value;
            category = vc.getVariableCategory();
        } else {
            throw new RuleException("Function parameter is invalid.");
        }
        List<VariableCategory> variableCategories = resLibraries.getVariableCategories();
        for (VariableCategory vc : variableCategories) {
            if (!category.equals(vc.getName())) {
                continue;
            }
            for (Variable v : vc.getVariables()) {
                if (v.getName().equals(property) || v.getLabel().equals(property)) {
                    parameter.setProperty(v.getName());
                    parameter.setPropertyLabel(v.getLabel());
                    break;
                }
            }
        }
    }

    private void rebuildParameters(ResourceLibrary resLibraries, List<Parameter> parameters, List<com.ruleforge.model.library.action.Parameter> targetParameters, Map<String, String> namedMap, boolean forDSL) {
        if (parameters != null && targetParameters != null) {
            for (int i = 0; i < parameters.size(); i++) {
                if (i > targetParameters.size() - 1) {
                    break;
                }
                Parameter parameter = parameters.get(i);
                com.ruleforge.model.library.action.Parameter p = targetParameters.get(i);
                parameter.setType(p.getType());
                Value value = parameter.getValue();
                rebuildValue(value, resLibraries, namedMap, forDSL);
            }
        }
    }

    public void rebuildCriterion(Criterion criterion, ResourceLibrary resLibraries, Map<String, String> namedMap, boolean forDSL) {
        if (criterion == null) {
            return;
        }
        if (criterion instanceof Criteria) {
            Criteria criteria = (Criteria) criterion;
            rebuildCriteria(resLibraries, criteria, namedMap, forDSL);
        } else if (criterion instanceof Junction) {
            Junction junction = (Junction) criterion;
            Collection<Criterion> criterionList = junction.getCriterions();
            if (criterionList != null) {
                for (Criterion c : criterionList) {
                    rebuildCriterion(c, resLibraries, namedMap, forDSL);
                }
            }
        } else if (criterion instanceof NamedCriteria) {
            NamedCriteria namedCriteria = (NamedCriteria) criterion;
            namedMap.put(namedCriteria.getReferenceName(), namedCriteria.getVariableCategory());
            CriteriaUnit unit = namedCriteria.getUnit();
            buildCriteriaUnit(unit, resLibraries, namedMap, forDSL);
        } else if (criterion instanceof NamedJunction) {
            NamedJunction jun = (NamedJunction) criterion;
            namedMap.put(jun.getReferenceName(), jun.getVariableCategory());
        }
    }

    private void buildCriteriaUnit(CriteriaUnit unit, ResourceLibrary resLibraries, Map<String, String> namedMap, boolean forDSL) {
        Criteria criteria = unit.getCriteria();
        if (criteria != null) {
            rebuildCriteria(resLibraries, criteria, namedMap, forDSL);
        }
        List<CriteriaUnit> units = unit.getNextUnits();
        if (units != null) {
            for (CriteriaUnit nextUnit : units) {
                buildCriteriaUnit(nextUnit, resLibraries, namedMap, forDSL);
            }
        }
    }

    private void rebuildCriteria(ResourceLibrary resLibraries, Criteria criteria, Map<String, String> namedMap, boolean forDSL) {
        List<VariableCategory> variableCategories = resLibraries.getVariableCategories();
        Left left = criteria.getLeft();
        LeftPart leftPart = left.getLeftPart();
        if (leftPart instanceof VariableLeftPart) {
            VariableLeftPart part = (VariableLeftPart) leftPart;
            String variableLabel = part.getVariableLabel();
            String variableName = part.getVariableName();
            if (StringUtils.isNotBlank(variableLabel)) {
                String variableCategory = part.getVariableCategory();
                if (forDSL) {
                    Variable var = getVariableByLabel(variableCategories, variableCategory, variableLabel, namedMap);
                    part.setVariableName(var.getName());
                    part.setDatatype(var.getType());
                } else {
                    Variable var = getVariableByName(variableCategories, variableCategory, variableName, namedMap);
                    part.setVariableLabel(var.getLabel());
                    part.setDatatype(var.getType());
                }
            }
        } else if (leftPart instanceof AbstractLeftPart) {
            AbstractLeftPart part = (AbstractLeftPart) leftPart;
            String variableCategory = part.getVariableCategory();
            String variableLabel = part.getVariableLabel();
            String variableName = part.getVariableName();
            if (forDSL) {
                Variable var = getVariableByLabel(variableCategories, variableCategory, variableLabel, namedMap);
                part.setVariableName(var.getName());
            } else {
                Variable var = getVariableByName(variableCategories, variableCategory, variableName, namedMap);
                part.setVariableLabel(var.getLabel());
            }
        } else if (leftPart instanceof CommonFunctionLeftPart) {
            CommonFunctionLeftPart p = (CommonFunctionLeftPart) leftPart;
            CommonFunctionParameter parameter = p.getParameter();
            rebuildCommonFunctionParameter(parameter, resLibraries, namedMap, forDSL);
        } else if (leftPart instanceof MethodLeftPart) {
            MethodLeftPart part = (MethodLeftPart) leftPart;
            String beanLabel = part.getBeanLabel();
            String methodLabel = part.getMethodLabel();
            List<ActionLibrary> actionLibraries = resLibraries.getActionLibraries();
            SpringBean targetBean = null;
            for (ActionLibrary al : actionLibraries) {
                List<SpringBean> beans = al.getSpringBeans();
                for (SpringBean bean : beans) {
                    if (beanLabel.equals(bean.getName())) {
                        part.setBeanId(bean.getId());
                        targetBean = bean;
                        break;
                    }
                }
                if (targetBean != null) {
                    break;
                }
            }
            if (targetBean == null) {
                throw new RuleException("Bean[" + beanLabel + "] not exist.");
            }
            Method targetMethod = null;
            for (Method method : targetBean.getMethods()) {
                if (methodLabel.equals(method.getName())) {
                    targetMethod = method;
                    part.setMethodName(method.getMethodName());
                    break;
                }
            }
            if (targetMethod == null) {
                throw new RuleException("Bean[" + beanLabel + "] method[" + targetMethod + "] not exist.");
            }
            List<Parameter> parameters = part.getParameters();
            rebuildParameters(resLibraries, parameters, targetMethod.getParameters(), namedMap, forDSL);
        } else if (leftPart instanceof FunctionLeftPart) {
            FunctionLeftPart part = (FunctionLeftPart) leftPart;
            List<Parameter> parameters = part.getParameters();
            if (parameters != null && parameters.size() > 0) {
                for (Parameter param : parameters) {
                    Value pv = param.getValue();
                    if (pv == null) {
                        continue;
                    }
                    rebuildValue(pv, resLibraries, namedMap, forDSL);
                }
            }
        }
        Value value = criteria.getValue();
        rebuildValue(value, resLibraries, namedMap, forDSL);
    }

    public void rebuildValue(Value value, ResourceLibrary resLibraries, Map<String, String> namedMap, boolean forDSL) {
        if (value == null) {
            return;
        }
        if (value instanceof ParenValue) {
            ParenValue pv = (ParenValue) value;
            Value v = pv.getValue();
            rebuildValue(v, resLibraries, namedMap, forDSL);
        } else if (value instanceof ConstantValue) {
            ConstantValue cv = (ConstantValue) value;
            String category = cv.getConstantCategory();
            if (forDSL) {
                String label = cv.getConstantLabel();
                com.ruleforge.model.library.constant.Constant constant = getConstantByLabel(resLibraries.getConstantCategories(), category, label);
                cv.setConstantName(constant.getName());
            } else {
                String name = cv.getConstantName();
                com.ruleforge.model.library.constant.Constant constant = getConstantByName(resLibraries.getConstantCategories(), category, name);
                cv.setConstantLabel(constant.getLabel());
            }
        } else if (value instanceof VariableValue) {
            VariableValue variableValue = (VariableValue) value;
            if (forDSL) {
                if (StringUtils.isNotBlank(variableValue.getVariableLabel())) {
                    Variable var = getVariableByLabel(resLibraries.getVariableCategories(), variableValue.getVariableCategory(), variableValue.getVariableLabel(), namedMap);
                    variableValue.setVariableName(var.getName());
                    variableValue.setDatatype(var.getType());
                }
            } else {
                if (StringUtils.isNotBlank(variableValue.getVariableName())) {
                    Variable var = getVariableByName(resLibraries.getVariableCategories(), variableValue.getVariableCategory(), variableValue.getVariableName(), namedMap);
                    variableValue.setVariableLabel(var.getLabel());
                    variableValue.setDatatype(var.getType());
                }
            }
        } else if (value instanceof ParameterValue) {
            ParameterValue parameterValue = (ParameterValue) value;
            if (forDSL) {
                String variableLabel = parameterValue.getVariableLabel();
                Variable var = getVariableByLabel(resLibraries.getVariableCategories(), VariableCategory.PARAM_CATEGORY, variableLabel, namedMap);
                parameterValue.setVariableName(var.getName());
            } else {
                String variableName = parameterValue.getVariableName();
                Variable var = getVariableByName(resLibraries.getVariableCategories(), VariableCategory.PARAM_CATEGORY, variableName, namedMap);
                parameterValue.setVariableLabel(var.getLabel());
            }
        } else if (value instanceof NamedReferenceValue) {
            NamedReferenceValue refValue = (NamedReferenceValue) value;
            String propertyLabel = refValue.getPropertyLabel();
            String propertyName = refValue.getPropertyName();
            String refName = refValue.getReferenceName();
            String variableCategory = namedMap.get(refName);
            if (variableCategory == null) {
                refName = refName.substring(1, refName.length());
                variableCategory = namedMap.get(refName);
            }
            if (variableCategory == null) {
                throw new RuleException("Reference [" + refName + "] not define.");
            }
            if (forDSL) {
                Variable var = getVariableByLabel(resLibraries.getVariableCategories(), variableCategory, propertyLabel, namedMap);
                refValue.setPropertyName(var.getName());
                refValue.setDatatype(var.getType());
            } else {
                Variable var = getVariableByName(resLibraries.getVariableCategories(), variableCategory, propertyName, namedMap);
                refValue.setPropertyLabel(var.getLabel());
                refValue.setDatatype(var.getType());
            }
        } else if (value instanceof CommonFunctionValue) {
            CommonFunctionValue cfv = (CommonFunctionValue) value;
            CommonFunctionParameter parameter = cfv.getParameter();
            rebuildCommonFunctionParameter(parameter, resLibraries, namedMap, forDSL);
        } else if (value instanceof MethodValue) {
            MethodValue methodValue = (MethodValue) value;
            String beanLabel = methodValue.getBeanLabel();
            String methodLabel = methodValue.getMethodLabel();
            List<ActionLibrary> actionLibraries = resLibraries.getActionLibraries();
            SpringBean targetBean = null;
            for (ActionLibrary al : actionLibraries) {
                List<SpringBean> beans = al.getSpringBeans();
                for (SpringBean bean : beans) {
                    if (beanLabel.equals(bean.getName())) {
                        methodValue.setBeanId(bean.getId());
                        targetBean = bean;
                        break;
                    }
                }
                if (targetBean != null) break;
            }
            if (targetBean == null) {
                throw new RuleException("Bean[" + beanLabel + "] not exist.");
            }
            Method targetMethod = null;
            for (Method method : targetBean.getMethods()) {
                if (methodLabel.equals(method.getName())) {
                    targetMethod = method;
                    methodValue.setMethodName(method.getMethodName());
                    break;
                }
            }
            if (targetMethod == null) {
                throw new RuleException("Bean[" + beanLabel + "] method[" + targetMethod + "] not exist.");
            }
            List<Parameter> parameters = methodValue.getParameters();
            rebuildParameters(resLibraries, parameters, targetMethod.getParameters(), namedMap, forDSL);
        }
        ComplexArithmetic complexArithmetic = value.getArithmetic();
        if (complexArithmetic == null) {
            return;
        }
        Value subValue = complexArithmetic.getValue();
        rebuildValue(subValue, resLibraries, namedMap, forDSL);
    }

    // V6.9.19 — 抽 findChildInCategory helper, 消 getConstantByName/ByLabel + getVariableByName/ByLabel
    // 4 method 14 行 100% 同构 pattern (linear scan categories → match category → linear scan children
    // → match child → throw on miss)。 Build-time per-rule-build 调用, JFR 0 sample 预期, pure code
    // elegance + dead-code reduction (4 × 14 行 → 4 × 5-7 行 + 13 行 helper = -28 行净减少)。
    //
    // <C> 容器类型 (ConstantCategory / VariableCategory), <T> child 类型 (Constant / Variable)。
    // categoryNameExtractor: 取容器的名字 (ConstantCategory::getLabel / VariableCategory::getName)
    // childrenGetter: 取容器的子列表 (::getConstants / ::getVariables)
    // childMatch: 子项匹配谓词 (child.getName().equals(name) 等)
    // kind: 错误信息前缀 ("Constant" / "Variable")
    private <C, T> T findChildInCategory(
            List<C> categories,
            String category,
            String identifier,
            java.util.function.Function<C, String> categoryNameExtractor,
            java.util.function.Function<C, List<T>> childrenGetter,
            java.util.function.Predicate<T> childMatch,
            String kind) {
        for (C c : categories) {
            if (!categoryNameExtractor.apply(c).equals(category)) {
                continue;
            }
            for (T child : childrenGetter.apply(c)) {
                if (childMatch.test(child)) {
                    return child;
                }
            }
        }
        throw new RuleException(kind + " [" + category + "." + identifier + "] was not found.");
    }

    private com.ruleforge.model.library.constant.Constant getConstantByName(List<ConstantCategory> constantCategories, String category, String name) {
        return this.findChildInCategory(constantCategories, category, name,
            ConstantCategory::getLabel, ConstantCategory::getConstants,
            constant -> constant.getName().equals(name),
            "Constant");
    }

    private com.ruleforge.model.library.constant.Constant getConstantByLabel(List<ConstantCategory> constantCategories, String category, String label) {
        return this.findChildInCategory(constantCategories, category, label,
            ConstantCategory::getLabel, ConstantCategory::getConstants,
            constant -> constant.getLabel().equals(label),
            "Constant");
    }

    public Variable getVariableByName(List<VariableCategory> variableCategories, String category, String name, Map<String, String> namedMap) {
        // V6.9 — 砍 containsKey + get 双 lookup, 套 V5.93 原则. value 永为 String (namedMap
        // 是 category 重命名表,正常用法 put non-null value), 所以 `get(key) != null` ↔
        // `containsKey(key)` 100% 等价. 节省 1 个 containsKey hash lookup per call (build-time
        // per-rule-build 路径, JFR 0 sample 预期, perf 微优化 + code elegance).
        if (namedMap != null) {
            String renamed = namedMap.get(category);
            if (renamed != null) {
                category = renamed;
            }
        }
        return this.findChildInCategory(variableCategories, category, name,
            VariableCategory::getName, VariableCategory::getVariables,
            var -> var.getName().equals(name),
            "Variable");
    }

    public Variable getVariableByLabel(List<VariableCategory> variableCategories, String category, String label, Map<String, String> namedMap) {
        // V6.9 — 同 getVariableByName, 砍 containsKey + get 双 lookup 套 V5.93 原则.
        if (namedMap != null) {
            String renamed = namedMap.get(category);
            if (renamed != null) {
                category = renamed;
            }
        }
        return this.findChildInCategory(variableCategories, category, label,
            VariableCategory::getName, VariableCategory::getVariables,
            var -> var.getLabel().equals(label),
            "Variable");
    }

}
