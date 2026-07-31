-- Govern the lead Todo journey without mutating immutable template versions,
-- trigger bindings, simulation evidence, or historical Todo instances.
-- Desired JSON is derived first so no-op re-execution does not touch optimistic
-- versions or audit timestamps.

set @lead_invalid_schema=(
  select json_set(
    payload_schema_json,
    '$.properties.leadId.title','线索',
    '$.properties.leadId.description','当前待办关联的线索业务对象',
    '$.properties.leadId."x-semantic-type"','BUSINESS_ID',
    '$.properties.leadId."x-option-source"','LEAD_DIRECTORY',
    '$.properties.leadId."x-business-type"','LEAD',
    '$.properties.leadId."x-read-only-business-context"',true,
    '$.properties.reviewId.title','复核记录',
    '$.properties.reviewId.description','本次疑似无效主管复核记录',
    '$.properties.reviewId."x-semantic-type"','SYSTEM_ID',
    '$.properties.reviewId."x-read-only-business-context"',true,
    '$.properties.reasonCode.title','疑似无效原因',
    '$.properties.reasonCode.description','销售提交主管复核时选择的标准无效原因',
    '$.properties.reasonCode."x-semantic-type"','DICT',
    '$.properties.reasonCode."x-option-source"','SYSTEM_DICTIONARY',
    '$.properties.reasonCode."x-dict-type"','law_lead_invalid_reason',
    '$.properties.reasonCode."x-read-only-business-context"',true,
    '$.properties.ownerId.title','原线索负责人',
    '$.properties.ownerId.description','提交疑似无效复核前的线索负责人',
    '$.properties.ownerId."x-semantic-type"','USER_ID',
    '$.properties.ownerId."x-option-source"','SYSTEM_USER',
    '$.properties.ownerId."x-owner-eligible"',true,
    '$.properties.ownerId."x-read-only-business-context"',true,
    '$.properties.reviewerId.title','复核主管',
    '$.properties.reviewerId.description','负责本次疑似无效复核的主管',
    '$.properties.reviewerId."x-semantic-type"','USER_ID',
    '$.properties.reviewerId."x-option-source"','SYSTEM_USER',
    '$.properties.reviewerId."x-owner-eligible"',true,
    '$.properties.reviewerId."x-read-only-business-context"',true,
    '$.properties.operatorId.title','提交人',
    '$.properties.operatorId.description','提交疑似无效标记的操作人',
    '$.properties.operatorId."x-semantic-type"','USER_ID',
    '$.properties.operatorId."x-option-source"','SYSTEM_USER',
    '$.properties.operatorId."x-read-only-business-context"',true)
  from todo_event_catalog
  where event_type='LEAD_SUSPECT_INVALID_MARKED' and payload_version=1
  limit 1
);

update todo_event_catalog
set payload_schema_json=cast(@lead_invalid_schema as json),
    owner_field_paths_json=json_array('reviewerId'),
    version=version+1,
    update_by='migration',
    update_time=sysdate()
where event_type='LEAD_SUSPECT_INVALID_MARKED' and payload_version=1
  and not (
    payload_schema_json <=> cast(@lead_invalid_schema as json)
    and owner_field_paths_json <=> json_array('reviewerId')
  );

set @lead_retry_schema=(
  select json_set(
    payload_schema_json,
    '$.properties.leadId.title','线索',
    '$.properties.leadId.description','当前重试计划关联的线索业务对象',
    '$.properties.leadId."x-semantic-type"','BUSINESS_ID',
    '$.properties.leadId."x-option-source"','LEAD_DIRECTORY',
    '$.properties.leadId."x-business-type"','LEAD',
    '$.properties.leadId."x-read-only-business-context"',true,
    '$.properties.planId.title','重试计划',
    '$.properties.planId.description','无法联系后生成的持久化重试计划',
    '$.properties.planId."x-semantic-type"','SYSTEM_ID',
    '$.properties.planId."x-read-only-business-context"',true,
    '$.properties.windowCode.title','当前重试窗口',
    '$.properties.windowCode.description','T0、T+1、T+2 时间轴中的当前处理窗口',
    '$.properties.windowCode."x-semantic-type"','SYSTEM_CODE',
    '$.properties.windowCode."x-option-source"','RETRY_WINDOW_CATALOG',
    '$.properties.windowCode."x-read-only-business-context"',true,
    '$.properties.occurrenceNo.title','执行序号',
    '$.properties.occurrenceNo.description','当前窗口在重试计划中的稳定执行序号',
    '$.properties.occurrenceNo."x-semantic-type"','SYSTEM_COUNTER',
    '$.properties.occurrenceNo."x-read-only-business-context"',true,
    '$.properties.ownerId.title','线索负责人',
    '$.properties.ownerId.description','当前负责联系该线索的用户',
    '$.properties.ownerId."x-semantic-type"','USER_ID',
    '$.properties.ownerId."x-option-source"','SYSTEM_USER',
    '$.properties.ownerId."x-owner-eligible"',true,
    '$.properties.ownerId."x-read-only-business-context"',true)
  from todo_event_catalog
  where event_type='LEAD_RETRY_WINDOW_DUE' and payload_version=1
  limit 1
);

