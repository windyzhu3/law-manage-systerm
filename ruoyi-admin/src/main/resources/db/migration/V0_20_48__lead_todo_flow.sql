-- Lead business facts for the TD-001..TD-004 flow. Todo runtime tables remain Todo-owned.
alter table biz_lead
  add column tag_confirm_status varchar(16) not null default 'PENDING' comment 'PENDING/CONFIRMED/CORRECTED' after source_code,
  add column tag_confirm_time datetime null comment '标签确认时间' after tag_confirm_status,
  add column tag_confirm_by bigint null comment '标签确认人' after tag_confirm_time,
  add column first_contact_status varchar(20) null comment '首联处理状态' after tag_confirm_by,
  add column first_contact_time datetime null comment '首联完成时间' after first_contact_status,
  add column first_contact_result varchar(32) null comment '首联结果' after first_contact_time,
  add column city varchar(100) null comment '所在城市' after first_contact_result,
  add column visited char(1) null comment '是否到所：0否 1是' after city,
  add column invalid_reason_code varchar(40) null comment '受控无效原因' after invalid_reason,
  add column invalid_source_node varchar(64) null comment '产生无效判断的节点' after invalid_reason_code,
  add column invalid_review_status varchar(20) null comment '无效复核状态' after invalid_source_node,
  add column retry_stage varchar(20) null comment '当前重试阶段' after invalid_review_status,
  add column retry_attempt_count int not null default 0 comment '当前阶段尝试次数' after retry_stage,
  add column next_retry_time datetime null comment '下次重试时间' after retry_attempt_count,
  add column disposition varchar(20) null comment '资源处置位置' after pool_status,
  add column dead_pool_time datetime null comment '进入Dead-Pool时间' after disposition,
  add column dead_pool_reason varchar(500) null comment 'Dead-Pool原因' after dead_pool_time,
  add column row_version int not null default 0 comment '乐观锁版本' after update_time;

-- Existing rows retain their lifecycle and public-pool meaning. Conversion wins over pool location.
update biz_lead
set disposition=case
  when customer_id is not null or status='3' then 'CONVERTED'
  when pool_status='1' then 'PUBLIC_POOL'
  else 'ACTIVE'
end
where disposition is null;

alter table biz_lead
  modify column disposition varchar(20) not null default 'ACTIVE' comment 'ACTIVE/PUBLIC_POOL/DEAD_POOL/CONVERTED',
  add key idx_biz_lead_disposition (disposition,dead_pool_time,lead_id),
  add key idx_biz_lead_first_contact (owner_id,first_contact_status,disposition,del_flag),
  add key idx_biz_lead_invalid_review (invalid_review_status,dept_id,update_time),
  add key idx_biz_lead_retry_due (disposition,next_retry_time,retry_stage),
  add constraint chk_biz_lead_tag_confirm_status check (tag_confirm_status in ('PENDING','CONFIRMED','CORRECTED')),
  add constraint chk_biz_lead_first_contact_result check (first_contact_result is null or first_contact_result in ('VALID','SUSPECT_INVALID','UNREACHABLE')),
  add constraint chk_biz_lead_invalid_review_status check (invalid_review_status is null or invalid_review_status in ('PENDING','CONFIRMED','MISJUDGED')),
  add constraint chk_biz_lead_retry_stage check (retry_stage is null or retry_stage in ('T0','T1_AM','T1_NOON','T1_PM','T2_AM','T2_NOON','T2_PM','EXHAUSTED')),
  add constraint chk_biz_lead_disposition check (disposition in ('ACTIVE','PUBLIC_POOL','DEAD_POOL','CONVERTED')),
  add constraint chk_biz_lead_visited check (visited is null or visited in ('0','1')),
  add constraint chk_biz_lead_retry_attempt_count check (retry_attempt_count>=0),
  add constraint chk_biz_lead_row_version check (row_version>=0);

