const { test, expect, request: playwrightRequest } = require('@playwright/test')
const {
  WINDOW_CODES,
  createRunContext,
  verifyBackendIdentity,
  setupLeadTodoFixtures,
  cleanupLeadTodoFixtures,
  assertNoLeadTodoFixtures,
  leadState,
  evidenceNames,
  policySnapshot
} = require('./support/lead-todo-e2e-database')

const enabled = String(process.env.LEAD_TODO_E2E).toLowerCase() === 'true'
let fixtures
let runContext
let cleanupRequest
let verifiedRun
let setupAttempted = false

test.describe.serial('Lead Todo flow with real browser, API and cleanable MySQL fixtures', () => {
  test.skip(!enabled, 'Set LEAD_TODO_E2E=true and the disposable *_e2e database variables')

  test.beforeAll(async () => {
    runContext = createRunContext()
    let ready = false
    try {
      cleanupRequest = await authenticatedApiRequest(runContext.seller)
      verifiedRun = await verifyBackendIdentity(runContext, cleanupRequest)
      setupAttempted = true
      fixtures = setupLeadTodoFixtures(verifiedRun)
      ready = true
    } finally {
      if (!ready && verifiedRun && setupAttempted) {
        const cleanup = await cleanupLeadTodoFixtures(verifiedRun, { apiRequest: cleanupRequest })
        assertNoLeadTodoFixtures(runContext, cleanup)
      }
    }
  })

  test.afterAll(async () => {
    try {
      if (runContext && verifiedRun && setupAttempted) {
        const cleanup = await cleanupLeadTodoFixtures(verifiedRun, { apiRequest: cleanupRequest })
        assertNoLeadTodoFixtures(runContext, cleanup)
      }
    } finally {
      if (cleanupRequest) await cleanupRequest.dispose()
    }
  })

  test('VALID first contact fills all conditional fields, uploads CONTACT_PROOF and creates TD-004', async ({ page }) => {
    const lead = fixtures.leads.VALID
    await login(page, process.env.LEAD_SALES_USER)
    await openFirstContact(page, lead.leadNo)
    const drawer = page.getByTestId('lead-first-contact-drawer')
    await selectFormOption(page, drawer, '首联结果', '有效')
    await fillDateTime(drawer, '联系时间', '2026-07-26T09:30:00')
    await fillFormText(drawer, '姓名', '有效客户张先生')
    await fillFormText(drawer, '城市', '上海')
    await fillFormText(drawer, '诉求', '合同争议与损失追偿')
    await selectFormOption(page, drawer, '是否到所', '是')
    const fileName = `valid-contact-proof-${fixtures.marker}.txt`
    await uploadMaterial(drawer, fileName, 'VALID CONTACT PROOF')
    await expect(drawer.getByText(fileName, { exact: true })).toBeVisible()
    await drawer.getByTestId('lead-first-contact-submit').click()
    await expect(page.getByText('首联已完成，下一待办将由流程引擎生成')).toBeVisible()

    await expect.poll(() => leadState(lead.leadNo, runContext).firstContactResult).toBe('VALID')
    await expect.poll(() => leadState(lead.leadNo, runContext).td004Count).toBe(1)
    expect(evidenceNames(lead.leadNo, runContext)).toContain(fileName)
  })

  test('manual call saves the supplied filename as evidence and never fabricates a provider callback', async ({ page }) => {
    const lead = fixtures.leads.MANUAL
    await login(page, process.env.LEAD_SALES_USER)
    await openFirstContact(page, lead.leadNo)
    const drawer = page.getByTestId('lead-first-contact-drawer')
    await drawer.getByRole('button', { name: '补录通话' }).click()
    const dialog = page.getByRole('dialog', { name: '人工补录通话证据' })
    await expect(dialog.getByText('不会生成外呼供应商ID')).toBeVisible()
    await fillDateTime(dialog, '开始时间', '2026-07-26T10:00:00')
    await fillFormText(dialog, '通话结果', 'NO_ANSWER')
    await fillFormText(dialog, '人工说明', '用户未接听，人工补录真实拨号截图')
    const fileName = `manual-call-evidence-${fixtures.marker}.txt`
    await uploadMaterial(dialog, fileName, 'MANUAL CALL EVIDENCE')
    await expect(dialog.getByText(fileName, { exact: true })).toBeVisible()
    await dialog.getByRole('button', { name: '保存证据' }).click()
    await expect(page.getByText('通话证据已保存')).toBeVisible()

    await expect.poll(() => leadState(lead.leadNo, runContext).manualCalls).toBe(1)
    expect(evidenceNames(lead.leadNo, runContext)).toContain(fileName)
    await expect(drawer.getByText('人工补录', { exact: true }).first()).toBeVisible()
  })

  test('invalid review TRUE_INVALID is an independent fixture and enters Dead-Pool', async ({ page }) => {
    const lead = fixtures.leads.TRUE_INVALID
    await login(page, process.env.LEAD_SUPERVISOR_USER)
    await openReview(page, lead.leadNo)
    const dialog = page.getByRole('dialog', { name: '确认无效判断' })
    await dialog.getByText('确认无效', { exact: true }).click()
    await fillFormText(dialog, '复核意见', '证据完整，独立夹具确认无效')
    await dialog.getByTestId('invalid-review-submit').click()
    await expect(page.getByText('复核结论已提交')).toBeVisible()

    await expect.poll(() => leadState(lead.leadNo, runContext).invalidReviewStatus).toBe('CONFIRMED')
    await expect.poll(() => leadState(lead.leadNo, runContext).disposition).toBe('DEAD_POOL')
  })

  test('invalid review MISJUDGED_VALID is an independent fixture and reopens TD-001', async ({ page }) => {
    const lead = fixtures.leads.MISJUDGED
    await login(page, process.env.LEAD_SUPERVISOR_USER)
    await openReview(page, lead.leadNo)
    const dialog = page.getByRole('dialog', { name: '确认无效判断' })
    await dialog.getByText('误判有效', { exact: true }).click()
    await fillFormText(dialog, '复核意见', '复核证据证明是有效线索，重开首联')
    await dialog.getByTestId('invalid-review-submit').click()
    await expect(page.getByText('复核结论已提交')).toBeVisible()

    await expect.poll(() => leadState(lead.leadNo, runContext).invalidReviewStatus).toBe('MISJUDGED')
    await expect.poll(() => leadState(lead.leadNo, runContext).td001Open).toBe(1)
  })

  test('retry below the attempt limit retains the same TD-003 for multiple attempts', async ({ page }) => {
    const lead = fixtures.leads.RETAIN
    await login(page, process.env.LEAD_SALES_USER)
    await completeRetry(page, lead.leadNo, '下一窗口', `retain-1-${fixtures.marker}.txt`)
    await expect.poll(() => leadState(lead.leadNo, runContext).latestRetryResult).toBe('CONTINUE_CURRENT_WINDOW')
    await expect.poll(() => leadState(lead.leadNo, runContext).activeTodoCount).toBe(1)
    await completeRetry(page, lead.leadNo, '下一窗口', `retain-2-${fixtures.marker}.txt`)
    await expect.poll(() => leadState(lead.leadNo, runContext).retryFacts).toBe(2)
    expect(leadState(lead.leadNo, runContext)).toMatchObject({
      latestRetryResult: 'CONTINUE_CURRENT_WINDOW',
      retryStage: 'T0',
      activeTodoCount: 1
    })
  })

  test('retry at the limit advances the schedule but does not create the next-window Todo immediately', async ({ page }) => {
    const lead = fixtures.leads.NEXT
    await login(page, process.env.LEAD_SALES_USER)
    await completeRetry(page, lead.leadNo, '下一窗口', `next-window-${fixtures.marker}.txt`)
    await expect.poll(() => leadState(lead.leadNo, runContext).latestRetryResult).toBe('NEXT_WINDOW')
    expect(leadState(lead.leadNo, runContext)).toMatchObject({
      retryStage: 'T1_AM',
      activeTodoCount: 0,
      pendingWindows: 1
    })
  })

  test('retry CONNECTED cancels remaining windows and creates TD-004', async ({ page }) => {
    const lead = fixtures.leads.CONNECTED
    await login(page, process.env.LEAD_SALES_USER)
    await completeRetry(page, lead.leadNo, '已接通', `connected-${fixtures.marker}.txt`, {
      name: '接通客户李女士',
      city: '杭州',
      demand: '劳动争议调解',
      visited: '否'
    })
    await expect.poll(() => leadState(lead.leadNo, runContext).latestRetryResult).toBe('CONNECTED')
    await expect.poll(() => leadState(lead.leadNo, runContext).td004Count).toBe(1)
    expect(leadState(lead.leadNo, runContext).cancelledWindows).toBeGreaterThanOrEqual(1)
  })

  test('retry exhausted returns the lead to the public pool', async ({ page }) => {
    const lead = fixtures.leads.EXHAUSTED
    await login(page, process.env.LEAD_SALES_USER)
    await completeRetry(page, lead.leadNo, '已耗尽', `exhausted-${fixtures.marker}.txt`)
    await expect.poll(() => leadState(lead.leadNo, runContext).latestRetryResult).toBe('EXHAUSTED')
    expect(leadState(lead.leadNo, runContext)).toMatchObject({
      disposition: 'PUBLIC_POOL',
      retryStage: 'EXHAUSTED',
      activeTodoCount: 0
    })
  })

  test('Dead-Pool restore requires a reason, submits it and verifies the restored public-pool state', async ({ page }) => {
    const lead = fixtures.leads.DEAD_POOL
    await login(page, process.env.LEAD_SUPERVISOR_USER)
    await page.goto('/lead/dead-pool')
    await filterWorkbench(page, lead.leadNo)
    const row = tableRow(page, lead.leadNo)
    await expect(row.getByRole('button', { name: '领取' })).toHaveCount(0)
    await row.getByRole('button', { name: '恢复' }).click()
    const dialog = page.getByRole('dialog', { name: '恢复 Dead-Pool 线索' })
    await fillFormText(dialog, '恢复原因', '主管复核后确认可重新进入普通公海')
    await dialog.getByTestId('dead-pool-restore-submit').click()
    await expect(page.getByText('线索已恢复到公海')).toBeVisible()

    await expect.poll(() => leadState(lead.leadNo, runContext).disposition).toBe('PUBLIC_POOL')
    expect(leadState(lead.leadNo, runContext).restoreCount).toBe(1)
  })

  test('assignment policy persists all seven windows and deterministically recovers a concurrent write while preserving the draft', async ({ browser }) => {
    const leftContext = await browser.newContext()
    const rightContext = await browser.newContext()
    const left = await leftContext.newPage()
    const right = await rightContext.newPage()
    const leftAttempts = [4, 2, 3, 4, 5, 6, 7]
    const rightAttempts = [5, 3, 4, 5, 6, 7, 8]
    try {
      await Promise.all([
        login(left, process.env.LEAD_POLICY_ADMIN_USER),
        login(right, process.env.LEAD_POLICY_ADMIN_USER)
      ])
      await Promise.all([
        openPolicy(left, fixtures.policy.policyCode),
        openPolicy(right, fixtures.policy.policyCode)
      ])
      await setSevenWindowAttempts(left, leftAttempts)
      await setSevenWindowAttempts(right, rightAttempts)

      await left.getByTestId('policy-save-submit').click()
      await expect(left.getByText('轮转策略已保存')).toBeVisible()
      await expect.poll(() => policySnapshot(runContext).rowVersion).toBe(fixtures.policy.rowVersion + 1)
      expect(windowAttempts(policySnapshot(runContext))).toEqual(leftAttempts)

      // Deterministic concurrent write: the right editor still carries the old expectedVersion.
      await right.getByTestId('policy-save-submit').click()
      await expect(right.getByText(/服务器版本已更新为 v\d+/)).toBeVisible()
      await expect(right.getByText('草稿已完整保留')).toBeVisible()
      await expectSevenWindowAttempts(right, rightAttempts)

      await right.getByTestId('policy-conflict-keep-draft').click()
      await expectSevenWindowAttempts(right, rightAttempts)
      await right.getByTestId('policy-save-submit').click()
      await expect(right.getByText('轮转策略已保存')).toBeVisible()
      await expect.poll(() => policySnapshot(runContext).rowVersion).toBe(fixtures.policy.rowVersion + 2)
      expect(windowAttempts(policySnapshot(runContext))).toEqual(rightAttempts)

      await right.reload()
      await openPolicy(right, fixtures.policy.policyCode)
      await expectSevenWindowAttempts(right, rightAttempts)
    } finally {
      await leftContext.close()
      await rightContext.close()
    }
  })
})

