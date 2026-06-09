package com.ruleforge.console.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruleforge.console.app.entity.UserEntity;
import com.ruleforge.console.app.entity.UserProjectPermissionEntity;
import com.ruleforge.console.mapper.UserMapper;
import com.ruleforge.console.mapper.UserProjectPermissionMapper;
import com.ruleforge.console.model.User;
import com.ruleforge.console.repository.model.FileType;
import com.ruleforge.console.service.PermissionService;
import com.ruleforge.console.servlet.permission.ProjectConfig;
import com.ruleforge.console.util.EnvironmentUtils;
import com.ruleforge.console.servlet.RequestHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * DB-backed 权限服务 — V5.15 权限改造
 *
 * <p>替代原来的两套实现:
 * <ul>
 *   <li>{@code console.service.impl.PermissionServiceImpl} — 老的 always-true stub (已删除)</li>
 *   <li>{@code console.repository.permission.PermissionServiceImpl} — 老的文件存储版本 (保留但不激活)</li>
 * </ul>
 *
 * <p>权限来源从"仓库文件 {@code ___resource__security__config__}"迁移到
 * {@code rf_user_project_permission} 表。
 *
 * <p>接口 {@link PermissionService} 的 6 个方法签名不变,所有调用方无需改动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final UserMapper userMapper;
    private final UserProjectPermissionMapper permissionMapper;

    @Override
    public boolean isAdmin() {
        User user = EnvironmentUtils.getLoginUser(RequestHolder.newRequestContext());
        return user != null && user.isAdmin();
    }

    @Override
    public boolean projectHasPermission(String path) {
        if (isAdmin()) return true;
        ProjectConfig config = loadProjectPermission(path);
        return config != null && config.isReadProject();
    }

    @Override
    public boolean projectPackageHasReadPermission(String path) {
        if (isAdmin()) return true;
        ProjectConfig config = loadProjectPermission(path);
        return config != null && config.isReadPackage();
    }

    @Override
    public boolean projectPackageHasWritePermission(String path) {
        if (isAdmin()) return true;
        ProjectConfig config = loadProjectPermission(path);
        return config != null && config.isWritePackage();
    }

    @Override
    public boolean fileHasWritePermission(String path) {
        return fileHasPermission(path, true);
    }

    @Override
    public boolean fileHasReadPermission(String path) {
        return fileHasPermission(path, false);
    }

    // ── 内部方法 ──

    private boolean fileHasPermission(String path, boolean write) {
        if (isAdmin()) return true;
        path = processPath(path);
        int slashPos = path.indexOf("/");
        if (slashPos == -1) return true;
        String project = path.substring(0, slashPos);

        int pointPos = path.lastIndexOf(".");
        if (pointPos == -1) return true;

        ProjectConfig config = loadProjectPermission(path);
        if (config == null) return false;

        String extName = path.substring(pointPos + 1);
        FileType type = FileType.parse(extName);
        return checkFilePermission(config, type, write);
    }

    private boolean checkFilePermission(ProjectConfig config, FileType type, boolean write) {
        if (type == null) return true;
        return switch (type) {
            case VariableLibrary -> write ? config.isWriteVariableFile() : config.isReadVariableFile();
            case ActionLibrary -> write ? config.isWriteActionFile() : config.isReadActionFile();
            case ConstantLibrary -> write ? config.isWriteConstantFile() : config.isReadConstantFile();
            case DecisionTable, ScriptDecisionTable, Crosstab -> write ? config.isWriteDecisionTableFile() : config.isReadDecisionTableFile();
            case DecisionTree -> write ? config.isWriteDecisionTreeFile() : config.isReadDecisionTreeFile();
            case ParameterLibrary -> write ? config.isWriteParameterFile() : config.isReadParameterFile();
            case RuleFlow -> write ? config.isWriteFlowFile() : config.isReadFlowFile();
            case Ruleset, RulesetLib, UL -> write ? config.isWriteRuleFile() : config.isReadRuleFile();
            case Scorecard, ComplexScorecard -> write ? config.isWriteScorecardFile() : config.isReadScorecardFile();
            case DIR, Package -> true;
        };
    }

    /**
     * 从 DB 加载项目权限:session user → username → user_id → permission row → ProjectConfig
     */
    private ProjectConfig loadProjectPermission(String path) {
        User user = EnvironmentUtils.getLoginUser(RequestHolder.newRequestContext());
        if (user == null) return null;

        path = processPath(path);
        int slashPos = path.indexOf("/");
        String project = slashPos == -1 ? path : path.substring(0, slashPos);

        try {
            UserEntity userEntity = userMapper.selectByUsername(user.getUsername());
            if (userEntity == null) return null;

            UserProjectPermissionEntity entity = permissionMapper.selectByUserIdAndProject(
                    userEntity.getId(), project);
            if (entity == null) return null;

            return entity.toProjectConfig();
        } catch (Exception e) {
            log.error("loadProjectPermission 失败: user={}, project={}", user.getUsername(), project, e);
            return null;
        }
    }

    private String processPath(String path) {
        if (path.startsWith("/")) return path.substring(1);
        return path;
    }
}
