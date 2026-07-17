-- Retrofit the 15 technical templates to the v0.2 definition/file/action contract.
-- Existing versions remain immutable. New published versions and trigger rules are created.

set @case_manager_role_id=(select role_id from sys_role where role_key='case_manager' and del_flag='0' limit 1);
set @finance_role_id=(select role_id from sys_role where role_key='finance_manager' and del_flag='0' limit 1);
set @partner_role_id=(select role_id from sys_role where role_key='law_partner_manager' and del_flag='0' limit 1);

create temporary table tmp_todo_task11_role_guard (
  role_count int not null,
  constraint ck_task11_required_roles_present check (role_count=3)
) engine=innodb;
insert into tmp_todo_task11_role_guard(role_count)
values ((@case_manager_role_id is not null)+(@finance_role_id is not null)+(@partner_role_id is not null));
drop temporary table tmp_todo_task11_role_guard;

create temporary table tmp_todo_template_contract (
  template_code varchar(64) not null,
  event_type varchar(100) not null,
  business_type varchar(100) not null,
  owner_legacy varchar(128) not null,
  owner_config longtext not null,
  dod_config longtext not null,
  sla_minutes int not null,
  ui_config longtext not null,
  source_version_id bigint null,
  new_version_no int null,
  new_version_id bigint null,
  definition_text longtext null,
  primary key (template_code)
) engine=innodb;

