package com.ruleforge.decision.flow.executor;

import com.ruleforge.Utils;
import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.builder.KnowledgeBuilder;
import com.ruleforge.builder.ResourceBase;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.response.ExecutionResponse;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.runtime.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Rule 节点执行器(替代原 RuleServiceTaskDelegate)。
 *
 * 关键事实(V5.18 修法保留,搬到这里):
 * - OutputModel 是 POJO,规则引擎 ObjectTypeActivity 字符串模式只识别 GeneralEntity,
 *   所以走"包成 GeneralEntity + session.fireRules + BeanUtils.populate 写回 POJO"的路
 * - applicant / order 走 hybrid 路径时是 Map(Flowable 序列化要求),要包成 transient entity
 * - 其它 Map 当 fireRules parameters
 *
 * flowable:delegateExpression / flowable:class 的老 BPMN 不再支持(step 1 plan 决定)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleNodeExecutor implements NodeExecutor {

    private final KnowledgeBuilder knowledgeBuilder;

    @Override
    public String supportedType() {
        return "SERVICE_TASK:rule";
    }

    @Override
    public void execute(FlowNode node, FlowContext context) {
        String file = node.attr("ruleforge", "file");
        String project = node.attr("ruleforge", "project");
        String version = node.attr("ruleforge", "version");

        if (file == null || file.isEmpty()) {
            throw new FlowExecutionException("Rule node missing ruleforge:file at " + node.getNodeId());
        }

        KnowledgePackage knowledgePackage = loadKnowledge(file, project, version, node.getNodeId());

        KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(knowledgePackage);

        // 把 ctx.vars 当流程变量
        Map<String, Object> variables = context.getVars();
        Map<String, Object> parameters = insertFacts(session, variables);

        // V5.18 修法:OutputModel var-assign 走"包成 entity + fireRules + BeanUtils.populate 写回"
        if (context.getOutputModel() != null) {
            try {
                GeneralEntity outputEntity = wrapOutputModelAsEntity(context.getOutputModel());
                session.insert(outputEntity);
                session.fireRules();
                syncOutputModelFromEntity(context.getOutputModel(), outputEntity);
            } catch (Exception e) {
                log.warn("[RULE-NODE] OutputModel mutation 非致命异常: {}", e.getMessage());
            }
        }

        ExecutionResponse response;
        try {
            response = parameters != null ? session.fireRules(parameters) : session.fireRules();
        } catch (com.ruleforge.exception.RuleException e) {
            log.warn("[RULE-NODE] 规则执行出现非致命异常(本地 OutputModel 已处理): {}", e.getMessage());
            ExecutionResponseImpl empty = new ExecutionResponseImpl();
            empty.setFiredRules(new java.util.ArrayList<>());
            response = empty;
        }

        // 写回 ctx.vars
        Map<String, Object> results = extractResults(session, variables);
        context.getVars().putAll(results);

        ExecutionResponseImpl res = (ExecutionResponseImpl) response;
        context.getVars().put("_firedRules", res.getFiredRules().size());
        context.getVars().put("_matchedRules", res.getMatchedRules().size());

        try {
            session.writeLogFile();
        } catch (Exception e) {
            log.error("Failed to write log file", e);
        }
    }

    private KnowledgePackage loadKnowledge(String file, String project, String version, String nodeId) {
        try {
            if (project != null && !project.isEmpty() && !file.startsWith("/")) {
                KnowledgeService service = (KnowledgeService) Utils.getApplicationContext()
                    .getBean(KnowledgeService.BEAN_ID);
                String resourceKey = project + "/" + file;
                try {
                    return service.getKnowledge(resourceKey);
                } catch (Exception e) {
                    log.warn("Failed to load knowledge package: {}, building from file instead", resourceKey);
                }
            }
            ResourceBase resourceBase = knowledgeBuilder.newResourceBase();
            String ver = (version != null && !"LATEST".equals(version)) ? version : null;
            resourceBase.addResource(file, ver, true);
            KnowledgeBase knowledgeBase = knowledgeBuilder.buildKnowledgeBase(resourceBase);
            return knowledgeBase.getKnowledgePackage();
        } catch (Exception e) {
            throw new FlowExecutionException(
                "Failed to load knowledge package for node " + nodeId + ": " + e.getMessage(), e);
        }
    }

    private Map<String, Object> insertFacts(KnowledgeSession session, Map<String, Object> variables) {
        Map<String, Object> parameters = null;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) continue;
            if (value instanceof GeneralEntity) {
                session.insert(value);
            } else if (value instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapValue = (Map<String, Object>) value;
                if ("applicant".equals(key)) {
                    session.insert(asTransientEntity(mapValue, "com.ruleforge.decision.model.ApplicantModel"));
                } else if ("order".equals(key)) {
                    session.insert(asTransientEntity(mapValue, "com.ruleforge.decision.model.OrderModel"));
                } else {
                    parameters = mapValue;
                }
            } else {
                session.insert(value);
            }
        }
        return parameters;
    }

    private GeneralEntity asTransientEntity(Map<String, Object> facts, String targetClass) {
        GeneralEntity entity = new GeneralEntity(targetClass);
        entity.putAll(facts);
        return entity;
    }

    private Map<String, Object> extractResults(KnowledgeSession session, Map<String, Object> originalVars) {
        Map<String, Object> results = new HashMap<>();
        for (Map.Entry<String, Object> entry : originalVars.entrySet()) {
            Object value = entry.getValue();
            if (value == null) continue;
            results.put(entry.getKey(), value);
            if (value instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> entityMap = (Map<String, Object>) value;
                for (Map.Entry<String, Object> prop : entityMap.entrySet()) {
                    if (prop.getValue() != null) {
                        results.put(entry.getKey() + "_" + prop.getKey(), prop.getValue());
                    }
                }
            }
        }
        Map<String, Object> params = session.getParameters();
        if (params != null) results.putAll(params);
        return results;
    }

    /**
     * V5.18 修法(任务 #213):业务侧 POJO → GeneralEntity。
     * Apache Commons BeanUtils.describe 反射读所有 getter,跳过 "class" key。
     * 用 Object 而非 OutputModel 类型(模块边界:OutputModel 在 executor-app,lib 不依赖)
     */
    @SuppressWarnings("unchecked")
    private GeneralEntity wrapOutputModelAsEntity(Object outputModel) throws Exception {
        GeneralEntity entity = new GeneralEntity(outputModel.getClass().getName());
        java.util.Map<String, String> snapshot = org.apache.commons.beanutils.BeanUtils.describe(outputModel);
        for (java.util.Map.Entry<String, String> e : snapshot.entrySet()) {
            if ("class".equals(e.getKey())) continue;
            entity.put(e.getKey(), e.getValue());
        }
        return entity;
    }

    /**
     * V5.18 修法(任务 #213):GeneralEntity → 业务侧 POJO。
     * BeanUtils.populate 反射调 setter。
     */
    private void syncOutputModelFromEntity(Object outputModel, GeneralEntity entity) throws Exception {
        org.apache.commons.beanutils.BeanUtils.populate(outputModel, entity);
    }
}
