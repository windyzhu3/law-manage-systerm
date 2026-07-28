alter table todo_configuration_resource_item drop check chk_todo_config_resource_type;
alter table todo_configuration_resource_item
  add constraint chk_todo_config_resource_type
  check (resource_type in ('FIELD','MATERIAL','DOD_RECIPE','SIMULATION_SCENARIO'));

create table todo_simulation_evidence (
  evidence_id bigint not null auto_increment,
  template_id bigint not null,
  version_id bigint not null,
  definition_hash varchar(64) not null,
  scenario_code varchar(64) not null,
  scenario_version int not null,
  result_status varchar(16) not null,
  input_hash varchar(64) not null,
  trace_summary_json json not null,
  executed_by bigint not null,
  executed_time datetime not null,
  expire_time datetime null,
  primary key (evidence_id),
  unique key uk_todo_simulation_evidence
    (version_id,definition_hash,scenario_code,scenario_version,input_hash),
  key idx_todo_simulation_evidence_gate
    (version_id,definition_hash,scenario_code,scenario_version,result_status,expire_time)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

insert into todo_configuration_resource_item(
  resource_type,resource_code,resource_name,description,business_type,value_json,status,sort_order,create_by)
values
('SIMULATION_SCENARIO','TD001_VALID','有效首联','验证有效首联后进入客户中心审核','LEAD',
 json_object('templateCode','TD-001','scenarioVersion',1,
   'completionPayload',json_object('contactResult','VALID','contactedAt',concat('$','{SIMULATION_NOW}')),
   'editableFields',json_array('contactResult','contactedAt'),'requiredMaterials',json_array(),
   'completionNodeKey','TD-001','occurrence',1,'expectedNextTemplateCode','TD-004',
   'requiredForPublish',true),'ACTIVE',10,'migration'),
('SIMULATION_SCENARIO','TD001_SUSPECT_INVALID','疑似无效','验证疑似无效后进入无效线索复核','LEAD',
 json_object('templateCode','TD-001','scenarioVersion',1,
   'completionPayload',json_object('contactResult','SUSPECT_INVALID','contactedAt',concat('$','{SIMULATION_NOW}')),
   'editableFields',json_array('contactResult','contactedAt'),'requiredMaterials',json_array(),
   'completionNodeKey','TD-001','occurrence',1,'expectedNextTemplateCode','TD-002',
   'requiredForPublish',true),'ACTIVE',20,'migration'),
('SIMULATION_SCENARIO','TD001_UNREACHABLE','未接通','验证未接通后进入重试计划','LEAD',
 json_object('templateCode','TD-001','scenarioVersion',1,
   'completionPayload',json_object('contactResult','UNREACHABLE','contactedAt',concat('$','{SIMULATION_NOW}')),
   'editableFields',json_array('contactResult','contactedAt'),'requiredMaterials',json_array(),
   'completionNodeKey','TD-001','occurrence',1,'expectedNextTemplateCode','TD-003',
   'requiredForPublish',true),'ACTIVE',30,'migration');
