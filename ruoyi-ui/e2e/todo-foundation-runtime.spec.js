const { test, expect } = require('@playwright/test')

const domains = [
  { type: 'CASE', id: 101, no: 'CS-101' },
  { type: 'CONTRACT', id: 102, no: 'CT-102' },
  { type: 'MATTER', id: 103, no: 'MT-103' },
  { type: 'LEAD', id: 104, no: 'LD-104' },
  { type: 'CUSTOMER', id: 105, no: 'CU-105' },
  { type: 'FINANCE', id: 106, no: 'FN-106' }
]

test('all six business fixtures render a shared summary and request their uppercase canonical type and id', async ({ page }) => {
  const state = await setupBusinessTodoRuntime(page)
  for (const domain of domains) {
    await page.goto(`/todo/runtime/${domain.type.toLowerCase()}`)
    await expect(page.getByTestId('business-todo-summary')).toBeVisible()
    await expect.poll(() => state.summaryRequests.some(value => value === `${domain.type}/${domain.id}`)).toBe(true)
    await page.getByRole('button', { name: '查看全部' }).click()
    await expect.poll(() => state.listRequests.some(value => value === `${domain.type}/${domain.id}`)).toBe(true)
  }
})

test('business drawer opens canonical detail, completes through shared action form, and refreshes only after success', async ({ page }) => {
  const state = await setupBusinessTodoRuntime(page)
  await page.goto('/todo/runtime/lead')
  await page.getByRole('button', { name: '查看全部' }).click()
  await page.getByText('Domain TODO', { exact: true }).click()
  await expect.poll(() => state.detailRequests).toBe(1)
  await expect(page.getByText('TODO PROFILE')).toBeVisible()
  await page.locator('.el-drawer__close-btn').last().click()
  await page.getByRole('button', { name: '完成', exact: true }).click()
  await expect(page.getByRole('dialog').getByRole('button', { name: '确认', exact: true })).toBeEnabled()
  await page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => state.completePayload && state.completePayload.actionId).toMatch(/^business-/)
  await expect.poll(() => state.summaryRequests.filter(value => value === 'LEAD/104').length).toBeGreaterThan(1)
  await expect.poll(() => state.listRequests.filter(value => value === 'LEAD/104').length).toBeGreaterThan(1)
})

test('business summary clears stale data and does not request an invalid id', async ({ page }) => {
  const state = await setupBusinessTodoRuntime(page, true)
  await page.goto('/todo/runtime/invalid')
  await expect(page.getByTestId('business-todo-summary')).toBeVisible()
  await page.waitForTimeout(100)
  expect(state.summaryRequests).toEqual([])
  await expect(page.getByRole('button', { name: '查看全部' })).toBeDisabled()
})

test('business drawer sends canonical return and transfer actions', async ({ page }) => {
  const state = await setupBusinessTodoRuntime(page)
  await page.goto('/todo/runtime/lead'); await page.getByRole('button', { name: '查看全部' }).click()
  await page.getByRole('button', { name: '退回', exact: true }).click()
  await page.getByRole('dialog').locator('textarea').fill('needs revision')
  await page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => state.actionPayloads.return && state.actionPayloads.return.opinion).toBe('needs revision')
  await page.getByRole('button', { name: '转派', exact: true }).click()
  await page.getByRole('dialog').getByRole('spinbutton').fill('2')
  await page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => state.actionPayloads.transfer && state.actionPayloads.transfer.fields.targetOwnerId).toBe(2)
  await page.getByRole('button', { name: '链路', exact: true }).click()
  await expect.poll(() => state.chainRequests).toBe(1)
})

