create table if not exists todo_exception_log (
  exception_log_id bigint not null auto_increment,
  todo_id bigint not null,
  action_id varchar(64) not null,
  operation_type varchar(32) not null,
  from_status varchar(24),
  operator_id bigint not null,
  operator_name varchar(64) not null,
  operator_dept_id bigint,
  reason varchar(1000) not null,
  payload_json json,
  create_time datetime not null default current_timestamp,
  primary key (exception_log_id),
  unique key uk_todo_exception_action (action_id),
  key idx_todo_exception_timeline (todo_id,create_time,exception_log_id)
) engine=innodb comment='待办受控异常操作记录';

create table if not exists todo_sla_waiver (
  waiver_id bigint not null auto_increment,
  todo_id bigint not null,
  action_id varchar(64) not null,
  original_due_at datetime not null,
  new_due_at datetime not null,
  operator_id bigint not null,
  operator_name varchar(64) not null,
  operator_dept_id bigint,
  reason varchar(1000) not null,
  create_time datetime not null default current_timestamp,
  primary key (waiver_id),
  unique key uk_todo_sla_waiver_action (action_id),
  key idx_todo_sla_waiver_timeline (todo_id,create_time,waiver_id),
  key idx_todo_sla_waiver_due (new_due_at,todo_id)
) engine=innodb comment='待办SLA豁免记录';

alter table todo_notification add column source_id varchar(64) not null default 'DEFAULT' after notification_type;
alter table todo_notification drop index uk_todo_notification;
alter table todo_notification add unique key uk_todo_notification_source(todo_id,user_id,notification_type,source_id);