async function authenticatedApiRequest(username) {
  const baseURL = process.env.TODO_E2E_BACKEND_URL || 'http://127.0.0.1:8080'
  const bootstrap = await playwrightRequest.newContext({ baseURL })
  try {
    const response = await bootstrap.post('/login', {
      data: {
        username,
        password: process.env.LEAD_TODO_E2E_PASSWORD || '',
        code: '',
        uuid: ''
      }
    })
    const body = await response.json().catch(() => ({}))
    if (!response.ok() || Number(body.code || 200) !== 200 || !body.token) {
      throw new Error(`Unable to authenticate Lead Todo E2E cleanup actor: ${body.msg || response.status()}`)
    }
    return playwrightRequest.newContext({
      baseURL,
      extraHTTPHeaders: { Authorization: `Bearer ${body.token}` }
    })
  } finally {
    await bootstrap.dispose()
  }
}

async function login(page, username) {
  await page.goto('/login')
  await page.getByPlaceholder('账号').fill(username || '')
  await page.getByPlaceholder('密码').fill(process.env.LEAD_TODO_E2E_PASSWORD || '')
  await Promise.all([
    page.waitForResponse(response => response.url().includes('/login') && response.request().method() === 'POST'),
    page.getByRole('button', { name: '登录' }).click()
  ])
  await expect(page).not.toHaveURL(/\/login(?:\?|$)/)
}

