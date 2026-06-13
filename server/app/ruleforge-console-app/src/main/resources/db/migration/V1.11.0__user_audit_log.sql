-- V5.17.0 user/permission audit log
--
-- 背景:V5.15 引入 user-mgmt CRUD(创建/修改/启用禁用/重置密码/项目权限),
-- 所有变更只 log.info 一行,事后查不出"谁在什么时候改了哪个用户的什么字段"。
-- V5.17 给 user-mgmt 的所有变更点接 audit 表,跟 V5.10-C
-- (gr_git_dualwrite_failure) 一脉相承:写到独立表 + 简单 query。
--
-- 字段设计要点:
--   * actor_username     — 谁干的(必填),目前所有触发点都在 admin 操作路径
--   * action             — 事件类型(CREATE_USER / UPDATE_USER / TOGGLE_ENABLED /
--                          RESET_PASSWORD / SAVE_PERMISSIONS / LOGIN_SUCCESS /
--                          LOGIN_FAIL)
--   * target_user_id     — 被操作的用户 id(LOGIN_FAIL 拿不到时为 NULL)
--   * target_username    — 冗余存,用户被删/改名后 audit 仍能看懂
--   * field_name         — UPDATE_USER 时记录改的字段(password / is_admin / can_import / can_export)
--   * old_value / new_value — 改之前/之后的值
--                          密码**不**存明文也**不**存 BCrypt hash(只记事件,
--                          old_value/new_value = NULL,field_name='password' 即可)
--   * project            — 涉及项目(预留,SAVE_PERMISSIONS 单 project 留痕
--                          留到 V5.18,V5.17 先记 note 含 count)
--   * note               — 自由备注(SAVE_PERMISSIONS 记 count 等)
--   * occurred_at        — 事件时间,DATETIME(3) 毫秒精度
--
-- 索引选择:
--   * idx_actor_time  — 查"某 admin 干了啥"最常用
--   * idx_target_time — 查"某用户被改了啥"次常用
--   * idx_action_time  — 查"近期 LOGIN_FAIL 频次"做安全监控
--
-- 注意:这张表**只** append,不清(类似 gr_git_dualwrite_failure 老的 V5.12 TTL
-- 30 天清理是另一个 commit,留给 V5.18+)。当前 user-mgmt 操作频次低,1 年
-- 估算几千行,无 TTL 必要。

CREATE TABLE IF NOT EXISTS rf_user_audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    occurred_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    actor_username  VARCHAR(64)  NOT NULL,
    action          VARCHAR(32)  NOT NULL,
    target_user_id  BIGINT       NULL,
    target_username VARCHAR(64)  NULL,
    field_name      VARCHAR(32)  NULL,
    old_value       VARCHAR(255) NULL,
    new_value       VARCHAR(255) NULL,
    project         VARCHAR(128) NULL,
    note            VARCHAR(255) NULL,
    INDEX idx_actor_time (actor_username, occurred_at),
    INDEX idx_target_time (target_user_id, occurred_at),
    INDEX idx_action_time (action, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='V5.17 audit log of user/permission changes';
