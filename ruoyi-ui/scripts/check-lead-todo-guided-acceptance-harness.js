const assert = require('node:assert')
const fs = require('node:fs')
const path = require('node:path')
const { spawnSync } = require('node:child_process')

const root = path.resolve(__dirname, '..', '..')
const harnessPath = path.join(root, 'scripts', 'run-lead-todo-guided-acceptance.ps1')

assert.ok(fs.existsSync(harnessPath), 'guided Lead Todo acceptance harness must exist')
const source = fs.readFileSync(harnessPath, 'utf8')

function runHarness(args, env = {}) {
  const result = spawnSync('powershell.exe', ['-NoLogo', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', harnessPath, ...args], {
    cwd: root,
    encoding: 'utf8',
    env: { ...process.env, ...env },
    windowsHide: true
  })
  return { ...result, output: `${result.stdout || ''}${result.stderr || ''}`.trim() }
}

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
assert.match(source, /CreationUtc/, 'owned roots and descendants must carry immutable creation identity')
assert.match(source, /ExecutablePath/, 'process identity should retain an executable signature when Win32_Process exposes one')
assert.match(source, /CommandLine/, 'process identity should retain a command signature when Win32_Process exposes one')
assert.match(source, /UNOWNED_SERVICE_LISTENER/, 'an unowned backend or frontend listener must fail closed')
assert.match(source, /function\s+Assert-OwnedServiceListener/, 'listener ownership refusal must be a reusable executable contract')
assert.match(source, /function\s+Assert-ServiceListenerAbsence[\s\S]*backendPort[\s\S]*frontendPort/, 'final cleanup must query both backend and frontend ports')
assert.match(source, /services\.listener-absence/, 'both-port listener proof must be a durable manifest stage')
assert.match(source, /TODO_E2E_HARNESS_FAIL_AFTER_BACKEND_BIND/, 'controlled post-bind cleanup testing must have an explicit harness-only flag')
assert.match(source, /harness\.test-only-failure-after-backend-bind/, 'controlled failure must be visible in the sanitized stage manifest')
assert.doesNotMatch(source, /Stop-Process[^\r\n]*(?:Get-PortOwners|listenerOwners)/, 'listener PIDs must never be killed without ancestry verification')

const processStage = source.slice(source.indexOf('function Invoke-ProcessStage'), source.indexOf('function Get-RequiredEnvironment'))
const processStart = processStage.indexOf('Start-Process')
const processRegister = processStage.indexOf('Register-OwnedProcessRoot')
const processWait = processStage.indexOf('WaitForExit')
assert.ok(processStart >= 0 && processRegister > processStart && processWait > processRegister,
  'generic registered process stages must capture immutable ownership after Start-Process and before waiting')
assert.match(source, /browser\.guided-lead-2-of-2[\s\S]*RegisterOwnedRoot/, 'Playwright must opt into start-time root registration')

const ownershipRun = runHarness(['-ProcessOwnershipSelfTest'])
assert.strictEqual(ownershipRun.status, 0, ownershipRun.output)
const ownership = JSON.parse(ownershipRun.stdout)
assert.strictEqual(ownership.mutationPerformed, false)
assert.strictEqual(ownership.preMinimumRootRejected, true)
assert.strictEqual(ownership.reusedPidRejected, true)
assert.strictEqual(ownership.validDescendantAccepted, true)
assert.strictEqual(ownership.unownedListenerRefused, true)
assert.strictEqual(ownership.stopIdentityMismatchRefused, true)

const validationRunId = `contract-${process.pid}-${Date.now()}`
const validationPath = path.join(root, 'ruoyi-ui', 'output', 'playwright', 'lead-todo-guided-configuration', 'runs', validationRunId)
assert.ok(!fs.existsSync(validationPath), 'validation fixture must start absent')
const validation = runHarness(['-ValidateOnly', '-RunId', validationRunId])
assert.strictEqual(validation.status, 0, validation.output)
assert.strictEqual(JSON.parse(validation.stdout).mutationPerformed, false)
assert.ok(!fs.existsSync(validationPath), 'ValidateOnly must not create its run directory')

for (const unsafeRunId of ['../escape', '..', 'bad\\path', 'bad/path']) {
  const unsafe = runHarness(['-ValidateOnly', '-RunId', unsafeRunId])
  assert.notStrictEqual(unsafe.status, 0, `unsafe runId ${unsafeRunId} must fail closed`)
  assert.match(unsafe.output, /unsafe run id|unsupported run id/i)
}

assert.match(source, /lead-todo-guided-configuration[\\\/]runs/, 'runtime evidence must be namespaced under runs/<runId>')
assert.match(source, /TODO_E2E_ARTIFACT_DIR/, 'the harness must pass its isolated run directory to Playwright')
assert.match(source, /Refusing to overwrite existing acceptance run/i, 'prior run bundles must never be overwritten')

console.log(`Verified guided Lead Todo acceptance harness contract (${requiredEnvironment.length} environment variables, ${baselines.length} baselines)`)
