package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Start/End Event 节点执行器。noop,只记日志。
 */
@Slf4j
@Component
public class EventNodeExecutor implements NodeExecutor {

    @Override
    public String supportedType() {
        return "EVENT";
    }

    @Override
    public void execute(FlowNode node, FlowContext context) {
        switch (node.getType()) {
            case START_EVENT -> log.debug("[FLOW-START] {}", node.getName());
            case END_EVENT   -> log.debug("[FLOW-END]   {}", node.getName());
            default          -> log.debug("[FLOW-EVENT] {} type={}", node.getName(), node.getType());
        }
    }
}
