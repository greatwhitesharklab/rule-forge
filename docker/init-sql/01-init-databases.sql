-- RuleForge 数据库初始化脚本
-- Docker Compose 首次启动时自动执行

CREATE DATABASE IF NOT EXISTS app_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ruleforge_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- Flowable 单独建库,避免和 Flyway 管理的 ruleforge_db 抢同一张 schema。
-- Flyway 管 gr_* 表,Flowable 自己的 SQL 脚本管 act_* 表,各管各的不冲突。
CREATE DATABASE IF NOT EXISTS flowable_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON app_db.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON ruleforge_db.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON flowable_db.* TO 'root'@'%';

FLUSH PRIVILEGES;
