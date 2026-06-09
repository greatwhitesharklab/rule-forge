package com.ruleforge.console.audit.service;

import com.ruleforge.console.app.entity.UserEntity;
import com.ruleforge.console.audit.entity.AuditLogEntity;
import com.ruleforge.console.audit.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * V5.17 audit log 实现 — 简单 INSERT + 查询。
 *
 * <p>写入路径:每个 user-mgmt 操作在 controller / service 调 {@code log*},
 * 失败 catch + log.warn,**不**抛(避免 audit 故障导致 user-mgmt 主路径异常;
 * 跟 V5.10-C dualWriteFailureRepository.insert 同一设计原则)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogMapper auditLogMapper;

    @Override
    public void logCreateUser(String actor, UserEntity target) {
        insert(AuditLogEntity.builder()
                .actor(actor)
                .action("CREATE_USER")
                .targetUserId(target.getId())
                .targetUsername(target.getUsername())
                .build());
    }

    @Override
    public void logUpdateUserField(String actor, UserEntity target, String fieldName, String oldValue, String newValue) {
        // 密码场景:oldValue/newValue 必为 null(接口约定)
        insert(AuditLogEntity.builder()
                .actor(actor)
                .action("UPDATE_USER")
                .targetUserId(target.getId())
                .targetUsername(target.getUsername())
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .build());
    }

    @Override
    public void logToggleEnabled(String actor, UserEntity target, boolean enabled) {
        insert(AuditLogEntity.builder()
                .actor(actor)
                .action("TOGGLE_ENABLED")
                .targetUserId(target.getId())
                .targetUsername(target.getUsername())
                .fieldName("is_enabled")
                .oldValue(String.valueOf(!enabled))
                .newValue(String.valueOf(enabled))
                .build());
    }

    @Override
    public void logResetPassword(String actor, UserEntity target) {
        // 密码脱敏:只记事件,old/new 都 null
        insert(AuditLogEntity.builder()
                .actor(actor)
                .action("RESET_PASSWORD")
                .targetUserId(target.getId())
                .targetUsername(target.getUsername())
                .fieldName("password")
                .oldValue(null)
                .newValue(null)
                .note("admin reset password (hash not stored)")
                .build());
    }

    @Override
    public void logSavePermissions(String actor, UserEntity target, int projectCount) {
        insert(AuditLogEntity.builder()
                .actor(actor)
                .action("SAVE_PERMISSIONS")
                .targetUserId(target.getId())
                .targetUsername(target.getUsername())
                .note("批量保存 " + projectCount + " 个项目权限 (V5.17 仅记 count;per-project 行留 V5.18)")
                .build());
    }

    @Override
    public void logLoginSuccess(String actor, UserEntity target) {
        insert(AuditLogEntity.builder()
                .actor(actor)
                .action("LOGIN_SUCCESS")
                .targetUserId(target.getId())
                .targetUsername(target.getUsername())
                .build());
    }

    @Override
    public void logLoginFail(String actor, String attemptedUsername) {
        insert(AuditLogEntity.builder()
                .actor(actor)
                .action("LOGIN_FAIL")
                .targetUserId(null)
                .targetUsername(attemptedUsername)
                .build());
    }

    @Override
    public List<AuditLogEntity> listAuditLogs(String actor, String action, int limit) {
        return auditLogMapper.selectListByFilters(actor, action, limit);
    }

    /**
     * Fire-and-forget 写入:异常 catch + log.warn,**不**抛。
     * 跟 V5.10-C dualWriteFailureRepository 同款设计(避免 audit 故障
     * 导致 user-mgmt 主路径异常)。
     */
    private void insert(AuditLogEntity row) {
        try {
            auditLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("audit log insert 失败 action={} actor={}, 忽略: {}",
                    row.getAction(), row.getActor(), e.getMessage());
        }
    }
}