create table biz_lead_call_record (
  call_record_id bigint not null auto_increment,
  lead_id bigint not null,
  todo_id bigint null,
  call_channel varchar(32) not null,
  external_call_id varchar(128) null,
  started_at datetime not null,
  ended_at datetime null,
  duration_seconds int null,
  call_result varchar(32) null,
  recording_file_object_id bigint null,
  manual_notes varchar(1000) null,
  provider_summary_hash char(64) null,
  idempotency_key varchar(192) not null,
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  primary key (call_record_id),
  unique key uk_biz_lead_call_record_idempotency (idempotency_key),
  key idx_biz_lead_call_record_lead (lead_id,started_at,call_record_id),
  key idx_biz_lead_call_record_external (call_channel,external_call_id),
  key idx_biz_lead_call_record_todo (todo_id),
  constraint chk_biz_lead_call_channel check (call_channel in ('MANUAL','APP','OUTBOUND_SYSTEM')),
  constraint chk_biz_lead_call_duration check (duration_seconds is null or duration_seconds>=0),
  constraint chk_biz_lead_call_period check (ended_at is null or ended_at>=started_at)
) engine=innodb comment='线索通话事实及证据';

create table biz_lead_invalid_review (
  review_id bigint not null auto_increment,
  lead_id bigint not null,
  reason_code varchar(40) not null,
  sales_explanation varchar(1000) null,
  submitted_by bigint not null,
  submitted_at datetime not null,
  reviewer_id bigint null,
  review_result varchar(32) null,
  review_comment varchar(1000) null,
  reviewed_at datetime null,
  system_default char(1) not null default 'N',
  todo_id bigint null,
  status varchar(20) not null default 'PENDING',
  idempotency_key varchar(192) not null,
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  row_version int not null default 0,
  primary key (review_id),
  unique key uk_biz_lead_invalid_review_idempotency (idempotency_key),
  unique key uk_biz_lead_invalid_review_todo (todo_id),
  key idx_biz_lead_invalid_review_queue (reviewer_id,status,submitted_at),
  key idx_biz_lead_invalid_review_lead (lead_id,review_id),
  constraint chk_biz_lead_invalid_reason check (reason_code in ('NO_DEMAND','DENY_SUBMISSION','COMPETITOR_INTERFERENCE','OTHER')),
  constraint chk_biz_lead_invalid_review_result check (review_result is null or review_result in ('TRUE_INVALID','MISJUDGED_VALID')),
  constraint chk_biz_lead_invalid_review_default check (system_default in ('Y','N')),
  constraint chk_biz_lead_invalid_review_state check (status in ('PENDING','COMPLETED'))
) engine=innodb comment='疑似无效线索复核事实';

create table biz_lead_retry_record (
  retry_record_id bigint not null auto_increment,
  lead_id bigint not null,
  plan_id bigint not null,
  window_code varchar(32) not null,
  attempt_no int not null,
  contact_result varchar(32) not null,
  next_window_code varchar(32) null,
  todo_id bigint null,
  call_record_id bigint null,
  occurred_at datetime not null,
  idempotency_key varchar(192) not null,
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  primary key (retry_record_id),
  unique key uk_biz_lead_retry_attempt (plan_id,window_code,attempt_no),
  unique key uk_biz_lead_retry_idempotency (idempotency_key),
  key idx_biz_lead_retry_lead (lead_id,occurred_at,retry_record_id),
  key idx_biz_lead_retry_todo (todo_id),
  constraint chk_biz_lead_retry_result check (contact_result in ('CONNECTED','NEXT_WINDOW','EXHAUSTED')),
  constraint chk_biz_lead_retry_attempt check (attempt_no>0)
) engine=innodb comment='线索重试窗口处理事实';

create table biz_lead_quality_record (
  quality_record_id bigint not null auto_increment,
  lead_id bigint not null,
  sales_user_id bigint not null,
  reason_code varchar(40) not null,
  reviewer_id bigint not null,
  source_todo_id bigint null,
  source_review_id bigint null,
  quality_type varchar(32) not null,
  idempotency_key varchar(192) not null,
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  primary key (quality_record_id),
  unique key uk_biz_lead_quality_idempotency (idempotency_key),
  unique key uk_biz_lead_quality_review_type (source_review_id,quality_type),
  key idx_biz_lead_quality_sales (sales_user_id,create_time),
  key idx_biz_lead_quality_lead (lead_id,quality_record_id),
  constraint chk_biz_lead_quality_type check (quality_type in ('INVALID_MISJUDGMENT'))
) engine=innodb comment='线索质量与误判记录';

