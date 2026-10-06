create table knowledge_document (
    id bigint auto_increment primary key,
    project_id bigint not null,
    source varchar(240) not null,
    content clob not null,
    created_at datetime not null default current_timestamp,
    constraint fk_knowledge_project foreign key (project_id) references research_project(id)
);
create index idx_knowledge_project on knowledge_document(project_id, id);
