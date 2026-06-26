package com.ruleforge.v1.exec;

import com.ruleforge.model.GeneralEntity;
import com.ruleforge.rete.test.EngineContextWirer;
import com.ruleforge.v1.ast.Action;
import com.ruleforge.v1.ast.ActionType;
import com.ruleforge.v1.ast.HitPolicy;
import com.ruleforge.v1.ast.NodeBase;
import com.ruleforge.v1.ast.Rule;
import com.ruleforge.v1.ast.RuleAsset;
import com.ruleforge.v1.ast.RuleAssetIO;
import com.ruleforge.v1.ast.RuleSetNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V7.5 — 规则独立文件(ruleRef 引用)BDD。
 *
 * <p>决策流节点 {@code ruleRef="precheck"}(不内嵌 rules)→ V1FlowRunner 从 ruleFiles Map 加载
 * 规则文件内容(precheck RuleSetNode)执行。规则独立文件 → 跨流程复用 + 独立 git diff。
 */
@DisplayName("V7.5 规则独立文件(ruleRef 引用)")
class V1FlowRunnerRuleRefTest {

    private static RuleAsset asset;

    @BeforeAll
    static void setup() throws Exception {
        EngineContextWirer.wire();
        try (InputStream in = V1FlowRunnerRuleRefTest.class.getResourceAsStream(
                "/com/ruleforge/v1/ast/rule_ref_loan.json")) {
            assertThat(in).as("rule_ref_loan.json 测试资源存在").isNotNull();
            asset = RuleAssetIO.read(in);
        }
    }

    private static Map<String, Object> fact(int age) {
        Map<String, Object> f = new GeneralEntity("LoanApplication");
        f.put("age", age);
        return f;
    }

    /** ruleFiles:precheck → RuleSetNode(age<18 REJECT UNDERAGE,模拟独立规则文件内容)。 */
    private static Map<String, NodeBase> ruleFiles() {
        Map<String, NodeBase> rf = new HashMap<>();
        RuleSetNode precheck = new RuleSetNode();
        precheck.setId("precheck");
        precheck.setName("准入");
        precheck.setHitPolicy(HitPolicy.FIRST_MATCH);
        Rule r1 = new Rule();
        r1.setId("r1");
        r1.setCondition("age < 18");
        Action a = new Action(ActionType.REJECT);
        a.setReason("UNDERAGE");
        r1.setActions(Collections.singletonList(a));
        precheck.setRules(Collections.singletonList(r1));
        rf.put("precheck", precheck);
        return rf;
    }

    // Given 决策流 RuleSet 节点 ruleRef=precheck(不内嵌)+ ruleFiles{precheck:age<18 REJECT}
    // When age=16 Then 命中 precheck age<18 → REJECT UNDERAGE(规则从 ruleFiles 加载)
    @Test
    @DisplayName("ruleRef 引用规则文件:age=16 → precheck REJECT UNDERAGE")
    void ruleRef_从ruleFiles加载_age16_reject() {
        V1FlowRunner.FlowResult r = V1FlowRunner.execute(asset, fact(16), null, ruleFiles());
        assertThat(r.rejected).isTrue();
        assertThat(r.rejectReason).isEqualTo("UNDERAGE");
    }

    @Test
    @DisplayName("ruleRef 引用:age=30 → precheck 不命中 → approve(default)")
    void ruleRef_age30_approve() {
        V1FlowRunner.FlowResult r = V1FlowRunner.execute(asset, fact(30), null, ruleFiles());
        assertThat(r.rejected).isFalse();
        assertThat(r.decision).isEqualTo("approve");
    }
}
