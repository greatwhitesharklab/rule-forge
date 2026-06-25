package com.ruleforge.v1.exec;

import com.ruleforge.model.GeneralEntity;
import com.ruleforge.rete.test.EngineContextWirer;
import com.ruleforge.v1.ast.RuleAsset;
import com.ruleforge.v1.ast.RuleAssetIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V7.1 W7.1-1 — exclusiveGateway 分流执行 BDD。
 *
 * <p>flow: Start → ScoreCard(算 riskScore) → <b>exclusiveGateway</b>(风险网关)
 * <br>网关 3 出边:
 * <ul>
 *   <li>{@code f_high}(riskScore &gt;= 50) → t_approve(SET_DECISION approve)</li>
 *   <li>{@code f_review}(riskScore &gt;= 30) → t_review(SET_DECISION review)</li>
 *   <li>{@code f_low}(无 condition, gateway.defaultFlow) → t_reject(SET_DECISION reject)</li>
 * </ul>
 *
 * <p>覆盖 BPMN exclusiveGateway 语义:出边 CEL 条件**首个命中**即走;**全部不命中**走
 * {@code defaultFlow} 兜底。这要求 V1FlowRunner 从线性链遍历升级为**图遍历**
 * (一节点多出边 + 条件评估),不再用 {@code orderNodes} 预排序。
 */
@DisplayName("V7.1 W7.1-1 — exclusiveGateway 分流执行")
class V1FlowRunnerGatewayTest {

    private static RuleAsset asset;

    @BeforeAll
    static void setup() throws Exception {
        EngineContextWirer.wire();
        try (InputStream in = V1FlowRunnerGatewayTest.class.getResourceAsStream(
                "/com/ruleforge/v1/ast/gateway_loan.json")) {
            assertThat(in).as("gateway_loan.json 测试资源存在").isNotNull();
            asset = RuleAssetIO.read(in);
        }
    }

    private Map<String, Object> fact(int age) {
        Map<String, Object> f = new GeneralEntity("LoanApplication");
        f.put("age", age);
        return f;
    }

    // Given age=40 When 执行 gateway 流 Then ScoreCard b3 命中 riskScore=60 → f_high(riskScore>=50) → approve
    @Test
    @DisplayName("高分(riskScore=60)→ 命中首条出边 f_high → approve")
    void 高分_命中首条出边_approve() {
        V1FlowRunner.FlowResult r = V1FlowRunner.execute(asset, fact(40));
        assertThat(r.rejected).isFalse();
        assertThat(r.decision).isEqualTo("approve");
    }

    // Given age=30 When 执行 Then riskScore=40 → f_high 不命中, f_review(riskScore>=30) 命中 → review
    @Test
    @DisplayName("中分(riskScore=40)→ f_high 不命中, 首个命中 f_review → review")
    void 中分_跳过不命中_选首个命中_review() {
        V1FlowRunner.FlowResult r = V1FlowRunner.execute(asset, fact(30));
        assertThat(r.rejected).isFalse();
        assertThat(r.decision).isEqualTo("review");
    }

    // Given age=20 When 执行 Then riskScore=20 → f_high/f_review 都不命中 → defaultFlow(f_low) → reject
    @Test
    @DisplayName("低分(riskScore=20)→ 所有条件出边不命中 → defaultFlow 兜底 → reject")
    void 低分_全不命中_defaultFlow兜底_reject() {
        V1FlowRunner.FlowResult r = V1FlowRunner.execute(asset, fact(20));
        assertThat(r.rejected).isFalse();
        assertThat(r.decision).isEqualTo("reject");
    }
}