insert into tmp_todo_template_contract(template_code,event_type,business_type,owner_legacy,owner_config,dod_config,sla_minutes,ui_config) values
('LEAD_FIRST_CONTACT','LEAD_ASSIGNED','LEAD','PAYLOAD:toOwnerId','{"operand":"toOwnerId","type":"PAYLOAD"}','{"materials":[{"minCount":1,"type":"CONTACT_PROOF"}],"requiredFields":["contactResult"]}',30,'{"fields":[{"key":"contactResult","type":"text"},{"key":"contactProof","materialType":"CONTACT_PROOF","type":"file"}],"fileRelationType":"CONTACT_PROOF","formCode":"LEAD_FIRST_CONTACT"}'),
('CONTRACT_REVIEW','CONTRACT_SUBMITTED','CONTRACT',concat('ROLE:',@partner_role_id),concat('{"operand":',@partner_role_id,',"type":"ROLE"}'),'{"requiredFields":["reviewAction","opinion"]}',240,'{"fields":[{"key":"reviewAction","type":"dict"},{"key":"opinion","type":"textarea"}],"formCode":"CONTRACT_REVIEW"}'),
('CONTRACT_SIGN','CONTRACT_APPROVED','CONTRACT','PAYLOAD:ownerId','{"operand":"ownerId","type":"PAYLOAD"}','{"materials":[{"minCount":1,"type":"SIGNED_CONTRACT"}],"requiredFields":["signStatus","signMethod","signDate"]}',960,'{"fields":[{"key":"signStatus","type":"dict"},{"key":"signMethod","type":"dict"},{"key":"signDate","type":"date"},{"key":"signedContract","materialType":"SIGNED_CONTRACT","type":"file"}],"fileRelationType":"SIGNED_CONTRACT","formCode":"CONTRACT_SIGN"}'),
('PAYMENT_CONFIRM','CONTRACT_SIGNED','CONTRACT',concat('ROLE:',@finance_role_id),concat('{"operand":',@finance_role_id,',"type":"ROLE"}'),'{"requiredFields":["planId","receivedAmount","voucherUrl"]}',480,'{"fields":[{"key":"planId","type":"number"},{"key":"receivedAmount","type":"number"},{"key":"voucherUrl","type":"text"}],"formCode":"PAYMENT_CONFIRM"}'),
('INVOICE_HANDLE','PAYMENT_CONFIRMED','CONTRACT',concat('ROLE:',@finance_role_id),concat('{"operand":',@finance_role_id,',"type":"ROLE"}'),'{"requiredFields":["planId","action"]}',960,'{"fields":[{"key":"planId","type":"number"},{"key":"action","type":"dict"},{"key":"invoiceNo","type":"text"},{"key":"invoiceFileUrl","type":"text"}],"formCode":"INVOICE_HANDLE"}'),
('CASE_CREATE_CHECK','INVOICE_HANDLED','CONTRACT',concat('ROLE:',@case_manager_role_id),concat('{"operand":',@case_manager_role_id,',"type":"ROLE"}'),'{"requiredFields":["materialsChecked"]}',960,'{"fields":[{"key":"materialsChecked","type":"dict"}],"formCode":"CASE_CREATE_CHECK"}'),
('CASE_ASSIGN','CASE_CREATED','CASE',concat('ROLE:',@case_manager_role_id),concat('{"operand":',@case_manager_role_id,',"type":"ROLE"}'),'{"requiredFields":["lawyerId"]}',480,'{"fields":[{"key":"lawyerId","type":"user"}],"formCode":"CASE_ASSIGN"}'),
('CASE_ACCEPT','CASE_ASSIGNED','CASE','PAYLOAD:mainLawyerId','{"operand":"mainLawyerId","type":"PAYLOAD"}','{"requiredFields":["confirmId","accepted"]}',240,'{"fields":[{"key":"confirmId","type":"number"},{"key":"accepted","type":"dict"}],"formCode":"CASE_ACCEPT"}'),
('CASE_REASSIGN','CASE_REJECTED','CASE',concat('ROLE:',@case_manager_role_id),concat('{"operand":',@case_manager_role_id,',"type":"ROLE"}'),'{"requiredFields":["lawyerId"]}',960,'{"fields":[{"key":"lawyerId","type":"user"}],"formCode":"CASE_REASSIGN"}'),
('CASE_TRANSFER_REVIEW','CASE_TRANSFER_REQUESTED','CASE',concat('ROLE:',@partner_role_id),concat('{"operand":',@partner_role_id,',"type":"ROLE"}'),'{"requiredFields":["transferId","reviewAction","opinion"]}',960,'{"fields":[{"key":"transferId","type":"number"},{"key":"reviewAction","type":"dict"},{"key":"opinion","type":"textarea"}],"formCode":"CASE_TRANSFER_REVIEW"}'),
('MATTER_NODE_HANDLE','MATTER_NODE_READY','MATTER','PAYLOAD:ownerId','{"operand":"ownerId","type":"PAYLOAD"}','{"requiredFields":["nodeId","actualDate"]}',960,'{"fields":[{"key":"nodeId","type":"number"},{"key":"actualDate","type":"date"}],"formCode":"MATTER_NODE_HANDLE"}'),
('MATTER_EXPENSE_REVIEW','MATTER_EXPENSE_SUBMITTED','MATTER',concat('ROLE:',@finance_role_id),concat('{"operand":',@finance_role_id,',"type":"ROLE"}'),'{"requiredFields":["expenseId","result"]}',960,'{"fields":[{"key":"expenseId","type":"number"},{"key":"result","type":"dict"}],"formCode":"MATTER_EXPENSE_REVIEW"}'),
('MATTER_DOCUMENT_SUPPLY','MATTER_DOCUMENT_REQUIRED','MATTER','PAYLOAD:ownerId','{"operand":"ownerId","type":"PAYLOAD"}','{"requiredFields":["documentType","fileName","fileUrl"]}',960,'{"fields":[{"key":"documentType","type":"dict"},{"key":"fileName","type":"text"},{"key":"fileUrl","type":"text"}],"formCode":"MATTER_DOCUMENT_SUPPLY"}'),
('CASE_CLOSE_CONFIRM','ARCHIVE_APPLIED','MATTER',concat('ROLE:',@case_manager_role_id),concat('{"operand":',@case_manager_role_id,',"type":"ROLE"}'),'{"requiredFields":["action","opinion","feeClearStatus"]}',960,'{"fields":[{"key":"action","type":"dict"},{"key":"opinion","type":"textarea"},{"key":"feeClearStatus","type":"dict"}],"formCode":"CASE_CLOSE_CONFIRM"}'),
('CASE_ARCHIVE_CONFIRM','CASE_CLOSED','MATTER',concat('ROLE:',@case_manager_role_id),concat('{"operand":',@case_manager_role_id,',"type":"ROLE"}'),'{"requiredFields":["action","opinion","archiveNo"]}',960,'{"fields":[{"key":"action","type":"dict"},{"key":"opinion","type":"textarea"},{"key":"archiveNo","type":"text"}],"formCode":"CASE_ARCHIVE_CONFIRM"}');

update tmp_todo_template_contract x
join todo_template t on t.template_code=x.template_code
set x.source_version_id=(select v.version_id from todo_template_version v where v.template_id=t.template_id order by v.version_no desc limit 1);

create temporary table tmp_todo_task11_source_guard (
  template_count int not null,
  source_version_count int not null,
  constraint ck_task11_all_15_templates_present check (template_count=15),
  constraint ck_task11_all_15_source_versions_present check (source_version_count=15)
) engine=innodb;
insert into tmp_todo_task11_source_guard(template_count,source_version_count)
select count(t.template_id),count(x.source_version_id)
from tmp_todo_template_contract x left join todo_template t on t.template_code=x.template_code;
drop temporary table tmp_todo_task11_source_guard;

update tmp_todo_template_contract
set definition_text=concat(
  '{"acceptanceRefs":[],"autoActions":[],"decisionRefs":[],"dod":{"config":',dod_config,
  '},"event":{"condition":{},"eventType":"',event_type,'","payloadVersion":1},"owner":{"config":',owner_config,
  '},"routing":{"config":{}},"schemaVersion":1,"sla":{"config":{"calendarCode":"DEFAULT","minutes":',sla_minutes,
  '}},"templateCode":"',template_code,'","ui":{"config":',ui_config,'}}');

