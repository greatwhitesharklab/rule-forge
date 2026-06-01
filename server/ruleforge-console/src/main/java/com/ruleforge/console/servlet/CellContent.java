package com.ruleforge.console.servlet;

import com.ruleforge.console.servlet.decisiontable.Header;

public class CellContent {
    private int span;
    private int row;
    private int col;
    private Header header;
    private String content;
    private String type = "value";

    public int getSpan() {
        return this.span;
    }

    public void setSpan(int span) {
        this.span = span;
    }

    public int getRow() {
        return this.row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getCol() {
        return this.col;
    }

    public void setCol(int col) {
        this.col = col;
    }

    public String getContent() {
        return this.content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Header getHeader() {
        return this.header;
    }

    public void setHeader(Header header) {
        this.header = header;
    }

    public String getType() {
        return this.type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
