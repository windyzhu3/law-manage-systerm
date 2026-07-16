alter table todo_template_version
  add column definition_schema_version int not null default 1 after ui_schema_json,
  add column definition_json json null after definition_schema_version,
  add column compiled_json json null after definition_json,
  add column definition_hash char(64) null after compiled_json,
  add column validation_report_json json null after definition_hash,
  modify column published_by varchar(64) null,
  modify column published_time datetime null,
  add key idx_todo_definition_status(status,template_id,version_no),
  add key idx_todo_definition_hash(definition_hash);
