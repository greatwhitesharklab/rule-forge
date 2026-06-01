package com.ruleforge.console.controller;

import com.ruleforge.Utils;
import com.ruleforge.console.exception.NoPermissionException;
import com.ruleforge.console.model.User;
import com.ruleforge.console.repository.BaseRepositoryService;
import com.ruleforge.console.repository.RepositoryService;
import com.ruleforge.console.repository.model.RepositoryFile;
import com.ruleforge.console.repository.permission.PermissionStore;
import com.ruleforge.console.servlet.permission.ProjectConfig;
import com.ruleforge.console.servlet.permission.UserPermission;
import com.ruleforge.console.util.EnvironmentUtils;
import com.ruleforge.exception.RuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/${ruleforgeV2.root.path}/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final RepositoryService repositoryService;
    private final PermissionStore permissionStore;

    @PostMapping("/loadResourceSecurityConfigs")
    public List<UserPermission> loadResourceSecurityConfigs() throws Exception {
        if (!((com.ruleforge.console.repository.permission.PermissionService) permissionStore).isAdmin()) {
            throw new NoPermissionException();
        }
        User loginUser = EnvironmentUtils.getLoginUser(null);
        String companyId = loginUser != null ? loginUser.getCompanyId() : null;
        List<UserPermission> permissions = repositoryService.loadResourceSecurityConfigs(companyId);
        List<User> users = EnvironmentUtils.getEnvironmentProvider().getUsers();
        if (users == null) users = new ArrayList<>();
        List<UserPermission> result = new ArrayList<>();
        for (User user : users) {
            if (user.isAdmin()) continue;
            if (companyId != null) {
                if (user.getCompanyId() == null || !user.getCompanyId().equals(companyId)) continue;
            }
            boolean exist = false;
            for (UserPermission p : permissions) {
                if (p.getUsername().equals(user.getUsername())) {
                    exist = true;
                    break;
                }
            }
            if (exist) continue;
            UserPermission up = new UserPermission();
            up.setProjectConfigs(new ArrayList<ProjectConfig>());
            up.setUsername(user.getUsername());
            result.add(up);
        }
        result.addAll(permissions);
        List<RepositoryFile> projects = repositoryService.loadProjects(companyId);
        for (UserPermission p : result) {
            buildProjectConfigs(projects, p);
        }
        return result;
    }

    @PostMapping("/saveResourceSecurityConfigs")
    public void saveResourceSecurityConfigs(@RequestParam String content) throws Exception {
        if (!((com.ruleforge.console.repository.permission.PermissionService) permissionStore).isAdmin()) {
            throw new NoPermissionException();
        }
        User user = EnvironmentUtils.getLoginUser(null);
        String companyId = user != null ? user.getCompanyId() : null;
        content = Utils.decodeURL(content);
        String path = BaseRepositoryService.RESOURCE_SECURITY_CONFIG_FILE + (companyId == null ? "" : companyId);
        try {
            repositoryService.saveFile(path, content, false, null, user);
            permissionStore.refreshPermissionStore();
        } catch (Exception ex) {
            throw new RuleException(ex);
        }
    }

    private void buildProjectConfigs(List<RepositoryFile> projects, UserPermission p) {
        List<ProjectConfig> configs = p.getProjectConfigs();
        if (configs == null) {
            configs = new ArrayList<>();
            p.setProjectConfigs(configs);
        }
        for (RepositoryFile project : projects) {
            boolean exist = false;
            for (ProjectConfig c : p.getProjectConfigs()) {
                if (project.getName().equals(c.getProject())) {
                    exist = true;
                    break;
                }
            }
            if (exist) continue;
            ProjectConfig config = new ProjectConfig();
            config.setProject(project.getName());
            configs.add(config);
        }
    }
}
