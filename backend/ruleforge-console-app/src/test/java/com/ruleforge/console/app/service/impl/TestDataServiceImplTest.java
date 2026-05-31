package com.ruleforge.console.app.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.entity.BatchTestRowEntity;
import com.ruleforge.console.app.entity.BatchTestSessionEntity;
import com.ruleforge.console.app.mapper.BatchTestRowMapper;
import com.ruleforge.console.app.mapper.BatchTestSessionMapper;
import com.ruleforge.console.model.TestDataImportResult;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: 测试数据导入服务
 *
 * TestDataService 负责 Excel 解析、DB 存储和导出。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TestDataServiceImpl - 测试数据导入/导出")
class TestDataServiceImplTest {

    @Mock private BatchTestSessionMapper sessionMapper;
    @Mock private BatchTestRowMapper rowMapper;
    @Mock private ObjectMapper objectMapper;
    @InjectMocks private TestDataServiceImpl service;

    private VariableCategory buildVariableCategory(String name, String clazz, String... varLabels) {
        VariableCategory vc = new VariableCategory();
        vc.setName(name);
        vc.setClazz(clazz);
        for (String label : varLabels) {
            Variable var = new Variable();
            var.setName(label.toLowerCase());
            var.setLabel(label);
            var.setType(com.ruleforge.model.library.Datatype.String);
            vc.addVariable(var);
        }
        return vc;
    }

    /** 创建只有表头的最小有效 Excel */
    private byte[] createMinimalExcel(String sheetName, String... headers) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ExcelWriter writer = EasyExcel.write(baos).build();
        List<List<String>> headList = new ArrayList<>();
        for (String h : headers) {
            headList.add(Collections.singletonList(h));
        }
        WriteSheet sheet = EasyExcel.writerSheet(0, sheetName).head(headList).build();
        writer.write(Collections.emptyList(), sheet);
        writer.finish();
        return baos.toByteArray();
    }

    /** 创建含数据的 Excel */
    private byte[] createExcelWithData(String sheetName, String[] headers, List<List<Object>> dataRows) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ExcelWriter writer = EasyExcel.write(baos).build();
        List<List<String>> headList = new ArrayList<>();
        for (String h : headers) {
            headList.add(Collections.singletonList(h));
        }
        WriteSheet sheet = EasyExcel.writerSheet(0, sheetName).head(headList).build();
        writer.write(dataRows, sheet);
        writer.finish();
        return baos.toByteArray();
    }

    @Nested
    @DisplayName("Scenario: 导入 Excel 成功")
    class ImportExcelSuccess {

        // Given 一个有效的 Excel 文件和变量分类
        // When importExcel 被调用
        // Then 创建 session 和 row 记录，返回 sessionId
        @Test
        @DisplayName("导入含数据的 Excel 时创建 session 和 rows")
        void shouldCreateSessionAndRows() throws Exception {
            // Given — Excel 有 2 行数据
            VariableCategory vc = buildVariableCategory("客户信息", "com.test.Customer", "Name", "Age");
            List<List<Object>> dataRows = List.of(
                    List.of("Alice", "25"),
                    List.of("Bob", "30")
            );
            byte[] excelBytes = createExcelWithData("客户信息", new String[]{"Name", "Age"}, dataRows);
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    excelBytes);

            when(sessionMapper.insert(any(BatchTestSessionEntity.class))).thenAnswer(inv -> {
                inv.getArgument(0, BatchTestSessionEntity.class).setId(100L);
                return 1;
            });
            when(rowMapper.insert(any(BatchTestRowEntity.class))).thenReturn(1);

            // When
            TestDataImportResult result = service.importExcel(file, List.of(vc), "project1", "pkg1", "file1.xml");

            // Then
            assertThat(result.getSessionId()).isEqualTo(100L);
            assertThat(result.getTotalRows()).isEqualTo(2);
            verify(sessionMapper, times(1)).insert(any(BatchTestSessionEntity.class));
            verify(rowMapper, times(2)).insert(any(BatchTestRowEntity.class));
        }
    }

    @Nested
    @DisplayName("Scenario: 导入空 Excel")
    class ImportEmptyExcel {

        // Given 只有表头的 Excel 文件
        // When importExcel 被调用
        // Then 创建 session 但 totalRows 为 0
        @Test
        @DisplayName("空 Excel（仅有表头）时 totalRows 为 0")
        void shouldCreateSessionWithZeroRows() throws Exception {
            // Given — 只有表头的 Excel
            VariableCategory vc = buildVariableCategory("参数", "param", "Key");
            byte[] excelBytes = createMinimalExcel("参数", "Key");
            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    excelBytes);

            when(sessionMapper.insert(any(BatchTestSessionEntity.class))).thenAnswer(inv -> {
                inv.getArgument(0, BatchTestSessionEntity.class).setId(200L);
                return 1;
            });

            // When
            TestDataImportResult result = service.importExcel(file, List.of(vc), "p", "pkg", "f");

            // Then
            assertThat(result.getTotalRows()).isEqualTo(0);
            verify(rowMapper, never()).insert(any(BatchTestRowEntity.class));
        }
    }

    @Nested
    @DisplayName("Scenario: session 字段正确映射")
    class SessionFieldMapping {

        // Given 导入参数 project/packageId/files
        // When session 被创建
        // Then 字段正确设置
        @Test
        @DisplayName("session 的 project/packageId/files/status 正确")
        void shouldMapSessionFieldsCorrectly() throws Exception {
            // Given
            VariableCategory vc = buildVariableCategory("参数", "param", "Key");
            byte[] excelBytes = createMinimalExcel("参数", "Key");
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    excelBytes);

            when(sessionMapper.insert(any(BatchTestSessionEntity.class))).thenAnswer(inv -> {
                inv.getArgument(0, BatchTestSessionEntity.class).setId(300L);
                return 1;
            });

            // When
            service.importExcel(file, List.of(vc), "myProject", "myPackage", "rules.xml");

            // Then
            ArgumentCaptor<BatchTestSessionEntity> captor = ArgumentCaptor.forClass(BatchTestSessionEntity.class);
            verify(sessionMapper).insert(captor.capture());
            BatchTestSessionEntity session = captor.getValue();
            assertThat(session.getProject()).isEqualTo("myProject");
            assertThat(session.getPackageId()).isEqualTo("myPackage");
            assertThat(session.getFiles()).isEqualTo("rules.xml");
            assertThat(session.getStatus()).isEqualTo(BatchTestSessionEntity.STATUS_UPLOADED);
            assertThat(session.getProgress()).isEqualTo(0.0);
        }
    }
}
