const assert = require('node:assert')
const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..', '..')
const harnessPath = path.join(root, 'scripts', 'run-lead-todo-guided-acceptance.ps1')

assert.ok(fs.existsSync(harnessPath), 'guided Lead Todo acceptance harness must exist')
const source = fs.readFileSync(harnessPath, 'utf8')

const requiredEnvironment = [
  'TODO_E2E_DB_NAME',
  'TODO_E2E_DB_HOST',
  'TODO_E2E_DB_PORT',
  'TODO_E2E_DB_USER',
  'TODO_E2E_DB_PASSWORD',
  'TODO_E2E_REDIS_HOST',
  'TODO_E2E_REDIS_PORT',
  'TODO_E2E_REDIS_DISPOSABLE',
  'TODO_CONFIG_E2E_PASSWORD',
  'TODO_CONFIG_E2E_PASSWORD_HASH',
  'TODO_CONFIG_E2E_RUN_MARKER',
  'TODO_CONFIG_E2E_SLA_CODE',
  'TODO_CONFIG_E2E_DOD_CODE',
  'TODO_CONFIG_E2E_LEAD_NO'
]
requiredEnvironment.forEach(name => assert.match(source, new RegExp(name), `${name} must be part of the harness contract`))

assert.match(source, /\^\[A-Za-z0-9_\]\+_e2e\$/, 'database name must be restricted to disposable _e2e names')
assert.match(source, /Refusing.*database/i, 'guarded database refusal must be explicit')
assert.match(source, /information_schema\.schemata/i, 'database existence and absence must be queried independently')

const baselines = [
  'ry_20260417.sql',
  'quartz.sql',
  'lead_module_20260602.sql',
  'lead_menu_20260602.sql',
  'customer_contract_module_20260603.sql',
  'customer_contract_dict_patch_20260611.sql',
  'case_module_20260611.sql',
  'matter_module_20260615.sql',
  'matter_menu_patch_20260617.sql',
  'finance_module_20260624.sql',
  'customer_tag_assign_permission_fix_20260627.sql'
]
baselines.forEach(file => assert.match(source, new RegExp(file.replace('.', '\\.')), `${file} must be imported`))

const exactPlaywright = 'npx playwright test tests/e2e/todo-config-journey.spec.js --grep "GUIDED_LEAD_" --project=chromium --reporter=list'
assert.strictEqual(source.split(exactPlaywright).length - 1, 1, 'the exact browser command must appear once')
assert.match(source, /bootstrap-proof/i, 'bootstrap must emit independently verifiable proof counts')
assert.match(source, /runtime-requery/i, 'runtime outcome must be requeried outside Playwright')
assert.match(source, /todo-e2e-global-teardown\.js/, 'repository global teardown must own the database drop')
assert.match(source, /harnessPid/i, 'manifest must distinguish the harness process')
assert.match(source, /backendLauncherPid/i, 'manifest must distinguish the backend launcher process')
assert.match(source, /backendApplicationPid/i, 'manifest must distinguish the Java application process')
assert.match(source, /guided-lead-acceptance-manifest\.json/, 'sanitized manifest must be emitted')
assert.match(source, /ValidateOnly/, 'a no-mutation validation mode must be supported')
assert.match(source, /Get-CimInstance\s+(?:-ClassName\s+)?Win32_Process/, 'process ownership must use a PS5-compatible Win32_Process snapshot')
assert.match(source, /ParentProcessId/, 'owned descendants must be derived through parent process IDs')
assert.match(source, /function\s+Get-OwnedProcessTree/, 'owned process-tree discovery must be reusable during readiness and finally cleanup')
assert.ok(source.split('Get-OwnedProcessTree').length - 1 >= 3, 'owned process-tree discovery must run during readiness and cleanup')
assert.match(source, /ProcessOwnershipSelfTest/, 'ancestry isolation must have a non-mutating PowerShell self-test')
assert.match(source, /UNOWNED_SERVICE_LISTENER/, 'an unowned backend or frontend listener must fail closed')
assert.match(source, /function\s+Assert-OwnedServiceListener/, 'listener ownership refusal must be a reusable executable contract')
assert.match(source, /function\s+Assert-ServiceListenerAbsence[\s\S]*backendPort[\s\S]*frontendPort/, 'final cleanup must query both backend and frontend ports')
assert.match(source, /services\.listener-absence/, 'both-port listener proof must be a durable manifest stage')
assert.match(source, /TODO_E2E_HARNESS_FAIL_AFTER_BACKEND_BIND/, 'controlled post-bind cleanup testing must have an explicit harness-only flag')
assert.match(source, /harness\.test-only-failure-after-backend-bind/, 'controlled failure must be visible in the sanitized stage manifest')
assert.doesNotMatch(source, /Stop-Process[^\r\n]*(?:Get-PortOwners|listenerOwners)/, 'listener PIDs must never be killed without ancestry verification')

console.log(`Verified guided Lead Todo acceptance harness contract (${requiredEnvironment.length} environment variables, ${baselines.length} baselines)`)
