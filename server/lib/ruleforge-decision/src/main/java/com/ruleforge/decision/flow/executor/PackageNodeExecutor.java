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
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.runtime.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Package 节点执行器(替代原 PackageServiceTaskDelegate)。
 * <p>
 * 跟 RuleNodeExecutor 类似,但不识别 applicant/order 特殊 key,
 * 所有 Map 都当 fireRules parameters。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PackageNodeExecutor implements NodeExecutor {

    private final KnowledgeBuilder knowledgeBuilder;

    @Override
    public String supportedType() {
        return "SERVICE_TASK:package";
    }

    @Override
    public void execute(FlowNode node, FlowContext context) {
        String packageId = node.attr("ruleforge", "packageId");
        String project = node.attr("ruleforge", "project");

        if (packageId == null) {
            throw new FlowExecutionException(
                "Package node missing ruleforge:packageId at " + node.getNodeId());
        }

        String resourceKey = (project != null && !project.isEmpty())
            ? project + "/" + packageId
            : packageId;

        KnowledgePackage knowledgePackage;
        try {
            KnowledgeService service = (KnowledgeService) Utils.getApplicationContext()
                .getBean(KnowledgeService.BEAN_ID);
            knowledgePackage = service.getKnowledge(resourceKey);
        } catch (Exception e) {
            throw new FlowExecutionException(
                "Failed to load knowledge package: " + resourceKey, e);
        }

        KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(knowledgePackage);

        Map<String, Object> parameters = insertFacts(session, context.getVars());

        ExecutionResponseImpl response;
        try {
            response = (ExecutionResponseImpl) (parameters != null
                ? session.fireRules(parameters) : session.fireRules());
        } catch (com.ruleforge.exception.RuleException e) {
            log.warn("[PACKAGE-NODE] 规则执行非致命异常: {}", e.getMessage());
            response = new ExecutionResponseImpl();
            response.setFiredRules(new java.util.ArrayList<>());
        }

        Map<String, Object> results = extractResults(session, context.getVars());
        context.getVars().putAll(results);
        context.getVars().put("_firedRules", response.getFiredRules().size());
        context.getVars().put("_matchedRules", response.getMatchedRules().size());
    }

    private Map<String, Object> insertFacts(KnowledgeSession session, Map<String, Object> variables) {
        Map<String, Object> parameters = null;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            Object value = entry.getValue();
            if (value == null) continue;
            if (value instanceof GeneralEntity) {
                session.insert(value);
            } else if (value instanceof Map<?, ?>) {
                parameters = (Map<String, Object>) value;
            } else {
                session.insert(value);
            }
        }
        return parameters;
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
}
