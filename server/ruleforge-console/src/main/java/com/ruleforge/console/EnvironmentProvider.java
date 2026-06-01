package com.ruleforge.console;

import com.ruleforge.console.servlet.RequestContext;
import com.ruleforge.console.model.User;

import java.util.List;

public interface EnvironmentProvider {

    User getLoginUser(RequestContext context);

    List<User> getUsers();
}
