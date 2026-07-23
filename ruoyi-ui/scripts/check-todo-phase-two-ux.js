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

console.log(`todo phase two ux contract passed (${checks} checks)`)
