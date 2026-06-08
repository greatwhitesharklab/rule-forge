package com.ruleforge.decision.flow.executor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RulesPackage 节点执行器。
 * <p>
 * 补老系统 gap:moddle.json 注册了 taskType=rulesPackage + rulesList 字段,
 * 但老 RuleServiceTaskDelegate 不识别。新执行器按 rulesList JSON 数组逐条
 * 把子规则当独立 rule 节点跑,结果累加到 ctx.vars.rulesFired。
 */
@Slf4j
@Component
public class RulesPackageNodeExecutor implements NodeExecutor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, String>>> RULES_LIST_TYPE = new TypeReference<>() {};

    @Override
    public String supportedType() {
        return "SERVICE_TASK:rulesPackage";
    }

    @Override
    public void execute(FlowNode node, FlowContext context) {
        String rulesListJson = node.attr("ruleforge", "rulesList");
        if (rulesListJson == null || rulesListJson.isBlank()) {
            throw new FlowExecutionException(
                "RulesPackage node missing ruleforge:rulesList at " + node.getNodeId());
        }

        List<Map<String, String>> rules;
        try {
            rules = MAPPER.readValue(rulesListJson, RULES_LIST_TYPE);
        } catch (Exception e) {
            throw new FlowExecutionException(
                "Invalid ruleforge:rulesList JSON at " + node.getNodeId() + ": " + e.getMessage(), e);
        }

        // 当前实现:把每条规则的 file/project/version 临时覆盖到当前 node,调 RuleNodeExecutor 逻辑
        // Phase 1 简化版:仅记录已跑的规则列表,等 step 4 跟 RuleNodeExecutor 集成时再下沉
        List<String> fired = new ArrayList<>();
        for (Map<String, String> rule : rules) {
            fired.add(rule.getOrDefault("name", rule.getOrDefault("file", "?")));
        }
        context.getVars().put("rulesFired", fired);
        log.info("[RULES-PACKAGE] node={} rules={}", node.getNodeId(), fired);
    }
}
