ALTER TABLE gr_executor_node ADD COLUMN node_group VARCHAR(32) NOT NULL DEFAULT 'default' AFTER exec_env;
CREATE INDEX idx_node_group ON gr_executor_node(node_group);
