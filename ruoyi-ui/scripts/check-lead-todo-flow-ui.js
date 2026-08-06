const assert = require('assert')
const fs = require('fs')
const path = require('path')

const uiRoot = path.resolve(__dirname, '..')
const repoRoot = path.resolve(uiRoot, '..')

function read(relativePath) {
  const absolutePath = path.resolve(repoRoot, relativePath)
  assert(fs.existsSync(absolutePath), `missing ${relativePath}`)
  return fs.readFileSync(absolutePath, 'utf8')
}

function assertIncludes(source, fragment, label) {
  assert(source.includes(fragment), `${label} is missing ${fragment}`)
}

function assertApi(source, name, url, method) {
  const match = source.match(new RegExp(`export function ${name}\\([^)]*\\)\\s*\\{([\\s\\S]*?)\\n\\}`))
  assert(match, `missing API ${name}`)
  assertIncludes(match[1], url, `${name} URL`)
  assertIncludes(match[1], `method: '${method}'`, `${name} method`)
}

function assertComponent(file, componentName) {
  const source = read(file)
  assertIncludes(source, `name: '${componentName}'`, `${componentName} component name`)
  return source
}

function run() {
  const api = read('ruoyi-ui/src/api/lead/index.js')
  ;[
    ['confirmLeadTag', '/lead/tag/confirm', 'post'],
    ['listLeadCallRecords', '/call-records', 'get'],
    ['addLeadCallRecord', '/call-records', 'post'],
    ['listInvalidReview', '/lead/invalid-review/list', 'get'],
    ['completeInvalidReview', '/complete', 'post'],
    ['listLeadRetry', '/lead/retry/list', 'get'],
    ['getLeadRetryTimeline', '/retry-timeline', 'get'],
    ['listDeadPool', '/lead/dead-pool/list', 'get'],
    ['restoreDeadPoolLead', '/restore', 'post'],
    ['listAssignmentPolicy', '/lead/assignment-policy', 'get'],
    ['saveAssignmentPolicy', '/lead/assignment-policy', 'put']
  ].forEach(args => assertApi(api, ...args))

  const firstContact = assertComponent(
    'ruoyi-ui/src/views/lead/components/LeadFirstContactDrawer.vue',
    'LeadFirstContactDrawer'
  )
  ;['TodoDynamicForm', 'LeadCallTimeline', 'contactResult', 'VALID', 'SUSPECT_INVALID', 'UNREACHABLE',
    'contactName', 'city', 'legalDemand', 'visited', 'role="alert"', 'v-loading'].forEach(fragment =>
    assertIncludes(firstContact, fragment, 'first-contact drawer'))
  ;['data-testid="lead-first-contact-drawer"', 'data-testid="lead-first-contact-submit"'].forEach(fragment =>
    assertIncludes(firstContact, fragment, 'first-contact stable selectors'))
  assert(!/provider(Call|Id)|externalCallId\s*[:=]/.test(firstContact),
    'first-contact UI must not fabricate provider identity')
  assert(!/ownerId[^A-Za-z]/.test(firstContact), 'first-contact UI must not expose raw owner ID')
  ;['this.actionId = stableActionId(', 'actionId: this.actionId', 'if (this.submitError)'].forEach(fragment =>
    assertIncludes(firstContact, fragment, 'first-contact response-loss replay identity'))

  const callTimeline = read('ruoyi-ui/src/views/lead/components/LeadCallTimeline.vue')
  ;['this.actionId = stableActionId(', 'actionId: this.actionId', 'if (this.submitError)',
    'BusinessFileEvidenceList'].forEach(fragment =>
    assertIncludes(callTimeline, fragment, 'manual-call response-loss replay/evidence'))

  const detail = read('ruoyi-ui/src/views/lead/components/LeadDetailDrawer.vue')
  ;['BusinessTodoSummary', 'LeadCallTimeline', 'LeadRetryTimeline', 'Todo', 'SLA',
    "tagConfirmStatus === 'CONFIRMED'", "allowed(todo,'complete')"].forEach(fragment =>
    assertIncludes(detail, fragment, 'lead detail'))

  const pages = [
    ['ruoyi-ui/src/views/lead/review/index.vue', 'lead:invalid-review:list', 'lead:invalid-review:handle'],
    ['ruoyi-ui/src/views/lead/retry/index.vue', 'lead:retry:list', 'LeadRetryTimeline'],
    ['ruoyi-ui/src/views/lead/dead-pool/index.vue', 'lead:dead-pool:list', 'lead:dead-pool:restore'],
    ['ruoyi-ui/src/views/lead/policy/index.vue', 'lead:assignment-policy:list', 'lead:assignment-policy:edit']
  ]
  pages.forEach(([file, ...fragments]) => {
    const source = read(file)
    fragments.forEach(fragment => assertIncludes(source, fragment, file))
    ;['v-loading', 'el-empty', 'role="alert"'].forEach(fragment =>
      assertIncludes(source, fragment, `${file} states`))
  })

  const deadPool = read('ruoyi-ui/src/views/lead/dead-pool/index.vue')
  assert(!/claimLead|lead:pool:claim|>\s*领取\s*</.test(deadPool),
    'Dead-Pool must not expose a public-pool claim action')
  ;["actionId: stableActionId('DEAD_POOL_RESTORE'", 'actionId: this.form.actionId',
    'if (this.submitError)'].forEach(fragment =>
    assertIncludes(deadPool, fragment, 'Dead-Pool response-loss replay identity'))
  const review = read('ruoyi-ui/src/views/lead/review/index.vue')
  ;['reviewOpinion', "allowed(row, action)", "actionId: stableActionId('TD002_REVIEW'",
    'actionId: this.form.actionId', 'if (this.submitError)',
    'BusinessFileEvidenceList'].forEach(fragment =>
    assertIncludes(review, fragment, 'invalid-review governed completion/replay'))
  assert(!/reviewComment/.test(review),
    'TD-002 primary frontend contract must use reviewOpinion')
  const policy = read('ruoyi-ui/src/views/lead/policy/index.vue')
  ;['T0', 'T1_AM', 'T1_NOON', 'T1_PM', 'T2_AM', 'T2_NOON', 'T2_PM',
    'candidateUserIds', 'availabilityStatus', 'delegateUserId', 'expectedVersion',
    'recoverConflict', 'keepDraftOnLatest', 'useLatest', 'CONCURRENT_MODIFICATION'].forEach(fragment =>
    assertIncludes(policy, fragment, 'assignment policy editor'))
  assert(!/allow-create/.test(policy),
    'policy editor source must be selected from the governed catalogue')
  assert(!/<el-input[^>]*(ownerId|candidateUserIds)/.test(policy),
    'policy editor must use governed candidate selection, not raw IDs')
  ;['policy-card-', 'data-testid="policy-editor"', 'retry-window-',
    'data-testid="policy-conflict-keep-draft"', 'data-testid="policy-save-submit"'].forEach(fragment =>
    assertIncludes(policy, fragment, 'policy stable selectors'))

  const schemaMigration = read('ruoyi-admin/src/main/resources/db/migration/V0_20_52__lead_todo_flow_navigation.sql')
  assertIncludes(schemaMigration, 'create table biz_lead_source_governance_audit', 'lead governance schema migration')
  assert(!/\b(start transaction|insert into|update\s+biz_|commit;)\b/i.test(schemaMigration),
    'V0.20.52 must remain the independently repairable schema-only boundary')
  const migration = read('ruoyi-admin/src/main/resources/db/migration/V0_20_53__lead_source_governance_and_navigation.sql')
  ;[
    "'invalid-review'", "'lead/review/index'", "'lead:invalid-review:list'",
    "'retry'", "'lead/retry/index'", "'lead:retry:list'",
    "'dead-pool'", "'lead/dead-pool/index'", "'lead:dead-pool:list'",
    "'assignment-policy'", "'lead/policy/index'", "'lead:assignment-policy:list'"
  ].forEach(fragment => assertIncludes(migration, fragment, 'lead navigation migration'))
  ;['start transaction', 'biz_lead_source_governance_audit',
    'tmp_lead_source_governance_guard', "governance_result='REMAPPED'",
    'FAILURE_INJECTION_POINT_BEFORE_COMMIT', 'commit;'].forEach(fragment =>
    assertIncludes(migration.toLowerCase(), fragment.toLowerCase(), 'lead source governance migration'))
  assert(!/create table\s+biz_lead_source_governance_audit/i.test(migration),
    'V0.20.53 must not repeat permanent DDL from V0.20.52')

  const dynamicForm = read('ruoyi-ui/src/components/TodoDynamicForm/index.vue')
  ;[':prop="field.key"', ":for=\"field.type === 'materialChecklist' ? null : controlId(field)\"",
    ':id="labelId(field)"', 'controlId: this.controlId(field)',
    'labelledBy: this.labelId(field)', "'aria-label': field.label"].forEach(fragment =>
    assertIncludes(dynamicForm, fragment, 'Todo dynamic form accessible association'))
  const filePicker = read('ruoyi-ui/src/components/BusinessFile/BusinessFilePicker.vue')
  ;['<el-button', ':id="controlId || null"', ':aria-labelledby="labelledBy || null"'].forEach(fragment =>
    assertIncludes(filePicker, fragment, 'file picker interactive accessible association'))
  const materialChecklist = read('ruoyi-ui/src/components/BusinessFile/TodoMaterialChecklist.vue')
  ;['role="group"', ':aria-labelledby="labelledBy || null"', ':aria-label="`选择${item.label}`"'].forEach(fragment =>
    assertIncludes(materialChecklist, fragment, 'material checklist group association'))

  const e2e = read('ruoyi-ui/e2e/lead-todo-flow.spec.js')
  ;['VALID first contact', 'CONTACT_PROOF', 'manual call saves', 'TRUE_INVALID',
    'MISJUDGED_VALID', 'multiple attempts', 'does not create the next-window Todo immediately',
    'CONNECTED cancels', 'retry exhausted', 'Dead-Pool restore requires a reason',
    'all seven windows', 'concurrent write', 'setupLeadTodoFixtures',
    'leadState(', 'evidenceNames(', 'policySnapshot('].forEach(fragment =>
    assertIncludes(e2e, fragment, 'lead E2E structure'))
  ;['createRunContext', 'getByTestId(`lead-row-${keyword}`)', 'getByTestId(\'business-todo-drawer\')',
    'getByTestId(\'policy-save-submit\')', 'getByTestId(\'todo-action-submit\')'].forEach(fragment =>
    assertIncludes(e2e, fragment, 'lead E2E stable selectors/lifecycle'))
  const e2eDatabase = read('ruoyi-ui/e2e/support/lead-todo-e2e-database.js')
  ;['TODO_E2E_REAL_BACKEND', "_e2e$/i", 'cleanupLeadTodoFixtures',
    'assertNoLeadTodoFixtures', 'WINDOW_CODES', 'file_object_version',
    'todo_schedule_occurrence', 'createRunContext', 'start transaction',
    '/files/${file.fileObjectId}/retire', 'relationId', 'retireObjectIfUnreferenced',
    'verifyBackendIdentity', 'FOUNDATION_E2E_IDENTITY_SECRET', 'todo_sla_waiver',
    'LEAD_E2E_POLICY_ADMIN_NAVIGATION', 'lead_e2e_policy_admin_',
    "m.component='lead/policy/index'",
    "m.perms in('lead:assignment-policy:list','lead:assignment-policy:edit','lead:setting:options'," +
      "'system:user:list','todo:definition:list')",
    'delete from sys_role where role_key=@policy_admin_role_key'].forEach(fragment =>
    assertIncludes(e2eDatabase, fragment, 'lead E2E cleanable database fixture'))
  assert(!/\blike\b/i.test(e2eDatabase),
    'lead E2E ownership must use exact manifest membership, never wildcard matching')
  assert(/insert into todo_schedule_plan\([\s\S]*?schedule_purpose,idempotency_key,timezone[\s\S]*?concat\('LEAD_RETRY:',t\.business_id,':',@\$\{key\}_RETRY\)/.test(e2eDatabase),
    'lead E2E retry fixtures must provide the governed schedule purpose and semantic idempotency key')
  ;["drawer.getByRole('button', { name: 'close 业务待办', exact: true }).click()", 'await expect(drawer).toBeHidden()'].forEach(fragment =>
    assertIncludes(e2e, fragment, 'lead E2E retry drawer lifecycle'))
  assert(!e2e.includes("drawer.locator('.el-drawer__close-btn').click()"),
    'lead E2E retry drawer close control must be uniquely identified')
  assert(!/page\.route|route\.fulfill|mock/i.test(e2e),
    'lead E2E must not intercept or mock production business routes')

  console.log('Lead Todo flow UI contract passed')
}

run()
