package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * V5.34 A3 — {@code <bpmn:compensateIntermediateThrowEvent ruleforge:attachedToRef="..."/>} 节点执行器。
 *
 * <p>Mirror Rust V5.31 P0 {@code compensation_intermediate.rs} 契约:
 * v0 纯 no-op,只 log。handler 注册在 {@link CompensationThrow} 时遍历
 * {@code def.attachedCompensations}(由 {@link com.ruleforge.decision.flow.parser.BpmnXmlParser} 解析时
 * 从 {@code ruleforge:attachedToRef} 倒推建立索引)。
 *
 * <p>路过这个节点时不写 stack / vars / token。
 */
@Slf4j
@Component
public class CompensationIntermediateExecutor implements NodeExecutor {

    @Override
    public String supportedType() {
        return "COMPENSATION_INTERMEDIATE";
    }

    @Override
    public void execute(FlowNode node, FlowContext context) {
        String attachedTo = node.attr("ruleforge", "attachedToRef");
        log.debug("[COMP-INTERMEDIATE] flowRunId={} nodeId={} attachedToRef={} (v0: no-op)",
            context.getFlowRunId(), node.getNodeId(), attachedTo);
    }
}
