package com.ruleforge.console.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.console.model.ApplicationAllVariableCategoryMap;
import com.ruleforge.console.model.ReadTestDataExcelResult;
import com.ruleforge.console.model.TestDataImportErrorMsgDto;
import com.ruleforge.console.model.TestRowId2VariableCategoryMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Excel工具类，统一使用EasyExcel处理Excel读写操作
 * 替代原有的Apache POI实现，提供更好的性能和内存使用
 *
 * @author Fred
 * @since 2025/8/17 14:30
 */
@Slf4j
public class ExcelUtils {

    /**
     * 读取Excel文件，支持多Sheet
     *
     * @param file                 Excel文件
     * @param variableCategoryList 变量类别映射
     * @return 解析后的数据列表
     */
    public static ReadTestDataExcelResult readTestDataExcel(MultipartFile file, List<VariableCategory> variableCategoryList) throws IOException {
        Map<String, VariableCategory> variableCategoryMap = new HashMap<>();
        for (VariableCategory vc : variableCategoryList) {
            variableCategoryMap.put(vc.getName(), vc);
        }

        // 使用EasyExcel读取所有Sheet数据
        TestDataAnalysisEventListener testDataAnalysisEventListener = new TestDataAnalysisEventListener(variableCategoryMap);
        EasyExcel
                .read(file.getInputStream(), testDataAnalysisEventListener)
                .registerConverter(new CustomListConverter())
                .doReadAll();

        return new ReadTestDataExcelResult(new ArrayList<>(testDataAnalysisEventListener.getDataMap().values()),
                testDataAnalysisEventListener.getErrorMsgDtoList());
    }

    /**
     * 写入Excel文件，支持多Sheet
     *
     * @param outputStream 输出流
     * @param sheetDataMap Sheet数据映射
     */
    public static void writeExcel(OutputStream outputStream, Map<String, List<List<Object>>> sheetDataMap) {
        try {
            ExcelWriter excelWriter = EasyExcel.write(outputStream).build();

            int sheetIndex = 0;
            for (Map.Entry<String, List<List<Object>>> entry : sheetDataMap.entrySet()) {
                String sheetName = entry.getKey();
                List<List<Object>> data = entry.getValue();

                WriteSheet writeSheet = EasyExcel.writerSheet(sheetIndex, sheetName)
//                        .head((List<List<String>>) (data.isEmpty() ? Collections.singletonList(new ArrayList<>()) : Collections.singletonList(data.get(0))))
                        .registerWriteHandler(createHeaderStyle())
                        .build();

                // 写入数据（跳过表头）
                if (data.size() > 1) {
                    excelWriter.write(data.subList(1, data.size()), writeSheet);
                }

                sheetIndex++;
            }

            excelWriter.finish();
        } catch (Exception e) {
            log.error("写入Excel文件失败", e);
            throw new RuntimeException("写入Excel文件失败", e);
        }
    }

