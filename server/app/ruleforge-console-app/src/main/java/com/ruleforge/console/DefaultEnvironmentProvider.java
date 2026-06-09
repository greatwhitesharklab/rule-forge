package com.ruleforge.console;

import com.ruleforge.console.model.DefaultUser;
import com.ruleforge.console.model.User;
import com.ruleforge.console.servlet.RequestContext;

import java.util.ArrayList;
import java.util.List;

public class DefaultEnvironmentProvider implements EnvironmentProvider {

    @Override
    public User getLoginUser(RequestContext context) {
        DefaultUser user = new DefaultUser();
        user.setUsername("admin");
        user.setCompanyId("ruleforge");
        user.setAdmin(true);
        return user;
    }

    @Override
    public List<User> getUsers() {
        return new ArrayList<>();
    }
}
