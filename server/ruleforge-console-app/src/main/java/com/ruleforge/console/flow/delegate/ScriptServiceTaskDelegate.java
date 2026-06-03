package com.ruleforge.console.flow.delegate;

import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleScriptContext;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class ScriptServiceTaskDelegate implements JavaDelegate {

    private static final String DEFAULT_SCRIPT_FORMAT = "groovy";

    private ScriptEngineManager scriptEngineManager;

    public ScriptServiceTaskDelegate() {
        this.scriptEngineManager = new ScriptEngineManager();
    }

    // Package-private for testing
    void setScriptEngineManager(ScriptEngineManager manager) {
        this.scriptEngineManager = manager;
    }

    @Override
    public void execute(DelegateExecution execution) {
        FlowNode flowNode = (FlowNode) execution.getCurrentFlowElement();
        String script = null;

        // Read script content from ScriptTask's script field
        if (flowNode instanceof org.flowable.bpmn.model.ScriptTask scriptTask) {
            script = scriptTask.getScript();
        }

        if (script == null || script.isEmpty()) {
            log.warn("No script content for service task: {}", execution.getCurrentActivityId());
            return;
        }

        // Determine script language
        String scriptFormat = DEFAULT_SCRIPT_FORMAT;
        if (flowNode instanceof org.flowable.bpmn.model.ScriptTask scriptTask) {
            String format = scriptTask.getScriptFormat();
            if (format != null && !format.isEmpty()) {
                scriptFormat = format;
            }
        }

        try {
            ScriptEngine engine = scriptEngineManager.getEngineByName(scriptFormat);
            if (engine == null) {
                throw new RuntimeException("No script engine found for: " + scriptFormat);
            }

            // Set up script context with process variables
            SimpleScriptContext context = new SimpleScriptContext();
            Map<String, Object> variables = new HashMap<>(execution.getVariables());
            context.setAttribute("variables", variables, ScriptContext.ENGINE_SCOPE);

            // parameters map for script to write results into
            Map<String, Object> parameters = new HashMap<>();
            context.setAttribute("parameters", parameters, ScriptContext.ENGINE_SCOPE);

            // Execute script
            engine.eval(script, context);

            // Write results back to process variables
            if (!parameters.isEmpty()) {
                execution.setVariables(parameters);
            }

        } catch (Exception e) {
            log.error("Script execution failed for task: {}", execution.getCurrentActivityId(), e);
            throw new RuntimeException(
                "Script execution failed for task: " + execution.getCurrentActivityId(), e);
        }
    }
}
