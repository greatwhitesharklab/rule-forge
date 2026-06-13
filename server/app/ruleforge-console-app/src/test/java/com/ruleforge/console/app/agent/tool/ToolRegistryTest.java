package com.ruleforge.console.app.agent.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * B-1 回归测试:getTool 必须在 getAllTools 从未被调用时也能返回已注册的 tool。
 *
 * <p>背景:AlertBell 在 frame 启动早期轮询 /agent/tools/list_drafts,后端 invokeTool
 * 调 toolRegistry.getTool(name)。修复前 getTool 不触发懒加载,toolDefs 为空时返回 null,
 * 导致所有 agent tool 首次调用 404(实测 list_drafts 几分钟内 39 次 404)。
 */
class ToolRegistryTest {

    @Test
    @DisplayName("getTool 在 getAllTools 未调用前也能返回已注册 tool(懒加载,B-1 修复)")
    void getTool_returnsRegisteredTool_withoutPriorGetAllToolsCall() {
        ToolRegistry registry = new ToolRegistry();
        // 不调 getAllTools(),直接 getTool —— 修复前返回 null
        assertNotNull(registry.getTool(ToolRegistry.LIST_DRAFTS),
                "getTool 必须触发懒加载,否则首次 tool 调用 404");
        assertNotNull(registry.getTool(ToolRegistry.DRAFT_RULE));
        assertNotNull(registry.getTool(ToolRegistry.LIST_PROJECTS));
    }

    @Test
    @DisplayName("getTool 对未注册 tool 返回 null")
    void getTool_returnsNullForUnknownTool() {
        ToolRegistry registry = new ToolRegistry();
        assertNull(registry.getTool("nonexistent_tool_xyz"));
    }
}
