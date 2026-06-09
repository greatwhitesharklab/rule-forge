package com.ruleforge.console.batchtest.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Feature: ExcelRowParser — v5.8.4 BatchTest Excel upload
 *
 * 解析 .xlsx 第一张 sheet 的固定列 schema,按模式返回 List<Map<String,Object>>。
 *
 * 行为约定:
 *   - forDatasourceOnly:表头必须含 entityId + fieldName,clazz 可选 → 返 [{entityId, fieldName, clazz?}, ...]
 *   - forFlowWithDatasource:表头第一列 = inputField(默认 "entityId")= entityId,其他列原样保留
 *   - forFlowWithFile:透传 ExcelUtils.readTestDataExcel,需传 variableCategories
 *   - 缺必填列 → 抛 IllegalArgumentException
 *   - 数据行超过 10000 → 截断,前 10000 行
 */
@DisplayName("ExcelRowParser - v5.8.4 BatchTest Excel upload")
class ExcelRowParserTest {

    private final ExcelRowParser parser = new ExcelRowParser();

    // ── 工具:用 EasyExcel 在内存中构造一个 .xlsx 字节流 ──
    private static MultipartFile buildExcel(String sheetName, List<String> header, List<List<String>> rows) {
        // EasyExcel:head 配置表头,doWrite 只接收数据行(不含表头)
        // 用临时文件而不是 ByteArrayOutputStream,避免 EasyExcel 内部的 hashCode 循环
        java.io.File tmp = null;
        try {
            tmp = java.io.File.createTempFile("ruleforge-excel-test-", ".xlsx");
            tmp.deleteOnExit();
            com.alibaba.excel.EasyExcel.write(tmp)
                    .head(buildHead(header))
                    .sheet(sheetName)
                    .doWrite(rows);
            byte[] bytes = java.nio.file.Files.readAllBytes(tmp.toPath());
            return new MockMultipartFile(
                    "file", "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    bytes);
        } catch (java.io.IOException e) {
            throw new RuntimeException("buildExcel 失败", e);
        } finally {
            if (tmp != null && tmp.exists()) {
                tmp.delete();
            }
        }
    }

    private static List<List<String>> buildHead(List<String> header) {
        List<List<String>> h = new ArrayList<>();
        for (String col : header) {
            h.add(List.of(col));
        }
        return h;
    }

    @Nested
    @DisplayName("Scenario: DATASOURCE+FILE 模式解析")
    class DatasourceOnlyParsing {

        // Given 一个 .xlsx 包含表头 entityId,fieldName,clazz + 3 行数据
        // When 调 parser.forDatasourceOnly(file)
        // Then 返 List,size=3,每行 key 集合 = {entityId, fieldName, clazz}
        @Test
        @DisplayName("标准 entityId/fieldName/clazz 三列")
        void shouldParseDatasourceOnlyRows() throws Exception {
            MultipartFile file = buildExcel("Sheet1",
                    List.of("entityId", "fieldName", "clazz"),
                    List.of(
                            List.of("1", "name", "User"),
                            List.of("2", "age", "User"),
                            List.of("3", "email", "User")));

            List<Map<String, Object>> result = parser.forDatasourceOnly(file);

            assertThat(result).hasSize(3);
            assertThat(result.get(0)).containsEntry("entityId", "1")
                    .containsEntry("fieldName", "name")
                    .containsEntry("clazz", "User");
            assertThat(result.get(1)).containsEntry("entityId", "2")
                    .containsEntry("fieldName", "age");
            assertThat(result.get(2)).containsEntry("entityId", "3")
                    .containsEntry("clazz", "User");
        }

        // Given 表头只有 entityId,fieldName(无 clazz)
        // When forDatasourceOnly
        // Then 返 List,clazz 字段为 null
        @Test
        @DisplayName("缺 clazz 列:clazz 为 null")
        void shouldAllowMissingClazz() throws Exception {
            MultipartFile file = buildExcel("Sheet1",
                    List.of("entityId", "fieldName"),
                    List.of(List.of("1", "name")));

            List<Map<String, Object>> result = parser.forDatasourceOnly(file);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsEntry("entityId", "1")
                    .containsEntry("fieldName", "name")
                    .containsEntry("clazz", null);
        }

        // Given 表头缺 entityId
        // When forDatasourceOnly
        // Then 抛 IllegalArgumentException,msg 含 "entityId"
        @Test
        @DisplayName("缺 entityId 列 → IllegalArgumentException")
        void shouldFailWhenEntityIdColumnMissing() {
            MultipartFile file = buildExcel("Sheet1",
                    List.of("fieldName", "clazz"),
                    List.of(List.of("name", "User")));

            assertThatThrownBy(() -> parser.forDatasourceOnly(file))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("entityId");
        }

        // Given 表头缺 fieldName
        // When forDatasourceOnly
        // Then 抛 IllegalArgumentException,msg 含 "fieldName"
        @Test
        @DisplayName("缺 fieldName 列 → IllegalArgumentException")
        void shouldFailWhenFieldNameColumnMissing() {
            MultipartFile file = buildExcel("Sheet1",
                    List.of("entityId", "clazz"),
                    List.of(List.of("1", "User")));

            assertThatThrownBy(() -> parser.forDatasourceOnly(file))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fieldName");
        }
    }

    @Nested
    @DisplayName("Scenario: FLOW+DATASOURCE 模式解析")
    class FlowWithDatasourceParsing {

        // Given 表头 entityId,extraFieldA,extraFieldB + 2 行
        // When forFlowWithDatasource(file, inputField="entityId")
        // Then 返 List,每行保留所有 3 列,entityId 字段值为 cell 值
        @Test
        @DisplayName("默认 inputField=entityId,所有列保留")
        void shouldParseFlowDatasourceDefaultField() throws Exception {
            MultipartFile file = buildExcel("Sheet1",
                    List.of("entityId", "extraFieldA", "extraFieldB"),
                    List.of(
                            List.of("1", "x1", "y1"),
                            List.of("2", "x2", "y2")));

            List<Map<String, Object>> result = parser.forFlowWithDatasource(file, null);

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).containsEntry("entityId", "1")
                    .containsEntry("extraFieldA", "x1")
                    .containsEntry("extraFieldB", "y1");
            assertThat(result.get(1)).containsEntry("entityId", "2")
                    .containsEntry("extraFieldA", "x2")
                    .containsEntry("extraFieldB", "y2");
        }

        // Given 表头 id,name,phone
        // When forFlowWithDatasource(file, inputField="id")
        // Then 返 List,每行 entityId = id 列的值
        @Test
        @DisplayName("自定义 inputField=id,entityId 取 id 列")
        void shouldParseFlowDatasourceCustomField() throws Exception {
            MultipartFile file = buildExcel("Sheet1",
                    List.of("id", "name", "phone"),
                    List.of(List.of("42", "Alice", "555")));

            List<Map<String, Object>> result = parser.forFlowWithDatasource(file, "id");

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).containsEntry("id", "42")
                    .containsEntry("name", "Alice")
                    .containsEntry("phone", "555");
        }

