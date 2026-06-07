package com.ruleforge.console.controller;

import com.ruleforge.console.app.entity.UserEntity;
import com.ruleforge.console.app.entity.UserProjectPermissionEntity;
import com.ruleforge.console.mapper.UserMapper;
import com.ruleforge.console.mapper.UserProjectPermissionMapper;
import com.ruleforge.console.app.service.AuthService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 权限管理 Controller — V5.15 改造
 *
 * <p>既有端点 (load/saveResourceSecurityConfigs) 保留,
 * 新增用户 CRUD + 项目权限 DB 读写端点。
 *
 * <p>所有端点 admin-only (permissionService.isAdmin() 门控)。
 */
@RestController
@RequestMapping("/${ruleforge.root.path}/permission")
@RequiredArgsConstructor
public class PermissionController {

    private final RepositoryService repositoryService;
    private final PermissionStore permissionStore;
    private final AuthService authService;
    private final UserMapper userMapper;
    private final UserProjectPermissionMapper permissionMapper;

    // ── 既有端点 (保留兼容,admin-only) ──

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
        content = com.ruleforge.Utils.decodeURL(content);
        String path = BaseRepositoryService.RESOURCE_SECURITY_CONFIG_FILE + (companyId == null ? "" : companyId);
        try {
            repositoryService.saveFile(path, content, false, null, user);
            permissionStore.refreshPermissionStore();
        } catch (Exception ex) {
            throw new RuleException(ex);
        }
    }

    // ── V5.15 新增:用户管理 CRUD (admin-only) ──

    /**
     * 列出所有用户
     */
    @GetMapping("/users")
    public ResponseEntity<List<UserEntity>> listUsers() {
        assertAdmin();
        return ResponseEntity.ok(authService.listUsers());
    }

    /**
     * 创建用户
     */
    @PostMapping("/users")
    public Map<String, Object> createUser(@RequestParam String username,
                                           @RequestParam String password,
                                           @RequestParam(defaultValue = "false") boolean isAdmin,
                                           @RequestParam(defaultValue = "false") boolean canExport) {
        assertAdmin();
        try {
            UserEntity user = authService.createUser(username, password, isAdmin, canExport);
            return Map.of("status", true, "id", user.getId(), "username", user.getUsername());
        } catch (IllegalArgumentException e) {
            return Map.of("status", false, "error", e.getMessage());
        }
    }

    /**
     * 修改用户(含密码重置)
     */
    @PutMapping("/users/{id}")
    public Map<String, Object> updateUser(@PathVariable Long id,
                                           @RequestParam(required = false) String password,
                                           @RequestParam(required = false) Boolean isAdmin,
                                           @RequestParam(required = false) Boolean canImport,
                                           @RequestParam(required = false) Boolean canExport) {
        assertAdmin();
        try {
            UserEntity user = authService.updateUser(id, password, isAdmin, canImport, canExport);
            return Map.of("status", true, "username", user.getUsername());
        } catch (IllegalArgumentException e) {
            return Map.of("status", false, "error", e.getMessage());
        }
    }

    /**
     * 启用/禁用用户(不物理删)
     */
    @PatchMapping("/users/{id}/enabled")
    public Map<String, Object> toggleEnabled(@PathVariable Long id,
                                              @RequestParam boolean enabled) {
        assertAdmin();
        authService.toggleEnabled(id, enabled);
        return Map.of("status", true);
    }

    /**
     * 重置密码
     */
    @PostMapping("/users/{id}/reset-password")
    public Map<String, Object> resetPassword(@PathVariable Long id,
                                              @RequestParam String newPassword) {
        assertAdmin();
        authService.resetPassword(id, newPassword);
        return Map.of("status", true);
    }

    /**
     * 获取某用户的项目权限
     */
    @GetMapping("/users/{id}/permissions")
    public ResponseEntity<List<UserProjectPermissionEntity>> getUserPermissions(@PathVariable Long id) {
        assertAdmin();
        return ResponseEntity.ok(permissionMapper.selectByUserId(id));
    }

    /**
     * 批量保存某用户的项目权限(replace-all)
     */
    @PostMapping("/users/{id}/permissions")
    public Map<String, Object> saveUserPermissions(@PathVariable Long id,
                                                     @RequestBody List<ProjectConfig> configs) {
        assertAdmin();
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            return Map.of("status", false, "error", "用户不存在 id=" + id);
        }
        // 清空旧权限 + 批量 insert 新权限
        permissionMapper.deleteByUserId(id);
        for (ProjectConfig pc : configs) {
            UserProjectPermissionEntity entity = toEntity(id, pc);
            permissionMapper.insert(entity);
        }
        return Map.of("status", true, "count", configs.size());
    }

    // ── 内部 helper ──

    private void assertAdmin() {
        User user = EnvironmentUtils.getLoginUser(null);
        if (user == null || !user.isAdmin()) {
            throw new NoPermissionException();
        }
    }

    private UserProjectPermissionEntity toEntity(Long userId, ProjectConfig pc) {
        UserProjectPermissionEntity entity = new UserProjectPermissionEntity();
        entity.setUserId(userId);
        entity.setProject(pc.getProject());
        entity.setReadProject(pc.isReadProject());
        entity.setReadPackage(pc.isReadPackage());
        entity.setWritePackage(pc.isWritePackage());
        entity.setReadVariableFile(pc.isReadVariableFile());
        entity.setWriteVariableFile(pc.isWriteVariableFile());
        entity.setReadParameterFile(pc.isReadParameterFile());
        entity.setWriteParameterFile(pc.isWriteParameterFile());
        entity.setReadConstantFile(pc.isReadConstantFile());
        entity.setWriteConstantFile(pc.isWriteConstantFile());
        entity.setReadActionFile(pc.isReadActionFile());
        entity.setWriteActionFile(pc.isWriteActionFile());
        entity.setReadRuleFile(pc.isReadRuleFile());
        entity.setWriteRuleFile(pc.isWriteRuleFile());
        entity.setReadDecisionTableFile(pc.isReadDecisionTableFile());
        entity.setWriteDecisionTableFile(pc.isWriteDecisionTableFile());
        entity.setReadDecisionTreeFile(pc.isReadDecisionTreeFile());
        entity.setWriteDecisionTreeFile(pc.isWriteDecisionTreeFile());
        entity.setReadScorecardFile(pc.isReadScorecardFile());
        entity.setWriteScorecardFile(pc.isWriteScorecardFile());
        entity.setReadFlowFile(pc.isReadFlowFile());
        entity.setWriteFlowFile(pc.isWriteFlowFile());
        return entity;
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
