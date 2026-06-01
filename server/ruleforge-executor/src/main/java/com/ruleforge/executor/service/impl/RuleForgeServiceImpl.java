package com.ruleforge.executor.service.impl;

import com.ruleforge.Utils;
import com.ruleforge.executor.service.RuleForgeService;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.model.rule.RuleInfo;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.response.ExecutionResponse;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.runtime.service.KnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * @author fred
 * 2021/11/08 6:01 PM
 */
@Slf4j
@Service
public class RuleForgeServiceImpl implements RuleForgeService {

    @Override
    public Map<String, Object> doTest(String project, String packageId, String flowId, List<Map<String, Object>> mapList) {
        List<VariableCategory> variableCategories = mapToVariableCategories(mapList);
        Map<VariableCategory, Object> facts = new HashMap<>();
        for (VariableCategory vc : variableCategories) {
            String clazz = vc.getClazz();
            Object entity = null;
            if (vc.getName().equals(VariableCategory.PARAM_CATEGORY)) {
                entity = new HashMap<String, Object>();
            } else {
                entity = new GeneralEntity(clazz);
            }
            for (Variable var : vc.getVariables()) {
                buildObject(entity, var);
            }
            facts.put(vc, entity);
        }

        long start = System.currentTimeMillis();

        // 从Spring中获取KnowledgeService接口实例
        KnowledgeService service = (KnowledgeService) Utils.getApplicationContext().getBean(KnowledgeService.BEAN_ID);
        KnowledgePackage knowledgePackage = null;
        try {
            // 通过KnowledgeService接口获取指定的资源包
            knowledgePackage = service.getKnowledge(project + "/" + packageId);
        } catch (Exception e) {
            log.error("service.getKnowledge error", e);
        }
        if (knowledgePackage == null) {
            return null;
        }
        // 通过取到的KnowledgePackage对象创建KnowledgeSession对象
        KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(knowledgePackage);

        Map<String, Object> parameters = null;
        for (Object obj : facts.values()) {
            if (!(obj instanceof GeneralEntity) && (obj instanceof HashMap)) {
                parameters = (Map<String, Object>) obj;
            } else {
                session.insert(obj);
            }
        }
        ExecutionResponse response = null;
        if (StringUtils.hasText(flowId)) {
            // Flow execution is now handled by Flowable BPMN engine.
            // Executor only handles rule execution; flow must be started via Flowable.
            throw new RuntimeException("Flow execution requires Flowable engine. Deploy and start via console app.");
        } else {
            if (parameters == null) {
                response = session.fireRules();
            } else {
                response = session.fireRules(parameters);
            }
        }

        for (VariableCategory vc : facts.keySet()) {
            Object obj = facts.get(vc);
            if (obj == null) {
                continue;
            }
            if (obj instanceof Map && !(obj instanceof GeneralEntity)) {
                obj = session.getParameters();
            }
            for (Variable var : vc.getVariables()) {
                buildVariableValue(obj, var);
            }
        }
        long end = System.currentTimeMillis();
        long elapse = end - start;
        try {
            session.writeLogFile();
        } catch (Exception e) {
            log.error("session.writeLogFile error", e);
        }
        ExecutionResponseImpl res = (ExecutionResponseImpl) response;
        List<RuleInfo> firedRules = res.getFiredRules();
        List<RuleInfo> matchedRules = res.getMatchedRules();
        StringBuilder sb = new StringBuilder();
        sb.append("耗时：" + elapse + "ms");
        if (StringUtils.isEmpty(flowId)) {
            sb.append("，");
            sb.append("匹配的规则共" + matchedRules.size() + "个");
            if (matchedRules.size() > 0) {
                buildRulesName(matchedRules, sb);
            }
            sb.append("；");
            sb.append("触发的规则共" + firedRules.size() + "个");
            buildRulesName(firedRules, sb);
        }
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("info", sb.toString());
        resultMap.put("data", variableCategories);
        return resultMap;

    }

