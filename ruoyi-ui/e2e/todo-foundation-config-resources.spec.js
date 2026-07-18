const { test, expect } = require('@playwright/test')

async function json(route, data, envelope) {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope || { code: 200, msg: 'success', data }) })
}

async function choose(page, formItem, label) {
  await formItem.locator('input').click()
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').getByText(label, { exact: true }).click()
}

async function setupConfig(page) {
  const state = { triggers: [], calendars: [], decisions: [], failNextTrigger: false, decisionUpdates: 0 }
  await page.context().addCookies([{ name: 'Admin-Token', value: 'e2e-token', url: 'http://127.0.0.1:4173/' }])
  await page.addInitScript(() => { document.cookie = 'Admin-Token=e2e-token; path=/' })
  await page.route('**/prod-api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/prod-api', '')
    const body = request.postDataJSON ? request.postDataJSON() : {}
    if (path === '/getInfo') return json(route, null, { code: 200, user: { userId: 1, userName: 'admin', nickName: 'admin', avatar: '' }, roles: ['admin'], permissions: ['*:*:*'] })
    if (path === '/getRouters') return json(route, [{ path: '/', component: 'Layout', children: [{ path: 'todo/config', component: 'todo/config/index', name: 'TodoConfig', meta: { title: '待办配置', icon: 'clipboard' } }] }])
    if (path === '/todo/template' && request.method() === 'GET') return json(route, [{ template_id: 1, template_name: '线索跟进', template_code: 'LEAD_FOLLOWUP' }])
    if (path === '/todo/event-catalog') return json(route, [{ event_type: 'LEAD_ASSIGNED', payload_version: 1, status: 'ACTIVE' }])
    if (path === '/todo/template/1/versions') return json(route, [{ version_id: 10, version_no: 1, status: 'PUBLISHED' }])
    if (path === '/todo/template/trigger') {
      if (request.method() === 'GET') return json(route, state.triggers)
      if (state.failNextTrigger) { state.failNextTrigger = false; return json(route, null, { code: 500, msg: 'stale trigger version', data: null }) }
      const id = body.triggerRuleId || state.triggers.length + 1
      const previous = state.triggers.find(row => row.trigger_rule_id === id)
      const item = {
        ...body,
        trigger_rule_id: id, triggerRuleId: id,
        event_type: body.eventType, payload_version: body.payloadVersion,
        template_id: body.templateId, template_version_id: body.templateVersionId,
        template_name: '线索跟进', business_type: body.businessType,
        condition_json: body.conditionJson, enabled: body.enabled,
        version: previous ? previous.version + 1 : 0
      }
      state.triggers = state.triggers.filter(row => row.trigger_rule_id !== id).concat(item)
      return json(route, item)
    }
    if (path === '/todo/calendar') {
      if (request.method() === 'GET') return json(route, state.calendars)
      const id = body.calendarId || state.calendars.length + 1
      const previous = state.calendars.find(row => row.calendar_id === id)
      const item = {
        ...body,
        calendar_id: id, calendarId: id,
        calendar_code: body.calendarCode, calendar_name: body.calendarName,
        timezone: body.timezone, work_days: body.workDays,
        work_start: body.workStart, work_end: body.workEnd,
        exception_json: body.exceptionJson, status: body.status,
        version: previous ? previous.version + 1 : 0
      }
      state.calendars = state.calendars.filter(row => row.calendar_id !== id).concat(item)
      return json(route, item)
    }
    if (path === '/todo/decisions') {
      if (request.method() === 'GET') return json(route, state.decisions)
      const item = { ...body, decisionId: state.decisions.length + 1, version: 0, impactedTemplateCodes: ['TD-001', 'TD-002'] }
      state.decisions.push(item)
      return json(route, item)
    }
    if (/^\/todo\/decisions\/\d+$/.test(path)) {
      state.decisionUpdates++
      const id = Number(path.split('/').pop())
      state.decisions = state.decisions.map(row => row.decisionId === id ? { ...row, ...body, version: row.version + 1 } : row)
      return json(route, state.decisions.find(row => row.decisionId === id))
    }
    if (path.startsWith('/system/dict/data/type/') || path === '/system/config/configKey/sys.index.skinName') return json(route, [])
    return json(route, {})
  })
  await page.goto('/todo/config')
  await page.getByRole('tab', { name: '触发规则' }).waitFor()
  return state
}

