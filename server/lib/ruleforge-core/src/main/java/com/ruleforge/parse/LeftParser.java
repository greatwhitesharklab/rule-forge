//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.ruleforge.parse;

import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.ComplexArithmetic;
import com.ruleforge.model.rule.SimpleArithmetic;
import com.ruleforge.model.rule.SimpleArithmeticValue;
import com.ruleforge.model.rule.SimpleValue;
import com.ruleforge.model.rule.lhs.CommonFunctionLeftPart;
import com.ruleforge.model.rule.lhs.CommonFunctionParameter;
import com.ruleforge.model.rule.lhs.FunctionLeftPart;
import com.ruleforge.model.rule.lhs.Left;
import com.ruleforge.model.rule.lhs.LeftType;
import com.ruleforge.model.rule.lhs.MethodLeftPart;
import com.ruleforge.model.rule.lhs.VariableLeftPart;

import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;

public class LeftParser extends AbstractParser<Left> {
    private ComplexArithmeticParser complexArithmeticParser;
    private SimpleArithmeticParser simpleArithmeticParser;
    private ValueParser valueParser;

    public LeftParser() {
    }

    public Left parse(Element element) {
        Left left = new Left();
        String type = element.attributeValue("type");
        // V6.9.7 — if/else state machine → ternary (跟 V6.9.4 topJunctionOf 同模式)
        left.setType(StringUtils.isNotEmpty(type) ? LeftType.valueOf(type) : LeftType.variable);

        switch (left.getType()) {
            case variable:
                left.setLeftPart(this.buildVariableLeftPart(element));
                break;
            case function:
                left.setLeftPart(this.buildFunctionLeftPart(element));
                break;
            case method:
                left.setLeftPart(this.buildMethodLeftPart(element));
                break;
            case parameter:
                left.setLeftPart(this.buildVariableLeftPart(element));
                break;
            case commonfunction:
                left.setLeftPart(this.buildCommonFunctionLeftPart(element));
                break;
            case NamedReference:
                throw new RuleException("Not support reference type.");
            case all:
                throw new RuleException("Not support all type.");
            case exist:
                throw new RuleException("Not support exist type.");
            case collect:
                throw new RuleException("Not support collect type.");
            case eval:
                throw new RuleException("Not support eval type.");
        }

        // V5.96 — Iterator var123 → enhanced for
        for (Object obj : element.elements()) {
            if (obj != null && obj instanceof Element) {
                Element ele = (Element) obj;
                String name = ele.getName();
                if (this.complexArithmeticParser.support(name)) {
                    left.setArithmetic(this.complexArithmeticParser.parse(ele));
                } else if (this.simpleArithmeticParser.support(name)) {
                    SimpleArithmetic simpleArith = this.simpleArithmeticParser.parse(ele);
                    left.setArithmetic(this.convertSimpleArithmetic(simpleArith));
                }
            }
        }

        return left;
    }

    private ComplexArithmetic convertSimpleArithmetic(SimpleArithmetic simpleArith) {
        // V6.9.7 — 2-level if/else state machine → early return (跟 V6.9.3 OrBuilder buildCriterion 同模式)
        if (simpleArith == null) {
            return null;
        }
        ComplexArithmetic complex = new ComplexArithmetic();
        complex.setType(simpleArith.getType());
        SimpleValue simpleValue = new SimpleValue();
        complex.setValue(simpleValue);
        SimpleArithmeticValue sv = simpleArith.getValue();
        simpleValue.setContent(sv.getContent());
        SimpleArithmetic nextSimpleArithmetic = sv.getArithmetic();
        simpleValue.setArithmetic(this.convertSimpleArithmetic(nextSimpleArithmetic));
        return complex;
    }

    private CommonFunctionLeftPart buildCommonFunctionLeftPart(Element element) {
        CommonFunctionLeftPart part = new CommonFunctionLeftPart();
        part.setName(element.attributeValue("function-name"));
        part.setLabel(element.attributeValue("function-label"));

        // V6.4 — 2-level nested do-while find-first → enhanced for + 2 个 continue。
        // 原来 2-level do-while: 内层 find-next Element, 外层 find-next function-parameter
        // Element, 内嵌 for 收集 value 子 Element, setParameter 每次覆盖 (single-writer
        // last-wins 契约)。 实际行为是 "process all matching function-parameter items" —
        // 等价 enhanced for + 2 个 continue (skip non-Element + skip non-function-parameter
        // Element)。 iterator 状态由 List iterator 决定, 两种写法一致 (锁定
        // [[v644-leftparser-commonfunction-flatten]])。
        for (Object obj : element.elements()) {
            if (!(obj instanceof Element)) {
                continue;
            }
            Element ele = (Element) obj;
            if (!ele.getName().equals("function-parameter")) {
                continue;
            }
            CommonFunctionParameter p = new CommonFunctionParameter();
            p.setName(ele.attributeValue("name"));
            p.setProperty(ele.attributeValue("property-name"));
            p.setPropertyLabel(ele.attributeValue("property-label"));
            for (Object object : ele.elements()) {
                if (object instanceof Element) {
                    Element e = (Element) object;
                    if (e.getName().equals("value")) {
                        p.setObjectParameter(this.valueParser.parse(e));
                    }
                }
            }
            part.setParameter(p);
        }

        return part;
    }

    private MethodLeftPart buildMethodLeftPart(Element element) {
        MethodLeftPart part = new MethodLeftPart();
        part.setBeanId(element.attributeValue("bean-name"));
        part.setBeanLabel(element.attributeValue("bean-label"));
        part.setMethodLabel(element.attributeValue("method-label"));
        part.setMethodName(element.attributeValue("method-name"));
        part.setParameters(this.parseParameters(element, this.valueParser));
        return part;
    }

    private FunctionLeftPart buildFunctionLeftPart(Element element) {
        FunctionLeftPart part = new FunctionLeftPart();
        part.setName(element.attributeValue("name"));
        part.setParameters(this.parseParameters(element, this.valueParser));
        return part;
    }

    private VariableLeftPart buildVariableLeftPart(Element element) {
        VariableLeftPart part = new VariableLeftPart();
        String variable = element.attributeValue("var");
        if (StringUtils.isNotEmpty(variable)) {
            part.setVariableName(variable);
        }

        String variableLabel = element.attributeValue("var-label");
        if (StringUtils.isNotEmpty(variableLabel)) {
            part.setVariableLabel(variableLabel);
        }

        String variableCategory = element.attributeValue("var-category");
        if (StringUtils.isNotEmpty(variableCategory)) {
            part.setVariableCategory(variableCategory);
        }

        String datatype = element.attributeValue("datatype");
        if (StringUtils.isNotEmpty(datatype)) {
            part.setDatatype(Datatype.valueOf(datatype));
        }

        return part;
    }

    public void setValueParser(ValueParser valueParser) {
        this.valueParser = valueParser;
    }

    public void setComplexArithmeticParser(ComplexArithmeticParser complexArithmeticParser) {
        this.complexArithmeticParser = complexArithmeticParser;
    }

    public void setSimpleArithmeticParser(SimpleArithmeticParser simpleArithmeticParser) {
        this.simpleArithmeticParser = simpleArithmeticParser;
    }

    public boolean support(String name) {
        return name.equals("left");
    }
}
