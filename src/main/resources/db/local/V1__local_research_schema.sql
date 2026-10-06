-- Local single-user H2 schema; adapted from MySQL V2/V3 research migrations.
create table research_project
(
    id               bigint auto_increment primary key,
    user_id          bigint       not null,
    name             varchar(160) not null,
    description      text         null,
    workspace_path   varchar(1024) not null,
    default_language varchar(32)  not null default 'python',
    status           varchar(24)  not null default 'ACTIVE',
    created_at       datetime     not null default current_timestamp,
    updated_at       datetime     not null default current_timestamp ,
    deleted_at       datetime     null,
    index idx_research_project_user_updated (user_id, updated_at),
    index idx_research_project_status (status)
) comment '科研项目工作区' ;

create table research_task
(
    id                bigint auto_increment primary key,
    project_id        bigint       not null,
    user_id           bigint       not null,
    title             varchar(240) not null,
    request_text      text         not null,
    task_type         varchar(32)  not null default 'CODING',
    status            varchar(32)  not null default 'QUEUED',
    plan_json         varchar(1000000)         null,
    idempotency_key   varchar(96)  null,
    approval_required tinyint      not null default 1,
    error_code        varchar(64)  null,
    error_message     text         null,
    created_at        datetime     not null default current_timestamp,
    updated_at        datetime     not null default current_timestamp ,
    started_at        datetime     null,
    finished_at       datetime     null,
    constraint fk_research_task_project foreign key (project_id) references research_project (id),
    unique key uk_research_task_idempotency (user_id, idempotency_key),
    index idx_research_task_project_created (project_id, created_at),
    index idx_research_task_status_updated (status, updated_at)
) comment '科研助手任务' ;

create table task_event
(
    id         bigint auto_increment primary key,
    task_id    bigint      not null,
    event_type varchar(48) not null,
    payload    varchar(1000000)        not null,
    created_at datetime    not null default current_timestamp,
    constraint fk_task_event_task foreign key (task_id) references research_task (id) on delete cascade,
    index idx_task_event_task_id (task_id, id)
) comment '科研任务进度事件' ;

create table experiment_run
(
    id                bigint auto_increment primary key,
    task_id           bigint        not null,
    run_key           varchar(96)   not null,
    runtime_language  varchar(32)   not null,
    working_directory varchar(1024) not null,
    command_json      varchar(1000000)          not null,
    status            varchar(24)   not null default 'QUEUED',
    process_id        bigint        null,
    exit_code         int           null,
    code_revision     varchar(64)   null,
    stdout_path       varchar(1024) null,
    stderr_path       varchar(1024) null,
    result_path       varchar(1024) null,
    created_at        datetime      not null default current_timestamp,
    started_at        datetime      null,
    finished_at       datetime      null,
    heartbeat_at      datetime      null,
    constraint fk_experiment_run_task foreign key (task_id) references research_task (id),
    unique key uk_experiment_run_key (run_key),
    index idx_experiment_run_task_created (task_id, created_at),
    index idx_experiment_run_status_heartbeat (status, heartbeat_at)
) comment '科研实验运行记录' ;

alter table research_task add column result_text longtext null;
alter table research_task add column output_path varchar(1024) null;
alter table research_task add column provider varchar(24) not null default 'live';

