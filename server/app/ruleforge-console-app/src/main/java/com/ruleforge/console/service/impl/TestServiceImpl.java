package com.ruleforge.console.service.impl;

import com.ruleforge.console.repository.ExternalRepository;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.console.model.ApplicationAllVariableCategoryMap;
import com.ruleforge.console.model.BatchTestFlowMap;
import com.ruleforge.console.model.SaveProcessItemDto;
import com.ruleforge.console.model.TestRuntimeErrorDto;
import com.ruleforge.console.service.TestService;
import com.ruleforge.engine.RuleExecutionResponse;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.engine.KnowledgeSession;
import com.ruleforge.engine.KnowledgeSessionFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestServiceImpl implements TestService {

    private final ExternalRepository externalRepository;

    /**
     * V7.21 — BPMN 决策流(FlowEngine)彻底删除后,doFlowTest 改走纯规则路径。
     *
     * <p>方法签名(含 flowId/flowMap 入参)保留不变,避免 BatchTest FLOW 模式
     * (FlowBatchTestSubject / BatchTestServiceImpl)的调用方大面积改动;
     * flowId/flowMap 入参不再参与执行,仅作签名兼容。
     */
    @Override
    public SaveProcessItemDto doFlowTest(KnowledgePackage knowledgePackage, String flowId, ApplicationAllVariableCategoryMap row, BatchTestFlowMap flowMap) throws Exception {
        long start = System.currentTimeMillis();

        KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(knowledgePackage);

        SaveProcessItemDto saveProcessItemModel = new SaveProcessItemDto();
        Map<String, Object> parameterMap = null;
        for (String name : row.keySet()) {
            Object fact = row.get(name);
            if ((fact instanceof Map) && !(fact instanceof GeneralEntity)) {
                parameterMap = (Map<String, Object>) fact;
            } else if (fact != null) {
                session.insert(fact);

                if (name.equals("输出信息")) {
                    saveProcessItemModel.setOutputModel((GeneralEntity) fact);
                }
            }
        }

        RuleExecutionResponse response;
        if (parameterMap == null) {
            response = session.fireRules();
        } else {
            response = session.fireRules(parameterMap);
        }
        // 把触发规则数 + 耗时写回行(供批量测试结果展示)
        Map<String, Object> result = new HashMap<>();
        result.put("触发规则数", response.getFiredRules().size());
        long elapse = System.currentTimeMillis() - start;
        result.put("耗时", elapse);
        row.put("测试结果", result);

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
