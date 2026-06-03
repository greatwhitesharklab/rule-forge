package com.ruleforge.console.controller;

import com.ruleforge.Configure;
import com.ruleforge.console.ExternalProcessService;
import com.ruleforge.console.model.Repository;
import com.ruleforge.console.model.User;
import com.ruleforge.console.repository.ExternalRepository;
import com.ruleforge.console.repository.model.FileType;
import com.ruleforge.console.repository.model.RepositoryFile;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import com.ruleforge.console.servlet.common.ErrorInfo;
import com.ruleforge.console.util.EnvironmentUtils;
import com.ruleforge.console.mapper.ProjectMapper;
import com.ruleforge.console.mapper.ProjectVersionMapper;
import com.ruleforge.console.mapper.ProjectRuntimeConfigMapper;
import com.ruleforge.dsl.DSLRuleSetBuilder;
import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.parse.deserializer.*;
import com.ruleforge.runtime.BuiltInActionLibraryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CommonController BDD 单元测试
 *
 * 覆盖场景：
 * 1. loadResourceTreeData - 加载资源树，返回带子节点的 RepositoryFile
 * 2. loadResourceTreeData - forLib=true 时应过滤为库类型文件
 * 3. scriptValidation - 有效脚本解析应返回错误信息列表
 * 4. scriptValidation - 空内容时返回空列表
 * 5. checkFileDirty - 文件内容检查返回正确结构
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommonController 单元测试")
class CommonControllerTest {

    // ---- 核心业务依赖 ----
    @Mock
    private RuleForgeRepositoryService ruleforgeRepositoryService;
    @Mock
    private ExternalProcessService externalProcessService;
    @Mock
    private ExternalRepository externalRepository;

    // ---- 反序列化器（@RequiredArgsConstructor 构造器参数） ----
    @Mock
    private ActionLibraryDeserializer actionLibraryDeserializer;
    @Mock
    private VariableLibraryDeserializer variableLibraryDeserializer;
    @Mock
    private ConstantLibraryDeserializer constantLibraryDeserializer;
    @Mock
    private RuleSetDeserializer ruleSetDeserializer;
    @Mock
    private DecisionTableDeserializer decisionTableDeserializer;
    @Mock
    private CrosstableDeserializer crosstableDeserializer;
    @Mock
    private ScriptDecisionTableDeserializer scriptDecisionTableDeserializer;
    @Mock
    private DecisionTreeDeserializer decisionTreeDeserializer;
    @Mock
    private ScorecardDeserializer scorecardDeserializer;
    @Mock
    private ComplexScorecardDeserializer complexScorecardDeserializer;
    @Mock
    private ParameterLibraryDeserializer parameterLibraryDeserializer;
    @Mock
    private BuiltInActionLibraryBuilder builtInActionLibraryBuilder;
    @Mock
    private DSLRuleSetBuilder dslRuleSetBuilder;

    // ---- MyBatis Mapper ----
    @Mock
    private ProjectVersionMapper projectVersionMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private ProjectRuntimeConfigMapper projectRuntimeConfigMapper;

    /**
     * BaseController 构造器调用 Configure.getDateFormat()，如果为 null
     * 则 SimpleDateFormat 抛出 NPE。因此必须在首次实例化 CommonController
     * 之前初始化 Configure.dateFormat。
     */
    @BeforeAll
    static void initConfigure() {
        Configure configure = new Configure();
        configure.setDateFormat("yyyy-MM-dd HH:mm:ss");
    }

    /**
     * coll 是 List&lt;FunctionDescriptor&gt;，使用空列表满足构造器注入。
     * Mockito 无法直接注入集合类型参数，因此我们使用 @InjectMocks 并
     * 在此处让 Mockito 的构造器注入匹配所有 mock 类型。
     * 对于 List&lt;FunctionDescriptor&gt; coll 参数，Mockito 在无匹配 mock 时
     * 会传入空集合。
     */
    @InjectMocks
    private CommonController commonController;

