const { test, expect } = require('@playwright/test')

// Task 13 intentionally unroutes the legacy Foundation tab host. This suite now
// exercises the supported definition surface through the real template page.

async function json(route, data, envelope) {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope || { code: 200, msg: 'success', data }) })
}

function templateDetail() {
  return {
    templateId: 1,
    templateCode: 'TD-001',
    templateName: '线索首联',
    businessType: 'LEAD',
    status: '0',
    version: 2,
    editableVersion: {
      versionId: 10,
      versionNo: 2,
      status: 'DRAFT',
      changeSummary: 'Foundation 配置中心迁移验证',
      impactScope: '线索',
      definitionJson: JSON.stringify({
        schemaVersion: 1,
        templateCode: 'TD-001',
        event: { eventType: 'LEAD_ASSIGNED', payloadVersion: 1, condition: {} },
        owner: { config: { type: 'ROLE', candidates: ['lawyer'] } },
        dod: { config: { composition: 'ALL' } },
        sla: { config: {} },
        ui: { config: { businessStage: 'LEAD', templateType: 'STANDARD', priority: 'HIGH' } },
        routing: { config: { start: 'task', nodes: [{ key: 'task', type: 'TASK' }], edges: [] } },
        autoActions: [],
        decisionRefs: [],
        acceptanceRefs: []
      })
    },
    ruleReferences: []
  }
}

async function setup(page) {
  const detail = templateDetail()
  await page.context().addCookies([{ name: 'Admin-Token', value: 'e2e-token', url: 'http://127.0.0.1:4173/' }])
  await page.addInitScript(() => { document.cookie = 'Admin-Token=e2e-token; path=/' })
  await page.route('**/prod-api/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/prod-api', '')
    if (path === '/getInfo') return json(route, null, { code: 200, user: { userId: 1, userName: 'admin', nickName: 'admin', avatar: '' }, roles: ['admin'], permissions: ['*:*:*'] })
    if (path === '/getRouters') return json(route, [{ path: '/', component: 'Layout', children: [{ path: 'todo-template', component: 'todo/config/template/index', name: 'TodoConfigTemplate', meta: { title: '待办模板配置', icon: 'list' } }] }])
    if (path === '/todo/config/dashboard') return json(route, { templateCount: 1, publishedTemplateCount: 0, draftTemplateCount: 1, todayTriggeredTodoCount: 0 })
    if (path === '/todo/config/templates') return json(route, null, { code: 200, rows: [{ ...detail, editableVersion: undefined, eventType: 'LEAD_ASSIGNED', publishStatus: 'DRAFT' }], total: 1 })
    if (path === '/todo/config/templates/1') return json(route, detail)
    if (path === '/todo/config/template-catalog/events') return json(route, [{ eventType: 'LEAD_ASSIGNED', payloadVersion: 1, businessObjectType: 'LEAD', payloadSchemaJson: '{}', status: 'ACTIVE' }])
    if (path.startsWith('/todo/config/template-catalog/')) return json(route, [])
    if (path === '/todo/config/templates/1/versions') return json(route, [detail.editableVersion])
    if (path.startsWith('/system/dict/data/type/') || path === '/system/config/configKey/sys.index.skinName') return json(route, [])
    return json(route, {})
  })
  await page.goto('/todo-template')
  await page.getByText('TD-001', { exact: true }).waitFor()
}

test('Foundation definition coverage uses the actual template configuration route', async ({ page }) => {
  await setup(page)
  await page.getByRole('button', { name: '详情' }).click()
  await expect(page.locator('.el-drawer')).toBeVisible()
  await expect(page.getByText('LEAD_ASSIGNED', { exact: true })).toBeVisible()
  await expect(page.getByText('Foundation 配置中心迁移验证', { exact: true })).toBeVisible()
})

test('draft definition opens in the real eight-step template drawer', async ({ page }) => {
  await setup(page)
  await page.getByRole('button', { name: '编辑' }).click()
  await expect(page.locator('.el-drawer')).toBeVisible()
  for (const step of ['基础信息', '触发条件', '负责人', 'SLA', '完成条件', '路由', '模拟', '版本']) {
    await expect(page.getByText(step, { exact: true })).toBeVisible()
  }
})
