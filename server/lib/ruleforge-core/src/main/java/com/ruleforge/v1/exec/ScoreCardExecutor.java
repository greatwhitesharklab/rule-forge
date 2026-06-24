package com.ruleforge.v1.exec;

import com.ruleforge.v1.ast.Band;
import com.ruleforge.v1.ast.Card;
import com.ruleforge.v1.ast.ScoreAggregation;
import com.ruleforge.v1.ast.ScoreCardNode;
import com.ruleforge.v1.ast.Schema;
import com.ruleforge.v1.cel.CelEngine;

import java.util.List;
import java.util.Map;

/**
 * V1 ScoreCard 执行器(W2-3)。<b>不进 RETE</b> — 分段求值 + 聚合不是 RETE 强项,
 * 直接 CelEngine.evalBoolean 求 band 命中。
 *
 * <p>执行语义:
 * <ol>
 *   <li>每个 card:按 band 顺序 CelEngine.evalBoolean,首个命中 band 的 score 取出(找不到命中 band → 0)</li>
 *   <li>聚合所有 card 的分到 output 字段:
 *     <ul>
 *       <li>SUM — 求和</li>
 *       <li>AVG — 平均(card 数)</li>
 *       <li>MIN/MAX — 取最小/最大</li>
 *       <li>WEIGHTED_SUM — sum(score * weight),weight 缺省 1</li>
 *     </ul>
 *   </li>
 *   <li>结果写入 fact[output]</li>
 * </ol>
 *
 * <p>对齐 PMML Scorecard 特征段(每段一个 band 分数),方便 PMML Adapter。
 */
public final class ScoreCardExecutor {

    private ScoreCardExecutor() {
    }

    public static void execute(ScoreCardNode node, Schema schema, Map<String, Object> fact) {
        List<Card> cards = node.getCards();
        if (cards == null || cards.isEmpty()) {
            fact.put(node.getOutput(), 0.0);
            return;
        }
        ScoreAggregation agg = node.getAggregation() == null ? ScoreAggregation.SUM : node.getAggregation();
        double sum = 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        int count = 0;
        double weightedSum = 0;

        for (Card card : cards) {
            double score = scoreOfCard(card, schema, fact);
            double weight = card.getWeight() == null ? 1.0 : card.getWeight();
            sum += score;
            min = Math.min(min, score);
            max = Math.max(max, score);
            count++;
            weightedSum += score * weight;
        }

        double result;
        switch (agg) {
            case AVG: result = count == 0 ? 0 : sum / count; break;
            case MIN: result = count == 0 ? 0 : min; break;
            case MAX: result = count == 0 ? 0 : max; break;
            case WEIGHTED_SUM: result = weightedSum; break;
            case SUM:
            default: result = sum; break;
        }
        fact.put(node.getOutput(), result);
    }

    /** card 的分:首个命中 band 的 score(无命中 → 0)。 */
    private static double scoreOfCard(Card card, Schema schema, Map<String, Object> fact) {
        if (card.getBands() == null) return 0;
        for (Band band : card.getBands()) {
            if (CelEngine.evalBoolean(band.getCondition(), fact, schema)) {
                return band.getScore();
            }
        }
        return 0; // 无命中 band → 0 分
    }
}
