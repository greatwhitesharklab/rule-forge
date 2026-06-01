package com.ruleforge.console.app.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
}
