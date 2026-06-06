package com.ruleforge.console.controller;

import com.ruleforge.console.app.entity.UserEntity;
import com.ruleforge.console.app.service.AuthService;
import com.ruleforge.console.app.util.PasswordUtil;
import com.ruleforge.console.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Feature: LoginController 密码认证 (V5.15)
 *
 * Scenario 1: 正常登录
 *   Given DB 有 admin 用户,密码 admin123
 *   When POST /frame/login(username=admin, password=admin123)
 *   Then 返 {status: true, user: {username: admin, admin: true}}
 *
 * Scenario 2: 密码错误
 *   Given DB 有 admin 用户
 *   When POST /frame/login(username=admin, password=wrong)
 *   Then 返 {status: false, error: "用户名或密码错误"}
 *
 * Scenario 3: session 写入
 *   Given 登录成功
 *   When session.getAttribute(USER_SESSION_KEY)
 *   Then DefaultUser 非 null 且 isAdmin=false(非 admin 用户)
 *
 * Scenario 4: logout 清 session
 *   Given session 有用户
 *   When POST /frame/logout
 *   Then session 被 invalidate
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LoginController - 密码认证 (V5.15)")
class LoginControllerAuthTest {

    @Mock
    private AuthService authService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private LoginController controller;

    private UserEntity buildUser(String username, boolean isAdmin) {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername(username);
        user.setCompanyId("ruleforge");
        user.setAdmin(isAdmin);
        user.setEnabled(true);
        return user;
    }

    @Nested
    @DisplayName("Scenario 1: 正常登录")
    class LoginSuccess {

        // Given DB 有 admin 用户,密码 admin123
        // When POST /frame/login
        // Then 返 {status: true, user: {...}}
        @Test
        @DisplayName("admin 登录 → 返 status=true + user info")
        void shouldReturnSuccessOnValidLogin() {
            UserEntity user = buildUser("admin", true);
            when(authService.login("admin", "admin123")).thenReturn(user);
            HttpSession session = new MockHttpSession();
            when(request.getSession(true)).thenReturn(session);

            Map<String, Object> result = controller.login(request, "admin", "admin123");

            assertThat(result.get("status")).isEqualTo(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> userInfo = (Map<String, Object>) result.get("user");
            assertThat(userInfo.get("username")).isEqualTo("admin");
            assertThat(userInfo.get("admin")).isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("Scenario 2: 密码错误")
    class LoginFailure {

        // Given DB 有 admin 用户
        // When POST /frame/login(password=wrong)
        // Then 返 {status: false, error: "用户名或密码错误"}
        @Test
        @DisplayName("密码错误 → 返 status=false + error message")
        void shouldReturnErrorOnWrongPassword() {
            when(authService.login("admin", "wrong")).thenReturn(null);

            Map<String, Object> result = controller.login(request, "admin", "wrong");

            assertThat(result.get("status")).isEqualTo(false);
            assertThat(result.get("error").toString()).contains("用户名或密码错误");
        }
    }

    @Nested
    @DisplayName("Scenario 3: session 写入")
    class SessionWrite {

        // Given 登录成功(非 admin 用户)
        // When session.getAttribute(USER_SESSION_KEY)
        // Then DefaultUser 非 null 且 isAdmin=false
        @Test
        @DisplayName("登录成功 → session 存 DefaultUser 且 isAdmin 正确")
        void shouldWriteUserToSession() {
            UserEntity user = buildUser("testuser", false);
            when(authService.login("testuser", "pass")).thenReturn(user);
            HttpSession session = new MockHttpSession();
            when(request.getSession(true)).thenReturn(session);

            controller.login(request, "testuser", "pass");

            User sessionUser = (User) session.getAttribute(LoginController.USER_SESSION_KEY);
            assertThat(sessionUser).isNotNull();
            assertThat(sessionUser.getUsername()).isEqualTo("testuser");
            assertThat(sessionUser.isAdmin()).isFalse();
        }
    }

    @Nested
    @DisplayName("Scenario 4: logout 清 session")
    class LogoutClearsSession {

        // Given session 有用户
        // When POST /frame/logout
        // Then session 被 invalidate
        @Test
        @DisplayName("logout → session invalidate")
        void shouldInvalidateSessionOnLogout() {
            HttpSession session = new MockHttpSession();
            when(request.getSession(false)).thenReturn(session);

            Map<String, Object> result = controller.logout(request);

            assertThat(result.get("status")).isEqualTo(true);
            // MockHttpSession stores the invalidate flag internally
            assertThat(((MockHttpSession) session).isInvalid()).isTrue();
        }
    }
}
