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
}

runNegativeFixture()
check()
console.log('todo configuration center contract ok')

module.exports = { assertRouteContract, exportedFunctionBody, runNegativeFixture }