create table biz_lead_dead_pool_log (
  dead_pool_log_id bigint not null auto_increment,
  lead_id bigint not null,
  action_type varchar(16) not null,
  reason_code varchar(64) not null,
  reason_detail varchar(1000) null,
  from_disposition varchar(20) not null,
  to_disposition varchar(20) not null,
  operator_id bigint not null,
  source_todo_id bigint null,
  action_time datetime not null,
  idempotency_key varchar(192) not null,
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  primary key (dead_pool_log_id),
  unique key uk_biz_lead_dead_pool_idempotency (idempotency_key),
  key idx_biz_lead_dead_pool_lead (lead_id,action_time,dead_pool_log_id),
  key idx_biz_lead_dead_pool_action (action_type,action_time),
  constraint chk_biz_lead_dead_pool_action check (action_type in ('ENTER','RESTORE')),
  constraint chk_biz_lead_dead_pool_from check (from_disposition in ('ACTIVE','PUBLIC_POOL','DEAD_POOL','CONVERTED')),
  constraint chk_biz_lead_dead_pool_to check (to_disposition in ('ACTIVE','PUBLIC_POOL','DEAD_POOL','CONVERTED'))
) engine=innodb comment='Dead-Pool进入与恢复审计';

create table biz_business_tag (
  tag_id bigint not null auto_increment,
  tag_code varchar(64) not null,
  tag_name varchar(100) not null,
  applicable_business_type varchar(32) not null default 'LEAD',
  tag_level varchar(20) null,
  color varchar(20) null,
  status char(1) not null default '0',
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  primary key (tag_id),
  unique key uk_biz_business_tag_code (tag_code),
  key idx_biz_business_tag_scope (applicable_business_type,status,tag_name)
) engine=innodb comment='统一业务标签定义';

create table biz_business_tag_rel (
  tag_rel_id bigint not null auto_increment,
  business_type varchar(32) not null,
  business_id bigint not null,
  tag_id bigint not null,
  tag_source varchar(32) not null default 'SYSTEM',
  confirm_status varchar(16) not null default 'PENDING',
  confirmed_by bigint null,
  confirmed_at datetime null,
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  primary key (tag_rel_id),
  unique key uk_biz_business_tag_rel (business_type,business_id,tag_id),
  key idx_biz_business_tag_rel_tag (tag_id,business_type,business_id),
  constraint chk_biz_business_tag_rel_source check (tag_source in ('SYSTEM','MANUAL','CORRECTED')),
  constraint chk_biz_business_tag_rel_confirm check (confirm_status in ('PENDING','CONFIRMED','CORRECTED'))
) engine=innodb comment='业务对象标签关系';

create table biz_lead_assignment_policy (
  policy_id bigint not null auto_increment,
  policy_code varchar(64) not null,
  policy_name varchar(128) not null,
  sales_dept_id bigint not null,
  source_code varchar(40) not null default '*',
  business_type varchar(32) not null default 'LEAD',
  retry_rule_json json not null,
  status varchar(16) not null default 'ACTIVE',
  row_version int not null default 0,
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  primary key (policy_id),
  unique key uk_biz_lead_assignment_policy_code (policy_code),
  unique key uk_biz_lead_assignment_policy_scope (sales_dept_id,source_code,business_type),
  key idx_biz_lead_assignment_policy_status (status,sales_dept_id),
  constraint chk_biz_lead_assignment_policy_status check (status in ('ACTIVE','INACTIVE'))
) engine=innodb comment='线索轮转分配策略';

create table biz_lead_assignment_policy_candidate (
  candidate_id bigint not null auto_increment,
  policy_id bigint not null,
  user_id bigint not null,
  sort_order int not null,
  status varchar(16) not null default 'ACTIVE',
  create_by varchar(64) null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  primary key (candidate_id),
  unique key uk_biz_lead_assignment_candidate_user (policy_id,user_id),
  unique key uk_biz_lead_assignment_candidate_order (policy_id,sort_order),
  key idx_biz_lead_assignment_candidate_active (policy_id,status,sort_order),
  constraint chk_biz_lead_assignment_candidate_status check (status in ('ACTIVE','INACTIVE')),
  constraint chk_biz_lead_assignment_candidate_order check (sort_order>=0)
) engine=innodb comment='线索轮转策略候选人顺序';

