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

test('extension requires due date, reason and proof, then refreshes only after success', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/todo/file-runtime'); await page.getByRole('button', { name: '查看全部' }).click(); await page.getByText('File TODO', { exact: true }).click()
  await page.getByRole('button', { name: '申请延期' }).click()
  const dialog = page.getByRole('dialog', { name: '申请延期' })
  await dialog.getByRole('button', { name: '提交申请' }).click(); expect(state.extension).toBeNull()
  await dialog.getByRole('textbox').first().click(); await page.keyboard.type('2026-09-01 10:00:00'); await page.keyboard.press('Enter'); await dialog.locator('textarea').fill('need more time')
  await dialog.getByRole('button', { name: '提交申请' }).click(); expect(state.extension).toBeNull()
  await dialog.locator('input[type=file]').setInputFiles({ name: 'proof.txt', mimeType: 'text/plain', buffer: Buffer.from('proof') })
  await expect.poll(() => state.uploads).toBe(1)
  await dialog.getByRole('button', { name: '提交申请' }).click()
  await expect.poll(() => state.extension && state.extension.proofFileObjectIds).toEqual([701])
  await expect.poll(() => state.summaries).toBeGreaterThan(1); await expect.poll(() => state.lists).toBeGreaterThan(1)
})

test('authorized material supports tokenized preview download and versions while missing relation stays blocked', async ({ page }) => {
  const state = await setup(page, { existingMaterials: true })
  await page.goto('/todo/file-runtime'); await page.getByRole('button', { name: '查看全部' }).click(); await page.getByText('File TODO', { exact: true }).click()
  const drawer = page.locator('.el-drawer').filter({ hasText: 'TODO PROFILE' })
  const authorized = drawer.locator('.file-row').filter({ hasText: 'evidence.pdf' })
  await expect(authorized).toBeVisible()

  await authorized.getByRole('button', { name: '版本' }).click()
  const versions = page.getByRole('dialog', { name: '文件版本' })
  await expect(versions.getByText('v1')).toBeVisible(); await expect(versions.getByText('evidence.pdf')).toBeVisible()
  await versions.getByRole('button', { name: '关闭' }).click()

  await authorized.getByRole('button', { name: '预览', exact: true }).click()
  await expect.poll(() => state.previewTokens).toBe(1); await expect.poll(() => state.previewAccesses).toBe(1)
  await authorized.getByRole('button', { name: '下载' }).click()
  await expect.poll(() => state.downloadTokens).toBe(1); await expect.poll(() => state.downloadAccesses).toBe(1)

  const blocked = drawer.locator('.file-row').filter({ hasText: 'orphan.txt' })
  await expect(blocked.getByText('未授权')).toBeVisible()
  await expect(blocked.getByRole('button', { name: '预览', exact: true })).toHaveCount(0)
  await expect(blocked.getByRole('button', { name: '下载' })).toHaveCount(0)
})

async function setup(page, options = {}) {
  const state = { uploads: 0, complete: null, extension: null, summaries: 0, lists: 0, previewTokens: 0, downloadTokens: 0, previewAccesses: 0, downloadAccesses: 0, versionLoads: 0 }; await page.context().addCookies([{ name: 'Admin-Token', value: 'x', url: 'http://127.0.0.1:4173/' }]); await page.addInitScript(() => { document.cookie = 'Admin-Token=x; path=/'; window.open = () => ({ opener: null, location: { replace() {} }, close() {} }) })
  await page.route('**/prod-api/**', async route => { const path = new URL(route.request().url()).pathname.replace('/prod-api', ''); const req = route.request(); const ok = data => route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, data }) })
    if (path === '/getInfo') return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, user: { userId: 1, userName: 'a' }, roles: ['admin'], permissions: ['*:*:*'] }) })
    if (path === '/getRouters') return ok([{ path: '/', component: 'Layout', children: [{ path: 'todo/file-runtime', component: 'todo/components/BusinessTodoSummary', name: 'TodoFileRuntime', props: { businessType: 'LEAD', businessId: 8, businessNo: 'LD-8' }, meta: { title: 'File' } }] }])
    if (path.endsWith('/summary')) { state.summaries++; return ok({ activeCount: 1 }) }; if (path.endsWith('/list')) { state.lists++; return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ code: 200, rows: [{ todo_id: 8, todo_no: 'TD-8', title: 'File TODO', status: 'SUBMITTED', allowedActions: ['complete'] }], total: 1 }) }) }
    if (path === '/todo/8') return ok({ todo: { todo_id: 8, title: 'File TODO', status: 'SUBMITTED', business_type: 'LEAD', business_id: 8 }, actions: [], materials: [], relations: [] })
    if (path === '/todo/8/form') return ok({ todoId: 8, businessType: 'LEAD', businessId: 8, ui: { config: { fields: [{ key: 'decision', label: 'Decision', required: true }] } }, dod: { config: { materials: [{ materialType: 'PROOF', label: 'Proof', minCount: 1 }, ...(options.existingMaterials ? [{ materialType: 'LEGACY', label: 'Legacy', minCount: 0 }] : [])] } }, extensionPolicy: { remainingRequestCount: 1, maxExtensionValue: 2, maxExtensionUnit: 'DAYS', proofRequired: true }, defaults: {}, materials: options.existingMaterials ? [{ relationId: 91, fileObjectId: 701, materialType: 'PROOF', fileName: 'evidence.pdf' }, { relationId: null, fileObjectId: 702, materialType: 'LEGACY', fileName: 'orphan.txt' }] : [] })
    if (path === '/files/701/versions') { state.versionLoads++; return ok([{ fileVersionId: 801, fileObjectId: 701, versionNo: 1, originalFileName: 'evidence.pdf', contentType: 'application/pdf', sizeBytes: 8, createdAt: '2026-07-18T08:00:00Z' }]) }
    if (path === '/files/701/preview-token') { state.previewTokens++; return ok({ token: 'preview-token' }) }
    if (path === '/files/701/download-token') { state.downloadTokens++; return ok({ token: 'download-token' }) }
    if (path === '/files/access/preview-token') { state.previewAccesses++; return route.fulfill({ contentType: 'application/pdf', body: Buffer.from('preview') }) }
    if (path === '/files/access/download-token') { state.downloadAccesses++; return route.fulfill({ contentType: 'application/octet-stream', body: Buffer.from('download') }) }
    if (path === '/files/register') return ok({ uploadIntentId: 'u1', fileObjectId: 701 }); if (path === '/files/u1/complete') { state.uploads++; return ok({ fileObjectId: 701, fileVersionId: 1, originalFileName: 'evidence.txt' }) }
    if (path === '/todo/8/complete') { state.complete = req.postDataJSON(); return ok({}) }; if (path === '/todo/8/extension-requests') { state.extension = req.postDataJSON(); return ok({}) }; if (path.startsWith('/system/')) return ok([]); return ok({})
  }); return state
}