update todo_event_catalog
set payload_schema_json=cast(@lead_retry_schema as json),
    owner_field_paths_json=json_array('ownerId'),
    version=version+1,
    update_by='migration',
    update_time=sysdate()
where event_type='LEAD_RETRY_WINDOW_DUE' and payload_version=1
  and not (
    payload_schema_json <=> cast(@lead_retry_schema as json)
    and owner_field_paths_json <=> json_array('ownerId')
  );

set @lead_progress_schema=(
  select json_set(
    payload_schema_json,
    '$.properties.leadId.title','线索',
    '$.properties.leadId.description','首联有效后进入五天跟进周期的线索',
    '$.properties.leadId."x-semantic-type"','BUSINESS_ID',
    '$.properties.leadId."x-option-source"','LEAD_DIRECTORY',
    '$.properties.leadId."x-business-type"','LEAD',
    '$.properties.leadId."x-read-only-business-context"',true,
    '$.properties.followupId.title','首联记录',
    '$.properties.followupId.description','本次有效首联对应的跟进记录',
    '$.properties.followupId."x-semantic-type"','SYSTEM_ID',
    '$.properties.followupId."x-read-only-business-context"',true,
    '$.properties.ownerId.title','线索负责人',
    '$.properties.ownerId.description','负责后续五天实质进展的用户',
    '$.properties.ownerId."x-semantic-type"','USER_ID',
    '$.properties.ownerId."x-option-source"','SYSTEM_USER',
    '$.properties.ownerId."x-owner-eligible"',true,
    '$.properties.ownerId."x-read-only-business-context"',true,
    '$.properties.operatorId.title','首联操作人',
    '$.properties.operatorId.description','完成本次有效首联的操作人',
    '$.properties.operatorId."x-semantic-type"','USER_ID',
    '$.properties.operatorId."x-option-source"','SYSTEM_USER',
    '$.properties.operatorId."x-read-only-business-context"',true,
    '$.properties.contactResult.title','首联结果',
    '$.properties.contactResult.description','触发五天实质进展任务的有效首联结论',
    '$.properties.contactResult."x-semantic-type"','DICT',
    '$.properties.contactResult."x-option-source"','SYSTEM_DICTIONARY',
    '$.properties.contactResult."x-dict-type"','law_first_contact_result',
    '$.properties.contactResult."x-read-only-business-context"',true)
  from todo_event_catalog
  where event_type='LEAD_FIRST_CONTACT_VALID' and payload_version=1
  limit 1
);

update todo_event_catalog
set payload_schema_json=cast(@lead_progress_schema as json),
    owner_field_paths_json=json_array('ownerId'),
    version=version+1,
    update_by='migration',
    update_time=sysdate()
where event_type='LEAD_FIRST_CONTACT_VALID' and payload_version=1
  and not (
    payload_schema_json <=> cast(@lead_progress_schema as json)
    and owner_field_paths_json <=> json_array('ownerId')
  );

