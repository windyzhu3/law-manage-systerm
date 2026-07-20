const assert = require('assert')
const fs = require('fs')

const required = [
  'src/api/todo-config.js',
  'src/views/todo/config/shared/ConfigPageShell.vue',
  'src/views/todo/config/shared/ConfigMetricCard.vue',
  'src/views/todo/config/shared/ConfigDetailDrawer.vue',
  'src/views/todo/config/styles/config-center.scss'
]

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
  ['listTemplateVersions', '/todo/config/templates/${id}/versions', 'get'],
  ['updateTemplateDraft', '/todo/config/template-versions/${id}', 'put'],
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
  ['listReleaseRecords', '/todo/config/release-records', 'get'],
  ['getReleaseRecord', '/todo/config/release-records/${id}', 'get'],
  ['diffReleaseRecords', '/todo/config/release-records/${left}/diff/${right}', 'get'],
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

function check() {
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

runNegativeFixture()
runMutationNegativeFixtures()
runTriggerNegativeFixtures()
check()
console.log('todo configuration center contract ok')

module.exports = { assertRouteContract, exportedFunctionBody, runNegativeFixture }
