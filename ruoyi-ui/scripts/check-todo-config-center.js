const assert = require('assert')
const fs = require('fs')

const required = [
  'src/api/todo-config.js',
  'src/views/todo/config/shared/ConfigPageShell.vue',
  'src/views/todo/config/shared/ConfigMetricCard.vue',
  'src/views/todo/config/shared/ConfigDetailDrawer.vue',
  'src/views/todo/config/styles/config-center.scss',
  'src/views/todo/config/release/release-model.js'
]

const configurationPages = [
  'src/views/todo/config/template/index.vue',
  'src/views/todo/config/sla/index.vue',
  'src/views/todo/config/dod/index.vue',
  'src/views/todo/config/trigger/index.vue',
  'src/views/todo/config/simulation/index.vue',
  'src/views/todo/config/release/index.vue'
]

const foundationGovernanceComponents = [
  'FoundationAdmissionOverview.vue',
  'FoundationResourceReadiness.vue',
  'HistoricalMigrationReadiness.vue',
  'FileSecurityReadiness.vue',
  'FinanceReadiness.vue',
  'AcceptanceReadiness.vue'
].map(name => `src/views/todo/config/components/${name}`)
const foundationE2eHarness = 'src/views/todo/config/components/FoundationConfigurationHarness.vue'

const routes = [
  ['getTodoConfigDashboard', '/todo/config/dashboard', 'get'],
  ['listSlaRules', '/todo/config/sla-rules', 'get'],
  ['getSlaRule', '/todo/config/sla-rules/${id}', 'get'],
  ['createSlaRule', '/todo/config/sla-rules', 'post'],
  ['updateSlaRule', '/todo/config/sla-rules/${id}', 'put'],
  ['copySlaRule', '/todo/config/sla-rules/${id}/copy', 'post'],
  ['toggleSlaRule', '/todo/config/sla-rules/${id}/toggle', 'post'],
  ['testSlaRule', '/todo/config/sla-rules/${id}/test', 'post'],
  ['listDodRules', '/todo/config/dod-rules', 'get'],
  ['getDodRule', '/todo/config/dod-rules/${id}', 'get'],
  ['createDodRule', '/todo/config/dod-rules', 'post'],
  ['updateDodRule', '/todo/config/dod-rules/${id}', 'put'],
  ['copyDodRule', '/todo/config/dod-rules/${id}/copy', 'post'],
  ['toggleDodRule', '/todo/config/dod-rules/${id}/toggle', 'post'],
  ['getDodRuleReferenceCount', '/todo/config/dod-rules/${id}/reference-count', 'get'],
  ['testDodRule', '/todo/config/dod-rules/${id}/test', 'post'],
  ['listTodoTemplates', '/todo/config/templates', 'get'],
  ['getTodoTemplate', '/todo/config/templates/${id}', 'get'],
  ['createTodoTemplate', '/todo/config/templates', 'post'],
  ['updateTodoTemplate', '/todo/config/templates/${id}', 'put'],
  ['copyTodoTemplate', '/todo/config/templates/${id}/copy', 'post'],
  ['importTodoTemplate', '/todo/config/templates/import', 'post'],
  ['toggleTodoTemplate', '/todo/config/templates/${id}/toggle', 'post'],
  ['listTemplateVersions', '/todo/config/templates/${id}/versions', 'get'],
  ['updateTemplateDraft', '/todo/config/template-versions/${id}', 'put'],
  ['preflightTemplateDraft', '/todo/config/template-versions/${id}/preflight', 'post'],
  ['listTemplateEventCatalog', '/todo/config/template-catalog/events', 'get'],
  ['listTemplateOwnerCatalog', '/todo/config/template-catalog/owners', 'get'],
  ['listTemplateHandlerCatalog', '/todo/config/template-catalog/handlers', 'get'],
  ['listTemplateValidatorCatalog', '/todo/config/template-catalog/validators', 'get'],
  ['listTemplateAutoActionCatalog', '/todo/config/template-catalog/auto-actions', 'get'],
  ['listTemplateRoutingTargetCatalog', '/todo/config/template-catalog/routing-targets', 'get'],
  ['listTemplateSlaRuleCatalog', '/todo/config/template-catalog/sla-rules', 'get'],
  ['listTemplateDodRuleCatalog', '/todo/config/template-catalog/dod-rules', 'get'],
  ['listTemplateCalendarCatalog', '/todo/config/template-catalog/calendars', 'get'],
  ['listTriggerRules', '/todo/config/trigger-rules', 'get'],
  ['createTriggerRule', '/todo/config/trigger-rules', 'post'],
  ['updateTriggerRule', '/todo/config/trigger-rules/${id}', 'put'],
  ['toggleTriggerRule', '/todo/config/trigger-rules/${id}/toggle', 'post'],
  ['sortTriggerRules', '/todo/config/trigger-rules/sort', 'post'],
  ['simulateTriggerRule', '/todo/config/trigger-rules/simulate', 'post'],
  ['listTriggerEventCatalog', '/todo/config/trigger-catalog/events', 'get'],
  ['listTriggerTemplateCatalog', '/todo/config/trigger-catalog/templates', 'get'],
  ['listTriggerTemplateVersions', '/todo/config/trigger-catalog/templates/${id}/versions', 'get'],
  ['simulateConfiguration', '/todo/config/simulations', 'post'],
  ['listBusinessObjects', '/todo/config/business-objects', 'get'],
  ['listReleaseRecords', '/todo/config/release-records', 'get'],
  ['getReleaseRecord', '/todo/config/release-records/${id}', 'get'],
  ['listReleaseVersions', '/todo/config/release-records/${id}/versions', 'get'],
  ['diffReleaseRecords', '/todo/config/release-records/${left}/diff/${right}', 'get'],
  ['copyReleaseDraft', '/todo/config/release-records/${id}/copy-draft', 'post'],
  ['publishReleaseRecord', '/todo/config/release-records/${id}/publish', 'post'],
  ['rollbackReleaseDraft', '/todo/config/release-records/${id}/rollback-draft', 'post']
]

