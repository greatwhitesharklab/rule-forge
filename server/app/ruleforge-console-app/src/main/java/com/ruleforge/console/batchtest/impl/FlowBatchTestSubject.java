package com.ruleforge.console.batchtest.impl;

import com.ruleforge.console.app.entity.BatchTestSessionEntity;
import com.ruleforge.console.batchtest.BatchTestSubject;
import com.ruleforge.console.batchtest.SubjectExecutionContext;
import com.ruleforge.console.batchtest.SubjectResult;
import com.ruleforge.console.model.ApplicationAllVariableCategoryMap;
import com.ruleforge.console.model.BatchTestFlowMap;
import com.ruleforge.console.model.SaveProcessItemDto;
import com.ruleforge.console.service.TestService;
import com.ruleforge.exception.RuleException;
import com.ruleforge.runtime.KnowledgePackage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * FLOW subject(V5.8.0)— 对每行跑一遍 KnowledgeSession(决策流)
 *
 * 复用既有 TestService.doFlowTest(从 V5.8.0 之前的 BatchTestServiceImpl
 * 抽出来的),不做重复实现。
 *
 * ctx.params 约定:
 *   - packageId        String,决策流所属包
 *   - knowledgePackage KnowledgePackage,从 params 拿(Controller 在 start 时加载)
 *   - flowId           String,决策流 id
 *   - flowMap          BatchTestFlowMap,流程变量映射
 *
 * ctx.input 反序列化为 ApplicationAllVariableCategoryMap(由 InputSource 负责反序列化)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowBatchTestSubject implements BatchTestSubject {

    private static final String TYPE = BatchTestSessionEntity.SUBJECT_FLOW;

    private final TestService testService;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public SubjectResult execute(SubjectExecutionContext ctx) {
        long start = System.currentTimeMillis();
        try {
            ApplicationAllVariableCategoryMap row = (ApplicationAllVariableCategoryMap) ctx.input();
            String flowId = (String) ctx.params().get("flowId");
            KnowledgePackage knowledgePackage = (KnowledgePackage) ctx.params().get("knowledgePackage");
            BatchTestFlowMap flowMap = (BatchTestFlowMap) ctx.params().get("flowMap");

            SaveProcessItemDto result = testService.doFlowTest(
                    knowledgePackage, flowId, row, flowMap);

            long latency = System.currentTimeMillis() - start;
            // output 留空给 InputSource / Service 决定怎么序列化(通常直接把 SaveProcessItemDto 序列化为 JSON)
            return SubjectResult.success(result, latency);

        } catch (RuleException e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("BatchTest 规则异常 rowId={} label={} msg={}", ctx.rowId(), e.getLabel(), e.getVal());
            return SubjectResult.failure(e.getLabel(), String.valueOf(e.getVal()), latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.error("BatchTest 未知异常 rowId={}", ctx.rowId(), e);
            return SubjectResult.failure("INTERNAL_ERROR", e.getMessage(), latency);
        }
    }
}
