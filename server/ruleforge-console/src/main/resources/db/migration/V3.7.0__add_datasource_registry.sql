-- 数据源管理模块表

-- 1. 数据源注册中心
create table nd_datasource
(
    id              bigint auto_increment primary key,
    name            varchar(128)  not null comment '数据源名称',
    type            varchar(32)   not null comment '类型: ADVANCE_AI / REST_API / JDBC',
    config_json     text          not null comment '连接配置(JSON)',
    enabled         tinyint(1)    default 1 comment '是否启用',
    description     varchar(512)  null comment '描述',
    timeout_ms      int           default 30000 comment '超时时间(毫秒)',
    retry_count     int           default 0 comment '重试次数',
    cache_enabled   tinyint(1)    default 1 comment '是否启用API响应缓存',
    cache_ttl_hours int           default 120 comment '缓存有效期(小时)',
    created_at      datetime      default current_timestamp,
    updated_at      datetime      default current_timestamp on update current_timestamp,
    created_by      varchar(64)   null,
    updated_by      varchar(64)   null,
    unique index idx_name (name)
) comment '数据源注册中心';

-- 2. 实体类→数据源映射（每个 clazz 映射到一个数据源）
create table nd_datasource_entity_mapping
(
    id             bigint auto_increment primary key,
    clazz          varchar(256) not null comment '实体类名',
    datasource_id  bigint       not null comment '数据源ID',
    created_at     datetime     default current_timestamp,
    unique index idx_clazz (clazz)
) comment '实体类与数据源映射';

-- 3. 变量字段映射（规则变量名 → 外部字段名，简单别名）
create table nd_datasource_field_mapping
(
    id              bigint auto_increment primary key,
    datasource_id   bigint       not null comment '数据源ID',
    clazz           varchar(256) not null comment '实体类名',
    variable_name   varchar(128) not null comment '规则变量名',
    remote_field    varchar(128) not null comment '外部字段名',
    created_at      datetime     default current_timestamp,
    unique index idx_mapping (datasource_id, clazz, variable_name)
) comment '变量字段映射';

-- 4. 数据源调用日志（用于API响应缓存和审计）
create table nd_datasource_log
(
    id               bigint auto_increment primary key,
    user_id          varchar(64)   not null comment '用户ID',
    datasource_id    bigint        not null comment '关联 nd_datasource.id',
    data_source      varchar(64)   not null comment '数据源标识: ADVANCE_AI / REST_API',
    api_endpoint     varchar(128)  not null comment 'API端点标识',
    request_method   varchar(10)   default 'POST',
    request_data     text          null comment '请求JSON',
    response_data    longtext      null comment '响应JSON',
    http_status      int           null comment 'HTTP状态码',
    status           varchar(16)   not null comment 'SUCCESS/FAILED/ERROR',
    error_message    varchar(1024) null comment '错误信息',
    response_time_ms bigint        null comment '响应耗时(毫秒)',
    request_id       varchar(64)   null comment '请求唯一ID',
    created_at       datetime      default current_timestamp,
    is_deleted       tinyint(1)    default 0,
    index idx_user_ds_endpoint (user_id, data_source, api_endpoint, status, created_at),
    index idx_created_at (created_at)
) comment '数据源调用日志(含缓存)';

-- 种子数据：Advance AI 数据源（accessKey/secretKey 通过管理界面配置）
insert into nd_datasource (name, type, config_json, enabled, description, timeout_ms, cache_enabled, cache_ttl_hours)
values ('advance-ai', 'ADVANCE_AI',
        '{"baseUrl":"https://mex-api.advance.ai","accessKey":"","secretKey":"","tokenValiditySeconds":3600,"tokenExpireBufferMinutes":5}',
        1, 'Advance AI 风控数据源', 30000, 1, 120);
