package com.ruleforge.debug;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6.9.29 — {@link MessageItem#toHtml()} 颜色映射契约 BDD。
 *
 * <p>锁 V6.9.29 收口 (MessageItem.java L33-62: 28 行 switch (8 cases) → static
 * EnumMap {@code COLOR_BY_TYPE} + {@code getOrDefault(type, "#000")}, 砍重复
 * {@code case ConsoleOutput: color = "#000"} dead branch) 的行为不变性:
 * <ul>
 *   <li>每个 {@link MsgType} 映射到原 switch 等价颜色</li>
 *   <li>未列出的 MsgType (default) → "#000"</li>
 *   <li>HTML 结构 {@code <div style="color:{color};margin:2px">{msg}</div>} 不变</li>
 * </ul>
 */
@DisplayName("V6.9.29 — MessageItem.toHtml() EnumMap 契约")
class MessageItemToHtmlEnumMapTest {

    @Test
    @DisplayName("Condition → #6495ED")
    void conditionBlue() {
        assertThat(html(MsgType.Condition)).contains("color:#6495ED");
    }

    @Test
    @DisplayName("ConsoleOutput → #000 (V6.9.29 砍重复 case 后仍 #000, 行为不变)")
    void consoleOutputBlack() {
        assertThat(html(MsgType.ConsoleOutput)).contains("color:#000");
    }

    @Test
    @DisplayName("ExecuteBeanMethod → #8A2BE2")
    void executeBeanMethodPurple() {
        assertThat(html(MsgType.ExecuteBeanMethod)).contains("color:#8A2BE2");
    }

    @Test
    @DisplayName("ExecuteFunction → #008B8B")
    void executeFunctionTeal() {
        assertThat(html(MsgType.ExecuteFunction)).contains("color:#008B8B");
    }

    @Test
    @DisplayName("RuleFlow → #9932CC")
    void ruleFlowPurple() {
        assertThat(html(MsgType.RuleFlow)).contains("color:#9932CC");
    }

    @Test
    @DisplayName("VarAssign → #FF7F50")
    void varAssignCoral() {
        assertThat(html(MsgType.VarAssign)).contains("color:#FF7F50");
    }

    @Test
    @DisplayName("ScoreCard → #40E0D0")
    void scoreCardTurquoise() {
        assertThat(html(MsgType.ScoreCard)).contains("color:#40E0D0");
    }

    @Test
    @DisplayName("RuleMatch → #666600")
    void ruleMatchOlive() {
        assertThat(html(MsgType.RuleMatch)).contains("color:#666600");
    }

    @Test
    @DisplayName("HTML 结构保留: <div style=\"color:{color};margin:2px\">{msg}</div>")
    void htmlStructurePreserved() {
        MessageItem item = new MessageItem("hello", MsgType.Condition);
        String html = item.toHtml();
        assertThat(html)
            .startsWith("<div style=\"")
            .contains("margin:2px")
            .contains(">hello</div>");
    }

    private static String html(MsgType type) {
        return new MessageItem("test", type).toHtml();
    }
}
