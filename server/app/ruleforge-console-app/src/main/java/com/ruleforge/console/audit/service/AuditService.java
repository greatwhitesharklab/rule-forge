package com.ruleforge.console.audit.service;

import com.ruleforge.console.app.entity.UserEntity;

import java.util.List;

/**
 * V5.17 user/permission audit log 服务。
 *
 * <p>每个 user-mgmt 操作点(createUser / updateUser / toggleEnabled /
 * resetPassword / savePermissions / login success / login fail)
 * 都有对应的 {@code log*} 方法。
 *
 * <p>设计原则:
 * <ul>
 *   <li><b>无返回值</b> — audit log 写入是 fire-and-forget,失败 log.warn
 *       但不抛(避免 audit 故障导致 user-mgmt 主路径异常)</li>
 *   <li><b>密码脱敏</b> — 密码变更只记 {@code fieldName="password"} +
 *       oldValue/newValue=null,不存明文也不存 hash</li>
 *   <li><b>actor 必填</b> — 调用方必须传当前操作人(从 session 拿)</li>
 * </ul>
 */
public interface AuditService {

    void logCreateUser(String actor, UserEntity target);

    /** UPDATE_USER 单字段变更。密码场景 oldValue/newValue 传 null 即可。 */
    void logUpdateUserField(String actor, UserEntity target, String fieldName, String oldValue, String newValue);

    void logToggleEnabled(String actor, UserEntity target, boolean enabled);

    void logResetPassword(String actor, UserEntity target);

    void logSavePermissions(String actor, UserEntity target, int projectCount);

    void logLoginSuccess(String actor, UserEntity target);

    /** 登录失败 — targetUserId 可能为 null(用户不存在场景) */
    void logLoginFail(String actor, String attemptedUsername);

    /**
     * 查询 audit log(倒序,最新在前)。
     *
     * @param actor  可选过滤;null/空 = 不过滤
     * @param action 可选过滤;null/空 = 不过滤
     * @param limit  最大条数(由 controller clamp 到 500)
     */
    List<com.ruleforge.console.audit.entity.AuditLogEntity> listAuditLogs(String actor, String action, int limit);
}
