-- Create a repairable TD-001 draft without changing the published entry point or
-- copying definition-bound simulation evidence. The draft is intentionally not
-- made current: an administrator must review, simulate and publish it.

set @td001_template_id=(
  select template_id from todo_template where template_code='TD-001'
);
set @td001_source_version_id=(
  select v.version_id
  from todo_template_version v
  where v.template_id=@td001_template_id and v.status='PUBLISHED'
  order by v.version_no desc,v.version_id desc
  limit 1
);
set @td002_target_version_id=(
  select v.version_id
  from todo_template t
  join todo_template_version v on v.template_id=t.template_id
  where t.template_code='TD-002' and v.status='PUBLISHED'
  order by v.version_no desc,v.version_id desc
  limit 1
);
set @td003_target_version_id=(
  select v.version_id
  from todo_template t
  join todo_template_version v on v.template_id=t.template_id
  where t.template_code='TD-003' and v.status='PUBLISHED'
  order by v.version_no desc,v.version_id desc
  limit 1
);
set @td004_target_version_id=(
  select v.version_id
  from todo_template t
  join todo_template_version v on v.template_id=t.template_id
  where t.template_code='TD-004' and v.status='PUBLISHED'
  order by v.version_no desc,v.version_id desc
  limit 1
);

create temporary table tmp_td001_configuration_guard (
  template_count int not null,
  source_count int not null,
  target_count int not null,
  constraint chk_td001_configuration_template check(template_count=1),
  constraint chk_td001_configuration_source check(source_count=1),
  constraint chk_td001_configuration_targets check(target_count=3)
) engine=innodb;
insert into tmp_td001_configuration_guard(template_count,source_count,target_count)
select
  if(@td001_template_id is null,0,1),
  if(@td001_source_version_id is null,0,1),
  if(@td002_target_version_id is null,0,1)
    +if(@td003_target_version_id is null,0,1)
    +if(@td004_target_version_id is null,0,1);
drop temporary table tmp_td001_configuration_guard;

set @td001_repair_marker='V0.20.65 TD-001 配置易用性修复草稿';
set @td001_repair_exists=(
  select count(*)
  from todo_template_version
  where template_id=@td001_template_id
    and status in ('DRAFT','BLOCKED')
    and change_summary=@td001_repair_marker
);
set @td001_repair_version_no=(
  select coalesce(max(version_no),0)+1
  from todo_template_version
  where template_id=@td001_template_id
);
set @td001_repair_version_id=(
  select greatest(
    coalesce(max(version_id),0)+1,
    coalesce((
      select auto_increment
      from information_schema.tables
      where table_schema=database() and table_name='todo_template_version'
    ),1)
  )
  from todo_template_version
);

set @td001_repair_routing=json_object(
  'identityBinding','MIGRATION_DYNAMIC',
  'start','current_task',
  'businessRouting',json_object('mode','SEQUENTIAL','joinMode','ALL'),
  'businessOutcomes',json_array(
    json_object(
      'id','contactResult_VALID',
      'label','当首联结果为有效时',
      'resultField','contactResult',
      'resultValue','VALID',
      'resultLabel','有效',
      'resultType','NEXT',
      'targetVersionId',@td004_target_version_id,
      'targetTemplateCode','TD-004',
      'default',false,
      'condition',json_object('$expression',json_object(
        'version',1,
        'root',json_object('field','contactResult','operator','EQ','value','VALID')
      ))
    ),
    json_object(
      'id','contactResult_SUSPECT_INVALID',
      'label','当首联结果为疑似无效时',
      'resultField','contactResult',
      'resultValue','SUSPECT_INVALID',
      'resultLabel','疑似无效',
      'resultType','NEXT',
      'targetVersionId',@td002_target_version_id,
      'targetTemplateCode','TD-002',
      'default',false,
      'condition',json_object('$expression',json_object(
        'version',1,
        'root',json_object('field','contactResult','operator','EQ','value','SUSPECT_INVALID')
      ))
    ),
    json_object(
      'id','contactResult_UNREACHABLE',
      'label','当首联结果为未接通时',
      'resultField','contactResult',
      'resultValue','UNREACHABLE',
      'resultLabel','未接通',
      'resultType','NEXT',
      'targetVersionId',@td003_target_version_id,
      'targetTemplateCode','TD-003',
      'default',false,
      'condition',json_object('$expression',json_object(
        'version',1,
        'root',json_object('field','contactResult','operator','EQ','value','UNREACHABLE')
      ))
    )
  ),
  'nodes',json_array(
    json_object('key','current_task','type','TASK',
      'templateCode','TD-001','templateVersionId',@td001_repair_version_id),
    json_object('key','business_result','type','DECISION'),
    json_object('key','task_contactResult_VALID','type','TASK',
      'templateCode','TD-004','templateVersionId',@td004_target_version_id),
    json_object('key','task_contactResult_SUSPECT_INVALID','type','TASK',
      'templateCode','TD-002','templateVersionId',@td002_target_version_id),
    json_object('key','task_contactResult_UNREACHABLE','type','TASK',
      'templateCode','TD-003','templateVersionId',@td003_target_version_id),
    json_object('key','route_end','type','END')
  ),
  'edges',json_array(
    json_object('key','edge_current_result','from','current_task','to','business_result','priority',0),
    json_object(
      'key','edge_result_valid','from','business_result','to','task_contactResult_VALID','priority',30,
      'condition',json_object('$expression',json_object(
        'version',1,
        'root',json_object('field','contactResult','operator','EQ','value','VALID')
      ))
    ),
    json_object(
      'key','edge_result_suspect_invalid','from','business_result',
      'to','task_contactResult_SUSPECT_INVALID','priority',20,
      'condition',json_object('$expression',json_object(
        'version',1,
        'root',json_object('field','contactResult','operator','EQ','value','SUSPECT_INVALID')
      ))
    ),
    json_object(
      'key','edge_result_unreachable','from','business_result',
      'to','task_contactResult_UNREACHABLE','priority',10,
      'condition',json_object('$expression',json_object(
        'version',1,
        'root',json_object('field','contactResult','operator','EQ','value','UNREACHABLE')
      ))
    ),
    json_object('key','edge_result_default','from','business_result','to','route_end',
      'priority',-1,'default',true),
    json_object('key','edge_valid_end','from','task_contactResult_VALID','to','route_end','priority',0),
    json_object('key','edge_suspect_end','from','task_contactResult_SUSPECT_INVALID','to','route_end','priority',0),
    json_object('key','edge_unreachable_end','from','task_contactResult_UNREACHABLE','to','route_end','priority',0)
  )
);

