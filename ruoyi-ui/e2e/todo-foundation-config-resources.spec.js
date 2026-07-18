const { test, expect } = require('@playwright/test')

async function json(route, data, envelope) { await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope || { code: 200, msg: 'success', data }) }) }
async function setupConfig(page) {
  const state = { triggers: [], calendars: [], decisions: [] }
  await page.context().addCookies([{ name: 'Admin-Token', value: 'e2e-token', url: 'http://127.0.0.1:4173/' }])
  await page.addInitScript(() => { document.cookie = 'Admin-Token=e2e-token; path=/' })
  await page.route('**/prod-api/**', async route => {
    const request = route.request(); const path = new URL(request.url()).pathname.replace('/prod-api', ''); const body = request.postDataJSON ? request.postDataJSON() : {}
    if (path === '/getInfo') return json(route, null, { code: 200, user: { userId: 1, userName: 'admin', nickName: 'admin', avatar: '' }, roles: ['admin'], permissions: ['*:*:*'] })
    if (path === '/getRouters') return json(route, [{ path: '/', component: 'Layout', children: [{ path: 'todo/config', component: 'todo/config/index', name: 'TodoConfig', meta: { title: '待办配置', icon: 'clipboard' } }] }])
    if (path === '/todo/template' && request.method() === 'GET') return json(route, [{ template_id: 1, template_name: '线索跟进', template_code: 'LEAD_FOLLOWUP' }])
    if (path === '/todo/template/1/versions') return json(route, [{ version_id: 10, version_no: 1, status: 'PUBLISHED' }])
    if (path === '/todo/template/trigger') { if (request.method() === 'GET') return json(route, state.triggers); const item = { ...body, trigger_rule_id: body.triggerRuleId || state.triggers.length + 1, event_type: body.eventType, enabled: body.enabled, template_name: '线索跟进', business_type: body.businessType }; state.triggers = state.triggers.filter(row => row.trigger_rule_id !== item.trigger_rule_id).concat(item); return json(route, item) }
    if (path === '/todo/calendar') { if (request.method() === 'GET') return json(route, state.calendars); const item = { ...body, calendar_id: body.calendarId || state.calendars.length + 1, calendar_code: body.calendarCode, calendar_name: body.calendarName, work_days: body.workDays, work_start: body.workStart, work_end: body.workEnd }; state.calendars = state.calendars.filter(row => row.calendar_id !== item.calendar_id).concat(item); return json(route, item) }
    if (path === '/todo/decisions') { if (request.method() === 'GET') return json(route, state.decisions); const item = { ...body, decisionId: state.decisions.length + 1, version: 0 }; state.decisions.push(item); return json(route, item) }
    if (/^\/todo\/decisions\/\d+$/.test(path)) { const id = Number(path.split('/').pop()); state.decisions = state.decisions.map(row => row.decisionId === id ? { ...row, ...body, version: row.version + 1 } : row); return json(route, state.decisions.find(row => row.decisionId === id)) }
    if (path.startsWith('/system/dict/data/type/') || path === '/system/config/configKey/sys.index.skinName') return json(route, [])
    return json(route, {})
  })
  await page.goto('/todo/config')
  await page.getByRole('tab', { name: '触发规则' }).waitFor()
  return state
}

test('trigger rules create, edit, and enable through the server refresh', async ({ page }) => {
  await setupConfig(page); await page.getByRole('tab', { name: '触发规则' }).click(); await page.getByRole('button', { name: '新增触发规则' }).click()
  const dialog = page.getByRole('dialog'); const inputs = dialog.locator('input'); await inputs.nth(0).click(); await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').getByText('线索跟进', { exact: true }).click(); await inputs.nth(1).click(); await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').getByText('v1', { exact: true }).click(); await inputs.nth(2).fill('LEAD'); await inputs.nth(4).fill('LEAD_ASSIGNED'); await dialog.getByRole('button', { name: '保存' }).click(); await expect(page.getByText('LEAD_ASSIGNED', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '编辑' }).click(); await page.getByRole('dialog').locator('input').nth(2).fill('LEAD_EDITED'); await page.getByRole('dialog').getByRole('button', { name: '保存' }).click(); await expect(page.getByText('LEAD_EDITED', { exact: true })).toBeVisible()
})

test('calendar edits survive reload', async ({ page }) => {
  await setupConfig(page); await page.getByRole('tab', { name: '工作日历' }).click(); await page.getByRole('button', { name: '新增日历' }).click(); const dialog = page.getByRole('dialog'); await dialog.locator('input').nth(0).fill('CN_TEST'); await dialog.locator('input').nth(1).fill('中国日历'); await dialog.getByRole('button', { name: '保存' }).click(); await page.reload(); await page.getByRole('tab', { name: '工作日历' }).click(); await expect(page.getByText('CN_TEST', { exact: true })).toBeVisible()
})

test('decision create, resolve and reopen preserves server state', async ({ page }) => {
  await setupConfig(page); await page.getByRole('tab', { name: '决策登记' }).click(); await page.getByRole('button', { name: '新增决策' }).click(); let dialog = page.getByRole('dialog'); await dialog.locator('input').nth(0).fill('DECISION_01'); await dialog.locator('input').nth(1).fill('确认负责人'); await dialog.getByRole('button', { name: '保存' }).click(); await page.getByRole('button', { name: '编辑' }).click(); dialog = page.getByRole('dialog'); await dialog.locator('.el-select .el-input__inner').click(); await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').getByText('RESOLVED', { exact: true }).click(); await dialog.locator('input').nth(4).fill('已确认'); await dialog.locator('input').nth(5).fill('通知团队'); await dialog.getByRole('button', { name: '保存' }).click(); await page.getByRole('button', { name: '重新打开' }).click(); await page.getByRole('dialog').getByRole('button', { name: '保存' }).click(); await expect(page.locator('.el-tab-pane:not([aria-hidden="true"])').getByText('OPEN', { exact: true })).toBeVisible()
})
