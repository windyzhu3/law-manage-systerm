alter table todo_trigger_rule add column rule_code varchar(64) null after trigger_rule_id;
alter table todo_trigger_rule add column rule_name varchar(128) null after rule_code;

update todo_trigger_rule
set rule_code=concat('TRIGGER_',trigger_rule_id),
    rule_name=concat('Trigger rule ',trigger_rule_id)
where rule_code is null or rule_name is null;

alter table todo_trigger_rule modify column rule_code varchar(64) not null;
alter table todo_trigger_rule modify column rule_name varchar(128) not null;
alter table todo_trigger_rule add unique key uk_todo_trigger_rule_code (rule_code);
