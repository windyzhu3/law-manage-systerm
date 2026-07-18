alter table todo_trigger_rule add column payload_version int not null default 1 after event_type;
update todo_trigger_rule set payload_version=1 where payload_version is null;
