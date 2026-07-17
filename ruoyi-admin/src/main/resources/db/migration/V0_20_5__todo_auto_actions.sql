alter table todo_action_log add column action_source varchar(16) not null default 'HUMAN' after action_type;

create table todo_auto_action_execution (
  execution_key varchar(300) not null,
  todo_id bigint not null,
  rule_key varchar(96) not null,
  action_type varchar(32) not null,
  status varchar(16) not null,
  attempt_count int not null default 1,
  claimed_at datetime not null,
  next_retry_at datetime null,
  completed_at datetime null,
  last_error_code varchar(96) null,
  last_error_message varchar(1000) null,
  create_time datetime not null default current_timestamp,
  update_time datetime not null default current_timestamp,
  primary key (execution_key),
  key idx_todo_auto_action_due (status,next_retry_at),
  key idx_todo_auto_action_todo (todo_id,rule_key),
  constraint chk_todo_auto_action_type check (action_type in ('COMPLETE_DEFAULT','RETURN_DEFAULT','ESCALATE','TRANSFER','RETURN_POOL','INVALID_RULE')),
  constraint chk_todo_auto_action_execution_status check (status in ('CLAIMED','SUCCESS','RETRY','DEAD')),
  constraint chk_todo_auto_action_attempt check (attempt_count > 0)
) engine=innodb comment='Idempotent controlled auto-action execution claim';

create table todo_auto_action_audit (
  audit_id bigint not null auto_increment,
  execution_key varchar(300) not null,
  attempt_no int not null,
  todo_id bigint not null,
  rule_key varchar(96) not null,
  action_type varchar(32) not null,
  status varchar(16) not null,
  service_actor_id bigint not null,
  service_actor_name varchar(64) not null,
  error_code varchar(96) null,
  error_message varchar(1000) null,
  executed_at datetime not null,
  primary key (audit_id),
  unique key uk_todo_auto_action_audit_attempt (execution_key,attempt_no),
  key idx_todo_auto_action_audit_todo (todo_id,executed_at),
  constraint chk_todo_auto_action_audit_status check (status in ('SUCCESS','RETRY','DEAD'))
) engine=innodb comment='Append-only controlled auto-action result audit';
