package com.ruleforge.model.rule.lhs;

import com.ruleforge.debug.MessageItem;
import com.ruleforge.model.rule.Op;
import com.ruleforge.model.rule.SimpleValue;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.rete.test.EngineContextWirer;
import com.ruleforge.runtime.WorkingMemory;
import com.ruleforge.runtime.rete.EvaluationContextImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * V5.95 — {@code Criteria.evaluate} 4 个 {@code addTipMsg} 调用 + {@code cleanTipMsg}
 * 在 {@code Rule.debug = false} 时门控跳过的契约 BDD。
 *
 * <p>post-V5.94 JFR 30s HotPathBenchTest 抓 {@code StringConcatHelper.prepend}
 * 593 sample(其中 production 路径 ~85%,test fixture ~15%)。audit 发现
 * {@code Criteria.evaluate} 4 个 addTipMsg 调用(line 34, 38, 115, 132)
 * 全部无条件执行:
 * <pre>
 * context.addTipMsg("计算条件：" + this.getId());
 * context.addTipMsg("左值：" + leftId);
 * context.addTipMsg("右值：" + valueId);
 * context.addTipMsg("执行比较：" + this.op.toString());
 * </pre>
 * 即使 {@code Rule.debug = false} 仍做 string concat(4 StringBuilder +
 * Long.getChars / String.getBytes)+ addTipMsg 内部 2 StringBuilder.append,
 * 末尾 {@code cleanTipMsg()} 立即把 builder 抹掉 — 纯浪费。
 *
 * <p>V5.95 加 {@code Criteria.debug} 字段,evaluate 走 {@code if (this.debug)}
 * 门控:
 * <ul>
 *   <li>{@code debug=true}:4 个 addTipMsg + cleanTipMsg 全部执行(保留 V5.94 行为)</li>
 *   <li>{@code debug=false}:4 个 addTipMsg 全部跳过(省 string concat + StringBuilder.append),
 *       cleanTipMsg 仍调但 no-op(builder 已空)</li>
 * </ul>
 *
 * <p>跟 V5.88 {@code CriteriaActivity.logMessage} 早返同模式 — 同一个 debug flag
 * 门控两处,只是位置不同(logMessage 在 CriteriaActivity 内,tipMsg 在 Criteria
 * 内部)。
 *
 * <p>行为对比:
 * <ul>
 *   <li>{@code getTipMsg()} 调用方只在 exception path 出现(ObjectTypeActivity
 *       line 24 + ActivationImpl line 101),debug=false 时返 null 不影响 error 抛出</li>
 *   <li>{@code logMessage}(V5.88 早返)跟 {@code addTipMsg}(V5.95 门控)是
 *       互补关系:logMessage 写 {@code executeMessageItems},addTipMsg 写
 *       {@code tipMsgBuilder},都是 debug-only 副产物</li>
 * </ul>
 *
 * @see com.ruleforge.docs.notes.v595-criteria-addtipmsg-debug-gate V5.95 完整 doc
 * @since 5.95
 */
@DisplayName("V5.95 — Criteria.evaluate 4 addTipMsg + cleanTipMsg debug 门控契约")
class CriteriaDebugTipMsgTest {

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    private EvaluationContextImpl ctxSpy;
    private WorkingMemory mockWM;

    @BeforeEach
    void setUp() {
        mockWM = mock(WorkingMemory.class);
        List<MessageItem> debugMsgs = Collections.emptyList();
        // spy on real EvaluationContextImpl, verify addTipMsg / cleanTipMsg call counts
        ctxSpy = spy(new EvaluationContextImpl(mockWM, new HashMap<>(), debugMsgs));
    }

    @Nested
    @DisplayName("debug=false 路径 (production 默认)")
    class DebugFalse {