async function filterWorkbench(page, keyword) {
  const input = page.getByPlaceholder(/线索编号、名称、手机号/)
  await input.fill(keyword)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(tableRow(page, keyword)).toBeVisible()
}

function tableRow(page, keyword) {
  return page.getByTestId(`lead-row-${keyword}`).locator('xpath=ancestor::tr').first()
}

async function openFirstContact(page, leadNo) {
  await page.goto('/lead/mine?module=mine')
  const input = page.getByPlaceholder('搜索线索名称、单位')
  await input.fill(leadNo)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  const row = tableRow(page, leadNo)
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: '首联', exact: true }).click()
  await expect(page.getByTestId('lead-first-contact-drawer')).toBeVisible()
}

async function openReview(page, leadNo) {
  await page.goto('/lead/invalid-review')
  await filterWorkbench(page, leadNo)
  await tableRow(page, leadNo).getByRole('button', { name: '复核', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '确认无效判断' })).toBeVisible()
}

function formItem(scope, label) {
  return scope.locator('.el-form-item').filter({
    has: scope.locator('.el-form-item__label').filter({ hasText: new RegExp(`^${escapeRegex(label)}$`) })
  }).first()
}

async function fillFormText(scope, label, value) {
  const item = formItem(scope, label)
  await expect(item).toBeVisible()
  const control = item.locator('textarea:visible,input:visible').first()
  await control.fill(value)
}

