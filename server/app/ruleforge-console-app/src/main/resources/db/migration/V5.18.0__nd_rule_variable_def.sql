-- V5.18.0: 重建 nd_rule_variable_def 表
--
-- 背景: DecisionServiceImpl.findAll() (executor-app 决策热路径) 查 nd_rule_variable_def,
-- 但这张表**从未**在任何 Flyway migration 里创建。RuleVariableDef entity + mapper
-- 存在,代码假定表存在,executor 启动 OK,只要 /api/loan/evaluate 一调就
-- "Table 'ruleforge_db.nd_rule_variable_def' doesn't exist" — 整个生产决策
-- 路径 100% 跑不起来。
--
-- 这张表原本可能是手工建在 dev 环境的(看 entity 注释 "根据 nd_rule_variable_def
-- 表生成的实体" 像 MyBatis-Plus auto-generator 的产物),但从来没进 git / Flyway。
--
-- 字段对齐: RuleVariableDef entity 14 个字段,1:1 映射(clazz/name/label/datatype/
-- act/default_value/ds_status/format_hint/sort_no/ext_json + 4 个时间戳/审计字段)。
-- 索引: (clazz, sort_no) 用于 DecisionServiceImpl.prepareVariableDefinitions
-- 分组 + 排序查询。

CREATE TABLE IF NOT EXISTS nd_rule_variable_def (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    clazz         VARCHAR(256) NOT NULL COMMENT '实体类全限定名,如 com.ruleforge.ADVModel',
    name          VARCHAR(128) NOT NULL COMMENT '字段名',
    label         VARCHAR(256)          DEFAULT NULL COMMENT '字段中文名',
    datatype      VARCHAR(64)           DEFAULT NULL COMMENT 'String/Long/Integer/Double/Boolean/Date/BigDecimal',
    act           VARCHAR(32)           DEFAULT NULL COMMENT 'I=input, O=output, IO=both',
    default_value VARCHAR(512)          DEFAULT NULL COMMENT '默认值',
    ds_status     INT                   DEFAULT 0    COMMENT '数据源状态: 0=不查,1=实时查,2=缓存',
    format_hint   VARCHAR(128)          DEFAULT NULL COMMENT '展示格式提示,如 #,##0.00',
    sort_no       INT                   DEFAULT 0    COMMENT '排序号',
    ext_json      TEXT                  DEFAULT NULL COMMENT '扩展 JSON,复杂场景用',
    created_at    DATETIME              DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by    VARCHAR(64)           DEFAULT NULL,
    updated_by    VARCHAR(64)           DEFAULT NULL,
    INDEX idx_clazz_sort (clazz, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规则变量定义(executor 决策热路径依赖此表)';
