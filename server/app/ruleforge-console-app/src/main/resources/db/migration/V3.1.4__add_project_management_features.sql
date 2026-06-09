-- 创建gr_project表
create table gr_project (
    id bigint auto_increment primary key,
    name varchar(128) null,
    is_lock tinyint(1) default 0 null,
    create_time datetime null,
    update_time datetime null
);

-- 如果gr_file表中有file_type=1的记录，则迁移到gr_project表
insert into gr_project(id, name, is_lock, create_time)
select id, name, 0, create_time
from gr_file
where file_type = 1;

-- 为gr_project表添加锁定版本字段
alter table gr_project
    add lock_version bigint default 0 null after is_lock;

-- 为gr_project表添加锁定用户字段
alter table gr_project
    add lock_user varchar(64) null after lock_version;

-- 创建gr_lock表
create table gr_lock
(
    id            bigint auto_increment,
    lock_resource varchar(128) null,
    create_time   datetime     null,
    constraint gr_lock_pk_2
        primary key (id),
    constraint gr_lock_pk
        unique (lock_resource)
);

-- 为gr_file_version表添加version_num_real字段
alter table gr_file_version
    add version_num bigint null after file_comment;

alter table gr_file_version
    add version_num_real bigint null after version_num;