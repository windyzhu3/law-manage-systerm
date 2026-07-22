-- CASE_ASSIGNED is governed by the event catalogue sample, whose assignee field is lawyerId.
-- Publish a corrective immutable version instead of mutating the 0.20.40 release snapshot.

set @case_accept_definition='{"acceptanceRefs":[],"autoActions":[],"decisionRefs":[],"dod":{"config":{"requiredFields":["confirmId","accepted"]}},"event":{"condition":{},"eventType":"CASE_ASSIGNED","payloadVersion":1},"owner":{"config":{"operand":"lawyerId","type":"PAYLOAD"}},"routing":{"config":{}},"schemaVersion":1,"sla":{"config":{"calendarCode":"DEFAULT","minutes":240}},"templateCode":"CASE_ACCEPT","ui":{"config":{"fields":[{"key":"confirmId","type":"number"},{"key":"accepted","type":"dict"}],"formCode":"CASE_ACCEPT"}}}';

set @case_accept_template_id=(select template_id from todo_template where template_code='CASE_ACCEPT' limit 1);
set @case_accept_source_version_id=(select version_id from todo_template_version
  where template_id=@case_accept_template_id and status='PUBLISHED'
  order by version_no desc,version_id desc limit 1);
set @case_accept_new_version_no=(select coalesce(max(version_no),0)+1 from todo_template_version
  where template_id=@case_accept_template_id);

create temporary table tmp_case_accept_trigger as
select rule_code,rule_name,event_type,payload_version,business_type,condition_json,sort_order
from todo_trigger_rule
where template_id=@case_accept_template_id and template_version_id=@case_accept_source_version_id and enabled='Y';

insert into todo_template_version(template_id,version_no,status,source_version_id,owner_rule_json,dod_rule_json,
  sla_rule_json,next_rule_json,ui_schema_json,change_summary,impact_scope,rollback_source_version_id,
  definition_schema_version,definition_json,compiled_json,definition_hash,validation_report_json,
  published_by,published_time)
select template_id,@case_accept_new_version_no,'PUBLISHED',version_id,json_quote('PAYLOAD:lawyerId'),dod_rule_json,
  sla_rule_json,next_rule_json,ui_schema_json,'修正接案确认负责人字段为事件目录 lawyerId',
  'CASE_ACCEPT 负责人解析',version_id,definition_schema_version,cast(@case_accept_definition as json),
  cast(@case_accept_definition as json),lower(sha2(@case_accept_definition,256)),
  json_object('errors',json_array(),'warnings',json_array()),'flyway-v0.20.41',sysdate()
from todo_template_version where version_id=@case_accept_source_version_id;

set @case_accept_new_version_id=(select version_id from todo_template_version
  where template_id=@case_accept_template_id and version_no=@case_accept_new_version_no limit 1);

insert into todo_template_draft_rule_ref(version_id,ref_type,ref_id_value,sort_order,config_json)
select @case_accept_new_version_id,ref_type,ref_id_value,sort_order,config_json
from todo_template_draft_rule_ref where version_id=@case_accept_source_version_id;

update todo_trigger_rule set enabled='N',update_by='flyway-v0.20.41',update_time=sysdate(),version=version+1
where template_id=@case_accept_template_id and enabled='Y';

insert into todo_trigger_rule(rule_code,rule_name,event_type,payload_version,template_id,template_version_id,
  business_type,enabled,condition_json,sort_order,create_by)
select concat(left(rule_code,52),'_FIX_V',@case_accept_new_version_no),rule_name,event_type,payload_version,
  @case_accept_template_id,@case_accept_new_version_id,business_type,'Y',condition_json,sort_order,'flyway-v0.20.41'
from tmp_case_accept_trigger;

update todo_template_version set status='RETIRED'
where version_id=@case_accept_source_version_id and status='PUBLISHED';

update todo_template set current_version=@case_accept_new_version_no,update_by='flyway-v0.20.41',update_time=sysdate()
where template_id=@case_accept_template_id;

drop temporary table tmp_case_accept_trigger;
