package com.ruleforge.console.service.impl;

import com.ruleforge.console.repository.ExternalRepository;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.RuleInfo;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.response.RuleExecutionResponse;
import com.ruleforge.console.model.ApplicationAllVariableCategoryMap;
import com.ruleforge.console.model.BatchTestFlowMap;
import com.ruleforge.console.model.SaveProcessItemDto;
import com.ruleforge.console.model.TestRuntimeErrorDto;
import com.ruleforge.console.service.TestService;
import com.ruleforge.decision.entity.DecisionFlowState;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.engine.FlowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestServiceImpl implements TestService {

    private final ExternalRepository externalRepository;
    // V5.21+: 自建决策流执行器(原 Flowable RuntimeService)
    private final FlowEngine flowEngine;

    @Override
    public SaveProcessItemDto doFlowTest(KnowledgePackage knowledgePackage, String flowId, ApplicationAllVariableCategoryMap row, BatchTestFlowMap flowMap) throws Exception {
        long start = System.currentTimeMillis();

        // 获取session
        KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(knowledgePackage);

        SaveProcessItemDto saveProcessItemModel = new SaveProcessItemDto();
        Map<String, Object> parameterMap = null;
        for (String name : row.keySet()) {
            Object fact = row.get(name);
            if ((fact instanceof Map) && !(fact instanceof GeneralEntity)) {
                parameterMap = (Map<String, Object>) fact;
            } else if (fact != null) {
                session.insert(fact);

                // 记录订单号
                if (name.equals("输出信息")) {
                    saveProcessItemModel.setOutputModel((GeneralEntity) fact);
                }
            }
        }

        if (StringUtils.hasText(flowId)) {
            saveProcessItemModel.setFlowId(flowId);

            // V5.21+: 走自建 FlowEngine(原 Flowable startProcessInstanceByKey + getVariables)
            // RuleNodeExecutor 对 outputModel == null 走 NoOp 兜底(见 RuleNodeExecutor.java line 67),
            // 守住模块边界:console-app 不引入 executor-app 的 OutputModel。
            Map<String, Object> flowVariables = new HashMap<>();
            for (String name : row.keySet()) {
                Object fact = row.get(name);
                if (fact instanceof GeneralEntity) {
                    flowVariables.put(((GeneralEntity) fact).getTargetClass(), fact);
                } else if (fact instanceof Map) {
                    flowVariables.putAll((Map<String, Object>) fact);
                }
            }
            FlowContext flowCtx = new FlowContext();
            flowCtx.setFlowRunId(UUID.randomUUID().toString());
            flowCtx.setVars(new HashMap<>(flowVariables));
            flowCtx.setSession(session);
            DecisionFlowState state = flowEngine.start(flowId, flowCtx);
            if (DecisionFlowState.STATUS_FAILED.equals(state.getStatus())) {
                throw new FlowExecutionException(state.getErrorMessage());
            }
            Map<String, Object> resultVars = flowCtx.getVars();
            session.getParameters().putAll(resultVars);
            row.put(VariableCategory.PARAM_CATEGORY, session.getParameters());

            Map<String, Object> result = new HashMap<>();
            // Record fired rules from FlowEngine output vars
            Object firedRules = resultVars.get("_firedRules");
            if (firedRules != null) {
                result.put("触发规则数", firedRules);
            }
            long end = System.currentTimeMillis();
            long elapse = end - start;

            result.put("耗时", elapse);
            row.put("测试结果", result);
        }

        // 返回决策流水
        saveProcessItemModel.setMessageItemList(session.getExecMessageItems());
        return saveProcessItemModel;
    }

    @Override
    public Map<String, Object> doBatchFlowTest(String packageId, KnowledgePackage knowledgePackage, String flowId, List<ApplicationAllVariableCategoryMap> rowList, BatchTestFlowMap flowMap) throws Exception {
        List<TestRuntimeErrorDto> errorList = new ArrayList<>();

        int i = 1;
        int maxItemNum = 500;
        List<SaveProcessItemDto> saveProcessItemDtoList = new ArrayList<>(maxItemNum);
        for (ApplicationAllVariableCategoryMap datum : rowList) {
            // 试算每条数据
            try {
                SaveProcessItemDto saveProcessItemDto = doFlowTest(knowledgePackage, flowId, datum, flowMap);
                saveProcessItemDto.setPackageId(packageId);
                saveProcessItemDtoList.add(saveProcessItemDto);
                if (saveProcessItemDtoList.size() >= maxItemNum) {
                    externalRepository.saveProcessItem(saveProcessItemDtoList);
                    saveProcessItemDtoList.clear();
                }
            } catch (RuleException e) {
                log.error("test batch - single: {} {}", e.getLabel(), e.getVal());
                errorList.add(new TestRuntimeErrorDto(i, e.getTipMsg()));
            }

            i++;
        }
        if (!saveProcessItemDtoList.isEmpty()) {
            externalRepository.saveProcessItem(saveProcessItemDtoList);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("flowMap", flowMap);
        result.put("errorList", errorList);
        return result;
    }
}
