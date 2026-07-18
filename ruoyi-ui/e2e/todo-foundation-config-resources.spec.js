const { test, expect } = require('@playwright/test')

async function json(route, data, envelope) {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope || { code: 200, msg: 'success', data }) })
}

async function choose(page, formItem, label) {
  await formItem.locator('input').click()
  const dropdown = page.locator('.el-select-dropdown:visible').last()
  await dropdown.locator('.el-select-dropdown__item').getByText(label, { exact: true }).click()
  await expect(dropdown).toBeHidden()
}

function formItem(dialog, label) {
  return dialog.locator('.el-form-item').filter({ hasText: label }).first()
}

async function setupConfig(page) {
  const state = {
    triggers: [], calendars: [], decisions: [], failNextTrigger: false, decisionUpdates: 0, admissionUpdates: 0,
    admissionEvidence: [{ evidenceId: 1, evidenceCode: 'G02-DICTIONARY-ROLE', gateCode: 'G-02', title: '业务字典与稳定角色键', status: 'OPEN', deliveryPhase: 'PHASE_ONE', version: 0 }],
    foundationResources: {
      gateCode: 'G-02', total: 4, ready: 1, sourceUnresolved: 1, runtimeMissing: 1, runtimeIncomplete: 1, gateReady: false,
      resources: [
        { resourceId: 1, resourceType: 'DICTIONARY', resourceCode: 'law_business_line', domainCode: 'QUOTE_CONTRACT', deliveryPhase: 'PHASE_ONE', sourceStatus: 'CONFIRMED', expectedValuesJson: '[{"value":"NON_LITIGATION"},{"value":"COMPREHENSIVE"},{"value":"EXECUTION"}]', minimumActiveItems: 3, activeItemCount: 3, expectedItemCount: 3, readinessStatus: 'READY', sourceRef: 'doc/v0.2-prd-readiness-gap-analysis.md:185', remark: '仓库明确三条业务线稳定值' },
        { resourceId: 2, resourceType: 'DICTIONARY', resourceCode: 'law_lead_invalid_level', domainCode: 'LEAD_CUSTOMER', deliveryPhase: 'PHASE_ONE', sourceStatus: 'NEEDS_DECISION', decisionRef: 'Q-008', minimumActiveItems: 1, activeItemCount: 0, expectedItemCount: 0, readinessStatus: 'SOURCE_UNRESOLVED' },
        { resourceId: 3, resourceType: 'ROLE', resourceCode: 'sales', domainCode: 'CROSS_DOMAIN', deliveryPhase: 'PHASE_ONE', sourceStatus: 'CONFIRMED', minimumActiveItems: 1, activeItemCount: 0, expectedItemCount: 0, readinessStatus: 'RUNTIME_MISSING' },
        { resourceId: 4, resourceType: 'DICTIONARY', resourceCode: 'law_followup_progress_type', domainCode: 'LEAD_CUSTOMER', deliveryPhase: 'PHASE_ONE', sourceStatus: 'CONFIRMED', minimumActiveItems: 6, activeItemCount: 4, expectedItemCount: 6, readinessStatus: 'RUNTIME_INCOMPLETE' }
      ]
    }
  }
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
    if (path === '/todo/decision-governance-options') return json(route, {
      users: [{ user_id: 8, user_name: 'owner', nick_name: 'Owner', dept_name: '产品部' }],
      roles: [{ role_id: 3, role_key: 'product_owner', role_name: 'Product Owner' }],
      deliveryPhases: ['PHASE_ONE', 'PHASE_TWO', 'CROSS_PHASE']
    })
    if (path === '/todo/admission-evidence/governance-options') return json(route, {
      users: [
        { user_id: 7, user_name: 'owner', nick_name: 'Owner' },
        { user_id: 9, user_name: 'reviewer', nick_name: 'Reviewer' }
      ],
      statuses: ['OPEN', 'IN_REVIEW', 'APPROVED', 'REJECTED']
    })
    if (path === '/todo/admission-evidence' && request.method() === 'GET') return json(route, state.admissionEvidence)
    if (path === '/todo/foundation-resources') return json(route, state.foundationResources)
    if (/^\/todo\/admission-evidence\/\d+$/.test(path)) {
      state.admissionUpdates++
      const id = Number(path.split('/').pop())
      state.admissionEvidence = state.admissionEvidence.map(row => row.evidenceId === id ? {
        ...row, ...body, version: row.version + 1,
        ownerNickName: body.ownerUserId === 7 ? 'Owner' : '', reviewerNickName: body.reviewerUserId === 9 ? 'Reviewer' : ''
      } : row)
      return json(route, state.admissionEvidence.find(row => row.evidenceId === id))
    }
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
  let dialog = page.getByRole('dialog')
  await formItem(dialog, '编码').locator('input').fill('DECISION_01')
  await formItem(dialog, '标题').locator('input').fill('确认负责人')
  await choose(page, formItem(dialog, '负责人'), 'Owner（owner）')
  await choose(page, formItem(dialog, '责任角色'), 'Product Owner（product_owner）')
  await formItem(dialog, '截止时间').locator('input').fill('2026-07-31 18:00:00')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('TD-001, TD-002', { exact: true })).toBeVisible()
  expect(state.decisions[0].ownerUserId).toBe(8)
  expect(state.decisions[0].ownerRoleKey).toBe('product_owner')
  expect(state.decisions[0].deliveryPhase).toBe('PHASE_ONE')

  await page.getByRole('button', { name: '编辑' }).click()
  dialog = page.getByRole('dialog')
  await choose(page, formItem(dialog, '状态'), 'RESOLVED')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('conclusion is required', { exact: true })).toBeVisible()
  expect(state.decisionUpdates).toBe(0)

  await formItem(dialog, '结论').locator('input').fill('已确认')
  await formItem(dialog, '处理方案').locator('input').fill('通知团队')
  await dialog.getByRole('button', { name: '保存' }).click()
  expect(state.decisionUpdates).toBe(1)
  await expect(page.getByText('TD-001, TD-002', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: '重新打开' }).click()
  await page.getByRole('dialog').getByRole('button', { name: '保存' }).click()
  await expect(page.locator('.el-tab-pane:not([aria-hidden="true"])').getByText('OPEN', { exact: true })).toBeVisible()
})