    private List<VariableCategory> mapToVariableCategories(List<Map<String, Object>> mapList) {
        List<VariableCategory> list = new ArrayList<>();
        for (Map<String, Object> map : mapList) {
            VariableCategory category = new VariableCategory();
            list.add(category);
            for (String key : map.keySet()) {
                switch (key) {
                    case "name":
                        category.setName((String) map.get(key));
                        break;
                    case "clazz":
                        category.setClazz((String) map.get(key));
                        break;
                    case "variables":
                        List<Map<String, Object>> variables = (List<Map<String, Object>>) map.get(key);
                        if (variables != null) {
                            for (Map<String, Object> m : variables) {
                                Variable var = new Variable();
                                category.addVariable(var);
                                for (String varName : m.keySet()) {
                                    switch (varName) {
                                        case "name":
                                            var.setName((String) m.get(varName));
                                            break;
                                        case "label":
                                            var.setLabel((String) m.get(varName));
                                            break;
                                        case "type":
                                            var.setType(Datatype.valueOf((String) m.get(varName)));
                                            break;
                                        case "defaultValue":
                                            var.setDefaultValue((String) m.get(varName));
                                            break;
                                    }
                                }
                            }
                        }
                        break;
                }
            }
        }
        return list;
    }

    private void buildRulesName(List<RuleInfo> firedRules, StringBuilder sb) {
        sb.append("：");
        int i = 0;
        for (RuleInfo rule : firedRules) {
            if (i > 0) {
                sb.append("，");
            }
            sb.append(rule.getName());
            i++;
        }
    }

    private void buildObject(Object obj, Variable var) {
        String name = var.getName();
        if (name.contains(".")) {
            instanceChildObject(obj, name);
        }
        String defaultValue = var.getDefaultValue();
        if (StringUtils.isEmpty(defaultValue)) {
            return;
        }
        Datatype type = var.getType();
        if (type.equals(Datatype.List)) {
            Utils.setObjectProperty(obj, name, buildList(defaultValue));
        } else if (type.equals(Datatype.Set)) {
            Utils.setObjectProperty(obj, name, buildSet(defaultValue));
        } else if (type.equals(Datatype.Map)) {
            return;
        } else {
            Object value = type.convert(defaultValue);
            Utils.setObjectProperty(obj, name, value);
        }
    }

    private List<GeneralEntity> buildList(String value) {
//        try {
//            List<GeneralEntity> result = new ArrayList<>();
//            ObjectMapper mapper = new ObjectMapper();
//            Map<String, Object> map = mapper.readValue(value, HashMap.class);
//            if (map.containsKey("rows")) {
//                List<Object> list = (List<Object>) map.get("rows");
//                for (Object obj : list) {
//                    if (obj instanceof Map) {
//                        GeneralEntity entity = new GeneralEntity((String) map.get("type"));
//                        entity.putAll((Map) obj);
//                        result.add(entity);
//                    }
//                }
//                return result;
//            } else {
        return null;
//            }
//        } catch (Exception e) {
//            throw new RuleException(e);
//        }
    }

    private Set<GeneralEntity> buildSet(String value) {
//        try {
//            Set<GeneralEntity> result = new HashSet<>();
//            ObjectMapper mapper = new ObjectMapper();
//            Map<String, Object> map = mapper.readValue(value, HashMap.class);
//            if (map.containsKey("rows")) {
//                List<Object> list = (List<Object>) map.get("rows");
//                for (Object obj : list) {
//                    if (obj instanceof Map) {
//                        GeneralEntity entity = new GeneralEntity((String) map.get("type"));
//                        entity.putAll((Map) obj);
//                        result.add(entity);
//                    }
//                }
//                return result;
//            } else {
        return null;
//            }
//        } catch (Exception e) {
//            throw new RuleException(e);
//        }
    }

    private void instanceChildObject(Object obj, String propertyName) {
//        int pointIndex = propertyName.indexOf(".");
//        if (pointIndex == -1) {
//            return;
//        }
//        String name = propertyName.substring(0, pointIndex);
//        propertyName = propertyName.substring(pointIndex + 1);
//        try {
//            Object instance = PropertyUtils.getProperty(obj, name);
//            if (instance != null) {
//                instanceChildObject(instance, propertyName);
//                return;
//            }
//            Object targetEntity = new GeneralEntity(name);
//            PropertyUtils.setProperty(obj, name, targetEntity);
//            instanceChildObject(targetEntity, propertyName);
//        } catch (Exception e) {
//            throw new RuleException(e);
//        }
    }

    private void buildVariableValue(Object object, Variable var) {
        String name = var.getName();
        Object value = Utils.getObjectProperty(object, name);
        if (value != null) {
            Datatype type = var.getType();
            if (type.equals(Datatype.List) || type.equals(Datatype.Set)) {
                //var.setDefaultValue(value.toString());
            } else {
                String str = type.convertObjectToString(value);
                var.setDefaultValue(str);
            }
        }
    }

}
