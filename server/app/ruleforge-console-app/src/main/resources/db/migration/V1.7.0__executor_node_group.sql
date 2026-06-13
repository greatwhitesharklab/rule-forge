-- V5.53: rename gr_ -> rf_
ALTER TABLE rf_executor_node ADD COLUMN node_group VARCHAR(32) NOT NULL DEFAULT 'default' AFTER exec_env;
CREATE INDEX idx_node_group ON rf_executor_node(node_group);
