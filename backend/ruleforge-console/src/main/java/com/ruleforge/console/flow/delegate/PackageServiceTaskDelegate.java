package com.ruleforge.console.flow.delegate;

import com.ruleforge.Utils;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.response.ExecutionResponse;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.runtime.service.KnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class PackageServiceTaskDelegate implements JavaDelegate {

    private static final String RF_NS = "http://ruleforge.com/schema";

    @Override
    public void execute(DelegateExecution execution) {
        String packageId = execution.getCurrentFlowElement().getAttributeValue(RF_NS, "packageId");
        String project = execution.getCurrentFlowElement().getAttributeValue(RF_NS, "project");

        if (packageId == null || packageId.isEmpty()) {
            log.warn("No package ID specified for service task: {}", execution.getCurrentActivityId());
            return;
        }

        // Build resource key
        String resourceKey;
        if (project != null && !project.isEmpty()) {
            resourceKey = project + "/" + packageId;
        } else {
            resourceKey = packageId;
        }

        // Load knowledge package via KnowledgeService
        KnowledgeService service;
        try {
            ApplicationContext ctx = Utils.getApplicationContext();
            service = (KnowledgeService) ctx.getBean(KnowledgeService.BEAN_ID);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get KnowledgeService for package: " + resourceKey, e);
        }

        KnowledgePackage knowledgePackage;
        try {
            knowledgePackage = service.getKnowledge(resourceKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load knowledge package: " + resourceKey, e);
        }

        if (knowledgePackage == null) {
            log.error("Knowledge package not found: {}", resourceKey);
            return;
        }

        // Create session and execute rules
        KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(knowledgePackage);

        // Insert process variables as facts
        Map<String, Object> variables = execution.getVariables();
        Map<String, Object> parameters = insertFacts(session, variables);

        ExecutionResponse response;
        if (parameters != null) {
            response = session.fireRules(parameters);
        } else {
            response = session.fireRules();
        }

        // Write results back to process variables
        Map<String, Object> resultVariables = extractResults(session, variables);
        execution.setVariables(resultVariables);

        // Store execution info
        ExecutionResponseImpl res = (ExecutionResponseImpl) response;
        execution.setVariable("_firedRules", res.getFiredRules().size());
        execution.setVariable("_matchedRules", res.getMatchedRules().size());

        try {
            session.writeLogFile();
        } catch (Exception e) {
            log.error("Failed to write log file", e);
        }
    }

    private Map<String, Object> insertFacts(KnowledgeSession session, Map<String, Object> variables) {
        Map<String, Object> parameters = null;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof GeneralEntity) {
                session.insert(value);
            } else if (value instanceof Map) {
                parameters = (Map<String, Object>) value;
            } else if (value != null) {
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
            if (value instanceof Map entityMap) {
                for (Map.Entry<String, Object> prop : ((Map<String, Object>) entityMap).entrySet()) {
                    if (prop.getValue() != null) {
                        results.put(entry.getKey() + "_" + prop.getKey(), prop.getValue());
                    }
                }
            }
        }
        Map<String, Object> params = session.getParameters();
        if (params != null) {
            results.putAll(params);
        }
        return results;
    }
}
