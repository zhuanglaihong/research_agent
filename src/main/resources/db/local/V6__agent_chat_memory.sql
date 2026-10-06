create table agent_chat_memory (
    memory_id varchar(96) primary key,
    messages_json clob not null,
    updated_at timestamp not null default current_timestamp
);
