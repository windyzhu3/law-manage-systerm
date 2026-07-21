const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const specPath = path.join(root, 'tests/e2e/todo-config-center.spec.js')
const bootstrapPath = path.join(root, 'tests/e2e/bootstrap/todo-config-admin.sql')
const playwrightConfigPath = path.join(root, 'playwright.config.js')
const workflowPath = path.resolve(root, '../.github/workflows/ci.yml')

function read(file) {
  if (!fs.existsSync(file)) throw new Error(`Missing required real-E2E file: ${path.relative(root, file)}`)
  return fs.readFileSync(file, 'utf8')
}

function requireText(source, value, label) {
  if (!source.includes(value)) throw new Error(`${label} must include ${value}`)
}

const spec = read(specPath)
for (const forbidden of ['page.route(', 'route.fulfill(', 'e2e-token']) {
  if (spec.includes(forbidden)) throw new Error(`Todo configuration real E2E must not mock core APIs: ${forbidden}`)
}
for (const required of [
  "loginAs(page, 'todo_config_admin'",
  "page.goto('/todo-template')",
  '新建模板',
  '保存草稿',
  '运行真实模拟',
  '发布',
  "page.goto('/todo-release-record')",
  'cleanupTodoConfiguration'
]) requireText(spec, required, 'Todo configuration real E2E spec')

const bootstrap = read(bootstrapPath)
for (const required of [
  'todo_config_admin',
  'TODO_CONFIG_E2E_PASSWORD_HASH',
  'todo:template:list',
  'todo:trigger:list',
  'todo:sla-rule:list',
  'todo:dod-rule:list',
  'todo:simulation:simulate',
  'todo:release:list'
]) requireText(bootstrap, required, 'Test-only identity bootstrap')

const playwrightConfig = read(playwrightConfigPath)
requireText(playwrightConfig, 'tests/e2e/**/*.spec.js', 'Playwright configuration')
requireText(playwrightConfig, 'TODO_E2E_REAL_BACKEND', 'Playwright configuration')

const workflow = read(workflowPath)
for (const required of [
  "node-version: '20'",
  'character set utf8mb4 collate utf8mb4_unicode_ci',
  'todo-config-admin.sql',
  'TODO_CONFIG_E2E_PASSWORD_HASH',
  'password="$(openssl rand -hex 9)"',
  'TODO_E2E_REAL_BACKEND: true',
  'todo-config-center.spec.js'
]) requireText(workflow, required, 'CI workflow')

console.log('Todo configuration real-backend E2E source contract passed')
