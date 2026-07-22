-- Phase-one reusable assets and immutable release snapshots for the 15 technical templates.
-- PRD TD-001..TD-025 remain governed BLOCKED drafts until their business dependencies are implemented.

insert into todo_sla_rule(rule_code,rule_name,sla_type,duration_value,duration_unit,calendar_code,start_strategy,
  soft_remind_percent,hard_remind_percent,escalate_percent,pause_policy_json,escalation_policy_json,
  auto_action_json,status,create_by)
select x.rule_code,x.rule_name,'RESPONSE',x.duration_value,x.duration_unit,'DEFAULT','TODO_CREATED',
  80,100,150,json_object(),json_object(),json_object(),'0','flyway-v0.20.40'
from (
  select 'SLA_RESPONSE_30M' rule_code,'30分钟响应' rule_name,30 duration_value,'MINUTE' duration_unit union all
  select 'SLA_RESPONSE_4H','4小时响应',4,'HOUR' union all
  select 'SLA_RESPONSE_8H','8小时响应',8,'HOUR' union all
  select 'SLA_RESPONSE_16H','16小时响应',16,'HOUR'
) x
where not exists(select 1 from todo_sla_rule existing where existing.rule_code=x.rule_code);

create temporary table tmp_todo_phase_one_dod (
  template_code varchar(64) not null,
  rule_code varchar(64) not null,
  rule_name varchar(128) not null,
  business_type varchar(32) not null,
  required_fields_json json not null,
  required_attachments_json json not null,
  primary key(template_code)
) engine=innodb;

insert into tmp_todo_phase_one_dod values
('LEAD_FIRST_CONTACT','LEAD_FIRST_CONTACT_DOD','完成首联并提交联系凭证','LEAD',json_array('contactResult'),json_array('CONTACT_PROOF')),
('CONTRACT_REVIEW','CONTRACT_REVIEW_DOD','完成合同审核','CONTRACT',json_array('reviewAction','opinion'),json_array()),
('CONTRACT_SIGN','CONTRACT_SIGN_DOD','完成合同签署并上传签署件','CONTRACT',json_array('signStatus','signMethod','signDate'),json_array('SIGNED_CONTRACT')),
('PAYMENT_CONFIRM','PAYMENT_CONFIRM_DOD','完成收款核验','CONTRACT',json_array('planId','receivedAmount','voucherUrl'),json_array()),
('INVOICE_HANDLE','INVOICE_HANDLE_DOD','完成开票处理','CONTRACT',json_array('planId','action'),json_array()),
('CASE_CREATE_CHECK','CASE_CREATE_CHECK_DOD','完成案件生成资料检查','CONTRACT',json_array('materialsChecked'),json_array()),
('CASE_ASSIGN','CASE_ASSIGN_DOD','完成案件分配','CASE',json_array('lawyerId'),json_array()),
('CASE_ACCEPT','CASE_ACCEPT_DOD','完成律师接案确认','CASE',json_array('confirmId','accepted'),json_array()),
('CASE_REASSIGN','CASE_REASSIGN_DOD','完成案件重新分配','CASE',json_array('lawyerId'),json_array()),
('CASE_TRANSFER_REVIEW','CASE_TRANSFER_REVIEW_DOD','完成转案审批','CASE',json_array('transferId','reviewAction','opinion'),json_array()),
('MATTER_NODE_HANDLE','MATTER_NODE_HANDLE_DOD','完成案件节点办理','MATTER',json_array('nodeId','actualDate'),json_array()),
('MATTER_EXPENSE_REVIEW','MATTER_EXPENSE_REVIEW_DOD','完成案件费用审核','MATTER',json_array('expenseId','result'),json_array()),
('MATTER_DOCUMENT_SUPPLY','MATTER_DOCUMENT_SUPPLY_DOD','完成案件材料补充','MATTER',json_array('documentType','fileName','fileUrl'),json_array()),
('CASE_CLOSE_CONFIRM','CASE_CLOSE_CONFIRM_DOD','完成结案确认','MATTER',json_array('action','opinion','feeClearStatus'),json_array()),
('CASE_ARCHIVE_CONFIRM','CASE_ARCHIVE_CONFIRM_DOD','完成归档确认','MATTER',json_array('action','opinion','archiveNo'),json_array());

insert into todo_dod_rule(rule_code,rule_name,rule_type,business_type,required_fields_json,
  required_attachments_json,conditional_rules_json,validator_refs_json,error_messages_json,status,create_by)
