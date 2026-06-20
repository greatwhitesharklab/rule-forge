package com.ruleforge.model.rete;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.ruleforge.Configure;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rete.jsondeserializer.CommonFunctionValueDeserializer;
import com.ruleforge.model.rete.jsondeserializer.ConstantValueDeserializer;
import com.ruleforge.model.rete.jsondeserializer.InputValueDeserializer;
import com.ruleforge.model.rete.jsondeserializer.MethodValueDeserializer;
import com.ruleforge.model.rete.jsondeserializer.NameReferenceValueDeserializer;
import com.ruleforge.model.rete.jsondeserializer.ParameterValueDeserializer;
import com.ruleforge.model.rete.jsondeserializer.ParenValueDeserializer;
import com.ruleforge.model.rete.jsondeserializer.ValueDeserializer;
import com.ruleforge.model.rete.jsondeserializer.VariableCategoryValueDeserializer;
import com.ruleforge.model.rete.jsondeserializer.VariableValueDeserializer;
import com.ruleforge.model.rule.ArithmeticType;
import com.ruleforge.model.rule.ComplexArithmetic;
import com.ruleforge.model.rule.Parameter;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.ValueType;
import com.ruleforge.model.rule.lhs.CommonFunctionParameter;
import com.ruleforge.engine.KnowledgePackageWrapper;

/**
 * @author Jacky.gao
 * 2015年3月6日
 */
public class JsonUtils {
    @Getter
    private static List<ValueDeserializer> valueDeserializers;

    static {
        valueDeserializers = new ArrayList<>();
        valueDeserializers.add(new ConstantValueDeserializer());
        valueDeserializers.add(new InputValueDeserializer());
        valueDeserializers.add(new ParameterValueDeserializer());
        valueDeserializers.add(new MethodValueDeserializer());
        valueDeserializers.add(new VariableCategoryValueDeserializer());
        valueDeserializers.add(new VariableValueDeserializer());
        valueDeserializers.add(new CommonFunctionValueDeserializer());
        valueDeserializers.add(new ParenValueDeserializer());
        valueDeserializers.add(new NameReferenceValueDeserializer());
    }

    public static String getJsonValue(JsonNode node, String propName) {
        if (node.get(propName) != null) {
            return node.get(propName).asText();
        }
        return null;
    }

    public static ComplexArithmetic parseComplexArithmetic(JsonNode node) {
        JsonNode arithmeticNode = node.get("arithmetic");
        if (arithmeticNode == null) {
            return null;
        }
        ComplexArithmetic arith = new ComplexArithmetic();
        arith.setType(ArithmeticType.valueOf(getJsonValue(arithmeticNode, "type")));
        arith.setValue(parseValue(arithmeticNode));
        return arith;
    }

    public static List<Parameter> parseParameters(JsonNode node) {
        JsonNode parametersNode = node.get("parameters");
        if (parametersNode == null) {
            return null;
        }
        // V6.9.12 — V5.96 skip: Iterator + while 状态机 → enhanced for。
        // 同时删 L88-89 死代码: `if (valueTypeText != null) { param.setValue(parseValue(...)); }`
        // 立即被下方无条件 `param.setValue(parseValue(...))` 覆盖, 经典 dead-then-overwrite
        // 反编译 artifact。 L91 单独保留, 行为 100% 等价 (parseValue 内部已处理 value==null)。
        List<Parameter> parameters = new ArrayList<Parameter>();
        for (JsonNode parameterNode : parametersNode) {
            Parameter param = new Parameter();
            param.setName(getJsonValue(parameterNode, "name"));
            String type = getJsonValue(parameterNode, "type");
            if (type != null) {
                param.setType(Datatype.valueOf(type));
            }
            param.setValue(parseValue(parameterNode));
            parameters.add(param);
        }
        return parameters;
    }

    public static Value parseValueNode(JsonNode valueNode) {
        Value value = null;
        ValueType valueType = ValueType.valueOf(getJsonValue(valueNode, "valueType"));
        for (ValueDeserializer des : valueDeserializers) {
            if (des.support(valueType)) {
                value = des.deserialize(valueNode);
                break;
            }
        }
        return value;
    }

    public static KnowledgePackageWrapper parseKnowledgePackageWrapper(String content) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.setDateFormat(new SimpleDateFormat(Configure.getDateFormat()));
            KnowledgePackageWrapper wrapper = mapper.readValue(content, KnowledgePackageWrapper.class);
            wrapper.buildDeserialize();
            return wrapper;
        } catch (Exception ex) {
            throw new RuleException(ex);
        }
    }

    public static CommonFunctionParameter parseCommonFunctionParameter(JsonNode node) {
        JsonNode parameterNode = node.get("parameter");
        if (parameterNode == null) {
            return null;
        }
        CommonFunctionParameter parameter = new CommonFunctionParameter();
        parameter.setName(JsonUtils.getJsonValue(parameterNode, "name"));
        parameter.setProperty(JsonUtils.getJsonValue(parameterNode, "property"));
        parameter.setPropertyLabel(JsonUtils.getJsonValue(parameterNode, "propertyLabel"));
        parameter.setObjectParameter(JsonUtils.parseValueNode(parameterNode.get("objectParameter")));
        return parameter;
    }

    public static Value parseValue(JsonNode node) {
        JsonNode valueNode = node.get("value");
        if (valueNode == null) {
            return null;
        }
        return parseValueNode(valueNode);
    }

}
