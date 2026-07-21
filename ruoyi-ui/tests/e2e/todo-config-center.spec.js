const { test, expect } = require('@playwright/test')
const fs = require('fs')
const path = require('path')
const { cleanupTodoConfiguration, requireEnv } = require('./support/todo-config-e2e-database')

const realBackend = process.env.TODO_E2E_REAL_BACKEND === 'true'
const password = process.env.TODO_CONFIG_E2E_PASSWORD
const runMarker = realBackend ? requireEnv('TODO_CONFIG_E2E_RUN_MARKER') : 'source-contract'
const slaCode = realBackend ? requireEnv('TODO_CONFIG_E2E_SLA_CODE') : 'E2E_SLA_source_contract'
const dodCode = realBackend ? requireEnv('TODO_CONFIG_E2E_DOD_CODE') : 'E2E_DOD_source_contract'
const leadNo = realBackend ? requireEnv('TODO_CONFIG_E2E_LEAD_NO') : 'E2E_LEAD_source_contract'
const templateCode = `E2E_TODO_CONFIG_${runMarker}_${Date.now()}`
const screenshotDir = process.env.TODO_CONFIG_E2E_SCREENSHOT_DIR

test.use({ viewport: { width: 1672, height: 941 } })

test.describe('Todo configuration centre with real backend', () => {
  test.skip(!realBackend, 'requires the disposable real-backend E2E environment')

  test.afterAll(() => cleanupTodoConfiguration(templateCode))

  test('creates a preflight-valid TASK-to-END definition, simulates all sections, publishes, and reviews it', async ({ page }) => {
    await loginAs(page, 'todo_config_admin', password)
    await page.goto('/todo-engine/todo-template')

    await page.getByRole('button', { name: '新建模板' }).click()
    const drawer = page.locator('.el-drawer__wrapper:visible')
    await expect(drawer).toBeVisible()

    await fillTextField(drawer, '模板编码', templateCode)
    await fillTextField(drawer, '模板名称', '验收首联待办')
    await selectOption(page, drawer, '业务类型', '线索')
    await selectOption(page, drawer, '业务阶段', '线索')
    await selectOption(page, drawer, '模板类型', '标准模板')
    await selectOption(page, drawer, '默认优先级', '普通')
    await fillTextField(drawer, '变更摘要', `真实后端 E2E 首次发布 ${runMarker}`)
    await capture(page, '02-template-editor.png')

    await goStep(drawer, '触发条件')
    await selectOption(page, drawer, '触发事件', 'LEAD_ASSIGNED · v1')

    await goStep(drawer, '负责人')
    await selectOption(page, drawer, '负责人规则', '业务负责人')

    await goStep(drawer, 'SLA')
    await selectOption(page, drawer, 'SLA 规则', slaCode)

    await goStep(drawer, '完成条件')
    await selectOption(page, drawer, 'DoD 规则', dodCode)
    const uiFields = drawer.getByTestId('template-ui-fields')
    const uiFieldInput = uiFields.locator('input.el-select__input')
    await uiFieldInput.fill('contactResult')
    await uiFieldInput.press('Enter')
    await expect(uiFields).toContainText('contactResult')

    // The first save creates the aggregate/version ID required by the TASK start node.
    await drawer.getByRole('button', { name: '保存草稿' }).click()
    const firstSaveMessage = page.getByText('模板草稿已保存')
    await expect(firstSaveMessage).toBeVisible()
    await expect(firstSaveMessage).toBeHidden({ timeout: 10000 })

    await goStep(drawer, '路由')
    const routing = drawer.getByTestId('routing-graph-editor')
    await routing.getByTestId('routing-add-node').click()
    const nodeRows = routing.locator('.el-table').nth(0).locator('tbody tr')
    await expect(nodeRows.nth(0).locator('.el-select').nth(1).locator('input')).toHaveValue('当前定义版本（起点）')
    await routing.getByTestId('routing-add-node').click()
    await selectIn(page, nodeRows.nth(1).locator('.el-select').nth(0), 'END')
    await routing.getByRole('button', { name: '新增连线' }).click()
    const edgeRow = routing.locator('.el-table').nth(1).locator('tbody tr').first()
    await selectIn(page, edgeRow.locator('.el-select').nth(1), 'node_2')
    await expect(routing).toContainText('node_1')
    await expect(routing).toContainText('node_2')

    await drawer.getByRole('button', { name: '保存草稿' }).click()
    await expect(page.getByText('模板草稿已保存')).toBeVisible()
    const selectedSla = drawer.locator('.el-form-item').filter({ hasText: 'SLA 规则' }).first().locator('input').first()
    await expect(selectedSla).toHaveValue(new RegExp(slaCode))
    await expect(drawer).toContainText(dodCode)
    await drawer.getByRole('button', { name: '发布预检' }).click()
    await expect(page.getByText('发布预检通过')).toBeVisible()

    await goStep(drawer, '模拟')
    await expect(drawer.getByText('只读模拟，不创建真实待办', { exact: true })).toBeVisible()
    await drawer.locator('.el-form-item').filter({ hasText: '业务对象 ID' }).locator('input').fill(businessLeadId())
    await drawer.locator('.el-form-item').filter({ hasText: '事件载荷 JSON' }).locator('textarea').fill(`{"ownerId":1,"source":"todo-config-real-e2e","runMarker":"${runMarker}","leadNo":"${leadNo}"}`)
    await drawer.getByRole('button', { name: '运行真实模拟' }).click()
    for (const section of [
      '1. 状态变化',
      '2. 命中模板',
      '3. 负责人',
      '4. SLA',
      '5. DoD',
      '6. 下一步路由',
      '7. 待办卡片预览',
      '8. 技术日志'
    ]) await expect(drawer.getByText(section, { exact: true })).toBeVisible()
    await capture(page, '06-simulation.png')

    await drawer.getByRole('button', { name: '发布', exact: true }).click()
    await expect(page.getByText('模板已发布')).toBeVisible()

    await page.goto('/todo-engine/todo-template')
    await page.getByText(templateCode, { exact: true }).first().click()
    const templateDetailDrawer = page.locator('.el-drawer__wrapper:visible')
    await expect(templateDetailDrawer).toBeVisible()
    await expect(templateDetailDrawer.getByText('未绑定规则库', { exact: true })).toHaveCount(0)
    await capture(page, '01-template-center.png')

    const calendarResponsePromise = page.waitForResponse(response => response.request().method() === 'GET' && new URL(response.url()).pathname === '/prod-api/todo/config/template-catalog/calendars')
    await page.goto('/todo-engine/todo-sla-rule')
    await expectSuccessfulApiResponse(calendarResponsePromise)
    await page.getByText(slaCode, { exact: true }).first().click()
    await expect(page.locator('.el-drawer__wrapper:visible')).toBeVisible()
    await expectNoPermissionError(page)
    await capture(page, '03-sla-rules.png')

    const validatorResponsePromise = page.waitForResponse(response => response.request().method() === 'GET' && new URL(response.url()).pathname === '/prod-api/todo/config/template-catalog/validators')
    await page.goto('/todo-engine/todo-dod-rule')
    await expectSuccessfulApiResponse(validatorResponsePromise)
    await page.getByText(dodCode, { exact: true }).first().click()
    await expect(page.locator('.el-drawer__wrapper:visible')).toBeVisible()
    await expectNoPermissionError(page)
    await capture(page, '04-dod-rules.png')

    await page.goto('/todo-engine/todo-trigger-rule')
    const triggerRow = page.locator('.el-table__body-wrapper tbody tr').first()
    await expect(triggerRow).toBeVisible()
    await triggerRow.click()
    await expect(page.locator('.el-drawer__wrapper:visible')).toBeVisible()
    await capture(page, '05-trigger-rules.png')

    await page.goto('/todo-engine/todo-release-record')
    await expect(page.getByText(templateCode, { exact: true })).toBeVisible()
    await page.getByText(templateCode, { exact: true }).click()
    await expect(page.locator('.el-drawer__wrapper:visible')).toContainText('版本详情')
    await capture(page, '07-release-records.png')
  })
})

