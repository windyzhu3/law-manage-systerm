const assert = require('assert')
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const read = relative => fs.readFileSync(path.join(root, relative), 'utf8')
let checks = 0

function check(name, assertion) {
  assertion()
  checks += 1
  process.stdout.write(`  pass ${name}\n`)
}

const workbenchPath = 'src/views/todo/config/template/index.vue'
const problemPath = 'src/views/todo/config/template/TemplateProblemSummary.vue'
const progressPath = 'src/views/todo/config/template/TemplateProgressCell.vue'
const summaryModelPath = 'src/views/todo/config/template/template-workbench-model.js'
const stylePath = 'src/views/todo/config/styles/config-center.scss'
const journeyPath = 'src/views/todo/config/journey/index.vue'
const journeyComponentPaths = [
  'src/views/todo/config/journey/components/JourneyStepNav.vue',
  'src/views/todo/config/journey/components/ConfigurationHealthPanel.vue',
  'src/views/todo/config/journey/components/EmployeeTodoPreview.vue',
  'src/views/todo/config/journey/components/JourneySaveStatus.vue',
  'src/views/todo/config/journey/components/JourneyConflictDialog.vue'
]
const journeyStepPaths = [
  'src/views/todo/config/journey/steps/EventStep.vue',
  'src/views/todo/config/journey/steps/TriggerStep.vue',
  'src/views/todo/config/journey/steps/OwnerStep.vue',
  'src/views/todo/config/journey/steps/DodStep.vue',
  'src/views/todo/config/journey/steps/SlaStep.vue',
  'src/views/todo/config/journey/steps/RoutingStep.vue'
]
const simulationPublishPaths = [
  'src/views/todo/config/journey/steps/SimulationPublishStep.vue',
  'src/views/todo/config/journey/components/BusinessObjectPayloadEditor.vue',
  'src/views/todo/config/journey/components/SimulationTrace.vue',
  'src/views/todo/config/journey/components/PublishPreflightPanel.vue'
]
const businessFirstComponentPaths = [
  'src/views/todo/config/journey/components/ContextResourceDrawer.vue',
  'src/views/todo/config/journey/components/TypedConditionBuilder.vue',
  'src/views/todo/config/resource/ResourceItemDrawer.vue',
  'src/views/todo/config/resource/BusinessDataSourcePanel.vue'
]
const workbench = read(workbenchPath)

check('renders the task-centered template workbench', () => {
  for (const token of [
    'TemplateProblemSummary',
    'TemplateProgressCell',
    '待办配置工作台',
    '按业务旅程完成配置、模拟与发布',
    '新建配置',
    '继续配置',
    '查看已发布版本',
    'data-testid="template-workbench"'
  ]) {
    assert(workbench.includes(token), `missing workbench token: ${token}`)
  }
})

check('uses the server-paged workbench endpoint without per-row journey calls', () => {
  assert(workbench.includes('listTodoTemplateWorkbench'), 'workbench endpoint is not used')
  assert(!workbench.includes('listTodoTemplates'), 'legacy template listing must not power the workbench')
  assert(!workbench.includes('getTodoTemplateJourney'), 'per-row journey calls are forbidden')
  assert(!workbench.includes('todayTriggeredTodoCount'), 'generic dashboard cards must be removed from the workbench')
})

check('prefers global server health counts and keeps a page-row fallback', () => {
  const model = require(path.join(root, summaryModelPath))
  const rows = [{ blockerCount: 1, warningCount: 2 }, { blockerCount: 0, warningCount: 1 }, {}]
  assert.deepStrictEqual(
    model.resolveProblemSummary({ blockerTemplates: 8, warningTemplates: 5, readyTemplates: 3 }, rows),
    { blockerTemplates: 8, warningTemplates: 5, readyTemplates: 3 },
    'server counts must win over current-page counts'
  )
  assert.deepStrictEqual(
    model.resolveProblemSummary({}, rows),
    { blockerTemplates: 1, warningTemplates: 1, readyTemplates: 1 },
    'older responses must fall back to mutually exclusive current-page counts'
  )
  for (const token of ['response.blockerTemplates', 'response.warningTemplates', 'response.readyTemplates']) {
    assert(workbench.includes(token), `workbench does not retain server summary field: ${token}`)
  }
})

