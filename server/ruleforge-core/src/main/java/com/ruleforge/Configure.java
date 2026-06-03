package com.ruleforge;

import org.apache.commons.lang.StringUtils;

public class Configure {
    private static String dateFormat;
    private static String tempStorePath;

    public void setDateFormat(String dateFormat) {
        if (StringUtils.isEmpty(dateFormat) || dateFormat.equals("${ruleforge.dateFormat}")) {
            Configure.dateFormat = "yyyy-MM-dd HH:mm:ss";
        } else {
            Configure.dateFormat = dateFormat;
        }
    }

    public void setTempStorePath(String tempStorePath) {
        if (!tempStorePath.equals("${ruleforge.tempStorePath}")) {
            Configure.tempStorePath = tempStorePath;
        }
    }

    public static String getTempStorePath() {
        return tempStorePath;
    }

    public static String getDateFormat() {
        // 单元测试 / 非 Spring 上下文下没人调 setDateFormat(),dateFormat 为 null。
        // 兜底返回 setDateFormat 默认值,避免 BuildRulesVisitor 等下游 NPE。
        return dateFormat != null ? dateFormat : "yyyy-MM-dd HH:mm:ss";
    }
}
