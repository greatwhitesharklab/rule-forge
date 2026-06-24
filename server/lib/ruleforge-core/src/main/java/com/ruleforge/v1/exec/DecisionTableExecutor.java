package com.ruleforge.v1.exec;

import com.ruleforge.v1.ast.Column;
import com.ruleforge.v1.ast.DecisionTableNode;
import com.ruleforge.v1.ast.Schema;
import com.ruleforge.v1.ast.TableHitPolicy;
import com.ruleforge.v1.ast.TableRow;
import com.ruleforge.v1.cel.CelEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * V1 DecisionTable 执行器(W2-2)。
 *
 * <p><b>线性 CEL 求值,不走 RETE</b>。原因:
 * <ul>
 *   <li>FIRST 命中策略("首行命中即定")在 RETE fire-all 模型下难正确实现(多行命中会互相覆盖)</li>
 *   <li>DecisionTable 行数少(现金贷矩阵几十行),线性 CEL 求值性能足够</li>
 *   <li>行 conditions 本就是 CEL,CelEngine.evalBoolean 直接求值,简洁正确</li>
 *   <li>RETE 留给 RuleSet(规则多,perf 收益大)</li>
 * </ul>
 *
 * <p>执行语义:
 * <ul>
 *   <li>{@link TableHitPolicy#FIRST}/{@link TableHitPolicy#UNIQUE}/{@link TableHitPolicy#PRIORITY}:
 *      按行序 CEL 求值,首命中行的 outputs 应用到 fact,停止</li>
 *   <li>{@link TableHitPolicy#ANY}/{@link TableHitPolicy#COLLECT}:所有命中行 outputs 应用
 *      (COLLECT 聚合到列表 — MVP 直接后写覆盖,逐字段)</li>
 *   <li>无命中 + 有 catch-all 行(全 '*')→ 应用 catch-all outputs(default)</li>
 * </ul>
 *
 * <p>condition 通配 '*' = 任意(跳过该列求值)。
 */
public final class DecisionTableExecutor {

    private DecisionTableExecutor() {
    }

    public static void execute(DecisionTableNode node, Schema schema, Map<String, Object> fact) {
        List<Column> inputs = node.getInputs();
        List<Column> outputs = node.getOutputs();
        TableHitPolicy policy = node.getHitPolicy() == null ? TableHitPolicy.FIRST : node.getHitPolicy();
        List<TableRow> rows = node.getRows();
        if (rows == null || outputs == null) {
            return;
        }

        TableRow defaultRow = null;
        boolean firstLike = policy == TableHitPolicy.FIRST
                || policy == TableHitPolicy.UNIQUE || policy == TableHitPolicy.PRIORITY;
        boolean matchedAny = false;

        for (TableRow row : rows) {
            if (isCatchAll(row, inputs == null ? 0 : inputs.size())) {
                if (defaultRow == null) {
                    defaultRow = row;
                }
                continue;
            }
            if (rowMatches(row, inputs, schema, fact)) {
                applyRowOutputs(row, outputs, fact);
                matchedAny = true;
                if (firstLike) {
                    return; // FIRST:首命中即停
                }
                // ANY/COLLECT:继续
            }
        }

        // 无命中 → catch-all default
        if (!matchedAny && defaultRow != null) {
            applyRowOutputs(defaultRow, outputs, fact);
        }
    }

    /** 行的所有非 '*' input 列条件都满足 → 命中。 */
    private static boolean rowMatches(TableRow row, List<Column> inputs, Schema schema, Map<String, Object> fact) {
        List<String> conds = row.getConditions();
        if (conds == null) {
            return true;
        }
        for (int c = 0; c < inputs.size() && c < conds.size(); c++) {
            String cond = conds.get(c);
            if (cond == null || cond.trim().isEmpty() || cond.trim().equals("*")) {
                continue;
            }
            if (!CelEngine.evalBoolean(cond, fact, schema)) {
                return false;
            }
        }
        return true;
    }

    private static void applyRowOutputs(TableRow row, List<Column> outputs, Map<String, Object> fact) {
        List<Object> outValues = row.getOutputs();
        if (outValues == null) return;
        for (int o = 0; o < outputs.size() && o < outValues.size(); o++) {
            fact.put(outputs.get(o).getName(), outValues.get(o));
        }
    }

    private static boolean isCatchAll(TableRow row, int inputCount) {
        List<String> conds = row.getConditions();
        if (conds == null) return true;
        for (int c = 0; c < inputCount && c < conds.size(); c++) {
            String cond = conds.get(c);
            if (cond != null && !cond.trim().isEmpty() && !cond.trim().equals("*")) {
                return false;
            }
        }
        return true;
    }
}
