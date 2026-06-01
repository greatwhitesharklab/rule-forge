package com.ruleforge.model.scorecard.runtime;

import java.util.List;

/**
 * @author Jacky.gao
 * @since 2016年9月26日
 */
public interface RowItem {
    int getRowNumber();

    Object getScore();

    Object getActualScore();

    void setActualScore(Object actualScore);

    String getWeight();

    List<CellItem> getCellItems();
}
