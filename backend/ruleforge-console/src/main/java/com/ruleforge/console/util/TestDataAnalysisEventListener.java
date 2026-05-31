package com.ruleforge.console.util;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.console.model.ApplicationAllVariableCategoryMap;
import com.ruleforge.console.model.TestDataImportErrorMsgDto;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * EasyExcel 测试数据解析监听器。
 *
 * 修复内容：
 * 1. 使用逻辑行号（AtomicInteger），避免多 Sheet 行号冲突
 * 2. 类型转换失败时放入 null，不再放入原始字符串
 * 3. 增加数据量上限 MAX_ROWS = 10000
 * 4. Sheet 名匹配支持 trim + 忽略大小写
 * 5. testRowData 的 key 使用 vc.getName() 而非原始 sheet 名
 *
 * @author Fred
 * @since 2025/8/24 21:55
 */
@Slf4j
public class TestDataAnalysisEventListener extends AnalysisEventListener<Map<Integer, String>> {

    public static final int MAX_ROWS = 10000;

    private final Map<String, VariableCategory> variableCategoryMap;
    @Getter
    private final Map<Integer, ApplicationAllVariableCategoryMap> dataMap = new HashMap<>();
    @Getter
    private final List<TestDataImportErrorMsgDto> errorMsgDtoList = new ArrayList<>();
    @Getter
    private boolean maxRowsExceeded = false;

    private VariableCategory vc;
    private Map<Integer, String> headMap;
    private final Map<String, Variable> vcFieldMap = new HashMap<>();
    private final AtomicInteger logicalRowCounter = new AtomicInteger(0);

    public TestDataAnalysisEventListener(Map<String, VariableCategory> variableCategoryMap) {
        this.variableCategoryMap = variableCategoryMap;
    }

    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        this.logicalRowCounter.set(0);
        this.vcFieldMap.clear();

        String sheetName = context.readSheetHolder().getSheetName();
        VariableCategory vc = resolveVariableCategory(sheetName);
        if (vc == null) {
            log.error("Variable category [{}] not exist.", sheetName);
            this.vc = null;
        } else {
            this.vc = vc;
            for (Variable variable : vc.getVariables()) {
                this.vcFieldMap.put(variable.getLabel(), variable);
            }
            this.headMap = headMap;
        }
    }

    @Override
    public void invoke(Map<Integer, String> data, AnalysisContext context) {
        if (this.vc == null || maxRowsExceeded) {
            return;
        }

        int rowIndex = logicalRowCounter.incrementAndGet();
        if (rowIndex > MAX_ROWS) {
            maxRowsExceeded = true;
            log.warn("Excel 数据超过 {} 行上限，后续行被忽略", MAX_ROWS);
            return;
        }

        Map<String, Object> entity;
        if (vc.getName().equals(VariableCategory.PARAM_CATEGORY)) {
            entity = new HashMap<>();
        } else {
            entity = new GeneralEntity(vc.getClazz());
        }

        Map<String, Object> testRowData = dataMap
                .computeIfAbsent(rowIndex, k -> new ApplicationAllVariableCategoryMap());

        for (Map.Entry<Integer, String> entry : data.entrySet()) {
            if (entry.getValue() != null && vcFieldMap.containsKey(headMap.get(entry.getKey()))) {
                String fieldName = headMap.get(entry.getKey());
                Variable variable = vcFieldMap.get(fieldName);

                try {
                    entity.put(variable.getName(), variable.getType().convert(entry.getValue()));
                } catch (Exception e) {
                    entity.put(variable.getName(), null);
                    TestDataImportErrorMsgDto errorMsgDto = new TestDataImportErrorMsgDto(
                            vc.getName(),
                            rowIndex,
                            fieldName,
                            e.getMessage());
                    errorMsgDtoList.add(errorMsgDto);
                    log.error("invoke {}", errorMsgDto);
                }
            }
        }
        testRowData.put(vc.getName(), entity);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
    }

    /**
     * trim + 忽略大小写匹配 VariableCategory
     */
    private VariableCategory resolveVariableCategory(String sheetName) {
        if (sheetName == null) {
            return null;
        }
        String trimmed = sheetName.trim();
        for (Map.Entry<String, VariableCategory> entry : variableCategoryMap.entrySet()) {
            if (entry.getKey().trim().equalsIgnoreCase(trimmed)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