select x.rule_code,x.rule_name,'TASK',x.business_type,x.required_fields_json,x.required_attachments_json,
  json_array(),json_array(),json_object(),'0','flyway-v0.20.40'
from tmp_todo_phase_one_dod x
where not exists(select 1 from todo_dod_rule existing where existing.rule_code=x.rule_code);

create temporary table tmp_todo_phase_one_contract (
  template_code varchar(64) not null,
  event_type varchar(100) not null,
  business_type varchar(32) not null,
  owner_legacy varchar(128) not null,
  owner_config json not null,
  dod_rule_code varchar(64) not null,
  sla_rule_code varchar(64) not null,
  source_version_id bigint null,
  new_version_no int null,
  new_version_id bigint null,
  primary key(template_code)
) engine=innodb;

set @case_manager_role_id=(select role_id from sys_role where role_key='case_manager' and del_flag='0' limit 1);
set @finance_role_id=(select role_id from sys_role where role_key='finance_manager' and del_flag='0' limit 1);
set @partner_role_id=(select role_id from sys_role where role_key='law_partner_manager' and del_flag='0' limit 1);

insert into tmp_todo_phase_one_contract(template_code,event_type,business_type,owner_legacy,owner_config,dod_rule_code,sla_rule_code) values
('LEAD_FIRST_CONTACT','LEAD_ASSIGNED','LEAD','PAYLOAD:ownerId',json_object('operand','ownerId','type','PAYLOAD'),'LEAD_FIRST_CONTACT_DOD','SLA_RESPONSE_30M'),
('CONTRACT_REVIEW','CONTRACT_SUBMITTED','CONTRACT',concat('ROLE:',@partner_role_id),json_object('operand',@partner_role_id,'type','ROLE'),'CONTRACT_REVIEW_DOD','SLA_RESPONSE_4H'),
('CONTRACT_SIGN','CONTRACT_APPROVED','CONTRACT','PAYLOAD:ownerId',json_object('operand','ownerId','type','PAYLOAD'),'CONTRACT_SIGN_DOD','SLA_RESPONSE_16H'),
('PAYMENT_CONFIRM','CONTRACT_SIGNED','CONTRACT',concat('ROLE:',@finance_role_id),json_object('operand',@finance_role_id,'type','ROLE'),'PAYMENT_CONFIRM_DOD','SLA_RESPONSE_8H'),
('INVOICE_HANDLE','PAYMENT_CONFIRMED','CONTRACT',concat('ROLE:',@finance_role_id),json_object('operand',@finance_role_id,'type','ROLE'),'INVOICE_HANDLE_DOD','SLA_RESPONSE_16H'),
('CASE_CREATE_CHECK','INVOICE_HANDLED','CONTRACT',concat('ROLE:',@case_manager_role_id),json_object('operand',@case_manager_role_id,'type','ROLE'),'CASE_CREATE_CHECK_DOD','SLA_RESPONSE_16H'),
('CASE_ASSIGN','CASE_CREATED','CASE',concat('ROLE:',@case_manager_role_id),json_object('operand',@case_manager_role_id,'type','ROLE'),'CASE_ASSIGN_DOD','SLA_RESPONSE_8H'),
('CASE_ACCEPT','CASE_ASSIGNED','CASE','PAYLOAD:mainLawyerId',json_object('operand','mainLawyerId','type','PAYLOAD'),'CASE_ACCEPT_DOD','SLA_RESPONSE_4H'),
('CASE_REASSIGN','CASE_REJECTED','CASE',concat('ROLE:',@case_manager_role_id),json_object('operand',@case_manager_role_id,'type','ROLE'),'CASE_REASSIGN_DOD','SLA_RESPONSE_16H'),
('CASE_TRANSFER_REVIEW','CASE_TRANSFER_REQUESTED','CASE',concat('ROLE:',@partner_role_id),json_object('operand',@partner_role_id,'type','ROLE'),'CASE_TRANSFER_REVIEW_DOD','SLA_RESPONSE_16H'),
('MATTER_NODE_HANDLE','MATTER_NODE_READY','MATTER','PAYLOAD:ownerId',json_object('operand','ownerId','type','PAYLOAD'),'MATTER_NODE_HANDLE_DOD','SLA_RESPONSE_16H'),
('MATTER_EXPENSE_REVIEW','MATTER_EXPENSE_SUBMITTED','MATTER',concat('ROLE:',@finance_role_id),json_object('operand',@finance_role_id,'type','ROLE'),'MATTER_EXPENSE_REVIEW_DOD','SLA_RESPONSE_16H'),
('MATTER_DOCUMENT_SUPPLY','MATTER_DOCUMENT_REQUIRED','MATTER','PAYLOAD:ownerId',json_object('operand','ownerId','type','PAYLOAD'),'MATTER_DOCUMENT_SUPPLY_DOD','SLA_RESPONSE_16H'),
('CASE_CLOSE_CONFIRM','ARCHIVE_APPLIED','MATTER',concat('ROLE:',@case_manager_role_id),json_object('operand',@case_manager_role_id,'type','ROLE'),'CASE_CLOSE_CONFIRM_DOD','SLA_RESPONSE_16H'),
('CASE_ARCHIVE_CONFIRM','CASE_CLOSED','MATTER',concat('ROLE:',@case_manager_role_id),json_object('operand',@case_manager_role_id,'type','ROLE'),'CASE_ARCHIVE_CONFIRM_DOD','SLA_RESPONSE_16H');

