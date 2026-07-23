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
const stylePath = 'src/views/todo/config/styles/config-center.scss'
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

console.log(`todo phase two ux contract passed (${checks} checks)`)
