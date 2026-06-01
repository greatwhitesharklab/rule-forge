package com.ruleforge.console.servlet.crosstab;

import com.ruleforge.console.servlet.CellContent;
import java.util.List;

public class CrossData {
    private CrossHeader header;
    private List<CrossRow> rows;
    private List<CrossColumn> columns;
    private List<CellContent> cells;

    public CrossHeader getHeader() {
        return this.header;
    }

    public void setHeader(CrossHeader header) {
        this.header = header;
    }

    public List<CrossRow> getRows() {
        return this.rows;
    }

    public void setRows(List<CrossRow> rows) {
        this.rows = rows;
    }

    public List<CrossColumn> getColumns() {
        return this.columns;
    }

    public void setColumns(List<CrossColumn> columns) {
        this.columns = columns;
    }

    public List<CellContent> getCells() {
        return this.cells;
    }

    public void setCells(List<CellContent> cells) {
        this.cells = cells;
    }
}
