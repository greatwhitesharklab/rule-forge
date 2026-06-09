package com.ruleforge.console.audit;

import com.ruleforge.console.app.service.AuthService;
import com.ruleforge.console.audit.entity.AuditLogEntity;
import com.ruleforge.console.audit.service.AuditService;
import com.ruleforge.console.controller.PermissionController;
import com.ruleforge.console.exception.NoPermissionException;
import com.ruleforge.console.mapper.UserMapper;
import com.ruleforge.console.mapper.UserProjectPermissionMapper;
import com.ruleforge.console.model.DefaultUser;
import com.ruleforge.console.repository.RepositoryService;
import com.ruleforge.console.repository.permission.PermissionStore;
import com.ruleforge.console.util.EnvironmentUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Feature: V5.17 audit log 查询端点
 *
 * <p>GET /ruleforge/permission/audit 拉 audit log(分页 + 过滤),
 * 复用 PermissionController 路由,跟现有 /users 端点一样 admin-only。
 *
 * <p>测试 mock {@code EnvironmentUtils.getLoginUser(any())} 切 admin/non-admin
 * 状态(跟现有 {@code PermissionControllerUserMgmtTest} 同款模式)。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditController - V5.17 audit log 查询端点")
class AuditControllerTest {

    private final RepositoryService repositoryService = mock(RepositoryService.class);
    private final PermissionStore permissionStore = mock(PermissionStore.class);
    private final AuthService authService = mock(AuthService.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserProjectPermissionMapper permissionMapper = mock(UserProjectPermissionMapper.class);
    private final AuditService auditService = mock(AuditService.class);
    private final PermissionController controller = new PermissionController(
            repositoryService, permissionStore, authService, userMapper, permissionMapper, auditService);

    private MockedStatic<EnvironmentUtils> envUtilsMock;

    @BeforeEach
    void mockEnvironmentUtils() {
        envUtilsMock = mockStatic(EnvironmentUtils.class);
        // 默认:admin 用户
        envUtilsMock.when(() -> EnvironmentUtils.getLoginUser(any())).thenReturn(admin("admin"));
    }

    @AfterEach
    void closeMock() {
        envUtilsMock.close();
    }

    private static DefaultUser admin(String name) {
        DefaultUser u = new DefaultUser();
        u.setUsername(name);
        u.setAdmin(true);
        u.setCompanyId("ruleforge");
        return u;
    }

    @Nested
    @DisplayName("admin 门控")
    class AdminGate {

        @Test
        @DisplayName("非 admin 调用 /permission/audit 应抛 NoPermissionException → 401")
        void shouldRejectNonAdmin() {
            // Given 当前用户是普通用户
            DefaultUser nonAdmin = new DefaultUser();
            nonAdmin.setUsername("user1");
            nonAdmin.setAdmin(false);
            envUtilsMock.when(() -> EnvironmentUtils.getLoginUser(any())).thenReturn(nonAdmin);

            // When 调 /permission/audit
            // Then 抛 NoPermissionException
            assertThatThrownBy(() -> controller.listAuditLogs(null, null, 20))
                    .isInstanceOf(NoPermissionException.class);
        }

        @Test
        @DisplayName("admin 调用 /permission/audit 应返 200 + audit log 列表")
        void shouldAllowAdmin() {
            // Given mapper 返 1 行
            AuditLogEntity row = new AuditLogEntity();
            row.setAction("CREATE_USER");
            // 用 nullable 让 null 实参也能匹配
            when(auditService.listAuditLogs(nullable(String.class), nullable(String.class), anyInt()))
                    .thenReturn(List.of(row));

            // When 调 /permission/audit
            List<AuditLogEntity> result = controller.listAuditLogs(null, null, 20);

            // Then 返列表
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAction()).isEqualTo("CREATE_USER");
        }
    }

    @Nested
    @DisplayName("过滤 + 分页")
    class FiltersAndPagination {

        @Test
        @DisplayName("actor 过滤 应透传给 AuditService.listAuditLogs")
        void shouldPassActorFilter() {
            // Given admin + actor=alice 过滤
            when(auditService.listAuditLogs("alice", null, 50)).thenReturn(List.of());

            // When 调
            controller.listAuditLogs("alice", null, 50);

            // Then AuditService 收到 actor="alice"
            verify(auditService).listAuditLogs("alice", null, 50);
        }

        @Test
        @DisplayName("size 超过上限 应 clamp 到 500(V5.10-C GitObservabilityController 同款防滥用)")
        void shouldClampSizeTo500() {
            // Given admin + size=99999
            when(auditService.listAuditLogs(nullable(String.class), nullable(String.class), anyInt())).thenReturn(List.of());

            // When 调
            controller.listAuditLogs(null, null, 99999);

            // Then 实际传给 service 的 size 被 clamp 到 500
            verify(auditService).listAuditLogs(null, null, 500);
        }
    }
}
