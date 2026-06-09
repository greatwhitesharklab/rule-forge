package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;
import java.util.Map;

/**
 * Script 节点执行器(替代原 ScriptServiceTaskDelegate)。
 * <p>
 * JSR-223 引擎,默认 groovy。bindings 暴露两个变量:variables(流程变量)+ parameters(脚本写结果)。
 * parameters 非空时,merge 回 ctx.vars。
 */
@Slf4j
@Component
public class ScriptNodeExecutor implements NodeExecutor {

    private ScriptEngineManager engineManager = new ScriptEngineManager();

    /** Package-private for testing — 注入 mock engine manager。 */
    void setEngineManager(ScriptEngineManager engineManager) {
        this.engineManager = engineManager;
    }

    @Override
    public String supportedType() {
        return "SCRIPT_TASK";
    }

    @Override
    public void execute(FlowNode node, FlowContext context) throws Exception {
        String script = node.getScriptText();
        if (script == null || script.isBlank()) {
            log.debug("ScriptTask {} has no script body, skip", node.getNodeId());
            return;
        }

        String format = node.getScriptFormat() == null ? "groovy" : node.getScriptFormat();
        ScriptEngine engine = engineManager.getEngineByName(format);
        if (engine == null) {
            throw new FlowExecutionException(
                "No script engine for format=" + format + " at node " + node.getNodeId());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = new java.util.HashMap<>();
        Bindings bindings = new SimpleBindings();
        bindings.put("variables", context.getVars());
        bindings.put("parameters", parameters);

        try {
            engine.eval(script, bindings);
        } catch (Exception e) {
            throw new FlowExecutionException(
                "Script execution failed for node " + node.getNodeId() + ": " + e.getMessage(), e);
        }

        if (!parameters.isEmpty()) {
            context.getVars().putAll(parameters);
        }
    }
}
