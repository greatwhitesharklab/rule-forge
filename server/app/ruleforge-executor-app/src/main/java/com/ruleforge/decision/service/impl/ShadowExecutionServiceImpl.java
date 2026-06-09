package com.ruleforge.decision.service.impl;

import com.ruleforge.Utils;
import com.ruleforge.debug.MessageItem;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.runtime.service.KnowledgeService;
import com.ruleforge.decision.model.OutputModel;
import com.ruleforge.decision.entity.DecisionFlowState;
import com.ruleforge.decision.entity.RuleVariableDef;
import com.ruleforge.decision.entity.ShadowConfig;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.engine.FlowEngine;
import com.ruleforge.decision.lazy.LazyEntityFactory;
import com.ruleforge.decision.lazy.LazyGeneralEntity;
import com.ruleforge.decision.service.IRuleVariableDefService;
import com.ruleforge.decision.service.IShadowComparisonService;
import com.ruleforge.decision.service.IShadowDecisionLogService;
import com.ruleforge.decision.service.IShadowExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 陪跑执行服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShadowExecutionServiceImpl implements IShadowExecutionService {

    private final LazyEntityFactory lazyEntityFactory;
    private final IRuleVariableDefService ruleVariableDefService;
    private final IShadowDecisionLogService shadowDecisionLogService;
    private final IShadowComparisonService shadowComparisonService;
    // V5.20+ 自建决策流执行器(V5.21 起为唯一执行路径)
    private final FlowEngine flowEngine;

    @Override
    @Async("shadowExecutor")
    public void executeShadowAsync(
            Long mainFlowLogId,
            String userId,
            String orderNo,
            String mainFlowId,
            ShadowConfig shadowConfig
    ) {
        String shadowRulePackagePath = shadowConfig.getShadowRulePackagePath();
        String shadowFlowId = shadowConfig.getShadowFlowId();
        // 如果没有配置陪跑流程ID，使用主流程ID
        if (shadowFlowId == null || shadowFlowId.isEmpty()) {
            shadowFlowId = mainFlowId;
        }

        log.info("开始执行陪跑: mainFlowLogId={}, userId={}, shadowRulePackagePath={}, shadowFlowId={}",
                mainFlowLogId, userId, shadowRulePackagePath, shadowFlowId);

        long totalStartTime = System.currentTimeMillis();
        long loadKnowledgeTime = 0;
        long flowExecutionTime = 0;

        Map<String, Object> inputParams = null;
        Map<String, Object> resultData = null;
        Map<String, Object> entityDataMap = null;
        ExecutionResponseImpl response = null;
        List<MessageItem> execMessageItems = null;
        Map<String, LazyGeneralEntity> insertedEntities = new HashMap<>();
        KnowledgeSession session = null;

        try {
            // 1. 查询变量定义
            List<RuleVariableDef> allVariableDefs = ruleVariableDefService.findAll();
            if (allVariableDefs.isEmpty()) {
                log.warn("陪跑执行失败: 没有变量定义, mainFlowLogId={}", mainFlowLogId);
                return;
            }

            // 2. 按 clazz 分组
            Map<String, List<RuleVariableDef>> groupedByClazz = allVariableDefs.stream()
                    .collect(Collectors.groupingBy(RuleVariableDef::getClazz));

            // 3. 获取 KnowledgeService
            KnowledgeService knowledgeService = (KnowledgeService) Utils.getApplicationContext()
                    .getBean(KnowledgeService.BEAN_ID);

            // 4. 加载陪跑规则包
            long stepStartTime = System.currentTimeMillis();
            KnowledgePackage knowledgePackage = knowledgeService.getKnowledge(shadowRulePackagePath);
            loadKnowledgeTime = System.currentTimeMillis() - stepStartTime;

            // 5. 创建 KnowledgeSession
            session = KnowledgeSessionFactory.newKnowledgeSession(knowledgePackage);

            // 6. 创建实体并插入 session
            OutputModel outputModel = new OutputModel();
            session.insert(outputModel);
            for (Map.Entry<String, List<RuleVariableDef>> entry : groupedByClazz.entrySet()) {
                String clazz = entry.getKey();
                LazyGeneralEntity entity = lazyEntityFactory.createLazyEntity(clazz, userId);
                session.insert(entity);
                insertedEntities.put(clazz, entity);
            }

            // 7. 执行陪跑决策流
            inputParams = session.getParameters();

            stepStartTime = System.currentTimeMillis();
            // V5.20+ 自建 FlowEngine(V5.21 起为唯一执行路径)
            FlowContext flowCtx = new FlowContext();
            flowCtx.setFlowRunId(UUID.randomUUID().toString());
            flowCtx.setVars(new HashMap<>());
            flowCtx.setSession(session);
            flowCtx.setOutputModel(outputModel);
            DecisionFlowState state = flowEngine.start(shadowFlowId, flowCtx);
            if (DecisionFlowState.STATUS_FAILED.equals(state.getStatus())) {
                throw new FlowExecutionException("Shadow flow failed: " + state.getErrorMessage());
            }
            if (DecisionFlowState.STATUS_WAITING_CALLBACK.equals(state.getStatus())
                || DecisionFlowState.STATUS_PENDING_ASYNC.equals(state.getStatus())) {
                // Shadow 路径遇 USER_TASK: 视为 FAIL(陪跑不挂起,直接跳过)
                log.warn("[SHADOW-CUSTOM] flow hit USER_TASK, recording as FAILED: shadowFlowId={} waitRef={}",
                    shadowFlowId, state.getWaitRef());
                throw new FlowExecutionException("Shadow flow suspended (USER_TASK) — not supported in shadow path");
            }
            log.info("[SHADOW-CUSTOM] completed: shadowFlowId={} status={}", shadowFlowId, state.getStatus());
            response = new ExecutionResponseImpl();
            flowExecutionTime = System.currentTimeMillis() - stepStartTime;

            // 8. 获取执行后参数
            resultData = session.getParameters();

            // 9. 收集实体数据
            entityDataMap = new HashMap<>();
            int totalLoadedFields = 0;
            for (Map.Entry<String, LazyGeneralEntity> entry : insertedEntities.entrySet()) {
                String clazz = entry.getKey();
                LazyGeneralEntity entity = entry.getValue();
                Map<String, Object> entityFields = new HashMap<>();
                for (String field : entity.getLoadedFields()) {
                    entityFields.put(field, entity.get(field));
                }
                entityDataMap.put(clazz, entityFields);
                totalLoadedFields += entity.getLoadedFields().size();
            }

            // 10. 获取执行消息
            execMessageItems = session.getExecMessageItems();

            long totalExecutionTime = System.currentTimeMillis() - totalStartTime;

            log.info("陪跑执行完成: mainFlowLogId={}, userId={}, shadowFlowId={}, 总耗时={}ms",
                    mainFlowLogId, userId, shadowFlowId, totalExecutionTime);

            // 11. 保存陪跑日志
            Long shadowFlowLogId = shadowDecisionLogService.saveShadowLog(
                    mainFlowLogId,
                    userId,
                    orderNo,
                    shadowFlowId,
                    null,  // flowVersion
                    shadowRulePackagePath,
                    null,  // rulePackageVersion
                    "SUCCESS",
                    null,  // rejectReason
                    null,  // rejectCode
                    inputParams,
                    resultData,
                    entityDataMap,
                    response,
                    execMessageItems,
                    loadKnowledgeTime,
                    flowExecutionTime,
                    totalExecutionTime,
                    totalLoadedFields,
                    null,  // errorMessage
                    null   // errorStackTrace
            );

            // 12. 触发自动对比
            if (shadowFlowLogId != null) {
                shadowComparisonService.compareAndSave(mainFlowLogId, shadowFlowLogId);
            }

        } catch (Exception e) {
            log.error("陪跑执行失败: mainFlowLogId={}, userId={}, shadowRulePackagePath={}",
                    mainFlowLogId, userId, shadowRulePackagePath, e);

            long totalExecutionTime = System.currentTimeMillis() - totalStartTime;

            // 收集实体数据
            int totalLoadedFields = 0;
            if (!insertedEntities.isEmpty()) {
                entityDataMap = new HashMap<>();
                for (Map.Entry<String, LazyGeneralEntity> entry : insertedEntities.entrySet()) {
                    String clazz = entry.getKey();
                    LazyGeneralEntity entity = entry.getValue();
                    Map<String, Object> entityFields = new HashMap<>();
                    for (String field : entity.getLoadedFields()) {
                        entityFields.put(field, entity.get(field));
                    }
                    entityDataMap.put(clazz, entityFields);
                    totalLoadedFields += entity.getLoadedFields().size();
                }
            }

            // 获取执行消息
            if (session != null) {
                execMessageItems = session.getExecMessageItems();
            }

            // 获取异常堆栈
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String stackTrace = sw.toString();

            // 保存失败日志
            try {
                Long shadowFlowLogId = shadowDecisionLogService.saveShadowLog(
                        mainFlowLogId,
                        userId,
                        orderNo,
                        shadowFlowId,
                        null,
                        shadowRulePackagePath,
                        null,
                        "FAILED",
                        null,
                        null,
                        inputParams,
                        resultData,
                        entityDataMap,
                        response,
                        execMessageItems,
                        loadKnowledgeTime,
                        flowExecutionTime,
                        totalExecutionTime,
                        totalLoadedFields,
                        e.getMessage(),
                        stackTrace
                );

                // 失败时也触发对比（状态差异会是 HIGH）
                if (shadowFlowLogId != null) {
                    shadowComparisonService.compareAndSave(mainFlowLogId, shadowFlowLogId);
                }
            } catch (Exception logEx) {
                log.error("陪跑日志保存失败: mainFlowLogId={}", mainFlowLogId, logEx);
            }
        }
    }
}