-- Controlled dictionaries use stable uppercase business values and UTF-8 Chinese labels.
insert into sys_dict_type(dict_name,dict_type,status,create_by,create_time,remark)
select x.dict_name,x.dict_type,'0','migration',sysdate(),x.remark
from (
  select '首联结果' dict_name,'law_first_contact_result' dict_type,'线索首联受控结果' remark union all
  select '线索标签确认状态','law_lead_tag_confirm_status','线索标签确认状态' union all
  select '线索无效原因','law_lead_invalid_reason','疑似无效原因' union all
  select '线索无效复核结果','law_lead_invalid_review_result','无效复核受控结果' union all
  select '线索重试阶段','law_retry_stage','T0/T+1/T+2重试窗口' union all
  select '线索重试结果','law_retry_result','重试窗口处理结果' union all
  select '通话渠道','law_call_channel','线索通话渠道' union all
  select '线索处置位置','law_lead_disposition','线索资源处置位置'
) x
where not exists(select 1 from sys_dict_type existing where existing.dict_type=x.dict_type);

insert into sys_dict_data(
  dict_sort,dict_label,dict_value,dict_type,css_class,list_class,is_default,status,create_by,create_time,remark)
select x.dict_sort,x.dict_label,x.dict_value,x.dict_type,'',x.list_class,x.is_default,'0','migration',sysdate(),x.remark
from (
  select 1 dict_sort,'有效' dict_label,'VALID' dict_value,'law_first_contact_result' dict_type,'success' list_class,'N' is_default,'首联有效' remark union all
  select 2,'疑似无效','SUSPECT_INVALID','law_first_contact_result','warning','N','转无效复核' union all
  select 3,'未接通','UNREACHABLE','law_first_contact_result','info','N','转重试计划' union all
  select 1,'待确认','PENDING','law_lead_tag_confirm_status','warning','Y','标签待确认' union all
  select 2,'已确认','CONFIRMED','law_lead_tag_confirm_status','success','N','标签已确认' union all
  select 3,'已纠正','CORRECTED','law_lead_tag_confirm_status','primary','N','标签已人工纠正' union all
  select 1,'无需求','NO_DEMAND','law_lead_invalid_reason','info','N','客户明确无需求' union all
  select 2,'拒绝提交','DENY_SUBMISSION','law_lead_invalid_reason','info','N','客户拒绝提交资料' union all
  select 3,'竞品干扰','COMPETITOR_INTERFERENCE','law_lead_invalid_reason','warning','N','竞品干扰' union all
  select 4,'其他','OTHER','law_lead_invalid_reason','info','N','其他受控原因' union all
  select 1,'确认无效','TRUE_INVALID','law_lead_invalid_review_result','danger','N','复核确认无效' union all
  select 2,'误判有效','MISJUDGED_VALID','law_lead_invalid_review_result','success','N','复核判定误判' union all
  select 1,'首日','T0','law_retry_stage','primary','N','首联当日窗口' union all
  select 2,'次日上午','T1_AM','law_retry_stage','primary','N','T+1上午窗口' union all
  select 3,'次日中午','T1_NOON','law_retry_stage','primary','N','T+1中午窗口' union all
  select 4,'次日下午','T1_PM','law_retry_stage','primary','N','T+1下午窗口' union all
  select 5,'第三日上午','T2_AM','law_retry_stage','warning','N','T+2上午窗口' union all
  select 6,'第三日中午','T2_NOON','law_retry_stage','warning','N','T+2中午窗口' union all
  select 7,'第三日下午','T2_PM','law_retry_stage','warning','N','T+2下午窗口' union all
  select 8,'已耗尽','EXHAUSTED','law_retry_stage','info','N','所有重试窗口已耗尽' union all
  select 1,'已接通','CONNECTED','law_retry_result','success','N','重试联系成功' union all
  select 2,'下一窗口','NEXT_WINDOW','law_retry_result','warning','N','进入下一重试窗口' union all
  select 3,'已耗尽','EXHAUSTED','law_retry_result','info','N','重试已耗尽' union all
  select 1,'人工补录','MANUAL','law_call_channel','info','N','人工补录通话' union all
  select 2,'移动应用','APP','law_call_channel','primary','N','APP通话' union all
  select 3,'外呼系统','OUTBOUND_SYSTEM','law_call_channel','success','N','外呼系统通话' union all
  select 1,'在办','ACTIVE','law_lead_disposition','primary','Y','正常在办线索' union all
  select 2,'公海','PUBLIC_POOL','law_lead_disposition','warning','N','普通公海线索' union all
  select 3,'Dead-Pool','DEAD_POOL','law_lead_disposition','danger','N','隔离Dead-Pool线索' union all
  select 4,'已转化','CONVERTED','law_lead_disposition','success','N','已转化线索'
) x
where not exists(
  select 1 from sys_dict_data existing
  where existing.dict_type=x.dict_type and existing.dict_value=x.dict_value
);

