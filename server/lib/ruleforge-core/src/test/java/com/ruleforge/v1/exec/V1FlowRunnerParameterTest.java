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
 * V7.4 — 参数库(pl)动态右值 BDD。
 *
 * <p>规则 condition {@code "riskScore >= param.riskThreshold"} 右值引用参数库
 * (ParameterValue,走 WorkingMemory.getParameters 通道,非 fact 字段)。
 * V1FlowRunner.execute 接收 parameters Map → RuleSet fireRules(parameters) 注入。
 *
 * <p>覆盖:CEL SELECT(param.xxx)→ CelCriteriaTranslator 产 ParameterValue →
 * RETE criteria 右值运行时从会话参数取(动态右值,非字面量/非预处理替换)。
 */
@DisplayName("V7.4 参数库动态右值(param.riskThreshold)")
class V1FlowRunnerParameterTest {

    private static RuleAsset asset;

    @BeforeAll
    static void setup() throws Exception {
        EngineContextWirer.wire();
        try (InputStream in = V1FlowRunnerParameterTest.class.getResourceAsStream(
                "/com/ruleforge/v1/ast/parameter_loan.json")) {
            assertThat(in).as("parameter_loan.json 测试资源存在").isNotNull();
            asset = RuleAssetIO.read(in);
        }
    }

    private Map<String, Object> fact(int riskScore) {
        Map<String, Object> f = new GeneralEntity("LoanApplication");
        f.put("riskScore", riskScore);
        return f;
    }

    private Map<String, Object> params(int threshold) {
        Map<String, Object> p = new HashMap<>();
        p.put("riskThreshold", threshold);
        return p;
    }

    // Given riskScore=60, param.riskThreshold=55 When 执行 Then 60>=55 命中 → approve
    @Test
    @DisplayName("riskScore(60) >= param.riskThreshold(55) → 命中 → approve")
    void 高于参数阈值_命中_approve() {
        V1FlowRunner.FlowResult r = V1FlowRunner.execute(asset, fact(60), params(55));
        assertThat(r.rejected).isFalse();
        assertThat(r.decision).isEqualTo("approve");
    }

    // Given riskScore=50, param.riskThreshold=55 When 执行 Then 50>=55 不命中 → default review
    @Test
    @DisplayName("riskScore(50) < param.riskThreshold(55) → 不命中 → default review")
    void 低于参数阈值_不命中_review() {
        V1FlowRunner.FlowResult r = V1FlowRunner.execute(asset, fact(50), params(55));
        assertThat(r.rejected).isFalse();
        assertThat(r.decision).isEqualTo("review");
    }

    // Given 同规则但参数阈值改成 45 When riskScore=50 Then 50>=45 命中 → approve(参数驱动,规则文件不变)
    @Test
    @DisplayName("改参数阈值(55→45)同规则同 fact → 命中翻转(参数库价值:不重发版改阈值)")
    void 改参数_同规则_命中翻转() {
        assertThat(V1FlowRunner.execute(asset, fact(50), params(55)).decision).isEqualTo("review");
        assertThat(V1FlowRunner.execute(asset, fact(50), params(45)).decision).isEqualTo("approve");
    }

    // Given 常量库 const.minScore(选项 A:cl 复用 pl 参数通道) When riskScore=60/40 Then 60>=50 命中 approve / 40 不命中 review
    @Test
    @DisplayName("const.minScore 动态右值(常量库,复用 param 参数通道)")
    void 常量库_const_动态右值() throws Exception {
        RuleAsset constAsset;
        try (InputStream in = V1FlowRunnerParameterTest.class.getResourceAsStream(
                "/com/ruleforge/v1/ast/const_loan.json")) {
            assertThat(in).as("const_loan.json 测试资源存在").isNotNull();
            constAsset = RuleAssetIO.read(in);
        }
        Map<String, Object> p = new HashMap<>();
        p.put("minScore", 50);
        assertThat(V1FlowRunner.execute(constAsset, fact(60), p).decision).isEqualTo("approve");
        assertThat(V1FlowRunner.execute(constAsset, fact(40), p).decision).isEqualTo("review");
    }
}
