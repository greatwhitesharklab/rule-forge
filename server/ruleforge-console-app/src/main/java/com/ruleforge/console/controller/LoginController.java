package com.ruleforge.console.controller;

import com.ruleforge.console.model.DefaultUser;
import com.ruleforge.console.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/${ruleforge.root.path}")
public class LoginController {

    public static final String USER_SESSION_KEY = "ruleforge.login.user";

    @PostMapping("/frame/login")
    public Map<String, Object> login(HttpServletRequest request,
                                     @RequestParam String username,
                                     @RequestParam String password) {
        DefaultUser user = new DefaultUser();
        user.setUsername(username);
        user.setCompanyId("ruleforge");
        user.setAdmin(true);
        user.setCanImport(true);
        user.setCanExport(true);
        request.getSession(true).setAttribute(USER_SESSION_KEY, user);
        return Map.of("status", true, "user", Map.of(
                "username", user.getUsername(),
                "admin", user.isAdmin(),
                "companyId", user.getCompanyId()
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
