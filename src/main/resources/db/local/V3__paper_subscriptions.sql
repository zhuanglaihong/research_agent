create table paper_subscription (
    project_id bigint primary key,
    topic varchar(100) not null,
    enabled boolean not null default false,
    last_checked datetime null,
    last_error varchar(500) null,
    constraint fk_paper_subscription_project foreign key (project_id) references research_project(id)
);

create table paper_item (
    id bigint auto_increment primary key,
    project_id bigint not null,
    arxiv_id varchar(60) not null,
    title varchar(500) not null,
    abstract_text text not null,
    url varchar(500) not null,
    published_at datetime null,
    created_at datetime not null default current_timestamp,
    constraint fk_paper_item_project foreign key (project_id) references research_project(id),
    unique key uk_paper_item_project_arxiv (project_id, arxiv_id),
    index idx_paper_item_project_created (project_id, created_at)
);
