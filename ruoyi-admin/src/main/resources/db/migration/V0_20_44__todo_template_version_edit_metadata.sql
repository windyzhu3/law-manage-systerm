alter table todo_template_version
  add column update_by varchar(64) null after published_time,
  add column update_time datetime null after update_by,
  add key idx_todo_template_version_recent_edit(status,update_time,version_id);