drop temporary table if exists tmp_lead_todo_guided_resource;
create temporary table tmp_lead_todo_guided_resource(
  resource_type varchar(24) not null,
  resource_code varchar(128) not null,
  resource_name varchar(128) not null,
  description varchar(500) null,
  business_type varchar(32) not null,
  value_json json not null,
  status varchar(16) not null,
  sort_order int not null,
  primary key(resource_type,business_type,resource_code)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

insert into tmp_lead_todo_guided_resource(
  resource_type,resource_code,resource_name,description,business_type,value_json,status,sort_order
) values
('FIELD','reviewResult','复核结果','主管对疑似无效线索给出的最终复核结论','LEAD',
  json_object('type','string','semanticType','DICT','optionSource','SYSTEM_DICTIONARY',
    'dictType','law_lead_invalid_review_result','operators',json_array('EQ','NE','IN')),'ACTIVE',110),
('FIELD','reviewOpinion','复核意见','主管填写的复核依据和处理意见','LEAD',
  json_object('type','string','semanticType','PLAIN_VALUE','operators',json_array('EQ','NE','CONTAINS')),'ACTIVE',120),
('FIELD','contactResult','联系结果','首联或重试窗口完成后的受控联系结果','LEAD',
  json_object('type','string','semanticType','DICT','optionSource','SYSTEM_DICTIONARY',
    'dictType','law_first_contact_result','operators',json_array('EQ','NE','IN')),'ACTIVE',130),
('FIELD','name','客户姓名','联系成功后补充的客户姓名','LEAD',
  json_object('type','string','semanticType','PLAIN_VALUE','operators',json_array('EQ','NE','CONTAINS')),'ACTIVE',140),
('FIELD','city','所在城市','联系成功后补充的客户所在城市','LEAD',
  json_object('type','string','semanticType','PLAIN_VALUE','operators',json_array('EQ','NE','IN')),'ACTIVE',150),
('FIELD','demand','客户诉求','联系成功后记录的咨询或委托诉求','LEAD',
  json_object('type','string','semanticType','PLAIN_VALUE','operators',json_array('EQ','NE','CONTAINS')),'ACTIVE',160),
('FIELD','visited','是否到所','客户是否已经到所面谈','LEAD',
  json_object('type','string','semanticType','DICT','optionSource','SYSTEM_DICTIONARY',
    'dictType','law_yes_no_flag','operators',json_array('EQ','NE')),'ACTIVE',170),
('FIELD','progressType','实质进展类型','五天周期内取得的实质进展类型','LEAD',
  json_object('type','string','semanticType','DICT','optionSource','SYSTEM_DICTIONARY',
    'dictType','law_lead_progress_type','operators',json_array('EQ','NE','IN')),'ACTIVE',180),
('FIELD','progressAt','进展发生时间','本次实质进展实际发生的时间','LEAD',
  json_object('type','string','format','date-time','semanticType','DATE_TIME',
    'operators',json_array('EQ','NE','GT','GTE','LT','LTE')),'ACTIVE',190),
('FIELD','remark','进展说明','对本次实质进展的补充说明','LEAD',
  json_object('type','string','semanticType','PLAIN_VALUE','operators',json_array('EQ','NE','CONTAINS')),'ACTIVE',200),
('MATERIAL','CONTACT_PROOF','联系凭证','每次拨号必须提交的通话记录、录音或人工补录凭证','LEAD',
  json_object(),'ACTIVE',20),
('MATERIAL','FOLLOWUP_PROOF','实质进展凭证','录音、截图、报价或外访凭证','LEAD',
  json_object(),'ACTIVE',30),
('DOD_RECIPE','LEAD_INVALID_REVIEW_READY','主管复核完成','填写复核结论和复核意见','LEAD',
  json_object('requiredFields',json_array('reviewResult','reviewOpinion'),
    'requiredAttachments',json_array(),'validatorRefs',json_array(),'conditionalRules',json_array()),'ACTIVE',110),
('DOD_RECIPE','LEAD_RETRY_READY','重试拨打完成','填写联系结果并提交本次拨号凭证','LEAD',
  json_object('requiredFields',json_array('contactResult'),
    'requiredAttachments',json_array('CONTACT_PROOF'),
    'validatorRefs',json_array('LeadFirstContactValidator'),
    'conditionalRules',json_array(json_object(
      'when',json_object('field','contactResult','operator','EQ','value','CONNECTED'),
      'requiredFields',json_array('name','city','demand','visited')))),'ACTIVE',120),
('DOD_RECIPE','LEAD_PROGRESS_READY','五天实质进展完成','登记实质进展并提交跟进凭证','LEAD',
  json_object('requiredFields',json_array('progressType','progressAt'),
    'requiredAttachments',json_array('FOLLOWUP_PROOF'),
    'validatorRefs',json_array(),'conditionalRules',json_array()),'ACTIVE',130);

insert into todo_configuration_resource_item(
  resource_type,resource_code,resource_name,description,business_type,
  value_json,status,sort_order,create_by
)
select resource_type,resource_code,resource_name,description,business_type,
       value_json,status,sort_order,'migration'
from tmp_lead_todo_guided_resource
on duplicate key update
  resource_item_id=todo_configuration_resource_item.resource_item_id;

update todo_configuration_resource_item resource
join tmp_lead_todo_guided_resource desired
  on desired.resource_type=resource.resource_type
 and desired.business_type=resource.business_type
 and desired.resource_code=resource.resource_code
set resource.resource_name=desired.resource_name,
    resource.description=desired.description,
    resource.value_json=desired.value_json,
    resource.status=desired.status,
    resource.sort_order=desired.sort_order,
    resource.version=resource.version+1,
    resource.update_by='migration',
    resource.update_time=sysdate()
where not (
  resource.resource_name <=> desired.resource_name
  and resource.description <=> desired.description
  and resource.value_json <=> desired.value_json
  and resource.status <=> desired.status
  and resource.sort_order <=> desired.sort_order
);

drop temporary table tmp_lead_todo_guided_resource;