test('a failed action leaves the business summary and list unchanged', async ({ page }) => {
  const state = await setupBusinessTodoRuntime(page, false, true)
  await page.goto('/todo/runtime/lead')
  await page.getByRole('button', { name: '查看全部' }).click()
  await expect.poll(() => state.listRequests.length).toBe(1)
  const summaryCount = state.summaryRequests.length
  const listCount = state.listRequests.length
  await page.getByRole('button', { name: '完成', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => state.completePayload && state.completePayload.actionId).toMatch(/^business-/)
  await page.waitForTimeout(100)
  expect(state.summaryRequests).toHaveLength(summaryCount)
  expect(state.listRequests).toHaveLength(listCount)
})

async function setupBusinessTodoRuntime(page, includeInvalid = false, failComplete = false) {
  const state = { summaryRequests: [], listRequests: [], detailRequests: 0, completePayload: null, actionPayloads: {}, chainRequests: 0 }
  await page.context().addCookies([{ name: 'Admin-Token', value: 'e2e-token', url: 'http://127.0.0.1:4173/' }])
  await page.addInitScript(() => { document.cookie = 'Admin-Token=e2e-token; path=/' })
  await page.route('**/prod-api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/prod-api', '')
    if (path === '/getInfo') return json(route, null, { code: 200, user: { userId: 1, userName: 'admin', nickName: 'admin', avatar: '' }, roles: ['admin'], permissions: ['*:*:*'] })
    if (path === '/getRouters') return json(route, routes(includeInvalid))
    if (path.startsWith('/todo/business/') && path.endsWith('/summary')) {
      state.summaryRequests.push(path.replace('/todo/business/', '').replace('/summary', ''))
      return json(route, { activeCount: 1, overdueCount: 0, ownerIds: [1], nearestDueAt: '2026-08-01' })
    }
    if (path.startsWith('/todo/business/') && path.endsWith('/list')) {
      state.listRequests.push(path.replace('/todo/business/', '').replace('/list', ''))
      return json(route, null, { code: 200, rows: [{ todo_id: 901, todo_no: 'TD-901', title: 'Domain TODO', status: 'SUBMITTED', allowedActions: ['complete', 'return', 'transfer'], due_at: '2026-08-01' }], total: 1 })
    }
    if (path === '/todo/901') {
      state.detailRequests++
      return json(route, { todo: { todo_id: 901, todo_no: 'TD-901', title: 'Domain TODO', status: 'SUBMITTED', business_type: 'LEAD', business_id: 104 }, actions: [], attachments: [], materials: [], relations: [] })
    }
    if (path === '/todo/901/form') return json(route, { todoId: 901, businessType: 'LEAD', businessId: 104, ui: { config: { fields: [] } }, dod: { config: {} }, defaults: {}, materials: [] })
    if (path === '/todo/chain/901') { state.chainRequests++; return json(route, { nodes: [{ todo_id: 901, title: 'Domain TODO' }] }) }
    if (/^\/todo\/901\/(complete|return|transfer)$/.test(path)) { const action=path.split('/').pop();state.actionPayloads[action]=request.postDataJSON();if(action==='complete')state.completePayload=state.actionPayloads[action];return json(route, null, failComplete&&action==='complete' ? { code: 500, msg: 'complete failed', data: null } : undefined) }
    if (path.startsWith('/system/dict/data/type/') || path === '/system/config/configKey/sys.index.skinName') return json(route, [])
    return json(route, {})
  })
  return state
}

function routes(includeInvalid) {
  const children = domains.map(domain => ({
    path: `todo/runtime/${domain.type.toLowerCase()}`,
    component: 'todo/components/BusinessTodoSummary',
    name: `TodoRuntime${domain.type}`,
    props: { businessType: domain.type, businessId: domain.id, businessNo: domain.no },
    meta: { title: `Todo ${domain.type}` }
  }))
  if (includeInvalid) children.push({ path: 'todo/runtime/invalid', component: 'todo/components/BusinessTodoSummary', name: 'TodoRuntimeInvalid', props: { businessType: 'LEAD', businessId: 0, businessNo: 'LD-0' }, meta: { title: 'Todo Invalid' } })
  return [{ path: '/', component: 'Layout', children }]
}

async function json(route, data, envelope) {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope || { code: 200, msg: 'success', data }) })
}
