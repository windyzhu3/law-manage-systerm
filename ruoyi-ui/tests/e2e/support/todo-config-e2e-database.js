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
    set @sla_code='${sqlLiteral(slaCode)}'; set @dod_code='${sqlLiteral(dodCode)}'; set @lead_no='${sqlLiteral(leadNo)}'; set @template_code='${sqlLiteral(code)}';
    set @template_prefix=concat('E2E_TODO_CONFIG_',@run_marker,'_');
    set @captcha_restore_key=concat('todo.e2e.captcha.restore.',@run_marker);
    set @operator_id=(select user_id from sys_user where user_name='todo_config_admin' and coalesce(create_by,'')=coalesce(@run_marker,'') and coalesce(remark,'')=coalesce(@test_remark,'') limit 1);
    set @role_id=(select role_id from sys_role where role_key='todo_config_admin' and coalesce(create_by,'')=coalesce(@run_marker,'') and coalesce(remark,'')=coalesce(@test_remark,'') limit 1);
    set @template_id=(select template_id from todo_template where template_code=@template_code limit 1);
    delimiter //
    drop procedure if exists todo_config_e2e_ownership_guard//
    create procedure todo_config_e2e_ownership_guard()
    begin
      if left(@template_code,char_length(@template_prefix))<>@template_prefix then signal sqlstate '45000' set message_text='Todo E2E template code prefix mismatch'; end if;
      if @operator_id is null or @role_id is null then signal sqlstate '45000' set message_text='Todo E2E test identity ownership mismatch'; end if;
      if @template_id is not null and not exists(select 1 from todo_template where template_id=@template_id and template_code=@template_code and create_by='todo_config_admin') then signal sqlstate '45000' set message_text='Todo E2E template ownership mismatch'; end if;
    end//
    delimiter ;
    call todo_config_e2e_ownership_guard();
    set @version_ids=coalesce((select group_concat(version_id order by version_id separator ',') from todo_template_version where template_id=@template_id),'');
    set @todo_ids=coalesce((select group_concat(todo_id order by todo_id separator ',') from todo_instance where template_id=@template_id or find_in_set(template_version_id,@version_ids)>0 or find_in_set(route_definition_version_id,@version_ids)>0),'');
    set @policy_ids=coalesce((select group_concat(policy_version_id order by policy_version_id separator ',') from todo_sla_policy_version where find_in_set(template_version_id,@version_ids)>0),'');

    -- Runtime rows are evidence that this definition is in use. Cleanup must never cascade through them.
    call assertCleanupCount('todo_route_token',(select count(*) from todo_route_token where find_in_set(root_todo_id,@todo_ids)>0));
    call assertCleanupCount('todo_route_join',(select count(*) from todo_route_join where find_in_set(root_todo_id,@todo_ids)>0));
    call assertCleanupCount('todo_sla_record',(select count(*) from todo_sla_record where find_in_set(todo_id,@todo_ids)>0));
    call assertCleanupCount('todo_cycle_occurrence',(select count(*) from todo_cycle_occurrence where find_in_set(todo_id,@todo_ids)>0 or find_in_set(policy_version_id,@policy_ids)>0));
    call assertCleanupCount('todo_sla_policy_version',(select count(*) from todo_sla_policy_version where find_in_set(template_version_id,@version_ids)>0));
    call assertCleanupCount('todo_instance',(select count(*) from todo_instance where find_in_set(todo_id,@todo_ids)>0));

    delete from todo_simulation_record where find_in_set(template_version_id,@version_ids)>0 and operator_id=@operator_id;
    delete from todo_template_draft_rule_ref where find_in_set(version_id,@version_ids)>0;
    delete from todo_trigger_rule where template_id=@template_id or find_in_set(template_version_id,@version_ids)>0;
    delete from todo_definition_action where operator_id=@operator_id and ((entity_id=@template_id and entity_type='TEMPLATE') or find_in_set(entity_id,@version_ids)>0 or find_in_set(source_entity_id,@version_ids)>0);
    delete from todo_template_version where find_in_set(version_id,@version_ids)>0;
    delete from todo_template where template_id=@template_id and template_code=@template_code and create_by='todo_config_admin';
    delete from todo_sla_rule where rule_code=@sla_code and create_by=@run_marker;
    delete from todo_dod_rule where rule_code=@dod_code and create_by=@run_marker;
    delete from biz_lead where lead_no=@lead_no and create_by=@run_marker and remark=@test_remark;
    set @captcha_original=(select config_value from sys_config where config_key=@captcha_restore_key and create_by=@run_marker and remark=@test_remark limit 1);
    update sys_config set config_value=@captcha_original where config_key='sys.account.captchaEnabled' and @captcha_original is not null;
    delete from sys_config where config_key=@captcha_restore_key and create_by=@run_marker and remark=@test_remark;
    delete from sys_user_role where user_id=@operator_id and role_id=@role_id;
    delete from sys_role_menu where role_id=@role_id;
    delete from sys_user where user_id=@operator_id and create_by=@run_marker and remark=@test_remark;
    delete from sys_role where role_id=@role_id and create_by=@run_marker and remark=@test_remark;
    call assertCleanupCount('todo_simulation_record',(select count(*) from todo_simulation_record where find_in_set(template_version_id,@version_ids)>0));
    call assertCleanupCount('todo_template_draft_rule_ref',(select count(*) from todo_template_draft_rule_ref where find_in_set(version_id,@version_ids)>0));
    call assertCleanupCount('todo_trigger_rule',(select count(*) from todo_trigger_rule where template_id=@template_id or find_in_set(template_version_id,@version_ids)>0));
    call assertCleanupCount('todo_definition_action',(select count(*) from todo_definition_action where operator_id=@operator_id and ((entity_id=@template_id and entity_type='TEMPLATE') or find_in_set(entity_id,@version_ids)>0 or find_in_set(source_entity_id,@version_ids)>0)));
    call assertCleanupCount('todo_template_version',(select count(*) from todo_template_version where find_in_set(version_id,@version_ids)>0));
    call assertCleanupCount('todo_template',(select count(*) from todo_template where template_id=@template_id or template_code=@template_code));
    call assertCleanupCount('todo_sla_rule',(select count(*) from todo_sla_rule where rule_code=@sla_code and create_by=@run_marker));
    call assertCleanupCount('todo_dod_rule',(select count(*) from todo_dod_rule where rule_code=@dod_code and create_by=@run_marker));
    call assertCleanupCount('biz_lead',(select count(*) from biz_lead where lead_no=@lead_no and create_by=@run_marker));
    call assertCleanupCount('captcha_restore',(select count(*) from sys_config where config_key=@captcha_restore_key));
    call assertCleanupCount('sys_user',(select count(*) from sys_user where user_id=@operator_id and create_by=@run_marker));
    call assertCleanupCount('sys_role',(select count(*) from sys_role where role_id=@role_id and create_by=@run_marker));
    call todo_config_e2e_guard();
    drop procedure todo_config_e2e_ownership_guard; drop procedure assertCleanupCount; drop procedure todo_config_e2e_guard;
  `
  executeSql(sql, database)
  assertSafeE2eDatabase(database)
}

module.exports = { assertSafeE2eDatabase, cleanupTodoConfiguration, requireEnv }
