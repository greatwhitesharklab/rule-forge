package com.ruleforge.console.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 用户实体 — 对应 rf_user 表 (V5.15 权限改造)
 *
 * <p>替代原来 {@code DefaultEnvironmentProvider} 硬编码的 admin 用户,
 * 支持真正的 BCrypt 密码认证 + admin/普通用户区分。
 *
 * <p>Session 里存的仍是 {@link com.ruleforge.console.model.DefaultUser}(接口兼容),
 * 这个 Entity 只在 DB 层使用。
 */
@Data
@TableName("rf_user")
public class UserEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("username")
    private String username;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("company_id")
    private String companyId;

    @TableField("is_admin")
    private boolean isAdmin;

    @TableField("is_enabled")
    private boolean isEnabled;

    @TableField("can_import")
    private boolean canImport;

    @TableField("can_export")
    private boolean canExport;

    @TableField("created_at")
    private Date createdAt;

    @TableField("updated_at")
    private Date updatedAt;

    /**
     * 将 DB entity 转为 session 兼容的 DefaultUser
     */
    public com.ruleforge.console.model.DefaultUser toSessionUser() {
        com.ruleforge.console.model.DefaultUser user = new com.ruleforge.console.model.DefaultUser();
        user.setUsername(username);
        user.setCompanyId(companyId);
        user.setAdmin(isAdmin);
        user.setCanImport(canImport);
        user.setCanExport(canExport);
        return user;
    }
}
