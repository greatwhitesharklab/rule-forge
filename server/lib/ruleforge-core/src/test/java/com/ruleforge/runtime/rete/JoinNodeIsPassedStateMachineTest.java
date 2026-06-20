package com.ruleforge.runtime.rete;

import com.ruleforge.engine.Path;
import com.ruleforge.rete.test.EngineContextWirer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * V6.9.2 — {@code joinNodeIsPassed} state machine 行为契约 BDD。
 *
 * <p>锁 V6.9.2 收口 (4 处 {@code if/return/fall-through} Fernflower 反编译 state machine
 * → early return + 单层 if) 的行为不变性:
 * <ul>
 *   <li>{@link CriteriaActivity#joinNodeIsPassed()} — paths==null/size≠1 返 false,
 *       size==1 递归查 child join</li>
 *   <li>{@link AndActivity#joinNodeIsPassed()} — passed=true → true; passed=false +
 *       paths.size==1 → 递归; 否则 false</li>
 *   <li>{@link OrActivity#joinNodeIsPassed()} — 跟 AndActivity 同模式</li>
 *   <li>{@link ObjectTypeActivity#joinNodeIsPassed()} — 永返 false (terminal for join)</li>
 * </ul>
 *
 * <p><b>Why V6.9.2 选这条</b>: 跟 V6.2 AbstractActivity.visitPaths 死代码 + V6.3
 * KnowledgeBase 3-level do-while + V6.4 LeftParser buildCommonFunctionLeftPart 同档
 * Fernflower 反编译 state machine 收口。JFR top signal CriteriaActivity.enter L30
 * 调 {@code this.joinNodeIsPassed()} 是 per-fact hot path (V5.100.9 报告),简化
 * branchy 状态机 → early return 既减 bytecode 又让 JIT inlining 更稳。 V5.96 立
 * 的"skip 反编译 state machine"原则延续。
 */
@DisplayName("V6.9.2 — joinNodeIsPassed state machine 行为契约")
class JoinNodeIsPassedStateMachineTest {

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    /**
     * 用 AbstractActivity 反射访问 paths 字段 (V6.2 反射路径迁移模式 — package-private
     * 字段 paths 在 AbstractActivity 中定义,子类可访问,但外部测试用反射)。
     */
    private static void setPaths(AbstractActivity activity, List<Path> paths) throws Exception {
        java.lang.reflect.Field f = AbstractActivity.class.getDeclaredField("paths");
        f.setAccessible(true);
        f.set(activity, paths);
    }

    private static Path mockPathToActivity(AbstractActivity toActivity) {
        Path path = mock(Path.class);
        when(path.getTo()).thenReturn(toActivity);
        return path;
    }

    @Nested
    @DisplayName("CriteriaActivity.joinNodeIsPassed")
    class CriteriaActivityJoin {

        // Given: CriteriaActivity, paths==null
        // When:  joinNodeIsPassed()
        // Then:  return false (L34 fall through)
        @Test
        @DisplayName("paths==null 返 false")
        void pathsNullReturnsFalse() throws Exception {
            CriteriaActivity ca = new CriteriaActivity(new com.ruleforge.model.rule.lhs.Criteria(), false);
            setPaths(ca, null);
            assertThat(ca.joinNodeIsPassed()).isFalse();
        }

        // Given: CriteriaActivity, paths.size()==2
        // When:  joinNodeIsPassed()
        // Then:  return false (L36 size > 1 → false)
        @Test
        @DisplayName("paths.size() > 1 返 false")
        void pathsMultipleReturnsFalse() throws Exception {
            CriteriaActivity ca = new CriteriaActivity(new com.ruleforge.model.rule.lhs.Criteria(), false);
            List<Path> paths = new ArrayList<>();
            paths.add(mockPathToActivity(new TerminalActivity(null)));
            paths.add(mockPathToActivity(new TerminalActivity(null)));
            setPaths(ca, paths);
            assertThat(ca.joinNodeIsPassed()).isFalse();
        }

        // Given: CriteriaActivity, paths.size()==0
        // When:  joinNodeIsPassed()
        // Then:  return false (L34 fall through)
        @Test
        @DisplayName("paths.size() == 0 返 false")
        void pathsEmptyReturnsFalse() throws Exception {
            CriteriaActivity ca = new CriteriaActivity(new com.ruleforge.model.rule.lhs.Criteria(), false);
            setPaths(ca, new ArrayList<>());
            assertThat(ca.joinNodeIsPassed()).isFalse();
        }

        // Given: CriteriaActivity, paths.size()==1 → TerminalActivity (joinNodeIsPassed=false)
        // When:  joinNodeIsPassed()
        // Then:  递归到 TerminalActivity → return false
        @Test
        @DisplayName("paths.size() == 1 → 递归 child joinNodeIsPassed")
        void pathsSingleDelegatesToChild() throws Exception {
            CriteriaActivity ca = new CriteriaActivity(new com.ruleforge.model.rule.lhs.Criteria(), false);
            TerminalActivity term = new TerminalActivity(null);
            List<Path> paths = new ArrayList<>();
            paths.add(mockPathToActivity(term));
            setPaths(ca, paths);
            // TerminalActivity.joinNodeIsPassed() = false
            assertThat(ca.joinNodeIsPassed()).isFalse();
        }
    }

    @Nested
    @DisplayName("AndActivity.joinNodeIsPassed")
    class AndActivityJoin {

        // Given: AndActivity passed=false, paths==null
        // When:  joinNodeIsPassed()
        // Then:  return false (L75 !passed → paths==null → fall through → return this.passed = false)
        @Test
        @DisplayName("passed=false + paths==null → 返 false")
        void passedFalsePathsNullReturnsFalse() throws Exception {
            AndActivity and = new AndActivity();
            setPaths(and, null);
            assertThat(and.joinNodeIsPassed()).isFalse();
        }

        // Given: AndActivity passed=false, paths.size()==2
        // When:  joinNodeIsPassed()
        // Then:  return false (L77 size != 1 → fall through → return this.passed = false)
        @Test
        @DisplayName("passed=false + paths.size()>1 → 返 false")
        void passedFalsePathsMultipleReturnsFalse() throws Exception {
            AndActivity and = new AndActivity();
            List<Path> paths = new ArrayList<>();
            paths.add(mockPathToActivity(new TerminalActivity(null)));
            paths.add(mockPathToActivity(new TerminalActivity(null)));
            setPaths(and, paths);
            assertThat(and.joinNodeIsPassed()).isFalse();
        }

        // Given: AndActivity passed=false, paths.size()==1 → TerminalActivity
        // When:  joinNodeIsPassed()
        // Then:  递归 child → TerminalActivity.joinNodeIsPassed = false
        @Test
        @DisplayName("passed=false + paths.size()==1 → 递归 child")
        void passedFalsePathsSingleDelegatesToChild() throws Exception {
            AndActivity and = new AndActivity();
            TerminalActivity term = new TerminalActivity(null);
            List<Path> paths = new ArrayList<>();
            paths.add(mockPathToActivity(term));
            setPaths(and, paths);
            assertThat(and.joinNodeIsPassed()).isFalse();
        }

        // Given: AndActivity passed=true (任一 path passed)
        // When:  joinNodeIsPassed()
        // Then:  return true (L75 !passed=false → fall through → return this.passed = true)
        @Test
        @DisplayName("passed=true → 立即返 true (跳过 paths 检查)")
        void passedTrueReturnsTrueImmediately() throws Exception {
            AndActivity and = new AndActivity();
            // 模拟 passed=true: 调 passAndNode() 会 set passed = true (L70)
            and.passAndNode();
            // 即便 paths == null,已 return true
            assertThat(and.joinNodeIsPassed()).isTrue();
        }
    }

    @Nested
    @DisplayName("OrActivity.joinNodeIsPassed")
    class OrActivityJoin {

        // Given: OrActivity passed=false, paths==null
        // When:  joinNodeIsPassed()
        // Then:  return false (跟 AndActivity 同模式)
        @Test
        @DisplayName("passed=false + paths==null → 返 false")
        void passedFalsePathsNullReturnsFalse() throws Exception {
            OrActivity or = new OrActivity();
            setPaths(or, null);
            assertThat(or.joinNodeIsPassed()).isFalse();
        }

        // Given: OrActivity passed=false, paths.size()==1 → TerminalActivity
        // When:  joinNodeIsPassed()
        // Then:  递归 child
        @Test
        @DisplayName("passed=false + paths.size()==1 → 递归 child")
        void passedFalsePathsSingleDelegatesToChild() throws Exception {
            OrActivity or = new OrActivity();
            TerminalActivity term = new TerminalActivity(null);
            List<Path> paths = new ArrayList<>();
            paths.add(mockPathToActivity(term));
            setPaths(or, paths);
            assertThat(or.joinNodeIsPassed()).isFalse();
        }
    }

    @Nested
    @DisplayName("ObjectTypeActivity.joinNodeIsPassed — terminal for join")
    class ObjectTypeActivityJoin {

        // Given: ObjectTypeActivity
        // When:  joinNodeIsPassed()
        // Then:  永返 false (跟 V6.2 同模式 — terminal 不参与 join)
        @Test
        @DisplayName("ObjectTypeActivity.joinNodeIsPassed 永返 false")
        void objectTypeAlwaysReturnsFalse() {
            ObjectTypeActivity ota = new ObjectTypeActivity("com.example.Type");
            assertThat(ota.joinNodeIsPassed()).isFalse();
        }
    }
}