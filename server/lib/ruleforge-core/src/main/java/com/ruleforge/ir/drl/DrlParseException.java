package com.ruleforge.ir.drl;

import org.antlr.v4.runtime.ParserRuleContext;

/**
 * V5.42.2 — DRL ParseTree 转换过程中的领域异常。
 *
 * <p>DrlAstVisitor / DrlDeserializer 走 ParseTree 阶段任何错误都抛本异常,带
 * (line, column) + 错误信息。CHANGELOG 写明:V5.42.2 / 5.42.4 的 caller
 * 期待接 DrlParseException,而不是 raw ANTLR RecognitionException。
 *
 * @since 5.42
 */
public class DrlParseException extends RuntimeException {

    /** 错误位置 0-based;null 表示 "not position-specific" */
    private final Integer line;
    private final Integer column;

    public DrlParseException(String message) {
        super(message);
        this.line = null;
        this.column = null;
    }

    /**
     * V5.42.3a — 给手写 parser (DslParser / PlaceholderExpander) 用的 line/column 构造器。
     * 不依赖 ANTLR 的 {@link ParserRuleContext}。
     */
    public DrlParseException(String message, int line, int column) {
        super("line " + line + ":" + column + " " + message);
        this.line = line;
        this.column = column;
    }

    public DrlParseException(String message, ParserRuleContext ctx) {
        super(formatMessage(message, ctx));
        this.line = ctx != null ? ctx.getStart().getLine() : null;
        this.column = ctx != null ? ctx.getStart().getCharPositionInLine() : null;
    }

    public DrlParseException(String message, Throwable cause) {
        super(message, cause);
        this.line = null;
        this.column = null;
    }

    public Integer getLine() { return line; }
    public Integer getColumn() { return column; }

    private static String formatMessage(String message, ParserRuleContext ctx) {
        if (ctx == null || ctx.getStart() == null) {
            return message;
        }
        return "line " + ctx.getStart().getLine() + ":" + ctx.getStart().getCharPositionInLine() + " " + message;
    }
}
