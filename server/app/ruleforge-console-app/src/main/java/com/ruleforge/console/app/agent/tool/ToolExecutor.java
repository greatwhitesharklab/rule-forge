package com.ruleforge.console.app.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruleforge.console.app.draft.DraftApplyService;
import com.ruleforge.console.app.draft.DraftEntity;
import com.ruleforge.console.app.draft.DraftHistoryEntity;
import com.ruleforge.console.app.draft.DraftHistoryService;
import com.ruleforge.console.app.draft.DraftService;
import com.ruleforge.console.app.draft.TestCaseEntity;
import com.ruleforge.console.app.draft.TestCaseService;
import com.ruleforge.console.app.agent.audit.AgentAuditEntity;
import com.ruleforge.console.app.agent.audit.AgentAuditService;
import com.ruleforge.console.app.service.IAnalysisService;
import com.ruleforge.console.repository.model.ResourceItem;
import com.ruleforge.console.repository.model.ResourcePackage;
import com.ruleforge.console.service.impl.RuleForgeRepositoryServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工具执行器 — 将 LLM 的工具调用映射到现有内部服务。
 *
 * <p>每个工具名对应一个处理方法，接收 JSON 参数字符串，返回 JSON 字符串结果。
 * 直接调用 Service 层（不经过 HTTP），零网络开销。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutor {

    private final IAnalysisService analysisService;
    private final RuleForgeRepositoryServiceImpl repoService;
    private final ObjectMapper objectMapper;
    private final DraftService draftService;
    private final DraftApplyService draftApplyService;
    private final TestCaseService testCaseService;
    private final DraftHistoryService draftHistoryService;       // V5.22.3
    private final AgentAuditService agentAuditService;            // V5.22.3

    /**
     * 执行工具调用
     *
     * @param toolName 工具名称
     * @param argsJson JSON 参数字符串
     * @return 工具执行结果（JSON 字符串）
     */
    public String execute(String toolName, String argsJson) {
        try {
            JsonNode args = argsJson != null && !argsJson.isEmpty()
                    ? objectMapper.readTree(argsJson)
                    : objectMapper.createObjectNode();

            return switch (toolName) {
                case ToolRegistry.LIST_PROJECTS -> executeListProjects();
                case ToolRegistry.LIST_PACKAGES -> executeListPackages(args);
                case ToolRegistry.EXPORT_PACKAGE -> executeExportPackage(args);
                case ToolRegistry.QUERY_FLOW_TIMESERIES -> executeQueryFlowTimeseries(args);
                case ToolRegistry.QUERY_REJECT_DISTRIBUTION -> executeQueryRejectDistribution(args);
                case ToolRegistry.QUERY_RULE_COVERAGE -> executeQueryRuleCoverage(args);
                case ToolRegistry.QUERY_RULE_FIRE_FREQUENCY -> executeQueryRuleFireFrequency(args);
                case ToolRegistry.DETECT_ANOMALIES -> executeDetectAnomalies(args);
                case ToolRegistry.QUERY_METRICS -> executeQueryMetrics(args);
                case ToolRegistry.LIST_ALERTS -> executeListAlerts();
                case ToolRegistry.QUERY_SIMULATION_STATS -> executeQuerySimulationStats();

                // V5.22 — AI Rule Authoring
                case ToolRegistry.DRAFT_RULE -> executeDraftRule(args);
                case ToolRegistry.LIST_DRAFTS -> executeListDrafts(args);
                case ToolRegistry.GET_DRAFT -> executeGetDraft(args);
                case ToolRegistry.SUBMIT_DRAFT -> executeSubmitDraft(args);
                case ToolRegistry.REJECT_DRAFT -> executeRejectDraft(args);
                case ToolRegistry.APPROVE_DRAFT -> executeApproveDraft(args);
                case ToolRegistry.APPLY_DRAFT -> executeApplyDraft(args);
                case ToolRegistry.GENERATE_TEST_CASES -> executeGenerateTestCases(args);
                case ToolRegistry.RUN_TEST -> executeRunTest(args);

                // V5.22.1 — 草稿测试用例持久化
                case ToolRegistry.LIST_TEST_CASES -> executeListTestCases(args);
                case ToolRegistry.ADD_TEST_CASE -> executeAddTestCase(args);
                case ToolRegistry.DELETE_TEST_CASE -> executeDeleteTestCase(args);
                case ToolRegistry.RUN_SAVED_TESTS -> executeRunSavedTests(args);

                // V5.22.2 — 规则健康仪表盘
                case ToolRegistry.GET_RULE_HEALTH -> executeGetRuleHealth(args);

                // V5.22.3 — 草稿状态历史 + 工具调用审计
                case ToolRegistry.GET_DRAFT_HISTORY -> executeGetDraftHistory(args);
                case ToolRegistry.LIST_AGENT_AUDIT -> executeListAgentAudit(args);

                default -> "{\"error\": \"Unknown tool: " + toolName + "\"}";
            };
        } catch (Exception e) {
            log.error("Tool execution failed: {} args: {}", toolName, argsJson, e);
            return "{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    // ===== 工具实现 =====

    private String executeListProjects() throws Exception {
        List<String> projects = repoService.loadProjectNames();
        return objectMapper.writeValueAsString(Map.of("projects", projects));
    }

    private String executeListPackages(JsonNode args) throws Exception {
        String project = args.path("project").asText("");
        if (project.isEmpty()) {
            return "{\"error\": \"project 参数必填\"}";
        }
        List<ResourcePackage> packages = repoService.loadProjectResourcePackages(project);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ResourcePackage pkg : packages) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", pkg.getId());
            entry.put("name", pkg.getName());
            entry.put("version", pkg.getVersion());
            int itemCount = pkg.getResourceItems() != null ? pkg.getResourceItems().size() : 0;
            entry.put("resourceItemCount", itemCount);
            result.add(entry);
        }
        return objectMapper.writeValueAsString(Map.of("project", project, "packages", result));
    }

    private String executeExportPackage(JsonNode args) throws Exception {
        String project = args.path("project").asText("");
        String packageId = args.path("packageId").asText("");
        if (project.isEmpty() || packageId.isEmpty()) {
            return "{\"error\": \"project 和 packageId 参数必填\"}";
        }
        List<ResourcePackage> packages = repoService.loadProjectResourcePackages(project);
        ResourcePackage targetPkg = packages.stream()
                .filter(p -> packageId.equals(p.getId()) || packageId.equals(p.getName()))
                .findFirst().orElse(null);
        if (targetPkg == null) {
            return "{\"error\": \"Package not found: " + packageId + "\"}";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("packageName", targetPkg.getName());
        result.put("packageId", targetPkg.getId());
        result.put("version", targetPkg.getVersion());

        List<Map<String, Object>> files = new ArrayList<>();
        if (targetPkg.getResourceItems() != null) {
            for (ResourceItem item : targetPkg.getResourceItems()) {
                Map<String, Object> fileEntry = new LinkedHashMap<>();
                fileEntry.put("name", item.getName());
                fileEntry.put("path", item.getPath());
                try (InputStream is = repoService.readFile(item.getPath(), item.getVersion())) {
                    if (is != null) {
                        String content = new BufferedReader(
                                new InputStreamReader(is, StandardCharsets.UTF_8))
                                .lines().collect(Collectors.joining("\n"));
                        // 截断超大文件
                        fileEntry.put("content", content.length() > 4000
                                ? content.substring(0, 4000) + "...(truncated)" : content);
                    }
                } catch (Exception e) {
                    fileEntry.put("content", null);
                    fileEntry.put("error", e.getMessage());
                }
                files.add(fileEntry);
            }
        }
        result.put("files", files);
        return objectMapper.writeValueAsString(result);
    }

    private String executeQueryFlowTimeseries(JsonNode args) throws Exception {
        Date startTime = parseDate(args, "startTime");
        Date endTime = parseDate(args, "endTime");
        if (startTime == null || endTime == null) {
            return "{\"error\": \"startTime 和 endTime 参数必填，格式: yyyy-MM-ddTHH:mm:ss\"}";
        }
        String granularity = args.path("granularity").asText("hourly");
        Map<String, Object> result = analysisService.getFlowLogTimeSeries(
                startTime, endTime, null, null, null, granularity);
        return objectMapper.writeValueAsString(result);
    }

    private String executeQueryRejectDistribution(JsonNode args) throws Exception {
        Date startTime = parseDate(args, "startTime");
        Date endTime = parseDate(args, "endTime");
        if (startTime == null || endTime == null) {
            return "{\"error\": \"startTime 和 endTime 参数必填\"}";
        }
        List<Map<String, Object>> result = analysisService.getRejectDistribution(
                startTime, endTime, null, 20);
        return objectMapper.writeValueAsString(Map.of("rejectDistribution", result));
    }

    private String executeQueryRuleCoverage(JsonNode args) throws Exception {
        String project = args.path("project").asText("");
        Date startTime = parseDate(args, "startTime");
        Date endTime = parseDate(args, "endTime");
        if (project.isEmpty() || startTime == null || endTime == null) {
            return "{\"error\": \"project, startTime 和 endTime 参数必填\"}";
        }
        Map<String, Object> result = analysisService.getRuleCoverageAnalysis(project, startTime, endTime);
        return objectMapper.writeValueAsString(result);
    }

    private String executeQueryRuleFireFrequency(JsonNode args) throws Exception {
        String project = args.path("project").asText("");
        Date startTime = parseDate(args, "startTime");
        Date endTime = parseDate(args, "endTime");
        if (project.isEmpty() || startTime == null || endTime == null) {
            return "{\"error\": \"project, startTime 和 endTime 参数必填\"}";
        }
        List<Map<String, Object>> result = analysisService.getRuleFireFrequency(
                startTime, endTime, project);
        return objectMapper.writeValueAsString(Map.of("ruleFireFrequency", result));
    }

    private String executeDetectAnomalies(JsonNode args) throws Exception {
        Date currentTime = parseDate(args, "compareEnd");
        if (currentTime == null) currentTime = new Date();
        int baselineDays = args.path("baselineDays").asInt(7);
        double sigma = args.path("sigma").asDouble(2.0);
        String project = args.path("project").asText(null);

        List<Map<String, Object>> result = analysisService.detectAnomalies(
                currentTime, baselineDays, sigma, project);
        return objectMapper.writeValueAsString(Map.of("anomalies", result));
    }

    private String executeQueryMetrics(JsonNode args) throws Exception {
        // 使用时间序列 API 获取最近指标
        Calendar cal = Calendar.getInstance();
        Date endTime = cal.getTime();
        cal.add(Calendar.HOUR, -24);
        Date startTime = cal.getTime();

        Map<String, Object> result = analysisService.getFlowLogTimeSeries(
                startTime, endTime, null, null, null, "hourly");
        return objectMapper.writeValueAsString(Map.of(
                "period", "last 24 hours",
                "metrics", result));
    }

    private String executeListAlerts() throws Exception {
        // 获取包路径列表作为告警上下文
        List<String> packagePaths = analysisService.listPackagePaths();
        return objectMapper.writeValueAsString(Map.of(
                "monitoredPackages", packagePaths,
                "alertRuleCount", packagePaths.size()));
    }

    private String executeQuerySimulationStats() throws Exception {
        // 仿真统计 — 调用分析服务获取包概览
        Calendar cal = Calendar.getInstance();
        Date endTime = cal.getTime();
        cal.add(Calendar.DAY_OF_MONTH, -30);
        Date startTime = cal.getTime();

        List<Map<String, Object>> summary = analysisService.getPackageFlowSummary(startTime, endTime);
        return objectMapper.writeValueAsString(Map.of(
                "period", "last 30 days",
                "packageSummary", summary));
    }

    // ===== V5.22 AI Rule Authoring 工具实现 =====

    private String executeDraftRule(JsonNode args) throws Exception {
        String ruleType = args.path("ruleType").asText("");
        String project = args.path("project").asText("");
        String content = args.path("content").asText("");
        String createdBy = args.path("createdBy").asText("anonymous");
        String title = args.path("title").asText(null);
        String sessionId = args.path("sessionId").asText(null);
        String messageId = args.path("messageId").asText(null);

        if (ruleType.isEmpty() || project.isEmpty() || content.isEmpty()) {
            return "{\"error\": \"ruleType, project, content 参数必填\"}";
        }

        // 校验 content
        try {
            draftService.validateContent(ruleType, content);
        } catch (IllegalArgumentException e) {
            return objectMapper.writeValueAsString(Map.of(
                    "error", "content_validation_failed",
                    "message", e.getMessage()
            ));
        }

        DraftEntity draft = draftService.createDraft(ruleType, project, content, createdBy, title, "LLM", sessionId, messageId);
        ObjectNode out = objectMapper.createObjectNode();
        out.put("draftId", draft.getDraftId());
        out.put("status", draft.getStatus());
        out.put("ruleType", draft.getRuleType());
        out.put("project", draft.getProject());
        out.put("title", draft.getTitle());
        out.put("createdAt", draft.getCreatedAt() != null ? draft.getCreatedAt().toInstant().toString() : null);
        out.put("message", "草稿已创建。下一步:BA 在 UI 中查看 / 编辑 / 提交审批");
        return objectMapper.writeValueAsString(out);
    }

    private String executeListDrafts(JsonNode args) throws Exception {
        String project = args.path("project").asText(null);
        String status = args.path("status").asText(null);
        int limit = args.path("limit").asInt(50);

        List<DraftEntity> drafts;
        if (project != null && !project.isEmpty()) {
            drafts = draftService.listByProject(project, limit);
        } else if (status != null && !status.isEmpty()) {
            drafts = draftService.listByStatus(status, limit);
        } else {
            // 两者都空:返最近 50(简单实现,生产应该用分页)
            drafts = draftService.listByProject("default", limit);
            if (drafts.isEmpty()) {
                drafts = draftService.listByStatus(DraftEntity.STATUS_DRAFT, limit);
            }
        }

        ArrayNode arr = objectMapper.createArrayNode();
        for (DraftEntity d : drafts) {
            arr.add(draftService.toDto(d));
        }
        return objectMapper.writeValueAsString(Map.of("drafts", arr, "count", arr.size()));
    }

    private String executeGetDraft(JsonNode args) throws Exception {
        String draftId = args.path("draftId").asText("");
        if (draftId.isEmpty()) {
            return "{\"error\": \"draftId 参数必填\"}";
        }
        return draftService.get(draftId)
                .<String>map(d -> {
                    try {
                        return objectMapper.writeValueAsString(draftService.toDto(d));
                    } catch (JsonProcessingException e) {
                        return "{\"error\": \"serialize_failed: " + e.getMessage().replace("\"", "'") + "\"}";
                    }
                })
                .orElse("{\"error\": \"draft_not_found\", \"draftId\": \"" + draftId + "\"}");
    }

    private String executeSubmitDraft(JsonNode args) throws Exception {
        String draftId = args.path("draftId").asText("");
        String submittedBy = args.path("submittedBy").asText("anonymous");
        if (draftId.isEmpty()) {
            return "{\"error\": \"draftId 参数必填\"}";
        }
        try {
            DraftEntity d = draftService.submitForReview(draftId, submittedBy);
            return objectMapper.writeValueAsString(draftService.toDto(d));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return objectMapper.writeValueAsString(Map.of("error", "submit_failed", "message", e.getMessage()));
        }
    }

    private String executeRejectDraft(JsonNode args) throws Exception {
        String draftId = args.path("draftId").asText("");
        String reviewer = args.path("reviewer").asText("anonymous");
        String reason = args.path("reason").asText("");
        if (draftId.isEmpty()) {
            return "{\"error\": \"draftId 参数必填\"}";
        }
        try {
            DraftEntity d = draftService.reject(draftId, reviewer, reason);
            return objectMapper.writeValueAsString(draftService.toDto(d));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return objectMapper.writeValueAsString(Map.of("error", "reject_failed", "message", e.getMessage()));
        }
    }

    private String executeApproveDraft(JsonNode args) throws Exception {
        String draftId = args.path("draftId").asText("");
        String reviewer = args.path("reviewer").asText("anonymous");
        String comment = args.path("comment").asText("");
        if (draftId.isEmpty()) {
            return "{\"error\": \"draftId 参数必填\"}";
        }
        try {
            DraftEntity d = draftService.approve(draftId, reviewer, comment);
            return objectMapper.writeValueAsString(draftService.toDto(d));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return objectMapper.writeValueAsString(Map.of("error", "approve_failed", "message", e.getMessage()));
        }
    }

    private String executeApplyDraft(JsonNode args) throws Exception {
        String draftId = args.path("draftId").asText("");
        String packagePath = args.path("packagePath").asText("");
        String fileName = args.path("fileName").asText(null);
        String reviewer = args.path("reviewer").asText("anonymous");
        String versionComment = args.path("versionComment").asText(null);

        if (draftId.isEmpty() || packagePath.isEmpty()) {
            return "{\"error\": \"draftId 和 packagePath 必填\"}";
        }
        try {
            ObjectNode out = draftApplyService.applyToPackage(draftId, packagePath, fileName, reviewer, versionComment);
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            log.error("apply_draft failed: draftId={}", draftId, e);
            return objectMapper.writeValueAsString(Map.of("error", "apply_failed", "message", e.getMessage()));
        }
    }

    private String executeGenerateTestCases(JsonNode args) throws Exception {
        String draftId = args.path("draftId").asText("");
        int count = args.path("count").asInt(5);
        if (draftId.isEmpty()) {
            return "{\"error\": \"draftId 必填\"}";
        }
        DraftEntity draft = draftService.get(draftId).orElse(null);
        if (draft == null) {
            return objectMapper.writeValueAsString(Map.of("error", "draft_not_found", "draftId", draftId));
        }
        JsonNode content = objectMapper.readTree(draft.getContent());

        // 简单的"按行反推"生成:
        //   对每 row,取它的条件列 cellMap value 作 expected inputs
        //   生成 expectedAction 列表
        //   给变量名随机化值
        ArrayNode tests = objectMapper.createArrayNode();
        JsonNode rows = content.path("rows");
        JsonNode columns = content.path("columns");
        JsonNode cellMap = content.path("cellMap");

        if (!rows.isArray() || rows.size() == 0) {
            return objectMapper.writeValueAsString(Map.of(
                    "error", "no_rows",
                    "message", "草稿没有 rows,无法生成测试用例"
            ));
        }

        int generated = 0;
        for (JsonNode row : rows) {
            if (generated >= count) break;
            String rowId = row.path("rowId").asText("r" + generated);
            ObjectNode test = objectMapper.createObjectNode();
            test.put("name", "auto_" + rowId + "_" + draft.getRuleType());
            test.put("rowId", rowId);
            test.put("remark", row.path("remark").asText(""));

            // 收集 inputs(从 cellMap 里所有 condition 列)
            ObjectNode inputs = objectMapper.createObjectNode();
            ObjectNode expected = objectMapper.createObjectNode();
            if (columns.isArray()) {
                for (JsonNode col : columns) {
                    String type = col.path("type").asText("");
                    String variable = col.path("variable").asText("");
                    if (variable.isEmpty()) continue;
                    String key = rowId + "," + col.path("colId").asText("");
                    JsonNode cell = cellMap.path(key);
                    if (type.equals("condition") && !cell.isMissingNode()) {
                        inputs.set(variable, cell);
                    } else if (type.equals("action") && !cell.isMissingNode()) {
                        expected.set(variable, cell);
                    }
                }
            }
            test.set("inputs", inputs);
            test.set("expectedAction", expected);
            tests.add(test);
            generated++;
        }

        return objectMapper.writeValueAsString(Map.of(
                "draftId", draftId,
                "ruleType", draft.getRuleType(),
                "testCases", tests,
                "count", tests.size()
        ));
    }

    private String executeRunTest(JsonNode args) throws Exception {
        String draftId = args.path("draftId").asText("");
        if (draftId.isEmpty()) {
            return "{\"error\": \"draftId 必填\"}";
        }
        DraftEntity draft = draftService.get(draftId).orElse(null);
        if (draft == null) {
            return objectMapper.writeValueAsString(Map.of("error", "draft_not_found", "draftId", draftId));
        }
        JsonNode content = objectMapper.readTree(draft.getContent());
        // testCases 可能是 array 也可能是 string(向后兼容老调用)
        JsonNode testCasesNode = args.path("testCases");
        JsonNode testCases;
        if (testCasesNode.isArray()) {
            testCases = testCasesNode;
        } else {
            String testCasesJson = testCasesNode.asText("[]");
            testCases = objectMapper.readTree(testCasesJson);
        }

        ArrayNode results = objectMapper.createArrayNode();
        int passed = 0, failed = 0;
        for (JsonNode tc : testCases) {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("name", tc.path("name").asText(""));
            result.put("rowId", tc.path("rowId").asText(""));

            String matchedRowId = matchRow(content, tc.path("inputs"));
            if (matchedRowId != null) {
                result.put("matchedRowId", matchedRowId);
                result.put("status", "PASS");
                passed++;
            } else {
                result.put("matchedRowId", (String) null);
                result.put("status", "NO_MATCH");
                failed++;
            }
            results.add(result);
        }

        ObjectNode out = objectMapper.createObjectNode();
        out.put("draftId", draftId);
        out.put("passed", passed);
        out.put("failed", failed);
        out.set("results", results);
        return objectMapper.writeValueAsString(out);
    }

    // ===== V5.22.1 草稿测试用例持久化 =====

    private String executeListTestCases(JsonNode args) throws Exception {
        String draftId = args.path("draftId").asText("");
        if (draftId.isEmpty()) {
            return "{\"error\": \"draftId 必填\"}";
        }
        List<TestCaseEntity> cases = testCaseService.listByDraftId(draftId);
        ArrayNode arr = objectMapper.createArrayNode();
        for (TestCaseEntity tc : cases) {
            arr.add(testCaseService.toDto(tc));
        }
        return objectMapper.writeValueAsString(Map.of(
                "draftId", draftId,
                "testCases", arr,
                "count", arr.size()
        ));
    }

    private String executeAddTestCase(JsonNode args) throws Exception {
        String draftId = args.path("draftId").asText("");
        String name = args.path("name").asText("");
        String description = args.path("description").asText(null);
        String inputs = args.path("inputs").asText("");
        String expectedRowId = args.path("expectedRowId").asText(null);
        String createdBy = args.path("createdBy").asText("anonymous");
        String source = args.path("source").asText(TestCaseEntity.SOURCE_MANUAL);

        if (draftId.isEmpty() || name.isEmpty() || inputs.isEmpty()) {
            return "{\"error\": \"draftId, name, inputs 必填\"}";
        }
        try {
            TestCaseEntity tc = testCaseService.addTestCase(draftId, name, description, inputs, expectedRowId, createdBy, source);
            return objectMapper.writeValueAsString(testCaseService.toDto(tc));
        } catch (IllegalArgumentException e) {
            return objectMapper.writeValueAsString(Map.of("error", "add_test_case_failed", "message", e.getMessage()));
        }
    }

    private String executeDeleteTestCase(JsonNode args) throws Exception {
        String testCaseId = args.path("testCaseId").asText("");
        if (testCaseId.isEmpty()) {
            return "{\"error\": \"testCaseId 必填\"}";
        }
        boolean ok = testCaseService.deleteTestCase(testCaseId);
        return objectMapper.writeValueAsString(Map.of(
                "testCaseId", testCaseId,
                "deleted", ok
        ));
    }

    private String executeRunSavedTests(JsonNode args) throws Exception {
        String draftId = args.path("draftId").asText("");
        if (draftId.isEmpty()) {
            return "{\"error\": \"draftId 必填\"}";
        }
        DraftEntity draft = draftService.get(draftId).orElse(null);
        if (draft == null) {
            return objectMapper.writeValueAsString(Map.of("error", "draft_not_found", "draftId", draftId));
        }
        List<TestCaseEntity> cases = testCaseService.listByDraftId(draftId);
        if (cases.isEmpty()) {
            return objectMapper.writeValueAsString(Map.of(
                    "draftId", draftId,
                    "passed", 0, "failed", 0,
                    "results", objectMapper.createArrayNode(),
                    "message", "该草稿下没有保存的测试用例"
            ));
        }
        JsonNode content = objectMapper.readTree(draft.getContent());
        ArrayNode results = objectMapper.createArrayNode();
        int passed = 0, failed = 0;
        for (TestCaseEntity tc : cases) {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("testCaseId", tc.getTestCaseId());
            result.put("name", tc.getName());
            result.put("expectedRowId", tc.getExpectedRowId());
            try {
                JsonNode inputs = objectMapper.readTree(tc.getInputs());
                String matchedRowId = matchRow(content, inputs);
                result.put("matchedRowId", matchedRowId);
                // 状态:expected 不空时,看 matchedRowId 是否等于 expectedRowId
                String status;
                if (tc.getExpectedRowId() != null && !tc.getExpectedRowId().isEmpty()) {
                    boolean ok = tc.getExpectedRowId().equals(matchedRowId);
                    status = ok ? "PASS" : "FAIL";
                } else {
                    status = matchedRowId != null ? "PASS" : "NO_MATCH";
                }
                result.put("status", status);
                if ("PASS".equals(status)) passed++; else failed++;
            } catch (Exception e) {
                result.put("status", "ERROR");
                result.put("error", e.getMessage());
                failed++;
            }
            results.add(result);
        }
        ObjectNode out = objectMapper.createObjectNode();
        out.put("draftId", draftId);
        out.put("passed", passed);
        out.put("failed", failed);
        out.put("total", cases.size());
        out.set("results", results);
        return objectMapper.writeValueAsString(out);
    }

    // ===== V5.22.2 规则健康仪表盘 =====

    /**
     * 给 BA 看的规则健康总览。聚合多个数据源:
     * <ul>
     *   <li>staleDrafts — 草稿滞留(DRAFT/PENDING_REVIEW 超过 3 天)</li>
     *   <li>deadRules — 死规则(过去 N 天内从未触发)</li>
     *   <li>hotRules — 热规则 Top 5(触发频率最高)</li>
     *   <li>recentAnomalies — 最近异常事件</li>
     *   <li>topRejectReasons — Top 5 拒绝原因</li>
     * </ul>
     */
    private String executeGetRuleHealth(JsonNode args) throws Exception {
        String project = args.path("project").asText(null);
        int days = args.path("days").asInt(30);

        // 时间窗口
        Date endTime = new Date();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -days);
        Date startTime = cal.getTime();

        // stale draft 阈值(DRAFT/PENDING_REVIEW > 3 天未动)
        long staleThresholdMs = 3L * 24 * 3600 * 1000;
        Date staleBefore = new Date(System.currentTimeMillis() - staleThresholdMs);

        ObjectNode out = objectMapper.createObjectNode();
        out.put("project", project != null ? project : "all");
        out.put("days", days);
        out.put("generatedAt", new Date().toInstant().toString());

        // V5.22.3 — 跟踪每个 sub-source 成功/失败,用于 DEGRADED 标记
        ArrayNode failedSources = objectMapper.createArrayNode();
        int sourceCount = 0;
        int failedCount = 0;

        // 1) 死规则(从覆盖率分析里)
        try {
            Map<String, Object> coverage = analysisService.getRuleCoverageAnalysis(project, startTime, endTime);
            // coverage 是 Map,可能含 deadRules / hotRules / totalRules / activeRules 等
            ObjectNode coverageNode = objectMapper.valueToTree(coverage);
            out.set("coverage", coverageNode);
            sourceCount++;
        } catch (Exception e) {
            log.warn("getRuleCoverageAnalysis 失败: {}", e.getMessage());
            out.set("coverage", objectMapper.createObjectNode());
            failedSources.add("coverage");
            failedCount++;
            sourceCount++;
        }

        // 2) 热规则 Top 5
        try {
            List<Map<String, Object>> freq = analysisService.getRuleFireFrequency(startTime, endTime, project);
            ArrayNode hot = objectMapper.createArrayNode();
            int limit = Math.min(5, freq.size());
            for (int i = 0; i < limit; i++) {
                hot.add(objectMapper.valueToTree(freq.get(i)));
            }
            out.set("hotRules", hot);
            sourceCount++;
        } catch (Exception e) {
            log.warn("getRuleFireFrequency 失败: {}", e.getMessage());
            out.set("hotRules", objectMapper.createArrayNode());
            failedSources.add("hotRules");
            failedCount++;
            sourceCount++;
        }

        // 3) 最近异常
        try {
            List<Map<String, Object>> anomalies = analysisService.detectAnomalies(endTime, days, 2.0, project);
            ArrayNode arr = objectMapper.createArrayNode();
            int limit = Math.min(10, anomalies.size());
            for (int i = 0; i < limit; i++) {
                arr.add(objectMapper.valueToTree(anomalies.get(i)));
            }
            out.set("recentAnomalies", arr);
            sourceCount++;
        } catch (Exception e) {
            log.warn("detectAnomalies 失败: {}", e.getMessage());
            out.set("recentAnomalies", objectMapper.createArrayNode());
            failedSources.add("recentAnomalies");
            failedCount++;
            sourceCount++;
        }

        // 4) Top 拒绝原因
        try {
            List<Map<String, Object>> reject = analysisService.getRejectDistribution(startTime, endTime, project, 5);
            out.set("topRejectReasons", objectMapper.valueToTree(reject));
            sourceCount++;
        } catch (Exception e) {
            log.warn("getRejectDistribution 失败: {}", e.getMessage());
            out.set("topRejectReasons", objectMapper.createArrayNode());
            failedSources.add("topRejectReasons");
            failedCount++;
            sourceCount++;
        }

        // 5) 滞留草稿
        ArrayNode staleDrafts = objectMapper.createArrayNode();
        try {
            // DRAFT 滞留:创建超过 3 天还没提交审批
            List<DraftEntity> drafts = draftService.listByStatus(DraftEntity.STATUS_DRAFT, 200);
            for (DraftEntity d : drafts) {
                if (project != null && !project.isEmpty() && !project.equals(d.getProject())) continue;
                Date createdAt = d.getCreatedAt();
                if (createdAt != null && createdAt.before(staleBefore)) {
                    ObjectNode n = objectMapper.createObjectNode();
                    n.put("draftId", d.getDraftId());
                    n.put("title", d.getTitle());
                    n.put("status", d.getStatus());
                    n.put("project", d.getProject());
                    n.put("createdBy", d.getCreatedBy());
                    n.put("createdAt", createdAt.toInstant().toString());
                    long daysOld = (System.currentTimeMillis() - createdAt.getTime()) / (24L * 3600 * 1000);
                    n.put("daysOld", daysOld);
                    staleDrafts.add(n);
                }
            }
            // PENDING_REVIEW 滞留
            List<DraftEntity> pending = draftService.listByStatus(DraftEntity.STATUS_PENDING_REVIEW, 200);
            for (DraftEntity d : pending) {
                if (project != null && !project.isEmpty() && !project.equals(d.getProject())) continue;
                Date updatedAt = d.getUpdatedAt();
                if (updatedAt != null && updatedAt.before(staleBefore)) {
                    ObjectNode n = objectMapper.createObjectNode();
                    n.put("draftId", d.getDraftId());
                    n.put("title", d.getTitle());
                    n.put("status", d.getStatus());
                    n.put("project", d.getProject());
                    n.put("createdBy", d.getCreatedBy());
                    n.put("updatedAt", updatedAt.toInstant().toString());
                    long daysOld = (System.currentTimeMillis() - updatedAt.getTime()) / (24L * 3600 * 1000);
                    n.put("daysOld", daysOld);
                    staleDrafts.add(n);
                }
            }
            sourceCount++;
        } catch (Exception e) {
            log.warn("staleDrafts 收集失败: {}", e.getMessage());
            failedSources.add("staleDrafts");
            failedCount++;
            sourceCount++;
        }
        out.set("staleDrafts", staleDrafts);
        out.put("staleDraftCount", staleDrafts.size());

        // V5.22.3 — DEGRADED 标记:5 个 sub-source 全炸时返 status
        // 部分失败: status=PARTIAL, 完整: status=OK
        String status;
        if (failedCount == 0) {
            status = "OK";
        } else if (failedCount >= sourceCount) {
            status = "DEGRADED";
        } else {
            status = "PARTIAL";
        }
        out.put("status", status);
        out.put("failedSources", failedSources);
        out.put("failedSourceCount", failedCount);
        out.put("totalSourceCount", sourceCount);

        return objectMapper.writeValueAsString(out);
    }

    /**
     * 简单行匹配:对 content.rows 的每 row,看它的所有 condition cells 是否都满足
     * V5.22 v0 — 支持 eq/neq/lt/lte/gt/gte (string 去引号)
     */
    private String matchRow(JsonNode content, JsonNode inputs) {
        JsonNode rows = content.path("rows");
        JsonNode columns = content.path("columns");
        JsonNode cellMap = content.path("cellMap");
        if (!rows.isArray()) return null;

        for (JsonNode row : rows) {
            String rowId = row.path("rowId").asText("");
            if (rowId.isEmpty()) continue;
            boolean allMatch = true;
            if (columns.isArray()) {
                for (JsonNode col : columns) {
                    if (!col.path("type").asText("").equals("condition")) continue;
                    String variable = col.path("variable").asText("");
                    String op = col.path("operator").asText("eq");
                    String key = rowId + "," + col.path("colId").asText("");
                    JsonNode expected = cellMap.path(key);
                    if (expected.isMissingNode()) continue;
                    JsonNode actual = inputs.path(variable);
                    if (actual.isMissingNode() || !compareValues(op, expected, actual)) {
                        allMatch = false;
                        break;
                    }
                }
            }
            if (allMatch) return rowId;
        }
        return null;
    }

    private boolean compareValues(String op, JsonNode expected, JsonNode actual) {
        // string 值的引号剥掉:'REJECTED' -> REJECTED
        JsonNode exp = stripQuotes(expected);

        switch (op) {
            case "eq", "":
                return valuesEqual(exp, actual);
            case "neq":
                return !valuesEqual(exp, actual);
            case "lt":
                return numbers(actual, exp) < 0;   // actual < expected
            case "lte":
                return numbers(actual, exp) <= 0;  // actual ≤ expected
            case "gt":
                return numbers(actual, exp) > 0;   // actual > expected
            case "gte":
                return numbers(actual, exp) >= 0;  // actual ≥ expected
            default:
                return valuesEqual(exp, actual);
        }
    }

    private JsonNode stripQuotes(JsonNode n) {
        if (n != null && n.isTextual()) {
            String s = n.asText();
            if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
                return objectMapper.getNodeFactory().textNode(s.substring(1, s.length() - 1));
            }
        }
        return n;
    }

    private int numbers(JsonNode a, JsonNode b) {
        double x = parseAsDouble(a);
        double y = parseAsDouble(b);
        if (Double.isNaN(x) || Double.isNaN(y)) return 1; // 不可比,返 1 表示不匹配
        return Double.compare(x, y);
    }

    private double parseAsDouble(JsonNode n) {
        if (n == null || n.isNull()) return Double.NaN;
        if (n.isNumber()) return n.asDouble();
        if (n.isTextual()) {
            try { return Double.parseDouble(n.asText()); }
            catch (NumberFormatException e) { return Double.NaN; }
        }
        return Double.NaN;
    }

    private boolean valuesEqual(JsonNode a, JsonNode b) {
        if (a.isNumber() && b.isNumber()) {
            return Double.compare(a.asDouble(), b.asDouble()) == 0;
        }
        if (a.isTextual() && b.isTextual()) {
            return a.asText().equals(b.asText());
        }
        if (a.isBoolean() && b.isBoolean()) {
            return a.asBoolean() == b.asBoolean();
        }
        return a.equals(b);
    }

    // ===== 辅助方法 =====

    private Date parseDate(JsonNode args, String field) {
        String dateStr = args.path(field).asText("");
        if (dateStr.isEmpty()) return null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            return sdf.parse(dateStr);
        } catch (Exception e) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                return sdf.parse(dateStr);
            } catch (Exception e2) {
                log.warn("Cannot parse date '{}': {}", dateStr, e2.getMessage());
                return null;
            }
        }
    }

    // ===== V5.22.3 草稿历史 + 工具调用审计 =====

    private String executeGetDraftHistory(JsonNode args) throws Exception {
        String draftId = args.path("draftId").asText("");
        if (draftId.isEmpty()) return "{\"error\": \"draftId 参数必填\"}";

        if (draftService.get(draftId).isEmpty()) {
            return "{\"error\": \"草稿不存在 draftId=" + draftId + "\"}";
        }

        List<DraftHistoryEntity> history = draftHistoryService.listByDraftId(draftId);
        ArrayNode arr = objectMapper.createArrayNode();
        for (DraftHistoryEntity h : history) {
            arr.add(draftHistoryService.toDto(h));
        }
        return objectMapper.writeValueAsString(Map.of(
                "draftId", draftId,
                "history", arr,
                "count", history.size()
        ));
    }

    private String executeListAgentAudit(JsonNode args) throws Exception {
        String userId = args.path("userId").asText(null);
        String sessionId = args.path("sessionId").asText(null);
        String status = args.path("status").asText(null);
        int limit = args.path("limit").asInt(50);
        if (limit <= 0 || limit > 200) limit = 50;

        List<AgentAuditEntity> rows = agentAuditService.listByFilter(userId, sessionId, status, limit);
        ArrayNode arr = objectMapper.createArrayNode();
        for (AgentAuditEntity a : rows) {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("id", a.getId());
            n.put("sessionId", a.getSessionId());
            n.put("messageId", a.getMessageId());
            n.put("userId", a.getUserId());
            n.put("toolName", a.getToolName());
            n.put("argsSummary", a.getArgsSummary());
            n.put("resultSize", a.getResultSize());
            n.put("status", a.getStatus());
            n.put("errorCode", a.getErrorCode());
            n.put("durationMs", a.getDurationMs());
            n.put("at", a.getCreatedAt() != null ? a.getCreatedAt().toInstant().toString() : null);
            arr.add(n);
        }
        return objectMapper.writeValueAsString(Map.of(
                "audits", arr,
                "count", rows.size()
        ));
    }
}
