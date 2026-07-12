alter table todo_template_version
  add column status varchar(16) not null default 'PUBLISHED' after version_no,
  add column source_version_id bigint null after status,
  add key idx_todo_template_version_status(template_id,status,version_no),
  add key idx_todo_template_version_source(source_version_id);

create table todo_definition_action (
  definition_action_id bigint not null auto_increment,
  action_id varchar(64) not null,
  action_type varchar(32) not null,
  entity_type varchar(24) not null,
  entity_id bigint,
  source_entity_id bigint,
  operator_id bigint not null,
  operator_name varchar(64) not null,
  payload_json json,
  create_time datetime not null default current_timestamp,
  primary key(definition_action_id),
  unique key uk_todo_definition_action(action_id),
  key idx_todo_definition_entity(entity_type,entity_id,create_time)
) engine=innodb comment='待办定义操作幂等与审计';
