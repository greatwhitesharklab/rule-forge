package com.ruleforge.ir.dmn;

import com.ruleforge.model.table.DecisionTable;
import com.ruleforge.model.table.TableDialect;
import org.kie.dmn.api.core.DMNCompiler;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.core.api.DMNFactory;

import java.io.StringReader;

/**
 * V5.40.4 — .dmn 资源分流器(给 KnowledgeBuilder 用)。
 *
 * <p>目的:在 {@code KnowledgeBuilder.buildKnowledgeBase()} 入口,资源路径以 {@code .dmn}
 * 结尾时绕过老 .xml 路径,直接走 DMN 1.3 反序列化产出 RuleForge DecisionTable。
 * **不**在这里调 {@code DecisionTableRulesBuilder.buildRules()},因为 builder 依赖
 * Spring 注入的 {@code cellContentBuilder} — 留给 KnowledgeBuilder 在拿到 table 后
 * 走跟老 .xml 路径同一句调用,这样所有路径汇合到 RETE 入口。
 *
 * <p>设计取舍:
 * <ul>
 *   <li>不修改现有任何 .xml 解析路径(老测试 0 破坏)</li>
 *   <li>DMN 路径设 {@link DecisionTable#setDialect(TableDialect)} = DMN,作为路径标志</li>
 *   <li>用 Kie DMN {@link DMNCompiler#compile(java.io.Reader)} 单点加载,不用 DMNRuntime(后者要 DMNModel 而非 XML 字符串)</li>
 *   <li>FEEL 表达式此时**不**翻译,落到 {@code SimpleValue.content} 由 TableRulesBuilder
 *       当成"未知字面量"跳过执行;V5.40.5+ 加 FEEL→RuleForge 翻译器</li>
 * </ul>
 *
 * @since 5.40
 */
public class DmnResourceDispatcher {

    private final DmnTableDeserializer deserializer = new DmnTableDeserializer();

    /**
     * Given 资源路径以 .dmn 结尾 + content 字符串,When dispatch,Then 编译 + 反序列化产出 RuleForge DecisionTable。
     *
     * <p>非 .dmn 路径直接抛 IllegalArgumentException(由 KnowledgeBuilder 入口判路径,这里只接合法 .dmn)。
     */
    public DecisionTable dispatch(String resourcePath, String dmnContent) {
        if (resourcePath == null || !resourcePath.toLowerCase().endsWith(".dmn")) {
            throw new IllegalArgumentException(
                "DmnResourceDispatcher only accepts .dmn paths, got: " + resourcePath);
        }
        if (dmnContent == null || dmnContent.isEmpty()) {
            throw new IllegalArgumentException("DMN content must not be empty for path: " + resourcePath);
        }

        // Compile via Kie
        DMNCompiler compiler = DMNFactory.newCompiler();
        DMNModel dmnModel;
        try (java.io.Reader reader = new StringReader(dmnContent)) {
            dmnModel = compiler.compile(reader);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to compile DMN at " + resourcePath + ": " + e.getMessage(), e);
        }

        // Deserialize to RuleForge DecisionTable
        DecisionTable table = deserializer.deserialize(dmnModel);
        if (table.getDialect() != TableDialect.DMN) {
            // defensive — deserializer 应当已经 set DMN dialect
            table.setDialect(TableDialect.DMN);
        }

        return table;
    }
}
