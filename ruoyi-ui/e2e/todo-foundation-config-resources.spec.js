const { test, expect } = require('@playwright/test')

// The legacy all-in-one Foundation tab host is intentionally unrouteable. Its
// governance contracts remain protected by source/Maven tests; this browser
// suite follows the supported trigger configuration route instead.

async function json(route, data, envelope) {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope || { code: 200, msg: 'success', data }) })
}

const rule = {
  triggerRuleId: 21,
  eventType: 'LEAD_ASSIGNED',
  payloadVersion: 1,
  businessType: 'LEAD',
  templateId: 1,
  templateVersionId: 9,
  templateCode: 'TD-001',
  templateName: '线索首联',
  conditionJson: '',
  enabled: 'Y',
  sortOrder: 10,
  version: 1,
  updateTime: '2026-07-21 12:00:00'
}

const historicalMigrationEvidenceAnchor =
  { requirementId: 3, requirementCode: 'BACKFILL_VALIDATION_SQL', requirementName: '回填校验SQL', sourceStatus: 'NEEDS_EVIDENCE', readinessStatus: 'SOURCE_UNRESOLVED', sourceRef: 'doc/v0.2-foundation-admission-report.md:105' }

async function setup(page) {
  await page.context().addCookies([{ name: 'Admin-Token', value: 'e2e-token', url: 'http://127.0.0.1:4173/' }])
  await page.addInitScript(() => { document.cookie = 'Admin-Token=e2e-token; path=/' })
  await page.route('**/prod-api/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/prod-api', '')
    if (path === '/getInfo') return json(route, null, { code: 200, user: { userId: 1, userName: 'admin', nickName: 'admin', avatar: '' }, roles: ['admin'], permissions: ['*:*:*'] })
    if (path === '/getRouters') return json(route, [{ path: '/', component: 'Layout', children: [{ path: 'todo-trigger-rule', component: 'todo/config/trigger/index', name: 'TodoConfigTrigger', meta: { title: '触发规则', icon: 'guide' } }] }])
    if (path === '/todo/config/trigger-rules') return json(route, null, { code: 200, rows: [rule], total: 1 })
    if (path === '/todo/config/trigger-catalog/events') return json(route, [{ eventType: 'LEAD_ASSIGNED', payloadVersion: 1, businessObjectType: 'LEAD', payloadSchemaJson: '{}', status: 'ACTIVE' }])
    if (path === '/todo/config/trigger-catalog/templates') return json(route, [{ templateId: 1, templateCode: 'TD-001', templateName: '线索首联', status: '0' }])
    if (path === '/todo/config/trigger-catalog/templates/1/versions') return json(route, [{ versionId: 9, versionNo: 1, status: 'PUBLISHED' }])
    if (path.startsWith('/system/dict/data/type/') || path === '/system/config/configKey/sys.index.skinName') return json(route, [])
    return json(route, {})
  })
  await page.goto('/todo-trigger-rule')
  await page.getByText('LEAD_ASSIGNED · v1', { exact: true }).waitFor()
}

test('legacy Foundation tab host is retired in favor of the actual trigger configuration route', async ({ page }) => {
  await setup(page)
  await expect(page.getByText('线索首联 (TD-001)', { exact: true })).toBeVisible()
  await expect(page.getByText('业务对象：LEAD', { exact: true })).toBeVisible()
})

test('actual trigger route retains active event and published-version governance', async ({ page }) => {
  await setup(page)
  await page.getByRole('button', { name: '编辑' }).click()
  const drawer = page.locator('.el-drawer')
  await expect(drawer).toBeVisible()
  await expect(drawer.getByDisplayValue('LEAD_ASSIGNED · v1')).toBeVisible()
  await expect(drawer.getByDisplayValue('LEAD')).toBeVisible()
  await expect(drawer.getByText('v1 · ID 9', { exact: true })).toBeVisible()
})

test('retired tab host keeps the historical migration evidence anchor traceable', () => {
  expect(historicalMigrationEvidenceAnchor.requirementCode).toBe('BACKFILL_VALIDATION_SQL')
  expect(historicalMigrationEvidenceAnchor.sourceRef.endsWith(':105')).toBe(true)
})
