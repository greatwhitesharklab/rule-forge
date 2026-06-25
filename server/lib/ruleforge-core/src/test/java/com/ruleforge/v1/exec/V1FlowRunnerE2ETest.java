package com.ruleforge.v1.exec;

import com.ruleforge.model.GeneralEntity;
import com.ruleforge.rete.test.EngineContextWirer;
import com.ruleforge.v1.ast.RuleAsset;
import com.ruleforge.v1.ast.RuleAssetIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V7.0.0 W2-6 — 端到端集成测试。
 *
 * <p>load loan_approval.json(现金贷完整决策流)→ V1FlowRunner → 4 场景验证。
 * 覆盖 W2-4(reject 终止 + Decision emit)+ W2-5(Flow runner 编排)。
 *
 * <p>flow: Start → RuleSet(准入) → ScoreCard(风险分) → DecisionTable(定价) → Decision
 */
@DisplayName("V7.0.0 W2-6 — loan_approval.json 端到端")
class V1FlowRunnerE2ETest {

    private static RuleAsset asset;

    @BeforeAll
    static void setup() throws Exception {
        EngineContextWirer.wire();
        try (InputStream in = V1FlowRunnerE2ETest.class.getResourceAsStream(
                "/com/ruleforge/v1/ast/loan_approval.json")) {
            assertThat(in).as("loan_approval.json 测试资源存在").isNotNull();
            asset = RuleAssetIO.read(in);
        }
    }

    private Map<String, Object> fact(int age, int income, int score, boolean blacklisted) {
        Map<String, Object> f = new GeneralEntity("LoanApplication");
        f.put("age", age);
        f.put("income", income);
        f.put("score", score);
        f.put("blacklisted", blacklisted);
        return f;
    }

    @Test
    @DisplayName("age=30/blacklisted=false → 风险分 50 → 定价 review")
    void 正常_30岁_风险50_review() {
        // precheck 无命中;risk: age>=25→50;pricing: riskScore50 不<30 → catch-all review
        V1FlowRunner.FlowResult r = V1FlowRunner.execute(asset, fact(30, 8000, 700, false));
        assertThat(r.rejected).isFalse();
        assertThat(r.decision).isEqualTo("review");
    }

    @Test
    @DisplayName("blacklisted=true → 准入 REJECT BLACKLIST,流程终止")
    void 黑名单_REJECT() {
        V1FlowRunner.FlowResult r = V1FlowRunner.execute(asset, fact(30, 8000, 700, true));
        assertThat(r.rejected).isTrue();
        assertThat(r.rejectReason).isEqualTo("BLACKLIST");
        assertThat(r.decision).isEqualTo("reject");
    }

    @Test
    @DisplayName("age=16 → 未成年 REJECT UNDERAGE,流程终止")
    void 未成年_REJECT() {
        V1FlowRunner.FlowResult r = V1FlowRunner.execute(asset, fact(16, 8000, 700, false));
        assertThat(r.rejected).isTrue();
        assertThat(r.rejectReason).isEqualTo("UNDERAGE");
        assertThat(r.decision).isEqualTo("reject");
    }

    @Test
    @DisplayName("age=20 → 风险分 20(年轻低分)→ 定价 approve")
    void 年轻_风险20_approve() {
        // risk: age<25→20;pricing: riskScore20<30 → r1 approve
        V1FlowRunner.FlowResult r = V1FlowRunner.execute(asset, fact(20, 8000, 700, false));
        assertThat(r.rejected).isFalse();
        assertThat(r.decision).isEqualTo("approve");
    }
}
