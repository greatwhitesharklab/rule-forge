package com.ruleforge.console.app.config;

import com.ruleforge.console.EnvironmentProvider;
import com.ruleforge.console.mapper.UserMapper;
import com.ruleforge.console.controller.LoginController;
import com.ruleforge.console.model.DefaultUser;
import com.ruleforge.console.model.User;
import com.ruleforge.console.servlet.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * 环境提供者 — V5.15 改造
 *
 * <p>getLoginUser: 从 HttpSession 读用户(session 由 LoginController 写入,含 DB 验证后的 DefaultUser)
 * <p>getUsers: 从 rf_user 表读全部启用用户(给 PermissionController 的权限配置面板用)
 */
@Component
@RequiredArgsConstructor
public class EnvironmentProviderImpl implements EnvironmentProvider {

    private final UserMapper userMapper;

    @Override
    public User getLoginUser(RequestContext context) {
        // 1. 优先从传入的 RequestContext 取 session (老代码路径)
        if (context != null && context.getRequest() != null) {
            HttpSession session = context.getRequest().getSession(false);
            if (session != null) {
                User user = (User) session.getAttribute(LoginController.USER_SESSION_KEY);
                if (user != null) {
                    return user;
                }
            }
        }
        // 2. fallback: 从 Spring RequestContextHolder 取当前请求的 session
        //    (PermissionController 等 @RestController 调 EnvironmentUtils.getLoginUser(null) 时走这里)
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            HttpSession session = request.getSession(false);
            if (session != null) {
                User user = (User) session.getAttribute(LoginController.USER_SESSION_KEY);
                if (user != null) {
                    return user;
                }
            }
        }
        // 未登录 → anonymous (isAdmin=false, 不能操作)
        DefaultUser user = new DefaultUser();
        user.setUsername("anonymous");
        user.setCompanyId("ruleforge");
        user.setAdmin(false);
        return user;
    }

    @Override
    public List<User> getUsers() {
        List<com.ruleforge.console.app.entity.UserEntity> entities = userMapper.selectList(null);
        List<User> result = new ArrayList<>(entities.size());
        for (com.ruleforge.console.app.entity.UserEntity entity : entities) {
            if (entity.isEnabled()) {
                result.add(entity.toSessionUser());
            }
        }
        return result;
    }
}
