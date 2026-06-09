package com.ruleforge.console.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
                getterVisibility = JsonAutoDetect.Visibility.NONE,
                isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class UserEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("username")
    private String username;

    @TableField("password_hash")
    @JsonIgnore
    private String passwordHash;

    @TableField("company_id")
    private String companyId;

    @TableField("is_admin")
    @JsonProperty("isAdmin")
    private boolean isAdmin;

    @TableField("is_enabled")
    @JsonProperty("isEnabled")
    private boolean isEnabled;

    @TableField("can_import")
    @JsonProperty("canImport")
    private boolean canImport;

    @TableField("can_export")
    @JsonProperty("canExport")
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