-- Permissions are attached to the closest existing lead pages without changing legacy grants.
set @lead_directory_id=(select menu_id from sys_menu where parent_id=0 and path='lead' order by menu_id limit 1);
set @lead_all_id=(select menu_id from sys_menu where parent_id=@lead_directory_id and path='all' order by menu_id limit 1);
set @lead_mine_id=(select menu_id from sys_menu where parent_id=@lead_directory_id and path='mine' order by menu_id limit 1);
set @lead_pool_id=(select menu_id from sys_menu where parent_id=@lead_directory_id and path='pool' order by menu_id limit 1);
set @lead_followup_id=(select menu_id from sys_menu where parent_id=@lead_directory_id and path='followup' order by menu_id limit 1);
set @lead_settings_id=(select menu_id from sys_menu where parent_id=@lead_directory_id and path='settings' order by menu_id limit 1);

insert into sys_menu(
  menu_name,parent_id,order_num,path,component,`query`,route_name,is_frame,is_cache,menu_type,
  visible,status,perms,icon,create_by,create_time)
select x.menu_name,x.parent_id,x.order_num,'#','',null,null,1,0,'F','0','0',x.perms,'#','migration',sysdate()
from (
  select '确认线索标签' menu_name,@lead_mine_id parent_id,201 order_num,'lead:tag:confirm' perms union all
  select '处理线索首联',@lead_mine_id,202,'lead:first-contact:handle' union all
  select '查看无效复核',@lead_all_id,203,'lead:invalid-review:list' union all
  select '处理无效复核',@lead_all_id,204,'lead:invalid-review:handle' union all
  select '查看线索重试',@lead_followup_id,205,'lead:retry:list' union all
  select '处理线索重试',@lead_followup_id,206,'lead:retry:handle' union all
  select '查看Dead-Pool',@lead_pool_id,207,'lead:dead-pool:list' union all
  select '恢复Dead-Pool线索',@lead_pool_id,208,'lead:dead-pool:restore' union all
  select '查看轮转策略',@lead_settings_id,209,'lead:assignment-policy:list' union all
  select '编辑轮转策略',@lead_settings_id,210,'lead:assignment-policy:edit' union all
  select '新增通话记录',@lead_followup_id,211,'lead:call-record:add' union all
  select '查看通话记录',@lead_followup_id,212,'lead:call-record:view'
) x
where x.parent_id is not null
  and not exists(select 1 from sys_menu existing where existing.perms=x.perms);

-- Create the missing event rows before applying their authoritative v1 schemas.
insert into todo_event_catalog(
  event_type,event_name,description,payload_version,business_object_type,payload_schema_json,
  owner_field_paths_json,producer,source_module,sample_payload_json,schema_status,status,create_by)
select x.event_type,x.event_name,x.description,1,'LEAD',
  json_object('type','object','properties',json_object(),'required',json_array()),
  null,'LEAD_TODO_FLOW','lead',json_object(),'READY','ACTIVE','migration'
