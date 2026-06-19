package com.ruleforge.runtime.rete;
import com.ruleforge.engine.Activity;
import com.ruleforge.engine.EvaluationContext;

import com.ruleforge.rete.test.EngineContextWirer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6.2 — {@link AbstractActivity#visitPaths} 契约 BDD。
 *
 * <p>锁 V6.2 修法(砍 dead-code else,Fernflower 反编译 artifact)的行为不变性:
 * <ul>
 *   <li>{@code paths == null} 或 {@code paths.isEmpty()} → 返 {@code null}</li>
 *   <li>非空 paths → 每个 path 的 to-activity 都被调 {@code enter(ctx, obj, newSubFactTracker)}</li>
 *   <li>每个 path 的 {@code setPassed(true)} 都被调到</li>
 *   <li>enter 返回的非 null results 被收集到返 list</li>
 *   <li>enter 返 null 的 path 不向 trackers 列表 add(trackers 仍可能为 null)</li>
 *   <li>无 enter 返回结果时 → 返 {@code null}(trackers 一直未初始化)</li>
 * </ul>
 *
 * <p><b>Why V6.2 选这条</b>: V5.92 resetStickyStateOnly (visitPaths 调用方) JFR
 * 抓 802 sample 后降到 49 sample (V5.92 doc 锁定)。 visitPaths 本身 JFR 0 sample
 * (被 JIT inline),所以本 PR 是 code quality 收口,非 perf 突破 — 跟 V5.93/V5.94/
 * V5.97/V5.98/V5.99/V6.1 同档。
 *
 * <p><b>死代码分析</b>: V6.2 修复前 visitPaths 第 33-43 行:
 * <pre>
 * int size = this.paths.size();           // line 33 — size 永远是 paths.size()
 * for (Path path : this.paths) {
 *     ...
 *     if (size > 0) {                      // line 39 — 永真 (line 31 保证了 !paths.isEmpty())
 *         results = activity.enter(context, obj, tracker.newSubFactTracker());
 *     } else {                             // line 41-43 — 死代码
 *         results = activity.enter(context, obj, tracker);
 *     }
 * }
 * </pre>
 * 第 31 行 {@code if (this.paths != null && !this.paths.isEmpty())} 已经保证
 * {@code size > 0}, 所以第 41-43 行的 {@code else} 不可达 — Fernflower 反编译
 * 留下的 if/else 模式 (在 decompiled 阶段 size 不是 final, 无法确定分支不可达)。
 *
 * <p>本方法 put 后 value 永远非 null 的契约同样适用 (跟 V5.97/V6.1 同档),所以
 * 行为 100% 等价。
 *
 * @see com.ruleforge.docs.notes.v622-abstractactivity-dead-code V6.2 完整 doc
 * @since 6.2
 */
@DisplayName("V6.2 — AbstractActivity.visitPaths 契约 (砍死代码 else)")
class AbstractActivityVisitPathsTest {

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    /**
     * 测试用 AbstractActivity 子类,只为 expose {@code visitPaths}。
     * {@code enter/joinNodeIsPassed/passAndNode/reset} 留空实现,因为测试不关注。
     */
    private static class TestActivity extends AbstractActivity {
        @Override
        public Collection<FactTracker> enter(EvaluationContext context, Object obj, FactTracker tracker) {
            return null; // 测试不调用 enter,只测 visitPaths
        }

        @Override
        public boolean joinNodeIsPassed() { return false; }

        @Override
        public void passAndNode() { }

        @Override
        public void reset() { }

        /** expose protected method to tests in same package */
        List<FactTracker> callVisitPaths(EvaluationContext ctx, Object obj, FactTracker t) {
            return visitPaths(ctx, obj, t);
        }
    }

    /**
     * 记录 enter 调用的 stub Activity,每次 enter 把 tracker 引用存进 captured,
     * 把构造时指定的 results 返给 caller。
     */
    private static class RecordingActivity implements Activity {
        final List<FactTracker> captured = new ArrayList<>();
        final Collection<FactTracker> resultsToReturn;
        final AtomicInteger callCount = new AtomicInteger();
        final List<Path> paths = new ArrayList<>();

        RecordingActivity(Collection<FactTracker> results) {
            this.resultsToReturn = results;
        }

        RecordingActivity() { this(null); }

        @Override
        public Collection<FactTracker> enter(EvaluationContext context, Object obj, FactTracker tracker) {
            callCount.incrementAndGet();
            captured.add(tracker);
            return resultsToReturn;
        }

        @Override
        public List<Path> getPaths() {
            return paths;
        }
    }

    @Nested
    @DisplayName("guard path: paths == null 或 empty → 返 null")
    class GuardReturnsNull {

        // Given: TestActivity 没 add 任何 path
        // When:  visitPaths(ctx, obj, tracker)
        // Then:  返 null (paths == null guard 触发)
        @Test
        @DisplayName("没 add 任何 path (paths == null) → 返 null")
        void nullPathsReturnsNull() {
            TestActivity activity = new TestActivity();
            FactTracker tracker = new FactTracker();
            assertThat(activity.callVisitPaths(null, null, tracker)).isNull();
        }

        // Given: TestActivity addPath 然后手动清空 paths
        // When:  visitPaths(ctx, obj, tracker)
        // Then:  返 null (paths.isEmpty() guard 触发)
        @Test
        @DisplayName("paths 内部为空 (size 0) → 返 null")
        void emptyPathsReturnsNull() {
            TestActivity activity = new TestActivity();
            // 触发 addPath 内部 paths 初始化,然后清空
            activity.addPath(new Path(new ObjectTypeActivity("X")));
            activity.getPaths().clear();
            FactTracker tracker = new FactTracker();
            assertThat(activity.callVisitPaths(null, null, tracker)).isNull();
        }
    }

    @Nested
    @DisplayName("happy path: 单 path 走 enter + newSubFactTracker + setPassed(true)")
    class SinglePathFlow {

        // Given: TestActivity 装 1 个 path,to = RecordingActivity 返 [recordedTracker]
        // When:  visitPaths(ctx, obj, tracker)
        // Then:  (1) 返 list 含 recordedTracker
        //        (2) RecordingActivity.captured 含一个 tracker (跟原 tracker 不同 — newSubFactTracker)
        //        (3) path.setPassed(true) 被调到
        //        (4) RecordingActivity.callCount == 1
        @Test
        @DisplayName("单 path → enter 用 newSubFactTracker,path.setPassed(true),返收集 results")
        void singlePathTriggersEnterWithNewSubTracker() {
            TestActivity activity = new TestActivity();
            // 预构造一个 newSubFactTracker, 让 recorder 直接 return 它 (而不是再 new 一个)
            FactTracker preTracker = new FactTracker();
            FactTracker recordedResult = preTracker.newSubFactTracker();
            RecordingActivity recorder = new RecordingActivity(java.util.Collections.singletonList(recordedResult));
            Path path = new Path(recorder);
            activity.addPath(path);

            FactTracker tracker = new FactTracker();
            List<FactTracker> result = activity.callVisitPaths(null, null, tracker);

            assertThat(recorder.callCount.get()).isEqualTo(1);
            assertThat(recorder.captured).hasSize(1);
            // enter 拿到的 tracker 永远 != 原 tracker (V6.2 死代码验证: size > 0 永真)
            assertThat(recorder.captured.get(0)).isNotSameAs(tracker);
            // 返 list 含 recorder 返的 [recordedResult]
            assertThat(result).containsExactly(recordedResult);
            assertThat(path.isPassed()).isTrue();
        }
    }

    @Nested
    @DisplayName("multi-path: 各自 enter,各自 setPassed,results 拼接")
    class MultiPathFlow {

        // Given: TestActivity 装 3 个 path,3 个 RecordingActivity 各自返 1 个 tracker
        // When:  visitPaths(ctx, obj, tracker)
        // Then:  (1) 3 个 recorder callCount 全 1
        //        (2) 3 个 path 全 isPassed() == true
        //        (3) 返 list 含 3 个 recordedResult (顺序 = addPath 顺序)
        //        (4) 3 个 recorder.captured 各自含 1 个 tracker (跟原 tracker 不同)
        @Test
        @DisplayName("3 paths → 各自 enter 一次,各自 setPassed(true),results 顺序拼接")
        void threePathsConcatenateResults() {
            TestActivity activity = new TestActivity();
            FactTracker pre = new FactTracker();
            FactTracker result1 = pre.newSubFactTracker();
            FactTracker result2 = pre.newSubFactTracker();
            FactTracker result3 = pre.newSubFactTracker();
            RecordingActivity r1 = new RecordingActivity(java.util.Collections.singletonList(result1));
            RecordingActivity r2 = new RecordingActivity(java.util.Collections.singletonList(result2));
            RecordingActivity r3 = new RecordingActivity(java.util.Collections.singletonList(result3));
            Path p1 = new Path(r1);
            Path p2 = new Path(r2);
            Path p3 = new Path(r3);
            activity.addPath(p1);
            activity.addPath(p2);
            activity.addPath(p3);

            FactTracker tracker = new FactTracker();
            List<FactTracker> result = activity.callVisitPaths(null, null, tracker);

            // 每个 recorder 收 1 个 tracker (跟原 tracker 不同)
            assertThat(r1.callCount.get()).isEqualTo(1);
            assertThat(r2.callCount.get()).isEqualTo(1);
            assertThat(r3.callCount.get()).isEqualTo(1);
            assertThat(r1.captured).hasSize(1);
            assertThat(r2.captured).hasSize(1);
            assertThat(r3.captured).hasSize(1);
            assertThat(r1.captured.get(0)).isNotSameAs(tracker);
            assertThat(r2.captured.get(0)).isNotSameAs(tracker);
            assertThat(r3.captured.get(0)).isNotSameAs(tracker);
            // 每个 path 都被 setPassed(true)
            assertThat(p1.isPassed()).isTrue();
            assertThat(p2.isPassed()).isTrue();
            assertThat(p3.isPassed()).isTrue();
            // 返 list 含 3 个 recordedResult (顺序 = addPath 顺序)
            assertThat(result).containsExactly(result1, result2, result3);
        }
    }

    @Nested
    @DisplayName("no-results path: enter 返 null → trackers 列表保持 null")
    class NoResultsPath {

        // Given: TestActivity 装 1 个 path,to = RecordingActivity 返 null
        // When:  visitPaths(ctx, obj, tracker)
        // Then:  返 null (trackers 一直没初始化)
        //        path.setPassed(true) 仍被调
        @Test
        @DisplayName("path enter 返 null → 返 null,但 path.setPassed(true) 仍被调")
        void enterReturnsNullPropagatesAsOverallNull() {
            TestActivity activity = new TestActivity();
            RecordingActivity recorder = new RecordingActivity(); // returns null by default
            Path path = new Path(recorder);
            activity.addPath(path);

            FactTracker tracker = new FactTracker();
            List<FactTracker> result = activity.callVisitPaths(null, null, tracker);

            assertThat(recorder.callCount.get()).isEqualTo(1);
            assertThat(result).isNull();
            assertThat(path.isPassed()).isTrue();
        }

        // Given: TestActivity 装 3 个 path, 第 1 个 enter 返 null, 第 2、3 个各返 1 个
        // When:  visitPaths(ctx, obj, tracker)
        // Then:  返 list 含 2 个 result (跳过 null result)
        @Test
        @DisplayName("混合 (null + 2 results) → 返 list 含 2 个 (跳过 null)")
        void mixedNullAndResultsConcatenatesNonNull() {
            TestActivity activity = new TestActivity();
            FactTracker pre = new FactTracker();
            FactTracker result2 = pre.newSubFactTracker();
            FactTracker result3 = pre.newSubFactTracker();
            RecordingActivity r1 = new RecordingActivity(); // null
            RecordingActivity r2 = new RecordingActivity(java.util.Collections.singletonList(result2));
            RecordingActivity r3 = new RecordingActivity(java.util.Collections.singletonList(result3));
            Path p1 = new Path(r1);
            Path p2 = new Path(r2);
            Path p3 = new Path(r3);
            activity.addPath(p1);
            activity.addPath(p2);
            activity.addPath(p3);

            FactTracker tracker = new FactTracker();
            List<FactTracker> result = activity.callVisitPaths(null, null, tracker);

            // 3 个 enter 全调 (1 null + 2 results)
            assertThat(r1.callCount.get()).isEqualTo(1);
            assertThat(r2.callCount.get()).isEqualTo(1);
            assertThat(r3.callCount.get()).isEqualTo(1);
            // 跳过 r1 的 null result
            assertThat(result).containsExactly(result2, result3);
            // 3 个 path 全 setPassed(true)
            assertThat(p1.isPassed()).isTrue();
            assertThat(p2.isPassed()).isTrue();
            assertThat(p3.isPassed()).isTrue();
        }
    }

    @Nested
    @DisplayName("V6.2 死代码验证: enter 总是用 newSubFactTracker, 永不传原 tracker")
    class DeadCodeElseNeverReached {

        // Given: TestActivity 装 1 个 path, to = RecordingActivity
        // When:  visitPaths(ctx, obj, tracker)
        // Then:  传给 enter 的 tracker != 原 tracker
        //        (这证明 size > 0 永远 true,else 不可达,V6.2 修复后行为不变)
        @Test
        @DisplayName("enter 拿到的 tracker 永远 != 原 tracker (V6.2 砍 else 不影响行为)")
        void enterTrackerIsAlwaysNewSubTracker() {
            TestActivity activity = new TestActivity();
            RecordingActivity recorder = new RecordingActivity();
            Path path = new Path(recorder);
            activity.addPath(path);

            FactTracker tracker = new FactTracker();
            activity.callVisitPaths(null, null, tracker);

            // 传给 enter 的 tracker 是 newSubFactTracker() (跟原 tracker 不同)
            assertThat(recorder.captured.get(0)).isNotSameAs(tracker);
        }
    }
}