function exportedFunctionBody(source, name) {
  const start = source.indexOf(`export function ${name}`)
  if (start < 0) throw new Error(`missing api ${name}`)
  const next = source.indexOf('\nexport function ', start + 1)
  return source.slice(start, next < 0 ? source.length : next)
}

function assertRouteContract(source, name, path, method) {
  const body = exportedFunctionBody(source, name)
  const url = path.includes('${') ? `url: \`${path}\`` : `url: '${path}'`
  if (!body.includes(url) || !body.includes(`method: '${method}'`)) {
    throw new Error(`missing route contract ${name}`)
  }
}

function runNegativeFixture() {
  const wrongVerb = "export function getTodoConfigDashboard() { return request({ url: '/todo/config/dashboard', method: 'post' }) }"
  const wrongPath = "export function getTodoConfigDashboard() { return request({ url: '/todo/config/not-dashboard', method: 'get' }) }"
  assert.throws(() => assertRouteContract(wrongVerb, 'getTodoConfigDashboard', '/todo/config/dashboard', 'get'), /missing route contract getTodoConfigDashboard/)
  assert.throws(() => assertRouteContract(wrongPath, 'getTodoConfigDashboard', '/todo/config/dashboard', 'get'), /missing route contract getTodoConfigDashboard/)
}

function runtimeAndE2eSources(root) {
  return fs.readdirSync(root, { withFileTypes: true }).flatMap(entry => {
    const file = `${root}/${entry.name}`
    if (entry.isDirectory()) return runtimeAndE2eSources(file)
    return /\.(js|ts|vue)$/.test(entry.name) ? [file] : []
  })
}

function assertNoLegacyConfigurationComponentReferences() {
  const legacyComponent = ['todo', 'config', 'index'].join('/')
  const offenders = ['src', 'e2e'].flatMap(runtimeAndE2eSources)
    .filter(file => fs.readFileSync(file, 'utf8').includes(legacyComponent))
  if (offenders.length) {
    throw new Error(`old configuration component path is forbidden in runtime/test routes: ${offenders.join(', ')}`)
  }
}

function assertFoundationE2eBehaviorCoverage() {
  const contracts = {
    'e2e/todo-foundation-config-resources.spec.js': [
      'trigger rules use the active catalog and refresh create, edit and toggle state',
      'calendar edit and status survive a server reload',
      'decision requires a conclusion and displays impacted template codes',
      'admission evidence requires independent review and survives submit and approval reloads',
      'foundation resources expose repository source and runtime gaps without promoting values',
      'aggregate admission truthfully remains two of eight until every gate is ready',
      'historical migration readiness exposes inventory and blockers without selecting defaults',
      'historical migration preflight failure preserves the existing readiness requirements',
      'historical migration JSON blob export failure stays local and produces no evidence download',
      'historical migration inventory hides evidence export without permission',
      'file security readiness separates technical controls from independent review',
      'finance readiness separates repository capabilities from schema and decision blockers',
      'phase-one acceptance governs scenarios mappings and batch bind without mock credit',
      '/todo/foundation-migration/exception-export', 'state.admissionUpdates', 'state.acceptanceUpdates'
    ],
    'e2e/todo-foundation-definition.spec.js': [
      'valid TASK to DECISION to END preflight enables publish and edits invalidate the gate',
      'unresolved decisions and invalid LOOP/JOIN are visible and keep publish blocked',
      'catalog text field can be edited and saved into the canonical definition',
      'simulation sends the complete command and semantic diff renders server content as text',
      'requests.simulation.push', 'requests.diff.push', 'requests.drafts.push',
      'simulation-completed-at', 'preflight-run', 'diff-result'
    ]
  }
  Object.entries(contracts).forEach(([file, tokens]) => {
    const content = fs.readFileSync(file, 'utf8')
    if (!content.includes('todo/config/components/FoundationConfigurationHarness')) {
      throw new Error(`${file} must render the retained Foundation components through the real E2E harness`)
    }
    tokens.forEach(token => {
      if (!content.includes(token)) throw new Error(`${file} missing executable Foundation E2E contract ${token}`)
    })
  })
}

function assertLocalizedConfigurationMetadata() {
  const migration = '../ruoyi-admin/src/main/resources/db/migration/V0_20_34__todo_configuration_center_localization.sql'
  const content = fs.readFileSync(migration, 'utf8')
  requireTokens(migration, content, [
    "'待办模板'", "'触发规则'", "'SLA规则'", "'完成条件'", "'模拟测试'", "'发布记录'",
    "'业务阶段'", "'业务类型'", "'模板类型'", "'发布状态'", "'触发方式'", "'条件操作符'",
    "'负责人规则类型'", "'SLA类型'", "'SLA时间单位'", "'SLA计时起点'", "'超时策略'",
    "'完成条件类型'", "'规则状态'", "'版本状态'", "'已退役'", "'RETIRED'",
    "component='todo/config/index'", "visible='1',status='1'"
  ])
}

