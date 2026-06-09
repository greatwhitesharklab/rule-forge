package com.ruleforge.console.batchtest.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.ruleforge.console.model.ApplicationAllVariableCategoryMap;
import com.ruleforge.console.util.ExcelUtils;
import com.ruleforge.model.library.variable.VariableCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel 解析器 — v5.8.4 BatchTest Excel upload
 *
 * 按 (subject, inputSource) 决定如何解析上传的 .xlsx:
 *
 *   - DATASOURCE+FILE   → 固定列 {@code entityId, fieldName, clazz?}
 *   - FLOW+DATASOURCE   → 固定列 {@code inputField}(默认 entityId)+ 其他列
 *   - FLOW+FILE         → 透传 {@link ExcelUtils#readTestDataExcel}
 *
 * 不是 {@code @Component} — 静态工具类,按需在 orchestrator 里 new。
 * 原因:依赖参数(inputField / variableCategories)由调用方传,不放在 Spring 容器里。
 *
 * 容量上限 10000 行(与 {@code TestDataAnalysisEventListener.MAX_ROWS} 对齐)。
 */
@Slf4j
public class ExcelRowParser {

    public static final int MAX_ROWS = 10_000;

    /**
     * DATASOURCE+FILE:Excel 三列 entityId, fieldName, clazz(optional) → List<Map>
     */
    public List<Map<String, Object>> forDatasourceOnly(MultipartFile file) throws IOException {
        SheetData data = readFirstSheet(file);
        if (!data.indexByName.containsKey("entityId")) {
            throw new IllegalArgumentException(
                    "DATASOURCE+FILE Excel 必须含 'entityId' 列(实际表头:" + data.indexByName.keySet() + ")");
        }
        if (!data.indexByName.containsKey("fieldName")) {
            throw new IllegalArgumentException(
                    "DATASOURCE+FILE Excel 必须含 'fieldName' 列(实际表头:" + data.indexByName.keySet() + ")");
        }
        List<Map<String, Object>> result = new ArrayList<>(data.rows.size());
        for (List<String> row : data.rows) {
            Map<String, Object> map = new HashMap<>();
            map.put("entityId", cell(data.indexByName.get("entityId"), row));
            map.put("fieldName", cell(data.indexByName.get("fieldName"), row));
            // clazz 可选
            Integer clazzIdx = data.indexByName.get("clazz");
            map.put("clazz", cell(clazzIdx, row));
            result.add(map);
        }
        log.debug("ExcelRowParser.forDatasourceOnly: rows={}", result.size());
        return result;
    }

    /**
     * FLOW+DATASOURCE:Excel 第一列 = inputField(默认 entityId),其他列原样保留
     */
    public List<Map<String, Object>> forFlowWithDatasource(MultipartFile file, String inputField) throws IOException {
        String field = (inputField == null || inputField.isBlank()) ? "entityId" : inputField;
        SheetData data = readFirstSheet(file);
        if (!data.indexByName.containsKey(field)) {
            throw new IllegalArgumentException(
                    "FLOW+DATASOURCE Excel 必须含 '" + field + "' 列(实际表头:" + data.indexByName.keySet() + ")");
        }
        // 保留所有列,key = header 文本
        List<Map<String, Object>> result = new ArrayList<>(data.rows.size());
        for (List<String> row : data.rows) {
            Map<String, Object> map = new LinkedHashMap<>();
            // 按表头顺序写,保证 entityId 在前
            for (Map.Entry<Integer, String> header : data.headers.entrySet()) {
                map.put(header.getValue(), cell(header.getKey(), row));
            }
            result.add(map);
        }
        log.debug("ExcelRowParser.forFlowWithDatasource inputField={} rows={}", field, result.size());
        return result;
    }

    /**
     * FLOW+FILE:透传 ExcelUtils.readTestDataExcel,需要传 variableCategories
     */
    public List<ApplicationAllVariableCategoryMap> forFlowWithFile(
            MultipartFile file, List<VariableCategory> variableCategories) throws Exception {
        if (variableCategories == null || variableCategories.isEmpty()) {
            throw new IllegalArgumentException(
                    "FLOW+FILE Excel 需要 inputConfig.variableCategories 描述变量库");
        }
        return ExcelUtils.readTestDataExcel(file, variableCategories)
                .getApplicationAllVariableCategoryMapList();
    }

    // ── 内部 helper ──

    /**
     * 读第一张 sheet 的表头 + 数据行(行内容是按表头列索引的 List<String>)
     */
    private SheetData readFirstSheet(MultipartFile file) throws IOException {
        HeaderCapturingListener listener = new HeaderCapturingListener();
        EasyExcel.read(file.getInputStream(), listener)
                .sheet(0)  // 第一张 sheet
                .doRead();
        return new SheetData(listener.header, listener.indexByName, listener.rows);
    }

    private static String cell(Integer colIdx, List<String> row) {
        if (colIdx == null || colIdx >= row.size()) return null;
        String v = row.get(colIdx);
        if (v == null) return null;
        String trimmed = v.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * headers: 按列序的 header 列表(Integer 列索引 → header 文本)
     * indexByName: 反向索引(header 文本 → Integer 列索引)
     * rows: 每一行按列序的 cell 列表
     */
    private record SheetData(
            Map<Integer, String> headers,
            Map<String, Integer> indexByName,
            List<List<String>> rows) {}

    /**
     * 抓 Excel 表头 + 行内容。EasyExcel 官方 listener 必须实现 invokeHeadMap + invoke + doAfterAllAnalysed。
     */
    private static class HeaderCapturingListener extends AnalysisEventListener<Map<Integer, String>> {
        Map<Integer, String> header = Collections.emptyMap();
        Map<String, Integer> indexByName = Collections.emptyMap();
        final List<List<String>> rows = new ArrayList<>();

        @Override
        public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
            // 保留原始列序
            Map<Integer, String> ordered = new LinkedHashMap<>();
            headMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> ordered.put(e.getKey(), e.getValue().trim()));
            this.header = ordered;
            // 反向索引(name → idx)
            Map<String, Integer> idx = new LinkedHashMap<>();
            ordered.forEach((k, v) -> idx.put(v, k));
            this.indexByName = idx;
        }

        @Override
        public void invoke(Map<Integer, String> data, AnalysisContext context) {
            if (rows.size() >= MAX_ROWS) {
                return;  // 截断
            }
            int maxCol = header.isEmpty() ? 0 : Collections.max(header.keySet());
            List<String> row = new ArrayList<>(maxCol + 1);
            for (int i = 0; i <= maxCol; i++) {
                row.add(data.get(i));
            }
            rows.add(row);
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            // no-op
        }
    }
}
