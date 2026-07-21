const { test, expect } = require('@playwright/test')
const { execFileSync } = require('node:child_process')

const realBackend = process.env.TODO_E2E_REAL_BACKEND === 'true'
const password = process.env.TODO_CONFIG_E2E_PASSWORD
const templateCode = `E2E_TODO_CONFIG_${Date.now()}`

test.describe('Todo configuration centre with real backend', () => {
  test.skip(!realBackend, 'requires the disposable real-backend E2E environment')

  test.afterAll(() => cleanupTodoConfiguration(templateCode))

  test('creates, simulates, publishes, and reviews a template through drawers', async ({ page }) => {
    await loginAs(page, 'todo_config_admin', password)
    await page.goto('/todo-template')

    await page.getByRole('button', { name: '新建模板' }).click()
    const drawer = page.locator('.el-drawer__wrapper:visible')
    await expect(drawer).toBeVisible()

    await fillTextField(drawer, '模板编码', templateCode)
    await fillTextField(drawer, '模板名称', '验收首联待办')
    await selectOption(page, drawer, '业务类型', 'Lead')
    await selectOption(page, drawer, '业务阶段', 'Lead')
    await selectOption(page, drawer, '模板类型', 'Standard')
    await selectOption(page, drawer, '默认优先级', '普通')
    await fillTextField(drawer, '变更摘要', '真实后端 E2E 首次发布')

    await goStep(drawer, '触发条件')
    await selectOption(page, drawer, '触发事件', 'LEAD_ASSIGNED · v1')

    await goStep(drawer, '负责人')
    await selectOption(page, drawer, '负责人规则', 'Business owner')

    await goStep(drawer, 'SLA')
    await selectOption(page, drawer, 'SLA 规则', 'E2E_SLA_FIRST_CONTACT_30M')

    await goStep(drawer, '完成条件')
    await selectOption(page, drawer, 'DoD 规则', 'E2E_DOD_FIRST_CONTACT')

    await drawer.getByRole('button', { name: '保存草稿' }).click()
    await expect(page.getByText('模板草稿已保存')).toBeVisible()
    await drawer.getByRole('button', { name: '发布预检' }).click()
    await expect(page.getByText('发布预检通过')).toBeVisible()

    await goStep(drawer, '模拟')
    await drawer.locator('.el-form-item').filter({ hasText: '业务对象 ID' }).locator('input').fill(await businessLeadId())
    await drawer.locator('.el-form-item').filter({ hasText: '事件载荷 JSON' }).locator('textarea').fill('{"ownerId":1,"source":"todo-config-real-e2e"}')
    await drawer.getByRole('button', { name: '运行真实模拟' }).click()
    await expect(drawer.getByText('模拟结果', { exact: true })).toBeVisible()

    await drawer.getByRole('button', { name: '发布', exact: true }).click()
    await expect(page.getByText('模板已发布')).toBeVisible()

    await page.goto('/todo-release-record')
    await expect(page.getByText(templateCode, { exact: true })).toBeVisible()
    await page.getByText(templateCode, { exact: true }).click()
    await expect(page.locator('.el-drawer__wrapper:visible')).toContainText('版本详情')
  })
})

async function loginAs(page, username, secret) {
  if (!secret) throw new Error('TODO_CONFIG_E2E_PASSWORD is required')
  await page.goto('/login')
  await page.locator('input[name="username"]').fill(username)
  await page.locator('input[name="password"]').fill(secret)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).not.toHaveURL(/\/login(?:\?|$)/)
}

async function fillTextField(scope, label, value) {
  const item = scope.locator('.el-form-item').filter({ hasText: label }).first()
  await item.locator('input,textarea').first().fill(value)
}

async function selectOption(page, scope, label, option) {
  const item = scope.locator('.el-form-item').filter({ hasText: label }).first()
  await item.locator('.el-select').first().click()
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: option }).first().click()
}

async function goStep(drawer, title) {
  await drawer.locator('.el-step__title').filter({ hasText: title }).first().click()
}

async function businessLeadId() {
  return process.env.TODO_CONFIG_E2E_BUSINESS_ID || '1'
}

function cleanupTodoConfiguration(code) {
  if (!realBackend || !code) return
  const database = process.env.TODO_E2E_DB_NAME || 'law_todo_config_e2e'
  const sql = `
    set @template_id=(select template_id from todo_template where template_code='${sqlLiteral(code)}' limit 1);
    delete from todo_simulation_record where template_version_id in (select version_id from todo_template_version where template_id=@template_id);
    delete from todo_template_draft_rule_ref where version_id in (select version_id from todo_template_version where template_id=@template_id);
    delete from todo_trigger_rule where template_id=@template_id or template_version_id in (select version_id from todo_template_version where template_id=@template_id);
    delete from todo_definition_action where operator_id=(select user_id from sys_user where user_name='todo_config_admin' and del_flag='0' limit 1);
    delete from todo_template_version where template_id=@template_id;
    delete from todo_template where template_id=@template_id;
    delete from todo_sla_rule where rule_code='E2E_SLA_FIRST_CONTACT_30M';
    delete from todo_dod_rule where rule_code='E2E_DOD_FIRST_CONTACT';
  `
  execFileSync('mysql', ['--protocol=tcp', `-h${process.env.TODO_E2E_DB_HOST || '127.0.0.1'}`, `-P${process.env.TODO_E2E_DB_PORT || '3306'}`, `-u${process.env.TODO_E2E_DB_USER || 'root'}`, database], {
    env: { ...process.env, MYSQL_PWD: process.env.TODO_E2E_DB_PASSWORD || 'root' },
    input: sql,
    stdio: ['pipe', 'inherit', 'inherit']
  })
}

function sqlLiteral(value) {
  return String(value).replace(/'/g, "''")
}
