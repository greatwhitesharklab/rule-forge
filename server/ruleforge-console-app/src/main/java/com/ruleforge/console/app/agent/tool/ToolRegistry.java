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

    // V5.22 — AI Rule Authoring tools
    public static final String DRAFT_RULE = "draft_rule";
    public static final String LIST_DRAFTS = "list_drafts";
    public static final String GET_DRAFT = "get_draft";
    public static final String SUBMIT_DRAFT = "submit_draft";
    public static final String REJECT_DRAFT = "reject_draft";
    public static final String APPROVE_DRAFT = "approve_draft";
    public static final String APPLY_DRAFT = "apply_draft";
    public static final String GENERATE_TEST_CASES = "generate_test_cases";
    public static final String RUN_TEST = "run_test";

    // V5.22.1 — 草稿测试用例持久化 + 跑 saved tests
    public static final String LIST_TEST_CASES = "list_test_cases";
    public static final String ADD_TEST_CASE = "add_test_case";
    public static final String DELETE_TEST_CASE = "delete_test_case";
    public static final String RUN_SAVED_TESTS = "run_saved_tests";

    // V5.22.2 — 规则健康仪表盘(给 BA 看:死规则 / 热规则 / 滞留草稿 / 异常)
    public static final String GET_RULE_HEALTH = "get_rule_health";

    // V5.22.3 — 草稿状态历史 + 工具调用历史
    public static final String GET_DRAFT_HISTORY = "get_draft_history";
    public static final String LIST_AGENT_AUDIT = "list_agent_audit";

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

        // V5.22 — AI Rule Authoring tools
        register(DRAFT_RULE, "创建 AI 规则草稿。LLM 调用此工具把生成的规则存到 rf_draft 表,返回 draftId 给 BA 审批",
                List.of(prop("ruleType", "string", "ruleType - 规则类型: decision_table/ul/decision_tree/scorecard/script_decision_table"),
                        prop("project", "string", "project - 项目名"),
                        prop("content", "string", "content - 规则 JSON 字符串,跟 schema/{ruleType}.json 一致"),
                        prop("createdBy", "string", "createdBy - 创建人(用户/agent 名)"),
                        prop("title", "string", "title - 可选,草稿标题"),
                        prop("sessionId", "string", "sessionId - 可选,LLM 会话 ID"),
                        prop("messageId", "string", "messageId - 可选,LLM 消息 ID")));
        register(LIST_DRAFTS, "列草稿(按项目或按状态过滤)",
                List.of(prop("project", "string", "project - 可选,项目名过滤"),
                        prop("status", "string", "status - 可选,状态过滤 DRAFT/PENDING_REVIEW/APPROVED/REJECTED/EXPIRED"),
                        prop("limit", "number", "limit - 默认 50")));
        register(GET_DRAFT, "取草稿详情",
                List.of(prop("draftId", "string", "draftId - 草稿 ID")));
        register(SUBMIT_DRAFT, "把 DRAFT 草稿提交审批(DRAFT → PENDING_REVIEW)",
                List.of(prop("draftId", "string", "draftId - 草稿 ID"),
                        prop("submittedBy", "string", "submittedBy - 提交人")));
        register(REJECT_DRAFT, "拒绝草稿(必须 PENDING_REVIEW 状态)",
                List.of(prop("draftId", "string", "draftId - 草稿 ID"),
                        prop("reviewer", "string", "reviewer - 审批人"),
                        prop("reason", "string", "reason - 拒绝原因")));
        register(APPROVE_DRAFT, "审批通过草稿(必须 PENDING_REVIEW 状态)",
                List.of(prop("draftId", "string", "draftId - 草稿 ID"),
                        prop("reviewer", "string", "reviewer - 审批人"),
                        prop("comment", "string", "comment - 审批意见")));
        register(APPLY_DRAFT, "把审批通过的草稿写入目标包,生成新版本文件",
                List.of(prop("draftId", "string", "draftId - 草稿 ID"),
                        prop("packagePath", "string", "packagePath - 目标包路径"),
                        prop("fileName", "string", "fileName - 可选,落地文件名(默认 rule_<type>_<draftId>.json)"),
                        prop("reviewer", "string", "reviewer - 审批人(一步到位时)"),
                        prop("versionComment", "string", "versionComment - 版本说明")));
        register(GENERATE_TEST_CASES, "为草稿生成测试用例模板(基于 cellMap 反推每个 row 的 condition 真假组合)",
                List.of(prop("draftId", "string", "draftId - 草稿 ID"),
                        prop("count", "number", "count - 生成数量,默认 5")));
        register(RUN_TEST, "用测试输入跑草稿(本地 mock 执行,验证 cellMap 走哪一行)",
                List.of(prop("draftId", "string", "draftId - 草稿 ID"),
                        prop("testCases", "string", "testCases - JSON 数组字符串:[{\"name\":\"...\",\"inputs\":{...},\"expectAction\":{...}}, ...]")));

        // V5.22.1 — 草稿测试用例持久化 + 跑 saved tests
        register(LIST_TEST_CASES, "列草稿下所有保存的测试用例",
                List.of(prop("draftId", "string", "draftId - 草稿 ID")));
        register(ADD_TEST_CASE, "给草稿加一个测试用例(BA 手动 / LLM 自动生成的,都走这个落库)",
                List.of(prop("draftId", "string", "draftId - 草稿 ID"),
                        prop("name", "string", "name - 用例名"),
                        prop("description", "string", "description - 描述 (可空)"),
                        prop("inputs", "string", "inputs - 入参 JSON 字符串,例: {\"age\":17,\"income\":5000}"),
                        prop("expectedRowId", "string", "expectedRowId - 期望命中的行 ID (可空)"),
                        prop("createdBy", "string", "createdBy - 创建人"),
                        prop("source", "string", "source - MANUAL/LLM (默认 MANUAL)")));
        register(DELETE_TEST_CASE, "删测试用例",
                List.of(prop("testCaseId", "string", "testCaseId - 用例 ID")));
        register(RUN_SAVED_TESTS, "跑草稿下所有保存的测试用例(从 rf_draft_test_case 拉,逐个 matchRow)",
                List.of(prop("draftId", "string", "draftId - 草稿 ID")));

        // V5.22.2 — 规则健康仪表盘
        register(GET_RULE_HEALTH, "给 BA 看规则健康总览:死规则 / 热规则 / 滞留草稿 / 异常事件 / Top 拒绝原因",
                List.of(prop("project", "string", "project - 可选,只看某个项目"),
                        prop("days", "number", "days - 时间窗口(默认 30 天)")));

        // V5.22.3 — 草稿状态历史 / 工具调用审计
        register(GET_DRAFT_HISTORY, "取草稿的完整状态历史时间线(CREATE/SUBMIT/APPROVE/REJECT/APPLY/EXPIRE)",
                List.of(prop("draftId", "string", "draftId - 草稿 ID")));
        register(LIST_AGENT_AUDIT, "列工具调用审计(自己调过的工具,按时间倒序)",
                List.of(prop("userId", "string", "userId - 可选,只看自己;不传看所有"),
                        prop("sessionId", "string", "sessionId - 可选,只看某个会话"),
                        prop("status", "string", "status - 可选,过滤 OK/ERROR/RATE_LIMITED"),
                        prop("limit", "number", "limit - 默认 50")));

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
