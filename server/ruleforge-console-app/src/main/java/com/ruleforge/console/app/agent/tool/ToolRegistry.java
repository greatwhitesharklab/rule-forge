package com.ruleforge.console.app.agent.tool;

import com.ruleforge.console.app.agent.model.AgentModels.*;
import com.ruleforge.console.app.agent.model.AgentModels.ToolDef.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Agent 工具注册表。
 *
 * <p>定义了 LLM 可以调用的所有工具，每个工具对应一个现有的内部服务方法。
 * 工具执行由 {@link com.ruleforge.console.app.agent.AgentService} 负责。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {

    /** 工具名 → 定义 */
    private final Map<String, ToolDef> toolDefs = new LinkedHashMap<>();

    // 工具名称常量
    public static final String LIST_PROJECTS = "list_projects";
    public static final String LIST_PACKAGES = "list_packages";
    public static final String EXPORT_PACKAGE = "export_package";
    public static final String QUERY_FLOW_TIMESERIES = "query_flow_timeseries";
    public static final String QUERY_REJECT_DISTRIBUTION = "query_reject_distribution";
    public static final String QUERY_RULE_COVERAGE = "query_rule_coverage";
    public static final String QUERY_RULE_FIRE_FREQUENCY = "query_rule_fire_frequency";
    public static final String DETECT_ANOMALIES = "detect_anomalies";
    public static final String QUERY_METRICS = "query_metrics";
    public static final String LIST_ALERTS = "list_alerts";
    public static final String QUERY_SIMULATION_STATS = "query_simulation_stats";

    /**
     * 初始化时注册所有工具
     */
    public List<ToolDef> getAllTools() {
        if (toolDefs.isEmpty()) {
            registerAll();
        }
        return new ArrayList<>(toolDefs.values());
    }

    public ToolDef getTool(String name) {
        return toolDefs.get(name);
    }

    private void registerAll() {
        register(LIST_PROJECTS, "列出所有项目名称", Collections.emptyList());
        register(LIST_PACKAGES, "列出指定项目的知识包列表",
                List.of(prop("project", "string", "项目名称")));
        register(EXPORT_PACKAGE, "导出指定知识包的完整内容（规则定义、参数、变量等）",
                List.of(prop("project", "string", "项目名称"),
                        prop("packageId", "string", "知识包 ID")));
        register(QUERY_FLOW_TIMESERIES, "查询决策流执行时间序列趋势",
                List.of(prop("startTime", "string", "开始时间 (ISO格式，如 2026-01-01T00:00:00)"),
                        prop("endTime", "string", "结束时间 (ISO格式)"),
                        prop("granularity", "string", "时间粒度: hourly/daily（默认 hourly）")));
        register(QUERY_REJECT_DISTRIBUTION, "查询拒绝原因分布",
                List.of(prop("startTime", "string", "开始时间 (ISO格式)"),
                        prop("endTime", "string", "结束时间 (ISO格式)")));
        register(QUERY_RULE_COVERAGE, "查询规则覆盖率分析（已触发 vs 未触发的规则）",
                List.of(prop("project", "string", "项目名称"),
                        prop("startTime", "string", "开始时间 (ISO格式)"),
                        prop("endTime", "string", "结束时间 (ISO格式)")));
        register(QUERY_RULE_FIRE_FREQUENCY, "查询规则触发频率排行",
                List.of(prop("project", "string", "项目名称"),
                        prop("startTime", "string", "开始时间 (ISO格式)"),
                        prop("endTime", "string", "结束时间 (ISO格式)")));
        register(DETECT_ANOMALIES, "检测异常决策模式（与基线偏差超过阈值）",
                List.of(prop("baselineStart", "string", "基线开始时间 (ISO格式)"),
                        prop("baselineEnd", "string", "基线结束时间 (ISO格式)"),
                        prop("compareStart", "string", "对比开始时间 (ISO格式)"),
                        prop("compareEnd", "string", "对比结束时间 (ISO格式)"),
                        prop("sigma", "number", "异常阈值标准差倍数（默认 2.0）")));
        register(QUERY_METRICS, "查询执行性能指标（P50/P95/P99延迟、成功率）",
                List.of(prop("startTime", "string", "开始时间 (ISO格式)"),
                        prop("endTime", "string", "结束时间 (ISO格式)")));
        register(LIST_ALERTS, "查询告警规则和最近的告警历史", Collections.emptyList());
        register(QUERY_SIMULATION_STATS, "查询规则仿真统计信息", Collections.emptyList());

        log.info("Registered {} agent tools", toolDefs.size());
    }

    private void register(String name, String description, List<PropertyDef> props) {
        ToolDef def = new ToolDef();
        FunctionDef fn = new FunctionDef();
        fn.setName(name);
        fn.setDescription(description);

        ParametersDef params = new ParametersDef();
        Map<String, PropertyDef> propMap = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (int i = 0; i < props.size(); i++) {
            PropertyDef p = props.get(i);
            propMap.put(p.getType() + "_" + i, p); // temp key
        }
        // Re-map with proper names
        Map<String, PropertyDef> finalMap = new LinkedHashMap<>();
        for (PropertyDef p : props) {
            // Use description-derived key
            String key = p.getDescription().replaceAll("[^a-zA-Z0-9_]", "_");
            finalMap.put(key, p);
        }
        params.setProperties(props.isEmpty() ? null : toPropertyMap(props));
        params.setRequired(null);
        fn.setParameters(params);

        def.setFunction(fn);
        toolDefs.put(name, def);
    }

    private Map<String, PropertyDef> toPropertyMap(List<PropertyDef> props) {
        Map<String, PropertyDef> map = new LinkedHashMap<>();
        for (PropertyDef p : props) {
            // Extract param name from description (convention: first word before space is paramName)
            String desc = p.getDescription();
            String paramName = desc.split("[\\s(（]")[0];
            PropertyDef entry = new PropertyDef();
            entry.setType(p.getType());
            entry.setDescription(desc);
            map.put(paramName, entry);
        }
        return map;
    }

    private static PropertyDef prop(String name, String type, String desc) {
        PropertyDef p = new PropertyDef();
        p.setType(type);
        p.setDescription(name + " - " + desc);
        return p;
    }
}
