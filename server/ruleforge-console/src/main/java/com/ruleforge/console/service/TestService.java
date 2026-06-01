package com.ruleforge.console.service;

import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.console.model.ApplicationAllVariableCategoryMap;
import com.ruleforge.console.model.BatchTestFlowMap;
import com.ruleforge.console.model.SaveProcessItemDto;

import java.util.List;
import java.util.Map;

public interface TestService {

    SaveProcessItemDto doFlowTest(KnowledgePackage knowledgePackage, String flowId,
                                  ApplicationAllVariableCategoryMap row, BatchTestFlowMap flowMap) throws Exception;

    Map<String, Object> doBatchFlowTest(String packageId, KnowledgePackage knowledgePackage, String flowId,
                                        List<ApplicationAllVariableCategoryMap> rowList, BatchTestFlowMap flowMap) throws Exception;

}