        // Given Criteria.debug = false
        // When evaluate
        // Then 0 个 addTipMsg 调用 + cleanTipMsg 调 1 次(noop,保留调用契约)
        @Test
        @DisplayName("debug=false → 0 addTipMsg 调用 (省 4 StringBuilder + 4 string concat)")
        void debugFalseSkipsAll4AddTipMsg() {
            Criteria c = buildCriteria("X", "name", "alice");
            c.setDebug(false);

            c.evaluate(ctxSpy, new Object(), new ArrayList<>());

            // 锁契约:4 个 addTipMsg 全部跳过 — 用 anyString 验证 0 调用
            verify(ctxSpy, times(0)).addTipMsg(org.mockito.ArgumentMatchers.anyString());
        }

        // Given Criteria.debug = false
        // When evaluate (无论 fact match 与否)
        // Then cleanTipMsg 调 1 次(无 op,但保留调用 — V5.95 修法为保留 cleanTipMsg 调用)
        // 实际 V5.95 实现:cleanTipMsg 在 if (this.debug) { } 块外 — 总是调 1 次(builder
        // 已是空,cleanTipMsg 抹空 length=0,无副作用)
        @Test
        @DisplayName("debug=false → cleanTipMsg 仍调 1 次(no-op,保留调用契约)")
        void debugFalseCleanTipMsgStillCalled() {
            Criteria c = buildCriteria("X", "name", "alice");
            c.setDebug(false);

            c.evaluate(ctxSpy, new Object(), new ArrayList<>());

            // V5.95 修法选择:cleanTipMsg 始终调 1 次(builder 已空,无副作用)。
            // 这样 activation execute 路径的 cleanTipMsg 调用契约不变。
            verify(ctxSpy, times(1)).cleanTipMsg();
        }

        // Given Criteria.debug = false + 1000 次 evaluate
        // When 重复调用
        // Then addTipMsg 总 0 次
        @Test
        @DisplayName("debug=false → 1000 次 evaluate 后 addTipMsg 仍 0 次 (锁 V5.95 性能契约)")
        void debugFalseThousandEvaluatesNoTipMsg() {
            Criteria c = buildCriteria("X", "name", "alice");
            c.setDebug(false);

            for (int i = 0; i < 1000; i++) {
                c.evaluate(ctxSpy, new Object(), new ArrayList<>());
            }

            // 1000 次 evaluate, 0 addTipMsg 调用 = V5.95 性能契约
            verify(ctxSpy, times(0)).addTipMsg(org.mockito.ArgumentMatchers.anyString());
        }
    }

    @Nested
    @DisplayName("debug=true 路径 (debug 模式)")
    class DebugTrue {

        // Given Criteria.debug = true
        // When evaluate
        // Then 4 个 addTipMsg 调用 + 1 个 cleanTipMsg 调用
        @Test
        @DisplayName("debug=true → 4 addTipMsg 全部执行(锁 V5.95 保留 V5.94 行为)")
        void debugTrueCallsAll4AddTipMsg() {
            Criteria c = buildCriteria("X", "name", "alice");
            c.setDebug(true);

            c.evaluate(ctxSpy, new Object(), new ArrayList<>());

            // 锁 V5.95 保留的 V5.94 行为:4 个 addTipMsg + 1 cleanTipMsg 全部调
            // leftId/valueId/getId() 实际值含 [变量]/[字符] 前缀,锁总调用次数
            verify(ctxSpy, times(4)).addTipMsg(org.mockito.ArgumentMatchers.anyString());
            verify(ctxSpy, times(1)).cleanTipMsg();
        }
    }

    @Nested
    @DisplayName("默认 debug 状态")
    class DefaultDebug {

        // Given Criteria 不显式 setDebug
        // When isDebug()
        // Then false (production-safe 默认)
        @Test
        @DisplayName("new Criteria() 默认 debug=false (production 路径安全)")
        void newCriteriaDefaultDebugFalse() {
            Criteria c = new Criteria();
            // 默认 false — production-safe,不调 setDebug 就是 debug=false 路径
            assertThat(c.isDebug()).isFalse();
        }
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
}
