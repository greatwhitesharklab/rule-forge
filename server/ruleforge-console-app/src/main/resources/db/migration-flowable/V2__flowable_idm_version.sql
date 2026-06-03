-- Flowable 8.0.0 多引擎的 schema version 标记
--
-- Flowable 8.0.0 把 engine 拆成 4 个:
--   - Common (flowable-engine-common)   → ACT_GE_PROPERTY.common.schema.version
--   - Process (flowable-engine)         → ACT_GE_PROPERTY.schema.version
--   - EventRegistry (flowable-event-registry) → ACT_GE_PROPERTY.eventregistry.schema.version
--   - IDM (flowable-idm-engine)         → ACT_ID_PROPERTY.schema.version
--
-- V1 已经在 ACT_GE_PROPERTY 里塞了 schema.version=8.0.0.0 (process engine 用的)
-- 但 Common 和 EventRegistry 用的 key 不同,IDM 用的表也不同,这里补齐。

insert into ACT_GE_PROPERTY (NAME_, VALUE_, REV_) values ('common.schema.version', '8.0.0.0', 1);
insert into ACT_GE_PROPERTY (NAME_, VALUE_, REV_) values ('eventregistry.schema.version', '8.0.0.0', 1);

-- IDM 用自己的 ACT_ID_PROPERTY,存的是同样的 schema.version 字段
create table if not exists ACT_ID_PROPERTY (
    NAME_ varchar(64),
    VALUE_ varchar(300),
    REV_ integer,
    primary key (NAME_)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE utf8_bin;

insert into ACT_ID_PROPERTY (NAME_, VALUE_, REV_) values ('schema.version', '8.0.0.0', 1);
