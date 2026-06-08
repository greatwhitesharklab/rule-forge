package com.ruleforge.executor.controller;

import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.cache.CacheUtils;
import com.ruleforge.runtime.cache.KnowledgeCache;
import com.ruleforge.runtime.response.RuleExecutionResponse;
import com.ruleforge.runtime.service.KnowledgeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * TestController BDD 单元测试
 *
 * 覆盖场景：
 * 1. doTest - 规则执行应返回响应字符串(V5.21+: 1 参,纯规则路径,无 Flowable 分支)
 * 2. doTest - knowledgeService 找不到包时应抛异常
 * 3. knowledge - 传入 packageId 应标记知识包为 dirty
 * 4. knowledge - 传入 null packageId 不应调用 markKnowledgeDirty
 *
 * 历史:曾有 `flow` 参数分支走 Flowable RuntimeService,V5.21 PR3 已删。
 * 决策流测试改走 console-app `/doTest`(FlowEngine.start)或 executor-app
 * DecisionServiceImpl.executeDecisionFlow 主路径。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TestController 单元测试")
class TestControllerTest {

    @Mock
    private KnowledgeService knowledgeService;

    @InjectMocks
    private TestController testController;

    // ------------------------------------------------------------------ //
    //  doTest
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("规则执行测试 - 正常执行规则并返回结果")
    void doTest_shouldExecuteRules() throws Exception {
        // Given: knowledgeService 返回有效的 KnowledgePackage
        KnowledgePackage mockPackage = mock(KnowledgePackage.class);
        KnowledgeSession mockSession = mock(KnowledgeSession.class);
        RuleExecutionResponse mockResponse = mock(RuleExecutionResponse.class);
        String expectedResponse = "rules executed successfully";

        when(knowledgeService.getKnowledge("projectA/pkg1")).thenReturn(mockPackage);
        when(mockResponse.toString()).thenReturn(expectedResponse);

        try (MockedStatic<KnowledgeSessionFactory> factoryMock = mockStatic(KnowledgeSessionFactory.class)) {
            factoryMock.when(() -> KnowledgeSessionFactory.newKnowledgeSession(mockPackage))
                    .thenReturn(mockSession);
            when(mockSession.fireRules()).thenReturn(mockResponse);

            // When: 调用 doTest(单参,无 flow 字段)
            String result = testController.doTest("projectA/pkg1");

            // Then: 应返回规则执行结果
            assertThat(result).isEqualTo(expectedResponse);
            verify(knowledgeService).getKnowledge("projectA/pkg1");
            verify(mockSession).fireRules();
        }
    }

    @Test
    @DisplayName("规则执行测试 - 知识包不存在时 getKnowledge 应抛出异常")
    void doTest_packageNotFound_shouldPropagateException() throws Exception {
        // Given: knowledgeService.getKnowledge 抛出 IOException
        when(knowledgeService.getKnowledge("nonexistent")).thenThrow(new RuntimeException("Package not found"));

        // When & Then: 调用 doTest 应抛出异常
        assertThatThrownBy(() -> testController.doTest("nonexistent"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Package not found");
    }

    // ------------------------------------------------------------------ //
    //  knowledge
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("知识包标记 - 传入 packageId 应标记对应知识包为 dirty")
    void knowledge_withPackageId_shouldMarkDirty() {
        // Given: 准备 params 和 mock cache
        Map<String, String> params = new HashMap<>();
        params.put("packageId", "projectA/pkg1");

        KnowledgeCache mockCache = mock(KnowledgeCache.class);

        try (MockedStatic<CacheUtils> cacheUtilsMock = mockStatic(CacheUtils.class)) {
            cacheUtilsMock.when(CacheUtils::getKnowledgeCache).thenReturn(mockCache);

            // When: 调用 knowledge
            testController.knowledge(params);

            // Then: 应标记知识包为 dirty
            verify(mockCache).markKnowledgeDirty("projectA/pkg1");
        }
    }

    @Test
    @DisplayName("知识包标记 - packageId 为 null 时不应调用 markKnowledgeDirty")
    void knowledge_nullPackageId_shouldNotMarkDirty() {
        // Given: params 中没有 packageId
        Map<String, String> params = new HashMap<>();

        try (MockedStatic<CacheUtils> cacheUtilsMock = mockStatic(CacheUtils.class)) {
            KnowledgeCache mockCache = mock(KnowledgeCache.class);
            cacheUtilsMock.when(CacheUtils::getKnowledgeCache).thenReturn(mockCache);

            // When: 调用 knowledge
            testController.knowledge(params);

            // Then: 不应调用 markKnowledgeDirty
            verify(mockCache, never()).markKnowledgeDirty(anyString());
        }
    }
}
