package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.NodeType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点执行器注册中心。
 * <p>
 * 路由策略:
 * 1. SERVICE_TASK 看 ruleforge:taskType(rule/action/package/rulesPackage) 路由到具体 Executor
 * 2. EXCLUSIVE_GATEWAY 一律走 GatewayNodeExecutor
 * 3. USER_TASK 走 UserTaskNodeExecutor
 * 4. START_EVENT / END_EVENT 走 EventNodeExecutor
 * 5. PARALLEL_GATEWAY 走 ParallelGatewayExecutor
 * 6. SCRIPT_TASK 走 ScriptNodeExecutor
 * 7. 其他 NodeType 按类型路由
 * <p>
 * 启动时所有 NodeExecutor Bean 通过 Spring 注入进来。
 */
@Component
public class NodeExecutorRegistry {

    /** SERVICE_TASK 路由表:taskType → Executor */
    private final Map<String, NodeExecutor> serviceTaskExecutors = new HashMap<>();
    /** 其它类型路由:NodeType → Executor */
    private final Map<NodeType, NodeExecutor> typeExecutors = new HashMap<>();

    public NodeExecutorRegistry(List<NodeExecutor> executors) {
        for (NodeExecutor ex : executors) {
            String type = ex.supportedType();
            if (type.startsWith("SERVICE_TASK:")) {
                String taskType = type.substring("SERVICE_TASK:".length());
                serviceTaskExecutors.put(taskType, ex);
            } else if ("EVENT".equals(type)) {
                // EventNodeExecutor 同时支持 START_EVENT / END_EVENT
                typeExecutors.put(NodeType.START_EVENT, ex);
                typeExecutors.put(NodeType.END_EVENT, ex);
            } else {
                try {
                    typeExecutors.put(NodeType.valueOf(type), ex);
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException("Unknown NodeExecutor supportedType: " + type, e);
                }
            }
        }
    }

    /**
     * 拿到当前节点对应的执行器。
     */
    public NodeExecutor resolve(FlowNode node) {
        if (node.getType() == NodeType.SERVICE_TASK) {
            // V5.33 A1:MI 优先于具体 taskType 路由
            // 任何 SERVICE_TASK 如果带 ruleforge:multiInstance="true",先走 MI wrapper
            if ("true".equalsIgnoreCase(node.attr("ruleforge", "multiInstance"))) {
                NodeExecutor wrapper = serviceTaskExecutors.get("multiInstance");
                if (wrapper == null) {
                    throw new com.ruleforge.decision.exception.FlowExecutionException(
                        "No multiInstance executor registered (V5.33 A1 wrapper missing bean?) at node "
                        + node.getNodeId());
                }
                return wrapper;
            }
            String taskType = node.attr("ruleforge", "taskType");
            if (taskType == null) {
                throw new com.ruleforge.decision.exception.FlowExecutionException(
                    "ServiceTask missing ruleforge:taskType at node " + node.getNodeId());
            }
            NodeExecutor ex = serviceTaskExecutors.get(taskType);
            if (ex == null) {
                throw new com.ruleforge.decision.exception.FlowExecutionException(
                    "No executor for serviceTask taskType=" + taskType + " at node " + node.getNodeId());
            }
            return ex;
        }
        NodeExecutor ex = typeExecutors.get(node.getType());
        if (ex == null) {
            throw new com.ruleforge.decision.exception.FlowExecutionException(
                "No executor for nodeType=" + node.getType() + " at node " + node.getNodeId());
        }
        return ex;
    }
}
