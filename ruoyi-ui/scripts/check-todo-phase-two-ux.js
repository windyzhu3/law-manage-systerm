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

function loadScenarioWorkbenchModel() {
  const source = read('src/views/todo/config/journey/simulation-workbench-model.js')
    .replace(/export function /g, 'function ')
  return new Function(
    `${source}; return {
      scenarioGate,
      failedScenarioResult,
      scenarioFailureMessage,
      scenarioRepairTarget,
      versionIdentity,
      versionStatus,
      versionDiffPlan,
      simulationCompletionMessage,
      readinessRepairTarget,
      journeySimulationReadiness,
      mergeJourneySimulationReadiness,
      persistedScenarioResults
    }`
  )()
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

check('normalizes template version identities and never plans an invalid diff request', () => {
  const model = loadScenarioWorkbenchModel()
  assert.strictEqual(model.versionIdentity({ version_id: 82 }), 82)
  assert.strictEqual(model.versionIdentity({ versionId: 88 }), 88)
  assert.strictEqual(model.versionIdentity({ id: '79' }), 79)
  assert.strictEqual(model.versionIdentity({ version_id: undefined }), null)
  assert.strictEqual(model.versionStatus({ publish_status: 'published' }), 'PUBLISHED')
  assert.deepStrictEqual(
    model.versionDiffPlan(
      [{ version_id: 82, status: 'PUBLISHED' }],
      88
    ),
    {
      available: true,
      leftVersionId: 82,
      rightVersionId: 88,
      reason: ''
    }
  )
  assert.deepStrictEqual(
    model.versionDiffPlan([{ version_id: 82, status: 'DRAFT' }], 88),
    {
      available: false,
      leftVersionId: null,
      rightVersionId: 88,
      reason: 'NO_PUBLISHED_VERSION'
    }
  )
  assert.deepStrictEqual(
    model.versionDiffPlan([{ version_id: undefined, status: 'PUBLISHED' }], 88),
    {
      available: false,
      leftVersionId: null,
      rightVersionId: 88,
      reason: 'INVALID_VERSION_ID'
    }
  )
})

check('uses authoritative readiness for the final message and repair target', () => {
  const model = loadScenarioWorkbenchModel()
  assert.strictEqual(
    model.simulationCompletionMessage({ publicationReady: true }),
    '模拟发布验证已全部通过'
  )
  assert.strictEqual(
    model.simulationCompletionMessage({ publicationReady: false }),
    ''
  )
  assert.deepStrictEqual(
    model.readinessRepairTarget(
      { code: 'TODO_FULL_SIMULATION_REQUIRED' },
      []
    ),
    {
      stepCode: 'SIMULATION_PUBLISH',
      scenarioCode: '',
      focusTarget: 'full-simulation'
    }
  )
  assert.deepStrictEqual(
    model.readinessRepairTarget(
      {
        code: 'TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE',
        blockingScenarios: [{ scenarioCode: 'TD001_UNREACHABLE' }]
      },
      [{ scenarioCode: 'TD001_UNREACHABLE' }]
    ),
    {
      stepCode: 'SIMULATION_PUBLISH',
      scenarioCode: 'TD001_UNREACHABLE',
      focusTarget: 'scenario-selector'
    }
  )
})

check('projects and merges server simulation readiness without losing journey context', () => {
  const model = loadScenarioWorkbenchModel()
  const journey = {
    template: {
      templateId: 17,
      versionId: 88,
      definitionHash: 'hash-88'
    },
    steps: [
      { code: 'EVENT', state: 'COMPLETED', value: { eventType: 'LEAD_ASSIGNED' } },
      { code: 'SIMULATION_PUBLISH', state: 'BLOCKED', issueCount: 1, value: { config: {} } }
    ],
    issues: [
      { code: 'TODO_JOURNEY_OWNER_FALLBACK_REQUIRED', stepCode: 'OWNER' },
      { code: 'TODO_FULL_SIMULATION_REQUIRED', stepCode: 'SIMULATION_PUBLISH' }
    ],
    employeePreview: { title: '首联待办' }
  }
  assert.deepStrictEqual(model.journeySimulationReadiness(journey), {
    templateId: 17,
    versionId: 88,
    definitionHash: 'hash-88',
    blockingScenarios: [],
    fullSimulationPassed: false,
    publicationReady: false,
    issues: [journey.issues[1]]
  })
  const readiness = {
    templateId: 17,
    versionId: 88,
    definitionHash: 'hash-88',
    blockingScenarios: [],
    fullSimulationPassed: true,
    publicationReady: true,
    issues: []
  }
  const merged = model.mergeJourneySimulationReadiness(journey, readiness)
  assert.notStrictEqual(merged, journey)
  assert.strictEqual(merged.employeePreview, journey.employeePreview)
  assert.deepStrictEqual(merged.issues, [journey.issues[0]])
  assert.deepStrictEqual(merged.steps[1], {
    code: 'SIMULATION_PUBLISH',
    state: 'COMPLETED',
    issueCount: 0,
    value: { config: {} }
  })
  assert.deepStrictEqual(
    model.journeySimulationReadiness(merged),
    readiness,
    'the authoritative definition hash and publication gate must survive the parent-child feedback cycle'
  )
})

check('keeps failed scenario diagnostics visible and returns repair to the first blocked scenario', () => {
  const model = loadScenarioWorkbenchModel()
  const scenarios = [
    { scenarioCode: 'TD001_VALID', scenarioName: '有效首联', requiredForPublish: true },
    { scenarioCode: 'TD001_UNREACHABLE', scenarioName: '未接通', requiredForPublish: true }
  ]
  const failed = model.failedScenarioResult(scenarios[0], {
    businessCode: 'TODO_SIMULATION_SCENARIO_NODE_UNRESOLVED',
    message: 'Completion node reference TD-001 does not resolve to one task node'
  })
  assert.deepStrictEqual(failed, {
    scenarioCode: 'TD001_VALID',
    scenarioName: '有效首联',
    passed: false,
    businessCode: 'TODO_SIMULATION_SCENARIO_NODE_UNRESOLVED',
    message: 'Completion node reference TD-001 does not resolve to one task node'
  })
  assert.strictEqual(
    model.scenarioFailureMessage(failed),
    '当前场景无法定位待办完成节点，请保存后续路由后重新验证'
  )
  const gate = model.scenarioGate(scenarios, { TD001_VALID: failed }, 'draft-hash')
  assert.strictEqual(gate.blockingScenarios[0].reason, 'LAST_RUN_FAILED')
  assert.deepStrictEqual(
    model.scenarioRepairTarget(
      { code: 'TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE', stepCode: 'SIMULATION_PUBLISH' },
      gate,
      scenarios
    ),
    {
      stepCode: 'SIMULATION_PUBLISH',
      scenarioCode: 'TD001_VALID',
      focusTarget: 'scenario-selector'
    }
  )
})

check('restores exact-hash scenario evidence after reopening the simulation step', () => {
  const model = loadScenarioWorkbenchModel()
  const scenarios = [
    { scenarioCode: 'TD001_VALID', scenarioName: '有效首联', expectedNextTemplateCode: 'TD-004', requiredForPublish: true },
    { scenarioCode: 'TD001_UNREACHABLE', scenarioName: '未接通', expectedNextTemplateCode: 'TD-003', requiredForPublish: true }
  ]
  const readiness = {
    definitionHash: 'hash-88',
    blockingScenarios: [],
    publicationReady: true,
    issues: []
  }
  assert.deepStrictEqual(model.persistedScenarioResults(scenarios, readiness, 'hash-88'), {
    TD001_VALID: {
      scenarioCode: 'TD001_VALID',
      scenarioName: '有效首联',
      expectedNextTemplateCode: 'TD-004',
      actualNextTemplateCode: 'TD-004',
      passed: true,
      message: '当前草稿已通过验证',
      evidence: { definitionHash: 'hash-88' }
    },
    TD001_UNREACHABLE: {
      scenarioCode: 'TD001_UNREACHABLE',
      scenarioName: '未接通',
      expectedNextTemplateCode: 'TD-003',
      actualNextTemplateCode: 'TD-003',
      passed: true,
      message: '当前草稿已通过验证',
      evidence: { definitionHash: 'hash-88' }
    }
  })
  assert.deepStrictEqual(model.persistedScenarioResults(scenarios, {
    ...readiness,
    definitionHash: 'old-hash'
  }, 'hash-88'), {})
})

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
    'updateTodoTemplateJourney',
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

check('keeps owner strategy edits local until an explicit apply action', () => {
  const owner = read('src/views/todo/config/journey/steps/OwnerStep.vue')
  const ownerModel = read('src/views/todo/config/journey/journey-step-model.js')
  for (const token of ['createOwnerStrategyDrafts', 'updateOwnerStrategyDraft', 'applyOwnerStrategyDraft']) {
    assert(ownerModel.includes(token), `owner model missing non-destructive draft helper: ${token}`)
  }
  assert(owner.includes('应用此规则'), 'owner editor needs an explicit apply action')
  assert(owner.includes('未应用修改'), 'owner editor must make pending changes visible')
  assert(ownerModel.includes('ownerEligible'), 'owner editor must honor the event owner whitelist')
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

check('configures governed completion results without free-text routing rules', () => {
  const step = read('src/views/todo/config/journey/steps/RoutingStep.vue')
  const routing = read('src/views/todo/config/journey/components/BusinessRoutingEditor.vue')
  assert(step.includes(':outcome-set="resources.businessOutcomeSet || {}"'),
    'routing step must provide the governed completion-result catalog')
  assert(routing.includes('应用首联推荐路由'),
    'lead routing must offer one-click recommended routes')
  assert(routing.includes('v-model="row.resultValue"'),
    'ordinary routing must select a governed result value')
  assert(routing.includes('materializeOutcomeRouting'),
    'recommended routes must use the tested canonical materializer')
  assert(routing.includes('resultSentence'),
    'ordinary routing must explain each branch in business language')
  assert(!routing.includes('v-model.trim="row.label"'),
    'ordinary typed routing must not ask users to type an opaque business result')
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

check('groups creation validation and renders governed semantic labels', () => {
  const components = [
    'src/views/todo/config/journey/components/CreationValidationPanel.vue',
    'src/views/todo/config/journey/components/EventInputPanel.vue',
    'src/views/todo/config/journey/components/TodoCreationPreview.vue',
    'src/views/todo/config/journey/components/SemanticValueRenderer.vue',
    'src/views/todo/config/journey/components/SemanticOptionSelector.vue',
    'src/views/todo/config/journey/components/AdvancedPayloadOverride.vue'
  ]
  for (const componentPath of components) {
    assert(fs.existsSync(path.join(root, componentPath)), `missing semantic simulation component: ${componentPath}`)
  }
  const payload = read('src/views/todo/config/journey/components/BusinessObjectPayloadEditor.vue')
  const api = read('src/api/todo-config.js')
  assert(payload.includes('CreationValidationPanel'), 'payload editor must show creation blockers separately')
  assert(payload.includes('EventInputPanel'), 'payload editor must group event inputs')
  assert(payload.includes('TodoCreationPreview'), 'payload editor must preview the created Todo')
  assert(payload.includes('AdvancedPayloadOverride'), 'technical overrides must remain advanced-only')
  assert(payload.includes('applyHydrationOverrides'), 'manual event inputs must remain visible after reactive updates')
  assert(payload.includes('remainingHydrationBlockers'), 'manual event inputs must clear their matching blockers')
  assert(payload.includes('creationCoverage'), 'manual event inputs must update local creation coverage')
  const semanticSelector = read('src/views/todo/config/journey/components/SemanticOptionSelector.vue')
  assert(semanticSelector.includes('seedCurrentOption'), 'semantic selectors must display the current governed label instead of a raw ID')
  assert(semanticSelector.includes('ensureCurrentOptions'),
    'controlled semantic selectors must resolve a non-null initial value without waiting for focus')
  assert(api.includes('listTodoFieldOptions'), 'semantic selectors need a governed options endpoint')
  assert(!payload.includes("MISSING: '待补充'"), 'optional missing fields must not be labelled as blockers')
})

check('runs governed completion scenarios and blocks publish until all pass', () => {
  const components = [
    'src/views/todo/config/journey/components/ScenarioSelector.vue',
    'src/views/todo/config/journey/components/CompletionFormRenderer.vue',
    'src/views/todo/config/journey/components/BatchScenarioGate.vue'
  ]
  for (const componentPath of components) {
    assert(fs.existsSync(path.join(root, componentPath)), `missing scenario workbench component: ${componentPath}`)
  }
  const step = read('src/views/todo/config/journey/steps/SimulationPublishStep.vue')
  const selector = read(components[0])
  const form = read(components[1])
  const gate = read(components[2])
  const api = read('src/api/todo-config.js')
  for (const token of ['有效首联', '疑似无效', '未接通', '预期下一待办', '实际下一待办']) {
    assert(selector.includes(token) || gate.includes(token), `scenario workbench missing label: ${token}`)
  }
  assert(step.includes('listJourneyScenarios'), 'scenario list must be server governed')
  assert(step.includes('simulateJourneyScenario'), 'single scenario execution must use the server')
  assert(step.includes('batchSimulateJourneyScenarios'), 'batch scenario gate must use the server')
  assert(step.includes('persistedScenarioResults'),
    'reopening the step must restore exact-hash server scenario evidence')
  assert(step.includes('@sample-load="loadReadOnlySample"'),
    'sample loading must be a one-click search, select and hydrate action')
  assert(step.includes('await this.hydratePayload()'),
    'one-click sample loading must hydrate the selected sample')
  assert(selector.includes('scenarioTargetLabel'),
    'scenario target codes must be rendered with the governed template name')
  assert(gate.includes('reasonLabel'),
    'scenario blockers must explain whether evidence is missing, stale or failed')
  assert(form.includes('SemanticOptionSelector'), 'completion dictionaries must reuse governed semantic selectors')
  assert(form.includes('contactedAt'), 'simulation time must be available as the contactedAt default')
  assert(!form.includes('reviewResult'), 'TD-001 completion form must not hard-code downstream review fields')
  assert(gate.includes('批量验证三个场景'), 'batch gate must provide one clear action')
  const page = read('src/views/todo/config/journey/index.vue')
  const health = read('src/views/todo/config/journey/components/ConfigurationHealthPanel.vue')
  assert(page.includes('@readiness-change="applySimulationReadiness"'),
    'simulation readiness must synchronize the parent journey immediately')
  assert(step.includes("$emit('readiness-change'"),
    'the simulation step must emit authoritative readiness')
  assert(step.includes('模拟发布验证已全部通过'),
    'the full simulation must have one authoritative success message')
  assert(step.includes('完整试运行尚未通过'),
    'the full simulation blocker must be explained in Chinese')
  assert(health.includes('issueMessage(issue)'),
    'the health panel must localize legacy simulation blockers')
  for (const apiName of ['listJourneyScenarios', 'simulateJourneyScenario', 'batchSimulateJourneyScenarios']) {
    assert(api.includes(`function ${apiName}`), `missing scenario API: ${apiName}`)
  }
})

check('activates the four lead templates as one server-governed release', () => {
  const step = read('src/views/todo/config/journey/steps/SimulationPublishStep.vue')
  const health = read('src/views/todo/config/journey/components/ConfigurationHealthPanel.vue')
  const api = read('src/api/todo-config.js')
  for (const apiName of ['getLeadReleaseReadiness', 'activateLeadRelease']) {
    assert(api.includes(`function ${apiName}`), `missing coordinated lead release API: ${apiName}`)
  }
  for (const label of ['当前入口', '线索已分配', '下游版本', '疑似无效主管复核', '无法联系重试', '5天实质进展']) {
    assert(step.includes(label), `lead release panel missing Chinese label: ${label}`)
  }
  assert(step.includes('releaseReadiness.activationReady'),
    'activation must be disabled by backend release readiness rather than client hash comparisons')
  assert(step.includes('getLeadReleaseReadiness'),
    'active binding and exact four-version evidence must come from the server')
  assert(step.includes("this.$confirm('将同时启用首联入口和三个下游版本"),
    'coordinated activation must require an explicit confirmation')
  assert(step.includes("this.$emit('published'"),
    'successful activation must refresh the journey and its template workbench')
  assert(health.includes('TODO_LEAD_RELEASE_EVIDENCE_INCOMPLETE'),
    'release evidence blockers must have a localized health-panel explanation')
})

check('shows authoritative cross-step impact and keeps the active narrow-screen step visible', () => {
  const page = read('src/views/todo/config/journey/index.vue')
  const nav = read('src/views/todo/config/journey/components/JourneyStepNav.vue')
  const health = read('src/views/todo/config/journey/components/ConfigurationHealthPanel.vue')
  assert(page.includes('journey-impact-banner'),
    'the journey must expose the server-computed impact after saving')
  assert(page.includes('synchronizeImpact'),
    'the impact banner must provide one synchronization action')
  assert(health.includes('服务端校验'),
    'the health panel must explain that saved health is authoritative')
  assert(nav.includes('scrollIntoView'),
    'the active narrow-screen step must be scrolled into view')
  assert(nav.includes('scroll-snap-type'),
    'the narrow-screen step rail must remain horizontally scrollable')
})

check('preserves pending owner choices until an explicit replacement is confirmed', () => {
  const page = read('src/views/todo/config/journey/index.vue')
  const owner = read('src/views/todo/config/journey/steps/OwnerStep.vue')
  assert(page.includes('journeyDraft'),
    'the seven-step shell must keep one deep-cloned draft keyed by template and version')
  assert(page.includes('@patch="onStepPatch"'),
    'journey child editors must send non-destructive patches')
  assert(owner.includes('confirmOwnerReplacement'),
    'owner source changes need an explicit confirmation boundary')
  assert(owner.includes("this.$emit('patch'"),
    'confirmed owner replacement must emit a patch instead of a destructive full value')
  assert(!owner.includes("this.$emit('patch', { owner: { config: {} } })"),
    'switching owner source must never emit an empty owner configuration')
})

check('renders governed effects and returns repair issues to exact coordinates', () => {
  const effects = read('src/views/todo/config/journey/business-effect-model.js')
  const routing = read('src/views/todo/config/journey/components/BusinessRoutingEditor.vue')
  const health = read('src/views/todo/config/journey/components/ConfigurationHealthPanel.vue')
  const simulation = read('src/views/todo/config/journey/steps/SimulationPublishStep.vue')
  const page = read('src/views/todo/config/journey/index.vue')
  for (const label of [
    '生成下一待办', '结束当前路径', '保留当前待办',
    '等待系统计划下一窗口', '完成后开启下一周期'
  ]) assert(effects.includes(label), `missing governed effect label: ${label}`)
  assert(routing.includes('effectPresentation'),
    'business routing must render the shared governed effect cards')
  assert(routing.includes('filteredRoutingTargets'),
    'business routing target choices must be business-domain filtered')
  assert(health.includes("$emit('repair', { ...issue })"),
    'configuration health must forward the complete readiness issue')
  assert(simulation.includes('...(issue || {})') &&
    simulation.includes("$emit('navigate-repair'") &&
    simulation.includes('resourceKey:') && simulation.includes('fieldPath:'),
  'simulation publish must forward the complete readiness issue and its coordinates')
  assert(page.includes('fixLocation(issue)'),
    'the shell must resolve fieldPath/resourceKey coordinates before navigating')
  assert(page.includes('editor.focusField(target.focusTarget, target.resourceKey)'),
    'the shell must pass the resolved resource key and exact control to the target step')
  assert(page.includes('location.openResourceDrawer') && page.includes('location.resourceType'),
    'governed configuration resources must route to their maintenance drawer when required')
})

check('renders retry-window and five-day SLA presets without generic destructive editing', () => {
  const sla = read('src/views/todo/config/journey/steps/SlaStep.vue')
  const model = read('src/views/todo/config/journey/journey-step-model.js')
  assert(sla.includes('retryWindowGroups') && sla.includes('config.schedule'),
    'TD-003 must render its governed schedule windows from the current draft')
  assert(model.includes('slaSchedulePresentation') && sla.includes('schedulePresentation.mode'),
    'SLA layout must classify the governed schedule before choosing retry or self-cycle presentation')
  assert(sla.includes('selfCycleMode') && sla.includes('sla-self-cycle'),
    'TD-004 windows must render a dedicated self-cycle presentation')
  for (const label of ['T0', 'T+1', 'T+2', '每 5 天循环']) {
    assert(sla.includes(label), `SLA preset explanation missing: ${label}`)
  }
  assert(sla.includes('v-if="!scheduleMode"'),
    'schedule-window drafts must not fall through to the generic duration editor')
})

check('focuses exact owner, DoD, and routing controls and opens advanced sections on demand', () => {
  const owner = read('src/views/todo/config/journey/steps/OwnerStep.vue')
  const dod = read('src/views/todo/config/journey/steps/DodStep.vue')
  const routing = read('src/views/todo/config/journey/steps/RoutingStep.vue')
  const businessRouting = read('src/views/todo/config/journey/components/BusinessRoutingEditor.vue')
  for (const target of ['ownerSelection', 'ownerFallbackType', 'ownerFallbackSelection']) {
    assert(owner.includes(`ref="${target}"`), `owner repair target missing precise ref: ${target}`)
  }
  assert(owner.includes('ownerFocusTarget(fieldPath, resourceKey)'),
    'owner repair must map coordinates to an exact control')
  for (const target of ['requiredFields', 'requiredAttachments', 'validatorRefs']) {
    assert(dod.includes(`ref="${target}"`), `DoD repair target missing precise ref: ${target}`)
  }
  assert(dod.includes('activeAdvanced') && dod.includes("this.activeAdvanced = ['advanced']"),
    'DoD repair must open advanced validation controls before focusing them')
  assert(routing.includes('activeAdvanced') && routing.includes("this.activeAdvanced = ['graph']"),
    'routing repair must open the topology section for node or edge coordinates')
  assert(businessRouting.includes('routingFocusTarget(fieldPath, resourceKey)'),
    'business routing repair must map result, effect, and target coordinates precisely')
})

check('opens only resolved referenced resources and falls back to visible precise repair context', () => {
  const page = read('src/views/todo/config/journey/index.vue')
  const model = read('src/views/todo/config/journey/journey-step-model.js')
  const drawer = read('src/views/todo/config/journey/components/ContextResourceDrawer.vue')
  assert(model.includes('resolveRepairResourceItem'),
    'referenced recipe and material identifiers must resolve from the journey resource catalog')
  assert(model.includes('request.item = item') && model.includes('clone(source.item)'),
    'context repair requests must preserve a safe deep-cloned resource item')
  assert(page.includes('resolveRepairResourceItem') && page.includes('resolvedItem'),
    'the journey shell must resolve resource identifiers before opening a drawer')
  assert(page.includes('if (resolvedItem)') && page.includes('this.repairContext = {'),
    'only a current catalog match may open the drawer; stale rich items must use repair context')
  assert(page.includes('repairContext') && page.includes('未找到需要修复的配置资源'),
    'missing referenced resources must leave visible repair context at the precise control')
  assert(page.includes('focusRepairControl(location)'),
    'missing referenced resources must focus the exact configured control')
  assert(drawer.includes('itemResource && request.item'),
    'the contextual drawer must never turn a missing referenced item into blank create state')
  assert(!model.includes('? clone(direct)'),
    'a rich issue item must never bypass the authoritative current resource catalog')
})

console.log(`todo phase two ux contract passed (${checks} checks)`)
