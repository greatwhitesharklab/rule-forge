package com.ruleforge.ir.migration;

import com.ruleforge.model.table.ColumnType;
import com.ruleforge.model.table.HitPolicy;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

/**
 * V5.40.5 — RuleForge 老 .xml 决策表 → DMN 1.3 一次性迁移转换器。
 *
 * <p>这是 V5.40 决策表→DMN 切格式的"数据迁移"工具。一次性跑在 console-app 启动时
 * (可配置 {@code ruleforge.legacy-xml.migrate=true}),把老 .xml 决策表翻译成 .dmn
 * 写回 Git 仓库,跑完删原 .xml。
 *
 * <p>支持老 .xml 格式(RuleForge 2015 起一直用的 {@code <rule-config> → <decision-table>} 结构):
 * <pre>{@code
 * <rule-config>
 *   <decision-table name="..." salience="0">
 *     <columns>
 *       <column num="0" name="age" type="Criteria" datatype="Integer"/>
 *       <column num="1" name="tier" type="Assignment" datatype="String"/>
 *     </columns>
 *     <rows>
 *       <row num="0">
 *         <cell col="0" value=">= 30"/>
 *         <cell col="1" value="GOLD"/>
 *       </row>
 *     </rows>
 *   </decision-table>
 * </rule-config>
 * }</pre>
 *
 * <p>翻译规则:
 * <ul>
 *   <li>{@code <decision-table name="...">} → DMN {@code decision.name + variable.name}</li>
 *   <li>{@code type="Criteria"} → DMN {@code <input>};{@code type="Assignment"} → DMN {@code <output>}</li>
 *   <li>每 {@code <row>} → DMN {@code <rule>},{@code <cell col="i">value="..."} →
 *       {@code <inputEntry i="i"><text>...</text></inputEntry>} 或 {@code <outputEntry>}</li>
 *   <li>{@code value="-"} 保留(等价 DMN 短路符);其他原样落到 {@code <text>}</li>
 *   <li>默认 {@code hitPolicy=FIRST}(老 RuleForge 决策表行号顺序短路语义)</li>
 * </ul>
 *
 * <p>注意:**V5.40.5 不支持**老 .xml 的 complex features(DSL 占位符、variable library 引用、
 * library include、cross-decision-table) — 这些场景在 V5.40.6 / V5.50 扩展支持。
 * 失败时抛 {@link XmlMigrationException},由调用方决定 fallback 策略(保留老 .xml 不动)。
 *
 * @since 5.40
 */
public class XmlToDmnTableConverter {

