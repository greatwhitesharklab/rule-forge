package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.registry.DataSourceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * V5.23 — DataSource 节点执行器。
 *
 * <p>在 BPMN 里这样用:
 * <pre>
 *   &lt;bpmn:serviceTask id="fetch_credit" name="拉征信"
 *                     flowable:type="SERVICE_TASK"
 *                     ruleforge:taskType="data_source"
 *                     ruleforge:dataSource="credit_score"
 *                     ruleforge:inputVar="applicant"   &lt;!-- optional, 默认整个 vars --&gt;
 *                     ruleforge:outputVar="creditResult" &lt;!-- optional, 默认 merge 到 vars --&gt;
 *   /&gt;
 * </pre>
 *
 * <p>实现路径:
 * <ol>
 *   <li>从 Spring 拿 {@link DataSourceClient} — 可能是 {@code null}(没启用 data source 模块的部署)</li>
 *   <li>读 {@code ruleforge:dataSource} 拿到 DS name</li>
 *   <li>从 ctx.vars 取 input(整个 vars 或按 inputVar)</li>
 *   <li>调 {@code client.fetch(name, inputs)}</li>
 *   <li>把 output merge 回 ctx.vars(或写到指定 outputVar 子 map)</li>
 * </ol>
 *
 * <p>模块边界:本 lib 通过 {@link DataSourceClient} 接口与 datasource 解耦 —
 * 实际 client impl 由各 app 注入(委托给 {@code DataSourceRegistry})。
 */
@Slf4j
@Component
public class DataSourceNodeExecutor implements NodeExecutor {

    private final DataSourceClient dataSourceClient;

    /**
     * 用 {@link ObjectProvider} 容忍 {@code DataSourceClient} 不存在 —
     * 部署时只接 core / decision 的 app 也能跑(节点不出现或被排除),不强制依赖。
     */
    @Autowired
    public DataSourceNodeExecutor(ObjectProvider<DataSourceClient> clientProvider) {
        this.dataSourceClient = clientProvider.getIfAvailable();
        if (this.dataSourceClient == null) {
            log.warn("[DataSource] no DataSourceClient bean found; "
                + "data_source nodes will fail at runtime if reached");
        }
    }

    @Override
    public String supportedType() {
        return "SERVICE_TASK:data_source";
    }

    @Override
    public void execute(FlowNode node, FlowContext context) throws Exception {
        if (dataSourceClient == null) {
            throw new FlowExecutionException(
                "DataSourceClient not configured (no ruleforge-datasource module on classpath?) "
                + "at node " + node.getNodeId());
        }

        String dsName = node.attr("ruleforge", "dataSource");
        String inputVar = node.attr("ruleforge", "inputVar");
        String outputVar = node.attr("ruleforge", "outputVar");

        if (dsName == null || dsName.isEmpty()) {
            throw new FlowExecutionException(
                "data_source node missing ruleforge:dataSource at " + node.getNodeId());
        }

        Map<String, Object> inputs;
        if (inputVar != null && !inputVar.isEmpty()) {
            Object v = context.getVars().get(inputVar);
            if (v instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) m;
                inputs = casted;
            } else {
                // 不是 map — 用单字段包装,允许 {id: applicant.id} 这种用法
                inputs = Map.of(inputVar, v);
            }
        } else {
            inputs = new java.util.HashMap<>(context.getVars());
        }

        long t0 = System.currentTimeMillis();
        Map<String, Object> outputs;
        try {
            outputs = dataSourceClient.fetch(dsName, inputs);
        } catch (Exception e) {
            log.error("[DataSource] fetch failed: name={} node={} after {}ms: {}",
                dsName, node.getNodeId(), System.currentTimeMillis() - t0, e.getMessage());
            throw new FlowExecutionException(
                "data_source fetch failed: name=" + dsName + " node=" + node.getNodeId() + ": " + e.getMessage(), e);
        }
        long elapsed = System.currentTimeMillis() - t0;
        log.info("[DataSource] fetch ok: name={} node={} {}ms keys={}",
            dsName, node.getNodeId(), elapsed,
            outputs == null ? 0 : outputs.size());

        if (outputs == null) return;

        if (outputVar != null && !outputVar.isEmpty()) {
            // 写到一个子 map: vars[outputVar] = outputs
            context.getVars().put(outputVar, outputs);
        } else {
            // merge 到根 vars
            for (Map.Entry<String, Object> e : outputs.entrySet()) {
                context.getVars().put(e.getKey(), e.getValue());
            }
        }
    }
}
