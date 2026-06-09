package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.AsyncNodeSuspendException;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * UserTask 节点执行器 — 人工决策介入。
 *
 * BPMN 表达:
 *   <bpmn:userTask id="manualReview"
 *                  ruleforge:decisionType="binary"
 *                  ruleforge:decisionField="approved">
 *     <bpmn:outgoing>flow_approve</bpmn:outgoing>
 *     <bpmn:outgoing>flow_reject</bpmn:outgoing>
 *   </bpmn:userTask>
 *   <bpmn:sequenceFlow id="flow_approve" ... ruleforge:decisionValue="1"/>
 *   <bpmn:sequenceFlow id="flow_reject"  ... ruleforge:decisionValue="0"/>
 *
 * 行为:抛 AsyncNodeSuspendException(waitType=USER_TASK, nextRetryAt=null 即无限等)
 * 业务系统调 executor /flow/decision?flowRunId=xxx&decision=0|1 提交决策
 * 决策值写到 ctx.vars[decisionField],FlowNodeRunner.nextNode() 按 decisionValue 路由
 */
@Slf4j
@Component
public class UserTaskNodeExecutor implements NodeExecutor {

    @Override
    public String supportedType() {
        return "USER_TASK";
    }

    @Override
    public void execute(FlowNode node, FlowContext context) {
        String decisionType = node.attr("ruleforge", "decisionType");
        String decisionField = node.attr("ruleforge", "decisionField");

        if (!"binary".equals(decisionType)) {
            throw new FlowExecutionException(
                "Unsupported userTask decisionType=" + decisionType + " at " + node.getNodeId()
                + " (Phase 1 only supports 'binary')");
        }
        if (decisionField == null || decisionField.isBlank()) {
            throw new FlowExecutionException(
                "UserTask missing ruleforge:decisionField at " + node.getNodeId());
        }

        // 把 awaiting field 写到 ctx,GatewayNodeExecutor 路由时读
        context.setCurrentAwaitingField(decisionField);
        // 同时把字段名写到 vars._decisionField,FlowDecisionController 恢复时反查用
        // (currentAwaitingField 不会序列化到 rowVars,_decisionField 才会)
        context.getVars().put("_decisionField", decisionField);

        Map<String, Object> payload = new HashMap<>();
        payload.put("decisionType", "binary");
        payload.put("decisionField", decisionField);

        log.info("[USER-TASK-SUSPEND] nodeId={} decisionField={} flowRunId={}",
            node.getNodeId(), decisionField, context.getFlowRunId());

        throw new AsyncNodeSuspendException(
            node.getNodeId(),
            "USER_TASK",
            AsyncNodeSuspendException.WAIT_TYPE_USER_TASK,
            node.getNodeId(),       // waitRef = nodeId
            payload,
            null                    // 无限等
        );
    }
}
