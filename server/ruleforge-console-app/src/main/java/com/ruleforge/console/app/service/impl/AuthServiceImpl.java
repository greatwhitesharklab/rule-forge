package com.ruleforge.console.app.service.impl;

import com.ruleforge.console.app.entity.UserEntity;
import com.ruleforge.console.mapper.UserMapper;
import com.ruleforge.console.app.service.AuthService;
import com.ruleforge.console.app.util.PasswordUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 认证服务实现 — V5.15 权限改造
 *
 * <p>BCrypt 密码验证 + 用户 CRUD。替代原来 {@code DefaultEnvironmentProvider}
 * 硬编码 admin 的"永远 admin"行为。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;

    @Override
    public UserEntity login(String username, String password) {
        UserEntity user = userMapper.selectByUsername(username);
        if (user == null) {
            log.debug("login: 用户不存在 username={}", username);
            return null;
        }
        if (!user.isEnabled()) {
            log.debug("login: 用户被禁用 username={}", username);
            return null;
        }
        if (!PasswordUtil.matches(password, user.getPasswordHash())) {
            log.debug("login: 密码错误 username={}", username);
            return null;
        }
        log.info("login: 认证成功 username={} admin={}", username, user.isAdmin());
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
    public UserEntity createUser(String username, String password, boolean isAdmin, boolean canExport) {
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
        return user;
    }

    @Override
    public UserEntity updateUser(Long id, String newPassword, Boolean isAdmin, Boolean canImport, Boolean canExport) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在 id=" + id);
        }
        if (newPassword != null && !newPassword.isBlank()) {
            user.setPasswordHash(PasswordUtil.encode(newPassword));
        }
        if (isAdmin != null) {
            user.setAdmin(isAdmin);
        }
        if (canImport != null) {
            user.setCanImport(canImport);
        }
        if (canExport != null) {
            user.setCanExport(canExport);
        }
        userMapper.updateById(user);
        log.info("updateUser: 用户更新成功 id={}", id);
        return user;
    }

    @Override
    public void toggleEnabled(Long id, boolean enabled) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在 id=" + id);
        }
        user.setEnabled(enabled);
        userMapper.updateById(user);
        log.info("toggleEnabled: id={} enabled={}", id, enabled);
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在 id=" + id);
        }
        user.setPasswordHash(PasswordUtil.encode(newPassword));
        userMapper.updateById(user);
        log.info("resetPassword: id={}", id);
    }
}
