const assert = require('node:assert')
const fs = require('node:fs')
const path = require('node:path')
const { execFileSync } = require('node:child_process')
const { executeSql } = require('../tests/e2e/support/mysql-e2e-runner')
const { cleanupTodoConfiguration } = require('../tests/e2e/support/todo-config-e2e-database')

const container = process.env.TODO_E2E_MYSQL_CONTAINER
const sourceDb = process.env.TODO_E2E_SOURCE_DB
const database = process.env.TODO_E2E_DB_NAME
if (!container || !sourceDb || !database) throw new Error('TODO_E2E_MYSQL_CONTAINER, TODO_E2E_SOURCE_DB and TODO_E2E_DB_NAME are required')
if (!/^[A-Za-z0-9_]+_e2e$/.test(database)) throw new Error(`Unsafe disposable database: ${database}`)

const password = process.env.TODO_E2E_DB_PASSWORD
const marker = process.env.TODO_CONFIG_E2E_RUN_MARKER || 'task14_sql_contract'
const slaCode = `E2E_SLA_${marker}`
const dodCode = `E2E_DOD_${marker}`
const leadNo = `E2E_LEAD_${marker}`
const templateCode = `E2E_TODO_CONFIG_${marker}_DB_SAFETY`
Object.assign(process.env, { TODO_E2E_REAL_BACKEND: 'true', TODO_CONFIG_E2E_RUN_MARKER: marker, TODO_CONFIG_E2E_SLA_CODE: slaCode, TODO_CONFIG_E2E_DOD_CODE: dodCode, TODO_CONFIG_E2E_LEAD_NO: leadNo })

function cloneSource() {
  executeSql(`drop database if exists \`${database}\`; create database \`${database}\` character set utf8mb4 collate utf8mb4_unicode_ci;`, 'mysql')
  const dump = execFileSync('docker', ['exec', '-e', `MYSQL_PWD=${password}`, container, 'mysqldump', '--single-transaction', '--no-tablespaces', '--skip-triggers', '-uroot', sourceDb], { encoding: 'utf8', windowsHide: true, maxBuffer: 64 * 1024 * 1024 })
  executeSql(dump, database)
}

function renderBootstrap(expectedDatabase) {
  const adminHash = executeSql("select password from sys_user where user_name='admin' and del_flag='0' limit 1;", database).trim()
  assert(adminHash, 'source schema must contain an admin password hash')
  return fs.readFileSync(path.join(__dirname, '../tests/e2e/bootstrap/todo-config-admin.sql'), 'utf8')
    .replaceAll('TODO_CONFIG_E2E_PASSWORD_HASH', adminHash)
    .replaceAll('TODO_CONFIG_E2E_DATABASE', expectedDatabase)
    .replaceAll('TODO_CONFIG_E2E_RUN_MARKER', marker)
    .replaceAll('TODO_CONFIG_E2E_SLA_CODE', slaCode)
    .replaceAll('TODO_CONFIG_E2E_DOD_CODE', dodCode)
    .replaceAll('TODO_CONFIG_E2E_LEAD_NO', leadNo)
}

function requireSqlFailure(label, sql, expectedText) {
  let error
  try { executeSql(sql, database) } catch (failure) { error = failure }
  assert(error, `${label} must fail closed`)
  assert.match(String(error.stderr || error.message), new RegExp(expectedText, 'i'), `${label} must report its guard`)
  executeSql('drop procedure if exists todo_config_e2e_bootstrap_guard;', database)
}

function requireCleanupFailure(label, code, expectedText) {
  let error
  try { cleanupTodoConfiguration(code) } catch (failure) { error = failure }
  assert(error, `${label} must fail closed`)
  assert.match(String(error.stderr || error.message), new RegExp(expectedText, 'i'), `${label} must report its guard`)
}

