package com.ruleforge.console.audit;

import com.ruleforge.console.app.entity.UserEntity;
import com.ruleforge.console.audit.entity.AuditLogEntity;
import com.ruleforge.console.audit.mapper.AuditLogMapper;
import com.ruleforge.console.audit.service.AuditService;
import com.ruleforge.console.audit.service.AuditServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Feature: V5.17 user/permission audit log
 *
 * <p>背景:V5.15 引入 user-mgmt CRUD(创建/修改/启用禁用/重置密码/项目权限),
 * 所有变更只 {@code log.info} 一行,事后查不出"谁在什么时候改了哪个用户的什么字段"。
 * V5.17 给 user-mgmt 的所有变更点接 audit 表,跟 V5.10-C
 * ({@code gr_git_dualwrite_failure}) 一脉相承:写到独立表 + 简单 query。
 *
 * <p>数据流:
 * <pre>
 *   AuthServiceImpl.createUser / updateUser / toggleEnabled / resetPassword
 *     ↓ AuditService.log*(actor, target, ...)
 *   rf_user_audit_log INSERT
 *   (由 PermissionController.listAuditLogs() 查询,admin-only)
 * </pre>
 *
 * <p>设计约束:
 * <ul>
 *   <li>密码**不**记明文 — 只记 {@code action=RESET_PASSWORD} 事件,
 *       {@code field_name="password"}, oldValue/newValue=null
 *       (写"已重置"比写 hash 更有意义,hash 反查不出原密码)</li>
 *   <li>{@code target_username} 冗余存 — 用户的 username 可能被改或被删,
 *       audit 仍能看懂</li>
 *   <li>{@code actor} 必填 — V5.17 当前所有触发点都在 admin 操作路径上,
 *       actor=admin;后续 V5.18 自改密码时 actor=自己</li>
 * </ul>
 */
@DisplayName("AuditService - V5.17 user/permission audit log")
class AuditServiceTest {

    private final AuditLogMapper mapper = mock(AuditLogMapper.class);
    private final AuditService service = new AuditServiceImpl(mapper);

    @Nested
    @DisplayName("CREATE_USER")
    class CreateUser {

        @Test
        @DisplayName("createUser 后应落 1 行 audit,actor=操作人,target=被创建用户,action=CREATE_USER")
        void shouldLogCreateUser() {
            // Given admin 操作 + 一个新建用户
            UserEntity target = new UserEntity();
            target.setId(42L);
            target.setUsername("alice");
            target.setAdmin(false);
            target.setEnabled(true);

            // When audit 触发
            service.logCreateUser("admin", target);

            // Then mapper.insert 被调一次,字段映射正确
            ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
            verify(mapper).insert(captor.capture());
            AuditLogEntity row = captor.getValue();
            assertThat(row.getActor()).isEqualTo("admin");
            assertThat(row.getAction()).isEqualTo("CREATE_USER");
            assertThat(row.getTargetUserId()).isEqualTo(42L);
            assertThat(row.getTargetUsername()).isEqualTo("alice");
        }
    }

    @Nested
    @DisplayName("UPDATE_USER(字段级)")
    class UpdateUserField {

        @Test
        @DisplayName("updateUser 改 isAdmin:true→false 应落 1 行 audit,field=is_admin,old/new 有值")
        void shouldLogAdminToggle() {
            // Given 用户当前 isAdmin=true,改成 false
            UserEntity target = new UserEntity();
            target.setId(7L);
            target.setUsername("bob");

            // When audit 触发
            service.logUpdateUserField("admin", target, "is_admin", "true", "false");

            // Then mapper.insert 被调一次
            ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
            verify(mapper).insert(captor.capture());
            AuditLogEntity row = captor.getValue();
            assertThat(row.getAction()).isEqualTo("UPDATE_USER");
            assertThat(row.getFieldName()).isEqualTo("is_admin");
            assertThat(row.getOldValue()).isEqualTo("true");
            assertThat(row.getNewValue()).isEqualTo("false");
        }

        @Test
        @DisplayName("updateUser 改密码 应落 1 行 audit,field=password,old/new=null(密码不存明文/不存 hash)")
        void shouldLogPasswordChangeWithoutPlaintext() {
            // Given 用户改密码
            UserEntity target = new UserEntity();
            target.setId(7L);
            target.setUsername("bob");

            // When audit 触发
            service.logUpdateUserField("admin", target, "password", null, null);

            // Then mapper.insert 被调一次,old/new 都为 null(脱敏)
            ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
            verify(mapper).insert(captor.capture());
            AuditLogEntity row = captor.getValue();
            assertThat(row.getAction()).isEqualTo("UPDATE_USER");
            assertThat(row.getFieldName()).isEqualTo("password");
            assertThat(row.getOldValue())
                    .as("旧密码绝不能落到 audit 表 — BCrypt hash 也不存,反查无意义")
                    .isNull();
            assertThat(row.getNewValue())
                    .as("新密码绝不能落到 audit 表 — BCrypt hash 也不存")
                    .isNull();
        }
    }

    @Nested
    @DisplayName("TOGGLE_ENABLED")
    class ToggleEnabled {

