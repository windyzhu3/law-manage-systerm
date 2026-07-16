create table todo_sla_policy_version (
  policy_version_id bigint not null auto_increment,
  template_version_id bigint not null,
  duration_value bigint not null,
  duration_unit varchar(24) not null,
  max_extension_count int not null default 0,
  max_extension_value bigint not null default 0,
  max_extension_unit varchar(24) not null default 'WORKING_DAYS',
  proof_required boolean not null default false,
  pending_sla_mode varchar(16) not null default 'CONTINUE',
  cycle_interval bigint null,
  cycle_unit varchar(24) null,
  cycle_max_occurrences int null,
  status varchar(16) not null default 'ACTIVE',
  create_time datetime not null default current_timestamp,
  primary key (policy_version_id),
  unique key uk_todo_sla_policy_template_version (template_version_id),
  constraint chk_todo_sla_policy_duration_unit check (duration_unit in ('MINUTES','HOURS','CALENDAR_DAYS','WORKING_DAYS')),
  constraint chk_todo_sla_policy_extension_unit check (max_extension_unit in ('MINUTES','HOURS','CALENDAR_DAYS','WORKING_DAYS')),
  constraint chk_todo_sla_policy_pending_mode check (pending_sla_mode='CONTINUE'),
  constraint chk_todo_sla_policy_limits check (duration_value>0 and max_extension_count>=0 and max_extension_value>=0),
  constraint chk_todo_sla_policy_cycle check (cycle_interval is null or (cycle_interval>0 and cycle_unit in ('MINUTES','HOURS','CALENDAR_DAYS','WORKING_DAYS') and cycle_max_occurrences>0))
) engine=innodb comment='Immutable versioned Todo SLA, extension and bounded cycle policy';

create table todo_cycle_occurrence (
  cycle_occurrence_id bigint not null auto_increment,
  todo_id bigint not null,
  policy_version_id bigint not null,
  occurrence_key varchar(160) not null,
  occurrence_no int not null,
  due_at datetime not null,
  status varchar(16) not null default 'SCHEDULED',
  fired_at datetime null,
  create_time datetime not null default current_timestamp,
  primary key (cycle_occurrence_id),
  unique key uk_todo_cycle_occurrence_key (occurrence_key),
  unique key uk_todo_cycle_occurrence_number (todo_id,policy_version_id,occurrence_no),
  key idx_todo_cycle_due (status,due_at,todo_id)
) engine=innodb comment='Bounded idempotent SLA cycle occurrences';

alter table todo_sla_record
  add column original_due_at datetime null after due_at,
  add column remind80_due_at datetime null after paused_seconds,
  add column overdue100_due_at datetime null after remind80_at,
  add column escalate150_due_at datetime null after overdue100_at;
update todo_sla_record set original_due_at=due_at,
  remind80_due_at=date_add(start_at,interval floor(timestampdiff(second,start_at,due_at)*0.8) second),
  overdue100_due_at=due_at,
  escalate150_due_at=date_add(start_at,interval floor(timestampdiff(second,start_at,due_at)*1.5) second)
where original_due_at is null or remind80_due_at is null or overdue100_due_at is null or escalate150_due_at is null;

create table todo_extension_request (
  extension_id bigint not null auto_increment,
  todo_id bigint not null,
  request_action_id varchar(64) not null,
  status varchar(16) not null default 'PENDING',
  original_due_at datetime not null,
  requested_due_at datetime not null,
  approved_due_at datetime null,
  request_reason varchar(1000) not null,
  proof_file_ids_json json not null,
  requester_id bigint not null,
  requester_name varchar(64) not null,
  requester_dept_id bigint null,
  policy_version_id bigint not null,
  pending_sla_mode varchar(16) not null default 'CONTINUE',
  decision_action_id varchar(64) null,
  decision_reason varchar(1000) null,
  decision_actor_id bigint null,
  decision_actor_name varchar(64) null,
  decision_actor_dept_id bigint null,
  decided_at datetime null,
  version int not null default 0,
  create_time datetime not null default current_timestamp,
  pending_todo_id bigint generated always as (case when status='PENDING' then todo_id else null end) stored,
  primary key (extension_id),
  unique key uk_todo_extension_request_action (request_action_id),
  unique key uk_todo_extension_decision_action (decision_action_id),
  unique key uk_todo_extension_one_pending (pending_todo_id),
  key idx_todo_extension_timeline (todo_id,create_time,extension_id),
  constraint chk_todo_extension_status check (status in ('PENDING','APPROVED','REJECTED','CANCELLED')),
  constraint chk_todo_extension_pending_mode check (pending_sla_mode='CONTINUE'),
  constraint chk_todo_extension_due check (requested_due_at>original_due_at)
) engine=innodb comment='Governed normal extension request and decision audit';

create table todo_extension_action (
  action_id varchar(64) not null,
  action_type varchar(16) not null,
  action_status varchar(16) not null default 'CLAIMED',
  todo_id bigint not null,
  extension_id bigint null,
  result_status varchar(16) null,
  actor_id bigint not null,
  actor_name varchar(64) not null,
  actor_dept_id bigint null,
  create_time datetime not null default current_timestamp,
  applied_time datetime null,
  primary key (action_id),
  key idx_todo_extension_action_result (extension_id,action_type),
  constraint chk_todo_extension_action_type check (action_type in ('REQUEST','APPROVE','REJECT')),
  constraint chk_todo_extension_action_status check (action_status in ('CLAIMED','APPLIED'))
) engine=innodb comment='Single idempotency namespace for all governed extension actions';

alter table todo_notification add column delivery_key varchar(128) null after source_id;
update todo_notification set delivery_key=sha2(concat(length(cast(todo_id as char)),':',todo_id,'|',
  length(cast(user_id as char)),':',user_id,'|',length(notification_type),':',notification_type,'|',
  length(source_id),':',source_id,'|'),256) where delivery_key is null;
alter table todo_notification modify column delivery_key varchar(128) not null,modify column source_id varchar(128) not null;
-- Preserve the previous composite uniqueness contract: uk_todo_notification_source(todo_id,user_id,notification_type,source_id).
alter table todo_notification add unique key uk_todo_notification_delivery (delivery_key);

set @todo_menu=(select menu_id from sys_menu where perms='todo:list' order by menu_id limit 1);
insert into sys_menu(menu_name,parent_id,order_num,path,component,`query`,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select x.name,@todo_menu,x.ord,'#','',null,null,1,0,'F','0','0',x.perm,'#','admin',sysdate() from (
  select 'Request governed extension' name,30 ord,'todo:extension:request' perm
  union all select 'Approve governed extension',31,'todo:extension:approve'
) x where not exists(select 1 from sys_menu m where m.perms=x.perm);
