alter table todo_trigger_rule
  add column sort_order int not null default 0 after condition_json,
  add column create_by varchar(64) null after sort_order,
  add column update_by varchar(64) null after create_by,
  add column update_time datetime null after update_by,
  add key idx_todo_trigger_rule_sort (sort_order,trigger_rule_id);
