package com.ruleforge.ir.dmn;

import com.ruleforge.model.table.Cell;
import com.ruleforge.model.table.Column;
import com.ruleforge.model.table.ColumnType;
import com.ruleforge.model.table.DecisionTable;
import com.ruleforge.model.table.HitPolicy;
import com.ruleforge.model.table.Row;
import com.ruleforge.model.table.TableDialect;
import com.ruleforge.model.rule.SimpleValue;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

/**
 * V5.40 — RuleForge DecisionTable → DMN 1.3 决策表适配器(写出,序列化)。
 *
 * <p>这是 {@link DmnTableDeserializer} 的反向操作。字段映射表与 deserializer 对称:
 * <ul>
 *   <li>{@link DecisionTable#getVariableName()} → DMN {@code <variable name="..."/>}</li>
 *   <li>{@link DecisionTable#getHitPolicy()} → DMN {@code <decisionTable hitPolicy="...">}</li>
 *   <li>{@link DecisionTable#getAggregation()} → DMN {@code <decisionTable aggregation="...">}</li>
 *   <li>Columns of type {@link ColumnType#Criteria} → DMN {@code <input>}</li>
 *   <li>Columns of type {@link ColumnType#Assignment} → DMN {@code <output>}</li>
 *   <li>每个 {@link Row} → DMN {@code <rule>},cells 文本取自 {@link com.ruleforge.model.rule.SimpleValue#getContent()}</li>
 * </ul>
 *
 * <p>输出 namespace 用 DMN 1.3(20191111/MODEL/),Kie 10.1.0 实测稳。
 *
 * @since 5.40
 */
public class DmnTableSerializer {

    private static final String DMN_NS = "https://www.omg.org/spec/DMN/20191111/MODEL/";

    /**
     * Given RuleForge DecisionTable,When serialize,Then 输出 DMN 1.3 XML 字符串。
     *
     * <p>要求 table.dialect 必须是 {@link TableDialect#DMN} — 防止不小心把 RuleForge Native
     * 表导出成 DMN 1.3(会丢字段)。
     */
    public String serialize(DecisionTable table) {
        if (table == null) {
            throw new IllegalArgumentException("DecisionTable must not be null");
        }
        if (table.getDialect() != TableDialect.DMN) {
            throw new IllegalArgumentException(
                "DecisionTable.dialect must be TableDialect.DMN, got: " + table.getDialect()
                + " — refusing to export RuleForge Native table as DMN 1.3");
        }

        Document doc = DocumentHelper.createDocument();
        Element definitions = doc.addElement("definitions", DMN_NS);
        definitions.addAttribute("id", "rf_" + System.currentTimeMillis());
        definitions.addAttribute("name", table.getVariableName() == null
            ? "DecisionTable" : table.getVariableName());
        definitions.addAttribute("namespace",
            "http://ruleforge.com/dmn/" + (table.getVariableName() == null
                ? "exported" : table.getVariableName().replaceAll("\\s+", "_")));

        // decision
        Element decision = definitions.addElement("decision", DMN_NS);
        decision.addAttribute("id", "d_exported");
        decision.addAttribute("name", table.getVariableName() == null
            ? "Decision" : table.getVariableName());

        // variable
        Element variable = decision.addElement("variable", DMN_NS);
        variable.addAttribute("name", table.getVariableName() == null
            ? "Decision" : table.getVariableName());
        variable.addAttribute("typeRef", "string");

        // decisionTable
        Element dt = decision.addElement("decisionTable", DMN_NS);
        dt.addAttribute("id", "dt_exported");
        if (table.getHitPolicy() != null) {
            dt.addAttribute("hitPolicy", table.getHitPolicy().name());
        } else {
            dt.addAttribute("hitPolicy", HitPolicy.FIRST.name());
        }
        if (table.getAggregation() != null) {
            dt.addAttribute("aggregation", table.getAggregation().name());
        }

        // inputs — Columns of type Criteria
        int inputIdx = 0;
        int outputIdx = 0;
        for (Column col : table.getColumns()) {
            if (col.getType() == ColumnType.Criteria) {
                Element input = dt.addElement("input", DMN_NS);
                input.addAttribute("id", "i_" + inputIdx);
                if (col.getVariableLabel() != null) {
                    input.addAttribute("label", col.getVariableLabel());
                }
                if (col.getVariableName() != null) {
                    input.addAttribute("name", col.getVariableName());
                }
                Element inputExpression = input.addElement("inputExpression", DMN_NS);
                inputExpression.addAttribute("id", "ie_" + inputIdx);
                inputExpression.addAttribute("typeRef", "string");
                Element inputText = inputExpression.addElement("text", DMN_NS);
                inputText.setText(col.getVariableName() == null ? "" : col.getVariableName());
                inputIdx++;
            }
        }

        // outputs — Columns of type Assignment
        for (Column col : table.getColumns()) {
            if (col.getType() == ColumnType.Assignment) {
                Element output = dt.addElement("output", DMN_NS);
                output.addAttribute("id", "o_" + outputIdx);
                if (col.getVariableLabel() != null) {
                    output.addAttribute("name", col.getVariableLabel());
                }
                output.addAttribute("typeRef", "string");
                outputIdx++;
            }
        }

        // rules — 每个 Row 对应一个 <rule>
        Map<String, Cell> cellMap = table.getCellMap();
        int ruleIdx = 0;
        for (Row row : table.getRows()) {
            Element rule = dt.addElement("rule", DMN_NS);
            rule.addAttribute("id", "r_" + ruleIdx);

            // inputEntries — columns of type Criteria,顺序对应
            int inputCol = 0;
            for (Column col : table.getColumns()) {
                if (col.getType() == ColumnType.Criteria) {
                    Cell cell = cellMap == null ? null
                        : cellMap.get(table.buildCellKey(row.getNum(), inputCol));
                    Element inputEntry = rule.addElement("inputEntry", DMN_NS);
                    inputEntry.addAttribute("id", "ie_r" + ruleIdx + "_" + inputCol);
                    Element inputEntryText = inputEntry.addElement("text", DMN_NS);
                    inputEntryText.setText(valueToText(cell));
                    inputCol++;
                }
            }
            // outputEntries — columns of type Assignment
            int outputCol = inputCol;
            for (Column col : table.getColumns()) {
                if (col.getType() == ColumnType.Assignment) {
                    Cell cell = cellMap == null ? null
                        : cellMap.get(table.buildCellKey(row.getNum(), outputCol));
                    Element outputEntry = rule.addElement("outputEntry", DMN_NS);
                    outputEntry.addAttribute("id", "oe_r" + ruleIdx + "_" + outputCol);
                    Element outputEntryText = outputEntry.addElement("text", DMN_NS);
                    outputEntryText.setText(valueToText(cell));
                    outputCol++;
                }
            }
            ruleIdx++;
        }

        // 输出格式化 XML 字符串
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setEncoding("UTF-8");
        StringWriter sw = new StringWriter();
        try {
            XMLWriter writer = new XMLWriter(sw, format);
            writer.write(doc);
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write DMN XML", e);
        }
        return sw.toString();
    }

    private static String valueToText(Cell cell) {
        if (cell == null || cell.getValue() == null) {
            return "-";
        }
        if (cell.getValue() instanceof SimpleValue) {
            return ((SimpleValue) cell.getValue()).getContent();
        }
        // 其他 Value 实现:fallback 到 "-"
        return "-";
    }
}