    /**
     * Given 老 .xml 决策表字符串,When convert,Then 产生 DMN 1.3 XML 字符串。
     *
     * <p>成功路径:V5.40.5 BDD 验证转换后 DMN 能被 Kie 加载 + evaluateAll 出正确结果。
     */
    public String convert(String xmlContent) {
        if (xmlContent == null || xmlContent.isEmpty()) {
            throw new XmlMigrationException("XML content must not be empty");
        }

        Document doc;
        try {
            doc = DocumentHelper.parseText(xmlContent);
        } catch (DocumentException e) {
            throw new XmlMigrationException("Failed to parse XML: " + e.getMessage(), e);
        }

        Element root = doc.getRootElement();
        // 老 .xml 的根通常是 <rule-config>,内含 <decision-table> 或其它
        Element decisionTable = findDecisionTableElement(root);
        if (decisionTable == null) {
            throw new XmlMigrationException(
                "No <decision-table> element found in XML root: " + root.getName());
        }

        String tableName = decisionTable.attributeValue("name");
        if (tableName == null) {
            throw new XmlMigrationException(
                "<decision-table> missing required 'name' attribute");
        }

        // === Build DMN XML ===
        Document dmn = DocumentHelper.createDocument();
        Element definitions = dmn.addElement("definitions", DmnNamespace.DMN_1_3_MODEL);
        definitions.addAttribute("id", "rf_" + safeId(tableName));
        definitions.addAttribute("name", tableName);
        definitions.addAttribute("namespace", "http://ruleforge.com/dmn/migrated/" + safeId(tableName));

        Element decision = definitions.addElement("decision", DmnNamespace.DMN_1_3_MODEL);
        // 顶级 inputData 节点 — Kie 10.1.0 model.getInputs() 只会数顶级 inputData,
        // 缺失会返回 0(不影响 evaluateAll,但 deserializer 抽不出)
        // 跟简单 table.dmn fixture 一致:为每个 input column 加一个 inputData
        Element columnsElemEarly = decisionTable.element("columns");
        if (columnsElemEarly != null) {
            int inputDataIdx = 0;
            for (Element col : (List<Element>) columnsElemEarly.elements("column")) {
                String name = col.attributeValue("name");
                String type = col.attributeValue("type");
                if (name == null || type == null) {
                    throw new XmlMigrationException(
                        "<column> missing required 'name' or 'type' attribute");
                }
                if (ColumnType.Criteria.name().equals(type) || "Condition".equals(type)) {
                    Element inputData = definitions.addElement("inputData",
                        DmnNamespace.DMN_1_3_MODEL);
                    inputData.addAttribute("id", "id_" + inputDataIdx);
                    inputData.addAttribute("name", name);
                    Element varElem = inputData.addElement("variable",
                        DmnNamespace.DMN_1_3_MODEL);
                    varElem.addAttribute("name", name);
                    varElem.addAttribute("typeRef", "string");
                    inputDataIdx++;
                }
            }
        }
        decision.addAttribute("id", "d_" + safeId(tableName));
        decision.addAttribute("name", tableName);
        Element variable = decision.addElement("variable", DmnNamespace.DMN_1_3_MODEL);
        variable.addAttribute("name", tableName);
        variable.addAttribute("typeRef", "string");

        Element dt = decision.addElement("decisionTable", DmnNamespace.DMN_1_3_MODEL);
        dt.addAttribute("id", "dt_" + safeId(tableName));
        // V5.40.5 默认 hitPolicy=FIRST(老 RuleForge 决策表行号顺序短路语义)
        dt.addAttribute("hitPolicy", HitPolicy.FIRST.name());

        // === Columns(决策表内的 <input> / <output> clauses) ===
        int colIdx = 0;
        if (columnsElemEarly != null) {
            for (Element col : (List<Element>) columnsElemEarly.elements("column")) {
                String name = col.attributeValue("name");
                String type = col.attributeValue("type");
                if (name == null || type == null) {
                    throw new XmlMigrationException(
                        "<column> missing required 'name' or 'type' attribute");
                }
                if (ColumnType.Criteria.name().equals(type)
                    || "Condition".equals(type)) {  // 兼容老 type 名字
                    Element input = dt.addElement("input", DmnNamespace.DMN_1_3_MODEL);
                    input.addAttribute("id", "i_" + colIdx);
                    input.addAttribute("name", name);
                    input.addAttribute("label", name);
                    Element inputExpression = input.addElement("inputExpression",
                        DmnNamespace.DMN_1_3_MODEL);
                    inputExpression.addAttribute("id", "ie_" + colIdx);
                    inputExpression.addAttribute("typeRef", "string");
                    inputExpression.addElement("text", DmnNamespace.DMN_1_3_MODEL)
                        .setText(name);
                } else if (ColumnType.Assignment.name().equals(type)
                    || "Action".equals(type)) {
                    Element output = dt.addElement("output", DmnNamespace.DMN_1_3_MODEL);
                    output.addAttribute("id", "o_" + colIdx);
                    output.addAttribute("name", name);
                    output.addAttribute("typeRef", "string");
                } else {
                    throw new XmlMigrationException(
                        "Unsupported column type: " + type + " (supported: Criteria, Assignment)");
                }
                colIdx++;
            }
        }

        // === Rules ===
        Element rowsElem = decisionTable.element("rows");
        if (rowsElem != null) {
            int ruleIdx = 0;
            for (Element row : (List<Element>) rowsElem.elements("row")) {
                Element rule = dt.addElement("rule", DmnNamespace.DMN_1_3_MODEL);
                rule.addAttribute("id", "r_" + ruleIdx);

                List<Element> cells = row.elements("cell");
                for (Element cell : cells) {
                    String colAttr = cell.attributeValue("col");
                    String value = cell.attributeValue("value", "-");
                    int cellCol;
                    try {
                        cellCol = Integer.parseInt(colAttr);
                    } catch (NumberFormatException e) {
                        throw new XmlMigrationException(
                            "<cell> col attribute must be integer, got: " + colAttr, e);
                    }
                    Element columnElement = columnsElemEarly == null ? null
                        : (Element) columnsElemEarly.elements("column").get(cellCol);
                    if (columnElement == null) {
                        throw new XmlMigrationException(
                            "<cell col=\"" + cellCol + "\"> references missing column");
                    }
                    String colType = columnElement.attributeValue("type");
                    boolean isInput = ColumnType.Criteria.name().equals(colType)
                        || "Condition".equals(colType);
                    if (isInput) {
                        Element inputEntry = rule.addElement("inputEntry",
                            DmnNamespace.DMN_1_3_MODEL);
                        inputEntry.addAttribute("id", "ie_r" + ruleIdx + "_" + cellCol);
                        inputEntry.addElement("text", DmnNamespace.DMN_1_3_MODEL)
                            .setText(value);
                    } else {
                        Element outputEntry = rule.addElement("outputEntry",
                            DmnNamespace.DMN_1_3_MODEL);
                        outputEntry.addAttribute("id", "oe_r" + ruleIdx + "_" + cellCol);
                        // 老 RuleForge 用裸 GOLD/SILVER/BRONZE,DMN 要求带引号
                        outputEntry.addElement("text", DmnNamespace.DMN_1_3_MODEL)
                            .setText("\"" + value + "\"");
                    }
                }
                ruleIdx++;
            }
        }

        // === Output ===
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setEncoding("UTF-8");
        StringWriter sw = new StringWriter();
        try {
            XMLWriter writer = new XMLWriter(sw, format);
            writer.write(dmn);
            writer.close();
        } catch (IOException e) {
            throw new XmlMigrationException("Failed to serialize DMN XML", e);
        }
        return sw.toString();
    }

    private static Element findDecisionTableElement(Element root) {
        if ("decision-table".equals(root.getName())) {
            return root;
        }
        return root.element("decision-table");
    }

    private static String safeId(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
