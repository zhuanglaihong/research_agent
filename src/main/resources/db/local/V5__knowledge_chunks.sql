create table knowledge_chunk (
    id bigint auto_increment primary key,
    project_id bigint not null,
    document_id bigint not null,
    chunk_index integer not null,
    content clob not null,
    constraint fk_chunk_project foreign key (project_id) references research_project(id),
    constraint fk_chunk_document foreign key (document_id) references knowledge_document(id),
    unique key uk_chunk_document_index (document_id, chunk_index)
);
create index idx_chunk_project on knowledge_chunk(project_id, document_id);
