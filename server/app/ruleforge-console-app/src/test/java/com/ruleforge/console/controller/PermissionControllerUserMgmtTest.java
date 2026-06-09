package com.ruleforge.console.controller;

import com.ruleforge.console.app.entity.UserEntity;
import com.ruleforge.console.app.entity.UserProjectPermissionEntity;
import com.ruleforge.console.audit.service.AuditService;
import com.ruleforge.console.mapper.UserMapper;
import com.ruleforge.console.mapper.UserProjectPermissionMapper;
import com.ruleforge.console.app.service.AuthService;
import com.ruleforge.console.app.util.PasswordUtil;
import com.ruleforge.console.exception.NoPermissionException;
import com.ruleforge.console.model.DefaultUser;
import com.ruleforge.console.repository.RepositoryService;
import com.ruleforge.console.repository.permission.PermissionStore;
import com.ruleforge.console.servlet.permission.ProjectConfig;
import com.ruleforge.console.util.EnvironmentUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Feature: PermissionController 用户管理 CRUD (V5.15)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionController - 用户管理 CRUD (V5.15)")
class PermissionControllerUserMgmtTest {

    @Mock private RepositoryService repositoryService;
    @Mock private PermissionStore permissionStore;
    @Mock private AuthService authService;
    @Mock private UserMapper userMapper;
    @Mock private UserProjectPermissionMapper permissionMapper;
    /** V5.17:audit log 服务,user-mgmt 操作都走它 */
    @Mock private AuditService auditService;

    @InjectMocks
    private PermissionController controller;

    private MockedStatic<EnvironmentUtils> envUtilsMock;

    @BeforeEach
    void mockEnvironmentUtils() {
        envUtilsMock = mockStatic(EnvironmentUtils.class);
        // 默认:admin 用户
        DefaultUser admin = new DefaultUser();
        admin.setUsername("admin");
        admin.setAdmin(true);
        admin.setCompanyId("ruleforge");
        envUtilsMock.when(() -> EnvironmentUtils.getLoginUser(any())).thenReturn(admin);
    }

    @AfterEach
    void closeMock() {
        envUtilsMock.close();
    }

    @Nested
    @DisplayName("Scenario 1: 创建用户")
    class CreateUser {

        @Test
        @DisplayName("admin 创建用户 → authService.createUser 被调 + 返 id")
        void shouldCreateUserWithBcryptHash() {
            UserEntity created = new UserEntity();
            created.setId(99L);
            created.setUsername("testuser");
            created.setAdmin(false);
            when(authService.createUser("admin", "testuser", "pass123", false, false)).thenReturn(created);

            Map<String, Object> result = controller.createUser("testuser", "pass123", false, false);

            assertThat(result.get("status")).isEqualTo(true);
            assertThat(result.get("id")).isEqualTo(99L);
            assertThat(result.get("username")).isEqualTo("testuser");
            verify(authService).createUser("admin", "testuser", "pass123", false, false);
        }

        @Test
        @DisplayName("创建重复用户名 → error")
        void shouldRejectDuplicateUsername() {
            when(authService.createUser("admin", "testuser", "pass", false, false))
                    .thenThrow(new IllegalArgumentException("用户名 'testuser' 已存在"));

            Map<String, Object> result = controller.createUser("testuser", "pass", false, false);
            assertThat(result.get("error").toString()).contains("已存在");
        }
    }

    @Nested
    @DisplayName("Scenario 2: 非 admin 创建 → NoPermissionException")
    class NonAdminCreate {

        @Test
        @DisplayName("非 admin → NoPermissionException")
        void shouldThrowNoPermissionForNonAdmin() {
            // 覆盖默认 admin → 非 admin
            DefaultUser nonAdmin = new DefaultUser();
            nonAdmin.setUsername("user1");
            nonAdmin.setAdmin(false);
            envUtilsMock.when(() -> EnvironmentUtils.getLoginUser(any())).thenReturn(nonAdmin);

            assertThatThrownBy(() -> controller.createUser("x", "y", false, false))
                    .isInstanceOf(NoPermissionException.class);
        }
    }

    @Nested
    @DisplayName("Scenario 3: 保存项目权限")
    class SavePermissions {

        @Test
        @DisplayName("admin 保存权限 → delete old + batch insert")
        void shouldReplacePermissions() {
            UserEntity user = new UserEntity();
            user.setId(42L);
            when(userMapper.selectById(42L)).thenReturn(user);
            when(permissionMapper.deleteByUserId(42L)).thenReturn(2);
            when(permissionMapper.insert(any(UserProjectPermissionEntity.class))).thenReturn(1);

            ProjectConfig pc = new ProjectConfig();
            pc.setProject("proj-a");
            pc.setReadProject(true);
            pc.setReadRuleFile(true);
            pc.setWriteRuleFile(true);
            Map<String, Object> result = controller.saveUserPermissions(42L, List.of(pc));

            assertThat(result.get("status")).isEqualTo(true);
            verify(permissionMapper).deleteByUserId(42L);
            ArgumentCaptor<UserProjectPermissionEntity> captor = ArgumentCaptor.forClass(UserProjectPermissionEntity.class);
            verify(permissionMapper).insert(captor.capture());
            UserProjectPermissionEntity inserted = captor.getValue();
            assertThat(inserted.getUserId()).isEqualTo(42L);
            assertThat(inserted.getProject()).isEqualTo("proj-a");
            assertThat(inserted.isReadProject()).isTrue();
            assertThat(inserted.isReadRuleFile()).isTrue();
            assertThat(inserted.isWriteRuleFile()).isTrue();
        }
    }
}
