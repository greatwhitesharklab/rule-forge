-- 监控与告警模块表

-- 指标快照（1分钟聚合）
create table nd_metrics_snapshot
(
    id             bigint auto_increment primary key,
    metric_name    varchar(128)  not null comment '指标名，如 rule.execution.latency',
    metric_type    varchar(32)   not null comment 'TIMER, COUNTER, GAUGE',
    tags           varchar(512)  null     comment 'JSON标签，如 {"package":"loan-rules","status":"SUCCESS"}',
    snapshot_time  datetime      not null comment '聚合窗口时间',
    p50_ms         bigint        null     comment 'P50延迟（TIMER）',
    p95_ms         bigint        null     comment 'P95延迟（TIMER）',
    p99_ms         bigint        null     comment 'P99延迟（TIMER）',
    mean_ms        double        null     comment '平均延迟（TIMER）',
    max_ms         bigint        null     comment '最大延迟（TIMER）',
    min_ms         bigint        null     comment '最小延迟（TIMER）',
    count_val      bigint        null     comment '事件数',
    total_ms       double        null     comment '累计时间（TIMER）',
    gauge_val      double        null     comment '当前值（GAUGE）',
    created_at     datetime      not null default current_timestamp,
    index idx_metric_name_time (metric_name, snapshot_time),
    index idx_snapshot_time (snapshot_time)
) comment '1分钟聚合指标快照';

-- 告警规则
create table nd_alert_rule
(
    id              bigint auto_increment primary key,
    name            varchar(128)  not null comment '规则名称',
    enabled         tinyint(1)    not null default 1,
    metric_name     varchar(128)  not null comment '监控的指标名',
    metric_tags     varchar(512)  null     comment 'JSON标签过滤，null=全部',
    `condition`     varchar(16)   not null comment 'GT, LT, GTE, LTE, EQ',
    threshold       double        not null comment '阈值',
    duration_min    int           not null default 1 comment '连续触发窗口数',
    webhook_url     varchar(512)  not null comment 'Webhook 地址',
    webhook_headers varchar(1024) null     comment 'JSON请求头，如 {"Authorization":"Bearer x"}',
    cooldown_min    int           not null default 10 comment '冷却时间（分钟）',
    last_fired_at   datetime      null     comment '上次触发时间',
    created_at      datetime      not null default current_timestamp,
    updated_at      datetime      null     on update current_timestamp,
    index idx_enabled (enabled),
    index idx_metric_name (metric_name)
) comment '告警规则定义';

-- 告警历史
create table nd_alert_history
(
    id              bigint auto_increment primary key,
    alert_rule_id   bigint        not null,
    rule_name       varchar(128)  not null comment '触发时的规则名称快照',
    metric_name     varchar(128)  not null,
    actual_value    double        not null comment '实际值',
    threshold       double        not null comment '阈值',
    webhook_url     varchar(512)  not null,
    webhook_status  int           null     comment 'Webhook HTTP状态码',
    webhook_response varchar(1024) null    comment 'Webhook响应（截断）',
    fired_at        datetime      not null default current_timestamp,
    index idx_alert_rule_id (alert_rule_id),
    index idx_fired_at (fired_at)
) comment '告警触发历史';
