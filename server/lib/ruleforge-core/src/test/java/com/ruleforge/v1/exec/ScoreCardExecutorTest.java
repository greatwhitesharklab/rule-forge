package com.ruleforge.v1.exec;

import com.ruleforge.model.GeneralEntity;
import com.ruleforge.v1.ast.Band;
import com.ruleforge.v1.ast.Card;
import com.ruleforge.v1.ast.ScoreAggregation;
import com.ruleforge.v1.ast.ScoreCardNode;
import com.ruleforge.v1.ast.Schema;
import com.ruleforge.v1.ast.SchemaField;
import com.ruleforge.v1.ast.V1DataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V7.0.0 W2-3 — ScoreCard 执行器 BDD。bands CEL 求值 + aggregation。
 * 不进 RETE,直接 CelEngine.evalBoolean。
 */
@DisplayName("V7.0.0 W2-3 — ScoreCard 执行器")
class ScoreCardExecutorTest {

    private static Schema schema() {
        Schema s = new Schema();
        s.setName("LoanApplication");
        s.setFields(Arrays.asList(
                new SchemaField("age", V1DataType.NUMBER),
                new SchemaField("income", V1DataType.NUMBER),
                new SchemaField("riskScore", V1DataType.NUMBER)));
        return s;
    }

    private static Band band(String cond, double score) {
        Band b = new Band();
        b.setCondition(cond);
        b.setScore(score);
        return b;
    }

    private static Card card(String id, String field, Band... bands) {
        Card c = new Card();
        c.setId(id);
        c.setField(field);
        c.setBands(Arrays.asList(bands));
        return c;
    }

    private Map<String, Object> fact(int age) {
        Map<String, Object> f = new GeneralEntity("LoanApplication");
        f.put("age", age);
        f.put("income", 8000);
        return f;
    }

    @Test
    @DisplayName("单 card age<25→20 / age>=25→50,age=30 SUM → riskScore=50")
    void 单card_命中_band_SUM() {
        ScoreCardNode node = new ScoreCardNode();
        node.setId("risk");
        node.setOutput("riskScore");
        node.setAggregation(ScoreAggregation.SUM);
        node.setCards(Collections.singletonList(
                card("age_card", "age", band("age < 25", 20), band("age >= 25", 50))));
        Map<String, Object> f = fact(30);
        ScoreCardExecutor.execute(node, schema(), f);
        assertThat(((Number) f.get("riskScore")).doubleValue()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("age=20 → age<25 band → riskScore=20")
    void 年轻_命中_低分() {
        ScoreCardNode node = new ScoreCardNode();
        node.setId("risk");
        node.setOutput("riskScore");
        node.setAggregation(ScoreAggregation.SUM);
        node.setCards(Collections.singletonList(
                card("age_card", "age", band("age < 25", 20), band("age >= 25", 50))));
        Map<String, Object> f = fact(20);
        ScoreCardExecutor.execute(node, schema(), f);
        assertThat(((Number) f.get("riskScore")).doubleValue()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("多 card SUM: age_card 50 + income_card 40 → 90")
    void 多card_SUM_累加() {
        ScoreCardNode node = new ScoreCardNode();
        node.setId("risk");
        node.setOutput("riskScore");
        node.setAggregation(ScoreAggregation.SUM);
        node.setCards(Arrays.asList(
                card("age_card", "age", band("age >= 25", 50)),
                card("income_card", "income", band("income >= 5000", 40))));
        Map<String, Object> f = fact(30);
        f.put("income", 8000);
        ScoreCardExecutor.execute(node, schema(), f);
        assertThat(((Number) f.get("riskScore")).doubleValue()).isEqualTo(90.0);
    }

    @Test
    @DisplayName("AVG: 两 card 50+40 → 45")
    void AVG_聚合() {
        ScoreCardNode node = new ScoreCardNode();
        node.setId("risk");
        node.setOutput("riskScore");
        node.setAggregation(ScoreAggregation.AVG);
        node.setCards(Arrays.asList(
                card("age_card", "age", band("age >= 25", 50)),
                card("income_card", "income", band("income >= 5000", 40))));
        Map<String, Object> f = fact(30);
        f.put("income", 8000);
        ScoreCardExecutor.execute(node, schema(), f);
        assertThat(((Number) f.get("riskScore")).doubleValue()).isEqualTo(45.0);
    }

    @Test
    @DisplayName("无命中 band → 该 card 0 分")
    void 无命中_band_零分() {
        ScoreCardNode node = new ScoreCardNode();
        node.setId("risk");
        node.setOutput("riskScore");
        node.setAggregation(ScoreAggregation.SUM);
        // age band 只有 <25,age=30 不命中 → 0
        node.setCards(Collections.singletonList(
                card("age_card", "age", band("age < 25", 20))));
        Map<String, Object> f = fact(30);
        ScoreCardExecutor.execute(node, schema(), f);
        assertThat(((Number) f.get("riskScore")).doubleValue()).isEqualTo(0.0);
    }
}