from (
  select 'LEAD_TAG_CONFIRMED' event_type,'线索标签已确认' event_name,'线索来源标签完成确认或纠正' description union all
  select 'LEAD_FIRST_CONTACT_VALID','首联有效','线索首联结果为有效' union all
  select 'LEAD_SUSPECT_INVALID_MARKED','疑似无效线索已标记','线索进入疑似无效复核' union all
  select 'LEAD_FIRST_CONTACT_UNREACHABLE','首联未接通','线索首联未接通并进入重试' union all
  select 'LEAD_INVALID_REVIEW_CONFIRMED','无效复核已确认','复核确认线索无效' union all
  select 'LEAD_INVALID_REVIEW_MISJUDGED','无效复核判定误判','复核判定线索仍然有效' union all
  select 'LEAD_RETRY_WINDOW_DUE','线索重试窗口到期','线索重试窗口可处理' union all
  select 'LEAD_RETRY_CONNECTED','线索重试已接通','线索在重试窗口联系成功' union all
  select 'LEAD_RETRY_EXHAUSTED','线索重试已耗尽','线索重试计划耗尽' union all
  select 'LEAD_MOVED_TO_DEAD_POOL','线索已进入Dead-Pool','无效线索进入隔离Dead-Pool'
) x
where not exists(
  select 1 from todo_event_catalog existing
  where existing.event_type=x.event_type and existing.payload_version=1
);

-- Forward correction for the canonical producer implemented before this migration.
update todo_event_catalog
set event_name='线索已分配',description='线索分配给销售后触发',business_object_type='LEAD',
    producer='LEAD_TODO_FLOW',source_module='lead',
    payload_schema_json=json_object(
      'type','object','additionalProperties',false,
      'properties',json_object(
        'schemaVersion',json_object('type','integer','const',1,'title','载荷版本'),
        'assignmentId',json_object('type','integer','title','分配记录ID'),
        'ownerId',json_object('type','integer','title','线索负责人'),
        'ownerDeptId',json_object('type','integer','title','负责人部门'),
        'operatorId',json_object('type','integer','title','操作人')
      ),
      'required',json_array('schemaVersion','assignmentId','ownerId','ownerDeptId','operatorId')
    ),
    owner_field_paths_json=json_array('ownerId'),
    sample_payload_json=json_object('schemaVersion',1,'assignmentId',1001,'ownerId',11,'ownerDeptId',103,'operatorId',1),
    schema_status='READY',status='ACTIVE',version=version+1,update_by='migration',update_time=sysdate()
where event_type='LEAD_ASSIGNED' and payload_version=1;

update todo_event_catalog
set event_name='线索标签已确认',description='线索来源标签完成确认或纠正',business_object_type='LEAD',
    producer='LEAD_TODO_FLOW',source_module='lead',
    payload_schema_json=json_object('type','object','additionalProperties',true,'properties',json_object(
      'schemaVersion',json_object('type','integer','const',1),'tagRelationId',json_object('type','integer'),
      'confirmStatus',json_object('type','string'),'operatorId',json_object('type','integer'),
      'leadId',json_object('type','integer')),'required',json_array('schemaVersion','tagRelationId','confirmStatus','operatorId')),
    sample_payload_json=json_object('schemaVersion',1,'tagRelationId',1001,'confirmStatus','CONFIRMED','operatorId',11),
    schema_status='READY',status='ACTIVE',version=version+1,update_by='migration',update_time=sysdate()
where event_type='LEAD_TAG_CONFIRMED' and payload_version=1;

update todo_event_catalog
set event_name='首联有效',description='线索首联结果为有效',business_object_type='LEAD',
    producer='LEAD_TODO_FLOW',source_module='lead',
    payload_schema_json=json_object('type','object','additionalProperties',true,'properties',json_object(
      'schemaVersion',json_object('type','integer','const',1),'followupId',json_object('type','integer'),
      'ownerId',json_object('type','integer'),'contactResult',json_object('type','string','const','VALID'),
      'operatorId',json_object('type','integer'),'leadId',json_object('type','integer')),
      'required',json_array('schemaVersion','followupId','ownerId','contactResult','operatorId')),
    owner_field_paths_json=json_array('ownerId'),
    sample_payload_json=json_object('schemaVersion',1,'followupId',1001,'ownerId',11,'contactResult','VALID','operatorId',11),
    schema_status='READY',status='ACTIVE',version=version+1,update_by='migration',update_time=sysdate()
