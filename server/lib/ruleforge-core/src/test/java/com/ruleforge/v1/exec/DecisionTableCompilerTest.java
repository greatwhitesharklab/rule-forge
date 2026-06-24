package com.ruleforge.v1.exec;

import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.rete.test.EngineContextWirer;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSessionImpl;
import com.ruleforge.v1.ast.Column;
import com.ruleforge.v1.ast.ColumnDirection;
import com.ruleforge.v1.ast.DecisionTableNode;
import com.ruleforge.v1.ast.Schema;
import com.ruleforge.v1.ast.SchemaField;
import com.ruleforge.v1.ast.TableHitPolicy;
import com.ruleforge.v1.ast.TableRow;
import com.ruleforge.v1.ast.V1DataType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V7.0.0 W2-2 — DecisionTable 编译器 BDD。
 *
 * <p>DecisionTableNode → RETE rules(每行一规则)→ insert GeneralEntity → fire →
 * 验证行命中 + 输出列 SET_VARIABLE 正确。
 */
@DisplayName("V7.0.0 W2-2 — DecisionTable 编译器")
class DecisionTableCompilerTest {

    private static Schema loanSchema() {
        Schema s = new Schema();
        s.setName("LoanApplication");
        s.setFields(Arrays.asList(
                new SchemaField("riskScore", V1DataType.NUMBER),
                new SchemaField("score", V1DataType.NUMBER),
                new SchemaField("decision", V1DataType.STRING)));
        return s;
    }

    /** 定价矩阵:riskScore<30 → approve;catch-all → review。 */
    private static DecisionTableNode pricingTable() {
        DecisionTableNode node = new DecisionTableNode();
        node.setId("pricing");
        node.setName("定价矩阵");
        node.setHitPolicy(TableHitPolicy.FIRST);
        node.setInputs(Arrays.asList(
                col("riskScore", V1DataType.NUMBER),
                col("score", V1DataType.NUMBER)));
        node.setOutputs(Collections.singletonList(col("decision", V1DataType.STRING)));
        TableRow r1 = new TableRow();
        r1.setId("r1");
        r1.setConditions(Arrays.asList("riskScore < 30", "*"));
        r1.setOutputs(Collections.singletonList("approve"));
        TableRow r2 = new TableRow();
        r2.setId("r2");
        r2.setConditions(Arrays.asList("*", "*")); // catch-all
        r2.setOutputs(Collections.singletonList("review"));
        node.setRows(Arrays.asList(r1, r2));
        return node;
    }

    private static Column col(String name, V1DataType type) {
        Column c = new Column(name, type, ColumnDirection.INPUT);
        return c;
    }

    private Map<String, Object> fire(DecisionTableNode node, Schema schema, Map<String, Object> fact) {
        DecisionTableExecutor.execute(node, schema, fact);
        return fact;
    }

    @BeforeAll
    static void wire() throws Exception {
        EngineContextWirer.wire();
    }

    @Test
    @DisplayName("riskScore<30 命中 r1 → decision=approve")
    void 低风险_命中_approve() {
        Map<String, Object> fact = new GeneralEntity("LoanApplication");
        fact.put("riskScore", 20);
        fact.put("score", 700);
        fire(pricingTable(), loanSchema(), fact);
        assertThat(fact.get("decision")).isEqualTo("approve");
    }

    @Test
    @DisplayName("riskScore=50 不命中 r1 → catch-all r2 → review")
    void 高风险_命中_catch_all_review() {
        Map<String, Object> fact = new GeneralEntity("LoanApplication");
        fact.put("riskScore", 50);
        fact.put("score", 700);
        fire(pricingTable(), loanSchema(), fact);
        assertThat(fact.get("decision")).isEqualTo("review");
    }

    @Test
    @DisplayName("多输入列条件 score>600 && riskScore<30 → approve")
    void 多列条件_AND() {
        // 给表加一行需要 score>600
        DecisionTableNode node = pricingTable();
        TableRow r0 = new TableRow();
        r0.setId("r0");
        r0.setConditions(Arrays.asList("riskScore < 30", "score > 600"));
        r0.setOutputs(Collections.singletonList("approve_strict"));
        // r0 放最前(高优先)
        node.setRows(Arrays.asList(r0, node.getRows().get(0), node.getRows().get(1)));

        Map<String, Object> fact = new GeneralEntity("LoanApplication");
        fact.put("riskScore", 20);
        fact.put("score", 700);
        fire(node, loanSchema(), fact);
        assertThat(fact.get("decision")).isEqualTo("approve_strict");

        // score 不够 → r0 不命中,走 r1
        Map<String, Object> fact2 = new GeneralEntity("LoanApplication");
        fact2.put("riskScore", 20);
        fact2.put("score", 500);
        fire(node, loanSchema(), fact2);
        assertThat(fact2.get("decision")).isEqualTo("approve");
    }
}
