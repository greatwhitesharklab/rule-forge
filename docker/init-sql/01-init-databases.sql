-- RuleForge 数据库初始化脚本
-- Docker Compose 首次启动时自动执行

CREATE DATABASE IF NOT EXISTS app_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ruleforge_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON app_db.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON ruleforge_db.* TO 'root'@'%';

FLUSH PRIVILEGES;
