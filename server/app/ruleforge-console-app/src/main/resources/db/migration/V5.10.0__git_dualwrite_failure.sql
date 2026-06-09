-- V5.10.0: dualWrite 失败 audit log (5.10-C)
--
-- 之前 dualWrite 失败只 log.error(...),查"过去 1 小时失败多少"完全没法
-- 回答。新表记每次失败事件,ad-hoc 可查、可做监控源、可被 5.10-B 迁移工具
-- 当做"已知待迁移"二次数据源(虽然 5.10-B 主源还是 gr_file_version.git_commit_sha IS NULL)。
--
-- 字段说明:
--   file_path       — 走 DB 的 path,带前导 "/"
--   project_id      — 冗余存,加速 GROUP BY project 聚合
--   file_id         — 冗余存 gr_file.id
--   error_type      — 异常类 simple name (JGit/IOException/etc.)
--   error_message   — 截前 2KB,避免单行爆 64KB
--   branch          — 当时 dualWrite 走的分支 (main / user/xxx)
--   occurred_at     — 失败时刻,DEFAULT CURRENT_TIMESTAMP

CREATE TABLE gr_git_dualwrite_failure (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_path       VARCHAR(512) NOT NULL,
    project_id      BIGINT NULL,
    file_id         BIGINT NULL,
    error_type      VARCHAR(128) NOT NULL,
    error_message   VARCHAR(2048) NULL,
    branch          VARCHAR(128) NULL,
    occurred_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_occurred_at (occurred_at),
    INDEX idx_project_id (project_id),
    INDEX idx_file_path (file_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Audit log of dualWriteToGit failures (DB succeeded but Git write failed)';
