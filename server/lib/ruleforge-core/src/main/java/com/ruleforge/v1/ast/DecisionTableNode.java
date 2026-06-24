package com.ruleforge.v1.ast;

import java.util.List;

/**
 * DecisionTable 节点 — 额度 / 定价 / 审批矩阵。
 * inputs(输入列)+ outputs(输出列)+ rows(条件行),按 {@link TableHitPolicy} 命中。
 */
public class DecisionTableNode extends NodeBase {
    private TableHitPolicy hitPolicy = TableHitPolicy.FIRST;
    private List<Column> inputs;
    private List<Column> outputs;
    private List<TableRow> rows;

    @Override
    public String getType() {
        return "DecisionTable";
    }

    public TableHitPolicy getHitPolicy() {
        return hitPolicy;
    }

    public void setHitPolicy(TableHitPolicy hitPolicy) {
        this.hitPolicy = hitPolicy;
    }

    public List<Column> getInputs() {
        return inputs;
    }

    public void setInputs(List<Column> inputs) {
        this.inputs = inputs;
    }

    public List<Column> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<Column> outputs) {
        this.outputs = outputs;
    }

    public List<TableRow> getRows() {
        return rows;
    }

    public void setRows(List<TableRow> rows) {
        this.rows = rows;
    }
}
