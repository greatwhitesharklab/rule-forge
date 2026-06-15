package com.ruleforge.ir.drl;

/**
 * V5.78.1 — DRL syntax error 描述。
 *
 * <p>背景:DrlIdeService.parseWithErrors 用此结构收集 ANTLR
 * {@code BaseErrorListener.syntaxError} 回调,跟 {@link DrlParseException}
 * 不同 — 后者是 deserializer 强校验模式抛出,前者是 IDE live editing
 * 场景的"非致命错误列表"。
 *
 * <p>字段语义:
 * <ul>
 *   <li>{@code line} 1-based 行号(ANTLR 约定)</li>
 *   <li>{@code column} 0-based 列号(ANTLR 约定,跟 IDE 1-based 差 1,
 *       UI 层自行 +1)</li>
 *   <li>{@code message} ANTLR 原始 message,可能含 token 文本</li>
 * </ul>
 *
 * @since 5.78
 */
public final class SyntaxError {

    private final int line;
    private final int column;
    private final String message;

    public SyntaxError(int line, int column, String message) {
        this.line = line;
        this.column = column;
        this.message = message;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "SyntaxError{line=" + line + ", column=" + column + ", message='" + message + "'}";
    }
}
