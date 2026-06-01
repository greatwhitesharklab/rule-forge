package com.ruleforge.console.servlet.common;

public class ErrorInfo {

    private String message;
    private int line;
    private int charPositionInLine;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getLine() {
        return line;
    }

    public void setLine(int line) {
        this.line = line;
    }

    public int getCharPositionInLine() {
        return charPositionInLine;
    }

    public void setCharPositionInLine(int charPositionInLine) {
        this.charPositionInLine = charPositionInLine;
    }
}
