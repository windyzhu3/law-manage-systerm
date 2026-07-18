const { test, expect } = require('@playwright/test')

test('completion uploads required material and submits its canonical file object id', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/todo/file-runtime'); await page.getByRole('button', { name: '查看全部' }).click(); await page.getByRole('button', { name: '完成' }).click()
  await page.getByRole('dialog').getByRole('textbox').last().fill('approve')
  await page.locator('input[type=file]').setInputFiles({ name: 'evidence.txt', mimeType: 'text/plain', buffer: Buffer.from('evidence') })
  await expect.poll(() => state.uploads).toBe(1)
  await page.getByRole('dialog').getByRole('button', { name: '确认', exact: true }).click()
  await expect.poll(() => state.complete && state.complete.fileObjectIds).toEqual([701])
  expect(state.complete.fields.decision).toBe('approve')
  expect(JSON.stringify(state.complete)).not.toContain('evidence.txt')
})

async function setup(page) {
  const state = { uploads: 0, complete: null }; await page.context().addCookies([{ name: 'Admin-Token', value: 'x', url: 'http://127.0.0.1:4173/' }]); await page.addInitScript(() => { document.cookie = 'Admin-Token=x; path=/' })
  await page.route('**/prod-api/**', async route => { const path = new URL(route.request().url()).pathname.replace('/prod-api', ''); const req = route.request(); const ok = data => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data }) })
    if (path === '/getInfo') return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, user: { userId: 1, userName: 'a' }, roles: ['admin'], permissions: ['*:*:*'] }) })
    if (path === '/getRouters') return ok([{ path: '/', component: 'Layout', children: [{ path: 'todo/file-runtime', component: 'todo/components/BusinessTodoSummary', name: 'TodoFileRuntime', props: { businessType: 'LEAD', businessId: 8, businessNo: 'LD-8' }, meta: { title: 'File' } }] }])
    if (path.endsWith('/summary')) return ok({ activeCount: 1 }); if (path.endsWith('/list')) return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, rows: [{ todo_id: 8, todo_no: 'TD-8', title: 'File TODO', status: 'SUBMITTED', allowedActions: ['complete'] }], total: 1 }) })
    if (path === '/todo/8/form') return ok({ todoId: 8, businessType: 'LEAD', businessId: 8, ui: { config: { fields: [{ key: 'decision', label: 'Decision', required: true }] } }, dod: { config: { materials: [{ materialType: 'PROOF', label: 'Proof', minCount: 1 }] } }, defaults: {}, materials: [] })
    if (path === '/files/register') return ok({ uploadIntentId: 'u1', fileObjectId: 701 }); if (path === '/files/u1/complete') { state.uploads++; return ok({ fileObjectId: 701, fileVersionId: 1, originalFileName: 'evidence.txt' }) }
    if (path === '/todo/8/complete') { state.complete = req.postDataJSON(); return ok({}) }; if (path.startsWith('/system/')) return ok([]); return ok({})
  }); return state
}
