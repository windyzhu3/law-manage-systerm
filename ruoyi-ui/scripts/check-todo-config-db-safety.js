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
const templateCode = `E2E_TODO_CONFIG_${marker}`
Object.assign(process.env, { TODO_E2E_REAL_BACKEND: 'true', TODO_CONFIG_E2E_RUN_MARKER: marker, TODO_CONFIG_E2E_SLA_CODE: slaCode, TODO_CONFIG_E2E_DOD_CODE: dodCode, TODO_CONFIG_E2E_LEAD_NO: leadNo })

function cloneSource() {
  executeSql(`drop database if exists \`${database}\`; create database \`${database}\` character set utf8mb4 collate utf8mb4_unicode_ci;`, 'mysql')
  const dump = execFileSync('docker', ['exec', '-e', `MYSQL_PWD=${password}`, container, 'mysqldump', '--single-transaction', '--no-tablespaces', '--skip-triggers', '-uroot', sourceDb], { encoding: 'utf8', windowsHide: true })
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

try {
  cloneSource()
  executeSql("delete ur from sys_user_role ur join sys_user u on u.user_id=ur.user_id where u.user_name='todo_config_admin'; delete rm from sys_role_menu rm join sys_role r on r.role_id=rm.role_id where r.role_key='todo_config_admin'; delete from sys_user where user_name='todo_config_admin'; delete from sys_role where role_key='todo_config_admin';", database)

  requireSqlFailure('wrong database name', renderBootstrap('another_disposable_e2e'), 'Unsafe Todo configuration E2E bootstrap database')

  executeSql("insert into sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time,remark) values('Collision','todo_config_admin',90,'5',1,1,'0','0','production',sysdate(),'NOT_A_TEST_ROLE');", database)
  requireSqlFailure('non-test role collision', renderBootstrap(database), 'collides with a non-test role')
  executeSql("delete from sys_role where role_key='todo_config_admin' and remark='NOT_A_TEST_ROLE';", database)

  executeSql(renderBootstrap(database), database)
  const bootstrapCounts = executeSql(`select (select count(*) from sys_user where user_name='todo_config_admin' and create_by='${marker}')+(select count(*) from sys_role where role_key='todo_config_admin' and create_by='${marker}')+(select count(*) from todo_sla_rule where rule_code='${slaCode}' and create_by='${marker}')+(select count(*) from todo_dod_rule where rule_code='${dodCode}' and create_by='${marker}')+(select count(*) from biz_lead where lead_no='${leadNo}' and create_by='${marker}');`, database).trim()
  assert.strictEqual(bootstrapCounts, '5', 'normal bootstrap must create all five marked resources')

  executeSql(`insert into todo_template(template_code,template_name,business_type,current_version,status,create_by,create_time) values('${templateCode}','Task14 cleanup contract','LEAD',0,'0','todo_config_admin',sysdate()); set @template_id=last_insert_id(); insert into todo_template_version(template_id,version_no,status,create_time) values(@template_id,1,'DRAFT',sysdate());`, database)
  cleanupTodoConfiguration(templateCode)
  const residue = executeSql(`select (select count(*) from sys_user where user_name='todo_config_admin' and create_by='${marker}')+(select count(*) from sys_role where role_key='todo_config_admin' and create_by='${marker}')+(select count(*) from todo_sla_rule where rule_code='${slaCode}')+(select count(*) from todo_dod_rule where rule_code='${dodCode}')+(select count(*) from biz_lead where lead_no='${leadNo}')+(select count(*) from todo_template where template_code='${templateCode}');`, database).trim()
  assert.strictEqual(residue, '0', 'cleanup must leave zero marked resources')
  console.log('Todo configuration bootstrap/cleanup container SQL contract passed')
} finally {
  executeSql(`drop database if exists \`${database}\`;`, 'mysql')
}
