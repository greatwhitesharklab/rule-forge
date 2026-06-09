package com.ruleforge.decision.service.impl;

import com.ruleforge.decision.lazy.LazyEntityFactory;
import com.ruleforge.decision.lazy.LazyGeneralEntity;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Feature: 决策引擎 hybrid facts 注入 (eager + lazy)
 *
 * <p>业务背景: 决策引擎收请求时,facts 数据从两条路径注入:
 * <ul>
 *   <li><b>eager</b> — 请求体 applicant / order 字段,业务系统调用时已知,直接当
 *       initialValues 塞进 {@link LazyGeneralEntity}({@link LazyEntityFactory}
 *       的 3 参 overload 内部会对每个 fact 调 {@code entity.put},
 *       标记 loadedProperties,后续 rule 读同名字段不查 DataSource)</li>
 *   <li><b>lazy</b> — 业务系统没传的字段,
 *       {@link LazyGeneralEntity#get(Object)} 走 DataSourceProvider.fetchFieldValue()</li>
 * </ul>
 * 规则 DSL 写 {@code applicant.age} 即可,字段从哪来由 hybrid 机制自动决定。
 *
 * <p>本测试覆盖 {@link DecisionServiceImpl#injectFacts(String, String, Map)} —
 * 它的契约很简单:facts 非空 → 调 3 参 overload;facts 空 → 调 2 参 overload。
 * fact 真正落到 entity 的 {@code put} 行为由
 * {@link LazyEntityFactory#createLazyEntity(String, String, Map)} 负责
 * (那个方法是已有实现,本测试不重复覆盖)。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DecisionServiceImpl - hybrid facts 注入")
class DecisionServiceImplFactsInjectionTest {

    @Mock private LazyEntityFactory lazyEntityFactory;
    @Mock private LazyGeneralEntity mockEntity;

    private DecisionServiceImpl service;

    @BeforeEach
    void setUp() {
        // injectFacts() 不依赖其它服务,只需要 lazyEntityFactory
        // V5.21+: 删 mock(org.flowable.engine.RuntimeService.class) — evaluate path
        // 走自建 FlowEngine,不再持有 Flowable RuntimeService 引用
        service = new DecisionServiceImpl(
                lazyEntityFactory,
                mock(com.ruleforge.decision.service.IRuleVariableDefService.class),
                mock(com.ruleforge.decision.service.IDecisionLogService.class),
                mock(com.ruleforge.decision.service.IShadowConfigService.class),
                mock(com.ruleforge.decision.service.IShadowExecutionService.class),
                mock(com.ruleforge.decision.service.IGrayStrategyService.class),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                // V5.20+ 自建决策流执行器 — injectFacts 测不到这些,但构造器要
                mock(com.ruleforge.decision.flow.engine.FlowEngine.class),
                mock(com.ruleforge.decision.flow.engine.FlowDefinitionRepo.class)
        );
    }

    @Nested
    @DisplayName("eager 路径 — facts 非空时")
    class EagerFacts {

        @Test
        @DisplayName("应调用 3 参 overload,initialValues 等于传入的 facts")
        void shouldCallThreeArgOverloadWithFacts() {
            // Given
            when(lazyEntityFactory.createLazyEntity(anyString(), anyString(), any(Map.class)))
                    .thenReturn(mockEntity);
            Map<String, Object> facts = new HashMap<>();
            facts.put("age", 25);
            facts.put("income", 8000.0);

            // When
            LazyGeneralEntity result = service.injectFacts(
                    "com.ruleforge.decision.model.ApplicantModel", "u001", facts);

            // Then
            assertThat(result).isSameAs(mockEntity);
            verify(lazyEntityFactory).createLazyEntity(
                    eq("com.ruleforge.decision.model.ApplicantModel"),
                    eq("u001"),
                    argThat(map -> map != null
                            && Integer.valueOf(25).equals(map.get("age"))
                            && Double.valueOf(8000.0).equals(map.get("income"))));
            // 2 参 overload 不该被调
            verify(lazyEntityFactory, never()).createLazyEntity(anyString(), anyString());
        }

        @Test
        @DisplayName("单字段 facts(只 1 个 key)也应走 3 参 overload,不降级")
        void shouldNotDegradeSingleFieldToTwoArg() {
            // Given
            when(lazyEntityFactory.createLazyEntity(anyString(), anyString(), any(Map.class)))
                    .thenReturn(mockEntity);
            Map<String, Object> facts = new HashMap<>();
            facts.put("age", 25);

            // When
            service.injectFacts(
                    "com.ruleforge.decision.model.ApplicantModel", "u001", facts);

            // Then 仍走 3 参,initialValues 含 1 个 key
            verify(lazyEntityFactory).createLazyEntity(
                    eq("com.ruleforge.decision.model.ApplicantModel"),
                    eq("u001"),
                    argThat(map -> map != null && map.size() == 1 && Integer.valueOf(25).equals(map.get("age"))));
        }
    }

    @Nested
    @DisplayName("lazy 路径 — facts 为空时")
    class LazyFallback {

        @Test
        @DisplayName("facts=null 时只创建 entity,走 2 参 overload(全部 lazy)")
        void shouldCallTwoArgOverloadWhenFactsNull() {
            // Given
            when(lazyEntityFactory.createLazyEntity(anyString(), anyString())).thenReturn(mockEntity);

            // When
            LazyGeneralEntity result = service.injectFacts(
                    "com.ruleforge.decision.model.ApplicantModel", "u001", null);

            // Then
            assertThat(result).isSameAs(mockEntity);
            verify(lazyEntityFactory).createLazyEntity(
                    eq("com.ruleforge.decision.model.ApplicantModel"), eq("u001"));
            // 3 参 overload 不该被调
            verify(lazyEntityFactory, never())
                    .createLazyEntity(anyString(), anyString(), any(Map.class));
        }

        @Test
        @DisplayName("facts 是空 Map 时同样走 2 参 overload(lazy 兜底)")
        void shouldCallTwoArgOverloadWhenFactsEmpty() {
            // Given
            when(lazyEntityFactory.createLazyEntity(anyString(), anyString())).thenReturn(mockEntity);

            // When
            service.injectFacts(
                    "com.ruleforge.decision.model.ApplicantModel", "u001", new HashMap<>());

            // Then
            verify(lazyEntityFactory).createLazyEntity(
                    eq("com.ruleforge.decision.model.ApplicantModel"), eq("u001"));
            verify(lazyEntityFactory, never())
                    .createLazyEntity(anyString(), anyString(), any(Map.class));
        }
    }

    @Nested
    @DisplayName("clazz/userId 透传")
    class ParamForwarding {

        @Test
        @DisplayName("ApplicantModel 和 OrderModel 都应透传到 factory,userId 用请求里的")
        void shouldForwardClazzAndUserId() {
            // Given
            when(lazyEntityFactory.createLazyEntity(anyString(), anyString(), any(Map.class)))
                    .thenReturn(mockEntity);
            Map<String, Object> orderFacts = new HashMap<>();
            orderFacts.put("amount", 5000.0);

            // When 调两次:applicant 一次,order 一次
            service.injectFacts("com.ruleforge.decision.model.ApplicantModel", "u001",
                    new HashMap<>(Map.of("age", 25)));
            service.injectFacts("com.ruleforge.decision.model.OrderModel", "u001", orderFacts);

            // Then 两次调用 clazz/userId 都正确
            verify(lazyEntityFactory).createLazyEntity(
                    eq("com.ruleforge.decision.model.ApplicantModel"), eq("u001"), any(Map.class));
            verify(lazyEntityFactory).createLazyEntity(
                    eq("com.ruleforge.decision.model.OrderModel"), eq("u001"), any(Map.class));
        }
    }
}
