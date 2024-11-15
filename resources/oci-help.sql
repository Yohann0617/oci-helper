--用户表
create table if not exists `oci_user`
(
    id              varchar(64)  not null,
    username        varchar(64)  null,
    oci_tenant_id   varchar(64)  null,
    oci_user_id     varchar(64)  null,
    oci_fingerprint varchar(64)  not null,
    oci_region      varchar(32)  not null,
    oci_key_path    varchar(256) not null,
    create_time     datetime default (datetime('now', 'localtime')) not null,
    primary key ("id")
);
CREATE INDEX idx_create_time ON oci_user (create_time DESC);

--开机任务表
create table if not exists `oci_create_task`
(
    id               varchar(64) not null,
    user_id          varchar(64) null,
    ocpus            REAL        DEFAULT 1.0,
    memory           REAL        DEFAULT 6.0,
    disk             INTEGER     DEFAULT 50,
    architecture     varchar(64) DEFAULT 'ARM',
    interval         INTEGER     DEFAULT 60,
    create_numbers   INTEGER     DEFAULT 1,
    root_password    varchar(64),
    operation_system varchar(64) DEFAULT 'Ubuntu',
    create_time      datetime    default (datetime('now', 'localtime')) not null,
    primary key ("id")
);
CREATE INDEX idx_create_time ON oci_create_task (create_time DESC);