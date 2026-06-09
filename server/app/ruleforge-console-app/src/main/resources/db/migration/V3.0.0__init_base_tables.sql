create table gr_file
(
    id          bigint auto_increment,
    name        varchar(128) null,
    file_type   int default 0 null,
    create_time datetime null,
    update_time datetime null,
    constraint gr_file_pk
        primary key (id)
);

create table gr_file_relation
(
    id         bigint auto_increment
        primary key,
    ancestor   bigint null,
    descendant bigint null,
    distance   int null
);

create table gr_file_version
(
    id             bigint auto_increment
        primary key,
    file_path      varchar(256) null,
    file_name      varchar(64)  null,
    file_comment   varchar(128) null,
    before_comment varchar(128) null,
    after_comment  varchar(128) null,
    audit_status   varchar(64)  null,
    create_user    varchar(64)  null,
    create_date    datetime     null,
    update_time    datetime     null
);