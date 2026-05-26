package com.ruleforge.executor.service.impl;

import com.ruleforge.Utils;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.model.rule.RuleInfo;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.runtime.response.RuleExecutionResponse;
import com.ruleforge.runtime.service.KnowledgeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.mockito.Mockito;

/**
 * RuleForgeServiceImpl BDD 单元测试
 *
 * 覆盖场景：
 * 1. doTest - 正常执行规则并返回带 info 和 data 的结果 Map
 * 2. doTest - 知识包不存在时返回 null
 * 3. doTest - flowId 非空时应抛出 RuntimeException
 * 4. doTest - 带参数输入（PARAM_CATEGORY）时正确使用参数 Map
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RuleForgeServiceImpl 单元测试")
class RuleForgeServiceImplTest {

    @InjectMocks
    private RuleForgeServiceImpl ruleForgeService;

    // ------------------------------------------------------------------ //
    //  doTest - 正常规则执行
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("规则测试 - 正常执行规则应返回包含 info 和 data 的结果")
    void doTest_normalExecution_shouldReturnResultMap() throws Exception {
        // Given: 模拟 Spring 上下文返回 KnowledgeService
        ApplicationContext mockContext = mock(ApplicationContext.class);
        KnowledgeService mockKnowledgeService = mock(KnowledgeService.class);
        when(mockContext.getBean(KnowledgeService.BEAN_ID)).thenReturn(mockKnowledgeService);

        KnowledgePackage mockPackage = mock(KnowledgePackage.class);
        when(mockKnowledgeService.getKnowledge("projectA/pkg1")).thenReturn(mockPackage);

        // 模拟 KnowledgeSession 和执行响应
        KnowledgeSession mockSession = mock(KnowledgeSession.class);
        ExecutionResponseImpl response = new ExecutionResponseImpl();
        response.setFiredRules(Collections.emptyList());
        List<RuleInfo> matchedRules = new ArrayList<>();
        response.addMatchedRules(matchedRules);
        when(mockSession.fireRules()).thenReturn(response);
        // getParameters 在 buildVariableValue 中对参数分类使用，此处非参数分类不需要

        try (MockedStatic<Utils> utilsMock = Mockito.mockStatic(Utils.class, Mockito.CALLS_REAL_METHODS);
             MockedStatic<KnowledgeSessionFactory> factoryMock = mockStatic(KnowledgeSessionFactory.class)) {

            utilsMock.when(Utils::getApplicationContext).thenReturn(mockContext);
            factoryMock.when(() -> KnowledgeSessionFactory.newKnowledgeSession(mockPackage))
                    .thenReturn(mockSession);

            // 准备输入数据 - 非参数类型（普通变量分类），使用 String 类型避免 Datatype.convert 的数值解析
            List<Map<String, Object>> mapList = buildVariableCategoryInput(
                    "Customer", "com.example.Customer", "name", "名称", "String", "hello");

            // When: 调用 doTest
            Map<String, Object> result = ruleForgeService.doTest("projectA", "pkg1", null, mapList);

            // Then: 应返回非 null 结果，包含 info 和 data
            assertThat(result).isNotNull();
            assertThat(result).containsKey("info");
            assertThat(result).containsKey("data");
            assertThat((String) result.get("info")).contains("耗时");
            assertThat((String) result.get("info")).contains("匹配的规则共0个");
            assertThat((String) result.get("info")).contains("触发的规则共0个");

            verify(mockSession).insert(any(GeneralEntity.class));
            verify(mockSession).fireRules();
            verify(mockSession).writeLogFile();
        }
    }

    @Test
    @DisplayName("规则测试 - 知识包不存在时应返回 null")
    void doTest_packageNotFound_shouldReturnNull() throws Exception {
        // Given: KnowledgeService 返回 null（知识包不存在）
        ApplicationContext mockContext = mock(ApplicationContext.class);
        KnowledgeService mockKnowledgeService = mock(KnowledgeService.class);
        when(mockContext.getBean(KnowledgeService.BEAN_ID)).thenReturn(mockKnowledgeService);
        when(mockKnowledgeService.getKnowledge("projectB/pkg2")).thenReturn(null);

        try (MockedStatic<Utils> utilsMock = Mockito.mockStatic(Utils.class, Mockito.CALLS_REAL_METHODS)) {
            utilsMock.when(Utils::getApplicationContext).thenReturn(mockContext);

            List<Map<String, Object>> mapList = new ArrayList<>();

            // When: 调用 doTest
            Map<String, Object> result = ruleForgeService.doTest("projectB", "pkg2", null, mapList);

            // Then: 知识包不存在，返回 null
            assertThat(result).isNull();
        }
    }

    @Test
    @DisplayName("规则测试 - flowId 非空时应抛出 RuntimeException")
    void doTest_withFlowId_shouldThrowException() throws Exception {
        // Given: 准备 Spring 上下文和有效的知识包
        ApplicationContext mockContext = mock(ApplicationContext.class);
        KnowledgeService mockKnowledgeService = mock(KnowledgeService.class);
        when(mockContext.getBean(KnowledgeService.BEAN_ID)).thenReturn(mockKnowledgeService);

        KnowledgePackage mockPackage = mock(KnowledgePackage.class);
        when(mockKnowledgeService.getKnowledge("projectC/pkg3")).thenReturn(mockPackage);

        KnowledgeSession mockSession = mock(KnowledgeSession.class);

        try (MockedStatic<Utils> utilsMock = Mockito.mockStatic(Utils.class, Mockito.CALLS_REAL_METHODS);
             MockedStatic<KnowledgeSessionFactory> factoryMock = mockStatic(KnowledgeSessionFactory.class)) {

            utilsMock.when(Utils::getApplicationContext).thenReturn(mockContext);
            factoryMock.when(() -> KnowledgeSessionFactory.newKnowledgeSession(mockPackage))
                    .thenReturn(mockSession);

            List<Map<String, Object>> mapList = new ArrayList<>();

            // When & Then: 传入 flowId 应抛出 RuntimeException
            assertThatThrownBy(() -> ruleForgeService.doTest("projectC", "pkg3", "flow-1", mapList))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Flowable");
        }
    }

    @Test
    @DisplayName("规则测试 - 带参数分类输入时应创建 HashMap 实体并使用 fireRules(params)")
    void doTest_withParamCategory_shouldUseParameterMap() throws Exception {
        // Given: 模拟 Spring 上下文
        ApplicationContext mockContext = mock(ApplicationContext.class);
        KnowledgeService mockKnowledgeService = mock(KnowledgeService.class);
        when(mockContext.getBean(KnowledgeService.BEAN_ID)).thenReturn(mockKnowledgeService);

        KnowledgePackage mockPackage = mock(KnowledgePackage.class);
        when(mockKnowledgeService.getKnowledge("projectD/pkg4")).thenReturn(mockPackage);

        KnowledgeSession mockSession = mock(KnowledgeSession.class);
        ExecutionResponseImpl response = new ExecutionResponseImpl();
        response.setFiredRules(Collections.emptyList());
        when(mockSession.fireRules(any(Map.class))).thenReturn(response);
        when(mockSession.getParameters()).thenReturn(new HashMap<>());

        try (MockedStatic<Utils> utilsMock = Mockito.mockStatic(Utils.class, Mockito.CALLS_REAL_METHODS);
             MockedStatic<KnowledgeSessionFactory> factoryMock = mockStatic(KnowledgeSessionFactory.class)) {

            utilsMock.when(Utils::getApplicationContext).thenReturn(mockContext);
            factoryMock.when(() -> KnowledgeSessionFactory.newKnowledgeSession(mockPackage))
                    .thenReturn(mockSession);

            // 准备参数分类输入
            List<Map<String, Object>> mapList = buildVariableCategoryInput(
                    VariableCategory.PARAM_CATEGORY, "java.util.HashMap",
                    "threshold", "阈值", "String", "100");

            // When: 调用 doTest
            Map<String, Object> result = ruleForgeService.doTest("projectD", "pkg4", null, mapList);

            // Then: 应使用 fireRules(params) 重载
            assertThat(result).isNotNull();
            verify(mockSession).fireRules(any(Map.class));
            // 参数类型不调用 insert
            verify(mockSession, never()).insert(any());
        }
    }

    // ------------------------------------------------------------------ //
    //  Helper methods
    // ------------------------------------------------------------------ //

    /**
     * 构建模拟的 VariableCategory 输入数据列表
     *
     * @param categoryName 变量分类名称
     * @param clazz        类名
     * @param varName      变量名
     * @param varLabel     变量标签
     * @param dataType     数据类型
     * @param defaultValue 默认值
     * @return 输入数据列表
     */
    private List<Map<String, Object>> buildVariableCategoryInput(
            String categoryName, String clazz,
            String varName, String varLabel, String dataType, String defaultValue) {

        Map<String, Object> variable = new HashMap<>();
        variable.put("name", varName);
        variable.put("label", varLabel);
        variable.put("type", dataType);
        variable.put("defaultValue", defaultValue);

        List<Map<String, Object>> variables = new ArrayList<>();
        variables.add(variable);

        Map<String, Object> category = new HashMap<>();
        category.put("name", categoryName);
        category.put("clazz", clazz);
        category.put("variables", variables);

        List<Map<String, Object>> result = new ArrayList<>();
        result.add(category);
        return result;
    }
}
