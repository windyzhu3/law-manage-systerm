-- Semantic metadata keeps canonical IDs/codes stable while allowing every editor
-- and simulation response to render governed business labels.
update todo_configuration_resource_item
set value_json=case resource_code
  when 'contactResult' then json_set(value_json,
    '$.semanticType','DICT','$.dictType','law_first_contact_result',
    '$.optionSource','SYSTEM_DICTIONARY')
  when 'reviewResult' then json_set(value_json,
    '$.semanticType','DICT','$.dictType','law_lead_invalid_review_result',
    '$.optionSource','SYSTEM_DICTIONARY')
  when 'attemptStage' then json_set(value_json,
    '$.semanticType','DICT','$.dictType','law_retry_stage',
    '$.optionSource','SYSTEM_DICTIONARY')
  when 'visited' then json_set(value_json,
    '$.semanticType','DICT','$.dictType','law_yes_no_flag',
    '$.optionSource','SYSTEM_DICTIONARY')
  else value_json
end,
version=version+1,
update_by='migration',
update_time=sysdate()
where resource_type='FIELD'
  and business_type='LEAD'
  and resource_code in ('contactResult','reviewResult','attemptStage','visited');

update todo_event_catalog
set payload_schema_json=json_set(payload_schema_json,
      '$.properties.ownerId."x-semantic-type"','USER_ID',
      '$.properties.ownerId."x-option-source"','SYSTEM_USER',
      '$.properties.ownerDeptId."x-semantic-type"','DEPT_ID',
      '$.properties.ownerDeptId."x-option-source"','SYSTEM_DEPARTMENT',
      '$.properties.operatorId."x-semantic-type"','USER_ID',
      '$.properties.operatorId."x-option-source"','SYSTEM_USER'),
    version=version+1,
    update_by='migration',
    update_time=sysdate()
where event_type='LEAD_ASSIGNED' and payload_version=1;
