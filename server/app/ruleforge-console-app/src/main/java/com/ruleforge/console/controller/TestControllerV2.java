package com.ruleforge.console.controller;

import com.alibaba.fastjson2.JSONObject;
import com.ruleforge.Utils;
import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.builder.KnowledgeBuilder;
import com.ruleforge.builder.ResourceBase;
import com.ruleforge.console.repository.ExternalRepository;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.model.rete.CriteriaNode;
import com.ruleforge.model.rete.ObjectTypeNode;
import com.ruleforge.model.rete.ReteNode;
import com.ruleforge.model.rete.TerminalNode;
import com.ruleforge.model.rule.lhs.LeftPart;
import com.ruleforge.model.rule.lhs.VariableLeftPart;
import com.ruleforge.model.scorecard.runtime.ScoreRule;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.response.ExecutionResponse;
import com.ruleforge.console.model.FastTestDto;
import com.ruleforge.console.model.ResultDto;
import com.ruleforge.console.service.TestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Fred
 * @since 2025/8/26 11:33
 */
@Slf4j
@RestController
@RequestMapping("/${ruleforge.root.path}/test")
@RequiredArgsConstructor
public class TestControllerV2 {
    private final KnowledgeBuilder knowledgeBuilder;
    private final ExternalRepository externalRepository;
    private final TestService testService;

    @PostMapping("/variableCategories/load")
    public List<VariableCategory> loadForTestVariableCategories(@RequestBody FastTestDto fastTestDto) throws RuleException {
        JSONObject jsonObject = null;
        if (org.springframework.util.StringUtils.hasText(fastTestDto.getAppId())) {
            jsonObject = this.externalRepository.findDataByAppId(fastTestDto.getAppId(), fastTestDto.getProjectId());
        }

        KnowledgeBase knowledgeBase = buildKnowledgeBase(fastTestDto.getFilePath());
        List<ObjectTypeNode> objectTypeNodeList = knowledgeBase.getRete().getObjectTypeNodes();
        Set<String> categorySet = new HashSet<>();
        Set<String> labelSet = new HashSet<>();
        for (ObjectTypeNode objectTypeNode : objectTypeNodeList) {
            extractVariableInfo(objectTypeNode.getChildrenNodes(), categorySet, labelSet);
        }

        List<VariableCategory> vcs = knowledgeBase.getResourceLibrary().getVariableCategories();
        List<VariableCategory> vcsReal = new ArrayList<>();
        for (VariableCategory variableCategory : vcs) {
            if (categorySet.contains(variableCategory.getName())) {
                JSONObject vcJsonObject = null;
                if (jsonObject != null && jsonObject.containsKey(variableCategory.getClazz())) {
                    vcJsonObject = (JSONObject) jsonObject.get(variableCategory.getClazz());
                }
                List<Variable> variableList = new ArrayList<>();
                for (Variable variable : variableCategory.getVariables()) {
                    if (labelSet.contains(variable.getLabel())) {
                        if (vcJsonObject != null && vcJsonObject.containsKey(variable.getName())) {
                            variable.setDefaultValue((String) vcJsonObject.get(variable.getName()));
                        }
                        variableList.add(variable);
                    }
                }
                if (!variableList.isEmpty()) {
                    variableCategory.setVariables(variableList);
                    vcsReal.add(variableCategory);
                }
            }
        }

        return vcsReal;
    }

    /**
     * 递归提取变量信息
     *
     * @param reteNodes   ReteNode列表
     * @param categorySet 变量分类集合
     * @param labelSet    变量标签集合
     */
    private void extractVariableInfo(List<ReteNode> reteNodes, Set<String> categorySet, Set<String> labelSet) {
        if (reteNodes == null || reteNodes.isEmpty()) {
            return;
        }

        for (ReteNode reteNode : reteNodes) {
            if (reteNode instanceof CriteriaNode) {
                CriteriaNode criteriaNode = (CriteriaNode) reteNode;
                LeftPart leftPart = criteriaNode.getCriteria().getLeft().getLeftPart();
                if (leftPart instanceof VariableLeftPart) {
                    VariableLeftPart variableLeftPart = (VariableLeftPart) leftPart;
                    categorySet.add(variableLeftPart.getVariableCategory());
                    labelSet.add(variableLeftPart.getVariableLabel());
                }

                // 递归处理子节点
                extractVariableInfo(criteriaNode.getChildrenNodes(), categorySet, labelSet);
            } else if (reteNode instanceof TerminalNode) {
                TerminalNode terminalNode = (TerminalNode) reteNode;
                if (terminalNode.getRule() instanceof ScoreRule) {
                    ScoreRule scoreRule = (ScoreRule) terminalNode.getRule();
                    extractVariableInfo(scoreRule.getKnowledgePackageWrapper().getAllNodes(), categorySet, labelSet);
                }
            }
        }
    }

    @PostMapping("/data/appId")
    public ResultDto<JSONObject> findDataByAppId(@RequestParam String appId,
                                                 @RequestParam String projectId) {
        return new ResultDto<>(this.externalRepository.findDataByAppId(appId, projectId));
    }

