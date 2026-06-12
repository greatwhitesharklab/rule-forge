package com.ruleforge.ir.dmn;

import com.ruleforge.model.table.Aggregation;
import com.ruleforge.model.table.Cell;
import com.ruleforge.model.table.Column;
import com.ruleforge.model.table.ColumnType;
import com.ruleforge.model.table.HitPolicy;
import com.ruleforge.model.table.Row;
import com.ruleforge.model.table.TableDialect;
import com.ruleforge.model.rule.SimpleValue;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.ast.DecisionNode;
import org.kie.dmn.model.api.Decision;
import org.kie.dmn.model.api.DecisionRule;
import org.kie.dmn.model.api.InputClause;
import org.kie.dmn.model.api.LiteralExpression;
import org.kie.dmn.model.api.OutputClause;
import org.kie.dmn.model.api.UnaryTests;

import java.util.ArrayList;
import java.util.List;

/**
 * V5.40 — DMN 1.3 决策表 → RuleForge DecisionTable 适配器(只读,反序列化)。
 *
 * <p>字段映射表:
 * <ul>
 *   <li>DMN {@code Decision.variable.name} → {@link com.ruleforge.model.table.DecisionTable#getVariableName()}</li>
 *   <li>DMN {@code DecisionTable.hitPolicy} → {@link com.ruleforge.model.table.DecisionTable#getHitPolicy()}</li>
 *   <li>DMN {@code DecisionTable.aggregation} → {@link com.ruleforge.model.table.DecisionTable#getAggregation()}</li>
 *   <li>DMN {@code DecisionTable.output[*].name} → {@link Column#getVariableLabel()}(输出列)</li>
 *   <li>DMN {@code DecisionTable.input[*].inputExpression.text} → {@link Column#getVariableName()}(输入列变量名)</li>
 *   <li>DMN {@code DecisionRule.inputEntry[*].text} → {@link Cell#getValue()}(SimpleValue.content)</li>
 *   <li>DMN {@code DecisionRule.outputEntry[*].text} → {@link Cell#getValue()}(SimpleValue.content)</li>
 * </ul>
 *
 * <p>V5.40+ 加载路径走 DMN:设 {@link com.ruleforge.model.table.DecisionTable#setDialect(TableDialect)} = DMN。
 * FEEL 表达式此时**不**翻译,等 V5.40.4 在 TableRulesBuilder 里做。
 *
 * @since 5.40
 */
public class DmnTableDeserializer {