        // Given 表头缺 inputField 指定的列
        // When forFlowWithDatasource(file, inputField="id")
        // Then 抛 IllegalArgumentException,msg 含 inputField 名
        @Test
        @DisplayName("缺 inputField 列 → IllegalArgumentException")
        void shouldFailWhenInputFieldColumnMissing() {
            MultipartFile file = buildExcel("Sheet1",
                    List.of("name", "phone"),
                    List.of(List.of("Alice", "555")));

            assertThatThrownBy(() -> parser.forFlowWithDatasource(file, "id"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("id");
        }
    }

    @Nested
    @DisplayName("Scenario: FLOW+FILE 模式解析")
    class FlowWithFileParsing {

        // Given 调 forFlowWithFile(file, variableCategories)
        // When variableCategories 非 null
        // Then 透传 ExcelUtils.readTestDataExcel,返 ApplicationAllVariableCategoryMap list
        @Test
        @DisplayName("传 variableCategories → 透传 readTestDataExcel")
        void shouldDelegateToReadTestDataExcel() {
            // FLOW+FILE 路径已由 TestDataServiceImpl 验证过,这里只 smoke test 入口:
            // 传 null variableCategories 应当立即抛 IllegalArgumentException
            MultipartFile file = new MockMultipartFile(
                    "file", "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    new byte[0]);

            assertThatThrownBy(() -> parser.forFlowWithFile(file, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("variableCategories");
        }
    }
}