-- The governed event sample is the source of truth for payload owner fields.
create temporary table tmp_todo_phase_one_event_guard (
  invalid_count int not null,
  constraint ck_phase_one_event_samples_ready check(invalid_count=0)
) engine=innodb;
insert into tmp_todo_phase_one_event_guard(invalid_count)
select count(*) from tmp_todo_phase_one_contract x
left join todo_event_catalog c on c.event_type=x.event_type and c.payload_version=1
where c.event_catalog_id is null or c.status<>'ACTIVE' or c.schema_status<>'READY'
  or c.sample_payload_json is null;
drop temporary table tmp_todo_phase_one_event_guard;

update tmp_todo_phase_one_contract x
join todo_template t on t.template_code=x.template_code
set x.source_version_id=(select v.version_id from todo_template_version v
    where v.template_id=t.template_id and v.status='PUBLISHED'
    order by v.version_no desc,v.version_id desc limit 1),
    x.new_version_no=(select coalesce(max(v.version_no),0)+1 from todo_template_version v where v.template_id=t.template_id);

create temporary table tmp_todo_phase_one_source_guard (
  template_count int not null,
  source_count int not null,
  constraint ck_phase_one_templates_present check(template_count=15),
  constraint ck_phase_one_sources_present check(source_count=15)
) engine=innodb;
insert into tmp_todo_phase_one_source_guard
select count(*),count(source_version_id) from tmp_todo_phase_one_contract;
drop temporary table tmp_todo_phase_one_source_guard;

set @lead_first_contact_definition='{"acceptanceRefs":[],"autoActions":[],"decisionRefs":[],"dod":{"config":{"materials":[{"minCount":1,"type":"CONTACT_PROOF"}],"requiredFields":["contactResult"]}},"event":{"condition":{},"eventType":"LEAD_ASSIGNED","payloadVersion":1},"owner":{"config":{"operand":"ownerId","type":"PAYLOAD"}},"routing":{"config":{}},"schemaVersion":1,"sla":{"config":{"calendarCode":"DEFAULT","minutes":30}},"templateCode":"LEAD_FIRST_CONTACT","ui":{"config":{"fields":[{"key":"contactResult","type":"text"},{"key":"contactProof","materialType":"CONTACT_PROOF","type":"file"}],"fileRelationType":"CONTACT_PROOF","formCode":"LEAD_FIRST_CONTACT"}}}';

insert into todo_template_version(template_id,version_no,status,source_version_id,owner_rule_json,dod_rule_json,
  sla_rule_json,next_rule_json,ui_schema_json,definition_schema_version,definition_json,compiled_json,
  definition_hash,validation_report_json,change_summary,impact_scope,published_by,published_time)
select t.template_id,x.new_version_no,'PUBLISHED',x.source_version_id,json_quote(x.owner_legacy),
  case when x.template_code='LEAD_FIRST_CONTACT' then json_extract(cast(@lead_first_contact_definition as json),'$.dod.config') else source.dod_rule_json end,
  case when x.template_code='LEAD_FIRST_CONTACT' then json_extract(cast(@lead_first_contact_definition as json),'$.sla.config') else source.sla_rule_json end,
  source.next_rule_json,source.ui_schema_json,source.definition_schema_version,
  case when x.template_code='LEAD_FIRST_CONTACT' then cast(@lead_first_contact_definition as json) else source.definition_json end,
  case when x.template_code='LEAD_FIRST_CONTACT' then cast(@lead_first_contact_definition as json) else source.compiled_json end,
  case when x.template_code='LEAD_FIRST_CONTACT' then lower(sha2(@lead_first_contact_definition,256)) else source.definition_hash end,
  json_object('errors',json_array(),'warnings',json_array()),
  '阶段一默认 SLA/DoD 资产绑定与负责人字段校准','Todo Engine 15个技术标准模板',
  'flyway-v0.20.40',sysdate()
