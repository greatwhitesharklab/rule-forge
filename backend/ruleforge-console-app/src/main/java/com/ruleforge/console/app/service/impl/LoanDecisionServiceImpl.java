package com.ruleforge.console.app.service.impl;

import com.ruleforge.Utils;
import com.ruleforge.debug.MessageItem;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.runtime.service.KnowledgeService;
import com.ruleforge.console.app.model.OutputModel;
import com.ruleforge.console.app.dto.LoanEvaluateRequest;
import com.ruleforge.console.app.dto.LoanEvaluateResponse;
import com.ruleforge.console.app.entity.RuleVariableDef;
import com.ruleforge.console.app.entity.ShadowConfig;
import com.ruleforge.console.app.exception.AsyncDataSourcePendingException;
import com.ruleforge.console.app.lazy.DecisionContext;
import com.ruleforge.console.app.lazy.LazyEntityFactory;
import com.ruleforge.console.app.lazy.LazyGeneralEntity;
import com.ruleforge.console.app.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 贷款决策评估服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanDecisionServiceImpl implements ILoanDecisionService {

    private final LazyEntityFactory lazyEntityFactory;
    private final IRuleVariableDefService ruleVariableDefService;
    private final IDecisionLogService decisionLogService;
    private final IShadowConfigService shadowConfigService;
    private final IShadowExecutionService shadowExecutionService;
    private final RuntimeService flowableRuntimeService;

    @Override
    public LoanEvaluateResponse evaluate(LoanEvaluateRequest request) {
        log.info("开始执行贷款决策评估: userId={}, rulePackagePath={}, flowId={}",
                request.getUserId(), request.getRulePackagePath(), request.getFlowId());

        ExecutionContext ctx = new ExecutionContext();
        ctx.request = request;

        // 初始化请求上下文，将 loanZone/orbitCode 透传到 RestDataSourceProvider
        DecisionContext.init(request.getLoanZone(), request.getOrbitCode());

        try {
            // 1. 准备变量定义
            if (!prepareVariableDefinitions(ctx)) {
                return LoanEvaluateResponse.failure("No variable definitions found in database");
            }

            // 2. 加载规则包和创建会话
            prepareKnowledgeSession(ctx);

            // 3. 插入实体
            insertEntities(ctx);

            // 4. 执行决策流
            executeDecisionFlow(ctx);

            // 5. 收集结果
            collectResults(ctx);

            // 6. 保存日志
            Long flowLogId = saveSuccessLog(ctx);

            // 7. 触发陪跑
            triggerShadowExecution(flowLogId, request);

            return LoanEvaluateResponse.success(ctx.resultData);

        } catch (Exception e) {
            // 检查异常链中是否包含 AsyncDataSourcePendingException
            AsyncDataSourcePendingException asyncEx = extractAsyncPendingException(e);
            if (asyncEx != null) {
                log.info("决策暂停，等待异步数据: userId={}, orderNo={}, flowId={}, dataSourceId={}, field={}.{}",
                        request.getUserId(), request.getOrderNo(), request.getFlowId(),
                        asyncEx.getAsyncDataSourceId(), asyncEx.getClazz(), asyncEx.getFieldName());

                // 保存 PENDING 状态日志
                savePendingLog(ctx, asyncEx);

                return LoanEvaluateResponse.asyncPending(
                        asyncEx.getAsyncDataSourceId(),
                        asyncEx.isTaskTriggered()
                );
            }

            // 其他异常作为失败处理
            log.error("决策流执行失败: userId={}, rulePackagePath={}, flowId={}",
                    request.getUserId(), request.getRulePackagePath(), request.getFlowId(), e);

            saveFailureLog(ctx, e);
            return LoanEvaluateResponse.failure("Decision execution failed: " + e.getMessage());
        } finally {
            DecisionContext.clear();
        }
    }

    private boolean prepareVariableDefinitions(ExecutionContext ctx) {
        long stepStartTime = System.currentTimeMillis();
        ctx.allVariableDefs = ruleVariableDefService.findAll();
        ctx.queryVariableDefTime = System.currentTimeMillis() - stepStartTime;

        log.info("查询到 {} 条变量定义, 耗时: {} ms", ctx.allVariableDefs.size(), ctx.queryVariableDefTime);

        if (ctx.allVariableDefs.isEmpty()) {
            return false;
        }

        ctx.groupedByClazz = ctx.allVariableDefs.stream()
                .collect(Collectors.groupingBy(RuleVariableDef::getClazz));
        log.info("按 clazz 分组后共 {} 个实体类", ctx.groupedByClazz.size());

        return true;
    }

    private void prepareKnowledgeSession(ExecutionContext ctx) throws Exception {
        KnowledgeService knowledgeService = (KnowledgeService) Utils.getApplicationContext()
                .getBean(KnowledgeService.BEAN_ID);

        long stepStartTime = System.currentTimeMillis();
        KnowledgePackage knowledgePackage = knowledgeService.getKnowledge(ctx.request.getRulePackagePath());
        ctx.loadKnowledgeTime = System.currentTimeMillis() - stepStartTime;
        log.info("加载规则包: {}, 耗时: {} ms", ctx.request.getRulePackagePath(), ctx.loadKnowledgeTime);

        stepStartTime = System.currentTimeMillis();
        ctx.session = KnowledgeSessionFactory.newKnowledgeSession(knowledgePackage);
        ctx.createSessionTime = System.currentTimeMillis() - stepStartTime;
    }

    private void insertEntities(ExecutionContext ctx) {
        long stepStartTime = System.currentTimeMillis();

        // 创建并插入 OutputModel
        ctx.outputModel = new OutputModel();
        ctx.session.insert(ctx.outputModel);
        log.debug("已插入 OutputModel 到 session");

        // 创建并插入其他实体
        for (Map.Entry<String, List<RuleVariableDef>> entry : ctx.groupedByClazz.entrySet()) {
            String clazz = entry.getKey();
            LazyGeneralEntity entity = lazyEntityFactory.createLazyEntity(clazz, ctx.request.getUserId());
            ctx.session.insert(entity);
            ctx.insertedEntities.put(clazz, entity);
        }

        ctx.insertEntityTime = System.currentTimeMillis() - stepStartTime;
    }

    private void executeDecisionFlow(ExecutionContext ctx) {
        // 将 loanZone/orbitCode 作为决策流输入参数传入
        Map<String, Object> params = new HashMap<>();
        if (ctx.request.getLoanZone() != null) {
            params.put("loanZone", ctx.request.getLoanZone());
        }
        if (ctx.request.getOrbitCode() != null) {
            params.put("orbitCode", ctx.request.getOrbitCode());
        }

        ctx.inputParams = new HashMap<>(ctx.session.getParameters());
        ctx.inputParams.putAll(params);
        log.info("决策流执行前参数: {}", ctx.inputParams);

        long stepStartTime = System.currentTimeMillis();
        ProcessInstance processInstance = flowableRuntimeService.startProcessInstanceByKey(ctx.request.getFlowId(), params);
        Map<String, Object> resultVars = flowableRuntimeService.getVariables(processInstance.getId());
        ctx.response = new ExecutionResponseImpl();
        ctx.flowExecutionTime = System.currentTimeMillis() - stepStartTime;

        ctx.execMessageItems = ctx.session.getExecMessageItems();
    }

    private void collectResults(ExecutionContext ctx) {
        ctx.entityDataMap = new HashMap<>();

        // 从 OutputModel 获取决策流的输出结果
        if (ctx.outputModel != null) {
            ctx.resultData = new HashMap<>();
            ctx.resultData.put("ruleResult", ctx.outputModel.getRuleResult());
            ctx.resultData.put("lockDays", ctx.outputModel.getLockDays());
            ctx.resultData.put("ifManualReview", ctx.outputModel.getIfManualReview());
            ctx.resultData.put("creditLimit", ctx.outputModel.getCreditLimit());
            ctx.resultData.put("product", ctx.outputModel.getProduct());
            ctx.resultData.put("creditLimit_validDay", ctx.outputModel.getCreditLimit_validDay());
            ctx.resultData.put("newCust_mainModel_charge_v1", ctx.outputModel.getNewCust_mainModel_charge_v1());
            ctx.resultData.put("newCust_ruleScore_charge_v1", ctx.outputModel.getNewCust_ruleScore_charge_v1());
            ctx.resultData.put("newCust_creditLevel_charge_v1", ctx.outputModel.getNewCust_creditLevel_charge_v1());
            ctx.resultData.put("newCust_tradeScore_charge_v1", ctx.outputModel.getNewCust_tradeScore_charge_v1());
            ctx.resultData.put("newCust_v1_1_1_result", ctx.outputModel.getNewCust_v1_1_1_result());
            ctx.resultData.put("rule_score", ctx.outputModel.getRule_score());
            ctx.resultData.put("adjust_coeff", ctx.outputModel.getAdjust_coeff());
            ctx.resultData.put("creditLimit_cap", ctx.outputModel.getCreditLimit_cap());
            ctx.resultData.put("addCredit_cap", ctx.outputModel.getAddCredit_cap());
            ctx.resultData.put("newCust_mainModel_charge_v2", ctx.outputModel.getNewCust_mainModel_charge_v2());
            ctx.resultData.put("newCust_ruleScore_charge_v2", ctx.outputModel.getNewCust_ruleScore_charge_v2());
            ctx.resultData.put("newCust_creditLevel_charge_v2", ctx.outputModel.getNewCust_creditLevel_charge_v2());
            ctx.resultData.put("newCust_income_subScore_v1_2", ctx.outputModel.getNewCust_income_subScore_v1_2());
            ctx.resultData.put("newCust_debt_subScore_v1_2", ctx.outputModel.getNewCust_debt_subScore_v1_2());
            ctx.resultData.put("newCsut_lrScoreCard_0105_score", ctx.outputModel.getNewCsut_lrScoreCard_0105_score());
            ctx.resultData.put("newCsut_lrScoreCard_0105_level", ctx.outputModel.getNewCsut_lrScoreCard_0105_level());
            ctx.resultData.put("newCsut_lrScoreCard_0105_credit", ctx.outputModel.getNewCsut_lrScoreCard_0105_credit());
            ctx.resultData.put("newWithdraw_lrScoreCard_0106_score", ctx.outputModel.getNewWithdraw_lrScoreCard_0106_score());
            ctx.resultData.put("newWithdraw_lrScoreCard_0106_level", ctx.outputModel.getNewWithdraw_lrScoreCard_0106_level());
            ctx.resultData.put("new_score1", ctx.outputModel.getNew_score1());
            ctx.resultData.put("new_score2", ctx.outputModel.getNew_score2());
            ctx.resultData.put("new_score3", ctx.outputModel.getNew_score3());
            ctx.resultData.put("new_level1", ctx.outputModel.getNew_level1());
            ctx.resultData.put("new_level2", ctx.outputModel.getNew_level2());
            ctx.resultData.put("new_level3", ctx.outputModel.getNew_level3());
            ctx.resultData.put("old_score1", ctx.outputModel.getOld_score1());
            ctx.resultData.put("old_score2", ctx.outputModel.getOld_score2());
            ctx.resultData.put("old_score3", ctx.outputModel.getOld_score3());
            ctx.resultData.put("old_level1", ctx.outputModel.getOld_level1());
            ctx.resultData.put("old_level2", ctx.outputModel.getOld_level2());
            ctx.resultData.put("old_level3", ctx.outputModel.getOld_level3());

            log.info("从 OutputModel 收集到 {} 个输出字段", ctx.resultData.size());
        } else {
            log.warn("OutputModel 为 null，无法收集输出结果");
            ctx.resultData = new HashMap<>();
        }

        // 收集 LazyGeneralEntity 的已加载字段
        for (Map.Entry<String, LazyGeneralEntity> entry : ctx.insertedEntities.entrySet()) {
            String clazz = entry.getKey();
            LazyGeneralEntity entity = entry.getValue();
            Map<String, Object> entityFields = new HashMap<>();

            for (String field : entity.getLoadedFields()) {
                entityFields.put(field, entity.get(field));
            }

            ctx.entityDataMap.put(clazz, entityFields);
            ctx.totalLoadedFields += entity.getLoadedFields().size();
        }

        ctx.totalExecutionTime = System.currentTimeMillis() - ctx.totalStartTime;

        log.info("决策流执行完成: userId={}, flowId={}, 总耗时={}ms, 引擎耗时={}ms, 输出结果={}",
                ctx.request.getUserId(), ctx.request.getFlowId(),
                ctx.totalExecutionTime, ctx.response.getDuration(), ctx.resultData);
    }

    private Long saveSuccessLog(ExecutionContext ctx) {
        return decisionLogService.saveDecisionLog(
                ctx.request.getUserId(),
                ctx.request.getOrderNo(),
                ctx.request.getFlowId(),
                null,
                ctx.request.getRulePackagePath(),
                null,
                "SUCCESS",
                null,
                null,
                ctx.inputParams,
                ctx.resultData,
                ctx.entityDataMap,
                ctx.response,
                ctx.execMessageItems,
                ctx.queryVariableDefTime,
                ctx.loadKnowledgeTime,
                ctx.createSessionTime,
                ctx.insertEntityTime,
                ctx.flowExecutionTime,
                ctx.totalExecutionTime,
                ctx.totalLoadedFields,
                null,
                null
        );
    }

    private void saveFailureLog(ExecutionContext ctx, Exception e) {
        ctx.totalExecutionTime = System.currentTimeMillis() - ctx.totalStartTime;

        // 收集已加载的实体数据
        if (!ctx.insertedEntities.isEmpty()) {
            ctx.entityDataMap = new HashMap<>();
            for (Map.Entry<String, LazyGeneralEntity> entry : ctx.insertedEntities.entrySet()) {
                String clazz = entry.getKey();
                LazyGeneralEntity entity = entry.getValue();
                Map<String, Object> entityFields = new HashMap<>();
                for (String field : entity.getLoadedFields()) {
                    entityFields.put(field, entity.get(field));
                }
                ctx.entityDataMap.put(clazz, entityFields);
                ctx.totalLoadedFields += entity.getLoadedFields().size();
            }
        }

        // 获取异常堆栈
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String stackTrace = sw.toString();

        // 获取执行消息明细
        List<MessageItem> execMessageItems = ctx.session != null ? ctx.session.getExecMessageItems() : null;

        // 异步保存失败日志
        decisionLogService.saveDecisionLogAsync(
                ctx.request.getUserId(),
                ctx.request.getOrderNo(),
                ctx.request.getFlowId(),
                null,
                ctx.request.getRulePackagePath(),
                null,
                "FAILED",
                null,
                null,
                ctx.inputParams,
                ctx.resultData,
                ctx.entityDataMap,
                ctx.response,
                execMessageItems,
                ctx.queryVariableDefTime,
                ctx.loadKnowledgeTime,
                ctx.createSessionTime,
                ctx.insertEntityTime,
                ctx.flowExecutionTime,
                ctx.totalExecutionTime,
                ctx.totalLoadedFields,
                e.getMessage(),
                stackTrace
        );
    }

    private void savePendingLog(ExecutionContext ctx, AsyncDataSourcePendingException asyncEx) {
        ctx.totalExecutionTime = System.currentTimeMillis() - ctx.totalStartTime;

        // 收集已加载的实体数据
        Map<String, Object> entityDataMap = new HashMap<>();
        for (Map.Entry<String, LazyGeneralEntity> entry : ctx.insertedEntities.entrySet()) {
            String clazz = entry.getKey();
            LazyGeneralEntity entity = entry.getValue();
            Map<String, Object> entityFields = new HashMap<>();
            for (String field : entity.getLoadedFields()) {
                entityFields.put(field, entity.get(field));
            }
            entityDataMap.put(clazz, entityFields);
            ctx.totalLoadedFields += entity.getLoadedFields().size();
        }

        // 暂停原因信息
        Map<String, Object> pendingInfo = new HashMap<>();
        pendingInfo.put("asyncDataSourceId", asyncEx.getAsyncDataSourceId());
        pendingInfo.put("asyncFieldClazz", asyncEx.getClazz());
        pendingInfo.put("asyncFieldName", asyncEx.getFieldName());
        pendingInfo.put("asyncTaskTriggered", asyncEx.isTaskTriggered());

        decisionLogService.saveDecisionLog(
                ctx.request.getUserId(),
                ctx.request.getOrderNo(),
                ctx.request.getFlowId(),
                null,
                ctx.request.getRulePackagePath(),
                null,
                "PENDING",
                null,
                null,
                ctx.inputParams,
                pendingInfo,
                entityDataMap,
                null,
                null,
                ctx.queryVariableDefTime,
                ctx.loadKnowledgeTime,
                ctx.createSessionTime,
                ctx.insertEntityTime,
                0,
                ctx.totalExecutionTime,
                ctx.totalLoadedFields,
                null,
                null
        );
    }

    /**
     * 从异常链中提取 AsyncDataSourcePendingException
     * ruleforge 引擎会将异常包装为 RuleException -> RuleAssertException
     */
    private AsyncDataSourcePendingException extractAsyncPendingException(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof AsyncDataSourcePendingException) {
                return (AsyncDataSourcePendingException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private void triggerShadowExecution(Long mainFlowLogId, LoanEvaluateRequest request) {
        try {
            List<ShadowConfig> shadowConfigs = shadowConfigService.findEnabledByMainPath(request.getRulePackagePath());
            if (shadowConfigs == null || shadowConfigs.isEmpty()) {
                return;
            }

            for (ShadowConfig config : shadowConfigs) {
                if (shadowConfigService.shouldExecuteShadow(config)) {
                    log.info("触发陪跑: mainFlowLogId={}, userId={}, shadowRulePackagePath={}",
                            mainFlowLogId, request.getUserId(), config.getShadowRulePackagePath());
                    shadowExecutionService.executeShadowAsync(
                            mainFlowLogId,
                            request.getUserId(),
                            request.getOrderNo(),
                            request.getFlowId(),
                            config
                    );
                }
            }
        } catch (Exception e) {
            log.error("触发陪跑失败: mainFlowLogId={}, userId={}", mainFlowLogId, request.getUserId(), e);
        }
    }

    /**
     * 执行上下文，用于在方法间传递状态
     */
    private static class ExecutionContext {
        LoanEvaluateRequest request;
        long totalStartTime = System.currentTimeMillis();
        long queryVariableDefTime = 0;
        long loadKnowledgeTime = 0;
        long createSessionTime = 0;
        long insertEntityTime = 0;
        long flowExecutionTime = 0;
        long totalExecutionTime = 0;
        int totalLoadedFields = 0;

        List<RuleVariableDef> allVariableDefs;
        Map<String, List<RuleVariableDef>> groupedByClazz;
        KnowledgeSession session;
        Map<String, LazyGeneralEntity> insertedEntities = new HashMap<>();
        OutputModel outputModel;

        Map<String, Object> inputParams;
        Map<String, Object> resultData;
        Map<String, Object> entityDataMap;
        ExecutionResponseImpl response;
        List<MessageItem> execMessageItems;
    }
}