try {
  cloneSource()
  executeSql("delete ur from sys_user_role ur join sys_user u on u.user_id=ur.user_id where u.user_name='todo_config_admin'; delete rm from sys_role_menu rm join sys_role r on r.role_id=rm.role_id where r.role_key='todo_config_admin'; delete from sys_user where user_name='todo_config_admin'; delete from sys_role where role_key='todo_config_admin';", database)
  // The production schema rejects these NULLs at the column boundary. The disposable clone relaxes only
  // the ownership columns so the bootstrap guard itself is proven fail-closed against legacy schemas.
  executeSql('alter table todo_sla_rule modify create_by varchar(64) null; alter table todo_dod_rule modify create_by varchar(64) null;', database)

  requireSqlFailure('wrong database name', renderBootstrap('another_disposable_e2e'), 'Unsafe Todo configuration E2E bootstrap database')

  executeSql("insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time,remark) values('Collision','todo_config_admin',90,'5',1,1,'0','0','production',sysdate(),'NOT_A_TEST_ROLE');", database)
  requireSqlFailure('non-test role collision', renderBootstrap(database), 'collides with a non-test role')
  executeSql("delete from sys_role where role_key='todo_config_admin' and remark='NOT_A_TEST_ROLE';", database)

  executeSql("insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time,remark) values('Null collision','todo_config_admin',90,'5',1,1,'0','0',null,sysdate(),null);", database)
  requireSqlFailure('NULL role collision', renderBootstrap(database), 'collides with a non-test role')
  executeSql("delete from sys_role where role_key='todo_config_admin';", database)

  executeSql("insert into sys_user(dept_id,user_name,nick_name,user_type,password,status,del_flag,create_by,create_time,remark) values(103,'todo_config_admin','Null collision','99','x','0','0',null,sysdate(),null);", database)
  requireSqlFailure('NULL user collision', renderBootstrap(database), 'collides with a non-test user')
  executeSql("delete from sys_user where user_name='todo_config_admin';", database)

  executeSql(`insert into todo_sla_rule(rule_code,rule_name,sla_type,duration_value,duration_unit,calendar_code,start_strategy,soft_remind_percent,hard_remind_percent,escalate_percent,status,version,create_by,create_time) values('${slaCode}','Null collision','RESPONSE',30,'MINUTE','DEFAULT','TODO_CREATED',80,100,150,'0',0,null,sysdate());`, database)
  requireSqlFailure('NULL SLA collision', renderBootstrap(database), 'SLA code collides')
  executeSql(`delete from todo_sla_rule where rule_code='${slaCode}';`, database)

  executeSql(`insert into todo_dod_rule(rule_code,rule_name,rule_type,required_fields_json,required_attachments_json,conditional_rules_json,validator_refs_json,error_messages_json,status,version,create_by,create_time) values('${dodCode}','Null collision','TASK',json_array(),json_array(),json_array(),json_array(),json_object(),'0',0,null,sysdate());`, database)
  requireSqlFailure('NULL DoD collision', renderBootstrap(database), 'DoD code collides')
  executeSql(`delete from todo_dod_rule where rule_code='${dodCode}';`, database)

  executeSql(`insert into biz_lead(lead_no,lead_name,contact_name,status,pool_status,priority,dept_id,del_flag,create_by,create_time,update_time,remark) values('${leadNo}','Null collision','Null','1','0','2',103,'0',null,sysdate(),sysdate(),null);`, database)
  requireSqlFailure('NULL lead collision', renderBootstrap(database), 'lead number collides')
  executeSql(`delete from biz_lead where lead_no='${leadNo}';`, database)

  const captchaBefore = executeSql("select config_value from sys_config where config_key='sys.account.captchaEnabled' limit 1;", database).trim()
  executeSql(renderBootstrap(database), database)
  const bootstrapCounts = executeSql(`select (select count(*) from sys_user where user_name='todo_config_admin' and create_by='${marker}')+(select count(*) from sys_role where role_key='todo_config_admin' and create_by='${marker}')+(select count(*) from todo_sla_rule where rule_code='${slaCode}' and create_by='${marker}')+(select count(*) from todo_dod_rule where rule_code='${dodCode}' and create_by='${marker}')+(select count(*) from biz_lead where lead_no='${leadNo}' and create_by='${marker}');`, database).trim()
  assert.strictEqual(bootstrapCounts, '5', 'normal bootstrap must create all five marked resources')

  const nonTestCode = `PRODUCTION_TEMPLATE_${marker}`
  executeSql(`insert into todo_template(template_code,template_name,business_type,current_version,status,create_by,create_time) values('${nonTestCode}','Production template','LEAD',0,'0','admin',sysdate());`, database)
  requireCleanupFailure('non-E2E template code', nonTestCode, 'E2E template code prefix')
  assert.strictEqual(executeSql(`select count(*) from todo_template where template_code='${nonTestCode}';`, database).trim(), '1', 'non-E2E template must remain unchanged')
  executeSql(`delete from todo_template where template_code='${nonTestCode}';`, database)

  const foreignCode = `E2E_TODO_CONFIG_${marker}_FOREIGN`
  executeSql(`insert into todo_template(template_code,template_name,business_type,current_version,status,create_by,create_time) values('${foreignCode}','Foreign template','LEAD',0,'0','admin',sysdate());`, database)
  requireCleanupFailure('foreign-owned E2E template', foreignCode, 'template ownership mismatch')
  assert.strictEqual(executeSql(`select count(*) from todo_template where template_code='${foreignCode}' and create_by='admin';`, database).trim(), '1', 'foreign template must remain unchanged')
  executeSql(`delete from todo_template where template_code='${foreignCode}';`, database)

  executeSql(`insert into todo_template(template_code,template_name,business_type,current_version,status,create_by,create_time) values('${templateCode}','Task14 cleanup contract','LEAD',0,'0','todo_config_admin',sysdate()); set @template_id=last_insert_id(); insert into todo_template_version(template_id,version_no,status,create_time) values(@template_id,1,'DRAFT',sysdate()); set @version_id=last_insert_id(); insert into todo_instance(todo_no,template_id,template_version_id,template_code,title,business_type,business_id,status,priority,sla_status,route_definition_version_id,created_at,version) values('TODO-${marker}',@template_id,@version_id,'${templateCode}','Runtime reference','LEAD',1,'CREATED','NORMAL','NORMAL',@version_id,sysdate(),0); set @todo_id=last_insert_id(); insert into todo_route_token(root_todo_id,node_key,branch_key,occurrence,definition_hash,route_token,status) values(@todo_id,'node_1','main',1,repeat('a',64),json_object(),'ARRIVED'); insert into todo_route_join(root_todo_id,node_key,occurrence,join_mode,required_branches,status) values(@todo_id,'join_1',1,'ALL',json_array('main'),'WAITING'); insert into todo_sla_record(todo_id,start_at,due_at,status) values(@todo_id,sysdate(),date_add(sysdate(),interval 30 minute),'RUNNING');`, database)
  requireCleanupFailure('route token runtime reference', templateCode, 'todo_route_token')
  executeSql("delete from todo_route_token where node_key='node_1';", database)
  requireCleanupFailure('route join runtime reference', templateCode, 'todo_route_join')
  executeSql("delete from todo_route_join where node_key='join_1';", database)
  requireCleanupFailure('SLA runtime reference', templateCode, 'todo_sla_record')
  executeSql(`delete s from todo_sla_record s join todo_instance t on t.todo_id=s.todo_id where t.template_code='${templateCode}';`, database)
  requireCleanupFailure('todo instance runtime reference', templateCode, 'todo_instance')
  executeSql(`delete from todo_instance where template_code='${templateCode}';`, database)
  cleanupTodoConfiguration(templateCode)
  const residue = executeSql(`select (select count(*) from sys_user where user_name='todo_config_admin' and create_by='${marker}')+(select count(*) from sys_role where role_key='todo_config_admin' and create_by='${marker}')+(select count(*) from todo_sla_rule where rule_code='${slaCode}')+(select count(*) from todo_dod_rule where rule_code='${dodCode}')+(select count(*) from biz_lead where lead_no='${leadNo}')+(select count(*) from todo_template where template_code='${templateCode}');`, database).trim()
  assert.strictEqual(residue, '0', 'cleanup must leave zero marked resources')
  assert.strictEqual(executeSql("select config_value from sys_config where config_key='sys.account.captchaEnabled' limit 1;", database).trim(), captchaBefore, 'captcha configuration must be restored')
  console.log('Todo configuration bootstrap/cleanup container SQL contract passed')
} finally {
  executeSql(`drop database if exists \`${database}\`;`, 'mysql')
}
