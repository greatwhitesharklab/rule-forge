package com.ruleforge.model.scorecard.runtime;

import com.ruleforge.engine.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * V6.9.16 — {@link ScorecardImpl} logMsg 门控 (V6.9.9.1 模式) 行为契约 BDD。
 *
 * <p>锁 V6.9.16 收口 (ScorecardImpl.executeSum L33 + executeWeightSum L47
 * logMsg 加 \`if (this.debug)\` 门控) 的行为不变性:
 * <ul>
 *   <li><b>debug=true</b>: logMsg 触发, MessageItem 入 messageItems</li>
 *   <li><b>debug=false</b>: logMsg 不触发, messageItems 空 (空 list 不是 null sentinel, 是空)</li>
 * </ul>
 *
 * <p><b>Why V6.9.16 选这条</b>: v69_pipeline P0 #5, scorecard path 影响小
 * (scorecard rules 数量比 rete 少), V6.9.9.1 模式直接套。 配 V5.88 CriteriaActivity
 * + V6.9.9 VariableAssign/ExecuteMethod + V6.9.10 ECF + V6.9.11 TerminalActivity
 * 完成 scorecard 子树 logMsg 门控闭环。
 *
 * <p>本测试只覆盖 ScorecardImpl (公开 API, 直接调用); ScoreRule L60 + L86
 * 留给 V6.9.17 (需 fireRules + KnowledgeSessionFactory 重装配, 先抽 helper 再测)。
 */
@DisplayName("V6.9.16 — ScorecardImpl logMsg debug 门控 (V6.9.9.1 模式)")
class ScorecardImplLogMsgGateTest {

    private Context context;

    @BeforeEach
    void setUp() {
        // Context interface, mock 直接 (executeSum/executeWeightSum 只用 logMsg)
        context = mock(Context.class);
    }

    @Nested
    @DisplayName("executeSum logMsg 门控")
    class ExecuteSum {

        @Test
        @DisplayName("debug=true → logMsg 触发, 带 求和得分 message")
        void logsWhenDebugTrue() {
            List<RowItem> items = new ArrayList<>();
            RowItemImpl row = new RowItemImpl();
            row.setScore("10");
            items.add(row);
            ScorecardImpl card = new ScorecardImpl("test-card", items, true);

            card.executeSum(context);

            verify(context, times(1)).logMsg(anyString(), any());
            verify(context).logMsg(org.mockito.ArgumentMatchers.contains("求和得分"), any());
        }

        @Test
        @DisplayName("debug=false → logMsg 不触发")
        void doesNotLogWhenDebugFalse() {
            List<RowItem> items = new ArrayList<>();
            RowItemImpl row = new RowItemImpl();
            row.setScore("10");
            items.add(row);
            ScorecardImpl card = new ScorecardImpl("test-card", items, false);

            card.executeSum(context);

            verify(context, never()).logMsg(anyString(), any());
        }
    }

    @Nested
    @DisplayName("executeWeightSum logMsg 门控")
    class ExecuteWeightSum {

        @Test
        @DisplayName("debug=true → logMsg 触发, 带 加权求和得分 message")
        void logsWhenDebugTrue() {
            List<RowItem> items = new ArrayList<>();
            RowItemImpl row = new RowItemImpl();
            row.setScore("10");
            row.setWeight("2");
            items.add(row);
            ScorecardImpl card = new ScorecardImpl("test-card", items, true);

            card.executeWeightSum(context);

            verify(context, times(1)).logMsg(anyString(), any());
            verify(context).logMsg(org.mockito.ArgumentMatchers.contains("加权求和得分"), any());
        }

        @Test
        @DisplayName("debug=false → logMsg 不触发")
        void doesNotLogWhenDebugFalse() {
            List<RowItem> items = new ArrayList<>();
            RowItemImpl row = new RowItemImpl();
            row.setScore("10");
            row.setWeight("2");
            items.add(row);
            ScorecardImpl card = new ScorecardImpl("test-card", items, false);

            card.executeWeightSum(context);

            verify(context, never()).logMsg(anyString(), any());
        }
    }
}
