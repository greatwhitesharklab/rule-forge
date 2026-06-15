package com.ruleforge.runtime.rete;

import com.ruleforge.debug.MessageItem;
import com.ruleforge.debug.MsgType;
import com.ruleforge.model.rule.Op;
import com.ruleforge.model.rule.SimpleValue;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.EvaluateResponse;
import com.ruleforge.model.rule.lhs.Left;
import com.ruleforge.model.rule.lhs.LeftType;
import com.ruleforge.model.rule.lhs.VariableLeftPart;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.rete.test.EngineContextWirer;
import com.ruleforge.runtime.WorkingMemory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * V5.88 — {@code CriteriaActivity.logMessage} 早返契约 BDD。
 *
 * <p>锁 logMessage 在以下行为契约(V5.88 早返优化前后必须保持一致):
 * <ul>
 *   <li>{@code debug=true}:走完整 String.format + context.logMsg,生成 MessageItem 写入
 *       {@code executeMessageItems}</li>
 *   <li>{@code debug=false}:早返,不调 String.format / toString / context.logMsg,
 *       {@code executeMessageItems} 保持空</li>
 * </ul>
 *
 * <p>JFR 35s 抓 2053 sample,reveal logMessage + String.format + StringBuilder 占 1570
 * sample(76%)。V5.88 加 1 行早返,预期 per-fact 节约 30-50% — 详见
 * [[v587-jfr-flamegraph]] V5.88 优化方向。
 *
 * @since 5.88
 */
@DisplayName("V5.88 — CriteriaActivity.logMessage 早返契约")
class CriteriaActivityLogMessageTest {

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    @Test
    @DisplayName("Given CriteriaActivity debug=true, When enter 触发 logMessage, Then executeMessageItems 增 1 MessageItem")
    void debugTrueLogsMessage() {
        // 构造 minimal Criteria(name="X", value="alice")
        Criteria c = buildCriteria("X", "name", "alice");
        CriteriaActivity activity = new CriteriaActivity(c, /* debug= */ true);

        WorkingMemory wm = newEmptyWorkingMemory();
        List<MessageItem> msgItems = new ArrayList<>();
        EvaluationContextImpl ctx = new EvaluationContextImpl(
            wm, new HashMap<>(), msgItems);

        // 调 enter:fact 不存在 → criteria evaluate 走 no-match, logMessage 仍触发
        Object fact = new Object();
        activity.enter(ctx, fact, new FactTracker());

        assertEquals(1, msgItems.size(),
            "V5.88 契约: debug=true 时 enter 触发 logMessage → executeMessageItems 增 1 MessageItem");
        MessageItem item = msgItems.get(0);
        assertNotNull(item);
        assertEquals(MsgType.Condition, item.getType(),
            "V5.88 契约: logMessage 用 MsgType.Condition");
    }

    @Test
    @DisplayName("Given CriteriaActivity debug=false, When enter 触发 logMessage, Then executeMessageItems 保持空(早返)")
    void debugFalseSkipsLog() {
        Criteria c = buildCriteria("X", "name", "alice");
        CriteriaActivity activity = new CriteriaActivity(c, /* debug= */ false);

        WorkingMemory wm = newEmptyWorkingMemory();
        List<MessageItem> msgItems = new ArrayList<>();
        EvaluationContextImpl ctx = new EvaluationContextImpl(
            wm, new HashMap<>(), msgItems);

        Object fact = new Object();
        activity.enter(ctx, fact, new FactTracker());

        assertEquals(0, msgItems.size(),
            "V5.88 契约: debug=false 时 logMessage 早返,executeMessageItems 保持空(无 String.format/toString 开销)");
    }

    @Test
    @DisplayName("Given CriteriaActivity debug=false, When 1000 次 enter, Then executeMessageItems 仍空(锁 V5.87 JFR 76% hot path 消失)")
    void debugFalseThousandEntersNoLog() {
        Criteria c = buildCriteria("X", "name", "alice");
        CriteriaActivity activity = new CriteriaActivity(c, /* debug= */ false);

        WorkingMemory wm = newEmptyWorkingMemory();
        List<MessageItem> msgItems = new ArrayList<>();
        EvaluationContextImpl ctx = new EvaluationContextImpl(
            wm, new HashMap<>(), msgItems);

        Object fact = new Object();
        for (int i = 0; i < 1000; i++) {
            activity.enter(ctx, fact, new FactTracker());
        }

        assertEquals(0, msgItems.size(),
            "V5.88: 1000 次 enter, debug=false 全部早返,executeMessageItems 仍 0");
    }

    // ====== helpers ======

    private Criteria buildCriteria(String varCat, String varName, String value) {
        Criteria c = new Criteria();
        Left left = new Left();
        left.setType(LeftType.variable);
        VariableLeftPart part = new VariableLeftPart();
        part.setVariableCategory(varCat);
        part.setVariableName(varName);
        part.setVariableLabel(varName);
        part.setDatatype(Datatype.String);
        left.setLeftPart(part);
        c.setLeft(left);
        c.setOp(Op.Equals);
        SimpleValue sv = new SimpleValue();
        sv.setContent(value);
        c.setValue(sv);
        return c;
    }

    private WorkingMemory newEmptyWorkingMemory() {
        // Mockito mock WorkingMemory — V5.81 PR #145 EngineContextWirer 已用 Mockito,
        // 本 BDD 同样套路,只给 EvaluationContextImpl 构造用,不参与 fact 流转
        return org.mockito.Mockito.mock(WorkingMemory.class);
    }
}
