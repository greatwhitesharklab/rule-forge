-- 创建rf_project表
create table rf_project (
    id bigint auto_increment primary key,
    name varchar(128) null,
    is_lock tinyint(1) default 0 null,
    create_time datetime null,
    update_time datetime null
);

-- 如果rf_file表中有file_type=1的记录，则迁移到rf_project表
insert into rf_project(id, name, is_lock, create_time)
select id, name, 0, create_time
from rf_file
where file_type = 1;

-- 为rf_project表添加锁定版本字段
alter table rf_project
    add lock_version bigint default 0 null after is_lock;

-- 为rf_project表添加锁定用户字段
alter table rf_project
    add lock_user varchar(64) null after lock_version;

-- 创建rf_lock表
create table rf_lock
(
    id            bigint auto_increment,
    lock_resource varchar(128) null,
    create_time   datetime     null,
    constraint rf_lock_pk_2
        primary key (id),
    constraint rf_lock_pk
        unique (lock_resource)
);

-- 为rf_file_version表添加version_num_real字段
alter table rf_file_version
    add version_num bigint null after file_comment;

alter table rf_file_version
    add version_num_real bigint null after version_num;
