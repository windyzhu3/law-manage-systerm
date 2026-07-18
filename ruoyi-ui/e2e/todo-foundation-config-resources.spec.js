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
  await page.getByLabel('模板').click(); await page.getByText('线索跟进', { exact: true }).last().click(); await page.getByLabel('模板版本').fill('1'); await page.getByLabel('业务类型').fill('LEAD')
  await page.getByLabel('触发事件').locator('input').first().fill('LEAD_ASSIGNED'); await page.getByRole('button', { name: '保存' }).click(); await expect(page.getByText('LEAD_ASSIGNED', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '编辑' }).click(); await page.getByLabel('业务类型').fill('LEAD_EDITED'); await page.getByRole('button', { name: '保存' }).click(); await expect(page.getByText('LEAD_EDITED', { exact: true })).toBeVisible()
})

test('calendar edits survive reload', async ({ page }) => {
  await setupConfig(page); await page.getByRole('tab', { name: '工作日历' }).click(); await page.getByRole('button', { name: '新增日历' }).click(); await page.getByLabel('日历编码').fill('CN_TEST'); await page.getByLabel('日历名称').fill('中国日历'); await page.getByRole('button', { name: '保存' }).click(); await page.reload(); await expect(page.getByText('CN_TEST', { exact: true })).toBeVisible()
})

test('decision create, resolve and reopen preserves server state', async ({ page }) => {
  await setupConfig(page); await page.getByRole('tab', { name: '决策登记' }).click(); await page.getByRole('button', { name: '新增决策' }).click(); await page.getByLabel('编码').fill('DECISION_01'); await page.getByLabel('标题').fill('确认负责人'); await page.getByRole('button', { name: '保存' }).click(); await page.getByRole('button', { name: '编辑' }).click(); await page.getByLabel('状态').click(); await page.getByText('RESOLVED', { exact: true }).last().click(); await page.getByLabel('结论').fill('已确认'); await page.getByLabel('处理方案').fill('通知团队'); await page.getByRole('button', { name: '保存' }).click(); await page.getByRole('button', { name: '重新打开' }).click(); await page.getByRole('button', { name: '保存' }).click(); await expect(page.getByText('OPEN', { exact: true })).toBeVisible()
})