check('keeps a single journey action and secondary template code metadata', () => {
  assert(workbench.includes('todo-template-journey'), 'primary action must open the hidden journey route')
  assert(workbench.includes('templateId'), 'journey navigation must retain templateId')
  assert(workbench.includes('template-code'), 'template code must be muted secondary metadata')
  assert(workbench.includes('@row-click="continueConfiguration"'), 'the task row must continue its journey')
  assert(workbench.includes('@click.stop="continueConfiguration(row)"'), 'the primary action must not double-handle row navigation')
  assert(!workbench.includes('openWorkflow'), 'legacy multi-action workflow must be removed')
})

check('provides reusable problem and progress cells', () => {
  assert(fs.existsSync(path.join(root, problemPath)), 'missing TemplateProblemSummary.vue')
  assert(fs.existsSync(path.join(root, progressPath)), 'missing TemplateProgressCell.vue')
  const problem = read(problemPath)
  const progress = read(progressPath)
  for (const token of ['项阻塞', '项警告', '可继续']) {
    assert(problem.includes(token), `problem summary missing state: ${token}`)
  }
  for (const token of ['配置进度', 'completedSteps', 'totalSteps']) {
    assert(progress.includes(token), `progress cell missing token: ${token}`)
  }
  assert(progress.includes('nextStepTitle'), 'progress copy must use the backend first incomplete step')
  assert(!progress.includes('this.completedSteps + 1'), 'progress copy must not assume journey steps are contiguous')
})

check('applies the approved visual tokens without gradients or table shadows', () => {
  const styles = read(stylePath)
  for (const token of [
    '--todo-navy: #0B2A55',
    '--todo-gold: #C89A3D',
    '--todo-surface: #FFFFFF',
    '--todo-bg: #F4F7FA',
    '--todo-border: #D9E1EA'
  ]) {
    assert(styles.includes(token), `missing approved visual token: ${token}`)
  }
  assert(!styles.includes('.template-workbench__table-section { box-shadow'), 'table section must not use a shadow')
  assert(!workbench.includes('linear-gradient'), 'workbench must not use gradients')
})

check('provides the seven-step journey shell and its reusable panels', () => {
  assert(fs.existsSync(path.join(root, journeyPath)), 'missing journey shell')
  for (const componentPath of journeyComponentPaths) {
    assert(fs.existsSync(path.join(root, componentPath)), `missing journey component: ${componentPath}`)
  }
  const journey = read(journeyPath)
  for (const token of [
    'JourneyStepNav',
    'ConfigurationHealthPanel',
    'EmployeeTodoPreview',
    'JourneySaveStatus',
    'JourneyConflictDialog',
    'activeComponent',
    'beforeRouteLeave',
    'canLeave(this.journey)',
    'mergeSaveResult',
    'updateTemplateDraft',
    'getTodoTemplateJourney'
  ]) {
    assert(journey.includes(token), `journey shell missing token: ${token}`)
  }
})

