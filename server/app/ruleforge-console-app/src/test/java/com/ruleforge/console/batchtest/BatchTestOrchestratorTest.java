package com.ruleforge.console.batchtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.entity.BatchTestRowEntity;
import com.ruleforge.console.app.entity.BatchTestSessionEntity;
import com.ruleforge.console.app.mapper.BatchTestRowMapper;
import com.ruleforge.console.app.mapper.BatchTestSessionMapper;
import com.ruleforge.console.batchtest.impl.DatasourceInputSource;
import com.ruleforge.console.batchtest.impl.DatasourceOnlyBatchTestSubject;
import com.ruleforge.console.batchtest.impl.ExecutorDatasourceClient;
import com.ruleforge.console.batchtest.impl.FileInputSource;
import com.ruleforge.console.batchtest.impl.FlowBatchTestSubject;
import com.ruleforge.console.service.BatchTestService;
import com.ruleforge.console.service.TestDataService;
import com.ruleforge.console.model.TestDataImportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Feature: BatchTestOrchestrator.startBatchTestFromExcel — v5.8.4 BatchTest Excel upload
 *
 * 把 Excel + StartBatchTestRequest 一起塞进去,期望 orchestrator:
 *   1. 创 session
 *   2. 调 ExcelRowParser 解析 Excel
 *   3. 按 (subjectType, inputSourceType) 决定每行的 input_data 形状
 *   4. 插 row 到 nd_batch_test_row
 *   5. 调度 subject 执行
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BatchTestOrchestrator.startBatchTestFromExcel - v5.8.4 Excel upload")
class BatchTestOrchestratorTest {

    @Mock private List<com.ruleforge.console.batchtest.BatchTestSubject> subjectBeans;
    @Mock private List<InputSource> inputSourceBeans;
    @Mock private BatchTestSessionMapper sessionMapper;
    @Mock private BatchTestRowMapper rowMapper;
    @Mock private FlowBatchTestSubject flowSubject;
    @Mock private DatasourceOnlyBatchTestSubject datasourceOnlySubject;
    @Mock private FileInputSource fileInputSource;
    @Mock private DatasourceInputSource datasourceInputSource;
    @Mock private BatchTestService batchTestService;
    @Mock private TestDataService testDataService;
    @Mock private java.util.concurrent.Executor batchTestExecutor;
    @Mock private ExecutorDatasourceClient executorDatasourceClient;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    private BatchTestOrchestrator orchestrator;

    @BeforeEach
    void wireOrchestratorMaps() {
        // Mockito @Mock List<...>.stream() 在 type erasure 下会被混淆,
        // 不用 @InjectMocks 注入 subjectBeans/inputSourceBeans — 改成手工构建 orchestrator,
        // 传真实的 List 进去,init() 时 toMap 不会再抛 ClassCast。
        java.util.List<com.ruleforge.console.batchtest.BatchTestSubject> subjects =
                java.util.List.of(flowSubject, datasourceOnlySubject);
        java.util.List<InputSource> sources =
                java.util.List.of(fileInputSource, datasourceInputSource);
        when(flowSubject.getType()).thenReturn(BatchTestSessionEntity.SUBJECT_FLOW);
        when(datasourceOnlySubject.getType()).thenReturn(BatchTestSessionEntity.SUBJECT_DATASOURCE);
        when(fileInputSource.getType()).thenReturn(BatchTestSessionEntity.INPUT_FILE);
        when(datasourceInputSource.getType()).thenReturn(BatchTestSessionEntity.INPUT_DATASOURCE);

        orchestrator = new BatchTestOrchestrator(
                subjects, sources,
                sessionMapper, rowMapper,
                flowSubject, fileInputSource, datasourceInputSource,
                batchTestService, testDataService, batchTestExecutor,
                objectMapper);
        orchestrator.init();
    }

