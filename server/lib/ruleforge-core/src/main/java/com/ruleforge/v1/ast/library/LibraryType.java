package com.ruleforge.v1.ast.library;

/**
 * V1 库类型(vl/cl/pl,al 留 V2)。映射老引擎 Value 体系:
 * <ul>
 *   <li>{@link #VARIABLE} — 变量库(vl):fact schema 字段定义,RuleAsset.schema 引用</li>
 *   <li>{@link #CONSTANT} — 常量库(cl):命名常量,CEL {@code const.NAME}(ConstantValue/PropertyConfigurer)</li>
 *   <li>{@link #PARAMETER} — 参数库(pl):运行时可调参数,CEL {@code param.NAME}(ParameterValue/会话参数)</li>
 * </ul>
 */
public enum LibraryType {
    VARIABLE,
    CONSTANT,
    PARAMETER
}