async function check() {
  if (fs.existsSync('src/views/todo/config/index.vue')) {
    throw new Error('old tabbed configuration page must be removed')
  }
  configurationPages.forEach(file => {
    if (!fs.existsSync(file)) throw new Error(`new configuration page must be retained: ${file}`)
  })
  foundationGovernanceComponents.forEach(file => {
    if (!fs.existsSync(file)) throw new Error(`Foundation component must be retained: ${file}`)
  })
  if (!fs.existsSync(foundationE2eHarness)) throw new Error(`Foundation E2E harness must be retained: ${foundationE2eHarness}`)
  assertNoLegacyConfigurationComponentReferences()
  assertLocalizedConfigurationMetadata()
  assertFoundationE2eBehaviorCoverage()
  required.forEach(file => {
    if (!fs.existsSync(file)) throw new Error(`missing ${file}`)
  })
  const packageJson = JSON.parse(fs.readFileSync('package.json', 'utf8'))
  if (!packageJson.scripts || packageJson.scripts['test:todo-config'] !== 'node scripts/check-todo-config-center.js') {
    throw new Error('missing exact test:todo-config package script')
  }

  const api = fs.readFileSync(required[0], 'utf8')
  routes.forEach(([name, path, method]) => assertRouteContract(api, name, path, method))

  const drawer = fs.readFileSync(required[3], 'utf8')
  for (const marker of ['<el-drawer', 'before-close', 'append-to-body', 'destroy-on-close', "update:visible", '$confirm', 'closeAfterSave']) {
    if (!drawer.includes(marker)) throw new Error(`missing drawer contract ${marker}`)
  }
  if (drawer.includes('<el-dialog')) throw new Error('configuration actions must not use el-dialog')

  for (const file of required.slice(1, 4)) {
    const component = fs.readFileSync(file, 'utf8')
    if (!component.includes('config-center.scss')) throw new Error(`missing shared style contract ${file}`)
    if (component.includes('<el-dialog')) throw new Error(`shared configuration component must not use el-dialog: ${file}`)
  }

  checkRuleLibraryPages()
  checkTriggerRulePage()
  checkTemplatePage()
  await checkSimulationAndReleasePages()
}

function source(file) {
  if (!fs.existsSync(file)) throw new Error(`missing ${file}`)
  const content = fs.readFileSync(file, 'utf8')
  if (content.includes('\uFFFD')) throw new Error(`invalid UTF-8 content ${file}`)
  if (content.includes('<el-dialog')) throw new Error(`configuration actions may not use el-dialog: ${file}`)
  return content
}

function requireTokens(file, content, tokens) {
  tokens.forEach(token => {
    if (!content.includes(token)) throw new Error(`${file} missing source contract ${token}`)
  })
}

function methodWindow(content, name) {
  const candidates = [`async ${name}(`, `${name}(`]
  const start = candidates.map(candidate => content.indexOf(candidate)).find(index => index >= 0)
  if (start < 0) throw new Error(`missing method ${name}`)
  return content.slice(start, start + 2400)
}

function assertMethodTokens(file, content, name, tokens) {
  const body = methodWindow(content, name)
  tokens.forEach(token => {
    if (!body.includes(token)) throw new Error(`${file} ${name} missing mutation contract ${token}`)
  })
}

function assertDirectToggleContract(file, content, api) {
  if (!content.includes('@click.stop="toggleRow(row)"')) throw new Error(`${file} row toggle must stop propagation and call toggleRow`)
  assertMethodTokens(file, content, 'toggleRow', [api, 'status: targetStatus', 'actionId:', 'expectedVersion:', 'rowToggleLoading', 'this.load()'])
  const body = methodWindow(content, 'toggleRow')
  if (body.includes('openDetail(row)')) throw new Error(`${file} row toggle may not open detail instead of mutating`)
}

function assertPersistedStatusContract(file, content) {
  requireTokens(file, content, ['v-if="persisted && !copyMode"', 'serverStatus', 'status: this.persisted && !this.copyMode ? this.form.serverStatus : this.form.status'])
}

function runMutationNegativeFixtures() {
  assert.throws(() => assertDirectToggleContract('fixture', '<el-button @click.stop="openDetail(row)" />', 'toggleSlaRule'), /row toggle must stop propagation/)
  assert.throws(() => assertPersistedStatusContract('fixture', 'payload() { return { status: this.form.status } }'), /missing source contract/)
}

