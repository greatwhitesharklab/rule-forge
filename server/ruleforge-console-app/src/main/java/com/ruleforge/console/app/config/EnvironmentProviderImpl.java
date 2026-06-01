package com.ruleforge.console.app.config;

import com.ruleforge.console.EnvironmentProvider;
import com.ruleforge.console.controller.LoginController;
import com.ruleforge.console.model.DefaultUser;
import com.ruleforge.console.model.User;
import com.ruleforge.console.servlet.RequestContext;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EnvironmentProviderImpl implements EnvironmentProvider {

    @Override
    public User getLoginUser(RequestContext context) {
        if (context != null && context.getRequest() != null) {
            HttpSession session = context.getRequest().getSession(false);
            if (session != null) {
                User user = (User) session.getAttribute(LoginController.USER_SESSION_KEY);
                if (user != null) {
                    return user;
                }
            }
        }
        DefaultUser user = new DefaultUser();
        user.setUsername("anonymous");
        user.setCompanyId("ruleforge");
        user.setAdmin(false);
        return user;
    }

    @Override
    public List<User> getUsers() {
        DefaultUser user1 = new DefaultUser();
        user1.setCompanyId("ruleforge");
        user1.setUsername("user1");
        List<User> users = new ArrayList<>();
        users.add(user1);
        return users;
    }
}
