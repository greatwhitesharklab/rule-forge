package com.ruleforge.console.service.impl.runner;

import com.ruleforge.Utils;
import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.console.model.TestRuntimeErrorDto;
import com.ruleforge.console.util.ExcelUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.PropertyUtils;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@AllArgsConstructor
public class TestPackageRunnable implements Runnable {

    private final KnowledgeBase knowledgeBase;
    private final String flowId;
    private final List<Map<String, Object>> data;
    @Getter
    private final Map<String, Integer> flowMap = new HashMap<>();
    @Getter
    private final List<TestRuntimeErrorDto> errorList = new ArrayList<>();
//    private final ExecutorService testExecutorService = Executors.newCachedThreadPool();

    @Override
    public void run() {
    }

    private void buildObject(Object obj, Map<String, Object> map, List<Variable> variables) {
        for (String name : map.keySet()) {
//            name = name.replaceAll("-", "\\.");
            if (name.contains(".")) {
                instanceChildObject(obj, name);
            }
            Object value = map.get(name);
            Variable var = null;
            for (Variable variable : variables) {
                if (name.equals(variable.getLabel()) || name.equals(variable.getName())) {
                    var = variable;
                    break;
                }
            }
            if (var == null) {
                throw new RuleException("Variable [" + name + "] not exist.");
            }
            Datatype type = var.getType();
            if (type.equals(Datatype.List) || type.equals(Datatype.Set) || type.equals(Datatype.Map)) {
                continue;
            }
            if (!org.springframework.util.StringUtils.isEmpty(value)) {
                value = type.convert(value);
            } else {
                value = null;
            }
            Utils.setObjectProperty(obj, var.getName(), value);
        }
    }

    private void instanceChildObject(Object obj, String propertyName) {
        int pointIndex = propertyName.indexOf(".");
        if (pointIndex == -1) {
            return;
        }
        String name = propertyName.substring(0, pointIndex);
        propertyName = propertyName.substring(pointIndex + 1);
        try {
            Object instance = PropertyUtils.getProperty(obj, name);
            if (instance != null) {
                instanceChildObject(instance, propertyName);
                return;
            }
            Object targetEntity = new GeneralEntity(name);
            PropertyUtils.setProperty(obj, name, targetEntity);
            instanceChildObject(targetEntity, propertyName);
        } catch (Exception e) {
            throw new RuleException(e);
        }
    }

}
