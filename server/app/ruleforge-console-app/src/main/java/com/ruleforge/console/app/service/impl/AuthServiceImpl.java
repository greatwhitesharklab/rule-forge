package com.ruleforge.console.app.service.impl;

import com.ruleforge.console.app.entity.UserEntity;
import com.ruleforge.console.mapper.UserMapper;
import com.ruleforge.console.app.service.AuthService;
import com.ruleforge.console.app.util.PasswordUtil;
import com.ruleforge.console.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 认证服务实现 — V5.15 权限改造 + V5.17 audit log 接入。
 *
 * <p>BCrypt 密码验证 + 用户 CRUD。替代原来 {@code DefaultEnvironmentProvider}
 * 硬编码 admin 的"永远 admin"行为。
 *
 * <p>V5.17:每个 user-mgmt 操作(createUser / updateUser / toggleEnabled /
 * resetPassword)在写完 DB 后调 {@link AuditService} 落 audit log;
 * audit 失败 log.warn 不抛(避免 audit 故障影响 user-mgmt 主路径)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final AuditService auditService;

    @Override
    public UserEntity login(String username, String password) {
        UserEntity user = userMapper.selectByUsername(username);
        if (user == null) {
            log.debug("login: 用户不存在 username={}", username);
            // V5.17:登录失败 audit(target_user_id=null)
            auditService.logLoginFail(username, username);
            return null;
        }
        if (!user.isEnabled()) {
            log.debug("login: 用户被禁用 username={}", username);
            auditService.logLoginFail(username, username);
            return null;
        }
        if (!PasswordUtil.matches(password, user.getPasswordHash())) {
            log.debug("login: 密码错误 username={}", username);
            auditService.logLoginFail(username, username);
            return null;
        }
        log.info("login: 认证成功 username={} admin={}", username, user.isAdmin());
        // V5.17:登录成功 audit(actor 跟 target 都记当前 username,V5.18 之前都是 self-actor)
        auditService.logLoginSuccess(user.getUsername(), user);
        return user;
    }

    @Override
    public UserEntity findByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    @Override
    public UserEntity findById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public List<UserEntity> listUsers() {
        return userMapper.selectList(null);
    }

    @Override
    public UserEntity createUser(String actor, String username, String password, boolean isAdmin, boolean canExport) {
        UserEntity existing = userMapper.selectByUsername(username);
        if (existing != null) {
            throw new IllegalArgumentException("用户名 '" + username + "' 已存在");
        }
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(PasswordUtil.encode(password));
        user.setCompanyId("ruleforge");
        user.setAdmin(isAdmin);
        user.setEnabled(true);
        user.setCanImport(isAdmin);
        user.setCanExport(canExport);
        userMapper.insert(user);
        log.info("createUser: 用户创建成功 username={} admin={}", username, isAdmin);
        // V5.17:audit create
        auditService.logCreateUser(actor, user);
        return user;
    }

    @Override
    public UserEntity updateUser(String actor, Long id, String newPassword, Boolean isAdmin, Boolean canImport, Boolean canExport) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在 id=" + id);
        }
        if (newPassword != null && !newPassword.isBlank()) {
            user.setPasswordHash(PasswordUtil.encode(newPassword));
            // V5.17:audit password 变更(old/new=null,脱敏)
            auditService.logUpdateUserField(actor, user, "password", null, null);
        }
        if (isAdmin != null && isAdmin != user.isAdmin()) {
            String oldVal = String.valueOf(user.isAdmin());
            user.setAdmin(isAdmin);
            auditService.logUpdateUserField(actor, user, "is_admin", oldVal, String.valueOf(isAdmin));
        }
        if (canImport != null && canImport != user.isCanImport()) {
            String oldVal = String.valueOf(user.isCanImport());
            user.setCanImport(canImport);
            auditService.logUpdateUserField(actor, user, "can_import", oldVal, String.valueOf(canImport));
        }
        if (canExport != null && canExport != user.isCanExport()) {
            String oldVal = String.valueOf(user.isCanExport());
            user.setCanExport(canExport);
            auditService.logUpdateUserField(actor, user, "can_export", oldVal, String.valueOf(canExport));
        }
        userMapper.updateById(user);
        log.info("updateUser: 用户更新成功 id={}", id);
        return user;
    }

    @Override
    public void toggleEnabled(String actor, Long id, boolean enabled) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在 id=" + id);
        }
        user.setEnabled(enabled);
        userMapper.updateById(user);
        log.info("toggleEnabled: id={} enabled={}", id, enabled);
        // V5.17:audit enable/disable
        auditService.logToggleEnabled(actor, user, enabled);
    }

    @Override
    public void resetPassword(String actor, Long id, String newPassword) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在 id=" + id);
        }
        user.setPasswordHash(PasswordUtil.encode(newPassword));
        userMapper.updateById(user);
        log.info("resetPassword: id={}", id);
        // V5.17:audit password reset(脱敏,只记事件)
        auditService.logResetPassword(actor, user);
    }
}
