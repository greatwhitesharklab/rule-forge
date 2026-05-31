package com.ruleforge.console.service;

import com.ruleforge.console.model.TestDataImportResult;
import com.ruleforge.model.library.variable.VariableCategory;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * 测试数据导入/导出服务
 */
public interface TestDataService {

    /**
     * 导入 Excel 测试数据，解析后存入数据库
     */
    TestDataImportResult importExcel(MultipartFile file, List<VariableCategory> variableCategories,
                                     String project, String packageId, String files) throws Exception;

    /**
     * 导出空白 Excel 模板
     */
    void exportTemplate(List<VariableCategory> variableCategories, OutputStream outputStream) throws Exception;

    /**
     * 导出批量测试结果 Excel
     */
    void exportTestResult(Long sessionId, List<VariableCategory> variableCategories,
                          Map<String, Integer> flowMap, OutputStream outputStream) throws Exception;
}
