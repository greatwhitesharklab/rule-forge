package com.ruleforge.decision.service.impl;

import com.ruleforge.Utils;
import com.ruleforge.debug.MessageItem;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.runtime.service.KnowledgeService;
import com.ruleforge.decision.model.OutputModel;
import com.ruleforge.decision.dto.DecisionRequest;
import com.ruleforge.decision.dto.DecisionResponse;
import com.ruleforge.decision.dto.GrayResolution;
import com.ruleforge.decision.entity.DecisionFlowState;
import com.ruleforge.decision.entity.RuleVariableDef;
import com.ruleforge.decision.entity.ShadowConfig;
import com.ruleforge.decision.exception.AsyncDataSourcePendingException;
import com.ruleforge.decision.exception.DecisionAsyncPendingException;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.engine.FlowDefinitionRepo;
import com.ruleforge.decision.flow.engine.FlowEngine;
import com.ruleforge.decision.lazy.DecisionContext;
import com.ruleforge.decision.lazy.LazyEntityFactory;
import com.ruleforge.decision.lazy.LazyGeneralEntity;
import com.ruleforge.model.GeneralEntity;
import org.apache.commons.beanutils.BeanUtils;
import com.ruleforge.decision.service.*;
import com.ruleforge.executor.service.impl.GrayVersionContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 贷款决策评估服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionServiceImpl implements IDecisionService {

    private final LazyEntityFactory lazyEntityFactory;
    private final IRuleVariableDefService ruleVariableDefService;
    private final IDecisionLogService decisionLogService;
    private final IShadowConfigService shadowConfigService;
    private final IShadowExecutionService shadowExecutionService;
    private final IGrayStrategyService grayStrategyService;
    private final MeterRegistry meterRegistry;
    // V5.20+ 自建决策流执行器(V5.21 起为唯一执行路径)
    private final FlowEngine flowEngine;
    private final FlowDefinitionRepo flowDefinitionRepo;

    @Override
    public DecisionResponse evaluate(DecisionRequest request) {
        log.info("开始执行贷款决策评估: userId={}, rulePackagePath={}, flowId={}",
                request.getUserId(), request.getRulePackagePath(), request.getFlowId());

        ExecutionContext ctx = new ExecutionContext();
        ctx.request = request;

        // 初始化请求上下文，将 loanZone/orbitCode 透传到 RestDataSourceProvider
        DecisionContext.init(request.getLoanZone(), request.getOrbitCode());

        try {
            // 1. 准备变量定义
            if (!prepareVariableDefinitions(ctx)) {
                return DecisionResponse.failure("No variable definitions found in database");
            }

            // 2. 加载规则包和创建会话
            prepareKnowledgeSession(ctx);

            // 3. 插入实体
            insertEntities(ctx);

            // 4. 执行决策流
            executeDecisionFlow(ctx);

            // 5. 收集结果
            collectResults(ctx);

            // 6. 记录监控指标
            recordMetrics(ctx, "SUCCESS");

            // 7. 保存日志
            Long flowLogId = saveSuccessLog(ctx);

            // 8. 触发陪跑
            triggerShadowExecution(flowLogId, request);

            return DecisionResponse.success(ctx.resultData);

        } catch (Exception e) {
            // V5.20+: 自建引擎路径抛 DecisionAsyncPendingException(USER_TASK 二元决策挂起)
            DecisionAsyncPendingException asyncFlowEx = extractAsyncFlowPendingException(e);
            if (asyncFlowEx != null) {
                log.info("决策流挂起等待人工决策: userId={}, orderNo={}, flowId={}, userTaskNode={}, flowRunId={}",
                        request.getUserId(), request.getOrderNo(), request.getFlowId(),
                        asyncFlowEx.getWaitRef(), asyncFlowEx.getCurrentNodeId());
                savePendingLog(ctx, asyncFlowEx);
                recordMetrics(ctx, "PENDING");
                return DecisionResponse.asyncPending(asyncFlowEx.getWaitRef(), true);
            }

            // 检查异常链中是否包含 AsyncDataSourcePendingException
            AsyncDataSourcePendingException asyncEx = extractAsyncPendingException(e);
            if (asyncEx != null) {
                log.info("决策暂停，等待异步数据: userId={}, orderNo={}, flowId={}, dataSourceId={}, field={}.{}",
                        request.getUserId(), request.getOrderNo(), request.getFlowId(),
                        asyncEx.getAsyncDataSourceId(), asyncEx.getClazz(), asyncEx.getFieldName());

                // 保存 PENDING 状态日志
                savePendingLog(ctx, asyncEx);

                recordMetrics(ctx, "PENDING");

                return DecisionResponse.asyncPending(
                        asyncEx.getAsyncDataSourceId(),
                        asyncEx.isTaskTriggered()
                );
            }

            // 其他异常作为失败处理
            log.error("决策流执行失败: userId={}, rulePackagePath={}, flowId={}",
                    request.getUserId(), request.getRulePackagePath(), request.getFlowId(), e);

            saveFailureLog(ctx, e);
            recordMetrics(ctx, "FAILED");
            return DecisionResponse.failure("Decision execution failed: " + e.getMessage());
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

        // 灰度版本解析
        GrayResolution grayResolution = grayStrategyService.resolveVersion(
                ctx.request.getRulePackagePath(), ctx.request.getUserId());
        ctx.grayResolution = grayResolution;

        long stepStartTime = System.currentTimeMillis();
        KnowledgePackage knowledgePackage;
        try {
            // 通过 ThreadLocal 将灰度版本传递到 KnowledgePackageServiceImpl
            if (grayResolution != null && grayResolution.isGrayHit()) {
                GrayVersionContext.set(grayResolution.getGitTag());
            }
            knowledgePackage = knowledgeService.getKnowledge(ctx.request.getRulePackagePath());
            ctx.loadKnowledgeTime = System.currentTimeMillis() - stepStartTime;
            log.info("加载规则包: {}, 灰度: {}, 耗时: {} ms",
                    ctx.request.getRulePackagePath(),
                    grayResolution != null && grayResolution.isGrayHit() ? grayResolution.getGitTag() : "生产版本",
                    ctx.loadKnowledgeTime);
        } finally {
            GrayVersionContext.clear();
        }

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

        // Hybrid facts 注入(V5.18 续):
        //   request.applicant → ApplicantModel(class=com.ruleforge.decision.model.ApplicantModel)
        //   request.order     → OrderModel(class=com.ruleforge.decision.model.OrderModel)
        //   其它 clazz(决策引擎按 nd_rule_variable_def 自动发现)→ 走 lazy 全权,
        //   业务系统没传对应 facts 就靠 DataSourceProvider.fetchFieldValue() 查
        Map<String, Map<String, Object>> factsByClazz = resolveFactsByClazz(ctx.request);

        // 创建并插入其他实体
        for (Map.Entry<String, List<RuleVariableDef>> entry : ctx.groupedByClazz.entrySet()) {
            String clazz = entry.getKey();
            Map<String, Object> facts = factsByClazz.getOrDefault(clazz, null);
            LazyGeneralEntity entity = injectFacts(clazz, ctx.request.getUserId(), facts);
            ctx.session.insert(entity);
            ctx.insertedEntities.put(clazz, entity);
        }

        // 任务 #213:把 OutputModel 包成 GeneralEntity 塞进 session,让规则引擎
        // 能 var-assign OutputModel.ruleResult 等字段。规则引擎的 ObjectTypeActivity
        // 字符串模式只识别 GeneralEntity(用 targetClass 字符串匹配),不识别普通 Java
        // POJO(那个要走 Class<?> typeClass 构造器,从 XML 走不到)。所以要走"包成
        // GeneralEntity + session.fireRules() + 字段回流"的路。Flowable 路径下的
        // 规则执行(delegete 那条)是另一条线,会跑自己的 session(只有 applicant/order,
        // 没有 OutputModel),跟这里互不干扰。
        try {
            GeneralEntity outputEntity = wrapOutputModelAsEntity(ctx.outputModel);
            ctx.session.insert(outputEntity);
            ctx.session.fireRules();
            syncOutputModelFromEntity(ctx.outputModel, outputEntity);
        } catch (Exception e) {
            log.warn("OutputModel 规则执行失败(非致命,继续走 Flowable 路径): {}", e.getMessage());
        }

        ctx.insertEntityTime = System.currentTimeMillis() - stepStartTime;
    }

    /**
     * 把 OutputModel 的字段拷到 GeneralEntity — Apache Commons BeanUtils.describe
     * 会反射读所有 getter,把 (字段名, 值) 放进 Map,GeneralEntity 继承 HashMap,
     * 直接 putAll 即可。规则 var-assign OutputModel.ruleResult 会改写 entity 里的
     * "ruleResult" key。
     */
    @SuppressWarnings("unchecked")
    private GeneralEntity wrapOutputModelAsEntity(OutputModel outputModel) throws Exception {
        GeneralEntity entity = new GeneralEntity(OutputModel.class.getName());
        java.util.Map<String, String> snapshot = BeanUtils.describe(outputModel);
        for (java.util.Map.Entry<String, String> e : snapshot.entrySet()) {
            // BeanUtils.describe 会包含 "class" 这个 key,跳过(GeneralEntity 不该有)
            if ("class".equals(e.getKey())) {
                continue;
            }
            entity.put(e.getKey(), e.getValue());
        }
        return entity;
    }

    /**
     * 把 GeneralEntity 里被规则改写的字段同步回 OutputModel POJO。
     * BeanUtils.populate(pojo, map) 通过 setter 写回。
     */
    private void syncOutputModelFromEntity(OutputModel outputModel, GeneralEntity entity) throws Exception {
        BeanUtils.populate(outputModel, entity);
    }

    /**
     * 把 facts 注入 entity — 决策引擎 hybrid 数据注入的入口。
     *
     * <p>facts 非空时:走 {@link LazyEntityFactory#createLazyEntity(String, String, Map)}
     * 3 参 overload,factories 内部对每个 fact 调 {@code entity.put(k, v)},
     * 标记为 {@code loadedProperties},后续规则读同名字段不查 DataSource。
     *
     * <p>facts 为 null/空时:走 2 参 overload,entity 全空,规则读字段全 lazy。
     *
     * <p>本方法是 package-private(无 modifier)以支持单测,生产代码走
     * {@link #insertEntities(ExecutionContext)} 路径。
     *
     * @param clazz 实体类全限定名(规则侧 {@code nd_rule_variable_def.clazz})
     * @param userId 实体主键({@code DataSourceProvider} 用它查 lazy 字段)
     * @param facts eager 要注入的字段;null/空 → 走 lazy
     * @return 创建好的 entity(已 insert 到 session 前的中间产物)
     */
    LazyGeneralEntity injectFacts(String clazz, String userId, Map<String, Object> facts) {
        if (facts == null || facts.isEmpty()) {
            return lazyEntityFactory.createLazyEntity(clazz, userId);
        }
        return lazyEntityFactory.createLazyEntity(clazz, userId, facts);
    }

    /**
     * 把 {@link DecisionRequest} 里的 facts 字段映射到 entity clazz。
     * 业务系统传 {@code request.applicant} → {@code ApplicantModel};
     * 传 {@code request.order} → {@code OrderModel}。
     * 其它 clazz 没 facts(lazy 兜底)。
     */
    private Map<String, Map<String, Object>> resolveFactsByClazz(DecisionRequest request) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        if (request.getApplicant() != null && !request.getApplicant().isEmpty()) {
            result.put("com.ruleforge.decision.model.ApplicantModel", request.getApplicant());
        }
        if (request.getOrder() != null && !request.getOrder().isEmpty()) {
            result.put("com.ruleforge.decision.model.OrderModel", request.getOrder());
        }
        return result;
    }

    /**
     * 构造 {@link FlowContext} 的初始 vars — V5.20+ 自建决策流执行器用。
     *
     * <p>vars 流转:
     * <ul>
     *   <li>loanZone / orbitCode 透传(原行为,RestDataSourceProvider 路由用)</li>
     *   <li>applicant / order 以 {@code Map<String, Object>} 形式塞,自建引擎收到后
     *       调 {@code LazyEntityFactory} 转成 {@link LazyGeneralEntity} 塞进 session
     *       (规则 DSL 写 {@code applicant.age} 就走这个 entity)</li>
     *   <li>applicant / order 为 null/空 → 不塞对应 key(走 nd_rule_variable_def
     *       自动发现 clazz + lazy 兜底,字段规则读时按需 DataSourceProvider 查)</li>
     * </ul>
     *
     * <p>Package-private 是为了让 {@code DecisionServiceImplFlowVariablesTest}
     * 直接覆盖(避免 mock 整个 FlowEngine 启动路径)。
     */
    Map<String, Object> buildProcessVariables(DecisionRequest request) {
        Map<String, Object> params = new HashMap<>();

        // loanZone / orbitCode 透传(原行为,RestDataSourceProvider 路由用)
        if (request.getLoanZone() != null) {
            params.put("loanZone", request.getLoanZone());
        }
        if (request.getOrbitCode() != null) {
            params.put("orbitCode", request.getOrbitCode());
        }

        // applicant / order 以 Map 形式塞进 FlowContext.vars — 自建引擎不要求
        // Serializable(跟 V5.x Flowable 路径不同,后者要把 vars 序列化到 act_ru_variable
        // BLOB 列)。Map<String, Object> 足够,Facts 注入由 FlowEngine 内部
        // LazyEntityFactory 转 entity 后 insert session。
        if (request.getApplicant() != null && !request.getApplicant().isEmpty()) {
            params.put("applicant", new HashMap<>(request.getApplicant()));
        }
        if (request.getOrder() != null && !request.getOrder().isEmpty()) {
            params.put("order", new HashMap<>(request.getOrder()));
        }

        return params;
    }

    /**
     * V5.20+: 自建决策流执行器(V5.21 起为唯一执行路径)。直接调 {@code FlowEngine.start},
     * 不再走 Flowable 引擎。
     *
     * <p>关键设计:
     * <ul>
     *   <li>不再调 {@code flowableRuntimeService.startProcessInstanceByKey}</li>
     *   <li>RuleNodeExecutor 内部已经做 OutputModel var-assign(V5.18 wrapOutputModelAsEntity +
     *       BeanUtils.populate 修法搬到 RuleNodeExecutor),所以 insertEntities 末尾的
     *       workaround 块对 custom 路径不重复</li>
     *   <li>抛 {@link DecisionAsyncPendingException} → DecisionServiceImpl catch 后走
     *       asyncPending 响应(USER_TASK 二元决策挂起)</li>
     * </ul>
     */
    private void executeDecisionFlow(ExecutionContext ctx) {
        Map<String, Object> params = buildProcessVariables(ctx.request);

        ctx.inputParams = new HashMap<>(ctx.session.getParameters());
        ctx.inputParams.putAll(params);
        log.info("[FLOW-CUSTOM] start: flowId={} params={}", ctx.request.getFlowId(), ctx.inputParams);

        long stepStartTime = System.currentTimeMillis();
        try {
            FlowContext flowCtx = new FlowContext();
            flowCtx.setFlowRunId(UUID.randomUUID().toString());
            flowCtx.setVars(new HashMap<>(params));
            flowCtx.setSession(ctx.session);
            flowCtx.setOutputModel(ctx.outputModel);
            // 同步主路径:从 startNodeId 推到 endEvent
            DecisionFlowState state = flowEngine.start(ctx.request.getFlowId(), flowCtx);
            if (DecisionFlowState.STATUS_WAITING_CALLBACK.equals(state.getStatus())
                || DecisionFlowState.STATUS_PENDING_ASYNC.equals(state.getStatus())) {
                throw new DecisionAsyncPendingException(
                    state.getFlowRunId(), state.getWaitRef(), state.getCurrentNodeId());
            }
            if (DecisionFlowState.STATUS_FAILED.equals(state.getStatus())) {
                throw new FlowExecutionException(state.getErrorMessage());
            }
            ctx.flowRunId = state.getFlowRunId();
            ctx.response = new ExecutionResponseImpl();
            ctx.execMessageItems = ctx.session.getExecMessageItems();
            log.info("[FLOW-CUSTOM] completed: flowRunId={} status={}",
                ctx.flowRunId, state.getStatus());
        } catch (DecisionAsyncPendingException e) {
            throw e;
        } catch (FlowExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new FlowExecutionException("Flow execution failed: " + e.getMessage(), e);
        } finally {
            ctx.flowExecutionTime = System.currentTimeMillis() - stepStartTime;
        }
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
                null,
                ctx.grayResolution
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
                stackTrace,
                ctx.grayResolution
        );
    }

    private void savePendingLog(ExecutionContext ctx, DecisionAsyncPendingException asyncFlowEx) {
        ctx.totalExecutionTime = System.currentTimeMillis() - ctx.totalStartTime;

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

        Map<String, Object> pendingInfo = new HashMap<>();
        pendingInfo.put("asyncFlowRunId", asyncFlowEx.getWaitRef());
        pendingInfo.put("userTaskNodeId", asyncFlowEx.getWaitRef());
        pendingInfo.put("pendingNodeId", asyncFlowEx.getCurrentNodeId());
        pendingInfo.put("waitType", asyncFlowEx.getWaitType());

        decisionLogService.saveDecisionLog(
                ctx.request.getUserId(),
                ctx.request.getOrderNo(),
                ctx.request.getFlowId(),
                asyncFlowEx.getWaitRef(),
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
                null,
                ctx.grayResolution
        );
    }

    private DecisionAsyncPendingException extractAsyncFlowPendingException(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof DecisionAsyncPendingException) {
                return (DecisionAsyncPendingException) current;
            }
            current = current.getCause();
        }
        return null;
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
                null,
                ctx.grayResolution
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

    private void recordMetrics(ExecutionContext ctx, String status) {
        try {
            String packageName = ctx.request.getRulePackagePath();
            String flowId = ctx.request.getFlowId();

            Timer.builder("rule.execution.latency")
                    .tag("package", packageName != null ? packageName : "unknown")
                    .tag("flow", flowId != null ? flowId : "unknown")
                    .tag("status", status)
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .publishPercentileHistogram()
                    .register(meterRegistry)
                    .record(ctx.totalExecutionTime, TimeUnit.MILLISECONDS);

            if (ctx.loadKnowledgeTime > 0) {
                Timer.builder("rule.execution.phase")
                        .tag("phase", "loadKnowledge")
                        .tag("package", packageName != null ? packageName : "unknown")
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry)
                        .record(ctx.loadKnowledgeTime, TimeUnit.MILLISECONDS);
            }
            if (ctx.flowExecutionTime > 0) {
                Timer.builder("rule.execution.phase")
                        .tag("phase", "flowExecution")
                        .tag("package", packageName != null ? packageName : "unknown")
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry)
                        .record(ctx.flowExecutionTime, TimeUnit.MILLISECONDS);
            }
            if (ctx.createSessionTime > 0) {
                Timer.builder("rule.execution.phase")
                        .tag("phase", "createSession")
                        .tag("package", packageName != null ? packageName : "unknown")
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry)
                        .record(ctx.createSessionTime, TimeUnit.MILLISECONDS);
            }
            if (ctx.insertEntityTime > 0) {
                Timer.builder("rule.execution.phase")
                        .tag("phase", "insertEntity")
                        .tag("package", packageName != null ? packageName : "unknown")
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry)
                        .record(ctx.insertEntityTime, TimeUnit.MILLISECONDS);
            }

            meterRegistry.counter("rule.execution.total",
                    "package", packageName != null ? packageName : "unknown",
                    "status", status
            ).increment();
        } catch (Exception e) {
            log.warn("记录监控指标失败", e);
        }
    }

    private void triggerShadowExecution(Long mainFlowLogId, DecisionRequest request) {
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
        DecisionRequest request;
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
        GrayResolution grayResolution;
        KnowledgeSession session;
        Map<String, LazyGeneralEntity> insertedEntities = new HashMap<>();
        OutputModel outputModel;
        String flowRunId;

        Map<String, Object> inputParams;
        Map<String, Object> resultData;
        Map<String, Object> entityDataMap;
        ExecutionResponseImpl response;
        List<MessageItem> execMessageItems;
    }
}