where event_type='LEAD_FIRST_CONTACT_VALID' and payload_version=1;

update todo_event_catalog
set event_name='疑似无效线索已标记',description='线索进入疑似无效复核',business_object_type='LEAD',
    producer='LEAD_TODO_FLOW',source_module='lead',
    payload_schema_json=json_object('type','object','additionalProperties',true,'properties',json_object(
      'schemaVersion',json_object('type','integer','const',1),'reviewId',json_object('type','integer'),
      'ownerId',json_object('type','integer'),'reviewerId',json_object('type','integer'),
      'reasonCode',json_object('type','string'),'operatorId',json_object('type','integer'),
      'leadId',json_object('type','integer')),
      'required',json_array('schemaVersion','reviewId','ownerId','reviewerId','reasonCode','operatorId')),
    owner_field_paths_json=json_array('reviewerId'),
    sample_payload_json=json_object('schemaVersion',1,'reviewId',1001,'ownerId',11,'reviewerId',12,'reasonCode','NO_DEMAND','operatorId',11),
    schema_status='READY',status='ACTIVE',version=version+1,update_by='migration',update_time=sysdate()
where event_type='LEAD_SUSPECT_INVALID_MARKED' and payload_version=1;

update todo_event_catalog
set event_name='首联未接通',description='线索首联未接通并进入重试',business_object_type='LEAD',
    producer='LEAD_TODO_FLOW',source_module='lead',
    payload_schema_json=json_object('type','object','additionalProperties',true,'properties',json_object(
      'schemaVersion',json_object('type','integer','const',1),'planId',json_object('type','integer'),
      'ownerId',json_object('type','integer'),'attempts',json_object('type','integer'),
      'nextContactAt',json_object('type','string','format','date-time'),
      'operatorId',json_object('type','integer'),'leadId',json_object('type','integer')),
      'required',json_array('schemaVersion','planId','ownerId','attempts','nextContactAt','operatorId')),
    owner_field_paths_json=json_array('ownerId'),
    sample_payload_json=json_object('schemaVersion',1,'planId',1001,'ownerId',11,'attempts',1,'nextContactAt','2026-07-26T09:00:00','operatorId',11),
    schema_status='READY',status='ACTIVE',version=version+1,update_by='migration',update_time=sysdate()
where event_type='LEAD_FIRST_CONTACT_UNREACHABLE' and payload_version=1;

update todo_event_catalog
set event_name='无效复核已确认',description='复核确认线索无效',business_object_type='LEAD',
    producer='LEAD_TODO_FLOW',source_module='lead',
    payload_schema_json=json_object('type','object','additionalProperties',true,'properties',json_object(
      'schemaVersion',json_object('type','integer','const',1),'reviewId',json_object('type','integer'),
      'reviewerId',json_object('type','integer'),'reviewResult',json_object('type','string','const','TRUE_INVALID'),
      'operatorId',json_object('type','integer'),'leadId',json_object('type','integer')),
      'required',json_array('schemaVersion','reviewId','reviewerId','reviewResult','operatorId')),
    sample_payload_json=json_object('schemaVersion',1,'reviewId',1001,'reviewerId',12,'reviewResult','TRUE_INVALID','operatorId',12),
    schema_status='READY',status='ACTIVE',version=version+1,update_by='migration',update_time=sysdate()
where event_type='LEAD_INVALID_REVIEW_CONFIRMED' and payload_version=1;

update todo_event_catalog
set event_name='无效复核判定误判',description='复核判定线索仍然有效',business_object_type='LEAD',
    producer='LEAD_TODO_FLOW',source_module='lead',
    payload_schema_json=json_object('type','object','additionalProperties',true,'properties',json_object(
      'schemaVersion',json_object('type','integer','const',1),'reviewId',json_object('type','integer'),
      'reviewerId',json_object('type','integer'),'reviewResult',json_object('type','string','const','MISJUDGED_VALID'),
      'ownerId',json_object('type','integer'),'operatorId',json_object('type','integer'),
      'leadId',json_object('type','integer')),
      'required',json_array('schemaVersion','reviewId','reviewerId','reviewResult','ownerId','operatorId')),
    owner_field_paths_json=json_array('ownerId'),
    sample_payload_json=json_object('schemaVersion',1,'reviewId',1001,'reviewerId',12,'reviewResult','MISJUDGED_VALID','ownerId',11,'operatorId',12),
    schema_status='READY',status='ACTIVE',version=version+1,update_by='migration',update_time=sysdate()