    /**
     * Given Kie DMN DMNModel,When 编译并反序列化第一个决策,Then 输出 RuleForge DecisionTable。
     *
     * <p>要求 DMNModel 必须有 1 个 decision 节点且是 decisionTable 表达式类型;
     * 0 决策 / 多决策 / literalExpression 决策都抛 IllegalArgumentException。
     */
    public com.ruleforge.model.table.DecisionTable deserialize(DMNModel model) {
        if (model == null) {
            throw new IllegalArgumentException("DMNModel must not be null");
        }
        if (model.getDecisions().isEmpty()) {
            throw new IllegalArgumentException(
                "DMNModel has no decision nodes, got 0 — model name=" + model.getName());
        }
        if (model.getDecisions().size() > 1) {
            throw new IllegalArgumentException(
                "DMNModel has " + model.getDecisions().size()
                + " decision nodes — V5.40.3 only supports single-decision tables, model name="
                + model.getName());
        }

        DecisionNode decisionNode = model.getDecisions().iterator().next();
        Decision kieDecision = decisionNode.getDecision();
        if (!(kieDecision.getExpression() instanceof org.kie.dmn.model.api.DecisionTable)) {
            throw new IllegalArgumentException(
                "Decision '" + decisionNode.getName()
                + "' expression is not a decisionTable, got: "
                + (kieDecision.getExpression() == null ? "null"
                    : kieDecision.getExpression().getClass().getSimpleName()));
        }
        org.kie.dmn.model.api.DecisionTable kieTable =
            (org.kie.dmn.model.api.DecisionTable) kieDecision.getExpression();

        com.ruleforge.model.table.DecisionTable out = new com.ruleforge.model.table.DecisionTable();
        // V5.40+ 标志:这个表来自 DMN 路径
        out.setDialect(TableDialect.DMN);

        // variableName — DMN decision 节点 <variable name="..."/>
        if (kieDecision.getVariable() != null && kieDecision.getVariable().getName() != null) {
            out.setVariableName(kieDecision.getVariable().getName());
        } else {
            out.setVariableName(decisionNode.getName());
        }

        // hitPolicy — DMN DecisionTable.hitPolicy (enum)
        if (kieTable.getHitPolicy() != null) {
            HitPolicy hit = mapHitPolicy(kieTable.getHitPolicy().value());
            if (hit != null) {
                out.setHitPolicy(hit);
            }
        }

        // aggregation — DMN DecisionTable.aggregation (enum)
        if (kieTable.getAggregation() != null) {
            Aggregation agg = mapAggregation(kieTable.getAggregation().value());
            if (agg != null) {
                out.setAggregation(agg);
            }
        }

        // Columns — 先 inputs 后 outputs
        List<Column> columns = new ArrayList<>();
        int colNum = 0;
        for (InputClause kieInput : kieTable.getInput()) {
            Column col = new Column();
            col.setNum(colNum++);
            col.setType(ColumnType.Criteria);
            col.setVariableName(extractInputExpressionText(kieInput));
            if (kieInput.getInputExpression() != null
                && kieInput.getInputExpression().getTypeRef() != null) {
                col.setVariableLabel(kieInput.getInputExpression().getTypeRef().getLocalPart());
            }
            columns.add(col);
        }
        for (OutputClause kieOutput : kieTable.getOutput()) {
            Column col = new Column();
            col.setNum(colNum++);
            col.setType(ColumnType.Assignment);
            col.setVariableLabel(kieOutput.getName());
            if (kieOutput.getTypeRef() != null) {
                col.setVariableName(kieOutput.getTypeRef().getLocalPart());
            }
            columns.add(col);
        }
        out.setColumns(columns);

        // Rows + CellMap — DMN DecisionRule
        List<Row> rows = new ArrayList<>();
        int rowNum = 0;
        for (DecisionRule kieRule : kieTable.getRule()) {
            Row row = new Row();
            row.setNum(rowNum++);
            rows.add(row);

            // inputEntries 写到输入列
            List<UnaryTests> inputEntries = kieRule.getInputEntry();
            for (int i = 0; i < inputEntries.size() && i < kieTable.getInput().size(); i++) {
                Cell cell = new Cell();
                cell.setRow(row.getNum());
                cell.setCol(i);
                cell.setVariableName(columns.get(i).getVariableName());
                cell.setValue(textToValue(extractUnaryText(inputEntries.get(i))));
                out.addCell(cell);
            }
            // outputEntries 写到输出列偏移
            List<LiteralExpression> outputEntries = kieRule.getOutputEntry();
            int outputOffset = kieTable.getInput().size();
            for (int i = 0; i < outputEntries.size() && (outputOffset + i) < columns.size(); i++) {
                Cell cell = new Cell();
                cell.setRow(row.getNum());
                cell.setCol(outputOffset + i);
                cell.setVariableName(columns.get(outputOffset + i).getVariableName());
                cell.setValue(textToValue(extractLiteralText(outputEntries.get(i))));
                out.addCell(cell);
            }
        }
        out.setRows(rows);

        return out;
    }

    // === 辅助 ===

    private static HitPolicy mapHitPolicy(String kieValue) {
        if (kieValue == null) {
            return null;
        }
        try {
            return HitPolicy.valueOf(kieValue);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Aggregation mapAggregation(String kieValue) {
        if (kieValue == null) {
            return null;
        }
        // DMN aggregation 是大写(SUM/COUNT/MIN/MAX),我们 enum 也是大写,直接对得上
        try {
            return Aggregation.valueOf(kieValue);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String extractInputExpressionText(InputClause kieInput) {
        if (kieInput.getInputExpression() == null) {
            return null;
        }
        return kieInput.getInputExpression().getText();
    }

    private static String extractUnaryText(UnaryTests unary) {
        if (unary == null) {
            return null;
        }
        return unary.getText();
    }

    private static String extractLiteralText(LiteralExpression literal) {
        if (literal == null) {
            return null;
        }
        return literal.getText();
    }

    private static SimpleValue textToValue(String text) {
        if (text == null) {
            return null;
        }
        SimpleValue v = new SimpleValue();
        v.setContent(text);
        return v;
    }
}
