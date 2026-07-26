const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const specPath = path.join(root, 'tests/e2e/todo-config-center.spec.js')
const journeySpecPath = path.join(root, 'tests/e2e/todo-config-journey.spec.js')
const bootstrapPath = path.join(root, 'tests/e2e/bootstrap/todo-config-admin.sql')
const playwrightConfigPath = path.join(root, 'playwright.config.js')
const productionServerPath = path.join(root, 'scripts/serve-e2e-production.js')
const productionServerContractPath = path.join(root, 'scripts/check-production-e2e-server.js')
const mysqlRunnerPath = path.join(root, 'tests/e2e/support/mysql-e2e-runner.js')
const databaseFixturePath = path.join(root, 'tests/e2e/support/todo-config-e2e-database.js')
const simulationTracePath = path.join(root, 'src/views/todo/config/journey/components/SimulationTrace.vue')
const externalReportGatePath = path.join(root, 'scripts/assert-external-db-reports.js')
const externalReportContractPath = path.join(root, 'scripts/check-external-db-reports-contract.js')
const workflowPath = path.resolve(root, '../.github/workflows/ci.yml')

function read(file) {
  if (!fs.existsSync(file)) throw new Error(`Missing required real-E2E file: ${path.relative(root, file)}`)
  return fs.readFileSync(file, 'utf8')
}

function requireText(source, value, label) {
  if (!source.includes(value)) throw new Error(`${label} must include ${value}`)
}

function forbidText(source, value, label) {
  if (source.includes(value)) throw new Error(`${label} must not include ${value}`)
}