async function loginAs(page, username, secret) {
  if (!secret) throw new Error('TODO_CONFIG_E2E_PASSWORD is required')
  await page.goto('/login')
  await page.getByPlaceholder('请输入账号/手机号/邮箱').fill(username)
  await page.getByPlaceholder('请输入密码').fill(secret)
  await page.getByRole('button', { name: '登录系统' }).click()
  await expect(page).not.toHaveURL(/\/login(?:\?|$)/)
}

async function fillTextField(scope, label, value) {
  const item = scope.locator('.el-form-item').filter({ hasText: label }).first()
  await item.locator('input,textarea').first().fill(value)
}

async function selectOption(page, scope, label, option) {
  const item = scope.locator('.el-form-item').filter({ hasText: label }).first()
  await selectIn(page, item.locator('.el-select').first(), option)
}

async function selectIn(page, select, option) {
  await select.click()
  const dropdown = page.locator('.el-select-dropdown:visible').last()
  await expect(dropdown).toBeVisible()
  await dropdown.locator('.el-select-dropdown__item').filter({ hasText: option }).first().click()
}

async function goStep(drawer, title) {
  await drawer.locator('.el-step__title').filter({ hasText: title }).first().click()
}

function businessLeadId() {
  return requireEnv('TODO_CONFIG_E2E_BUSINESS_ID')
}

async function expectNoPermissionError(page) {
  await expect(page.getByText('当前操作没有权限', { exact: true })).toHaveCount(0)
}

async function expectSuccessfulApiResponse(responsePromise) {
  const response = await responsePromise
  expect(response.status()).toBe(200)
  expect((await response.json()).code).toBe(200)
}

async function capture(page, name) {
  if (!screenshotDir) return
  fs.mkdirSync(screenshotDir, { recursive: true })
  await page.evaluate(() => {
    window.scrollTo(0, 0)
    document.querySelectorAll('.app-main,.main-container,.el-scrollbar__wrap').forEach(element => { element.scrollTop = 0 })
  })
  await page.screenshot({ path: path.join(screenshotDir, name), fullPage: false, animations: 'disabled' })
}
