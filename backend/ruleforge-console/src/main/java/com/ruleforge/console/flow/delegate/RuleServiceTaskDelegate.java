package com.ruleforge.console.flow.delegate;

import com.ruleforge.Utils;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.response.ExecutionResponse;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.runtime.service.KnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class RuleServiceTaskDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        String file = execution.getCurrentFlowElement().getAttributeValue(
                "http://ruleforge.com/schema", "file");
        String project = execution.getCurrentFlowElement().getAttributeValue(
                "http://ruleforge.com/schema", "project");

        if (file == null || file.isEmpty()) {
            log.warn("No rule file specified for service task: {}", execution.getCurrentActivityId());
            return;
        }

        KnowledgeService service = (KnowledgeService) Utils.getApplicationContext().getBean(KnowledgeService.BEAN_ID);
        String resourceKey = buildResourceKey(project, file);
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

    private String buildResourceKey(String project, String file) {
        if (project != null && !project.isEmpty()) {
            return project + "/" + file;
        }
        return file;
    }

    private Map<String, Object> insertFacts(KnowledgeSession session, Map<String, Object> variables) {
        Map<String, Object> parameters = null;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                parameters = (Map<String, Object>) value;
            } else if (value != null) {
                session.insert(value);
            }
        }
        return parameters;
    }

    private Map<String, Object> extractResults(KnowledgeSession session, Map<String, Object> originalVars) {
        Map<String, Object> results = new HashMap<>();
        Map<String, Object> params = session.getParameters();
        if (params != null) {
            results.putAll(params);
        }
        return results;
    }
}