update tmp_todo_template_contract x
join todo_template t on t.template_code=x.template_code
join todo_template_version v on v.template_id=t.template_id
  and v.status='PUBLISHED'
  and v.definition_hash=lower(sha2(x.definition_text,256))
  and v.version_no=(select max(v2.version_no) from todo_template_version v2
    where v2.template_id=t.template_id and v2.status='PUBLISHED'
      and v2.definition_hash=lower(sha2(x.definition_text,256)))
set x.new_version_id=v.version_id,x.new_version_no=v.version_no;

update tmp_todo_template_contract x
join todo_template t on t.template_code=x.template_code
set x.new_version_no=(select coalesce(max(v.version_no),0)+1 from todo_template_version v where v.template_id=t.template_id)
where x.new_version_id is null;

insert into todo_event_catalog(event_type,payload_version,business_object_type,payload_schema_json,
  owner_field_paths_json,condition_field_paths_json,default_value_field_paths_json,producer,sample_payload_json,status,create_by)
select distinct x.event_type,1,x.business_type,
  cast('{"additionalProperties":true,"type":"object"}' as json),json_array(),json_array(),json_array(),
  'law-business',json_object(),'ACTIVE','admin'
from tmp_todo_template_contract x
where not exists(select 1 from todo_event_catalog c where c.event_type=x.event_type and c.payload_version=1);

insert into todo_event_catalog(event_type,payload_version,business_object_type,payload_schema_json,
  owner_field_paths_json,condition_field_paths_json,default_value_field_paths_json,producer,sample_payload_json,status,create_by)
select 'CASE_TRANSFER_APPROVED',1,'CASE',cast('{"additionalProperties":true,"type":"object"}' as json),
  json_array(),json_array(),json_array(),'law-business',json_object(),'ACTIVE','admin'
where not exists(select 1 from todo_event_catalog c where c.event_type='CASE_TRANSFER_APPROVED' and c.payload_version=1);

insert into todo_template_version(template_id,version_no,status,source_version_id,owner_rule_json,dod_rule_json,
  sla_rule_json,next_rule_json,ui_schema_json,definition_schema_version,definition_json,compiled_json,
  definition_hash,validation_report_json,published_by,published_time)
select t.template_id,x.new_version_no,'PUBLISHED',x.source_version_id,json_quote(x.owner_legacy),cast(x.dod_config as json),
  json_object('calendarCode','DEFAULT','minutes',x.sla_minutes),null,cast(x.ui_config as json),1,
  cast(x.definition_text as json),cast(x.definition_text as json),lower(sha2(x.definition_text,256)),
  cast('{"errors":[],"warnings":[]}' as json),'admin',sysdate()
from tmp_todo_template_contract x join todo_template t on t.template_code=x.template_code
where x.new_version_id is null;

update tmp_todo_template_contract x
join todo_template t on t.template_code=x.template_code
join todo_template_version v on v.template_id=t.template_id and v.version_no=x.new_version_no
set x.new_version_id=v.version_id;

update todo_trigger_rule r
join todo_template t on t.template_id=r.template_id
join tmp_todo_template_contract x on x.template_code=t.template_code and x.event_type=r.event_type
set r.enabled='N'
where r.template_version_id<>x.new_version_id and r.enabled='Y';

update todo_trigger_rule r
join todo_template t on t.template_id=r.template_id
join tmp_todo_template_contract x on x.template_code='CASE_ACCEPT' and t.template_code=x.template_code
set r.enabled='N'
where r.event_type='CASE_TRANSFER_APPROVED' and r.template_version_id<>x.new_version_id and r.enabled='Y';

insert into todo_trigger_rule(event_type,template_id,template_version_id,business_type,enabled,condition_json)
select x.event_type,t.template_id,x.new_version_id,x.business_type,'Y',null
from tmp_todo_template_contract x join todo_template t on t.template_code=x.template_code
where not exists(select 1 from todo_trigger_rule r where r.event_type=x.event_type
  and r.template_version_id=x.new_version_id and r.business_type=x.business_type);

insert into todo_trigger_rule(event_type,template_id,template_version_id,business_type,enabled,condition_json)
select 'CASE_TRANSFER_APPROVED',t.template_id,x.new_version_id,'CASE','Y',json_object('requiresAcceptance',true)
from tmp_todo_template_contract x join todo_template t on t.template_code=x.template_code
where x.template_code='CASE_ACCEPT'
  and not exists(select 1 from todo_trigger_rule r where r.event_type='CASE_TRANSFER_APPROVED'
    and r.template_version_id=x.new_version_id and r.business_type='CASE');

update todo_template t join tmp_todo_template_contract x on x.template_code=t.template_code
set t.current_version=x.new_version_no,t.update_by='admin',t.update_time=sysdate();

drop temporary table tmp_todo_template_contract;