test('trigger rules use the active catalog and refresh create, edit and toggle state', async ({ page }) => {
  const state = await setupConfig(page)
  await page.getByRole('tab', { name: '触发规则' }).click()
  await page.getByRole('button', { name: '新增触发规则' }).click()
  let dialog = page.getByRole('dialog')
  let items = dialog.locator('.el-form-item')
  await choose(page, items.nth(0), '线索跟进')
  await choose(page, items.nth(1), 'v1')
  await items.nth(2).locator('input').fill('LEAD')
  await choose(page, items.nth(4), 'LEAD_ASSIGNED')
  await expect(items.nth(5).locator('input')).toHaveValue('1')
  await dialog.getByRole('button', { name: '保存' }).click()
  const pane = page.locator('.el-tab-pane:not([aria-hidden="true"])')
  await expect(pane.locator('.el-table').getByText('LEAD_ASSIGNED', { exact: true })).toBeVisible()
  expect(state.triggers[0].actionId).toBeTruthy()
  expect(state.triggers[0].expectedVersion).toBe(0)

  await page.getByRole('button', { name: '编辑' }).click()
  dialog = page.getByRole('dialog'); items = dialog.locator('.el-form-item')
  await items.nth(2).locator('input').fill('LEAD_EDITED')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('LEAD_EDITED', { exact: true })).toBeVisible()
  expect(state.triggers[0].version).toBe(1)

  let toggle = pane.locator('.el-table .el-switch').first()
  state.failNextTrigger = true
  await toggle.click()
  await expect.poll(() => state.triggers[0].enabled).toBe('Y')
  await expect(toggle).toHaveClass(/is-checked/)
  await toggle.click()
  await expect.poll(() => state.triggers[0].enabled).toBe('N')

  await page.reload(); await page.getByRole('tab', { name: '触发规则' }).click()
  toggle = page.locator('.el-tab-pane:not([aria-hidden="true"]) .el-table .el-switch').first()
  await expect(toggle).not.toHaveClass(/is-checked/)
})

test('calendar edit and status survive a server reload', async ({ page }) => {
  const state = await setupConfig(page)
  await page.getByRole('tab', { name: '工作日历' }).click()
  await page.getByRole('button', { name: '新增日历' }).click()
  let dialog = page.getByRole('dialog'); let items = dialog.locator('.el-form-item')
  await items.nth(0).locator('input').fill('CN_TEST')
  await items.nth(1).locator('input').fill('中国日历')
  await dialog.getByRole('button', { name: '保存' }).click()
  expect(state.calendars[0].actionId).toBeTruthy()

  await page.getByRole('button', { name: '编辑' }).click()
  dialog = page.getByRole('dialog'); items = dialog.locator('.el-form-item')
  await items.nth(1).locator('input').fill('中国日历-停用')
  await items.nth(3).locator('.el-switch').click()
  await dialog.getByRole('button', { name: '保存' }).click()
  expect(state.calendars[0].status).toBe('1')
  expect(state.calendars[0].version).toBe(1)

  await page.reload(); await page.getByRole('tab', { name: '工作日历' }).click()
  await expect(page.getByText('中国日历-停用', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '编辑' }).click()
  await expect(page.getByRole('dialog').locator('.el-form-item').nth(3).locator('.el-switch')).not.toHaveClass(/is-checked/)
})

test('decision requires a conclusion and displays impacted template codes', async ({ page }) => {
  const state = await setupConfig(page)
  await page.getByRole('tab', { name: '决策登记' }).click()
  await page.getByRole('button', { name: '新增决策' }).click()
  let dialog = page.getByRole('dialog'); let items = dialog.locator('.el-form-item')
  await items.nth(0).locator('input').fill('DECISION_01')
  await items.nth(1).locator('input').fill('确认负责人')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('TD-001, TD-002', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: '编辑' }).click()
  dialog = page.getByRole('dialog'); items = dialog.locator('.el-form-item')
  await choose(page, items.nth(4), 'RESOLVED')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('conclusion is required', { exact: true })).toBeVisible()
  expect(state.decisionUpdates).toBe(0)

  await items.nth(5).locator('input').fill('已确认')
  await items.nth(6).locator('input').fill('通知团队')
  await dialog.getByRole('button', { name: '保存' }).click()
  expect(state.decisionUpdates).toBe(1)
  await expect(page.getByText('TD-001, TD-002', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: '重新打开' }).click()
  await page.getByRole('dialog').getByRole('button', { name: '保存' }).click()
  await expect(page.locator('.el-tab-pane:not([aria-hidden="true"])').getByText('OPEN', { exact: true })).toBeVisible()
})
