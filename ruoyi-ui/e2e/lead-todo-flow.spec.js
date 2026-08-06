const { test, expect, request: playwrightRequest } = require('@playwright/test')
const {
  WINDOW_CODES,
  createRunContext,
  verifyBackendIdentity,
  setupLeadTodoFixtures,
  cleanupLeadTodoFixtures,
  assertNoLeadTodoFixtures,
  leadState,
  todoRuntimeState,
  slaWorkerState,
  makeSellerUnavailable,
  evidenceNames,
  policySnapshot
} = require('./support/lead-todo-e2e-database')

const enabled = String(process.env.LEAD_TODO_E2E).toLowerCase() === 'true'
let fixtures
let runContext
let cleanupRequest
let supervisorRequest
let informationRequest
let verifiedRun
let slaWorkerBaseline
let setupAttempted = false

test.describe.serial('Lead Todo flow with real browser, API and cleanable MySQL fixtures', () => {
  test.skip(!enabled, 'Set LEAD_TODO_E2E=true and the disposable *_e2e database variables')

  test.beforeAll(async () => {
    runContext = createRunContext()
    let ready = false
    try {
      cleanupRequest = await authenticatedApiRequest(runContext.seller)
      supervisorRequest = await authenticatedApiRequest(runContext.supervisor)
      informationRequest = await authenticatedApiRequest(runContext.informationOfficer)
      verifiedRun = await verifyBackendIdentity(runContext, cleanupRequest)
      slaWorkerBaseline = slaWorkerState(runContext).successfulRuns
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

  test('01 tag confirmation advances the governed round-robin cursor and assigns the lead', async ({ page }) => {
    const lead = fixtures.leads.ASSIGN
    expect(leadState(lead.leadNo, runContext)).toMatchObject({
      tagConfirmStatus: 'PENDING',
      ownerId: null
    })
    await login(page, process.env.LEAD_INFORMATION_USER)
    await openIntakeLead(page, lead.leadNo)
    const debug = await page.evaluate(leadNo => {
      const component = [...document.querySelectorAll('*')]
        .map(element => element.__vue__)
        .find(vm => Array.isArray(vm?.leadList))
      const row = component?.leadList?.find(item => item.leadNo === leadNo)
      return {
        permissions: component?.$store?.getters?.permissions,
        row,
        canConfirmTag: component && row ? component.canConfirmTag(row) : null,
        canConfirmTagPermission: component?.canConfirmTagPermission,
        permissionRenderKey: component?.permissionRenderKey,
        authPermission: component ? component.$auth?.hasPermi('lead:tag:confirm') : null,
        tagButtons: [...document.querySelectorAll(`[data-testid="lead-tag-confirm-${leadNo}"]`)]
          .map(element => ({
            text: element.textContent,
            display: getComputedStyle(element).display,
            visibility: getComputedStyle(element).visibility,
            width: element.getBoundingClientRect().width
          })),
        operationTexts: [...document.querySelectorAll('.lead-operation-column')]
          .map(element => element.textContent.trim()).filter(Boolean)
      }
    }, lead.leadNo)
    if (!debug.canConfirmTag || !debug.permissions?.includes('lead:tag:confirm')) {
      throw new Error(`Tag confirmation UI state mismatch: ${JSON.stringify(debug)}`)
    }
    const tagButton = page.locator(`[data-testid="lead-tag-confirm-${lead.leadNo}"]:visible`)
    await expect(tagButton, `Tag button missing after state check: ${JSON.stringify(debug)}`)
      .toBeVisible({ timeout: 5000 })
    await tagButton.click()
    await expect(page.getByText('标签已确认')).toBeVisible()

    await expect.poll(() => leadState(lead.leadNo, runContext)).toMatchObject({
      tagConfirmStatus: 'CONFIRMED',
      ownerName: process.env.LEAD_SALES_USER
    })
  })

  test('02 LEAD_ASSIGNED creates exactly one TD-001 owned by the selected salesperson', async ({ page }) => {
    const lead = fixtures.leads.ASSIGN
    await login(page, process.env.LEAD_SALES_USER)
    await openMineLead(page, lead.leadNo)
    await expect(page.locator(`[data-testid="lead-first-contact-${lead.leadNo}"]:visible`)).toBeVisible()
    await expect.poll(() => leadState(lead.leadNo, runContext), { timeout: 20000 }).toMatchObject({
      assignedEventStatus: 'PROCESSED',
      td001Open: 1,
      td001Owner: process.env.LEAD_SALES_USER
    })
  })

  test.afterAll(async () => {
    try {
      if (runContext && verifiedRun && setupAttempted) {
        const cleanup = await cleanupLeadTodoFixtures(verifiedRun, { apiRequest: cleanupRequest })
        assertNoLeadTodoFixtures(runContext, cleanup)
      }
    } finally {
      if (cleanupRequest) await cleanupRequest.dispose()
      if (supervisorRequest) await supervisorRequest.dispose()
      if (informationRequest) await informationRequest.dispose()
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

  test('06 TD-002 due automatic confirmation uses the production worker and records the controlled default', async ({ page }) => {
    const lead = fixtures.leads.AUTO_REVIEW
    const worker = slaWorkerState(runContext)
    expect(worker).toMatchObject({ status: '0', cronExpression: '0/10 * * * * ?' })

    await expect.poll(() => leadState(lead.leadNo, runContext), { timeout: 25000 }).toMatchObject({
      disposition: 'DEAD_POOL',
      invalidReviewStatus: 'CONFIRMED',
      reviewStatus: 'COMPLETED',
      reviewResult: 'TRUE_INVALID',
      reviewerUser: process.env.LEAD_SUPERVISOR_USER,
      reviewSystemDefault: 'Y',
      deadPoolEnterCount: 1,
      invalidConfirmedEventCount: 1
    })
    await expect.poll(() => todoRuntimeState(lead.leadNo, 'TD-002', runContext),
      { timeout: 25000 }).toMatchObject({
      status: 'COMPLETED',
      autoActionSuccessCount: 1
    })
    expect(slaWorkerState(runContext).successfulRuns).toBeGreaterThan(slaWorkerBaseline)

    await login(page, process.env.LEAD_SUPERVISOR_USER)
    await navigate(page, '/lead/dead-pool')
    await filterWorkbench(page, lead.leadNo)
    await expect(tableRow(page, lead.leadNo)).toBeVisible()
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

  test('10 SLA 80/100/150 thresholds notify the owner and expose the escalation to the supervisor', async ({ page }) => {
    const lead = fixtures.leads.SLA
    await expect.poll(() => todoRuntimeState(lead.leadNo, 'TD-001', runContext),
      { timeout: 25000 }).toMatchObject({
      slaStatus: 'ESCALATED',
      ownerReminderCount: 3,
      supervisorEscalationCount: 1,
      autoActionSuccessCount: 1
    })
    const state = todoRuntimeState(lead.leadNo, 'TD-001', runContext)
    expect(state.remind80At).toBeTruthy()
    expect(state.overdue100At).toBeTruthy()
    expect(state.escalate150At).toBeTruthy()

    await login(page, process.env.LEAD_SUPERVISOR_USER)
    await navigate(page, '/todo')
    await page.locator('input.el-radio-button__orig-radio[value="overdue"]').click({ force: true })
    const keyword = page.locator('.biz-filter-main .el-input input').first()
    const responsePromise = page.waitForResponse(response => {
      if (!response.url().includes('/todo/list') || response.request().method() !== 'GET') return false
      const url = new URL(response.url())
      return url.searchParams.get('mode') === 'overdue' &&
        url.searchParams.get('keyword') === lead.leadNo
    })
    await keyword.fill(lead.leadNo)
    await keyword.press('Enter')
    const response = await responsePromise
    const body = await response.json()
    expect(response.ok()).toBeTruthy()
    expect(body.total).toBe(1)
    expect(body.rows).toHaveLength(1)
    expect(body.rows[0].business_no).toBe(lead.leadNo)
    await expect(page.locator('.el-table__body-wrapper tr').filter({ hasText: lead.leadNo }).first())
      .toBeVisible()
  })

  test('Dead-Pool restore requires a reason, submits it and verifies the restored public-pool state', async ({ page }) => {
    const lead = fixtures.leads.DEAD_POOL
    await login(page, process.env.LEAD_SUPERVISOR_USER)
    await navigate(page, '/lead/dead-pool')
    await filterWorkbench(page, lead.leadNo)
    const row = tableRow(page, lead.leadNo)
    await expect(row.getByRole('button', { name: '领取' })).toHaveCount(0)
    await page.locator(`[data-testid="lead-dead-pool-restore-${lead.leadNo}"]:visible`).click()
    const dialog = page.getByRole('dialog', { name: '恢复 Dead-Pool 线索' })
    await fillFormText(dialog, '恢复原因', '主管复核后确认可重新进入普通公海')
    await dialog.getByTestId('dead-pool-restore-submit').click()
    await expect(page.getByText('线索已恢复到公海')).toBeVisible()

    await expect.poll(() => leadState(lead.leadNo, runContext).disposition).toBe('PUBLIC_POOL')
    expect(leadState(lead.leadNo, runContext).restoreCount).toBe(1)
  })

  test('11 an unavailable salesperson is skipped by the next round-robin assignment', async ({ page }) => {
    const lead = fixtures.leads.LEAVE
    expect(makeSellerUnavailable(runContext)).toEqual([['1']])
    await login(page, process.env.LEAD_INFORMATION_USER)
    await openIntakeLead(page, lead.leadNo)
    await page.locator(`[data-testid="lead-tag-confirm-${lead.leadNo}"]:visible`).click()
    await expect(page.getByText('标签已确认')).toBeVisible()

    await expect.poll(() => leadState(lead.leadNo, runContext), { timeout: 20000 }).toMatchObject({
      tagConfirmStatus: 'CONFIRMED',
      assignedEventStatus: 'PROCESSED',
      ownerName: process.env.LEAD_ALTERNATE_SALES_USER,
      td001Owner: process.env.LEAD_ALTERNATE_SALES_USER
    })
  })

  test('12 duplicate completion is idempotent and an unauthorized actor is rejected', async () => {
    const lead = fixtures.leads.AUTH
    const pending = leadState(lead.leadNo, runContext)
    expect(pending).toMatchObject({
      disposition: 'ACTIVE',
      reviewStatus: 'PENDING'
    })
    const actionId = `lead-e2e-auth-${fixtures.runId}`
    const command = {
      actionId,
      reviewResult: 'TRUE_INVALID',
      reviewOpinion: 'E2E duplicate and authorization boundary'
    }
    const unauthorized = await informationRequest.post(
      `/lead/invalid-review/${pending.reviewTodoId}/complete`, { data: command })
    const unauthorizedBody = await unauthorized.json()
    expect(unauthorized.status()).toBe(200)
    expect(Number(unauthorizedBody.code)).toBe(403)
    expect(leadState(lead.leadNo, runContext)).toMatchObject({
      disposition: 'ACTIVE',
      reviewStatus: 'PENDING',
      deadPoolEnterCount: 0,
      invalidConfirmedEventCount: 0
    })

    const first = await supervisorRequest.post(
      `/lead/invalid-review/${pending.reviewTodoId}/complete`, { data: command })
    const firstBody = await first.json()
    expect(first.ok(), JSON.stringify(firstBody)).toBeTruthy()
    expect(Number(firstBody.code || 200)).toBe(200)

    const replay = await supervisorRequest.post(
      `/lead/invalid-review/${pending.reviewTodoId}/complete`, { data: command })
    const replayBody = await replay.json()
    expect(replay.ok(), JSON.stringify(replayBody)).toBeTruthy()
    expect(Number(replayBody.code || 200)).toBe(200)

    expect(leadState(lead.leadNo, runContext)).toMatchObject({
      disposition: 'DEAD_POOL',
      reviewStatus: 'COMPLETED',
      reviewSystemDefault: 'N',
      deadPoolEnterCount: 1,
      invalidConfirmedEventCount: 1
    })
    expect(todoRuntimeState(lead.leadNo, 'TD-002', runContext)).toMatchObject({
      status: 'COMPLETED',
      actionCount: 1
    })
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
  const infoPromise = page.waitForResponse(response =>
    response.url().includes('/getInfo') && response.request().method() === 'GET')
  await Promise.all([
    page.waitForResponse(response => response.url().includes('/login') && response.request().method() === 'POST'),
    page.getByRole('button', { name: '登录' }).click()
  ])
  const info = await infoPromise
  const body = await info.json()
  expect(info.ok()).toBe(true)
  expect(body.code).toBe(200)
  expect(body.user?.userName).toBe(username)
  await expect(page).not.toHaveURL(/\/login(?:\?|$)/)
  await page.waitForLoadState('networkidle')
}

async function navigate(page, path) {
  await page.evaluate(async target => {
    const app = [...document.querySelectorAll('*')]
      .map(element => element.__vue__)
      .find(vm => vm?.$router)
    if (!app) throw new Error('Vue router is unavailable')
    try {
      await app.$router.push(target)
    } catch (error) {
      if (!String(error?.message || '').includes('Avoided redundant navigation')) throw error
    }
  }, path)
  await expect(page).toHaveURL(new RegExp(`${escapeRegex(path.split('?')[0])}(?:\\?|$)`))
}

async function filterWorkbench(page, keyword) {
  const input = page.getByPlaceholder(/线索编号、名称、手机号/)
  await input.fill(keyword)
  const responsePromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return response.request().method() === 'GET'
      && url.pathname.includes('/prod-api/lead/')
      && url.searchParams.get('keyword') === keyword
  })
  await page.getByTestId('lead-search-submit').click()
  const response = await responsePromise
  expect(response.status()).toBe(200)
  const body = await response.json()
  expect(body.code).toBe(200)
  expect(body.rows.some(row => row.leadNo === keyword)).toBe(true)
  await expect(tableRow(page, keyword)).toBeVisible()
}

function tableRow(page, keyword) {
  return page.getByTestId(`lead-row-${keyword}`).locator('xpath=ancestor::tr').first()
}

async function openFirstContact(page, leadNo) {
  await navigate(page, '/lead/mine?module=mine')
  const input = page.getByPlaceholder('搜索线索名称、单位')
  await input.fill(`E2E ${leadNo.substring(leadNo.lastIndexOf('_') + 1)}`)
  await page.getByTestId('lead-search-submit').click()
  const row = tableRow(page, leadNo)
  await expect(row).toBeVisible()
  await page.locator(`[data-testid="lead-first-contact-${leadNo}"]:visible`).click()
  await expect(page.getByTestId('lead-first-contact-drawer')).toBeVisible()
}

async function openMineLead(page, leadNo) {
  await navigate(page, '/lead/mine?module=mine')
  const input = page.getByPlaceholder('搜索线索名称、单位')
  await input.fill(`E2E ${leadNo.substring(leadNo.lastIndexOf('_') + 1)}`)
  await page.getByTestId('lead-search-submit').click()
  await expect(tableRow(page, leadNo)).toBeVisible()
}

async function openIntakeLead(page, leadNo) {
  await navigate(page, '/lead/all?module=all')
  const input = page.getByPlaceholder('搜索线索名称、单位')
  const keyword = `E2E ${leadNo.substring(leadNo.lastIndexOf('_') + 1)}`
  await input.fill(keyword)
  const responsePromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.pathname.endsWith('/lead/list') &&
      url.searchParams.get('leadName') === keyword &&
      url.searchParams.get('listMode') === 'all'
  })
  await page.getByTestId('lead-search-submit').click()
  const response = await responsePromise
  expect(response.ok()).toBe(true)
  const requestUrl = new URL(response.url())
  expect(requestUrl.searchParams.get('pageNum')).toBe('1')
  expect(requestUrl.searchParams.get('pageSize')).toBe('10')
  const body = await response.json()
  expect(body.total).toBe(1)
  expect((body.rows || []).map(row => row.leadNo)).toEqual([leadNo])
  await expect(tableRow(page, leadNo)).toBeVisible()
}

async function openReview(page, leadNo) {
  await navigate(page, '/lead/invalid-review')
  await filterWorkbench(page, leadNo)
  await page.locator(`[data-testid="lead-review-${leadNo}"]:visible`).click()
  await expect(page.getByRole('dialog', { name: '确认无效判断' })).toBeVisible()
}

function formItem(scope, label) {
  return scope.getByText(label, { exact: true })
    .locator('xpath=ancestor::div[contains(concat(" ",normalize-space(@class)," ")," el-form-item ")]')
    .first()
}

async function fillFormText(scope, label, value) {
  const item = formItem(scope, label)
  await expect(item).toBeVisible()
  const control = item.locator('textarea:visible,input:visible').first()
  await control.fill(value)
}

async function fillDateTime(scope, label, value) {
  const input = formItem(scope, label).locator('input').first()
  const displayValue = String(value).replace('T', ' ')
  await input.evaluate(element => element.removeAttribute('readonly'))
  await input.fill(displayValue)
  await input.press('Enter')
  await expect(input).toHaveValue(displayValue)
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
  await navigate(page, '/lead/retry')
  await filterWorkbench(page, leadNo)
  const row = tableRow(page, leadNo)
  await page.locator(`[data-testid="lead-retry-${leadNo}"]:visible`).click()
  const drawer = page.getByTestId('business-todo-drawer')
  await expect(drawer).toBeVisible()
  const todoRow = drawer.locator('[data-testid^="todo-row-"]').locator('xpath=ancestor::tr')
    .filter({ hasText: 'TD-003' }).first()
  await todoRow.getByRole('button', { name: '完成', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '完成待办' })
  await selectFormOption(page, dialog, '联系结果', resultLabel)
  await fillDateTime(dialog, '联系时间', new Date().toISOString().slice(0, 19))
  if (connected) {
    await fillFormText(dialog, '姓名', connected.name)
    await fillFormText(dialog, '城市', connected.city)
    await fillFormText(dialog, '诉求', connected.demand)
    await selectFormOption(page, dialog, '是否到所', connected.visited)
  }
  await uploadMaterial(dialog, fileName, `RETRY ${resultLabel}`)
  await dialog.getByTestId('todo-action-submit').click()
  await expect(page.getByText('处理成功')).toBeVisible()
  await expect(dialog).toBeHidden()
  await drawer.getByRole('button', { name: 'close 业务待办', exact: true }).click()
  await expect(drawer).toBeHidden()
}

async function openPolicy(page, policyCode) {
  await navigate(page, '/lead/assignment-policy')
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
