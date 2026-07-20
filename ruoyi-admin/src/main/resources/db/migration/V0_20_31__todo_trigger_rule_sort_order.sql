alter table todo_trigger_rule
  add column sort_order int not null default 0 after condition_json,
  add key idx_todo_trigger_rule_sort (sort_order,trigger_rule_id);
