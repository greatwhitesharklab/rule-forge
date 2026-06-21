package com.ruleforge.model.scorecard.runtime;

import com.ruleforge.Utils;
import com.ruleforge.debug.MsgType;
import com.ruleforge.engine.Context;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author Jacky.gao
 * @since 2016年9月26日
 */
public class ScorecardImpl implements Scorecard {
    private String name;
    private boolean debug;
    private List<RowItem> rowItems;

    public ScorecardImpl(String name, List<RowItem> rowItems, boolean debug) {
        this.name = name;
        this.rowItems = rowItems;
        this.debug = debug;
    }

    public BigDecimal executeSum(Context context) {
        BigDecimal result = new BigDecimal(0);
        for (RowItem row : rowItems) {
            BigDecimal score = Utils.toBigDecimal(row.getScore());
            row.setActualScore(score);
            result = result.add(score);
        }
        // V6.9.16 — logMsg debug 门控 (V6.9.9.1 模式)
        if (this.debug) {
            String msg = "+++ 求和得分：" + result;
            context.logMsg(msg, MsgType.ScoreCard);
        }
        return result;
    }

    public BigDecimal executeWeightSum(Context context) {
        BigDecimal result = new BigDecimal(0);
        for (RowItem row : rowItems) {
            BigDecimal score = Utils.toBigDecimal(row.getScore());
            BigDecimal weight = Utils.toBigDecimal(row.getWeight());
            BigDecimal actualScore = score.multiply(weight);
            row.setActualScore(actualScore);
            result = result.add(actualScore);
        }
        // V6.9.16 — logMsg debug 门控 (V6.9.9.1 模式)
        if (this.debug) {
            String msg = "+++ 加权求和得分：" + result;
            context.logMsg(msg, MsgType.ScoreCard);
        }
        return result;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<RowItem> getRowItems() {
        return rowItems;
    }
}
