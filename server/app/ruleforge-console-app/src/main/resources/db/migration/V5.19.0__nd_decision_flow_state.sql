-- V5.19.0: 自建决策流执行器状态表(替代 Flowable ACT_RU_*)
--
-- 背景: V5.20+ 撤掉 Flowable 8,每条 evaluate 一行 nd_decision_flow_state。同步主路径走完
--       直接 COMPLETED;异步节点(userTask / async)挂起 WAITING_CALLBACK,
--       @Scheduled 30s 扫一次恢复挂起超时的任务。结构照搬 nd_batch_test_session (V5.1.0)。
--
-- 命名遵循 V{Major}.{Feature}.{Fix} 规范(CLAUDE.md)。

CREATE TABLE IF NOT EXISTS nd_decision_flow_state (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    flow_id             VARCHAR(200)  NOT NULL                COMMENT 'BPMN process id, 关联 gr_file',
    flow_run_id         VARCHAR(64)   NOT NULL                COMMENT '单次执行 UUID',
    user_id             VARCHAR(64)   NULL                    COMMENT '触发 evaluate 的用户',
    order_no            VARCHAR(64)   NULL                    COMMENT '业务订单号',
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                        COMMENT 'PENDING/RUNNING/PENDING_ASYNC/WAITING_CALLBACK/COMPLETED/FAILED',
    current_node_id     VARCHAR(200)  NULL                    COMMENT '当前执行到 / 挂起的节点 id',
    current_node_type   VARCHAR(40)   NULL                    COMMENT 'START_EVENT/USER_TASK/...',
    next_retry_at       DATETIME      NULL                    COMMENT 'USER_TASK=NULL(无限等); ASYNC_DATA / ASYNC_TASK=重试时间',
    wait_ref            VARCHAR(200)  NULL                    COMMENT 'userTask 节点 id / callback id / dataSource id',
    wait_type           VARCHAR(20)   NULL                    COMMENT 'USER_TASK / ASYNC_DATA / ASYNC_TASK',
    flow_xml_version    VARCHAR(64)   NULL                    COMMENT 'sourceXml SHA-256, 恢复时校验',
    row_vars            MEDIUMTEXT    NULL                    COMMENT 'ctx.vars JSON 快照,resume 时反序列化',
    row_entity_snapshot MEDIUMTEXT   NULL                    COMMENT 'insertedEntities 序列化,备用',
    output_model        MEDIUMTEXT    NULL                    COMMENT '业务侧 OutputModel 反序列化,备用',
    progress            DOUBLE        DEFAULT 0               COMMENT '0.0 ~ 1.0',
    error_message       TEXT          NULL,
    locked_by           VARCHAR(64)   NULL                    COMMENT 'worker id, 抢占式锁',
    locked_at           DATETIME      NULL,
    locked_until        DATETIME      NULL                    COMMENT '锁过期时间, 默认 5 分钟',
    retry_count         INT           DEFAULT 0,
    total_execution_ms  BIGINT        DEFAULT 0,
    fireable_rules      INT           DEFAULT 0,
    matched_rules       INT           DEFAULT 0,
    create_time         DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_flow_run_id (flow_run_id),
    INDEX idx_status_next_retry (status, next_retry_at),
    INDEX idx_flow_id (flow_id),
    INDEX idx_status_locked_until (status, locked_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='自建决策流执行器状态机(替代 Flowable ACT_RU_*)';
