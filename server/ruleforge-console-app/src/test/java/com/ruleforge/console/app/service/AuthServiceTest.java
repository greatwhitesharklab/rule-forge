package com.ruleforge.console.app.service;

import com.ruleforge.console.app.entity.UserEntity;
import com.ruleforge.console.mapper.UserMapper;
import com.ruleforge.console.app.service.impl.AuthServiceImpl;
import com.ruleforge.console.app.util.PasswordUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Feature: AuthService BCrypt 密码认证 (V5.15)
 *
 * Scenario 1: 登录成功
 *   Given 用户存在且密码正确
 *   When login(username, password)
 *   Then 返 UserEntity
 *
 * Scenario 2: 密码错误
 *   Given 用户存在但密码错误
 *   When login(username, password)
 *   Then 返 null
 *
 * Scenario 3: 用户不存在
 *   Given username 不在 DB
 *   When login(username, password)
 *   Then 返 null
 *
 * Scenario 4: 用户被禁用
 *   Given is_enabled=false
 *   When login(username, password)
 *   Then 返 null
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService - BCrypt 密码认证 (V5.15)")
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthServiceImpl authService;

    private UserEntity buildUser(String username, String rawPassword, boolean isAdmin, boolean enabled) {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername(username);
        user.setPasswordHash(PasswordUtil.encode(rawPassword));
        user.setCompanyId("ruleforge");
        user.setAdmin(isAdmin);
        user.setEnabled(enabled);
        user.setCanImport(true);
        user.setCanExport(true);
        return user;
    }

    @Nested
    @DisplayName("Scenario 1: 登录成功")
    class LoginSuccess {

        // Given 用户存在且密码正确
        // When login(username, password)
        // Then 返 UserEntity
        @Test
        @DisplayName("正确密码 → 返 UserEntity")
        void shouldReturnUserOnCorrectPassword() {
            UserEntity user = buildUser("admin", "admin123", true, true);
            when(userMapper.selectByUsername("admin")).thenReturn(user);

            UserEntity result = authService.login("admin", "admin123");

            assertThat(result).isNotNull();
            assertThat(result.getUsername()).isEqualTo("admin");
            assertThat(result.isAdmin()).isTrue();
        }
    }

    @Nested
    @DisplayName("Scenario 2: 密码错误")
    class WrongPassword {

        // Given 用户存在但密码错误
        // When login(username, wrongPassword)
        // Then 返 null
        @Test
        @DisplayName("密码错误 → 返 null")
        void shouldReturnNullOnWrongPassword() {
            UserEntity user = buildUser("admin", "admin123", true, true);
            when(userMapper.selectByUsername("admin")).thenReturn(user);

            UserEntity result = authService.login("admin", "wrong-password");

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Scenario 3: 用户不存在")
    class UserNotFound {

        // Given username 不在 DB
        // When login(username, password)
        // Then 返 null
        @Test
        @DisplayName("用户不存在 → 返 null")
        void shouldReturnNullOnMissingUser() {
            when(userMapper.selectByUsername("nobody")).thenReturn(null);

            UserEntity result = authService.login("nobody", "any-password");

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Scenario 4: 用户被禁用")
    class UserDisabled {

        // Given is_enabled=false
        // When login(username, password)
        // Then 返 null
        @Test
        @DisplayName("用户被禁用 → 返 null")
        void shouldReturnNullOnDisabledUser() {
            UserEntity user = buildUser("disabled", "pass123", false, false);
            when(userMapper.selectByUsername("disabled")).thenReturn(user);

            UserEntity result = authService.login("disabled", "pass123");

            assertThat(result).isNull();
        }
    }
}