async function fillDateTime(scope, label, value) {
  const input = formItem(scope, label).locator('input').first()
  await input.evaluate(element => element.removeAttribute('readonly'))
  await input.fill(value)
  await input.press('Tab')
}

async function selectFormOption(page, scope, label, option) {
  const item = formItem(scope, label)
  await item.locator('.el-select').click()
  await page.locator('.el-select-dropdown:visible').getByText(option, { exact: true }).click()
}

async function uploadMaterial(scope, fileName, body) {
  const input = scope.locator('input[type=file]').last()
  await input.setInputFiles({
    name: fileName,
    mimeType: 'text/plain',
    buffer: Buffer.from(body, 'utf8')
  })
  await expect(scope.getByText(fileName, { exact: true })).toBeVisible()
}

async function completeRetry(page, leadNo, resultLabel, fileName, connected) {
  await page.goto('/lead/retry')
  await filterWorkbench(page, leadNo)
  const row = tableRow(page, leadNo)
  await row.getByRole('button', { name: '处理', exact: true }).click()
  const drawer = page.getByTestId('business-todo-drawer')
  await expect(drawer).toBeVisible()
  const todoRow = drawer.locator('[data-testid^="todo-row-"]').locator('xpath=ancestor::tr')
    .filter({ hasText: 'TD-003' }).first()
  await todoRow.getByRole('button', { name: '完成', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '完成待办' })
  await selectFormOption(page, dialog, '联系结果', resultLabel)
  if (connected) {
    await fillFormText(dialog, '姓名', connected.name)
    await fillFormText(dialog, '城市', connected.city)
    await fillFormText(dialog, '诉求', connected.demand)
    await selectFormOption(page, dialog, '是否到所', connected.visited)
  }
  await uploadMaterial(dialog, fileName, `RETRY ${resultLabel}`)
  await dialog.getByTestId('todo-action-submit').click()
  await expect(page.getByText('处理成功')).toBeVisible()
}

async function openPolicy(page, policyCode) {
  await page.goto('/lead/assignment-policy')
  const card = page.getByTestId(`policy-card-${policyCode}`)
  await expect(card).toBeVisible()
  await card.getByRole('button', { name: '编辑策略' }).click()
  await expect(page.getByText('七个联系窗口')).toBeVisible()
}

function policyWindowRow(page, code) {
  return page.getByTestId(`retry-window-${code}`).locator('xpath=ancestor::tr').first()
}

async function setSevenWindowAttempts(page, attempts) {
  expect(attempts).toHaveLength(WINDOW_CODES.length)
  for (let index = 0; index < WINDOW_CODES.length; index++) {
    const row = policyWindowRow(page, WINDOW_CODES[index])
    await expect(row).toBeVisible()
    const input = row.locator('.el-input-number input').last()
    await input.fill(String(attempts[index]))
    await input.press('Tab')
  }
}

async function expectSevenWindowAttempts(page, attempts) {
  for (let index = 0; index < WINDOW_CODES.length; index++) {
    await expect(policyWindowRow(page, WINDOW_CODES[index]).locator('.el-input-number input').last())
      .toHaveValue(String(attempts[index]))
  }
}

function windowAttempts(snapshot) {
  const byCode = new Map(snapshot.windows.map(window => [window.windowCode, Number(window.maxAttempts)]))
  return WINDOW_CODES.map(code => byCode.get(code))
}

function escapeRegex(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
