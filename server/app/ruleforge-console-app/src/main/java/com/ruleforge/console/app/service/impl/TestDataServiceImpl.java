package com.ruleforge.console.app.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.entity.BatchTestRowEntity;
import com.ruleforge.console.app.entity.BatchTestSessionEntity;
import com.ruleforge.console.app.mapper.BatchTestRowMapper;
import com.ruleforge.console.app.mapper.BatchTestSessionMapper;
import com.ruleforge.console.model.TestDataImportResult;
import com.ruleforge.console.service.TestDataService;
import com.ruleforge.console.model.ApplicationAllVariableCategoryMap;
import com.ruleforge.console.model.ReadTestDataExcelResult;
import com.ruleforge.console.util.ExcelUtils;
import com.ruleforge.model.library.variable.VariableCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试数据导入/导出服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestDataServiceImpl implements TestDataService {

    private final BatchTestSessionMapper sessionMapper;
    private final BatchTestRowMapper rowMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public TestDataImportResult importExcel(MultipartFile file, List<VariableCategory> variableCategories,
                                            String project, String packageId, String files) throws Exception {
        // 1. 解析 Excel
        ReadTestDataExcelResult parseResult = ExcelUtils.readTestDataExcel(file, variableCategories);
        List<ApplicationAllVariableCategoryMap> rows = parseResult.getApplicationAllVariableCategoryMapList();

        // 2. 创建会话
        BatchTestSessionEntity session = new BatchTestSessionEntity();
        session.setProject(project);
        session.setPackageId(packageId);
        session.setFiles(files);
        session.setStatus(BatchTestSessionEntity.STATUS_UPLOADED);
        session.setTotalRows(rows.size());
        session.setErrorCount(parseResult.getErrorMsgDtoList().size());
        session.setProgress(0.0);
        sessionMapper.insert(session);

        // 3. 批量写入行数据
        int rowIndex = 1;
        for (ApplicationAllVariableCategoryMap row : rows) {
            BatchTestRowEntity rowEntity = new BatchTestRowEntity();
            rowEntity.setSessionId(session.getId());
            rowEntity.setRowIndex(rowIndex++);
            rowEntity.setInputData(serializeRow(row));
            rowEntity.setStatus(BatchTestRowEntity.STATUS_PENDING);
            rowMapper.insert(rowEntity);
        }

        return new TestDataImportResult(
                session.getId(),
                rows.size(),
                parseResult.getErrorMsgDtoList(),
                false
        );
    }

    @Override
    public void exportTemplate(List<VariableCategory> variableCategories, OutputStream outputStream) throws Exception {
        ExcelUtils.writeExcelWithVariableCategories(variableCategories, null, null, null, outputStream);
    }

    @Override
    public void exportTestResult(Long sessionId, List<VariableCategory> variableCategories,
                                 Map<String, Integer> flowMap, OutputStream outputStream) throws Exception {
        // 查询所有行
        List<Map<String, Object>> rowMaps = rowMapper.selectBySessionId(sessionId);
        if (rowMaps.isEmpty()) {
            ExcelUtils.writeExcelWithVariableCategories(variableCategories, null, null, null, outputStream);
            return;
        }

        // 反序列化为 ApplicationAllVariableCategoryMap 列表
        List<ApplicationAllVariableCategoryMap> dataList = new ArrayList<>(rowMaps.size());
        for (Map<String, Object> rowMap : rowMaps) {
            String inputData = (String) rowMap.get("input_data");
            ApplicationAllVariableCategoryMap inputRow = deserializeRow(inputData, variableCategories);

            // 合并输出数据
            String outputData = (String) rowMap.get("output_data");
            if (outputData != null) {
                ApplicationAllVariableCategoryMap outputRow = deserializeRow(outputData, variableCategories);
                inputRow.putAll(outputRow);
            }

            dataList.add(inputRow);
        }

        ExcelUtils.writeExcelWithVariableCategories(variableCategories, dataList, null, flowMap, outputStream);
    }

    /**
     * 序列化行数据为 JSON
     */
    private String serializeRow(ApplicationAllVariableCategoryMap row) {
        try {
            return objectMapper.writeValueAsString(row);
        } catch (JsonProcessingException e) {
            log.error("序列化测试数据行失败", e);
            return "{}";
        }
    }

    /**
     * 反序列化 JSON 为行数据，将普通 HashMap 转换为 GeneralEntity
     */
    @SuppressWarnings("unchecked")
    private ApplicationAllVariableCategoryMap deserializeRow(String json, List<VariableCategory> variableCategories) {
        try {
            Map<String, Object> rawMap = objectMapper.readValue(json, Map.class);
            ApplicationAllVariableCategoryMap result = new ApplicationAllVariableCategoryMap();

            for (VariableCategory vc : variableCategories) {
                Object rawValue = rawMap.get(vc.getName());
                if (rawValue == null) continue;

                if (VariableCategory.PARAM_CATEGORY.equals(vc.getName())) {
                    result.put(vc.getName(), rawValue);
                } else {
                    com.ruleforge.model.GeneralEntity entity = new com.ruleforge.model.GeneralEntity(vc.getClazz());
                    if (rawValue instanceof Map) {
                        entity.putAll((Map<String, Object>) rawValue);
                    }
                    result.put(vc.getName(), entity);
                }
            }
            return result;
        } catch (JsonProcessingException e) {
            log.error("反序列化测试数据行失败", e);
            return new ApplicationAllVariableCategoryMap();
        }
    }
}
