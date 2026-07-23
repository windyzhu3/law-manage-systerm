update todo_configuration_resource_item
set value_json=json_set(
      value_json,
      '$.businessActions',json_array('LEAD_ASSIGNED'),
      '$.templateStages',json_array()
    ),
    update_by='migration',
    update_time=sysdate()
where resource_type='DOD_RECIPE'
  and business_type='LEAD'
  and resource_code='LEAD_FIRST_CONTACT_READY';

update todo_configuration_resource_item
set value_json=json_set(
      value_json,
      '$.businessActions',json_array('LEAD_FIRST_CONTACT_VALID'),
      '$.templateStages',json_array()
    ),
    update_by='migration',
    update_time=sysdate()
where resource_type='DOD_RECIPE'
  and business_type='CUSTOMER'
  and resource_code='CUSTOMER_PROGRESS_READY';

update todo_configuration_resource_item
set value_json=json_set(
      value_json,
      '$.businessActions',json_array('CONTRACT_APPROVED'),
      '$.templateStages',json_array()
    ),
    update_by='migration',
    update_time=sysdate()
where resource_type='DOD_RECIPE'
  and business_type='CONTRACT'
  and resource_code='CONTRACT_SIGN_READY';

update todo_configuration_resource_item
set value_json=json_set(
      value_json,
      '$.businessActions',json_array('CASE_ASSIGNED'),
      '$.templateStages',json_array()
    ),
    update_by='migration',
    update_time=sysdate()
where resource_type='DOD_RECIPE'
  and business_type='CASE'
  and resource_code='CASE_ACCEPT_READY';

update todo_configuration_resource_item
set value_json=json_set(
      value_json,
      '$.businessActions',json_array('CASE_CLOSED'),
      '$.templateStages',json_array()
    ),
    update_by='migration',
    update_time=sysdate()
where resource_type='DOD_RECIPE'
  and business_type='MATTER'
  and resource_code='MATTER_ARCHIVE_READY';
