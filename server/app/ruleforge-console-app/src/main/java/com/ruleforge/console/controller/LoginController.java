package com.ruleforge.console.controller;

import com.ruleforge.console.app.entity.UserEntity;
import com.ruleforge.console.app.service.AuthService;
import com.ruleforge.console.model.DefaultUser;
import com.ruleforge.console.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 登录/登出/当前用户 — V5.15 改造
 *
 * <p>V5.15 改动:
 *   - login() 不再硬编码 setAdmin(true),改为调 AuthService 做 BCrypt 验证
 *   - 认证失败返 {status: false, error: "用户名或密码错误"}
 *   - session 写入的 DefaultUser 属性来自 DB 实体
 */
@RestController
@RequestMapping("/${ruleforge.root.path}")
@RequiredArgsConstructor
public class LoginController {

    public static final String USER_SESSION_KEY = "ruleforge.login.user";

    private final AuthService authService;

    @PostMapping("/frame/login")
    public Map<String, Object> login(HttpServletRequest request,
                                     @RequestParam String username,
                                     @RequestParam String password) {
        UserEntity userEntity = authService.login(username, password);
        if (userEntity == null) {
            return Map.of("status", false, "error", "用户名或密码错误");
        }

        // DB entity → session 兼容的 DefaultUser
        DefaultUser sessionUser = userEntity.toSessionUser();
        request.getSession(true).setAttribute(USER_SESSION_KEY, sessionUser);

        return Map.of("status", true, "user", Map.of(
                "username", userEntity.getUsername(),
                "admin", userEntity.isAdmin(),
                "companyId", userEntity.getCompanyId()
        ));
    }

    @PostMapping("/frame/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return Map.of("status", true);
    }

    @PostMapping("/frame/currentUser")
    public Map<String, Object> currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        User user = session != null ? (User) session.getAttribute(USER_SESSION_KEY) : null;
        if (user == null) {
            return Map.of("status", false);
        }
        return Map.of("status", true, "user", Map.of(
                "username", user.getUsername(),
                "admin", user.isAdmin(),
                "companyId", user.getCompanyId()
        ));
    }
}