    /**
     * 根据变量分类写入Excel数据到输出流，支持模板和数据导出
     *
     * @param variableCategories 变量分类列表
     * @param dataList           数据映射，key为变量分类名称，value为对应的数据列表。如果为null则只导出模板
     * @param outputStream       输出流
     * @throws Exception 生成异常
     */
    public static void writeExcelWithVariableCategories(List<VariableCategory> variableCategories,
                                                        List<ApplicationAllVariableCategoryMap> dataList,
                                                        List<TestDataImportErrorMsgDto> errorMsgDtoList,
                                                        Map<String, Integer> flowMap,
                                                        OutputStream outputStream) throws Exception {
        if (variableCategories == null || variableCategories.isEmpty()) {
            throw new IllegalArgumentException("变量分类列表不能为空");
        }

        Map<String, List<TestDataImportErrorMsgDto>> errorMsgSheetNameMap = new HashMap<>();
        if (errorMsgDtoList != null) {
            for (TestDataImportErrorMsgDto errorMsgDto : errorMsgDtoList) {
                errorMsgSheetNameMap.computeIfAbsent(errorMsgDto.getSheetName(), k -> new ArrayList<>()).add(errorMsgDto);
            }
        }
        Map<String, TestRowId2VariableCategoryMap> variableCategoryNameDataMap = buildVariableCategoryNameDataMap(dataList);

        Map<String, HeaderInfo> sheetHeadMap = new HashMap<>();
        Map<String, List<List<Object>>> sheetDataMap = new HashMap<>();
        Map<String, Set<String>> sheetErrorDataSetMap = new HashMap<>();
        for (VariableCategory vc : variableCategories) {
            // 构建表头字段列表
            HeaderInfo headerInfo = buildHeaderInfo(vc);
            sheetHeadMap.put(vc.getName(), headerInfo);

            if (variableCategoryNameDataMap.containsKey(vc.getName())) {
                // 有数据时，创建包含数据的Sheet
                List<List<Object>> sheetData = buildSheetData(variableCategoryNameDataMap.get(vc.getName()), headerInfo.getVariableNameList());
                Set<String> sheetErrorDataSet = buildSheetErrorDataSet(errorMsgSheetNameMap.get(vc.getName()), headerInfo);

                if (!sheetData.isEmpty()) {
                    sheetDataMap.put(vc.getName(), sheetData);
                }
                if (!sheetErrorDataSet.isEmpty()) {
                    sheetErrorDataSetMap.put(vc.getName(), sheetErrorDataSet);
                }
            }
        }

        ExcelWriterBuilder excelWriterBuilder = EasyExcel
                .write(outputStream)
                .registerConverter(new CustomListConverter())
                .registerWriteHandler(new CustomCellColorHandler(sheetErrorDataSetMap));
        try (ExcelWriter excelWriter = excelWriterBuilder.build()) {
            int sheetIndex = 0;
            for (Map.Entry<String, HeaderInfo> headerInfoEntry : sheetHeadMap.entrySet()) {
                String sheetName = headerInfoEntry.getKey();
                HeaderInfo headerInfo = headerInfoEntry.getValue();

                WriteSheet writeSheet = EasyExcel.writerSheet(sheetIndex, sheetName)
                        .head(headerInfo.getHeadList())
                        .registerWriteHandler(createHeaderStyle())
                        .build();

                if (sheetDataMap.containsKey(sheetName)) {
                    // 有数据时，创建包含数据的Sheet
                    excelWriter.write(sheetDataMap.get(sheetName), writeSheet);
                } else {
                    // 模板只有表头，不需要写入数据行
                    excelWriter.write(Collections.emptyList(), writeSheet);
                }

                sheetIndex++;
            }
        }
    }

    /**
     * 表头信息内部类
     */
    @Getter
    private static class HeaderInfo {
        private final List<List<String>> headList;
        private final List<String> variableNameList;
        private final Map<String, Variable> variableLabelNameMap;

        public HeaderInfo(List<List<String>> headList, List<String> variableNameList, Map<String, Variable> variableLabelNameMap) {
            this.headList = headList;
            this.variableNameList = variableNameList;
            this.variableLabelNameMap = variableLabelNameMap;
        }
    }

    /**
     * 构建表头信息
     *
     * @param vc 变量分类
     * @return 表头信息
     */
    private static HeaderInfo buildHeaderInfo(VariableCategory vc) {
        Map<String, Variable> variableLabelMap = new LinkedHashMap<>();
        Map<String, Variable> variableNameMap = new LinkedHashMap<>();
        for (Variable variable : vc.getVariables()) {
            variableLabelMap.put(variable.getLabel(), variable);
            variableNameMap.put(variable.getName(), variable);
        }
        List<List<String>> headList = new LinkedList<>();
        for (String label : variableLabelMap.keySet()) {
            List<String> labelList = new ArrayList<>();
            labelList.add(label);
            headList.add(labelList);
        }
        List<String> variableNameList = new LinkedList<>(variableNameMap.keySet());

        return new HeaderInfo(headList, variableNameList, variableLabelMap);
    }

