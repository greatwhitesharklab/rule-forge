package com.ruleforge.model.rule.lhs;

import com.ruleforge.engine.EvaluationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * V5.51.3 — FromLeftPart 真实 evaluate 3 分支 BDD。
 *
 * <p>覆盖:
 * <ul>
 *   <li>stream:DRL {@code from $stream} — 直接从 obj 拿 Collection</li>
 *   <li>collect:复用 AbstractLeftPart.computeValue,property sum</li>
 *   <li>accumulate:5 种 StatisticType(count/sum/avg/min/max)</li>
 *   <li>boundary:accumulate 没设 statisticType 抛 RuleException</li>
 * </ul>
 *
 * <p>multiCondition 全 null(测试只覆盖 binding 集合遍历,不覆盖 inner filter),
 * multiCondition 走全匹配路径。
 */
@DisplayName("FromLeftPart - evaluate 3 分支 + accumulate 5 统计")
class FromLeftPartEvaluateTest {

    /** 测试用 fact POJO:有个 int score 字段 */
    public static class Fact {
        private int score;
        public Fact(int score) { this.score = score; }
        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }
    }

    /** 测试用 binding POJO:持有 Collection<Fact> 字段 */
    public static class Binding {
        private Collection<Fact> facts;
        public Binding(Collection<Fact> facts) { this.facts = facts; }
        public Collection<Fact> getFacts() { return facts; }
        public void setFacts(Collection<Fact> facts) { this.facts = facts; }
    }

    private EvaluationContext ctx;
    private List<Object> allMatched;

    @BeforeEach
    void setUp() {
        // multiCondition=null 时,computeValue 不会调 ctx,ctx 不会被 deref
        // (除非要测 multiCondition 路径,本测试类不覆盖)
        ctx = mock(EvaluationContext.class);
        allMatched = new ArrayList<>();
    }

    // ============================================================
    // === from stream 分支 ===
    // ============================================================

    @Nested
    @DisplayName("Given from $stream,When evaluate,Then 直接返回 binding Collection")
    class StreamBranch {

        @Test
        @DisplayName("from $stream → 返回 obj.variableName 的 Collection")
        void streamReturnsBindingCollection() {
            // Given
            List<Fact> facts = Arrays.asList(new Fact(1), new Fact(2));
            Binding binding = new Binding(facts);
            FromLeftPart part = new FromLeftPart();
            part.setVariableName("facts");
            part.setFromSource("stream");
            part.setVariableCategory("Test");
            part.setVariableLabel("facts");

            // When
            Object result = part.evaluate(ctx, binding, allMatched);

            // Then
            assertThat(result).isSameAs(facts);
        }

        @Test
        @DisplayName("from stream 且 binding 为 null → 抛 RuleException")
        void streamNullBindingThrows() {
            FromLeftPart part = new FromLeftPart();
            part.setVariableName("missing");
            part.setFromSource("stream");
            Binding binding = new Binding(null);

            assertThatThrownBy(() -> part.evaluate(ctx, binding, allMatched))
                .isInstanceOf(com.ruleforge.exception.RuleException.class)
                .hasMessageContaining("missing");
        }
    }

    // ============================================================
    // === from collect 分支 ===
    // ============================================================

    @Nested
    @DisplayName("Given from collect(...),When evaluate,Then 走 computeValue 路径")
    class CollectBranch {

        @Test
        @DisplayName("from collect + property=score → 返回 facts 集合(sum 语义)")
        void collectReturnsFactList() {
            // Given — multiCondition=null 时,computeValue 全匹配
            List<Fact> facts = Arrays.asList(new Fact(10), new Fact(20));
            Binding binding = new Binding(facts);
            FromLeftPart part = new FromLeftPart();
            part.setVariableName("facts");
            part.setFromSource("collect");
            part.setProperty("score");
            part.setVariableCategory("Test");
            part.setVariableLabel("facts");

            // When
            Object result = part.evaluate(ctx, binding, allMatched);

            // Then — sum 语义:BigDecimal(30)
            assertThat(result).isInstanceOf(BigDecimal.class);
            assertThat((BigDecimal) result).isEqualByComparingTo(new BigDecimal(30));
        }

        @Test
        @DisplayName("from collect + property=null → 返回 match count(Integer)")
        void collectNoPropertyReturnsCount() {
            List<Fact> facts = Arrays.asList(new Fact(10), new Fact(20), new Fact(30));
            Binding binding = new Binding(facts);
            FromLeftPart part = new FromLeftPart();
            part.setVariableName("facts");
            part.setFromSource("collect");
            part.setVariableCategory("Test");
            part.setVariableLabel("facts");

            Object result = part.evaluate(ctx, binding, allMatched);

            assertThat(result).isEqualTo(3);
        }
    }

    // ============================================================
    // === from accumulate 分支 — 5 种 StatisticType ===
    // ============================================================

    @Nested
    @DisplayName("Given from accumulate(...),When evaluate,Then 走 StatisticType 分支")
    class AccumulateBranch {

        private Binding newBinding(int... scores) {
            List<Fact> facts = new ArrayList<>();
            for (int s : scores) facts.add(new Fact(s));
            return new Binding(facts);
        }

        private FromLeftPart newAccumulatePart(StatisticType st) {
            FromLeftPart part = new FromLeftPart();
            part.setVariableName("facts");
            part.setFromSource("accumulate");
            part.setProperty("score");
            part.setStatisticType(st);
            part.setVariableCategory("Test");
            part.setVariableLabel("facts");
            return part;
        }

        @Test
        @DisplayName("count → match 数(Integer)")
        void count() {
            Object result = newAccumulatePart(StatisticType.count)
                .evaluate(ctx, newBinding(10, 20, 30), allMatched);
            assertThat(result).isEqualTo(3);
        }

        @Test
        @DisplayName("sum → BigDecimal sum")
        void sum() {
            Object result = newAccumulatePart(StatisticType.sum)
                .evaluate(ctx, newBinding(10, 20, 30), allMatched);
            assertThat((BigDecimal) result).isEqualByComparingTo(new BigDecimal(60));
        }

        @Test
        @DisplayName("avg → BigDecimal 平均")
        void avg() {
            Object result = newAccumulatePart(StatisticType.avg)
                .evaluate(ctx, newBinding(10, 20, 30), allMatched);
            assertThat((BigDecimal) result).isEqualByComparingTo(new BigDecimal("20.0000"));
        }

        @Test
        @DisplayName("max → BigDecimal 最大")
        void max() {
            Object result = newAccumulatePart(StatisticType.max)
                .evaluate(ctx, newBinding(5, 30, 12), allMatched);
            assertThat((BigDecimal) result).isEqualByComparingTo(new BigDecimal(30));
        }

        @Test
        @DisplayName("min → BigDecimal 最小")
        void min() {
            Object result = newAccumulatePart(StatisticType.min)
                .evaluate(ctx, newBinding(5, 30, 12), allMatched);
            assertThat((BigDecimal) result).isEqualByComparingTo(new BigDecimal(5));
        }

        @Test
        @DisplayName("StatisticType=percent/amount/none → 抛 RuleException(accumulate 不支持)")
        void nonAccumulateStatisticTypeThrows() {
            // accumulate 分支只支持 count/sum/avg/min/max — percent/amount/none 走 default 抛错
            assertThatThrownBy(() -> newAccumulatePart(StatisticType.percent)
                    .evaluate(ctx, newBinding(1, 2), allMatched))
                .isInstanceOf(com.ruleforge.exception.RuleException.class)
                .hasMessageContaining("不支持");
        }

        @Test
        @DisplayName("accumulate + statisticType=null → 抛 RuleException")
        void accumulateWithoutStatisticTypeThrows() {
            FromLeftPart part = new FromLeftPart();
            part.setVariableName("facts");
            part.setFromSource("accumulate");
            part.setProperty("score");
            // statisticType 未设

            assertThatThrownBy(() -> part.evaluate(ctx, newBinding(1, 2), allMatched))
                .isInstanceOf(com.ruleforge.exception.RuleException.class)
                .hasMessageContaining("StatisticType");
        }

        @Test
        @DisplayName("avg + match=0 → BigDecimal.ZERO(避免 divide-by-zero)")
        void avgEmptyBinding() {
            Object result = newAccumulatePart(StatisticType.avg)
                .evaluate(ctx, newBinding(), allMatched);
            assertThat((BigDecimal) result).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ============================================================
    // === getId 反映 fromSource + statisticType ===
    // ============================================================

    @Nested
    @DisplayName("getId 反映 fromSource + accumulate statisticType")
    class GetId {

        @Test
        @DisplayName("stream → from(Cat.Label,stream)")
        void streamId() {
            FromLeftPart part = new FromLeftPart();
            part.setVariableCategory("Applicant");
            part.setVariableLabel("a");
            part.setFromSource("stream");
            assertThat(part.getId()).isEqualTo("from(Applicant.a,stream)");
        }

        @Test
        @DisplayName("accumulate + count → from(Cat.Label,accumulate).count")
        void accumulateCountId() {
            FromLeftPart part = new FromLeftPart();
            part.setVariableCategory("Applicant");
            part.setVariableLabel("a");
            part.setFromSource("accumulate");
            part.setStatisticType(StatisticType.count);
            assertThat(part.getId()).isEqualTo("from(Applicant.a,accumulate).count");
        }
    }
}