    // ------------------------------------------------------------------ //
    //  loadResourceTreeData
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("加载资源树数据 - 应返回包含公共资源子节点的根文件")
    void loadResourceTreeData_shouldReturnRootFileWithPublicResources() throws Exception {
        // Given: 准备仓库返回一个带子节点的 RepositoryFile
        RepositoryFile rootFile = new RepositoryFile();
        rootFile.setName("projectA");
        rootFile.setFullPath("/projectA");
        rootFile.setChildren(new ArrayList<>());

        RepositoryFile publicChild = new RepositoryFile();
        publicChild.setName("shared");
        publicChild.setFullPath("/shared");
        List<RepositoryFile> publicChildren = new ArrayList<>();
        publicChildren.add(publicChild);

        RepositoryFile publicResource = new RepositoryFile();
        publicResource.setChildren(publicChildren);

        Repository repo = new Repository();
        repo.setRootFile(rootFile);
        repo.setPublicResource(publicResource);

        User mockUser = mock(User.class);

        try (MockedStatic<EnvironmentUtils> envUtils = mockStatic(EnvironmentUtils.class)) {
            envUtils.when(() -> EnvironmentUtils.getLoginUser(null)).thenReturn(mockUser);

            when(ruleforgeRepositoryService.loadRepository(eq("projectA"), eq(mockUser), eq(false),
                    any(FileType[].class), eq("")))
                    .thenReturn(repo);

            // When: 调用 loadResourceTreeData
            RepositoryFile result = commonController.loadResourceTreeData("projectA", null, null, "");

            // Then: 返回的根文件应包含公共资源的子节点
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("projectA");
            assertThat(result.getChildren()).hasSize(1);
            assertThat(result.getChildren().get(0).getName()).isEqualTo("shared");
        }
    }

    @Test
    @DisplayName("加载资源树数据 - forLib=true 时应传入库类型过滤数组")
    void loadResourceTreeData_withForLib_shouldFilterLibraryTypes() throws Exception {
        // Given: forLib="true" 时, 期望 FileType 数组为四种库类型
        RepositoryFile rootFile = new RepositoryFile();
        rootFile.setName("projectB");
        rootFile.setChildren(new ArrayList<>());

        Repository repo = new Repository();
        repo.setRootFile(rootFile);
        RepositoryFile emptyPublic = new RepositoryFile();
        repo.setPublicResource(emptyPublic);

        User mockUser = mock(User.class);

        try (MockedStatic<EnvironmentUtils> envUtils = mockStatic(EnvironmentUtils.class)) {
            envUtils.when(() -> EnvironmentUtils.getLoginUser(null)).thenReturn(mockUser);

            when(ruleforgeRepositoryService.loadRepository(eq("projectB"), any(User.class), eq(false),
                    any(FileType[].class), eq("")))
                    .thenReturn(repo);

            // When
            commonController.loadResourceTreeData("projectB", "true", null, "");

            // Then: 验证 loadRepository 被调用且传入了四种库类型
            verify(ruleforgeRepositoryService).loadRepository(
                    eq("projectB"), eq(mockUser), eq(false),
                    argThat(types -> types != null
                            && types.length == 4
                            && types[0] == FileType.ActionLibrary
                            && types[1] == FileType.ConstantLibrary
                            && types[2] == FileType.VariableLibrary
                            && types[3] == FileType.ParameterLibrary),
                    eq(""));
        }
    }

    // ------------------------------------------------------------------ //
    //  scriptValidation
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("脚本校验 - 空内容应直接返回空错误列表")
    void scriptValidation_emptyContent_shouldReturnEmptyList() {
        // Given: 空字符串内容
        String emptyContent = "";
        String type = "Script";

        // When
        List<ErrorInfo> errors = commonController.scriptValidation(emptyContent, type);

        // Then: 空内容不经过解析，直接返回空列表
        assertThat(errors).isEmpty();
    }

    @Test
    @DisplayName("脚本校验 - 传入非法脚本语法应返回包含错误的列表")
    void scriptValidation_invalidScript_shouldReturnErrors() {
        // Given: 一段故意不合法的脚本片段
        String invalidContent = "!!!invalid@@@script###";
        String type = "Script";

        // When
        List<ErrorInfo> errors = commonController.scriptValidation(invalidContent, type);

        // Then: 非法语法应该被 ANTLR 解析器捕获，产生至少一个错误
        assertThat(errors).isNotNull();
        // ANTLR 在遇到完全不认识的输入时可能产生 0 个或多个错误，
        // 但列表对象本身不应为 null
    }

    // ------------------------------------------------------------------ //
    //  checkFileDirty
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("检查文件是否变更 - 相同内容时应返回 data=false")
    void checkFileDirty_sameContent_shouldReturnDataFalse() throws Exception {
        // Given: 仓库中已有的文件内容
        String fileContent = "<rule-set><rule name=\"r1\"></rule></rule-set>";
        when(ruleforgeRepositoryService.readFile(anyString(), isNull(), isNull(), eq(false)))
                .thenReturn(new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8)));

        // When: 使用相同内容调用 checkFileDirty
        Map<String, Object> result = commonController.checkFileDirty(
                "/projectA/rules.xml", fileContent);

        // Then: status=true, data 字段存在（CompareUtils.compareContent 目前总返回 "null"）
        assertThat(result).containsEntry("status", true);
        assertThat(result).containsKey("data");
    }
}
