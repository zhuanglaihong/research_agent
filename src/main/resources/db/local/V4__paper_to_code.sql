create table paper_method (
    id bigint auto_increment primary key,
    project_id bigint not null,
    title varchar(500) not null,
    source varchar(500) not null,
    extracted_text clob not null,
    method_brief clob not null,
    created_at datetime not null default current_timestamp,
    constraint fk_paper_method_project foreign key (project_id) references research_project(id)
);
create index idx_paper_method_project on paper_method(project_id, id);