    private MultipartFile fakeExcel() {
        return new MockMultipartFile(
                "file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "x".getBytes());
    }

    /**
     * mock sessionMapper.insert 让它回填 id=42,这样 orchestrator 拿到的 sessionId 不会是 null
     */
    private void stubSessionInsert() {
        when(sessionMapper.insert(any(BatchTestSessionEntity.class)))
                .thenAnswer(inv -> {
                    BatchTestSessionEntity s = inv.getArgument(0);
                    var f = BatchTestSessionEntity.class.getDeclaredField("id");
                    f.setAccessible(true);
                    f.set(s, 42L);
                    return 1;
                });
        // 还要 mock selectById,orchestrator 创 session 后会 selectById 再 updateById
        when(sessionMapper.selectById(42L)).thenAnswer(inv -> {
            BatchTestSessionEntity s = new BatchTestSessionEntity();
            s.setId(42L);
            s.setSubjectType("UNKNOWN");  // orchestrator 会 set 真实值
            return s;
        });
    }

    @Nested
    @DisplayName("Scenario: DATASOURCE+FILE Excel 上传")
    class DatasourceFileExcel {

        // Given Excel 含 entityId,fieldName,clazz 三列 5 行
        // And StartBatchTestRequest(subject=DATASOURCE, inputSource=FILE, datasourceId=42)
        // When startBatchTestFromExcel
        // Then session 创建,totalRows=5,5 行 row 插入,每行 input_data = {entityId, fieldName, clazz}
        @Test
        @DisplayName("DATASOURCE+FILE:5 行入表,input_data 形如 {entityId, fieldName, clazz}")
        void shouldInsertRowsForDatasourceFile() throws Exception {
            stubSessionInsert();
            when(rowMapper.insert(any(BatchTestRowEntity.class))).thenReturn(1);

            // 用真实 Excel 路径,build 一个 in-memory xlsx
            MultipartFile excel = buildDsOnlyExcel();
            StartBatchTestRequest req = new StartBatchTestRequest(
                    BatchTestSessionEntity.SUBJECT_DATASOURCE, 42L,
                    BatchTestSessionEntity.INPUT_FILE, null,
                    Map.of("datasourceId", 42),
                    "proj", "pkg", "");

            // orchestrator 内部用真实 ExcelRowParser 解析 → 5 行
            Long sessionId = orchestrator.startBatchTestFromExcel(excel, req);

            assertThat(sessionId).isEqualTo(42L);
            // session 创了一次,updateById 至少 1 次(改 status=RUNNING + totalRows=5)
            verify(sessionMapper, times(1)).insert(any(BatchTestSessionEntity.class));
            // 5 行 insert
            ArgumentCaptor<BatchTestRowEntity> rowCap = ArgumentCaptor.forClass(BatchTestRowEntity.class);
            verify(rowMapper, times(5)).insert(rowCap.capture());
            // 第一个 row.input_data 解析后含 entityId
            String inputData = rowCap.getAllValues().get(0).getInputData();
            assertThat(inputData).contains("entityId").contains("fieldName");
            // session 改 RUNNING + totalRows=5(后续 subject.execute 走完后会再改 COMPLETED,
            // 所以检查"至少一次 update 把 status 设成 RUNNING 且 totalRows=5")
            ArgumentCaptor<BatchTestSessionEntity> sessCap = ArgumentCaptor.forClass(BatchTestSessionEntity.class);
            verify(sessionMapper, atLeastOnce()).updateById(sessCap.capture());
            boolean hasRunningWith5 = sessCap.getAllValues().stream().anyMatch(s ->
                    BatchTestSessionEntity.STATUS_RUNNING.equals(s.getStatus())
                            && Integer.valueOf(5).equals(s.getTotalRows()));
            assertThat(hasRunningWith5)
                    .as("至少一次 updateById 把 status 设为 RUNNING 且 totalRows=5")
                    .isTrue();
        }

        private MultipartFile buildDsOnlyExcel() throws Exception {
            java.io.File tmp = java.io.File.createTempFile("ds-only-", ".xlsx");
            tmp.deleteOnExit();
            com.alibaba.excel.EasyExcel.write(tmp)
                    .head(java.util.List.of(
                            java.util.List.of("entityId"),
                            java.util.List.of("fieldName"),
                            java.util.List.of("clazz")))
                    .sheet("Sheet1")
                    .doWrite(java.util.List.of(
                            java.util.List.of("1", "name", "User"),
                            java.util.List.of("2", "age", "User"),
                            java.util.List.of("3", "email", "User"),
                            java.util.List.of("4", "phone", "User"),
                            java.util.List.of("5", "score", "User")));
            byte[] bytes = java.nio.file.Files.readAllBytes(tmp.toPath());
            tmp.delete();
            return new MockMultipartFile("file", "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    bytes);
        }
    }

    @Nested
    @DisplayName("Scenario: FLOW+DATASOURCE Excel 上传")
    class FlowDatasourceExcel {

        // Given Excel 含 entityId,name 2 列 3 行
        // And ExecutorDatasourceClient.fetchFields 返回 {entityId → {name → "..."}}
        // When startBatchTestFromExcel
        // Then 3 行入表,每行 input_data = {request: {entityId, name}, response: {...}}
        @Test
        @DisplayName("FLOW+DATASOURCE:Excel 解析成 batchInputs,调 executor,3 行入表")
        void shouldFetchAndInsertForFlowDatasource() throws Exception {
            stubSessionInsert();
            // fetchAndInsertRows 内部会调 executor,这里 mock 让它返 3
            when(datasourceInputSource.fetchAndInsertRows(
                    anyLong(), anyString(), anyList(), any(Map.class), eq(42L)))
                    .thenReturn(3);

            MultipartFile excel = buildFlowDsExcel();
            StartBatchTestRequest req = new StartBatchTestRequest(
                    BatchTestSessionEntity.SUBJECT_FLOW, 1L,
                    BatchTestSessionEntity.INPUT_DATASOURCE, 99L,
                    Map.of("datasourceId", 99, "clazz", "T", "inputField", "entityId"),
                    "proj", "pkg", "flow-1");

            Long sessionId = orchestrator.startBatchTestFromExcel(excel, req);

            assertThat(sessionId).isEqualTo(42L);
            // datasourceInputSource.fetchAndInsertRows 应该被调一次
            verify(datasourceInputSource, times(1)).fetchAndInsertRows(
                    eq(99L), eq("T"), anyList(), any(Map.class), eq(42L));
            // 调 BatchTestService.executeBatchAsync 走老路径
            verify(batchTestService, never()).executeBatchAsync(anyLong(), any(), anyString(), anyList());
        }

        private MultipartFile buildFlowDsExcel() throws Exception {
            java.io.File tmp = java.io.File.createTempFile("flow-ds-", ".xlsx");
            tmp.deleteOnExit();
            com.alibaba.excel.EasyExcel.write(tmp)
                    .head(java.util.List.of(
                            java.util.List.of("entityId"),
                            java.util.List.of("name")))
                    .sheet("Sheet1")
                    .doWrite(java.util.List.of(
                            java.util.List.of("1", "Alice"),
                            java.util.List.of("2", "Bob"),
                            java.util.List.of("3", "Carol")));
            byte[] bytes = java.nio.file.Files.readAllBytes(tmp.toPath());
            tmp.delete();
            return new MockMultipartFile("file", "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    bytes);
        }
    }

    @Nested
    @DisplayName("Scenario: FLOW+FILE Excel 上传")
    class FlowFileExcel {

        // Given Excel 多 sheet + variableCategories
        // When startBatchTestFromExcel with subject=FLOW, inputSource=FILE
        // Then 走 ExcelUtils.readTestDataExcel,行入表,totalRows = 解析结果数
        @Test
        @DisplayName("FLOW+FILE:透传 TestDataServiceImpl.importExcel,行入表")
        void shouldDelegateToTestDataImportForFlowFile() throws Exception {
            // importExcel 自己建 session,返 sessionId=100 + totalRows=10
            when(testDataService.importExcel(
                    any(MultipartFile.class), anyList(), anyString(), anyString(), anyString()))
                    .thenReturn(new TestDataImportResult(100L, 10, new ArrayList<>(), false));

            MultipartFile excel = fakeExcel();
            // 传一个非空 variableCategories(orchestrator 校验:empty → IllegalArgumentException)
            com.ruleforge.model.library.variable.VariableCategory vc =
                    new com.ruleforge.model.library.variable.VariableCategory();
            vc.setName("input");
            List<com.ruleforge.model.library.variable.VariableCategory> vcs =
                    new ArrayList<>(List.of(vc));
            StartBatchTestRequest req = new StartBatchTestRequest(
                    BatchTestSessionEntity.SUBJECT_FLOW, 1L,
                    BatchTestSessionEntity.INPUT_FILE, null,
                    Map.of("variableCategories", vcs, "files", "test"),
                    "proj", "pkg", "flow-1");

            Long sessionId = orchestrator.startBatchTestFromExcel(excel, req);

            assertThat(sessionId).isEqualTo(100L);
            // importExcel 调一次
            verify(testDataService, times(1)).importExcel(
                    any(MultipartFile.class), anyList(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Scenario: 模式校验")
    class ModeValidation {

        // Given subjectType=DATASOURCE, inputSourceType=DATASOURCE(无意义)
        // When startBatchTestFromExcel
        // Then 抛 UnsupportedOperationException
        @Test
        @DisplayName("DATASOURCE+DATASOURCE → UnsupportedOperationException")
        void shouldRejectDatasourceDatasource() {
            StartBatchTestRequest req = new StartBatchTestRequest(
                    BatchTestSessionEntity.SUBJECT_DATASOURCE, 42L,
                    BatchTestSessionEntity.INPUT_DATASOURCE, 42L,
                    Map.of(), "proj", "pkg", "");

            assertThatThrownBy(() -> orchestrator.startBatchTestFromExcel(fakeExcel(), req))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("DATASOURCE + DATASOURCE");
        }

        // Given subjectType=BOGUS, inputSourceType=FILE
        // When startBatchTestFromExcel
        // Then 抛 UnsupportedOperationException(validateModeSupported 先 throw)
        @Test
        @DisplayName("未知 subjectType → UnsupportedOperationException(由 validateModeSupported)")
        void shouldRejectUnknownSubjectType() {
            StartBatchTestRequest req = new StartBatchTestRequest(
                    "BOGUS", 1L,
                    BatchTestSessionEntity.INPUT_FILE, null,
                    Map.of(), "proj", "pkg", "");

            assertThatThrownBy(() -> orchestrator.startBatchTestFromExcel(fakeExcel(), req))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("暂未实现");
        }
    }
}
