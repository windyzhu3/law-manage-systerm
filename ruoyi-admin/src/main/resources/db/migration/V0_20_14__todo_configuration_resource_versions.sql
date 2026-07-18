alter table todo_trigger_rule add column version int not null default 0 after condition_json;
alter table todo_work_calendar add column version int not null default 0 after status;
create index idx_todo_trigger_rule_version on todo_trigger_rule(trigger_rule_id,version);
create index idx_todo_calendar_version on todo_work_calendar(calendar_id,version);
