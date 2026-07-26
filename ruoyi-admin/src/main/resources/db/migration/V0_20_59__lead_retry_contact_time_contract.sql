-- TD-003 records a real call fact, whose canonical runtime contract requires
-- startedAt/contactedAt. Publish a new immutable template version instead of
-- rewriting the already-published historical version.
start transaction;

set @td003_template_id=(
  select t.template_id from todo_template t
  where t.template_code='TD-003'
);
set @td003_source_version_id=(
  select v.version_id
  from todo_template t
  join todo_template_version v
    on v.template_id=t.template_id and v.version_no=t.current_version
  where t.template_code='TD-003'
);
set @td003_needs_fix=(
  select case
    when json_contains_path(v.ui_schema_json,'one',
      '$.fields[*].contactedAt')=1 then 0
    when json_search(v.ui_schema_json,'one','contactedAt',null,
      '$.fields[*].key') is not null then 0
    else 1
  end
  from todo_template t
  join todo_template_version v
    on v.template_id=t.template_id and v.version_no=t.current_version
  where t.template_code='TD-003'
);
set @td003_new_version_no=(
  select t.current_version+@td003_needs_fix from todo_template t
  where t.template_code='TD-003'
);

insert into todo_template_version(
  template_id,version_no,status,source_version_id,
  owner_rule_json,dod_rule_json,sla_rule_json,next_rule_json,ui_schema_json,
  change_summary,impact_scope,rollback_source_version_id,
  definition_schema_version,definition_json,compiled_json,definition_hash,
  validation_report_json,published_by,published_time,update_by,update_time
)
select
  source.template_id,@td003_new_version_no,'PUBLISHED',source.version_id,
  source.owner_rule_json,
  json_set(
    source.dod_rule_json,
    '$.requiredFields',json_array('contactResult','contactedAt')
  ),
  source.sla_rule_json,source.next_rule_json,
  json_set(
    source.ui_schema_json,
    '$.fields',json_array_append(
      json_extract(source.ui_schema_json,'$.fields'),'$',
      json_object('key','contactedAt','type','datetime','label','联系时间')
    )
  ),
  '补齐重试联系时间事实','TD-003 员工表单与通话事实契约',
  source.version_id,source.definition_schema_version,
  json_set(
    source.definition_json,
    '$.dod.config.requiredFields',json_array('contactResult','contactedAt'),
    '$.ui.config.fields',json_array_append(
      json_extract(source.definition_json,'$.ui.config.fields'),'$',
      json_object('key','contactedAt','type','datetime','label','联系时间')
    )
  ),
  json_set(
    source.compiled_json,
    '$.dod.config.requiredFields',json_array('contactResult','contactedAt'),
    '$.ui.config.fields',json_array_append(
      json_extract(source.compiled_json,'$.ui.config.fields'),'$',
      json_object('key','contactedAt','type','datetime','label','联系时间')
    )
  ),
  lower(sha2(cast(json_set(
    source.definition_json,
    '$.dod.config.requiredFields',json_array('contactResult','contactedAt'),
    '$.ui.config.fields',json_array_append(
      json_extract(source.definition_json,'$.ui.config.fields'),'$',
      json_object('key','contactedAt','type','datetime','label','联系时间')
    )
  ) as char),256)),
  json_object('errors',json_array(),'warnings',json_array()),
  'migration',sysdate(),'migration',sysdate()
from todo_template_version source
where source.version_id=@td003_source_version_id
  and @td003_needs_fix=1;

set @td003_new_version_id=if(
  @td003_needs_fix=1,last_insert_id(),@td003_source_version_id
);

update todo_template t
set t.current_version=@td003_new_version_no,
    t.update_by='migration',
    t.update_time=sysdate()
where t.template_id=@td003_template_id;

update biz_lead_assignment_policy
set retry_rule_json=json_set(
      retry_rule_json,'$.templateVersionId',@td003_new_version_id
    ),
    row_version=row_version+1,
    update_by='migration',
    update_time=sysdate()
where status='ACTIVE'
  and json_extract(retry_rule_json,'$.templateVersionId') is not null;

update todo_prd_definition_catalog catalog
join todo_template_version version on version.version_id=@td003_new_version_id
set catalog.definition_json=version.definition_json
where catalog.template_code='TD-003';

commit;
