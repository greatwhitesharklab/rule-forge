package com.ruleforge.decision.service.impl;

import com.ruleforge.decision.dto.DecisionRequest;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Feature: DecisionServiceImpl - 构造 Flowable process variables
 *
 * <p>业务背景: /api/loan/evaluate 真实流量走 Flowable BPMN 路径 —
 * {@code DecisionServiceImpl.executeDecisionFlow()} 调
 * {@code runtimeService.startProcessInstanceByKey(flowId, params)} 启动流程,
 * BPMN 里 {@code rule-task} 通过 {@code RuleServiceTaskDelegate} 拿到这些 params 当
 * facts,塞进规则引擎 session。
 *
 * <p>本测试覆盖 {@link DecisionServiceImpl#buildProcessVariables(DecisionRequest)} —
 * 它的契约:
 * <ul>
 *   <li>{@code request.applicant} 作为 {@code Map<String,Object>}(serializable)塞
 *       {@code params["applicant"]};delegate 拿到后转 {@code ApplicantModel}
 *       entity 再 insert session</li>
 *   <li>{@code request.order} 同样以 Map 形式塞 {@code params["order"]}</li>
 *   <li>{@code loanZone} / {@code orbitCode} 走透传(原行为)</li>
 *   <li>applicant/order 没传 → 不塞对应 key(delegate 走 lazy 兜底)</li>
 * </ul>
 *
 * <p><b>为什么用 Map 而非直接传 entity?</b> Flowable 把 process variables
 * 序列化到 {@code act_ru_variable} BLOB 列,要求 {@code Serializable}。
 * {@code LazyGeneralEntity} 持有 Spring bean {@code DatasourceRoutingProvider},
 * 不可序列化,会抛 {@code NotSerializableException}。所以 entity 转换挪到
 * delegate 里做,Flowable 这层只过 Map。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DecisionServiceImpl - Flowable process variables 构造")
class DecisionServiceImplFlowVariablesTest {

    private DecisionServiceImpl service;

    @BeforeEach
    void setUp() {
        // buildProcessVariables() 不依赖任何外部 bean,直接 new 出即可
        service = new DecisionServiceImpl(
                mock(com.ruleforge.decision.lazy.LazyEntityFactory.class),
                mock(com.ruleforge.decision.service.IRuleVariableDefService.class),
                mock(com.ruleforge.decision.service.IDecisionLogService.class),
                mock(com.ruleforge.decision.service.IShadowConfigService.class),
                mock(com.ruleforge.decision.service.IShadowExecutionService.class),
                mock(com.ruleforge.decision.service.IGrayStrategyService.class),
                mock(RuntimeService.class),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                // V5.20+: 自建决策流执行器 — buildProcessVariables 测不到这些,但构造器要
                mock(com.ruleforge.decision.flow.engine.FlowEngine.class),
                mock(com.ruleforge.decision.flow.engine.FlowDefinitionRepo.class)
        );
    }

    @Nested
    @DisplayName("applicant / order facts 注入")
    class FactsAsMap {

        @Test
        @DisplayName("传 applicant Map 时,params['applicant'] 是 Map(待 delegate 转 entity)")
        void shouldInjectApplicantAsMap() {
            // Given
            DecisionRequest req = new DecisionRequest();
            req.setUserId("u001");
            Map<String, Object> applicant = new HashMap<>();
            applicant.put("age", 25);
            applicant.put("income", 8000.0);
            req.setApplicant(applicant);

            // When
            Map<String, Object> vars = service.buildProcessVariables(req);

            // Then
            assertThat(vars).containsKey("applicant");
            assertThat(vars.get("applicant")).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> passed = (Map<String, Object>) vars.get("applicant");
            assertThat(passed).containsEntry("age", 25).containsEntry("income", 8000.0);
        }

        @Test
        @DisplayName("传 order Map 时,params['order'] 是 Map(待 delegate 转 entity)")
        void shouldInjectOrderAsMap() {
            // Given
            DecisionRequest req = new DecisionRequest();
            req.setUserId("u001");
            Map<String, Object> order = new HashMap<>();
            order.put("amount", 5000.0);
            order.put("product", "PERSONAL_LOAN");
            req.setOrder(order);

            // When
            Map<String, Object> vars = service.buildProcessVariables(req);

            // Then
            assertThat(vars).containsKey("order");
            assertThat(vars.get("order")).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> passed = (Map<String, Object>) vars.get("order");
            assertThat(passed).containsEntry("amount", 5000.0).containsEntry("product", "PERSONAL_LOAN");
        }

        @Test
        @DisplayName("applicant + order 都传时,两个 key 都进 params")
        void shouldInjectBothWhenBothProvided() {
            // Given
            DecisionRequest req = new DecisionRequest();
            req.setUserId("u001");
            Map<String, Object> applicant = new HashMap<>();
            applicant.put("age", 25);
            req.setApplicant(applicant);
            Map<String, Object> order = new HashMap<>();
            order.put("amount", 5000.0);
            req.setOrder(order);

            // When
            Map<String, Object> vars = service.buildProcessVariables(req);

            // Then
            assertThat(vars).containsKeys("applicant", "order");
            assertThat(vars.get("applicant")).isInstanceOf(Map.class);
            assertThat(vars.get("order")).isInstanceOf(Map.class);
        }
    }

    @Nested
    @DisplayName("applicant / order 缺省")
    class FactsMissing {

        @Test
        @DisplayName("applicant=null 时,params 不含 'applicant' key(lazy 兜底走 delegate)")
        void shouldOmitApplicantWhenNull() {
            // Given
            DecisionRequest req = new DecisionRequest();
            req.setUserId("u001");
            // applicant 不设

            // When
            Map<String, Object> vars = service.buildProcessVariables(req);

            // Then
            assertThat(vars).doesNotContainKey("applicant");
        }

        @Test
        @DisplayName("order 是空 Map 时,params 不含 'order' key(避免无意义空 map)")
        void shouldOmitOrderWhenEmpty() {
            // Given
            DecisionRequest req = new DecisionRequest();
            req.setUserId("u001");
            req.setOrder(new HashMap<>());

            // When
            Map<String, Object> vars = service.buildProcessVariables(req);

            // Then
            assertThat(vars).doesNotContainKey("order");
        }
    }

    @Nested
    @DisplayName("loanZone / orbitCode 透传(原行为保留)")
    class ZoneForwarding {

        @Test
        @DisplayName("loanZone + orbitCode 仍以 String 形式塞 params,跟原 executeDecisionFlow 一致")
        void shouldForwardLoanZoneAndOrbitCode() {
            // Given
            DecisionRequest req = new DecisionRequest();
            req.setUserId("u001");
            req.setLoanZone("ORBIT");
            req.setOrbitCode("OC-007");

            // When
            Map<String, Object> vars = service.buildProcessVariables(req);

            // Then
            assertThat(vars).containsEntry("loanZone", "ORBIT");
            assertThat(vars).containsEntry("orbitCode", "OC-007");
        }
    }
}