    @PostMapping("/fast")
    public ResultDto<List<VariableCategory>> speedTest(@RequestBody FastTestDto fastTestDto) {
        List<Map<String, Object>> list = fastTestDto.getData();
        List<VariableCategory> variableCategories = mapToVariableCategories(list);
        Map<VariableCategory, Object> facts = new HashMap<>();
        Set<String> categorySet = new HashSet<>();
        for (VariableCategory vc : variableCategories) {
            String clazz = vc.getClazz();
            categorySet.add(clazz);
            Object entity;
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
        KnowledgeBase knowledgeBase = buildKnowledgeBase(fastTestDto.getFilePath());
        KnowledgePackage knowledgePackage = knowledgeBase.getKnowledgePackage();
        for (VariableCategory vc : knowledgeBase.getResourceLibrary().getVariableCategories()) {
            if (!categorySet.contains(vc.getClazz())) {
                Object entity;
                if (vc.getName().equals(VariableCategory.PARAM_CATEGORY)) {
                    entity = new HashMap<String, Object>();
                } else {
                    entity = new GeneralEntity(vc.getClazz());
                }
                for (Variable var : vc.getVariables()) {
                    buildObject(entity, var);
                }
                facts.put(vc, entity);
            }
        }
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
        String flowId = fastTestDto.getFlowId();
        if (StringUtils.isNotEmpty(flowId)) {
            throw new RuleException("Flow execution via V2 test endpoint is no longer supported. Use Flowable engine.");
        } else if (parameters == null) {
            response = session.fireRules();
        } else {
            response = session.fireRules(parameters);
        }

        List<VariableCategory> resultVc = new ArrayList<>();
        for (VariableCategory vc : facts.keySet()) {
            Object obj = facts.get(vc);
            if (obj == null || (obj instanceof GeneralEntity && ((GeneralEntity) obj).isEmpty())) {
                continue;
            }
            if (obj instanceof Map && !(obj instanceof GeneralEntity)) {
                obj = session.getParameters();
            }
            List<Variable> variablesToRemove = new ArrayList<>();
            for (Variable var : vc.getVariables()) {
                buildVariableValue(obj, var);
                if (!org.springframework.util.StringUtils.hasText(var.getDefaultValue())) {
                    variablesToRemove.add(var);
                }
            }
            vc.getVariables().removeAll(variablesToRemove);
            if (!vc.getVariables().isEmpty()) {
                resultVc.add(vc);
            }
        }
        long end = System.currentTimeMillis();
        long elapse = end - start;

        StringBuffer sb = new StringBuffer();
        sb.append("耗时：").append(elapse).append("ms");

        return new ResultDto<>(resultVc, true, sb.toString());
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

    private void buildObject(Object obj, Variable var) {
        String name = var.getName();
        if (name.contains(".")) {
            instanceChildObject(obj, name);
        }
        String defaultValue = var.getDefaultValue();
        if (StringUtils.isBlank(defaultValue)) {
            return;
        }
        Datatype type = var.getType();
        if (type.equals(Datatype.List)) {
            Utils.setObjectProperty(obj, name, buildList(defaultValue));
        } else if (type.equals(Datatype.Set)) {
            Utils.setObjectProperty(obj, name, buildSet(defaultValue));
        } else if (type.equals(Datatype.Map)) {
        } else {
            Object value = type.convert(defaultValue);
            Utils.setObjectProperty(obj, name, value);
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

    private List<GeneralEntity> buildList(String value) {
        try {
            List<GeneralEntity> result = new ArrayList<>();
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> map = mapper.readValue(value, HashMap.class);
            if (map.containsKey("rows")) {
                List<Object> list = (List<Object>) map.get("rows");
                for (Object obj : list) {
                    if (obj instanceof Map) {
                        GeneralEntity entity = new GeneralEntity((String) map.get("type"));
                        entity.putAll((Map) obj);
                        result.add(entity);
                    }
                }
                return result;
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new RuleException(e);
        }
    }

    private Set<GeneralEntity> buildSet(String value) {
        try {
            Set<GeneralEntity> result = new HashSet<>();
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> map = mapper.readValue(value, HashMap.class);
            if (map.containsKey("rows")) {
                List<Object> list = (List<Object>) map.get("rows");
                for (Object obj : list) {
                    if (obj instanceof Map) {
                        GeneralEntity entity = new GeneralEntity((String) map.get("type"));
                        entity.putAll((Map) obj);
                        result.add(entity);
                    }
                }
                return result;
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new RuleException(e);
        }
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

    private KnowledgeBase buildKnowledgeBase(String files) throws RuleException {
        files = Utils.decodeURL(files);
        ResourceBase resourceBase = knowledgeBuilder.newResourceBase();
        String[] paths = files.split(";");
        for (String path : paths) {
            String[] subPaths = path.split(",");
            path = subPaths[0];
            String version = null;
            if (subPaths.length > 1) {
                version = subPaths[1];
            }
            resourceBase.addResource(path, version, true);
        }
        return this.knowledgeBuilder.buildKnowledgeBase(resourceBase);
    }

}
