const { executeSql } = require('./mysql-e2e-runner')

function requireEnv(name) {
  const value = process.env[name]
  if (!value) throw new Error(`${name} is required`)
  return value
}

function sqlLiteral(value) { return String(value).replace(/'/g, "''") }

function assertSafeE2eDatabase(database) {
  const expected = requireEnv('TODO_E2E_DB_NAME')
  if (database !== expected || !/^[A-Za-z0-9_]+_e2e$/.test(database)) throw new Error(`Refusing Todo configuration cleanup for unsafe database: ${database}`)
}

const JOURNEY_FIXTURES = Object.freeze({
  REPAIR: 'JOURNEY_REPAIR',
  FAILED: 'JOURNEY_FAILED',
  WARNING: 'JOURNEY_WARNING'
})

function parseRow(output, columns) {
  const line = String(output || '').trim().split(/\r?\n/).filter(Boolean).pop()
  if (!line) throw new Error(`Todo E2E database query returned no row for ${columns.join(', ')}`)
  const values = line.split('\t')
  if (values.length !== columns.length) throw new Error(`Todo E2E database query returned ${values.length} columns; expected ${columns.length}`)
  return columns.reduce((result, column, index) => {
    result[column] = values[index] === 'NULL' ? null : values[index]
    return result
  }, {})
}

function loadJourneyFixture(scenario, expectedStatus = 'DRAFT') {
  const suffix = JOURNEY_FIXTURES[String(scenario || '').toUpperCase()]
  if (!suffix) throw new Error(`Unknown Todo journey E2E fixture: ${scenario}`)
  const database = requireEnv('TODO_E2E_DB_NAME')
  const runMarker = requireEnv('TODO_CONFIG_E2E_RUN_MARKER')
  assertSafeE2eDatabase(database)
  const templateCode = `E2E_TODO_CONFIG_${runMarker}_${suffix}`
  const row = parseRow(executeSql(`
    select t.template_id,v.version_id,t.template_code,v.status
    from todo_template t
    join todo_template_version v on v.template_id=t.template_id
    where t.template_code='${sqlLiteral(templateCode)}'
      and t.create_by='todo_config_admin'
    order by v.version_no desc,v.version_id desc limit 1;
  `, database), ['templateId', 'versionId', 'templateCode', 'status'])
  if (row.status !== expectedStatus) throw new Error(`Todo journey fixture ${templateCode} must be ${expectedStatus}, got ${row.status}`)
  return { ...row, templateId: Number(row.templateId), versionId: Number(row.versionId) }
}

function loadJourneyEventBinding(scenario) {
  const suffix = JOURNEY_FIXTURES[String(scenario || '').toUpperCase()]
  if (!suffix) throw new Error(`Unknown Todo journey E2E fixture: ${scenario}`)
  const database = requireEnv('TODO_E2E_DB_NAME')
  const runMarker = requireEnv('TODO_CONFIG_E2E_RUN_MARKER')
  assertSafeE2eDatabase(database)
  const templateCode = `E2E_TODO_CONFIG_${runMarker}_${suffix}`
  const row = parseRow(executeSql(`
    select json_unquote(json_extract(v.definition_json,'$.event.eventType')),
      cast(json_unquote(json_extract(v.definition_json,'$.event.payloadVersion')) as unsigned),
      coalesce(c.schema_status,'MISSING'),coalesce(c.status,'MISSING'),
      coalesce(json_length(json_extract(c.payload_schema_json,'$.properties')),0)
    from todo_template t
    join todo_template_version v on v.template_id=t.template_id
    left join todo_event_catalog c
      on c.event_type=json_unquote(json_extract(v.definition_json,'$.event.eventType'))
      and c.payload_version=cast(json_unquote(json_extract(v.definition_json,'$.event.payloadVersion')) as unsigned)
    where t.template_code='${sqlLiteral(templateCode)}' and t.create_by='todo_config_admin'
    order by v.version_no desc,v.version_id desc limit 1;
  `, database), ['eventType', 'payloadVersion', 'schemaStatus', 'resourceStatus', 'schemaFieldCount'])
  return {
    ...row,
    payloadVersion: Number(row.payloadVersion),
    schemaFieldCount: Number(row.schemaFieldCount)
  }
}

function loadRepairEventResource(expectedStatus) {
  const database = requireEnv('TODO_E2E_DB_NAME')
  const runMarker = requireEnv('TODO_CONFIG_E2E_RUN_MARKER')
  assertSafeE2eDatabase(database)
  const eventType = `E2E_SCHEMA_REPAIR_${runMarker}`.toUpperCase()
  const row = parseRow(executeSql(`
    select event_catalog_id,event_type,payload_version,schema_status,status,version,
      coalesce(json_length(json_extract(payload_schema_json,'$.properties')),0)
    from todo_event_catalog
    where event_type='${sqlLiteral(eventType)}'
      and create_by in ('${sqlLiteral(runMarker)}','todo_config_admin')
    order by payload_version desc,event_catalog_id desc limit 1;
  `, database), ['eventCatalogId', 'eventType', 'payloadVersion', 'schemaStatus', 'resourceStatus', 'version', 'schemaFieldCount'])
  if (expectedStatus && row.resourceStatus !== expectedStatus) {
    throw new Error(`Todo repair event ${eventType}@${row.payloadVersion} must be ${expectedStatus}, got ${row.resourceStatus}`)
  }
  return {
    ...row,
    eventCatalogId: Number(row.eventCatalogId),
    payloadVersion: Number(row.payloadVersion),
    version: Number(row.version),
    schemaFieldCount: Number(row.schemaFieldCount)
  }
}

function snapshotSimulationPersistence(templateId, businessType, businessId) {
  const database = requireEnv('TODO_E2E_DB_NAME')
  const leadNo = requireEnv('TODO_CONFIG_E2E_LEAD_NO')
  assertSafeE2eDatabase(database)
  if (!Number.isSafeInteger(Number(templateId)) || Number(templateId) <= 0) throw new Error('A positive Todo journey template ID is required')
  if (!/^[A-Z_]+$/.test(String(businessType || ''))) throw new Error(`Unsafe business type: ${businessType}`)
  if (!Number.isSafeInteger(Number(businessId)) || Number(businessId) >= 0) throw new Error('The no-write assertion requires a governed negative sample business ID')
  const businessTable = businessType === 'LEAD' ? 'biz_lead' : null
  const businessIdColumn = businessType === 'LEAD' ? 'lead_id' : null
  if (!businessTable) throw new Error(`No deterministic business snapshot mapping for ${businessType}`)
  const row = parseRow(executeSql(`
    select
      (select count(*) from todo_instance where template_id=${Number(templateId)}),
      (select count(*) from todo_relation where business_type='${sqlLiteral(businessType)}' and business_id=${Number(businessId)}),
      (select count(*) from ${businessTable} where ${businessIdColumn}=${Number(businessId)}),
      coalesce((select concat_ws('|',lead_id,lead_no,status,pool_status,owner_id,dept_id,
        date_format(coalesce(update_time,create_time),'%Y-%m-%dT%H:%i:%s.%f'))
        from biz_lead where lead_no='${sqlLiteral(leadNo)}' limit 1),'MISSING');
  `, database), ['todoInstances', 'todoRelations', 'sampleBusinessRows', 'businessFingerprint'])
  return {
    todoInstances: Number(row.todoInstances),
    todoRelations: Number(row.todoRelations),
    sampleBusinessRows: Number(row.sampleBusinessRows),
    businessFingerprint: row.businessFingerprint
  }
}

function assertSimulationPersistenceUnchanged(before, after) {
  for (const key of ['todoInstances', 'todoRelations', 'sampleBusinessRows', 'businessFingerprint']) {
    if (before[key] !== after[key]) {
      throw new Error(`Read-only Todo simulation changed ${key}: ${before[key]} -> ${after[key]}`)
    }
  }
}

function cleanupTodoConfiguration(code) {
  if (process.env.TODO_E2E_REAL_BACKEND !== 'true' || !code) return
  const database = requireEnv('TODO_E2E_DB_NAME')
  const runMarker = requireEnv('TODO_CONFIG_E2E_RUN_MARKER')
  const slaCode = requireEnv('TODO_CONFIG_E2E_SLA_CODE')
  const dodCode = requireEnv('TODO_CONFIG_E2E_DOD_CODE')
  const leadNo = requireEnv('TODO_CONFIG_E2E_LEAD_NO')
  const codePrefix = `E2E_TODO_CONFIG_${runMarker}_`
  if (!code.startsWith(codePrefix)) throw new Error(`Todo E2E template code prefix mismatch: expected ${codePrefix}`)
  assertSafeE2eDatabase(database)
  const sql = `
    delimiter //
    drop procedure if exists todo_config_e2e_guard//
    create procedure todo_config_e2e_guard()
    begin
      if database() <> '${sqlLiteral(database)}' or database() not regexp '_e2e$' then signal sqlstate '45000' set message_text='Unsafe Todo configuration E2E cleanup database'; end if;
    end//
    drop procedure if exists assertCleanupCount//
    create procedure assertCleanupCount(in resource_name varchar(64), in remaining bigint)
    begin
      declare cleanup_message varchar(128);
      if remaining <> 0 then set cleanup_message=concat('Todo E2E cleanup residue: ',resource_name,'=',remaining); signal sqlstate '45000' set message_text=cleanup_message; end if;
    end//
    delimiter ;
    call todo_config_e2e_guard();
    set @run_marker='${sqlLiteral(runMarker)}'; set @test_remark=concat('TEST_ONLY|TODO_CONFIG_E2E|',@run_marker);
    set @runtime_dept_code=concat('TODO_E2E_',@run_marker);
    set @runtime_policy_code=concat('TODO_E2E_POLICY_',left(sha2(@run_marker,256),32));
    set @sla_code='${sqlLiteral(slaCode)}'; set @dod_code='${sqlLiteral(dodCode)}'; set @lead_no='${sqlLiteral(leadNo)}'; set @template_code='${sqlLiteral(code)}';
    set @warning_decision_code=concat('E2E_ADVISORY_',@run_marker);
    set @template_prefix=concat('E2E_TODO_CONFIG_',@run_marker,'_');
    set @captcha_restore_key=concat('todo.e2e.captcha.restore.',@run_marker);
    set @operator_id=(select user_id from sys_user where user_name='todo_config_admin' and coalesce(create_by,'')=coalesce(@run_marker,'') and coalesce(remark,'')=coalesce(@test_remark,'') limit 1);
    set @role_id=(select role_id from sys_role where role_key='todo_config_admin' and coalesce(create_by,'')=coalesce(@run_marker,'') and coalesce(remark,'')=coalesce(@test_remark,'') limit 1);
    set @template_id=(select template_id from todo_template where template_code=@template_code limit 1);
    set @template_ids=coalesce((select group_concat(template_id order by template_id separator ',') from todo_template
      where left(template_code,char_length(@template_prefix))=@template_prefix),'');
    set @test_user_ids=coalesce((select group_concat(user_id order by user_id separator ',') from sys_user
      where user_name in ('todo_config_admin','todo_business_admin','todo_resource_admin','todo_publisher','todo_auditor')
        and create_by=@run_marker and remark=@test_remark),'');
    set @test_role_ids=coalesce((select group_concat(role_id order by role_id separator ',') from sys_role
      where role_key in ('todo_config_admin','todo_business_admin','todo_resource_admin','todo_publisher','todo_auditor')
        and create_by=@run_marker and remark=@test_remark),'');
    set @event_resource_ids=coalesce((select group_concat(event_catalog_id order by event_catalog_id separator ',')
      from todo_event_catalog where event_type=concat('E2E_SCHEMA_REPAIR_',@run_marker)),'');
    set @runtime_policy_id=(select policy_id from biz_lead_assignment_policy
      where policy_code=@runtime_policy_code and create_by=@run_marker limit 1);
    delimiter //
    drop procedure if exists todo_config_e2e_ownership_guard//
    create procedure todo_config_e2e_ownership_guard()
    begin
      if left(@template_code,char_length(@template_prefix))<>@template_prefix then signal sqlstate '45000' set message_text='Todo E2E template code prefix mismatch'; end if;
      if @operator_id is null or @role_id is null then signal sqlstate '45000' set message_text='Todo E2E test identity ownership mismatch'; end if;
      if @template_id is not null and not exists(select 1 from todo_template where template_id=@template_id and template_code=@template_code and create_by='todo_config_admin') then signal sqlstate '45000' set message_text='Todo E2E template ownership mismatch'; end if;
      if exists(select 1 from todo_template where find_in_set(template_id,@template_ids)>0 and create_by<>'todo_config_admin') then signal sqlstate '45000' set message_text='Todo E2E fixture template ownership mismatch'; end if;
    end//
    delimiter ;
    call todo_config_e2e_ownership_guard();
    set @version_ids=coalesce((select group_concat(version_id order by version_id separator ',') from todo_template_version where find_in_set(template_id,@template_ids)>0),'');
    set @todo_ids=coalesce((select group_concat(todo_id order by todo_id separator ',') from todo_instance where template_id=@template_id or find_in_set(template_version_id,@version_ids)>0 or find_in_set(route_definition_version_id,@version_ids)>0),'');
    set @policy_ids=coalesce((select group_concat(policy_version_id order by policy_version_id separator ',') from todo_sla_policy_version where find_in_set(template_version_id,@version_ids)>0),'');

    -- Runtime rows are evidence that this definition is in use. Cleanup must never cascade through them.
    call assertCleanupCount('todo_route_token',(select count(*) from todo_route_token where find_in_set(root_todo_id,@todo_ids)>0));
    call assertCleanupCount('todo_route_join',(select count(*) from todo_route_join where find_in_set(root_todo_id,@todo_ids)>0));
    call assertCleanupCount('todo_sla_record',(select count(*) from todo_sla_record where find_in_set(todo_id,@todo_ids)>0));
    call assertCleanupCount('todo_cycle_occurrence',(select count(*) from todo_cycle_occurrence where find_in_set(todo_id,@todo_ids)>0 or find_in_set(policy_version_id,@policy_ids)>0));
    call assertCleanupCount('todo_sla_policy_version',(select count(*) from todo_sla_policy_version where find_in_set(template_version_id,@version_ids)>0));
    call assertCleanupCount('todo_relation',(select count(*) from todo_relation where find_in_set(todo_id,@todo_ids)>0));
    call assertCleanupCount('todo_instance',(select count(*) from todo_instance where find_in_set(todo_id,@todo_ids)>0));

    delete from todo_simulation_record where find_in_set(template_version_id,@version_ids)>0;
    delete from todo_template_draft_rule_ref where find_in_set(version_id,@version_ids)>0;
    delete from todo_trigger_rule where find_in_set(template_id,@template_ids)>0 or find_in_set(template_version_id,@version_ids)>0;
    delete from todo_definition_action where (find_in_set(entity_id,@template_ids)>0 and entity_type='TEMPLATE')
      or find_in_set(entity_id,@version_ids)>0 or find_in_set(source_entity_id,@version_ids)>0
      or (entity_type='EVENT_RESOURCE' and (find_in_set(entity_id,@event_resource_ids)>0 or find_in_set(source_entity_id,@event_resource_ids)>0));
    delete from todo_template_version where find_in_set(version_id,@version_ids)>0;
    delete from todo_template where find_in_set(template_id,@template_ids)>0 and create_by='todo_config_admin';
    delete from todo_event_catalog where event_type=concat('E2E_SCHEMA_REPAIR_',@run_marker)
      and create_by in (@run_marker,'todo_config_admin');
    delete from todo_decision where decision_code=@warning_decision_code and create_by=@run_marker;
    delete from todo_sla_rule where rule_code=@sla_code and create_by=@run_marker;
    delete from todo_dod_rule where rule_code=@dod_code and create_by=@run_marker;
    delete from biz_lead where lead_no=@lead_no and create_by=@run_marker and remark=@test_remark;
    set @captcha_original=(select config_value from sys_config where config_key=@captcha_restore_key and create_by=@run_marker and remark=@test_remark limit 1);
    update sys_config set config_value=@captcha_original where config_key='sys.account.captchaEnabled' and @captcha_original is not null;
    delete from sys_config where config_key=@captcha_restore_key and create_by=@run_marker and remark=@test_remark;
    delete from sys_user_role where find_in_set(user_id,@test_user_ids)>0 or find_in_set(role_id,@test_role_ids)>0;
    delete from sys_role_menu where find_in_set(role_id,@test_role_ids)>0;
    delete from biz_lead_assignment_policy_candidate where policy_id=@runtime_policy_id;
    delete from biz_lead_assignment_policy where policy_id=@runtime_policy_id and create_by=@run_marker;
    delete from sys_user where find_in_set(user_id,@test_user_ids)>0 and create_by=@run_marker and remark=@test_remark;
    delete from sys_role where find_in_set(role_id,@test_role_ids)>0 and create_by=@run_marker and remark=@test_remark;
    delete from sys_dept where dept_code=@runtime_dept_code and create_by=@run_marker;
    call assertCleanupCount('todo_simulation_record',(select count(*) from todo_simulation_record where find_in_set(template_version_id,@version_ids)>0));
    call assertCleanupCount('todo_template_draft_rule_ref',(select count(*) from todo_template_draft_rule_ref where find_in_set(version_id,@version_ids)>0));
    call assertCleanupCount('todo_trigger_rule',(select count(*) from todo_trigger_rule where find_in_set(template_id,@template_ids)>0 or find_in_set(template_version_id,@version_ids)>0));
    call assertCleanupCount('todo_definition_action',(select count(*) from todo_definition_action where
      (find_in_set(entity_id,@template_ids)>0 and entity_type='TEMPLATE')
      or find_in_set(entity_id,@version_ids)>0 or find_in_set(source_entity_id,@version_ids)>0
      or (entity_type='EVENT_RESOURCE' and (find_in_set(entity_id,@event_resource_ids)>0 or find_in_set(source_entity_id,@event_resource_ids)>0))));
    call assertCleanupCount('todo_template_version',(select count(*) from todo_template_version where find_in_set(version_id,@version_ids)>0));
    call assertCleanupCount('todo_template',(select count(*) from todo_template where find_in_set(template_id,@template_ids)>0 or left(template_code,char_length(@template_prefix))=@template_prefix));
    call assertCleanupCount('todo_event_catalog',(select count(*) from todo_event_catalog where event_type=concat('E2E_SCHEMA_REPAIR_',@run_marker)));
    call assertCleanupCount('todo_decision',(select count(*) from todo_decision where decision_code=@warning_decision_code));
    call assertCleanupCount('todo_sla_rule',(select count(*) from todo_sla_rule where rule_code=@sla_code and create_by=@run_marker));
    call assertCleanupCount('todo_dod_rule',(select count(*) from todo_dod_rule where rule_code=@dod_code and create_by=@run_marker));
    call assertCleanupCount('biz_lead',(select count(*) from biz_lead where lead_no=@lead_no and create_by=@run_marker));
    call assertCleanupCount('captcha_restore',(select count(*) from sys_config where config_key=@captcha_restore_key));
    call assertCleanupCount('sys_user',(select count(*) from sys_user where find_in_set(user_id,@test_user_ids)>0 and create_by=@run_marker));
    call assertCleanupCount('sys_role',(select count(*) from sys_role where find_in_set(role_id,@test_role_ids)>0 and create_by=@run_marker));
    call assertCleanupCount('biz_lead_assignment_policy',(select count(*) from biz_lead_assignment_policy where policy_code=@runtime_policy_code and create_by=@run_marker));
    call assertCleanupCount('sys_dept',(select count(*) from sys_dept where dept_code=@runtime_dept_code and create_by=@run_marker));
    call todo_config_e2e_guard();
    drop procedure todo_config_e2e_ownership_guard; drop procedure assertCleanupCount; drop procedure todo_config_e2e_guard;
  `
  executeSql(sql, database)
  assertSafeE2eDatabase(database)
}

module.exports = {
  assertSafeE2eDatabase,
  assertSimulationPersistenceUnchanged,
  cleanupTodoConfiguration,
  loadJourneyEventBinding,
  loadJourneyFixture,
  loadRepairEventResource,
  requireEnv,
  snapshotSimulationPersistence
}
