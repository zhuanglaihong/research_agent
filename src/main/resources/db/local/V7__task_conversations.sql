alter table research_task add column conversation_id bigint null;
update research_task set conversation_id=id where conversation_id is null;
alter table research_task alter column conversation_id set not null;
create index idx_research_task_conversation_created on research_task(conversation_id,created_at);
