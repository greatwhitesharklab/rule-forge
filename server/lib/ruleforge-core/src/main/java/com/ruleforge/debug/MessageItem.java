package com.ruleforge.debug;

import java.util.Date;
import java.util.EnumMap;
import java.util.Map;

public class MessageItem {
    // V6.9.29 — V6.9.14 + V6.2: 28 行 switch (8 cases) → static EnumMap;
    // 砍重复 case ConsoleOutput (matches default "#000", dead code)
    private static final Map<MsgType, String> COLOR_BY_TYPE = new EnumMap<>(MsgType.class);
    static {
        COLOR_BY_TYPE.put(MsgType.Condition, "#6495ED");
        COLOR_BY_TYPE.put(MsgType.ExecuteBeanMethod, "#8A2BE2");
        COLOR_BY_TYPE.put(MsgType.ExecuteFunction, "#008B8B");
        COLOR_BY_TYPE.put(MsgType.RuleFlow, "#9932CC");
        COLOR_BY_TYPE.put(MsgType.VarAssign, "#FF7F50");
        COLOR_BY_TYPE.put(MsgType.ScoreCard, "#40E0D0");
        COLOR_BY_TYPE.put(MsgType.RuleMatch, "#666600");
        // ConsoleOutput omitted — falls through to default "#000"
    }

    private String msg;
    private MsgType type;
    private String leftVariable;
    private String leftVariableValue;
    private String rightVariable;
    private String rightVariableValue;
    /**
     * 执行时间
     */
    private Date execTime;

    public MessageItem(String msg, MsgType type) {
        this.msg = msg;
        this.type = type;
        this.execTime = new Date();
    }

    public MessageItem(String msg, MsgType type, String leftVariable, String leftVariableValue, String rightVariable, String rightVariableValue) {
        this.msg = msg;
        this.type = type;
        this.leftVariable = leftVariable;
        this.leftVariableValue = leftVariableValue;
        this.rightVariable = rightVariable;
        this.rightVariableValue = rightVariableValue;
        this.execTime = new Date();
    }

    public String toHtml() {
        String color = COLOR_BY_TYPE.getOrDefault(type, "#000");
        return "<div style=\"color:" + color + ";margin:2px\">" + msg + "</div>";
    }

    public String getMsg() {
        return msg;
    }

    public MsgType getType() {
        return type;
    }

    public String getLeftVariable() {
        return leftVariable;
    }

    public String getLeftVariableValue() {
        return leftVariableValue;
    }

    public String getRightVariable() {
        return rightVariable;
    }

    public String getRightVariableValue() {
        return rightVariableValue;
    }

    public Date getExecTime() {
        return execTime;
    }

    @Override
    public String toString() {
        return "MessageItem{" +
                "msg='" + msg + '\'' +
                ", type=" + type +
                ", leftVariable='" + leftVariable + '\'' +
                ", leftVariableValue='" + leftVariableValue + '\'' +
                ", rightVariable='" + rightVariable + '\'' +
                ", rightVariableValue='" + rightVariableValue + '\'' +
                ", execTime=" + execTime +
                '}';
    }
}
