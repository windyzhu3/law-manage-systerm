create table todo_sla_rule (
  sla_rule_id bigint not null auto_increment,
  rule_code varchar(64) not null,
  rule_name varchar(128) not null,
  sla_type varchar(32) not null,
  duration_value int not null,
  duration_unit varchar(16) not null,
  calendar_code varchar(64) not null,
  start_strategy varchar(32) not null,
  soft_remind_percent int not null default 80,
  hard_remind_percent int not null default 100,
  escalate_percent int not null default 150,
  pause_policy_json json null,
  escalation_policy_json json null,
  auto_action_json json null,
  status char(1) not null default '0',
  version int not null default 0,
  create_by varchar(64) not null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  primary key (sla_rule_id),
  unique key uk_todo_sla_rule_code (rule_code),
  key idx_todo_sla_rule_list (status,sla_type,update_time,sla_rule_id),
  check (duration_value > 0),
  check (soft_remind_percent > 0 and soft_remind_percent <= hard_remind_percent
         and hard_remind_percent <= escalate_percent)
) engine=innodb comment='Todo reusable SLA rule';

create table todo_dod_rule (
  dod_rule_id bigint not null auto_increment,
  rule_code varchar(64) not null,
  rule_name varchar(128) not null,
  rule_type varchar(32) not null,
  required_fields_json json not null,
  required_attachments_json json not null,
  conditional_rules_json json not null,
  validator_refs_json json not null,
  error_messages_json json not null,
  status char(1) not null default '0',
  version int not null default 0,
  create_by varchar(64) not null,
  create_time datetime not null default current_timestamp,
  update_by varchar(64) null,
  update_time datetime null,
  primary key (dod_rule_id),
  unique key uk_todo_dod_rule_code (rule_code),
  key idx_todo_dod_rule_list (status,rule_type,update_time,dod_rule_id)
) engine=innodb comment='Todo reusable definition-of-done rule';

create table todo_template_draft_rule_ref (
  ref_id bigint not null auto_increment,
  version_id bigint not null,
  ref_type varchar(32) not null,
  ref_id_value bigint not null,
  sort_order int not null default 0,
  config_json json null,
  create_time datetime not null default current_timestamp,
  primary key (ref_id),
  unique key uk_todo_template_draft_rule_ref (version_id,ref_type,ref_id_value,sort_order),
  key idx_todo_template_draft_rule_version (version_id,ref_type,sort_order)
) engine=innodb comment='Todo template draft reusable-rule references';

create table todo_simulation_record (
  simulation_id bigint not null auto_increment,
  request_id varchar(64) not null,
  template_version_id bigint not null,
  event_type varchar(64) not null,
  business_type varchar(32) not null,
  business_id bigint not null,
  input_summary_json json not null,
  result_json json not null,
  duration_ms bigint not null,
  operator_id bigint not null,
  create_time datetime not null default current_timestamp,
  primary key (simulation_id),
  unique key uk_todo_simulation_request (request_id),
  key idx_todo_simulation_operator (operator_id,create_time),
  key idx_todo_simulation_template_version (template_version_id,create_time)
) engine=innodb comment='Todo configuration simulation record';

alter table todo_template_version
  add column change_summary varchar(1024) null after ui_schema_json,
  add column impact_scope varchar(1024) null after change_summary,
  add column rollback_source_version_id bigint null after impact_scope,
  add key idx_todo_template_version_rollback_source(rollback_source_version_id);