set @td001_repair_definition=(
  select json_set(
    source.definition_json,
    '$.event.condition',json_object(),
    '$.owner.config',json_object(
      'type','PAYLOAD',
      'field','ownerId',
      'skipUnavailable',false,
      'useDelegation',false,
      'requireAvailable',true
    ),
    '$.routing.config',@td001_repair_routing
  )
  from todo_template_version source
  where source.version_id=@td001_source_version_id
);

start transaction;

insert into todo_template_version(
  version_id,template_id,version_no,status,source_version_id,
  owner_rule_json,dod_rule_json,sla_rule_json,next_rule_json,ui_schema_json,
  change_summary,impact_scope,rollback_source_version_id,
  definition_schema_version,definition_json,compiled_json,definition_hash,
  validation_report_json,published_by,published_time,update_by,update_time
)
select
  @td001_repair_version_id,@td001_template_id,@td001_repair_version_no,'DRAFT',
  @td001_source_version_id,
  json_extract(@td001_repair_definition,'$.owner.config'),
  json_extract(@td001_repair_definition,'$.dod.config'),
  json_extract(@td001_repair_definition,'$.sla.config'),
  json_extract(@td001_repair_definition,'$.routing.config'),
  json_extract(@td001_repair_definition,'$.ui.config'),
  @td001_repair_marker,
  '首联事件字段、负责人来源、三种业务结果路由与模拟发布',
  @td001_source_version_id,
  1,@td001_repair_definition,@td001_repair_definition,
  lower(sha2(cast(@td001_repair_definition as char),256)),
  json_object('errors',json_array(),'warnings',json_array()),
  null,null,'migration',sysdate()
where @td001_repair_exists=0;

-- The event schema explains every technical value in business language and
-- explicitly allows only ownerId as an event-based assignee source.
update todo_event_catalog
set payload_schema_json=json_set(
      payload_schema_json,
      '$.properties.schemaVersion.title','载荷版本',
      '$.properties.schemaVersion.description','事件结构版本，由系统自动维护',
      '$.properties.assignmentId.title','分配记录ID',
      '$.properties.assignmentId.description','系统生成的线索分配记录标识，仅用于追踪',
      '$.properties.ownerId.title','线索负责人',
      '$.properties.ownerId.description','待办将分配给该用户',
      '$.properties.ownerId."x-semantic-type"','USER_ID',
      '$.properties.ownerId."x-option-source"','SYSTEM_USER',
      '$.properties.ownerDeptId.title','负责人所属部门',
      '$.properties.ownerDeptId.description','线索负责人当前所属部门',
      '$.properties.ownerDeptId."x-semantic-type"','DEPT_ID',
      '$.properties.ownerDeptId."x-option-source"','SYSTEM_DEPARTMENT',
      '$.properties.operatorId.title','分配操作人',
      '$.properties.operatorId.description','执行本次线索分配的用户',
      '$.properties.operatorId."x-semantic-type"','USER_ID',
      '$.properties.operatorId."x-option-source"','SYSTEM_USER'
    ),
    owner_field_paths_json=json_array('ownerId'),
    condition_field_paths_json=json_array('ownerDeptId'),
    version=version+1,
    update_by='migration',
    update_time=sysdate()
where event_type='LEAD_ASSIGNED' and payload_version=1;

insert into todo_configuration_resource_item(
  resource_type,resource_code,resource_name,description,business_type,
  value_json,status,sort_order,create_by
)
select 'FIELD','invalidReasonCode','无效原因','销售判断线索疑似无效时选择的标准原因','LEAD',
  json_object(
    'type','string',
    'semanticType','DICT',
    'dictType','law_lead_invalid_reason',
    'optionSource','SYSTEM_DICTIONARY'
  ),'ACTIVE',70,'migration'
where not exists(
  select 1 from todo_configuration_resource_item
  where resource_type='FIELD' and resource_code='invalidReasonCode' and business_type='LEAD'
);

insert into todo_configuration_resource_item(
  resource_type,resource_code,resource_name,description,business_type,
  value_json,status,sort_order,create_by
)
select 'FIELD','salesExplanation','销售说明','销售标记疑似无效线索时填写的判断依据','LEAD',
  json_object('type','string'),'ACTIVE',80,'migration'
where not exists(
  select 1 from todo_configuration_resource_item
  where resource_type='FIELD' and resource_code='salesExplanation' and business_type='LEAD'
);

commit;