    /**
     * 构建Sheet数据
     *
     * @param testRowId2VariableCategoryMap 测试行ID到变量分类映射
     * @param variableNameList              变量名称列表
     * @return Sheet数据
     */
    private static List<List<Object>> buildSheetData(TestRowId2VariableCategoryMap testRowId2VariableCategoryMap, List<String> variableNameList) {
        List<List<Object>> sheetData = new ArrayList<>(testRowId2VariableCategoryMap.size());
        int rowMax = Collections.max(testRowId2VariableCategoryMap.keySet());
        for (int i = 0; i < rowMax; i++) {
            sheetData.add(new ArrayList<>(Collections.nCopies(variableNameList.size(), null)));
        }

        for (Map.Entry<Integer, Object> entry : testRowId2VariableCategoryMap.entrySet()) {
            Map<String, Object> categoryData = (Map<String, Object>) entry.getValue();
            int sheetRowId = entry.getKey() - 1;
            for (Map.Entry<String, Object> categoryDataEntry : categoryData.entrySet()) {
                int sheetFieldId = variableNameList.indexOf(categoryDataEntry.getKey());
                sheetData.get(sheetRowId).set(sheetFieldId, categoryDataEntry.getValue());
            }
        }

        return sheetData;
    }

    private static Set<String> buildSheetErrorDataSet(List<TestDataImportErrorMsgDto> errorMsgDtoList, HeaderInfo headerInfo) {
        if (errorMsgDtoList == null || errorMsgDtoList.isEmpty()) {
            return new HashSet<>();
        }

        Set<String> errorDataSet = new HashSet<>(errorMsgDtoList.size());
        for (TestDataImportErrorMsgDto errorMsgDto : errorMsgDtoList) {
            String variableName = headerInfo.getVariableLabelNameMap().get(errorMsgDto.getSheetFieldName()).getName();
            int fieldId = headerInfo.getVariableNameList().indexOf(variableName);
            errorDataSet.add(errorMsgDto.getSheetRowId() + "," + fieldId);
        }
        return errorDataSet;
    }

    /**
     * 构建变量分类名称数据映射
     *
     * @param dataList 应用所有变量分类映射列表
     * @return 变量分类名称数据映射
     */
    private static Map<String, TestRowId2VariableCategoryMap> buildVariableCategoryNameDataMap(List<ApplicationAllVariableCategoryMap> dataList) {
        Map<String, TestRowId2VariableCategoryMap> variableCategoryNameDataMap = new LinkedHashMap<>();
        if (dataList == null) {
            return variableCategoryNameDataMap;
        }

        for (int i = 0; i < dataList.size(); i++) {
            ApplicationAllVariableCategoryMap applicationAllVariableCategoryMap = dataList.get(i);
            for (Map.Entry<String, Object> vc : applicationAllVariableCategoryMap.entrySet()) {
                variableCategoryNameDataMap.computeIfAbsent(vc.getKey(), k -> new TestRowId2VariableCategoryMap()).put(i + 1, vc.getValue());
            }
        }
        return variableCategoryNameDataMap;
    }

    /**
     * 创建表头样式
     *
     * @return 表格样式
     */
    private static HorizontalCellStyleStrategy createHeaderStyle() {
        // 表头样式
        WriteCellStyle headWriteCellStyle = new WriteCellStyle();
        headWriteCellStyle.setFillForegroundColor(IndexedColors.SEA_GREEN.getIndex());
        headWriteCellStyle.setFillPatternType(FillPatternType.SOLID_FOREGROUND);

        WriteFont headWriteFont = new WriteFont();
        headWriteFont.setFontHeightInPoints((short) 12);
        headWriteFont.setBold(true);
        headWriteCellStyle.setWriteFont(headWriteFont);

        // 内容样式
        WriteCellStyle contentWriteCellStyle = new WriteCellStyle();
        WriteFont contentWriteFont = new WriteFont();
        contentWriteFont.setFontHeightInPoints((short) 11);
        contentWriteCellStyle.setWriteFont(contentWriteFont);

        return new HorizontalCellStyleStrategy(headWriteCellStyle, contentWriteCellStyle);
    }
}
