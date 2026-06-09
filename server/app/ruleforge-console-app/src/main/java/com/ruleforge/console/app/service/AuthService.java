package com.ruleforge.console.app.service;

import com.ruleforge.console.app.entity.UserEntity;

import java.util.List;

/**
 * 认证服务 — V5.15 权限改造
 *
 * <p>负责 BCrypt 密码验证 + 用户 CRUD。
 * LoginController 调 {@link #login} 做密码校验,
 * PermissionController 调 CRUD 做用户管理。
 */
public interface AuthService {

    /**
     * 密码认证:查 DB + BCrypt 校验。
     * @return 认证成功返 UserEntity,失败(null 用户 / 密码错 / 被禁用)返 null
     */
    UserEntity login(String username, String password);

    /**
     * 按用户名查 DB
     */
    UserEntity findByUsername(String username);

    /**
     * 按 ID 查 DB
     */
    UserEntity findById(Long id);

    /**
     * 列出所有启用用户(admin 用户管理面板用)
     */
    List<UserEntity> listUsers();

    /**
     * 创建新用户(BCrypt hash 密码)
     * @param actor 操作人(V5.17 audit 用),admin 用户名
     * @throws IllegalArgumentException username 已存在
     */
    UserEntity createUser(String actor, String username, String password, boolean isAdmin, boolean canExport);

    /**
     * 修改用户信息(密码为空则不修改)
     * @param actor 操作人(V5.17 audit 用)
     */
    UserEntity updateUser(String actor, Long id, String newPassword, Boolean isAdmin, Boolean canImport, Boolean canExport);

    /**
     * 启用/禁用用户(不物理删)
     * @param actor 操作人(V5.17 audit 用)
     */
    void toggleEnabled(String actor, Long id, boolean enabled);

    /**
     * 重置密码
     * @param actor 操作人(V5.17 audit 用)
     */
    void resetPassword(String actor, Long id, String newPassword);
}
