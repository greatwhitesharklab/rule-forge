package com.ruleforge.console.batchtest.impl;

import com.ruleforge.console.app.entity.BatchTestSessionEntity;
import com.ruleforge.console.batchtest.BatchTestSubject;
import com.ruleforge.console.batchtest.SubjectExecutionContext;
import com.ruleforge.console.batchtest.SubjectResult;
import com.ruleforge.v1.ast.RuleAsset;
import com.ruleforge.v1.ast.library.Libraries;
import com.ruleforge.v1.ast.NodeBase;
import com.ruleforge.v1.exec.V1FlowRunner;
import com.ruleforge.v1.exec.V1PublishedBundle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * V1 决策流批测 subject(V7.23)— 逐行跑 {@link V1FlowRunner},替代老 FLOW subject
 * (走老 KnowledgeSession / RETE 全量 fire)。
 *
 * <p>跟老 {@link FlowBatchTestSubject} 的区别:
 * <ul>
 *   <li>老 FLOW:KnowledgePackage → KnowledgeSession.fireRules() — V7.21 删 BPMN 后退化成
 *       纯 RETE 全量 fire,flowId 不参与编排</li>
 *   <li>V1_FLOW:{@link V1FlowRunner#execute} — 真正按决策流图遍历执行(6 节点 + CEL + 网关)</li>
 * </ul>
 *
 * <p>ctx.params 约定(由 {@code BatchTestOrchestrator.executeWithSubject} 在 session 开始时
 * resolve 一次,避免每行重复读文件):
 * <ul>
 *   <li>{@code bundle}  {@link V1PublishedBundle} — 决策流闭包(asset + libraries + ruleFiles)</li>
 *   <li>{@code flowPath} String — 决策流全路径(记日志用)</li>
 * </ul>
 *
 * <p>ctx.input 是行的输入 JSON(ObjectMapper 反序列化为 {@code Map<String,Object>}),
 * 直接作为 fact 灌入 V1FlowRunner(V1FlowRunner 内部包成 GeneralEntity)。
 */
@Slf4j
@Component
public class V1BatchTestSubject implements BatchTestSubject {

    private static final String TYPE = BatchTestSessionEntity.SUBJECT_V1_FLOW;

    /** params key:V1PublishedBundle(session 级,resolve 一次) */
    public static final String PARAM_BUNDLE = "bundle";
    /** params key:决策流全路径(记日志用) */
    public static final String PARAM_FLOW_PATH = "flowPath";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SubjectResult execute(SubjectExecutionContext ctx) {
        long start = System.currentTimeMillis();
        try {
            V1PublishedBundle bundle = (V1PublishedBundle) ctx.params().get(PARAM_BUNDLE);
            if (bundle == null) {
                throw new IllegalStateException(
                        "V1_FLOW subject 缺少 bundle 参数(Orchestrator 未在 session 开始时 resolve)");
            }
            RuleAsset asset = bundle.getAsset();
            Libraries libraries = bundle.getLibraries();
            Map<String, NodeBase> ruleFiles = bundle.getRuleFiles();

            // 行输入 JSON → fact(V1FlowRunner 内部包 GeneralEntity,这里传 LinkedHashMap 即可)
            Map<String, Object> inputFact = (ctx.input() instanceof Map)
                    ? (Map<String, Object>) ctx.input()
                    : new LinkedHashMap<>();

            V1FlowRunner.FlowResult result = V1FlowRunner.execute(asset, inputFact, libraries, ruleFiles);

            long latency = System.currentTimeMillis() - start;
            // output:decision + rejected + rejectReason + fact(供结果展示 + Simulation 对比)
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("decision", result.decision);
            output.put("rejected", result.rejected);
            output.put("rejectReason", result.rejectReason);
            output.put("flags", result.flags);
            output.put("fact", result.fact);
            return SubjectResult.success(output, latency);

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.error("V1_BATCH_TEST 未知异常 rowId={} flowPath={}", ctx.rowId(),
                    ctx.params().get(PARAM_FLOW_PATH), e);
            return SubjectResult.failure("V1_EXECUTION_ERROR", e.getMessage(), latency);
        }
    }
}
