const { test, expect } = require('@playwright/test')

test('case center submits assignment transfer approval and acceptance facts', async ({ page }) => {
  const calls = []
  const errors = []
  await setupCaseApp(page, calls, errors)

  await page.goto('/case/pending?module=pending')
  const pendingRow = page.locator('.el-table__body tr').filter({ hasText: 'CS-081' }).first()
  await expect(pendingRow).toBeVisible()
  await page.locator('button').filter({ hasText: /^分配$/ }).last().click()
  const assignDialog = page.getByRole('dialog', { name: '案件分配' })
  await assignDialog.locator('.el-form-item').filter({ hasText: '主办律师' }).locator('input').click()
  await page.locator('.el-select-dropdown:visible').getByText('律师甲', { exact: true }).click()
  await assignDialog.getByRole('button', { name: '确认分案' }).click()
  await expect.poll(() => calls.find(call => call.path === '/case/assign')?.body.mainLawyerId).toBe(12)

  await page.goto('/case/transfer?module=transfer')
  await expect(page.locator('.el-table__body tr').filter({ hasText: 'TR-111' }).first()).toBeVisible()
  await page.getByRole('button', { name: '发起转案' }).click()
  const transferDialog = page.getByRole('dialog', { name: '发起转案' })
  await transferDialog.locator('.el-form-item').filter({ hasText: '案件' }).locator('input').click()
  await page.locator('.el-select-dropdown:visible').getByText('办理中案件', { exact: true }).click()
  await transferDialog.locator('.el-form-item').filter({ hasText: '拟转入律师' }).locator('input').click()
  await page.locator('.el-select-dropdown:visible').getByText('律师乙', { exact: true }).click()
  await transferDialog.locator('.el-form-item').filter({ hasText: '转案详情' }).locator('textarea').fill('调整主办律师')
  await transferDialog.getByRole('button', { name: '提交', exact: true }).click()
  await expect.poll(() => calls.find(call => call.path === '/case/transfer')?.body).toMatchObject({
    caseId: 81,
    toLawyerId: 13
  })

  await page.locator('button').filter({ hasText: /^审批$/ }).last().click()
  await page.getByPlaceholder('请填写审批意见').fill('同意转案')
  await page.getByRole('button', { name: '提交审批' }).click()
  await expect.poll(() => calls.find(call => call.path === '/case/transfer/approve')?.body).toMatchObject({
    transferId: 111,
    action: 'passed'
  })

  await page.goto('/case/confirm?module=confirm')
  const confirmRow = page.locator('.el-table__body tr').filter({ hasText: '转案接收确认' }).first()
  await expect(confirmRow).toBeVisible()
  await page.locator('button').filter({ hasText: /^确认接收$/ }).last().click()
  await expect.poll(() => calls.find(call => call.path === '/case/confirm')?.body).toEqual({
    confirmId: 121,
    confirmResult: 'accepted',
    remark: '确认接收'
  })

  expect(errors).toEqual([])
})

async function setupCaseApp(page, calls, errors) {
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => {
    if (message.type() === 'error') errors.push(message.text())
  })
  await page.context().addCookies([{ name: 'Admin-Token', value: 'e2e-token', url: 'http://127.0.0.1:4173/' }])
  await page.addInitScript(() => { document.cookie = 'Admin-Token=e2e-token; path=/' })
  await page.route('**/prod-api/**', async route => {
    const url = new URL(route.request().url())
    const path = url.pathname.replace('/prod-api', '')
    const method = route.request().method()
    if (path === '/getInfo') return json(route, null, { code: 200, user: { userId: 1, userName: 'admin', nickName: '管理员', avatar: '' }, roles: ['admin'], permissions: ['*:*:*'] })
    if (path === '/getRouters') return json(route, routes())
    if (path.startsWith('/system/dict/data/type/')) return json(route, dict(path.split('/').pop()))
    if (path === '/system/config/configKey/sys.index.skinName') return json(route, '')
    if (path.startsWith('/system/notice/list')) return json(route, null, { code: 200, rows: [], total: 0 })
    if (path === '/case/dashboard') return json(route, { cards: [], lawyers: [], specialties: [] })
    if (path === '/case/lawyer/options') return json(route, lawyers())
    if (path.startsWith('/case/list')) {
      const row = url.searchParams.get('mode') === 'processing' ? processingCase() : pendingCase()
      return json(route, null, { code: 200, rows: [row], total: 1 })
    }
    if (path === '/case/assign' && method === 'POST') return capture(route, path, calls)
    if (path.startsWith('/case/transfer/list')) return json(route, null, { code: 200, rows: [transferRow()], total: 1 })
    if (path === '/case/transfer' && method === 'POST') return capture(route, path, calls)
    if (path === '/case/transfer/approve' && method === 'POST') return capture(route, path, calls)
    if (path.startsWith('/case/confirm/list')) return json(route, null, { code: 200, rows: [confirmRow()], total: 1 })
    if (path === '/case/confirm' && method === 'PUT') return capture(route, path, calls)
    return json(route, {})
  })
}

