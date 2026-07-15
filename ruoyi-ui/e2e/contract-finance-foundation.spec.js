const { test, expect } = require('@playwright/test')

test('customer contract lifecycle and contract payment keep current api contracts', async ({ page }) => {
  const calls = []
  const browserErrors = []
  const state = businessState()
  await setupApp(page, state, calls, browserErrors)

  await page.goto('/customer/list?module=list')
  await expect(page.getByText('张三客户', { exact: true }).first()).toBeVisible()
  await page.getByRole('button', { name: '新建合同' }).last().click()
  const create = page.getByRole('dialog', { name: '新建合同' })
  await create.locator('.el-form-item').filter({ hasText: '合同名称' }).locator('input').fill('常年法律顾问合同')
  await chooseSelect(page, create, '案件类型', '民事')
  await create.locator('.el-form-item').filter({ hasText: '签约金额' }).locator('input').fill('100')
  await chooseSelect(page, create, '收费方式', '一次性')
  await chooseSelect(page, create, '签订方式', '线上')
  await chooseSelect(page, create, '风险等级', '低风险')
  await create.getByRole('button', { name: '确定' }).click()
  await expect.poll(() => calls.some(call => call.path === '/contract' && call.method === 'POST')).toBe(true)

  const contractRow = page.locator('.el-table__body tr').filter({ hasText: '常年法律顾问合同' }).first()
  await expect(contractRow).toBeVisible()
  await page.getByRole('button', { name: '提交审批' }).last().click()
  await expect.poll(() => calls.some(call => call.path === '/contract/submit/10')).toBe(true)

  await page.goto('/contract/approval?module=approval')
  const approvalRow = page.locator('.el-table__body tr').filter({ hasText: '常年法律顾问合同' }).first()
  await expect(approvalRow).toBeVisible()
  await page.getByRole('button', { name: '审批' }).last().click()
  const approval = page.getByRole('dialog', { name: '合同审批' })
  await approval.getByRole('textbox').fill('同意')
  await approval.getByRole('button', { name: '提交' }).click()
  await expect.poll(() => calls.find(call => call.path === '/contract/approval')?.body.opinion).toBe('同意')

  await page.goto('/contract/list?module=list')
  const signedRow = page.locator('.el-table__body tr').filter({ hasText: '常年法律顾问合同' }).first()
  await expect(signedRow).toBeVisible()
  await page.getByRole('button', { name: '签署' }).last().click()
  const signing = page.getByRole('dialog', { name: '合同签署' })
  await signing.getByText('已签订', { exact: true }).click()
  await signing.getByRole('button', { name: '确定' }).click()
  await expect.poll(() => calls.find(call => call.path === '/contract/sign')?.body.signStatus).toBe('1')

  await page.goto('/contract/fee?module=fee')
  const feeRow = page.locator('.el-table__body tr').filter({ hasText: '张三客户' }).first()
  await feeRow.getByRole('button', { name: '确认' }).click()
  const prompt = page.locator('.el-message-box')
  await prompt.locator('input').fill('100')
  await prompt.getByRole('button', { name: '确定' }).click()
  await expect.poll(() => calls.find(call => call.path === '/contract/fee/confirm')?.body.planId).toBe(21)

  expect(calls.map(call => call.path)).toEqual(expect.arrayContaining([
    '/contract', '/contract/submit/10', '/contract/approval', '/contract/sign', '/contract/fee/confirm'
  ]))
  expect(browserErrors).toEqual([])
})

test('finance page confirms the same fee plan through finance api', async ({ page }) => {
  const calls = []
  const browserErrors = []
  const state = businessState()
  state.contract.auditStatus = '2'
  state.contract.signStatus = '1'
  state.contract.contractStatus = '1'
  await setupApp(page, state, calls, browserErrors)

  await page.goto('/finance/payment?module=payment')
  const row = page.locator('.el-table__body tr').filter({ hasText: '张三客户' }).first()
  await expect(row).toBeVisible()
  await page.getByRole('button', { name: '确认' }).last().click()
  const dialog = page.getByRole('dialog', { name: '确认回款' })
  await dialog.getByRole('button', { name: '确认入账' }).click()

  await expect.poll(() => calls.find(call => call.path === '/finance/payment/confirm')?.body.planId).toBe(21)
  expect(calls.find(call => call.path === '/finance/payment/confirm').body.paymentMethod).toBe('bank')
  expect(browserErrors).toEqual([])
})

async function chooseSelect(page, dialog, label, option) {
  await dialog.locator('.el-form-item').filter({ hasText: label }).locator('input').click()
  await page.locator('.el-select-dropdown:visible').getByText(option, { exact: true }).click()
}