const spec = read(specPath)
const journeySpec = read(journeySpecPath)
const mysqlRunner = read(mysqlRunnerPath)
const databaseFixture = read(databaseFixturePath)
const simulationTrace = read(simulationTracePath)
for (const required of [
  'TODO_E2E_MYSQL_CONTAINER', 'docker', 'mysql', 'No MySQL execution path is available',
  '--init-command=SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci'
]) requireText(mysqlRunner, required, 'Portable MySQL E2E runner')
for (const forbidden of ['page.route(', 'route.fulfill(', 'e2e-token']) {
  if (spec.includes(forbidden)) throw new Error(`Todo configuration real E2E must not mock core APIs: ${forbidden}`)
  if (journeySpec.includes(forbidden)) throw new Error(`Todo journey real E2E must not mock core APIs: ${forbidden}`)
}
forbidText(journeySpec, 'if (await', 'Todo journey deterministic E2E scenarios')
forbidText(journeySpec, 'TODO_CONFIG_E2E_JOURNEY_TEMPLATE_ID', 'Todo journey deterministic E2E fixtures')
forbidText(journeySpec, 'TODO_CONFIG_E2E_AUDITOR_USER', 'Todo journey deterministic E2E identities')
forbidText(journeySpec, 'SCENARIO_SCHEMA_REPAIR_RERUN', 'Todo journey exact-version repair scenario')
forbidText(journeySpec, 'SCENARIO_FAILED_SIMULATION_BLOCKS_PUBLISH', 'Todo journey repair-and-rerun scenario')
const journeySkips = journeySpec.match(/test\.skip\(/g) || []
if (journeySkips.length !== 1 || !journeySpec.includes("test.skip(!realBackend, 'requires the disposable real-backend E2E environment')")) {
  throw new Error('Todo journey E2E must have exactly one environment-only skip and no optional scenario skips')
}
for (const required of [
  "loginAs(page, 'todo_config_admin'",
  "pathname === '/prod-api/login'",
  'expectSuccessfulApiResponse(loginResponsePromise)',
  "not.toHaveURL(/\\/login(?:\\?|$)/, { timeout: 15000 })",
  "page.goto('/todo-engine/todo-template')",
  '新建配置',
  "page.goto(route)",
  "img[alt=\"404\"]",
  '当前操作没有权限|页面不存在',
  'publishedVersionSnapshot',
  "where status='PUBLISHED'",
  "'/todo-engine/todo-release-record'",
]) requireText(spec, required, 'Todo configuration real E2E spec')
requireText(spec, "require('./support/mysql-e2e-runner')", 'Todo configuration real E2E spec')
for (const required of [
  'loadJourneyFixture',
  'loadJourneyEventBinding',
  'loadRepairEventResource',
  'SCENARIO_SCHEMA_REPAIR_ACTIVATE_BIND_RERUN',
  'SCENARIO_FAILED_SIMULATION_REPAIR_RERUN',
  'SCENARIO_WARNING_REASON_REQUIRED',
  'SCENARIO_SAMPLE_NO_RUNTIME_WRITES',
  'SCENARIO_BUSINESS_ADMIN_BOUNDARY',
  'SCENARIO_RESOURCE_ADMIN_BOUNDARY',
  'SCENARIO_PUBLISHER_BOUNDARY',
  'SCENARIO_AUDITOR_BOUNDARY',
  'simulation-publish-step',
  'business-object-selector',
  'DEMO-L-001',
  "trace.locator('ol > li')",
  'run-journey-simulation',
  'publish-preflight-panel',
  'publish-current-draft',
  "status === 'PUBLISHED'",
  'todo_business_admin',
  'todo_resource_admin',
  'todo_publisher',
  'todo_auditor',
  'snapshotSimulationPersistence',
  'assertSimulationPersistenceUnchanged',
  '/prod-api/todo/config/resources/events/${resource.eventCatalogId}/status',
  "schemaStatus === 'READY'",
  "resourceStatus === 'ACTIVE'",
  'payloadVersion > originalBinding.payloadVersion',
  "page.locator('.event-option').filter({ hasText: `v${repairedResource.payloadVersion}` })",
  "page.locator('.condition-row .is-danger').click()",
  'resources\\/events\\/\\d+\\/versions$',
  "'.event-resource-meta'"
]) requireText(journeySpec, required, 'Todo journey real E2E spec')
requireText(databaseFixture, "require('./mysql-e2e-runner')", 'Todo configuration database fixture')
for (const required of [
  'assertCleanupCount', 'assertSafeE2eDatabase', 'todo_config_e2e_guard', 'todo_definition_action', 'call todo_config_e2e_guard();',
  'E2E_TODO_CONFIG_${runMarker}_', 'Todo E2E template ownership mismatch',
  'todo_instance', 'todo_route_token', 'todo_route_join', 'todo_sla_record', 'todo_sla_policy_version', 'todo_cycle_occurrence',
  'todo.e2e.captcha.restore.',
  'loadJourneyFixture',
  'loadJourneyEventBinding',
  'loadRepairEventResource',
  'snapshotSimulationPersistence',
  'assertSimulationPersistenceUnchanged',
  'todo_relation',
  'businessFingerprint'
]) requireText(databaseFixture, required, 'Todo configuration database fixture')
requireText(simulationTrace, "'NOT_MATCHED'", 'Journey simulation blocked-trace rendering')
forbidText(spec, "delete from todo_definition_action where operator_id=", 'Todo configuration real E2E cleanup')
forbidText(databaseFixture, "delete from todo_definition_action where operator_id=(select", 'Todo configuration database fixture')

const bootstrap = read(bootstrapPath)
forbidText(bootstrap, 'E2E_MISSING_CALENDAR_', 'Deterministic failed-simulation fixture')
requireText(bootstrap, "'$.event.condition',json_object('$expression'", 'Deterministic failed-simulation fixture')
for (const required of [
  'todo_config_admin',
  'todo_business_admin',
  'todo_resource_admin',
  'todo_publisher',
  'todo_auditor',
  'TODO_CONFIG_E2E_PASSWORD_HASH',
  'todo:template:list',
  'todo:trigger:list',
  'todo:sla-rule:list',
  'todo:dod-rule:list',
  'todo:simulation:simulate',
  'todo:release:list',
  'todo/config/journey/index',
  "signal sqlstate '45000'",
  'TODO_CONFIG_E2E_DATABASE',
  'TODO_CONFIG_E2E_RUN_MARKER',
  'TODO_CONFIG_E2E_SLA_CODE',
  'TODO_CONFIG_E2E_DOD_CODE',
  'TODO_CONFIG_E2E_LEAD_NO',
  'TEST_ONLY|TODO_CONFIG_E2E|',
  'pwd_update_date',
  '@repair_template_code',
  '@failed_template_code',
  '@warning_template_code',
  '_JOURNEY_REPAIR',
  '_JOURNEY_FAILED',
  '_JOURNEY_WARNING',
  'E2E_SCHEMA_REPAIR_',
  'todo:resource:edit',
  'todo:release:publish',
  'todo:definition:diff',
  'lead:mine:query'
]) requireText(bootstrap, required, 'Test-only identity bootstrap')
for (const required of [
  "coalesce(remark,'')", "coalesce(create_by,'')", "coalesce(@test_remark,'')", 'todo.e2e.captcha.restore.'
]) requireText(bootstrap, required, 'Test-only identity bootstrap')

const playwrightConfig = read(playwrightConfigPath)
requireText(playwrightConfig, 'tests/e2e/**/*.spec.js', 'Playwright configuration')
requireText(playwrightConfig, 'TODO_E2E_REAL_BACKEND', 'Playwright configuration')
requireText(playwrightConfig, 'serve-e2e-production.js', 'Playwright configuration')
requireText(playwrightConfig, 'workers: realBackend ? 1 : undefined', 'Playwright configuration')
forbidText(playwrightConfig, "npm run dev", 'Playwright real-backend configuration')

const productionServer = read(productionServerPath)
for (const required of ['/prod-api', 'TODO_E2E_BACKEND_URL', 'dist', 'index.html']) requireText(productionServer, required, 'Production E2E server')
const productionServerContract = read(productionServerContractPath)
for (const required of ['/todo-template', '/prod-api/probe?source=contract', "path: '/probe?source=contract'", 'Production E2E static/proxy contract passed']) requireText(productionServerContract, required, 'Production E2E server contract')

const externalReportGate = read(externalReportGatePath)
for (const required of [
  'FlywayMigrationTest',
  'FileMaterialEndToEndTest',
  'HistoricalMigrationPreflightEndToEndTest',
  'FoundationCollationMigrationTest',
  'PhaseTwoDatabaseInvariantTest',
  'TodoPhaseTwoTransactionTest',
  'TodoRoutingJoinConcurrencyTest',
  'TodoAutoActionFencingConcurrencyTest',
  'TodoDefinitionLedgerConcurrencyTest',
  'tests > 0',
  'skipped === 0',
  'failures === 0',
  'errors === 0'
]) requireText(externalReportGate, required, 'External-database report gate')

const externalReportContract = read(externalReportContractPath)
for (const required of ['missing report', 'zero tests', 'skipped test', 'failed test']) requireText(externalReportContract, required, 'External-database report gate negative contract')

const workflow = read(workflowPath)
for (const required of [
  "node-version: '20'",
  'character set utf8mb4 collate utf8mb4_unicode_ci',
  'todo-config-admin.sql',
  'TODO_CONFIG_E2E_PASSWORD_HASH',
  'TODO_CONFIG_E2E_RUN_MARKER',
  'TODO_CONFIG_E2E_SLA_CODE',
  'TODO_CONFIG_E2E_DOD_CODE',
  'TODO_CONFIG_E2E_LEAD_NO',
  'assert-external-db-reports.js',
  'serve-e2e-production.js',
  'password="$(openssl rand -hex 9)"',
  'TODO_E2E_REAL_BACKEND: true',
  'todo-config-center.spec.js'
]) requireText(workflow, required, 'CI workflow')
forbidText(workflow, "grep -R '<skipped'", 'CI workflow')

console.log('Todo configuration real-backend E2E source contract passed')