where event_type='LEAD_INVALID_REVIEW_MISJUDGED' and payload_version=1;

update todo_event_catalog
set event_name='线索重试窗口到期',description='线索重试窗口可处理',business_object_type='LEAD',
    producer='LEAD_TODO_FLOW',source_module='lead',
    payload_schema_json=json_object('type','object','additionalProperties',true,'properties',json_object(
      'schemaVersion',json_object('type','integer','const',1),'planId',json_object('type','integer'),
      'windowCode',json_object('type','string'),'occurrenceNo',json_object('type','integer'),
      'ownerId',json_object('type','integer'),'leadId',json_object('type','integer')),
      'required',json_array('schemaVersion','planId','windowCode','occurrenceNo','ownerId')),
    owner_field_paths_json=json_array('ownerId'),
    sample_payload_json=json_object('schemaVersion',1,'planId',1001,'windowCode','T1_AM','occurrenceNo',1,'ownerId',11),
    schema_status='READY',status='ACTIVE',version=version+1,update_by='migration',update_time=sysdate()
where event_type='LEAD_RETRY_WINDOW_DUE' and payload_version=1;

update todo_event_catalog
set event_name='线索重试已接通',description='线索在重试窗口联系成功',business_object_type='LEAD',
    producer='LEAD_TODO_FLOW',source_module='lead',
    payload_schema_json=json_object('type','object','additionalProperties',true,'properties',json_object(
      'schemaVersion',json_object('type','integer','const',1),'planId',json_object('type','integer'),
      'retryRecordId',json_object('type','integer'),'ownerId',json_object('type','integer'),
      'operatorId',json_object('type','integer'),'leadId',json_object('type','integer')),
      'required',json_array('schemaVersion','planId','retryRecordId','ownerId','operatorId')),
    owner_field_paths_json=json_array('ownerId'),
    sample_payload_json=json_object('schemaVersion',1,'planId',1001,'retryRecordId',2001,'ownerId',11,'operatorId',11),
    schema_status='READY',status='ACTIVE',version=version+1,update_by='migration',update_time=sysdate()
where event_type='LEAD_RETRY_CONNECTED' and payload_version=1;

update todo_event_catalog
set event_name='线索重试已耗尽',description='线索重试计划耗尽',business_object_type='LEAD',
    producer='LEAD_TODO_FLOW',source_module='lead',
    payload_schema_json=json_object('type','object','additionalProperties',true,'properties',json_object(
      'schemaVersion',json_object('type','integer','const',1),'planId',json_object('type','integer'),
      'operatorId',json_object('type','integer'),'leadId',json_object('type','integer')),
      'required',json_array('schemaVersion','planId','operatorId')),
    sample_payload_json=json_object('schemaVersion',1,'planId',1001,'operatorId',0),
    schema_status='READY',status='ACTIVE',version=version+1,update_by='migration',update_time=sysdate()
where event_type='LEAD_RETRY_EXHAUSTED' and payload_version=1;

update todo_event_catalog
set event_name='线索已进入Dead-Pool',description='无效线索进入隔离Dead-Pool',business_object_type='LEAD',
    producer='LEAD_TODO_FLOW',source_module='lead',
    payload_schema_json=json_object('type','object','additionalProperties',true,'properties',json_object(
      'schemaVersion',json_object('type','integer','const',1),'deadPoolLogId',json_object('type','integer'),
      'reasonCode',json_object('type','string'),'operatorId',json_object('type','integer'),
      'leadId',json_object('type','integer')),
      'required',json_array('schemaVersion','deadPoolLogId','reasonCode','operatorId')),
    sample_payload_json=json_object('schemaVersion',1,'deadPoolLogId',1001,'reasonCode','TRUE_INVALID','operatorId',12),
    schema_status='READY',status='ACTIVE',version=version+1,update_by='migration',update_time=sysdate()
where event_type='LEAD_MOVED_TO_DEAD_POOL' and payload_version=1;
