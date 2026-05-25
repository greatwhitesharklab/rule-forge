package com.ruleforge.console.app.service.impl;

import com.ruleforge.Utils;
import com.ruleforge.debug.MessageItem;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.runtime.service.KnowledgeService;
import com.ruleforge.console.app.model.OutputModel;
import com.ruleforge.console.app.entity.RuleVariableDef;
import com.ruleforge.console.app.entity.ShadowConfig;
import com.ruleforge.console.app.lazy.LazyEntityFactory;
import com.ruleforge.console.app.lazy.LazyGeneralEntity;
import com.ruleforge.console.app.service.IRuleVariableDefService;
import com.ruleforge.console.app.service.IShadowDecisionLogService;
import com.ruleforge.console.app.service.IShadowExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final RuntimeService flowableRuntimeService;

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
            ProcessInstance processInstance = flowableRuntimeService.startProcessInstanceByKey(shadowFlowId);
            Map<String, Object> resultVars = flowableRuntimeService.getVariables(processInstance.getId());
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
            shadowDecisionLogService.saveShadowLog(
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
                shadowDecisionLogService.saveShadowLog(
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
            } catch (Exception logEx) {
                log.error("陪跑日志保存失败: mainFlowLogId={}", mainFlowLogId, logEx);
            }
        }
    }
}
