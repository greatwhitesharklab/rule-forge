package com.ruleforge.model.rule.lhs;

import com.ruleforge.Utils;
import com.ruleforge.exception.RuleException;
import com.ruleforge.engine.EvaluationContext;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * V5.50.1 / V5.51.3 — DRL <code>from</code> 子句的 LHS LeftPart。
 *
 * <p>DRL 形式:
 * <pre>
 *   $a : Applicant(...) from $stream
 *   $a : Applicant(...) from collect(...)
 *   $a : Applicant(...) from accumulate(...)
 * </pre>
 *
 * <p>跟 {@link CollectLeftPart} 形态对齐:本类只产 DTO shape(extends
 * {@link AbstractLeftPart}),<code>evaluate</code> 走 3 种分支:
 * <ul>
 *   <li><b>stream</b>(fromSource="stream",{@code statisticType=null}):
 *       直接从 {@code obj.variableName} 拿 Collection 返回 — DRL
 *       {@code $a : Applicant(...) from $stream} 标准形态</li>
 *   <li><b>collect</b>(fromSource="collect"):复用 {@link AbstractLeftPart#computeValue}
 *       拿 match list,按 {@code property} 算 sum/avg/max/min,跟 CollectLeftPart 同语义</li>
 *   <li><b>accumulate</b>(fromSource="accumulate",{@code statisticType} 非空):
 *       按 {@link StatisticType#count}/{@link StatisticType#sum}/
 *       {@link StatisticType#avg}/{@link StatisticType#min}/{@link StatisticType#max}
 *       5 种内建统计走求值</li>
 * </ul>
 *
 * @since 5.50.1
 */
public class FromLeftPart extends AbstractLeftPart {
    private String property;
    /** "stream" / "collect" / "accumulate" — V5.50.1 不强制语义,留字符串辨识 */
    private String fromSource;
    /**
     * V5.51.3:accumulate 分支的统计类型。count/sum/avg/min/max 五种。
     * null 时 fromSource 走 stream / collect(不读此字段)。
     */
    private StatisticType statisticType;

    public String getProperty() {
        return property;
    }

    public void setProperty(String property) {
        this.property = property;
    }

    public String getFromSource() {
        return fromSource;
    }

    public void setFromSource(String fromSource) {
        this.fromSource = fromSource;
    }

    public StatisticType getStatisticType() {
        return statisticType;
    }

    public void setStatisticType(StatisticType statisticType) {
        this.statisticType = statisticType;
    }

    /**
     * V5.51.3:真实 evaluate 实现,3 种 fromSource 分支(stream / collect /
     * accumulate)。
     *
     * <p>跟 {@link CollectLeftPart#evaluate(EvaluationContext, Object, List)}
     * 同形态(不挂 LeftPart 接口 — LeftPart 只约定 getId(),evaluate 是约定俗成的
     * duck-typed 方法,各 LeftPart 子类按需 override)。
     */
    public Object evaluate(EvaluationContext context, Object obj, List<Object> allMatchedObjects) {
        if ("collect".equals(fromSource)) {
            return evaluateCollect(context, obj, allMatchedObjects);
        }
        if ("accumulate".equals(fromSource)) {
            return evaluateAccumulate(context, obj, allMatchedObjects);
        }
        // 默认 stream:直接拿 binding 集合
        return evaluateStream(obj);
    }

    /**
     * stream 形态:DRL {@code from $stream} — variableName 是 binding 名,
     * 直接从 obj 拿 Collection 返回。
     */
    private Object evaluateStream(Object obj) {
        Object value = Utils.getObjectProperty(obj, variableName);
        if (value == null) {
            throw new RuleException("from stream binding '" + variableName + "' 为 null");
        }
        if (!(value instanceof Collection)) {
            throw new RuleException("from stream binding '" + variableName
                + "' 不是 Collection,实际:" + value.getClass());
        }
        return value;
    }

    /**
     * collect 形态:复用 AbstractLeftPart.computeValue 拿 match facts,跟
     * CollectLeftPart.evaluate 同语义(sum/avg/max/min/property + count)。
     */
    private Object evaluateCollect(EvaluationContext context, Object obj,
                                   List<Object> allMatchedObjects) {
        ExprValue value = computeValue(context, obj, allMatchedObjects);
        List<Object> facts = value.getFacts();
        if (property == null) {
            return value.getMatch(); // count 语义
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Object fact : facts) {
            Object propertyValue = Utils.getObjectProperty(fact, property);
            total = total.add(Utils.toBigDecimal(propertyValue));
        }
        return total;
    }

    /**
     * accumulate 形态:按 {@link #statisticType} 走 count / sum / avg / min / max。
     */
    private Object evaluateAccumulate(EvaluationContext context, Object obj,
                                       List<Object> allMatchedObjects) {
        if (statisticType == null) {
            throw new RuleException("from accumulate 必须配 StatisticType(count/sum/avg/min/max),rule 当前没设");
        }
        ExprValue value = computeValue(context, obj, allMatchedObjects);
        int match = value.getMatch();
        List<Object> facts = value.getFacts();
        switch (statisticType) {
            case count:
                return match;
            case sum:
                BigDecimal sum = BigDecimal.ZERO;
                for (Object fact : facts) {
                    sum = sum.add(Utils.toBigDecimal(Utils.getObjectProperty(fact, property)));
                }
                return sum;
            case avg:
                if (match == 0) {
                    return BigDecimal.ZERO;
                }
                BigDecimal sumForAvg = BigDecimal.ZERO;
                for (Object fact : facts) {
                    sumForAvg = sumForAvg.add(Utils.toBigDecimal(Utils.getObjectProperty(fact, property)));
                }
                return sumForAvg.divide(new BigDecimal(match), 4, BigDecimal.ROUND_HALF_UP);
            case max:
                BigDecimal max = null;
                for (Object fact : facts) {
                    BigDecimal v = Utils.toBigDecimal(Utils.getObjectProperty(fact, property));
                    if (max == null || v.compareTo(max) > 0) {
                        max = v;
                    }
                }
                return max == null ? BigDecimal.ZERO : max;
            case min:
                BigDecimal min = null;
                for (Object fact : facts) {
                    BigDecimal v = Utils.toBigDecimal(Utils.getObjectProperty(fact, property));
                    if (min == null || v.compareTo(min) < 0) {
                        min = v;
                    }
                }
                return min == null ? BigDecimal.ZERO : min;
            default:
                throw new RuleException("from accumulate 不支持 StatisticType." + statisticType);
        }
    }

    @Override
    public String getId() {
        if (id == null) {
            id = "from(" + variableCategory + "." + variableLabel + ","
                + (fromSource == null ? "?" : fromSource) + ")";
            if ("accumulate".equals(fromSource) && statisticType != null) {
                id += "." + statisticType.name();
            }
        }
        return id;
    }
}