insert into sys_menu(menu_name,parent_id,order_num,path,component,`query`,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select 'Todo Engine',0,41,'todo-engine','',null,'TodoEngine',1,0,'M','0','0','','tree-table','admin',sysdate()
where not exists(select 1 from sys_menu where menu_name='Todo Engine' and path='todo-engine' and menu_type='M');

set @todo_engine_directory_id=(select menu_id from sys_menu where menu_name='Todo Engine' and path='todo-engine' and menu_type='M' order by menu_id limit 1);

insert into sys_menu(menu_name,parent_id,order_num,path,component,`query`,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select x.menu_name,@todo_engine_directory_id,x.order_num,x.path,x.component,null,x.route_name,1,0,'C','0','0',x.perms,x.icon,'admin',sysdate()
from (
  select 'Template configuration' menu_name,1 order_num,'todo-template' path,'todo/config/template/index' component,'TodoConfigTemplate' route_name,'todo:template:list' perms,'list' icon union all
  select 'Trigger configuration',2,'todo-trigger-rule','todo/config/trigger/index','TodoConfigTrigger','todo:trigger:list','guide' union all
  select 'SLA configuration',3,'todo-sla-rule','todo/config/sla/index','TodoConfigSla','todo:sla-rule:list','time' union all
  select 'DoD configuration',4,'todo-dod-rule','todo/config/dod/index','TodoConfigDod','todo:dod-rule:list','edit' union all
  select 'Simulation records',5,'todo-simulation','todo/config/simulation/index','TodoConfigSimulation','todo:simulation:list','bug' union all
  select 'Release records',6,'todo-release-record','todo/config/release/index','TodoConfigRelease','todo:release:list','history'
) x
where @todo_engine_directory_id is not null
  and not exists(select 1 from sys_menu existing where existing.component=x.component);

insert into sys_menu(menu_name,parent_id,order_num,path,component,`query`,route_name,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
select x.menu_name,(select menu_id from sys_menu page where page.component=x.parent_component order by menu_id limit 1),x.order_num,'#','',null,null,1,0,'F','0','0',x.perms,'#','admin',sysdate()
from (
  select 'Create template' menu_name,'todo/config/template/index' parent_component,10 order_num,'todo:template:create' perms union all
  select 'Edit template','todo/config/template/index',11,'todo:template:edit' union all
  select 'Copy template','todo/config/template/index',12,'todo:template:copy' union all
  select 'Create trigger','todo/config/trigger/index',10,'todo:trigger:create' union all
  select 'Edit trigger','todo/config/trigger/index',11,'todo:trigger:edit' union all
  select 'Toggle trigger','todo/config/trigger/index',12,'todo:trigger:toggle' union all
  select 'Create SLA rule','todo/config/sla/index',10,'todo:sla-rule:create' union all
  select 'Edit SLA rule','todo/config/sla/index',11,'todo:sla-rule:edit' union all
  select 'Copy SLA rule','todo/config/sla/index',12,'todo:sla-rule:copy' union all
  select 'Toggle SLA rule','todo/config/sla/index',13,'todo:sla-rule:toggle' union all
  select 'Create DoD rule','todo/config/dod/index',10,'todo:dod-rule:create' union all
  select 'Edit DoD rule','todo/config/dod/index',11,'todo:dod-rule:edit' union all
  select 'Copy DoD rule','todo/config/dod/index',12,'todo:dod-rule:copy' union all
  select 'Toggle DoD rule','todo/config/dod/index',13,'todo:dod-rule:toggle' union all
  select 'Run simulation','todo/config/simulation/index',10,'todo:simulation:simulate' union all
  select 'Publish release','todo/config/release/index',10,'todo:release:publish' union all
  select 'Compare release','todo/config/release/index',11,'todo:release:diff' union all
  select 'Rollback release','todo/config/release/index',12,'todo:release:rollback'
) x
where exists(select 1 from sys_menu page where page.component=x.parent_component)
  and not exists(select 1 from sys_menu existing where existing.perms=x.perms);

update sys_menu set visible='1',status='1' where component='todo/config/index';

insert into sys_dict_type(dict_name,dict_type,status,create_by,create_time,remark)
select x.dict_name,x.dict_type,'0','admin',sysdate(),'Todo configuration center'
from (
  select 'Business stage' dict_name,'law_todo_business_stage' dict_type union all
  select 'Business type','law_todo_business_type' union all
  select 'Template type','law_todo_template_type' union all
  select 'Publish status','law_todo_publish_status' union all
  select 'Trigger mode','law_todo_trigger_mode' union all
  select 'Condition operator','law_todo_condition_operator' union all
  select 'Owner rule type','law_todo_owner_rule_type' union all
  select 'SLA type','law_todo_sla_type' union all
  select 'SLA unit','law_todo_sla_unit' union all
  select 'SLA start strategy','law_todo_sla_start_strategy' union all
  select 'Timeout strategy','law_todo_timeout_strategy' union all
  select 'DoD rule type','law_todo_dod_rule_type' union all
  select 'Rule status','law_todo_rule_status' union all
  select 'Version status','law_todo_version_status'
) x
where not exists(select 1 from sys_dict_type existing where existing.dict_type=x.dict_type);

insert into sys_dict_data(dict_sort,dict_label,dict_value,dict_type,css_class,list_class,is_default,status,create_by,create_time,remark)
select x.dict_sort,x.dict_label,x.dict_value,x.dict_type,'',x.list_class,'N','0','admin',sysdate(),x.dict_label
from (
  select 1 dict_sort,'Lead' dict_label,'LEAD' dict_value,'law_todo_business_stage' dict_type,'primary' list_class union all
  select 2,'Contract','CONTRACT','law_todo_business_stage','primary' union all
  select 3,'Case','CASE','law_todo_business_stage','warning' union all
  select 4,'Matter','MATTER','law_todo_business_stage','warning' union all
  select 5,'Archive','ARCHIVE','law_todo_business_stage','success' union all
  select 1,'Lead','LEAD','law_todo_business_type','primary' union all
  select 2,'Customer','CUSTOMER','law_todo_business_type','primary' union all
  select 3,'Contract','CONTRACT','law_todo_business_type','warning' union all
  select 4,'Case','CASE','law_todo_business_type','warning' union all
  select 5,'Matter','MATTER','law_todo_business_type','success' union all
  select 1,'Standard','STANDARD','law_todo_template_type','primary' union all
  select 2,'Custom','CUSTOM','law_todo_template_type','warning' union all
  select 1,'Draft','DRAFT','law_todo_publish_status','info' union all
  select 2,'Published','PUBLISHED','law_todo_publish_status','success' union all
  select 3,'Rolled back','ROLLED_BACK','law_todo_publish_status','danger' union all
  select 1,'Event','EVENT','law_todo_trigger_mode','primary' union all
  select 2,'Manual','MANUAL','law_todo_trigger_mode','warning' union all
  select 3,'Schedule','SCHEDULE','law_todo_trigger_mode','success' union all
  select 1,'Equals','EQ','law_todo_condition_operator','primary' union all
  select 2,'Not equals','NE','law_todo_condition_operator','warning' union all
  select 3,'In','IN','law_todo_condition_operator','primary' union all
  select 4,'Not in','NOT_IN','law_todo_condition_operator','warning' union all
  select 5,'Exists','EXISTS','law_todo_condition_operator','success' union all
  select 6,'Not exists','NOT_EXISTS','law_todo_condition_operator','danger' union all
  select 1,'User','USER','law_todo_owner_rule_type','primary' union all
  select 2,'Role','ROLE','law_todo_owner_rule_type','primary' union all
  select 3,'Department','DEPT','law_todo_owner_rule_type','warning' union all
  select 4,'Payload field','PAYLOAD_FIELD','law_todo_owner_rule_type','success' union all
  select 1,'Working time','WORKING_TIME','law_todo_sla_type','primary' union all
  select 2,'Calendar time','CALENDAR_TIME','law_todo_sla_type','warning' union all
  select 1,'Minute','MINUTE','law_todo_sla_unit','primary' union all
  select 2,'Hour','HOUR','law_todo_sla_unit','primary' union all
  select 3,'Day','DAY','law_todo_sla_unit','warning' union all
  select 1,'Event time','EVENT_TIME','law_todo_sla_start_strategy','primary' union all
  select 2,'Creation time','CREATE_TIME','law_todo_sla_start_strategy','primary' union all
  select 3,'Assignment time','ASSIGN_TIME','law_todo_sla_start_strategy','warning' union all
  select 1,'Remind','REMIND','law_todo_timeout_strategy','warning' union all
  select 2,'Escalate','ESCALATE','law_todo_timeout_strategy','danger' union all
  select 3,'Auto action','AUTO_ACTION','law_todo_timeout_strategy','danger' union all
  select 1,'Required fields','REQUIRED_FIELDS','law_todo_dod_rule_type','primary' union all
  select 2,'Required attachments','REQUIRED_ATTACHMENTS','law_todo_dod_rule_type','primary' union all
  select 3,'Conditional','CONDITIONAL','law_todo_dod_rule_type','warning' union all
  select 4,'Validator','VALIDATOR','law_todo_dod_rule_type','success' union all
  select 1,'Enabled','ENABLED','law_todo_rule_status','success' union all
  select 2,'Disabled','DISABLED','law_todo_rule_status','info' union all
  select 1,'Draft','DRAFT','law_todo_version_status','info' union all
  select 2,'Published','PUBLISHED','law_todo_version_status','success' union all
  select 3,'Rolled back','ROLLED_BACK','law_todo_version_status','danger'
) x
where not exists(select 1 from sys_dict_data existing where existing.dict_type=x.dict_type and existing.dict_value=x.dict_value);