check('owns one 600ms autosave debounce and preserves explicit retry controls', () => {
  const journey = read(journeyPath)
  assert.strictEqual((journey.match(/setTimeout\s*\(/g) || []).length, 1, 'journey shell must own exactly one debounce timer')
  assert(journey.includes('}, 600)'), 'journey autosave debounce must be 600ms')
  assert(journey.includes('@retry="retrySave"'), 'failed saves must expose manual retry')
  assert(journey.includes('status === 409'), 'optimistic-lock conflicts must handle HTTP 409')
  assert(journey.includes('@refresh-merge="refreshAndMergeConflict"'), 'conflict dialog must support refresh and merge')
  assert(journey.includes('@save-copy="saveConflictCopy"'), 'conflict dialog must support saving a copy')
})

check('wires capability-aware reads and meaningful reused-route guards', () => {
  const journey = read(journeyPath)
  for (const token of [
    'resolveJourneyCapabilities',
    'snapshotReadPlan',
    'routeContextChanged(from, to)',
    'beforeRouteUpdate',
    'canSaveDraft',
    "plan.length === 1 && plan[0] === 'JOURNEY'"
  ]) {
    assert(journey.includes(token), `journey capability wiring missing token: ${token}`)
  }
  assert(journey.includes("'$route.query':"), 'route query watcher must reload meaningful context changes')
})

check('renders field-level conflict differences and stages authoritative copy navigation', () => {
  const journey = read(journeyPath)
  const conflict = read('src/views/todo/config/journey/components/JourneyConflictDialog.vue')
  for (const token of [
    'buildConflictCopyJourney',
    'createCopyTransition',
    'consumeCopyTransition',
    'pendingCopyTransition',
    'authoritativeCopy',
    'hasUnresolvedFieldConflicts',
    'discard-local'
  ]) {
    assert(journey.includes(token), `journey copy recovery missing token: ${token}`)
  }
  for (const token of ['differences', 'collisions', '同一字段', '你的修改', '服务器修改']) {
    assert(conflict.includes(token), `conflict dialog missing field-level difference token: ${token}`)
  }
})

check('uses the approved journey visual language responsively', () => {
  const sources = [read(journeyPath), ...journeyComponentPaths.map(read)].join('\n')
  for (const token of ['#0B2A55', '#C89A3D', '@media (max-width: 960px)', '@media (max-width: 640px)']) {
    assert(sources.includes(token), `journey shell missing visual token: ${token}`)
  }
  assert(!sources.includes('linear-gradient'), 'journey shell must not use gradients')
  assert(!sources.includes('table-section { box-shadow'), 'journey table sections must not use shadows')
})

check('installs business-first event trigger and owner journey editors', () => {
  for (const componentPath of [...journeyStepPaths, ...businessFirstComponentPaths]) {
    assert(fs.existsSync(path.join(root, componentPath)), `missing Task 11 component: ${componentPath}`)
  }
  const journey = read(journeyPath)
  for (const token of [
    'EventStep',
    'TriggerStep',
    'OwnerStep',
    'ContextResourceDrawer',
    '@repair-resource="openResourceRepair"',
    '@repaired="completeResourceRepair"'
  ]) {
    assert(journey.includes(token), `journey Task 11 wiring missing token: ${token}`)
  }
})

check('keeps normal journey editors free of raw JSON and code-entry controls', () => {
  const sources = journeyStepPaths.concat([
    'src/views/todo/config/journey/components/TypedConditionBuilder.vue'
  ]).map(read).join('\n')
  for (const forbidden of [
    '原始 JSON',
    '输入 JSON',
    '字段路径',
    '运算符编码',
    'type=\"textarea\"'
  ]) {
    assert(!sources.includes(forbidden), `business journey editor exposes technical input: ${forbidden}`)
  }
  for (const token of ['维护事件字段', '全部满足', '任一满足', '阻塞发布', '解析顺序', '兜底负责人']) {
    assert(sources.includes(token), `business journey editor missing guidance: ${token}`)
  }
})

check('guards owner and condition controls against unsupported runtime values', () => {
  const owner = read('src/views/todo/config/journey/steps/OwnerStep.vue')
  const ownerModel = read('src/views/todo/config/journey/journey-step-model.js')
  const conditions = read('src/views/todo/config/journey/components/TypedConditionBuilder.vue')
  assert(ownerModel.includes('isOwnerField'), 'event-owner choices must use governed numeric user references')
  assert(owner.includes("value: 'CANDIDATE_POOL'") && owner.includes('disabled: true'),
    'candidate-pool must remain visibly unavailable until runtime claim semantics are implemented')
  assert(conditions.includes('precision: integer ? 0'), 'integer conditions must not accept decimals')
  assert(owner.includes('scopeOwnerFields'), 'event-owner fields must be scoped to the selected event version')
  assert(owner.includes('ownerSelectionStillValid'), 'stale owner-field selections must be cleared and blocked')
})

check('restores visible focus after contextual event repair', () => {
  const event = read('src/views/todo/config/journey/steps/EventStep.vue')
  assert(event.includes('ref="eventSearch"'), 'event selection control needs a stable focus ref')
  assert(event.includes('ref="schemaRepair"'), 'schema repair area needs a stable focus ref')
  assert(event.includes('repairFocusTarget'), 'focus path must be resolved by the tested behavior model')
  assert(event.includes('scrollIntoView') && event.includes('.focus()'),
    'repair return must scroll and focus a real control')
  assert(event.includes('is-focus-restored'), 'repair return must visibly highlight the repaired area')
})

check('keeps existing incomplete event resources actionable during repair', () => {
  const journey = read('src/views/todo/config/journey/index.vue')
  const eventStep = read('src/views/todo/config/journey/steps/EventStep.vue')
  const contextDrawer = read('src/views/todo/config/journey/components/ContextResourceDrawer.vue')
  assert(journey.includes('eventCatalogId: event.eventCatalogId || event.event_catalog_id || null'),
    'resource refresh must retain the governed event identity')
  assert(journey.includes("description: event.description || ''"),
    'resource refresh must retain the business description')
  assert(eventStep.includes('resourceId: event && event.eventCatalogId'),
    'event repair must reopen the existing governed resource')
  assert(eventStep.includes('event.eventName'), 'the normal event picker must prefer the business event name')
  assert(contextDrawer.includes('this.$nextTick(() => this.hydrate())'),
    'context repair must hydrate only after the replacement request prop has rendered')
})

check('keeps event sample JSON behind an explicit advanced section', () => {
  const drawer = read('src/views/todo/config/resource/EventResourceDrawer.vue')
  const designer = read('src/views/todo/config/resource/PayloadSchemaDesigner.vue')
  assert(drawer.includes('根据字段生成样例'), 'event samples need a guided generator')
  assert(drawer.includes('高级设置：查看或调整原始样例'), 'raw event sample must be advanced-only')
  assert(drawer.includes('generateSample'), 'event sample generator must be wired')
  assert(designer.includes("this.$emit('validity-change', false, '请填写字段名称')"),
    'a newly added blank field must remain locally editable until it has a schema key')
})

check('allows only governed business resources to enter edit mode', () => {
  const panel = read('src/views/todo/config/resource/BusinessResourcePanel.vue')
  assert(panel.includes("item.source === 'GOVERNED'"), 'schema-derived fields must remain read-only')
  assert(panel.includes("openEdit('FIELD', row)"), 'governed fields need an edit action')
  assert(panel.includes("openEdit('MATERIAL', row)"), 'governed materials need an edit action')
  assert(panel.includes("openEdit('DOD_RECIPE', item)"), 'governed recipes need an edit action')
  assert(panel.includes(':item=\"drawer.item\"'), 'resource editor must receive the selected governed item')
})

check('presents the unified governed resource center without executable validator controls', () => {
  const resourceIndex = read('src/views/todo/config/resource/index.vue')
  const business = read('src/views/todo/config/resource/BusinessResourcePanel.vue')
  const validators = read('src/views/todo/config/resource/ValidatorCatalogPanel.vue')
  for (const token of ['事件目录', '校验器目录', '业务资源', '工作日历', '数据源状态']) {
    assert(resourceIndex.includes(token), `resource center missing section: ${token}`)
  }
  for (const token of ['ResourceItemDrawer', '业务字段', '材料类型', '完成条件配方']) {
    assert(business.includes(token), `business resource maintenance missing token: ${token}`)
  }
  assert(resourceIndex.includes('WorkCalendarDialog'), 'resource center must reuse WorkCalendarDialog')
  assert(resourceIndex.includes('BusinessDataSourcePanel'), 'resource center must expose data-source readiness')
  assert(validators.includes('高级状态'), 'validator technical status must be secondary')
  for (const forbidden of ['上传实现', '执行校验器', '@click=\"execute', '@click=\"upload']) {
    assert(!validators.includes(forbidden), `validator catalog exposes executable control: ${forbidden}`)
  }
})

check('installs DoD SLA and routing journey editors with employee-preview parity', () => {
  const components = [
    'src/views/todo/config/journey/components/DodRecipePicker.vue',
    'src/views/todo/config/journey/components/SlaTimelinePreview.vue',
    'src/views/todo/config/journey/components/BusinessRoutingEditor.vue'
  ]
  for (const componentPath of components) {
    assert(fs.existsSync(path.join(root, componentPath)), `missing Task 12 component: ${componentPath}`)
  }
  const journey = read(journeyPath)
  for (const token of [
    'DodStep',
    'SlaStep',
    'RoutingStep',
    'projectEmployeePreview',
    ':value="liveEmployeePreview"'
  ]) {
    assert(journey.includes(token), `journey Task 12 wiring missing token: ${token}`)
  }
})

check('uses business-first DoD recipes while keeping validators advanced-only', () => {
  const dod = read('src/views/todo/config/journey/steps/DodStep.vue')
  const picker = read('src/views/todo/config/journey/components/DodRecipePicker.vue')
  for (const token of ['推荐完成标准', '员工需要填写', '员工需要上传', '办理说明', '高级设置']) {
    assert(dod.includes(token) || picker.includes(token), `DoD business editor missing token: ${token}`)
  }
  assert(dod.includes('rankDodRecipes'), 'DoD recipes must be context-ranked')
  assert(dod.includes('materializeDodRecipe'), 'DoD recipes must materialize into the current draft')
  assert(dod.includes('validatorRefs'), 'advanced DoD settings must expose governed validator references')
  assert(!dod.includes('type="textarea"'), 'normal DoD journey must not expose raw JSON')
})

check('renders natural-language SLA semantics and repairable 80/100/150 timeline', () => {
  const sla = read('src/views/todo/config/journey/steps/SlaStep.vue')
  const timeline = read('src/views/todo/config/journey/components/SlaTimelinePreview.vue')
  for (const token of ['办理时长', '工作日历', '待办创建时起算', '员工申请暂停或恢复', '修复工作日历']) {
    assert(sla.includes(token), `SLA business editor missing token: ${token}`)
  }
  for (const token of ['创建待办', '80%', '100%', '150%', '提醒', '超时', '升级']) {
    assert(timeline.includes(token), `SLA timeline missing semantic marker: ${token}`)
  }
  assert(sla.includes('slaRepairBlocker'), 'SLA editor must surface calendar/calculation blockers')
  assert(sla.includes('previewTodoJourneySla'), 'SLA timeline must use the governed server calendar calculation')
  assert(sla.includes('this.refreshCalculation({ commitOnSuccess: false })'),
    'hydration and read-only preview must never write the journey draft')
  assert(sla.includes('this.refreshCalculation({ commitOnSuccess: true })'),
    'DAY edits must wait for a governed preview before writing the journey draft')
  assert(sla.includes('if (commitOnSuccess) this.emitPatch()'),
    'a governed DAY edit must emit exactly once after preview succeeds')
  assert(!sla.includes('start.getTime()'), 'SLA timeline must not fabricate wall-clock due dates')
  assert(!sla.includes('TODO_ACCEPTED'), 'journey must not expose unsupported SLA start semantics')
  assert(!sla.includes('TODO_STARTED'), 'journey must not expose unsupported SLA start semantics')
  assert(!sla.includes('type="textarea"'), 'normal SLA journey must not expose raw JSON')
})

check('edits ordered business routing and keeps the topology graph advanced-only', () => {
  const step = read('src/views/todo/config/journey/steps/RoutingStep.vue')
  const routing = read('src/views/todo/config/journey/components/BusinessRoutingEditor.vue')
  for (const token of ['完成后下一步', '业务结果', '结束', '并行办理', '全部完成后汇合']) {
    assert(step.includes(token) || routing.includes(token), `business routing missing token: ${token}`)
  }
  assert(step.includes('高级设置：拓扑图'), 'technical routing graph must be advanced-only')
  assert(step.includes('RoutingGraphEditor'), 'advanced routing must preserve existing graph editor compatibility')
  assert(routing.includes('templateName'), 'routing target business name must be primary')
  assert(routing.includes('routingDraftBlocker'), 'incomplete business routing must be flagged before continuing')
  assert(routing.includes('$emit(\'change\''), 'ordered routing changes must patch the journey draft')
  assert(!routing.includes('type="textarea"'), 'normal routing journey must not expose raw JSON')
})

check('keeps backend routing issues authoritative in the normal journey', () => {
  const step = read('src/views/todo/config/journey/steps/RoutingStep.vue')
  for (const forbidden of [
    'TODO_ROUTE_CYCLE_UNCONTROLLED',
    'TODO_ROUTE_NODE_UNREACHABLE',
    'TODO_ROUTE_TASK_TEMPLATE_REQUIRED'
  ]) {
    assert(!step.includes(forbidden), `client must not duplicate backend route issue: ${forbidden}`)
  }
  assert(step.includes('step.issueCount'), 'routing step must present authoritative backend issue state')
})

check('closes the journey with governed simulation and immutable publishing', () => {
  for (const componentPath of simulationPublishPaths) {
    assert(fs.existsSync(path.join(root, componentPath)), `missing Task 13 component: ${componentPath}`)
  }
  const journey = read(journeyPath)
  const step = read(simulationPublishPaths[0])
  const payload = read(simulationPublishPaths[1])
  const trace = read(simulationPublishPaths[2])
  const preflight = read(simulationPublishPaths[3])
  for (const token of [
    'SimulationPublishStep',
    '@published="handlePublished"',
    '@navigate-repair="navigateRepair"'
  ]) assert(journey.includes(token), `journey Task 13 wiring missing token: ${token}`)
  for (const token of [
    'listBusinessObjects',
    'hydrateTodoJourneyPayload',
    'simulateTodoJourney',
    'preflightTemplateDraft',
    'publishReleaseRecord',
    'manualOverrides',
    'expectedDefinitionHash',
    'requireSavedDraft',
    'localDateTimeNow',
    'this.readonly',
    'dirty',
    'saving'
  ]) assert(step.includes(token), `simulation/publish step missing governed call: ${token}`)
  assert(journey.includes("['PUBLISHED', 'RETIRED'].includes(publishStatus)"),
    'immutable journey versions must enter a read-only shell after publish')
  assert(journey.includes(':readonly="activeEditorReadonly"'),
    'publish-only users must retain simulation and publish controls on editable drafts')
  assert(journey.includes("this.activeStep === 'SIMULATION_PUBLISH' ? this.immutableVersion : this.publishedReadOnly"),
    'definition edit permission must not be conflated with immutable-version execution')
  assert(!step.includes('new Date().toISOString()'), 'LocalDateTime commands must not send UTC-offset instants')
  assert(payload.includes('只读样例'), 'sample objects must be visibly read-only')
  assert(payload.includes('••••••'), 'sensitive payload values must be masked')
  assert(trace.includes('EVENT') && trace.includes('TODO_PREVIEW'), 'simulation trace must cover the fixed journey')
  assert(preflight.includes('warningReason'), 'warnings must require an acknowledgement reason')
  assert(preflight.includes('<el-form v-if="warnings.length"'), 'warning acknowledgement must render inside an Element form context')
  const api = read('src/api/todo-config.js')
  assert(
    /preflightTemplateDraft[\s\S]*?repeatSubmit:\s*false/.test(api),
    'read-only POST preflight must opt out of the generic duplicate-submit guard'
  )
  assert(!step.includes('createRuntime'), 'simulation UI must never request runtime persistence')
})

console.log(`todo phase two ux contract passed (${checks} checks)`)
