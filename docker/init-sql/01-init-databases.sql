-- RuleForge 数据库初始化脚本
-- Docker Compose 首次启动时自动执行
--
-- V5.53:app_db 重命名为 ruleforge_app_db(语义清晰:RuleForge App-side data,
--   跟 ruleforge_db / ruleforge_app_db 命名一致)。
--   旧 app_db 保留作为向后兼容(本次 dev DB drop 后重新建,只留 ruleforge_app_db;
--   docker compose 启动时若 volume 已有 app_db 旧数据可继续用,但新部署一律 ruleforge_app_db)。

CREATE DATABASE IF NOT EXISTS ruleforge_app_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ruleforge_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON ruleforge_app_db.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON ruleforge_db.* TO 'root'@'%';

FLUSH PRIVILEGES;