from tmp_todo_phase_one_contract x
join todo_template t on t.template_code=x.template_code
join todo_template_version source on source.version_id=x.source_version_id;

update tmp_todo_phase_one_contract x
join todo_template t on t.template_code=x.template_code
join todo_template_version v on v.template_id=t.template_id and v.version_no=x.new_version_no
set x.new_version_id=v.version_id;

insert into todo_template_draft_rule_ref(version_id,ref_type,ref_id_value,sort_order,config_json)
select x.new_version_id,'SLA',sla.sla_rule_id,0,json_object('snapshot','PUBLISHED','ruleCode',sla.rule_code,
  'durationValue',sla.duration_value,'durationUnit',sla.duration_unit,'calendarCode',sla.calendar_code,
  'startStrategy',sla.start_strategy,'softRemindPercent',sla.soft_remind_percent,
  'hardRemindPercent',sla.hard_remind_percent,'escalatePercent',sla.escalate_percent,'version',sla.version)
from tmp_todo_phase_one_contract x join todo_sla_rule sla on sla.rule_code=x.sla_rule_code;

insert into todo_template_draft_rule_ref(version_id,ref_type,ref_id_value,sort_order,config_json)
select x.new_version_id,'DOD',dod.dod_rule_id,1,json_object('snapshot','PUBLISHED','ruleCode',dod.rule_code,
  'ruleType',dod.rule_type,'businessType',dod.business_type,'requiredFields',dod.required_fields_json,
  'requiredAttachments',dod.required_attachments_json,'conditionalRules',dod.conditional_rules_json,
  'validatorRefs',dod.validator_refs_json,'version',dod.version)
from tmp_todo_phase_one_contract x join todo_dod_rule dod on dod.rule_code=x.dod_rule_code;

update todo_template_version v
join tmp_todo_phase_one_contract x on v.template_id=(select t.template_id from todo_template t where t.template_code=x.template_code)
set v.status='RETIRED'
where v.status='PUBLISHED' and v.version_id<>x.new_version_id;

update todo_trigger_rule r
join tmp_todo_phase_one_contract x on r.template_id=(select t.template_id from todo_template t where t.template_code=x.template_code)
set r.enabled='N'
where r.enabled='Y' and r.template_version_id<>x.new_version_id;

insert into todo_trigger_rule(rule_code,rule_name,event_type,payload_version,template_id,template_version_id,
  business_type,enabled,condition_json,sort_order,create_by)
select concat('STD_',x.template_code,'_V',x.new_version_no),concat(t.template_name,'触发规则'),
  x.event_type,1,t.template_id,x.new_version_id,x.business_type,'Y',null,100,'flyway-v0.20.40'
from tmp_todo_phase_one_contract x join todo_template t on t.template_code=x.template_code
where not exists(select 1 from todo_trigger_rule r where r.event_type=x.event_type
  and r.payload_version=1 and r.template_version_id=x.new_version_id and r.business_type=x.business_type);

insert into todo_trigger_rule(rule_code,rule_name,event_type,payload_version,template_id,template_version_id,
  business_type,enabled,condition_json,sort_order,create_by)
select concat('STD_CASE_ACCEPT_TRANSFER_V',x.new_version_no),'转案通过后接案确认',
  'CASE_TRANSFER_APPROVED',1,t.template_id,x.new_version_id,'CASE','Y',json_object('requiresAcceptance',true),
  110,'flyway-v0.20.40'
from tmp_todo_phase_one_contract x join todo_template t on t.template_code=x.template_code
where x.template_code='CASE_ACCEPT'
  and not exists(select 1 from todo_trigger_rule r where r.event_type='CASE_TRANSFER_APPROVED'
    and r.payload_version=1 and r.template_version_id=x.new_version_id and r.business_type='CASE');

update todo_template t join tmp_todo_phase_one_contract x on x.template_code=t.template_code
set t.current_version=x.new_version_no,t.update_by='flyway-v0.20.40',t.update_time=sysdate();

drop temporary table tmp_todo_phase_one_contract;
drop temporary table tmp_todo_phase_one_dod;