function assertTriggerToggleContract(file, content) {
  if (!content.includes('@click.stop="toggleRow(row)"')) throw new Error(`${file} row toggle must stop propagation and call toggleRow`)
  const body = methodWindow(content, 'toggleRow')
  for (const token of ['rowToggleLoading', '$set(this.rowToggleLoading', '$confirm', 'toggleTriggerRule', 'buildTriggerToggleCommand', 'this.actionId(', 'version', 'this.load()']) {
    if (!body.includes(token)) throw new Error(`${file} toggleRow missing mutation contract ${token}`)
  }
  if (body.indexOf('$set(this.rowToggleLoading') > body.indexOf('$confirm')) throw new Error(`${file} toggle guard must be set before confirmation`)
  if (/toggleTriggerRule\([^,]+,\s*this\.(form|selected)/.test(body)) throw new Error(`${file} toggle must send a status-only command`)
}

function assertTriggerSortContract(file, content) {
  const body = methodWindow(content, 'saveSort')
  for (const token of ['sortTriggerRules', 'actionId:', 'items:', 'changedTriggerSortItems', 'sorting', 'this.load()']) {
    if (!body.includes(token)) throw new Error(`${file} saveSort missing mutation contract ${token}`)
  }
}

function runTriggerNegativeFixtures() {
  assert.throws(() => assertTriggerToggleContract('fixture', '<el-button @click.stop="toggleRow(row)" />\nasync toggleRow(row) { await this.$confirm(); await toggleTriggerRule(row.id, this.form) }'), /missing mutation contract|status-only/)
  assert.throws(() => assertTriggerSortContract('fixture', 'async saveSort() { await sortTriggerRules({ items: this.rows }) }'), /missing mutation contract/)
}

function checkRuleLibraryPages() {
  const slaPage = 'src/views/todo/config/sla/index.vue'
  const slaDrawer = 'src/views/todo/config/sla/SlaRuleDrawer.vue'
  const slaTimeline = 'src/views/todo/config/sla/SlaTimeline.vue'
  const dodPage = 'src/views/todo/config/dod/index.vue'
  const dodDrawer = 'src/views/todo/config/dod/DodRuleDrawer.vue'
  const files = [slaPage, slaDrawer, slaTimeline, dodPage, dodDrawer]
  const contents = Object.fromEntries(files.map(file => [file, source(file)]))
  const sla = `${contents[slaPage]}\n${contents[slaDrawer]}\n${contents[slaTimeline]}`
  const dod = `${contents[dodPage]}\n${contents[dodDrawer]}`

  requireTokens(slaPage, contents[slaPage], [
    'ConfigPageShell', 'ConfigMetricCard', 'SlaRuleDrawer', 'pagination',
    'law_todo_sla_type', 'law_todo_sla_unit', 'law_todo_sla_start_strategy',
    'law_todo_timeout_strategy', 'law_todo_rule_status', 'listSlaRules', 'getSlaRule'
  ])
  requireTokens('SLA workflow', sla, ['todo:sla-rule:create', 'todo:sla-rule:edit', 'todo:sla-rule:copy',
    'todo:sla-rule:toggle', 'createSlaRule', 'updateSlaRule', 'copySlaRule', 'toggleSlaRule', 'testSlaRule',
    'actionId', 'expectedVersion'])
  requireTokens(slaDrawer, contents[slaDrawer], [
    'ConfigDetailDrawer', 'listWorkCalendars', 'SlaTimeline', 'softRemindPercent',
    'hardRemindPercent', 'escalatePercent', '80%', '100%', '150%', 'createdAt',
    'pausePolicyJson', 'escalationPolicyJson', 'autoActionJson', 'actionId', 'expectedVersion'
  ])
  requireTokens(slaTimeline, contents[slaTimeline], ['80%', '100%', '150%', 'remind80At', 'overdue100At', 'escalate150At'])
  assertDirectToggleContract(slaPage, contents[slaPage], 'toggleSlaRule')
  assertPersistedStatusContract(slaDrawer, contents[slaDrawer])

  requireTokens(dodPage, contents[dodPage], [
    'ConfigPageShell', 'ConfigMetricCard', 'DodRuleDrawer', 'pagination',
    'law_todo_dod_rule_type', 'law_todo_rule_status',
    'listDodRules', 'getDodRule'
  ])
  requireTokens('DoD workflow', dod, ['todo:dod-rule:create', 'todo:dod-rule:edit', 'todo:dod-rule:copy',
    'todo:dod-rule:toggle', 'createDodRule', 'updateDodRule', 'copyDodRule', 'toggleDodRule',
    'getDodRuleReferenceCount', 'testDodRule', 'actionId', 'expectedVersion'])
  requireTokens(dodDrawer, contents[dodDrawer], [
    'ConfigDetailDrawer', 'listTodoValidatorCatalog', 'requiredFieldsJson',
    'requiredAttachmentsJson', 'conditionalRulesJson', 'validatorRefsJson', 'errorMessagesJson',
    'missingFields', 'missingAttachments', 'validatorIssues', 'payload', 'attachments',
    'actionId', 'expectedVersion'
  ])
  assertDirectToggleContract(dodPage, contents[dodPage], 'toggleDodRule')
  assertMethodTokens(dodPage, contents[dodPage], 'toggleRow', ['getDodRuleReferenceCount', '$confirm'])
  assertPersistedStatusContract(dodDrawer, contents[dodDrawer])

  const fixedOptionMarkers = ['slaTypeOptions', 'durationUnitOptions', 'startStrategyOptions', 'ruleTypeOptions', 'validatorOptions']
  Object.entries(contents).forEach(([file, content]) => {
    fixedOptionMarkers.forEach(marker => {
      if (content.includes(marker)) throw new Error(`${file} must use dictionary/catalog sources instead of ${marker}`)
    })
  })
}

function checkTriggerRulePage() {
  const page = 'src/views/todo/config/trigger/index.vue'
  const drawer = 'src/views/todo/config/trigger/TriggerRuleDrawer.vue'
  const builder = 'src/views/todo/config/trigger/TriggerConditionBuilder.vue'
  const sortModel = 'src/views/todo/config/trigger/trigger-sort-model.js'
  const contents = Object.fromEntries([page, drawer, builder, sortModel].map(file => [file, source(file)]))
  const workflow = `${contents[page]}\n${contents[drawer]}\n${contents[builder]}`

  requireTokens(page, contents[page], [
    'ConfigPageShell', 'ConfigMetricCard', 'TriggerRuleDrawer', 'pagination',
    'law_todo_trigger_mode', 'law_todo_condition_operator', 'law_todo_rule_status',
    'listTriggerRules', 'toggleTriggerRule', 'sortTriggerRules',
    'todo:trigger:list', 'todo:trigger:create', 'todo:trigger:edit', 'todo:trigger:toggle',
    'sourceEventLabel', 'conditionSummary', 'targetActionLabel', 'triggerModeLabel',
    '@row-click="openDetail"', '@click.stop="moveUp(row)"', '@click.stop="moveDown(row)"'
  ])
  requireTokens(drawer, contents[drawer], [
    'ConfigDetailDrawer', 'TriggerConditionBuilder',
    'listTriggerEventCatalog', 'listTriggerTemplateCatalog', 'listTriggerTemplateVersions', 'payloadSchemaJson', 'PUBLISHED', 'publishedVersions',
    'selectableEventCatalog', 'selectedEventActive', '历史停用',
    'selectableTemplateCatalog', 'selectedTemplateActive', 'effectiveEnabled', 'templateActive',
    `:disabled="effectiveEnabled === 'Y' && !templateActive(item)"`, '启用触发规则必须选择启用状态的待办模板',
    'businessObjectType', 'business_object_type',
    'createTriggerRule', 'updateTriggerRule', 'serverEnabled', 'enabled: this.persisted ? this.form.serverEnabled : this.form.enabled',
    'actionId', 'expectedVersion', 'conditionJson', '@dirty-change="conditionDraftDirty = $event"'
  ])
  requireTokens(builder, contents[builder], [
    'law_todo_condition_operator', 'payloadSchemaJson', 'schemaFields', 'operatorOptions',
    '$expression', 'version: 1', 'rawJson', 'malformed', 'repairRawJson',
    'fieldPath', 'conditions', 'valueJson', 'NOT_EXISTS', '@input="rawInput"', "this.$emit('dirty-change'"
  ])
  requireTokens('trigger workflow', workflow, ['eventType', 'payloadVersion', 'businessType', 'templateId', 'templateVersionId', 'sortOrder', 'version'])
  assertTriggerToggleContract(page, contents[page])
  assertTriggerSortContract(page, contents[page])
  requireTokens(page, contents[page], ['loadGlobalSortSnapshot', 'pageSize: 500', 'globalRows', 'handlePagination', 'moveTriggerRows', 'changedTriggerSortItems', 'sortPreparing: false'])
  assertMethodTokens(drawer, contents[drawer], 'save', ['createTriggerRule', 'updateTriggerRule', 'triggerRuleId', 'conditionJson', 'actionId', 'expectedVersion'])
  assertMethodTokens(drawer, contents[drawer], 'validate', ['effectiveEnabled', 'selectedTemplateActive'])
  const saveBody = methodWindow(contents[drawer], 'save')
  if (saveBody.includes('sortOrder:')) throw new Error('trigger save command must not send unsupported sortOrder; use sortTriggerRules')
  if (workflow.includes('simulateTriggerRule(')) throw new Error('Task 10 must not send an invented trigger simulation command')
  assertMethodTokens(page, contents[page], 'handoffSimulation', ["name: 'TodoConfigSimulation'", 'triggerRuleId', 'eventType', 'payloadVersion'])
  for (const marker of ['eventOptions', 'operatorOptions: [', 'templateOptions', 'runtimeUnsupportedOperators', 'normalizeSortOrders']) {
    if (workflow.includes(marker)) throw new Error(`trigger workflow must use catalog/dictionary sources instead of ${marker}`)
  }
  const model = require('../src/views/todo/config/trigger/trigger-sort-model')
  const sourceRows = Array.from({ length: 501 }, (_, index) => ({ triggerRuleId: index + 1, sortOrder: 0, version: index }))
  const baseline = Object.fromEntries(sourceRows.map(row => [row.triggerRuleId, row.sortOrder]))
  const moved = model.moveTriggerRows(sourceRows, 501, -1)
  assert.strictEqual(moved.rows[499].triggerRuleId, 501, 'global sort must cross the 500-row page boundary')
  assert.strictEqual(moved.rows[500].triggerRuleId, 500, 'global sort must retain the swapped boundary row')
  assert.strictEqual(new Set(moved.rows.map(row => row.sortOrder)).size, 501, 'global sort must canonicalize unique orders')
  assert.strictEqual(model.pageTriggerRows(moved.rows, 2, 500)[0].triggerRuleId, 500, 'dirty pagination must slice the global snapshot')
  assert.strictEqual(model.changedTriggerSortItems(moved.rows, baseline).length, 501, 'sort payload must include every globally changed row')
  assert.deepStrictEqual(Object.keys(model.buildTriggerToggleCommand('N', 7, 'toggle-1')).sort(), ['actionId', 'enabled', 'expectedVersion'])
}

function checkTemplatePage() {
  const page = 'src/views/todo/config/template/index.vue'
  const drawer = 'src/views/todo/config/template/TemplateDrawer.vue'
  const summary = 'src/views/todo/config/template/TemplateSummaryPanel.vue'
  const stepNames = ['basic', 'trigger', 'owner', 'sla', 'dod', 'routing', 'preview', 'versions']
  const stepFiles = [
    'TemplateBasicStep.vue', 'TemplateTriggerStep.vue', 'TemplateOwnerStep.vue', 'TemplateSlaStep.vue',
    'TemplateDodStep.vue', 'TemplateRoutingStep.vue', 'TemplatePreviewStep.vue', 'TemplateVersionStep.vue'
  ].map(name => `src/views/todo/config/template/steps/${name}`)
  const files = [page, drawer, summary, 'src/views/todo/config/template/template-draft-model.js', ...stepFiles]
  const contents = Object.fromEntries(files.map(file => [file, source(file)]))
  const workflow = files.map(file => contents[file]).join('\n')
  const nestedDrawers = source('src/views/todo/config/sla/SlaRuleDrawer.vue') + '\n' + source('src/views/todo/config/dod/DodRuleDrawer.vue')

  requireTokens(page, contents[page], [
    'ConfigPageShell', 'ConfigMetricCard', 'TemplateDrawer', 'TemplateSummaryPanel', 'pagination',
    'law_todo_business_stage', 'law_todo_business_type', 'law_todo_template_type', 'law_todo_publish_status',
    'listTodoTemplates', 'getTodoTemplate', 'importTodoTemplate', 'toggleTodoTemplate',
    'todo:template:list', 'todo:template:create', 'todo:template:import', 'todo:template:edit',
    'todo:template:copy', 'todo:template:toggle', 'todo:simulation:simulate', 'todo:release:publish',
    '@row-click="openDetail"', '@click.stop="toggleRow(row)"'
  ])
  stepNames.forEach(name => {
    if (!contents[drawer].includes(`name: '${name}'`)) throw new Error(`template drawer missing ${name} step`)
  })
  requireTokens(drawer, contents[drawer], [
    'size="78%"', 'ConfigDetailDrawer', 'hydrateTemplateDraft', 'toTemplateDraftPayload',
    'createTodoTemplate', 'copyTodoTemplate', 'updateTemplateDraft', 'listTemplateVersions',
    'preflightTemplateDraft', 'publishReleaseRecord', 'preflightGate', 'definitionSourceToken',
    'expectedDefinitionJson', 'ruleReferences', 'changeSummary', 'impactScope',
    'TODO_DEFINITION_CANONICAL_INVALID', 'definitionError', 'publishedReadOnly', 'validateAllSteps'
  ])
  requireTokens(summary, contents[summary], ['触发事件', '负责人规则', 'SLA', 'DoD', '下一步规则', '版本摘要'])
  requireTokens('template workflow', workflow, [
    'listTemplateEventCatalog', 'listTemplateOwnerCatalog', 'listTemplateHandlerCatalog',
    'listTemplateValidatorCatalog', 'listTemplateAutoActionCatalog', 'listTemplateSlaRuleCatalog', 'listTemplateDodRuleCatalog',
    'listTemplateCalendarCatalog',
    'SlaRuleDrawer', 'DodRuleDrawer', 'simulateConfiguration', 'RoutingGraphEditor',
    'law_todo_owner_rule_type', 'law_todo_condition_operator', 'law_todo_rule_status',
    'actionId', 'expectedVersion', 'expectedDefinitionJson'
  ])
  requireTokens('routing workflow', workflow, ['listTemplateRoutingTargetCatalog', 'TriggerConditionBuilder', 'payloadSchemaJson', 'termination'])
  requireTokens('simulation freshness', workflow, ['definitionReady', 'sourceToken', '当前草稿已变更，请先保存并完成发布预检'])
  requireTokens(summary, contents[summary], ['待办卡片预览', 'priority', 'owner'])
  assertMethodTokens(page, contents[page], 'toggleRow', ['toggleTodoTemplate', 'actionId:', 'expectedVersion:', 'rowToggleLoading', '$confirm', 'this.load()'])
  assertMethodTokens(drawer, contents[drawer], 'saveDraft', ['toTemplateDraftPayload', 'updateTemplateDraft', 'expectedDefinitionJson', 'ruleReferences', 'refreshSavedDraft'])
  assertMethodTokens(drawer, contents[drawer], 'runPreflight', ['preflightTemplateDraft', 'preflightGate', 'definitionSourceToken'])
  assertMethodTokens(drawer, contents[drawer], 'publish', ['runPreflight', 'publishReleaseRecord', 'preflightGate', 'definitionSourceToken', 'expectedDefinitionHash'])
  requireTokens(stepFiles[0], contents[stepFiles[0]], ['BASIC_FIELDS', 'basicValue', 'readonly || persisted'])
  if (contents[drawer].includes('ref="basic" v-model="form"')) throw new Error('basic step must not bind the entire aggregate draft')
  requireTokens(stepFiles[1], contents[stepFiles[1]], [':value="eventKey(item)"', 'eventSelection', 'model.payloadVersion" disabled'])
  requireTokens(stepFiles[3], contents[stepFiles[3]], ['listTemplateSlaRuleCatalog', 'listTemplateCalendarCatalog',
    ':calendar-options="calendarCatalog"', 'nestedSaved(savedId)', "v-hasPermi=\"['todo:sla-rule:create']\""])
  requireTokens(stepFiles[4], contents[stepFiles[4]], ['listTemplateDodRuleCatalog', 'listTemplateValidatorCatalog',
    ':provided-validators="validatorCatalog"', 'nestedSaved(savedId)', "v-hasPermi=\"['todo:dod-rule:create']\"",
    'template-dod-ui-rules', 'systemDerivedFields', 'system-derived-fields-change', 'missingUiFields'])
  if (/uiFields\s*:\s*\[\{[^}]*required\s*:\s*true/.test(contents[stepFiles[4]])) {
    throw new Error('template DoD step must not require UI fields when the selected rules only need attachments or system-derived values')
  }
  requireTokens('nested operation catalogs', nestedDrawers, ['calendarOptions', 'providedValidators'])
  requireTokens(stepFiles[7], contents[stepFiles[7]], ["v-hasPermi=\"['todo:release:diff']\""])
  if (workflow.includes('knownIds')) throw new Error('nested rule creation must select the exact returned id instead of diff guessing')
  requireTokens('nested exact identity', nestedDrawers, ['finishSaved(response.data)', "$emit('saved', savedId)"])
  requireTokens(summary, contents[summary], ['draft || definitionError', '<template v-if="draft">'])
  if (contents[drawer].includes("this.mode = 'view'")) throw new Error('template drawer must not mutate the mode prop')
  if (workflow.includes('<el-dialog')) throw new Error('template operations may not use el-dialog')

  const model = require('../src/views/todo/config/template/template-draft-model')
  const dodUiRules = require('../src/views/todo/config/template/template-dod-ui-rules')
  const fieldRule = [{ id: 1, requiredFieldsJson: '["contactResult"]', requiredAttachmentsJson: '[]', conditionalRulesJson: '[]' }]
  assert.deepStrictEqual(dodUiRules.missingUiFields(fieldRule, [1], [], []), ['contactResult'], 'selected field rules must expose missing UI fields')
  assert.deepStrictEqual(dodUiRules.missingUiFields(fieldRule, [1], ['contactResult'], []), [], 'configured UI fields must satisfy selected DoD rules')
  const attachmentRule = [{ id: 2, requiredFieldsJson: '[]', requiredAttachmentsJson: '["SIGNED_FILE"]', conditionalRulesJson: '[]' }]
  assert.deepStrictEqual(dodUiRules.missingUiFields(attachmentRule, [2], [], []), [], 'attachment-only DoD rules must not require UI fields')
  const derivedRule = [{ id: 3, requiredFieldsJson: '["generatedCaseNo"]', requiredAttachmentsJson: '[]', conditionalRulesJson: '[]' }]
  assert.deepStrictEqual(dodUiRules.missingUiFields(derivedRule, [3], [], ['generatedCaseNo']), [], 'system-derived fields must not require user-facing controls')
  const conditionalRule = [{ id: 4, requiredFieldsJson: '[]', requiredAttachmentsJson: '[]', conditionalRulesJson: '[{"field":"reason","when":{"field":"result","equals":"REJECT"}}]' }]
  assert.deepStrictEqual(dodUiRules.missingUiFields(conditionalRule, [4], ['reason'], []), ['result'], 'conditional source fields must be represented in the UI or system-derived')
  assert.deepStrictEqual(dodUiRules.missingUiFields(conditionalRule, [4], ['reason', 'result'], []), [], 'conditional target and source fields must satisfy the UI contract together')
  assert.throws(() => model.hydrateTemplateDraft({ editableVersion: { definitionJson: '{broken' } }),
    error => error && error.code === 'TODO_DEFINITION_CANONICAL_INVALID', 'malformed canonical template data must fail closed')
  const fixture = {
    templateId: 7, templateCode: 'T-7', templateName: '模板七', businessType: 'LEAD', status: '0', version: 3,
    editableVersion: { versionId: 9, versionNo: 4, status: 'DRAFT', definitionJson: JSON.stringify({
      schemaVersion: 1, templateCode: 'T-7', event: { eventType: 'LEAD_ASSIGNED', payloadVersion: 1, condition: {} },
      owner: { config: { type: 'ROLE', roleKey: 'lawyer' } }, dod: { config: {} }, sla: { config: {} },
      ui: { config: { businessStage: 'LEAD', templateType: 'STANDARD', priority: 'HIGH' } },
      routing: { config: { nodes: [], edges: [] } }, autoActions: [], decisionRefs: [], acceptanceRefs: []
    }), changeSummary: '变更', impactScope: '线索' },
    ruleReferences: [
      { type: 'DOD', id: 12, order: 2 }, { type: 'SLA', id: 8, order: 0 }, { type: 'DOD', id: 11, order: 1 }
    ]
  }
  const hydrated = model.hydrateTemplateDraft(fixture)
  const payload = model.toTemplateDraftPayload(hydrated, 'save-9')
  assert.deepStrictEqual(payload.ruleReferences.map(ref => `${ref.type}:${ref.id}:${ref.order}`), ['SLA:8:0', 'DOD:11:1', 'DOD:12:2'], 'rule references must retain explicit order')
  assert.strictEqual(JSON.parse(payload.definitionJson).ui.config.priority, 'HIGH', 'canonical metadata must survive save payload generation')
  const gate = model.createPreflightGate(9, hydrated.sourceDefinitionJson, 'hash-9')
  assert.strictEqual(model.canPublishFromGate(gate, 9, hydrated.sourceDefinitionJson, false), true, 'matching saved definition may publish')
  assert.strictEqual(model.canPublishFromGate(gate, 9, hydrated.sourceDefinitionJson, true), false, 'dirty draft must invalidate publish gate')
  assert.strictEqual(model.invalidatePreflightGate(), null, 'gate invalidation must be explicit')
  const unsaved = model.emptyDraft()
  unsaved.event = { eventType: 'LEAD_ASSIGNED', payloadVersion: 1, condition: { field: 'ownerId' } }
  unsaved.owner.config = { type: 'ROLE', candidates: ['lawyer'] }
  unsaved.slaRuleId = 8
  unsaved.dodRuleIds = [11, 12]
  unsaved.routing.config = { start: 'a', nodes: [{ key: 'a', type: 'TASK' }], edges: [] }
  const created = model.mergeCreatedAggregate(unsaved, { templateId: 77, versionId: 99, versionNo: 1 }, { sourceDefinitionJson: '{"saved":true}', templateVersion: 0 })
  assert.strictEqual(created.event.eventType, 'LEAD_ASSIGNED', 'create/copy must retain the unsaved event')
  assert.deepStrictEqual(created.owner.config.candidates, ['lawyer'], 'create/copy must retain the unsaved owner')
  assert.deepStrictEqual(created.dodRuleIds, [11, 12], 'create/copy must retain ordered DoD references')
  assert.strictEqual(created.versionId, 99, 'create/copy must merge the persisted aggregate identity')
}

async function checkSimulationAndReleasePages() {
  const simulationPage = 'src/views/todo/config/simulation/index.vue'
  const simulationDrawer = 'src/views/todo/config/simulation/SimulationDrawer.vue'
  const simulationResult = 'src/views/todo/config/simulation/SimulationResult.vue'
  const releasePage = 'src/views/todo/config/release/index.vue'
  const releaseDrawer = 'src/views/todo/config/release/ReleaseRecordDrawer.vue'
  const semanticDiff = 'src/views/todo/config/release/VersionSemanticDiff.vue'
  const contents = Object.fromEntries([
    simulationPage, simulationDrawer, simulationResult, releasePage, releaseDrawer, semanticDiff
  ].map(file => [file, source(file)]))

  requireTokens(simulationPage, contents[simulationPage], [
    'ConfigPageShell', 'ConfigMetricCard', 'SimulationDrawer',
    'todo:simulation:simulate', '只读模拟，不创建真实待办', 'listTemplateEventCatalog',
    'listTodoTemplates', 'law_todo_business_type', '$route.query', ':initial-event="handoffEvent"'
  ])
  requireTokens(simulationDrawer, contents[simulationDrawer], [
    'ConfigDetailDrawer', 'SimulationResult', 'simulateConfiguration', 'versionId',
    'eventType', 'payloadVersion', 'businessType', 'businessId', 'payload', 'effectiveAt',
    'expectedDefinitionHash', 'listBusinessObjects', 'listTodoTemplates', 'remote-method',
    '只读模拟，不创建真实待办'
  ])
  requireTokens(simulationResult, contents[simulationResult], [
    '状态变化', '命中模板', '负责人', 'SLA', 'DoD', '下一步路由', '待办卡片预览', '技术日志',
    'simulation.trigger', 'simulation.owner', 'simulation.sla', 'simulation.form', 'simulation.routes'
  ])
  const ordered = ['状态变化', '命中模板', '负责人', 'SLA', 'DoD', '下一步路由', '待办卡片预览', '技术日志']
    .map(token => contents[simulationResult].indexOf(token))
  assert(ordered.every(index => index >= 0) && ordered.every((index, position) => position === 0 || ordered[position - 1] < index),
    'simulation result sections must follow execution order')

  requireTokens(releasePage, contents[releasePage], [
    'ConfigPageShell', 'ConfigMetricCard', 'ReleaseRecordDrawer', 'listReleaseRecords',
    'todo:release:list', 'law_todo_version_status', 'dict-tag', 'collectReleasePages',
    'publisher', 'beginTime', 'endTime', 'exportRelease', 'releaseStatusOptions', "['PUBLISHED', 'RETIRED']"
  ])
  requireTokens(releaseDrawer, contents[releaseDrawer], [
    'ConfigDetailDrawer', 'VersionSemanticDiff', 'getReleaseRecord', 'listReleaseVersions', 'diffReleaseRecords',
    'copyReleaseDraft', 'rollbackReleaseDraft', '变更摘要', '影响范围', '规则快照',
    '回滚来源', '生成新草稿', '不可变发布版本', 'todo:release:diff',
    'todo:release:rollback', 'todo:template:copy'
  ])
  requireTokens(semanticDiff, contents[semanticDiff], [
    'changes', 'path', 'before', 'after', '无语义差异'
  ])
  if (/updateTodoTemplate|updateTemplateDraft|publishReleaseRecord/.test(contents[releaseDrawer])) {
    throw new Error('published release drawer must never mutate or republish an immutable version')
  }
  if (/nextVersionNo|newVersionNo|listTemplateVersions/.test(contents[releaseDrawer])) {
    throw new Error('release draft version numbers must be allocated only by the server')
  }
  if (contents[releasePage].includes("row.status==='PUBLISHED'?'success':'info'")) {
    throw new Error('release status must use law_todo_version_status dictionary rendering')
  }

  const model = require('../src/views/todo/config/release/release-model')
  const offsets = []
  const rows = await model.collectReleasePages(({ offset, limit }) => {
    offsets.push(offset)
    const all = [{ id: 1 }, { id: 2 }, { id: 3 }, { id: 4 }, { id: 5 }]
    return { rows: all.slice(offset, offset + limit), total: all.length }
  }, {}, { pageSize: 2, maxRows: 10 })
  assert.deepStrictEqual(offsets, [0, 2, 4], 'release export must fetch every page')
  assert.strictEqual(rows.length, 5, 'release export must include all matching rows')
  await assert.rejects(() => model.collectReleasePages(() => ({ rows: [], total: 11 }), {}, { pageSize: 2, maxRows: 10 }), /exceeds maximum/)
  let sequence = 0
  const registry = model.createActionIdRegistry(() => `action-${++sequence}`)
  assert.strictEqual(registry.idFor('copy'), registry.idFor('copy'), 'timeout retry must reuse the same action id')
  registry.complete('copy')
  assert.strictEqual(registry.idFor('copy'), 'action-2', 'successful mutation may allocate the next action id')
}

runNegativeFixture()
runMutationNegativeFixtures()
runTriggerNegativeFixtures()
check().then(() => console.log('todo configuration center contract ok')).catch(error => { console.error(error); process.exitCode = 1 })

module.exports = { assertRouteContract, exportedFunctionBody, runNegativeFixture }
