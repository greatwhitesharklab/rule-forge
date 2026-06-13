package com.ruleforge.decision.flow.bus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.decision.entity.DecisionFlowState;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.engine.FlowDefinitionRepo;
import com.ruleforge.decision.flow.engine.FlowEngine;
import com.ruleforge.decision.flow.engine.Token;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.mapper.DecisionFlowStateMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V5.38 C0 — Bus ↔ Flow 桥接器。
 *
 * <p>职责:把 {@link Message} 翻译成 {@link FlowEngine#resume} 调用,以及
 * 给 Signal 广播走 {@code resumeAllSuspendedByWaitRef} DB 扫描 fan-out。
 *
 * <p>这是 bus SPI 和 flow 引擎之间唯一有耦合的点 — 知道 flowId / currentNodeId / vars
 * 这些 flow 概念;bus 本身不知道。
 *
 * <p>v0 简化:
 * <ul>
 *   <li>反序列化 payload 里的 vars:用 Jackson;失败 → log warn + 用空 vars</li>
 *   <li>engine.resume 抛 → catch + log warn(桥接不冒泡,避免把 bus publish 挂方拉爆)</li>
 *   <li>resumeAllSuspendedByWaitRef 复用 {@code mapper.selectRecoverable(100)},过滤 waitRef startsWith</li>
 * </ul>
 */
@Slf4j
@Component
public class FlowResumer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final FlowEngine engine;
    private final FlowDefinitionRepo repo;
    /**
     * nullable — 单元测试可以传 null 跳过 resumeAllSuspendedByWaitRef 路径;
     * Spring 自动装配会自动注入真实 mapper。
     */
    private final DecisionFlowStateMapper stateMapper;

    /** Spring 装配用(只取 engine + repo,stateMapper 后续 setter 注入)。 */
    @Autowired
    public FlowResumer(@Lazy FlowEngine engine, FlowDefinitionRepo repo) {
        this(engine, repo, null);
    }

    /** 3-arg 显式构造器 — 测试用,可以显式 mock stateMapper。 */
    public FlowResumer(FlowEngine engine, FlowDefinitionRepo repo, DecisionFlowStateMapper stateMapper) {
        this.engine = engine;
        this.repo = repo;
        this.stateMapper = stateMapper;
    }

    /**
     * 桥接:从 {@link Message} 恢复挂起的 flow。
     *
     * <p>payload 约定(由 publisher 填):
     * <ul>
     *   <li>{@code flowRunId} — 必填,string</li>
     *   <li>{@code flowId} — 必填,string(用于 repo.getOrLoad)</li>
     *   <li>{@code currentNodeId} — 必填,string</li>
     *   <li>{@code vars} — 可选,Map&lt;String,Object&gt;(merge 进新 ctx.vars)</li>
     * </ul>
     *
     * <p>异常隔离:任一异常(engine.resume 抛 / def 找不到 / payload 缺字段)→ log warn,return。
     */
    @SuppressWarnings("unchecked")
    public void resumeFromMessage(Message message) {
        if (message == null || message.payload() == null) {
            log.warn("[BUS→FLOW] resumeFromMessage: null message or empty payload, skip");
            return;
        }
        Map<String, Object> p = message.payload();
        Object flowRunIdObj = p.get("flowRunId");
        Object flowIdObj = p.get("flowId");
        Object currentNodeIdObj = p.get("currentNodeId");
        if (!(flowRunIdObj instanceof String flowRunId)
            || !(flowIdObj instanceof String flowId)
            || !(currentNodeIdObj instanceof String currentNodeId)) {
            log.warn("[BUS→FLOW] resumeFromMessage: missing flowRunId/flowId/currentNodeId in payload, skip");
            return;
        }

        try {
            FlowDefinition def = repo.getOrLoad(flowId);
            if (def == null) {
                log.warn("[BUS→FLOW] resumeFromMessage: flowId={} not found in repo, skip", flowId);
                return;
            }

            // 1. 反序列化 vars
            Map<String, Object> vars = new HashMap<>();
            Object varsObj = p.get("vars");
            if (varsObj instanceof Map<?, ?> m) {
                try {
                    vars = (Map<String, Object>) m;
                } catch (ClassCastException e) {
                    log.warn("[BUS→FLOW] resumeFromMessage: vars payload not a String→Object map, use empty");
                    vars = new HashMap<>();
                }
            }

            // 2. 构造 ctx + 调 engine.resume
            FlowContext ctx = new FlowContext(
                new com.ruleforge.decision.flow.engine.FlowIdentity(flowRunId, flowId, null),
                com.ruleforge.decision.flow.engine.BusinessVars.from(vars),
                new com.ruleforge.decision.flow.engine.ReteSession(),
                new com.ruleforge.decision.flow.engine.SuspendRegistry());
            // Resume 路径从 DB 还原状态 — 设根 token + currentNodeId
            // (vars 已经在 BusinessVars.from(vars) 时由 setCurrentToken 共享引用)
            Token rootToken = new Token("tok-resume-" + flowRunId);
            rootToken.setCurrentNodeId(currentNodeId);
            ctx.activeTokens().add(rootToken);
            ctx.setCurrentToken(rootToken);
            log.info("[BUS→FLOW] resume flowRunId={} flowId={} currentNodeId={} channel={}",
                flowRunId, flowId, currentNodeId, message.channel());
            engine.resume(def, ctx, currentNodeId);
        } catch (Exception e) {
            // 桥接不冒泡 — 一个坏 message 不挂 publish 调用方(B0 跨池)
            log.error("[BUS→FLOW] resume failed for flowRunId={} channel={}: {}",
                flowRunIdObj, message.channel(), e.getMessage(), e);
        }
    }

    /**
     * 给 Signal 广播用:扫 {@code nd_decision_flow_state} PENDING_ASYNC rows,
     * 过滤 waitRef startsWith {@code prefix},逐条 resume。
     *
     * <p>返回实际 resume 成功的条数。
     *
     * <p>用于 C2 intermediate throw signal:不订阅 bus channel(那只是 1:1),
     * 走 fan-out — 多个 suspended flow 一起恢复。
     */
    public int resumeAllSuspendedByWaitRef(String prefix) {
        if (stateMapper == null) {
            log.warn("[BUS→FLOW] resumeAllSuspended: stateMapper not wired, skip");
            return 0;
        }
        if (prefix == null || prefix.isEmpty()) {
            log.warn("[BUS→FLOW] resumeAllSuspended: empty prefix, skip (避免全表扫描)");
            return 0;
        }
        List<DecisionFlowState> rows;
        try {
            rows = stateMapper.selectRecoverable(100);
        } catch (Exception e) {
            log.error("[BUS→FLOW] selectRecoverable failed: {}", e.getMessage(), e);
            return 0;
        }
        int resumed = 0;
        for (DecisionFlowState s : rows) {
            String waitRef = s.getWaitRef();
            if (waitRef == null || !waitRef.startsWith(prefix)) {
                continue;
            }
            // 构造内部 Message-like 调 resumeFromMessage(复用同一异常隔离路径)
            Map<String, Object> payload = new HashMap<>();
            payload.put("flowRunId", s.getFlowRunId());
            payload.put("flowId", s.getFlowId());
            payload.put("currentNodeId", s.getCurrentNodeId());
            // 把 row_vars 也带过去(FlowResumer 读 payload.vars 合并)
            Map<String, Object> vars = parseRowVars(s.getRowVars());
            if (vars != null) {
                payload.put("vars", vars);
            }
            Message msg = Message.builder()
                .name("signal-broadcast")
                .channel(prefix)
                .sourceNodeId(s.getCurrentNodeId())
                .payload(payload)
                .build();
            try {
                resumeFromMessage(msg);
                resumed++;
            } catch (Exception e) {
                // resumeFromMessage 自身已经隔离,这里是双保险
                log.warn("[BUS→FLOW] resumeAllSuspended: row id={} resume failed: {}",
                    s.getId(), e.getMessage());
            }
        }
        log.info("[BUS→FLOW] resumeAllSuspended prefix={} resumed={}/{}",
            prefix, resumed, rows.size());
        return resumed;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseRowVars(String rowVarsJson) {
        if (rowVarsJson == null || rowVarsJson.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(rowVarsJson, MAP_TYPE);
        } catch (Exception e) {
            log.warn("[BUS→FLOW] parseRowVars failed: {}", e.getMessage());
            return null;
        }
    }
}