function capture(route, path, calls) {
  calls.push({ path, method: route.request().method(), body: route.request().postDataJSON() || {} })
  return json(route, {})
}

function routes() {
  return [{ path: '/', component: 'Layout', children: [
    { path: 'case/pending', component: 'case/index', name: 'CasePendingE2E', meta: { title: '待分案案件' } },
    { path: 'case/transfer', component: 'case/index', name: 'CaseTransferE2E', meta: { title: '转案审批' } },
    { path: 'case/confirm', component: 'case/index', name: 'CaseConfirmE2E', meta: { title: '接案确认' } }
  ] }]
}

function pendingCase() {
  return { case_id: 81, case_no: 'CS-081', case_name: '待分案案件', customer_name: '客户甲',
    case_type: 'civil', urgency: 'normal', contract_no: 'HT-10', case_status: 'pending',
    priority: 'medium', estimated_workload: 24, create_time: '2026-07-15 08:00:00' }
}

function processingCase() {
  return { ...pendingCase(), case_name: '办理中案件', case_status: 'processing',
    main_lawyer_id: 12, main_lawyer_name: '律师甲' }
}

function transferRow() {
  return { transfer_id: 111, transfer_no: 'TR-111', case_id: 81, caseNo: 'CS-081',
    caseName: '办理中案件', from_lawyer_id: 12, from_lawyer_name: '律师甲',
    to_lawyer_id: 13, to_lawyer_name: '律师乙', applicant_name: '律师甲',
    transfer_reason: 'capacity', risk_level: 'medium', detail: '调整主办律师',
    transfer_status: 'pending', current_node: '法务经理审批', create_time: '2026-07-15 09:00:00' }
}

function confirmRow() {
  return { confirm_id: 121, case_id: 81, caseNo: 'CS-081', caseName: '办理中案件',
    confirm_user_id: 13, confirm_user_name: '律师乙', content: '转案接收确认', confirm_status: 'pending' }
}

function lawyers() {
  return [
    { userId: 12, userName: 'lawyer12', nickName: '律师甲', lawyerRole: 'lawyer', assignEnabled: 'Y' },
    { userId: 13, userName: 'lawyer13', nickName: '律师乙', lawyerRole: 'lawyer', assignEnabled: 'Y' }
  ]
}

function dict(type) {
  const values = {
    law_case_type: [['civil', '民事']], law_case_urgency: [['normal', '普通']],
    law_case_status: [['pending', '待分案'], ['processing', '办理中']],
    law_case_priority: [['medium', '中']], law_case_assign_method: [['manual', '人工分配']],
    law_case_assign_reason: [['normal', '常规分配']], law_lawyer_role: [['lawyer', '律师']],
    law_lawyer_match_level: [['high', '高']], law_lawyer_load_status: [['normal', '正常']],
    law_case_transfer_status: [['pending', '待审批'], ['passed', '已通过']],
    law_case_transfer_reason: [['capacity', '工作调整']], law_case_risk_level: [['medium', '中风险']],
    law_case_status_action: [['assign', '分案']],
    law_case_confirm_status: [['pending', '待确认'], ['accepted', '已确认']]
  }
  return (values[type] || [['0', '默认']]).map(([dictValue, dictLabel], index) => ({
    dictValue, dictLabel, listClass: 'default', isDefault: index === 0 ? 'Y' : 'N'
  }))
}

async function json(route, data, envelope) {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope || { code: 200, msg: 'success', data }) })
}