async function setupApp(page, state, calls, browserErrors) {
  page.on('pageerror', error => browserErrors.push(error.message))
  page.on('console', message => {
    if (message.type() === 'error') browserErrors.push(message.text())
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
    if (path === '/lead/setting/list') return json(route, [])
    if (path === '/customer/dashboard' || path === '/contract/dashboard' || path === '/finance/dashboard') return json(route, { cards: [] })
    if (path === '/customer/owner/options' || path === '/contract/owner/options') return json(route, [{ userId: 8, userName: 'alice', nickName: 'Alice' }])
    if (path.startsWith('/customer/list')) return json(route, null, { code: 200, rows: [state.customer], total: 1 })
    if (path.startsWith('/contract/list')) return json(route, null, { code: 200, rows: state.created ? [state.contract] : [], total: state.created ? 1 : 0 })
    if (path === '/contract' && method === 'POST') {
      calls.push(call(route, path)); state.created = true
      return json(route, {})
    }
    if (path === '/contract/submit/10') {
      calls.push(call(route, path)); state.contract.auditStatus = '1'
      return json(route, {})
    }
    if (path === '/contract/approval') {
      calls.push(call(route, path)); state.contract.auditStatus = '2'
      return json(route, {})
    }
    if (path === '/contract/sign') {
      calls.push(call(route, path)); state.contract.signStatus = '1'; state.contract.contractStatus = '1'
      return json(route, {})
    }
    if (path.startsWith('/contract/fee/list')) return json(route, null, { code: 200, rows: [state.contractFee], total: 1 })
    if (path === '/contract/fee/confirm') {
      calls.push(call(route, path)); state.contractFee.confirm_status = '1'
      return json(route, {})
    }
    if (path.startsWith('/contract/attachment/list') || path.startsWith('/contract/approval/list') || path.startsWith('/contract/status/list')) return json(route, null, { code: 200, rows: [], total: 0 })
    if (path.startsWith('/finance/payment/list')) return json(route, null, { code: 200, rows: [state.financeFee], total: 1 })
    if (path === '/finance/payment/confirm') {
      calls.push(call(route, path))
      return json(route, {})
    }
    return json(route, {})
  })
}

function call(route, path) {
  return { path, method: route.request().method(), body: route.request().postDataJSON() || {} }
}

function businessState() {
  return {
    created: false,
    customer: { customerId: 31, customerNo: 'KH031', customerName: '张三客户', customerType: 'personal', mobile: '13800000000', customerLevel: '2', ownerId: 8, ownerName: 'Alice', status: '0', contractCount: 0 },
    contract: { contractId: 10, contractNo: 'HT-10', contractName: '常年法律顾问合同', customerId: 31, customerName: '张三客户', caseType: 'civil', signAmount: 100, auditStatus: '0', signStatus: '0', contractStatus: '0', ownerName: 'Alice' },
    contractFee: { plan_id: 21, contract_id: 10, contractNo: 'HT-10', customerName: '张三客户', period_no: 1, receivable_amount: 100, received_amount: 0, plan_receive_date: '2026-08-01', confirm_status: '0', invoice_status: '0', contract_status: '1' },
    financeFee: { planId: 21, contractId: 10, contractNo: 'HT-10', customerName: '张三客户', receivableAmount: 100, receivedAmount: 0, pendingAmount: 100, planReceiveDate: '2026-08-01', confirmStatus: '0', invoiceStatus: '0', paymentMethod: 'bank' }
  }
}

function routes() {
  return [{ path: '/', component: 'Layout', children: [
    { path: 'customer/list', component: 'customer/index', name: 'CustomerContractE2E', meta: { title: '客户管理' } },
    { path: 'contract/list', component: 'contract/index', name: 'ContractListE2E', meta: { title: '合同管理' } },
    { path: 'contract/approval', component: 'contract/index', name: 'ContractApprovalE2E', meta: { title: '合同审批' } },
    { path: 'contract/fee', component: 'contract/index', name: 'ContractFeeE2E', meta: { title: '合同收费' } },
    { path: 'finance/payment', component: 'finance/index', name: 'FinancePaymentE2E', meta: { title: '回款确认' } }
  ] }]
}

function dict(type) {
  const values = {
    law_customer_type: [['personal', '个人']], law_customer_level: [['2', '普通']],
    law_contract_case_type: [['civil', '民事']], law_contract_fee_type: [['once', '一次性']],
    law_contract_sign_method: [['online', '线上']], law_contract_risk_level: [['1', '低风险']],
    law_contract_audit_status: [['0', '待提交'], ['1', '审核中'], ['2', '已通过']],
    law_contract_sign_status: [['0', '未签署'], ['1', '已签署'], ['2', '部分签署']],
    law_contract_status: [['0', '草稿'], ['1', '履约中']],
    law_contract_approval_action: [['pass', '通过'], ['reject', '拒绝'], ['back', '退回']],
    law_contract_receive_status: [['0', '待确认'], ['1', '已确认'], ['2', '已驳回']],
    law_contract_invoice_status: [['0', '未开票'], ['1', '已开票'], ['2', '部分开票']],
    law_finance_payment_method: [['bank', '银行转账']], sys_normal_disable: [['0', '正常'], ['1', '停用']]
  }
  return (values[type] || [['0', '默认']]).map(([dictValue, dictLabel], index) => ({ dictValue, dictLabel, listClass: 'default', isDefault: index === 0 ? 'Y' : 'N' }))
}

async function json(route, data, envelope) {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope || { code: 200, msg: 'success', data }) })
}
