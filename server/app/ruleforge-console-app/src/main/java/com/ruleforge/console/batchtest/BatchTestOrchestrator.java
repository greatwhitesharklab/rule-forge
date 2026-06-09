package com.ruleforge.console.batchtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.entity.BatchTestRowEntity;
import com.ruleforge.console.app.entity.BatchTestSessionEntity;
import com.ruleforge.console.app.mapper.BatchTestRowMapper;
import com.ruleforge.console.app.mapper.BatchTestSessionMapper;
import com.ruleforge.console.batchtest.impl.DatasourceInputSource;
import com.ruleforge.console.batchtest.impl.ExcelRowParser;
import com.ruleforge.console.batchtest.impl.FileInputSource;
import com.ruleforge.console.batchtest.impl.FlowBatchTestSubject;
import com.ruleforge.console.model.ApplicationAllVariableCategoryMap;
import com.ruleforge.console.model.BatchTestFlowMap;
import com.ruleforge.console.model.TestDataImportResult;
import com.ruleforge.console.service.BatchTestService;
import com.ruleforge.console.service.TestDataService;
import com.ruleforge.runtime.KnowledgePackage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * BatchTest 多态路由器(V5.8.0)
 *
 * 持有所有 BatchTestSubject + InputSource 实现,按 type 路由。同时负责:
 *   1. startBatchTest — 创 session + 调 InputSource 拉 inputs + 异步调度 Subject
 *   2. getProgress / getResults / list / cancel — 给 Controller 用的状态查询
 *
 * 老的 BatchTestServiceImpl 里的 executeBatchAsync / getSessionProgress 还在
 * (兼容老调用方),但 controller V5.8.0 之后走这个 orchestrator。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchTestOrchestrator {

    private final List<BatchTestSubject> subjectBeans;
    private final List<InputSource> inputSourceBeans;
    private final BatchTestSessionMapper sessionMapper;
    private final BatchTestRowMapper rowMapper;
    private final FlowBatchTestSubject flowSubject;     // 直接注入便于拿 KnowledgePackage
    private final FileInputSource fileInputSource;
    private final DatasourceInputSource datasourceInputSource;
    private final BatchTestService batchTestService;   // 复用老异步执行器
    private final TestDataService testDataService;     // v5.8.4 FLOW+FILE Excel 透传
    private final Executor batchTestExecutor;          // @Qualifier("batchTestExecutor") 来自 BatchTestAsyncConfig
    private final ObjectMapper objectMapper;          // 给 DATASOURCE subject 反序列化 input_data 用

    /**
     * V5.8.2 现阶段支持的 (subject, inputSource) 组合:
     *   ✓ FLOW + FILE            — 复用 BatchTestServiceImpl.executeBatchAsync
     *   ✓ FLOW + DATASOURCE     — 走 executor HTTP /test/datasource/fetch
     *   ✓ DATASOURCE + FILE     — V5.8.2 新增,直接调数据源测 SLA
     *   ✗ DATASOURCE + DATASOURCE — 没有意义(自己拉自己),拒绝
     */
    private void validateModeSupported(String subjectType, String inputSourceType) {
        if (BatchTestSessionEntity.SUBJECT_FLOW.equals(subjectType)
                && (BatchTestSessionEntity.INPUT_FILE.equals(inputSourceType)
                    || BatchTestSessionEntity.INPUT_DATASOURCE.equals(inputSourceType))) {
            return;
        }
        if (BatchTestSessionEntity.SUBJECT_DATASOURCE.equals(subjectType)
                && BatchTestSessionEntity.INPUT_FILE.equals(inputSourceType)) {
            return;  // V5.8.2: 裸数据源批量测,Excel 给 entityId+fieldName
        }
        if (BatchTestSessionEntity.SUBJECT_DATASOURCE.equals(subjectType)
                && BatchTestSessionEntity.INPUT_DATASOURCE.equals(inputSourceType)) {
            throw new UnsupportedOperationException(
                    "DATASOURCE + DATASOURCE 无意义(自己拉自己测试自己)," +
                    " 改成 DATASOURCE+FILE 在 Excel 里给 entityId+fieldName");
        }
        throw new UnsupportedOperationException(
                "subjectType=" + subjectType + " + inputSourceType=" + inputSourceType
                + " 暂未实现");
    }

    private Map<String, BatchTestSubject> subjectMap;
    private Map<String, InputSource> inputSourceMap;

    @PostConstruct
    void init() {
        subjectMap = subjectBeans.stream()
                .collect(toMap(BatchTestSubject::getType, identity()));
        inputSourceMap = inputSourceBeans.stream()
                .collect(toMap(InputSource::getType, identity()));
        log.info("BatchTestOrchestrator ready: subjects={}, inputSources={}",
                subjectMap.keySet(), inputSourceMap.keySet());
    }

    public BatchTestSubject resolveSubject(String type) {
        BatchTestSubject s = subjectMap.get(type);
        if (s == null) {
            throw new IllegalArgumentException("Unknown subjectType: " + type
                    + " (known: " + subjectMap.keySet() + ")");
        }
        return s;
    }

    public InputSource resolveInputSource(String type) {
        InputSource s = inputSourceMap.get(type);
        if (s == null) {
            throw new IllegalArgumentException("Unknown inputSourceType: " + type
                    + " (known: " + inputSourceMap.keySet() + ")");
        }
        return s;
    }

    /**
     * 启动一次批量测试会话。
     *
     * 流程:
     *   1. resolve subject + input source(throw if unknown)
     *   2. 创 session row(subjectType, subjectId, inputSourceType, inputSourceId, totalRows=占位 0)
     *   3. 调 inputSource.fetchAndInsert(config, sessionId) 拉 inputs 写 nd_batch_test_row
     *   4. 更新 session.totalRows
     *   5. 异步(batchTestExecutor):遍历 nd_batch_test_row,对每行调 subject.execute(),更新行
     *
     * @return 新建的 sessionId
     */
    @Transactional
    public Long startBatchTest(StartBatchTestRequest req) {
        // V5.8.0 现阶段只支持 FLOW+FILE,其他组合先 validate 一下
        validateModeSupported(req.subjectType(), req.inputSourceType());

        BatchTestSubject subject = resolveSubject(req.subjectType());
        InputSource inputSource = resolveInputSource(req.inputSourceType());

        // 1. 创 session(共享 createSession helper,Excel 路径也调)
        Long sessionId = createSession(req);
        log.info("BatchTest start: session={} subject={} inputSource={}",
                sessionId, req.subjectType(), req.inputSourceType());

        // 2. 拉 inputs 落库
        int rowCount = inputSource.fetchAndInsert(
                new InputSourceConfig(req.inputSourceType(), req.inputConfig()),
                sessionId);

        // 3. 更新 session.totalRows + status=RUNNING
        BatchTestSessionEntity session = sessionMapper.selectById(sessionId);
        session.setTotalRows(rowCount);
        session.setStatus(BatchTestSessionEntity.STATUS_RUNNING);
        sessionMapper.updateById(session);

        // 4. 异步执行每行 — 按 subject 分发(FLOW 走老路径,DATASOURCE 走 subject.execute)
        schedulePerRowExecution(sessionId, subject, req);

        return sessionId;
    }

    /**
     * v5.8.4 新:把 Excel + StartBatchTestRequest 一起塞进来,自动按 (subject, inputSource) 解析 + 调度。
     *
     * 路径分派:
     *   - DATASOURCE+FILE:Excel 三列 entityId,fieldName,clazz → 直接存 row.input_data
     *   - FLOW+DATASOURCE:Excel 第一列 = inputField(默认 entityId),调 ExecutorDatasourceClient 拉数据
     *   - FLOW+FILE:透传 TestDataServiceImpl.importExcel(走老路径,session 由它建)
     *
     * 失败错误码:IllegalArgumentException → 400(前端提示),UnsupportedOperationException → 501。
     */
    public Long startBatchTestFromExcel(MultipartFile file, StartBatchTestRequest req) throws Exception {
        validateModeSupported(req.subjectType(), req.inputSourceType());
        BatchTestSubject subject = resolveSubject(req.subjectType());

        // FLOW+FILE 走老路径:TestDataServiceImpl.importExcel 自己建 session + 写 rows,
        // 我们从返回的 sessionId 接着调度
        if (BatchTestSessionEntity.SUBJECT_FLOW.equals(req.subjectType())
                && BatchTestSessionEntity.INPUT_FILE.equals(req.inputSourceType())) {
            return startFlowFileViaExcel(file, req, subject);
        }

        // 其他两个模式:我们自己建 session,自己解析 + 写 rows
        Long sessionId = createSession(req);
        log.info("BatchTest Excel start: session={} subject={} inputSource={}",
                sessionId, req.subjectType(), req.inputSourceType());

        int rowCount = insertRowsFromExcel(file, req, sessionId);

        // 更新 totalRows + RUNNING
        BatchTestSessionEntity session = sessionMapper.selectById(sessionId);
        session.setTotalRows(rowCount);
        session.setStatus(BatchTestSessionEntity.STATUS_RUNNING);
        sessionMapper.updateById(session);

        schedulePerRowExecution(sessionId, subject, req);
        return sessionId;
    }

    /**
     * 共享:创 session row,返回 sessionId。Excel + JSON 路径都调这个,避免重复。
     */
    private Long createSession(StartBatchTestRequest req) {
        BatchTestSessionEntity session = new BatchTestSessionEntity();
        session.setSubjectType(req.subjectType());
        session.setSubjectId(req.subjectId());
        session.setInputSourceType(req.inputSourceType());
        session.setInputSourceId(req.inputSourceId());
        session.setProject(req.project());
        session.setPackageId(req.packageId());
        session.setFlowId(req.flowId());
        session.setStatus(BatchTestSessionEntity.STATUS_UPLOADED);
        session.setTotalRows(0);
        session.setErrorCount(0);
        session.setProgress(0.0);
        sessionMapper.insert(session);
        return session.getId();
    }

    /**
     * 共享:按 (subject, inputSource) 解析 Excel + 把 row 写到 nd_batch_test_row。
     * 返 row 数。
     */
    private int insertRowsFromExcel(MultipartFile file, StartBatchTestRequest req, Long sessionId) throws Exception {
        ExcelRowParser parser = new ExcelRowParser();
        boolean isDatasourceSubject = BatchTestSessionEntity.SUBJECT_DATASOURCE.equals(req.subjectType());
        boolean isDatasourceInput = BatchTestSessionEntity.INPUT_DATASOURCE.equals(req.inputSourceType());

        if (isDatasourceSubject && !isDatasourceInput) {
            // DATASOURCE+FILE:Excel 三列直接入表
            List<Map<String, Object>> rows = parser.forDatasourceOnly(file);
            for (int i = 0; i < rows.size(); i++) {
                insertRow(sessionId, i, objectMapper.writeValueAsString(rows.get(i)));
            }
            return rows.size();
        }

        if (!isDatasourceSubject && isDatasourceInput) {
            // FLOW+DATASOURCE:Excel 解析成 batchInputs,调 executor fetchFields 入表
            String inputField = (String) req.inputConfig().getOrDefault("inputField", "entityId");
            List<Map<String, Object>> rows = parser.forFlowWithDatasource(file, inputField);

            Long datasourceId = req.subjectId();  // FLOW+DATASOURCE 模式下 subjectId 留 null,数据源 id 在 inputConfig
            Object dsIdRaw = req.inputConfig().get("datasourceId");
            if (dsIdRaw != null) {
                datasourceId = ((Number) dsIdRaw).longValue();
            }
            String clazz = (String) req.inputConfig().getOrDefault("clazz", "");

            // 复用 DatasourceInputSource 的 executor fetch + 入表逻辑(inputConfig 里 inputField 由调用方决定)
            Map<String, Object> dsCfg = new HashMap<>();
            dsCfg.put("inputField", inputField);
            return datasourceInputSource.fetchAndInsertRows(datasourceId, clazz, rows, dsCfg, sessionId);
        }

        throw new IllegalStateException(
                "startBatchTestFromExcel 未匹配的 mode: subject=" + req.subjectType()
                + " inputSource=" + req.inputSourceType());
    }

    /**
     * FLOW+FILE 走老 TestDataServiceImpl.importExcel(它自己建 session,我们要复用它的
     * sessionId 再调度 FLOW subject)。variableCategories 必须在 inputConfig 里。
     */
    private Long startFlowFileViaExcel(MultipartFile file, StartBatchTestRequest req, BatchTestSubject subject) throws Exception {
        @SuppressWarnings("unchecked")
        List<com.ruleforge.model.library.variable.VariableCategory> vcs =
                (List<com.ruleforge.model.library.variable.VariableCategory>) req.inputConfig().get("variableCategories");
        if (vcs == null || vcs.isEmpty()) {
            throw new IllegalArgumentException(
                    "FLOW+FILE Excel 需要 inputConfig.variableCategories 描述变量库");
        }
        // 用 TargetFiles 占位(老 service 需要这个非空字符串)
        String files = req.inputConfig().get("files") instanceof String
                ? (String) req.inputConfig().get("files")
                : "excel-upload";
        TestDataImportResult importResult = testDataService.importExcel(
                file, vcs, req.project(), req.packageId(), files);
        Long sessionId = importResult.getSessionId();
        log.info("BatchTest FLOW+FILE Excel: 委托 importExcel session={} rows={}",
                sessionId, importResult.getTotalRows());

        // 调度 FLOW 主体(走老 BatchTestServiceImpl.executeBatchAsync)
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) req.inputConfig().getOrDefault("flowParams", Map.of());
        KnowledgePackage knowledgePackage = (KnowledgePackage) params.get("knowledgePackage");
        String flowId = (String) params.get("flowId");
        batchTestService.executeBatchAsync(sessionId, knowledgePackage, flowId, vcs);
        return sessionId;
    }

    private void insertRow(Long sessionId, int rowIndex, String inputDataJson) {
        BatchTestRowEntity row = new BatchTestRowEntity();
        row.setSessionId(sessionId);
        row.setRowIndex(rowIndex);
        row.setStatus(BatchTestRowEntity.STATUS_PENDING);
        row.setInputData(inputDataJson);
        rowMapper.insert(row);
    }

    /**
     * 异步执行每行 — V5.8.2 按 subject 分发:
     *   - FLOW(无论 FILE/DATASOURCE input):复用 BatchTestServiceImpl.executeBatchAsync
     *     老路径,内部 rowMapper.updateResult 已带 latencyMs / errorCode
     *   - DATASOURCE(V5.8.2 新):遍历行调 subject.execute(),结果直接写 DB
     */
    private void schedulePerRowExecution(Long sessionId, BatchTestSubject subject, StartBatchTestRequest req) {
        if (BatchTestSessionEntity.SUBJECT_FLOW.equals(req.subjectType())) {
            // FLOW 走老路径
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) req.inputConfig().getOrDefault("flowParams", Map.of());
            KnowledgePackage knowledgePackage = (KnowledgePackage) params.get("knowledgePackage");
            String flowId = (String) params.get("flowId");
            BatchTestFlowMap flowMap = (BatchTestFlowMap) params.getOrDefault("flowMap", new BatchTestFlowMap());
            @SuppressWarnings("unchecked")
            List<com.ruleforge.model.library.variable.VariableCategory> variableCategories =
                    (List<com.ruleforge.model.library.variable.VariableCategory>) params.getOrDefault("variableCategories", List.of());
            batchTestService.executeBatchAsync(sessionId, knowledgePackage, flowId, variableCategories);
        } else {
            // DATASOURCE(以及未来其他 subject)走新路径:遍历行调 subject.execute()
            executeWithSubject(sessionId, subject, req);
        }
    }

    /**
     * V5.8.2 新路径:DATASOURCE subject(以及未来扩展)按行调 subject.execute()
     * 每行:反序列化 input → 调 subject.execute(ctx) → 写 nd_batch_test_row
     */
    private void executeWithSubject(Long sessionId, BatchTestSubject subject, StartBatchTestRequest req) {
        // 拉所有行(简单实现,以后大表改 keyset pagination)
        List<BatchTestRowEntity> rows = rowMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BatchTestRowEntity>()
                        .eq("session_id", sessionId)
                        .orderByAsc("row_index"));

        // 组装 subject params(从 req.inputConfig 拿 + session 字段)
        Map<String, Object> subjectParams = new HashMap<>();
        subjectParams.put("datasourceId", req.subjectId());  // subjectId 在 DATASOURCE 模式下是 datasourceId
        subjectParams.put("inputSourceId", req.inputSourceId());

        // 异步遍历(后续加 fork-join 并行)
        for (BatchTestRowEntity row : rows) {
            // 逐行同步跑(够简单)。后续优化:fork-join pool 并行,失败隔离
            try {
                Object input = objectMapper.readValue(row.getInputData(), Object.class);
                SubjectExecutionContext ctx = new SubjectExecutionContext(
                        sessionId, row.getId(), input, subjectParams);
                SubjectResult result = subject.execute(ctx);
                writeResult(row, result);
            } catch (Exception e) {
                log.error("executeWithSubject row {} 异常", row.getId(), e);
                writeFailure(row, "EXECUTE_ERROR", e.getMessage());
            }
            updateSessionProgress(sessionId);
        }
        markSessionCompleted(sessionId);
    }

    private void writeResult(BatchTestRowEntity row, SubjectResult result) {
        try {
            String outputJson = objectMapper.writeValueAsString(result.output());
            rowMapper.updateResult(
                    row.getId(),
                    result.isSuccess() ? BatchTestRowEntity.STATUS_SUCCESS : BatchTestRowEntity.STATUS_ERROR,
                    outputJson,
                    result.errorMessage(),
                    result.latencyMs(),
                    result.httpStatus(),
                    result.errorCode());
        } catch (Exception e) {
            log.error("writeResult 失败 rowId={}", row.getId(), e);
        }
    }

    private void writeFailure(BatchTestRowEntity row, String code, String msg) {
        rowMapper.updateResult(
                row.getId(),
                BatchTestRowEntity.STATUS_ERROR,
                null,
                msg,
                0L,
                null,
                code);
    }

    private void updateSessionProgress(Long sessionId) {
        BatchTestSessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null) return;
        long total = session.getTotalRows() == null ? 0 : session.getTotalRows();
        long done = rowMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BatchTestRowEntity>()
                        .eq("session_id", sessionId)
                        .in("status", BatchTestRowEntity.STATUS_SUCCESS, BatchTestRowEntity.STATUS_ERROR)).intValue();
        long errors = rowMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BatchTestRowEntity>()
                        .eq("session_id", sessionId)
                        .eq("status", BatchTestRowEntity.STATUS_ERROR)).intValue();
        session.setErrorCount((int) errors);
        session.setProgress(total > 0 ? (double) done / total : 0.0);
        sessionMapper.updateById(session);
    }

    private void markSessionCompleted(Long sessionId) {
        BatchTestSessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null) return;
        long errors = rowMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BatchTestRowEntity>()
                        .eq("session_id", sessionId)
                        .eq("status", BatchTestRowEntity.STATUS_ERROR)).intValue();
        session.setStatus(errors > 0 ? BatchTestSessionEntity.STATUS_FAILED : BatchTestSessionEntity.STATUS_COMPLETED);
        session.setErrorCount((int) errors);
        session.setProgress(1.0);
        sessionMapper.updateById(session);
        log.info("BatchTest session {} completed: errors={}", sessionId, errors);
    }

    public Map<String, Object> getProgress(Long sessionId) {
        // 复用老的进度查询
        BatchTestSessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null) return Map.of("status", "NOT_FOUND");

        Map<String, Object> progress = new HashMap<>();
        progress.put("sessionId", sessionId);
        progress.put("status", session.getStatus());
        progress.put("totalRows", session.getTotalRows());
        progress.put("progress", session.getProgress());
        progress.put("errorCount", session.getErrorCount());
        progress.put("subjectType", session.getSubjectType());
        progress.put("inputSourceType", session.getInputSourceType());
        return progress;
    }

    public List<com.ruleforge.console.app.entity.BatchTestRowEntity> getResults(Long sessionId, int offset, int limit) {
        // 简单分页,以后改成 keyset pagination 优化大列表
        return rowMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.ruleforge.console.app.entity.BatchTestRowEntity>()
                        .eq("session_id", sessionId)
                        .orderByAsc("row_index")
                        .last("LIMIT " + limit + " OFFSET " + offset));
    }
}