        @Test
        @DisplayName("禁用用户应落 1 行 audit,action=TOGGLE_ENABLED,new_value=false")
        void shouldLogDisable() {
            // Given 用户被禁用
            UserEntity target = new UserEntity();
            target.setId(7L);
            target.setUsername("bob");

            // When audit 触发
            service.logToggleEnabled("admin", target, false);

            // Then mapper.insert 落行
            ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
            verify(mapper).insert(captor.capture());
            AuditLogEntity row = captor.getValue();
            assertThat(row.getAction()).isEqualTo("TOGGLE_ENABLED");
            assertThat(row.getNewValue()).isEqualTo("false");
        }
    }

    @Nested
    @DisplayName("RESET_PASSWORD")
    class ResetPassword {

        @Test
        @DisplayName("admin 重置某用户密码应落 1 行 audit,action=RESET_PASSWORD,field=password,old/new=null")
        void shouldLogResetPassword() {
            // Given admin 重置用户密码
            UserEntity target = new UserEntity();
            target.setId(7L);
            target.setUsername("bob");

            // When audit 触发
            service.logResetPassword("admin", target);

            // Then mapper.insert 落行
            ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
            verify(mapper).insert(captor.capture());
            AuditLogEntity row = captor.getValue();
            assertThat(row.getAction()).isEqualTo("RESET_PASSWORD");
            assertThat(row.getFieldName()).isEqualTo("password");
            assertThat(row.getOldValue()).isNull();
            assertThat(row.getNewValue()).isNull();
        }
    }

    @Nested
    @DisplayName("SAVE_PERMISSIONS")
    class SavePermissions {

        @Test
        @DisplayName("批量保存某用户权限应落 1 行 audit,action=SAVE_PERMISSIONS,note 含 count")
        void shouldLogSavePermissions() {
            // Given admin 保存某用户 5 个项目权限
            UserEntity target = new UserEntity();
            target.setId(7L);
            target.setUsername("bob");

            // When audit 触发
            service.logSavePermissions("admin", target, 5);

            // Then mapper.insert 落行
            ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
            verify(mapper).insert(captor.capture());
            AuditLogEntity row = captor.getValue();
            assertThat(row.getAction()).isEqualTo("SAVE_PERMISSIONS");
            assertThat(row.getNote()).contains("5");
        }
    }

    @Nested
    @DisplayName("LOGIN 审计")
    class LoginAudit {

        @Test
        @DisplayName("登录成功应落 1 行 audit,action=LOGIN_SUCCESS,target_username=登录用户")
        void shouldLogLoginSuccess() {
            // Given 用户登录成功
            UserEntity target = new UserEntity();
            target.setId(1L);
            target.setUsername("admin");

            // When audit 触发
            service.logLoginSuccess("admin", target);

            // Then mapper.insert 落行
            ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
            verify(mapper).insert(captor.capture());
            AuditLogEntity row = captor.getValue();
            assertThat(row.getAction()).isEqualTo("LOGIN_SUCCESS");
            assertThat(row.getTargetUsername()).isEqualTo("admin");
        }

        @Test
        @DisplayName("登录失败应落 1 行 audit,action=LOGIN_FAIL,target_username=尝试登录的用户名(不一定存在)")
        void shouldLogLoginFail() {
            // Given 用户密码错误(用户存在但密码错)
            service.logLoginFail("admin", "admin");

            // Then mapper.insert 落行
            ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
            verify(mapper).insert(captor.capture());
            AuditLogEntity row = captor.getValue();
            assertThat(row.getAction()).isEqualTo("LOGIN_FAIL");
            assertThat(row.getTargetUsername()).isEqualTo("admin");
            assertThat(row.getTargetUserId())
                    .as("登录失败时通常拿不到 user_id(target=null / 不存在)")
                    .isNull();
        }

        @Test
        @DisplayName("登录失败(用户不存在)target_user_id 应为 null")
        void shouldLogLoginFailForUnknownUser() {
            // Given 尝试用不存在的用户名登录
            service.logLoginFail("ghost", "ghost");

            // Then mapper.insert 落行
            ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
            verify(mapper).insert(captor.capture());
            AuditLogEntity row = captor.getValue();
            assertThat(row.getAction()).isEqualTo("LOGIN_FAIL");
            assertThat(row.getTargetUsername()).isEqualTo("ghost");
            assertThat(row.getTargetUserId()).isNull();
        }
    }

    @Nested
    @DisplayName("listAuditLogs 查询")
    class ListAuditLogs {

        @Test
        @DisplayName("listAuditLogs 应调 mapper.selectList + 按 occurred_at 倒序,支持 actor/action 过滤")
        void shouldQueryWithFilters() {
            // Given mapper 准备返 3 行
            when(mapper.selectListByFilters("admin", "LOGIN_SUCCESS", 100)).thenReturn(List.of());

            // When 查询
            service.listAuditLogs("admin", "LOGIN_SUCCESS", 100);

            // Then mapper 拿到正确参数
            verify(mapper).selectListByFilters("admin", "LOGIN_SUCCESS", 100);
        }
    }
}