test('admission evidence requires independent review and survives submit and approval reloads', async ({ page }) => {
  const state = await setupConfig(page)
  await page.getByRole('tab', { name: '准入证据' }).click()
  await expect(page.getByText('G02-DICTIONARY-ROLE', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '登记' }).click()
  let dialog = page.getByRole('dialog')
  await choose(page, formItem(dialog, '责任人'), 'Owner（owner）')
  await choose(page, formItem(dialog, '独立评审人'), 'Reviewer（reviewer）')
  await formItem(dialog, '截止时间').locator('input').fill('2026-07-31 18:00:00')
  await page.keyboard.press('Enter')
  await expect(page.locator('.el-picker-panel:visible')).toHaveCount(0)
  await choose(page, formItem(dialog, '状态'), 'IN_REVIEW')
  await formItem(dialog, '证据引用').locator('input').fill('repo://doc/g02.md')
  await formItem(dialog, '提交/评审结论').locator('textarea').fill('提交业务字典冻结清单')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(page.locator('.el-tab-pane:not([aria-hidden="true"])').getByText('IN_REVIEW', { exact: true })).toBeVisible()
  expect(state.admissionUpdates).toBe(1)
  expect(state.admissionEvidence[0].ownerUserId).toBe(7)
  expect(state.admissionEvidence[0].reviewerUserId).toBe(9)

  await page.getByRole('button', { name: '登记' }).click()
  dialog = page.getByRole('dialog')
  await choose(page, formItem(dialog, '状态'), 'APPROVED')
  await formItem(dialog, '提交/评审结论').locator('textarea').fill('独立评审通过')
  await dialog.getByRole('button', { name: '保存' }).click()
  expect(state.admissionUpdates).toBe(2)
  await page.reload(); await page.getByRole('tab', { name: '准入证据' }).click()
  await expect(page.locator('.el-tab-pane:not([aria-hidden="true"])').getByText('APPROVED', { exact: true })).toBeVisible()
  await expect(page.getByText('Owner', { exact: false }).first()).toBeVisible()
})

test('foundation resources expose repository source and runtime gaps without promoting values', async ({ page }) => {
  await setupConfig(page)
  await page.getByRole('tab', { name: '基础资源' }).click()
  const pane = page.locator('.el-tab-pane:not([aria-hidden="true"])')
  await expect(pane.getByText('G-02 资源门禁未就绪，不能批准准入证据')).toBeVisible()
  await expect(pane.getByText('law_business_line', { exact: false })).toBeVisible()
  await expect(pane.getByText('NON_LITIGATION、COMPREHENSIVE、EXECUTION', { exact: true })).toBeVisible()
  await expect(pane.getByText('Q-008', { exact: true })).toBeVisible()
  await expect(pane.getByText('来源待确认', { exact: true }).first()).toBeVisible()
  await expect(pane.getByText('运行态缺失', { exact: true }).first()).toBeVisible()
  await expect(pane.getByText('运行态不完整', { exact: true }).first()).toBeVisible()
})
