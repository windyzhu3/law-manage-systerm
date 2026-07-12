create table if not exists todo_template (
  template_id bigint not null auto_increment, template_code varchar(64) not null, template_name varchar(128) not null,
  business_type varchar(32) not null, current_version int not null default 0, status char(1) not null default '0',
  create_by varchar(64), create_time datetime not null default current_timestamp, update_by varchar(64), update_time datetime,
  primary key (template_id), unique key uk_todo_template_code (template_code)
) engine=innodb comment='待办模板';

create table if not exists todo_template_version (
  version_id bigint not null auto_increment, template_id bigint not null, version_no int not null,
  owner_rule_json json, dod_rule_json json, sla_rule_json json, next_rule_json json, ui_schema_json json,
  published_by varchar(64), published_time datetime, create_time datetime not null default current_timestamp,
  primary key (version_id), unique key uk_todo_template_version (template_id, version_no)
) engine=innodb comment='待办模板版本';

create table if not exists todo_trigger_rule (
  trigger_rule_id bigint not null auto_increment, event_type varchar(64) not null, template_id bigint not null,
  template_version_id bigint not null, business_type varchar(32) not null, enabled char(1) not null default 'Y',
  condition_json json, create_time datetime not null default current_timestamp,
  primary key (trigger_rule_id), unique key uk_todo_trigger_rule (event_type, template_version_id, business_type)
) engine=innodb comment='待办事件触发规则';

create table if not exists todo_instance (
  todo_id bigint not null auto_increment, todo_no varchar(64) not null, template_id bigint not null,
  template_version_id bigint not null, title varchar(200) not null, business_type varchar(32) not null,
  business_id bigint not null, business_no varchar(64), business_stage varchar(64), owner_id bigint, owner_dept_id bigint,
  status varchar(24) not null, priority varchar(16) not null default 'NORMAL', sla_status varchar(24) not null default 'NORMAL',
  created_at datetime not null default current_timestamp, claimed_at datetime, started_at datetime, submitted_at datetime,
  due_at datetime, completed_at datetime, cancelled_at datetime, previous_todo_id bigint, root_todo_id bigint,
  trigger_event_id varchar(64), trigger_idempotency_key varchar(200), next_idempotency_key varchar(200),
  dod_snapshot_json json, version int not null default 0, create_by varchar(64), update_by varchar(64), update_time datetime,
  primary key (todo_id), unique key uk_todo_no (todo_no),
  unique key uk_todo_trigger_idempotency (trigger_idempotency_key), unique key uk_todo_next_idempotency (next_idempotency_key),
  key idx_todo_owner_queue (owner_id, status, due_at, todo_id), key idx_todo_sla_queue (status, sla_status, due_at, todo_id)
) engine=innodb comment='待办实例';

create table if not exists todo_candidate (
  candidate_id bigint not null auto_increment, todo_id bigint not null, candidate_type varchar(16) not null,
  candidate_value bigint not null, create_time datetime not null default current_timestamp,
  primary key (candidate_id), unique key uk_todo_candidate (todo_id, candidate_type, candidate_value),
  key idx_todo_candidate_queue (candidate_type, candidate_value, todo_id)
) engine=innodb comment='待办候选人';

create table if not exists todo_cc (
  cc_id bigint not null auto_increment, todo_id bigint not null, user_id bigint not null, cc_type varchar(16) not null default 'CC',
  create_time datetime not null default current_timestamp, primary key (cc_id), unique key uk_todo_cc (todo_id, user_id, cc_type)
) engine=innodb comment='待办抄送';

create table if not exists todo_relation (
  relation_id bigint not null auto_increment, todo_id bigint not null, business_type varchar(32) not null,
  business_id bigint not null, business_no varchar(64), relation_type varchar(24) not null default 'PRIMARY',
  primary key (relation_id), unique key uk_todo_relation (todo_id, business_type, business_id, relation_type),
  key idx_todo_business_relation (business_type, business_id, todo_id)
) engine=innodb comment='待办业务关联';

create table if not exists todo_action_log (
  action_log_id bigint not null auto_increment, todo_id bigint not null, action_id varchar(64) not null,
  action_type varchar(24) not null, from_status varchar(24), to_status varchar(24), operator_id bigint not null,
  operator_name varchar(64), opinion varchar(1000), payload_json json, create_time datetime not null default current_timestamp,
  primary key (action_log_id), unique key uk_todo_action_idempotency (action_id), key idx_todo_action_timeline (todo_id, create_time, action_log_id)
) engine=innodb comment='待办动作日志';

create table if not exists todo_attachment (
  attachment_id bigint not null auto_increment, todo_id bigint not null, action_id varchar(64), attachment_type varchar(32) not null,
  file_name varchar(255) not null, file_url varchar(500) not null, uploader_id bigint, create_time datetime not null default current_timestamp,
  primary key (attachment_id), key idx_todo_attachment (todo_id, attachment_type)
) engine=innodb comment='待办附件';

create table if not exists todo_sla_record (
  sla_record_id bigint not null auto_increment, todo_id bigint not null, calendar_id bigint,
  start_at datetime not null, due_at datetime not null, paused_at datetime, paused_seconds bigint not null default 0,
  remind80_at datetime, overdue100_at datetime, escalate150_at datetime, status varchar(24) not null default 'RUNNING',
  version int not null default 0, primary key (sla_record_id), unique key uk_todo_sla (todo_id),
  key idx_todo_sla_record_queue (status, due_at, todo_id)
) engine=innodb comment='待办SLA记录';

create table if not exists todo_work_calendar (
  calendar_id bigint not null auto_increment, calendar_code varchar(64) not null, calendar_name varchar(128) not null,
  timezone varchar(64) not null default 'Asia/Shanghai', work_days varchar(32) not null default '1,2,3,4,5',
  work_start time not null default '09:00:00', work_end time not null default '18:00:00', exception_json json,
  status char(1) not null default '0', create_time datetime not null default current_timestamp, update_time datetime,
  primary key (calendar_id), unique key uk_todo_calendar_code (calendar_code)
) engine=innodb comment='待办工作日历';
