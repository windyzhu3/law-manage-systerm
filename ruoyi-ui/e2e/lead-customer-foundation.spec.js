const { test, expect } = require('@playwright/test')

test('lead actions keep assignment followup pool and conversion api contracts', async ({ page }) => {
  const calls = []
  await setupBusinessApp(page, async (route, path) => {
    if (path === '/lead/dashboard') return json(route, { cards: [], statuses: [] })
    if (path === '/lead/owner/options') return json(route, [{ userId: 8, userName: 'alice', nickName: 'Alice' }])
    if (path === '/lead/setting/list') return json(route, [{ settingType: 'source', settingCode: 'web', settingName: '官网', status: '0' }])
    if (path.startsWith('/lead/list')) return json(route, null, { code: 200, rows: [leadRow()], total: 1 })
    if (path.startsWith('/lead/followup/list')) return json(route, null, { code: 200, rows: [], total: 0 })
    if (route.request().method() !== 'GET') {
      calls.push({ path, body: route.request().postDataJSON() })
      return json(route, {})
    }
    return json(route, {})
  })

  await page.goto('/lead/list?module=all')
  const row = page.locator('.el-table__body tr').filter({ hasText: '张三' }).first()
  await expect(row).toBeVisible()

  await page.getByRole('button', { name: '分配', exact: false }).last().click()
  await page.getByText('Alice', { exact: true }).last().click()
  await page.getByRole('button', { name: '确认分配' }).click()
  await expect.poll(() => calls.find(item => item.path === '/lead/assign')?.body.ownerId).toBe(8)

  await page.getByRole('button', { name: '跟进', exact: false }).last().click()
  const followDialog = page.getByRole('dialog', { name: '记录跟进' })
  await followDialog.getByText('跟进结果').locator('..').getByRole('textbox').fill('有意向')
  await followDialog.getByText('跟进内容').locator('..').getByRole('textbox').fill('已确认合同审查需求')
  await followDialog.getByRole('button', { name: '保存记录' }).click()
  await expect.poll(() => calls.find(item => item.path === '/lead/followup')?.body.leadId).toBe(7)

  await page.getByRole('button', { name: '公海', exact: false }).last().click()
  const poolDialog = page.getByRole('dialog', { name: '进入公海' })
  await poolDialog.getByRole('button', { name: '确定' }).click()
  await expect.poll(() => calls.find(item => item.path === '/lead/pool')?.body.leadId).toBe(7)

  await page.getByRole('button', { name: '转化', exact: false }).last().click()
  await page.getByRole('dialog').getByRole('button', { name: '确定' }).click()
  await expect.poll(() => calls.some(item => item.path === '/lead/convert/7')).toBe(true)
})

test('customer detail exposes contacts tags and merge entry', async ({ page }) => {
  await setupBusinessApp(page, async (route, path) => {
    if (path === '/customer/dashboard') return json(route, { cards: [] })
    if (path === '/customer/owner/options') return json(route, [{ userId: 8, userName: 'alice', nickName: 'Alice' }])
    if (path.startsWith('/customer/list')) return json(route, null, { code: 200, rows: [customerRow()], total: 1 })
    if (path === '/customer/31') return json(route, customerRow())
    if (path.startsWith('/customer/contact/list')) return json(route, null, { code: 200, rows: [{ contact_id: 1, contact_name: '张三', mobile: '13800000000', relation_type: 'daily' }], total: 1 })
    if (path.startsWith('/customer/followup/list') || path.startsWith('/contract/list') || path.startsWith('/customer/merge/logs')) return json(route, null, { code: 200, rows: [], total: 0 })
    if (path.startsWith('/customer/tag/list')) return json(route, [{ tag_id: 5, tag_name: '重点客户', tag_color: '#2563eb', status: '0' }])
    if (path === '/customer/31/tags') return json(route, [5])
    if (path.startsWith('/customer/merge/candidates')) return json(route, [customerRow()])
    if (path === '/lead/setting/list') return json(route, [{ settingType: 'source', settingCode: 'web', settingName: '官网', status: '0' }])
    return json(route, {})
  })

  await page.goto('/customer/list?module=list')
  await page.getByText('张三客户', { exact: true }).first().click()
  const drawer = page.locator('.customer-detail-drawer')
  await expect(drawer.getByText('联系人', { exact: true })).toBeVisible()
  await expect(drawer.getByText('张三', { exact: true })).toBeVisible()
  await drawer.getByRole('button', { name: '维护标签' }).click()
  await expect(page.getByRole('dialog').getByText('重点客户', { exact: true })).toBeVisible()

  await page.goto('/customer/merge?module=merge')
  const mergeRow = page.locator('.el-table__body tr').filter({ hasText: '张三客户' }).first()
  await expect(mergeRow).toBeVisible()
  await mergeRow.getByRole('button', { name: '合并' }).click()
  await expect(page.getByRole('dialog').getByText('待合并客户', { exact: true })).toBeVisible()
})

async function setupBusinessApp(page, handler) {
  await page.context().addCookies([{ name: 'Admin-Token', value: 'e2e-token', url: 'http://127.0.0.1:4173/' }])
  await page.addInitScript(() => { document.cookie = 'Admin-Token=e2e-token; path=/' })
  await page.route('**/prod-api/**', async route => {
    const url = new URL(route.request().url())
    const path = url.pathname.replace('/prod-api', '')
    if (path === '/getInfo') return json(route, null, { code: 200, user: { userId: 1, userName: 'admin', nickName: '管理员', avatar: '' }, roles: ['admin'], permissions: ['*:*:*'] })
    if (path === '/getRouters') return json(route, routes())
    if (path.startsWith('/system/dict/data/type/')) return json(route, dict(path.split('/').pop()))
    if (path.startsWith('/system/notice/list')) return json(route, null, { code: 200, rows: [], total: 0 })
    if (path === '/system/config/configKey/sys.index.skinName') return json(route, '')
    return handler(route, path)
  })
}

function routes() {
  return [{ path: '/', component: 'Layout', children: [
    { path: 'lead/list', component: 'lead/index', name: 'LeadListE2E', meta: { title: '线索管理' } },
    { path: 'customer/list', component: 'customer/index', name: 'CustomerListE2E', meta: { title: '客户管理' } },
    { path: 'customer/merge', component: 'customer/index', name: 'CustomerMergeE2E', meta: { title: '客户合并' } }
  ] }]
}

function dict(type) {
  const values = {
    law_lead_status: [['0', '待分配'], ['1', '待跟进'], ['2', '跟进中'], ['3', '已转化']],
    law_lead_priority: [['2', '中']], law_lead_follow_type: [['phone', '电话']],
    law_lead_pool_status: [['0', '非公海'], ['1', '公海']], law_lead_setting_type: [['source', '来源']],
    law_customer_type: [['personal', '个人']], law_customer_industry: [['legal', '法律']],
    law_customer_level: [['2', '普通']], law_contact_relation: [['daily', '日常联系人']],
    law_yes_no_flag: [['1', '是'], ['0', '否']], law_customer_follow_type: [['phone', '电话']],
    sys_normal_disable: [['0', '正常'], ['1', '停用']], law_contract_status: [['0', '草稿']]
  }
  return (values[type] || [['0', '默认']]).map(([dictValue, dictLabel], index) => ({ dictValue, dictLabel, listClass: 'default', isDefault: index === 0 ? 'Y' : 'N' }))
}

function leadRow() {
  return { leadId: 7, leadNo: 'XS007', leadName: '张三咨询', contactName: '张三', mobile: '13800000000', sourceCode: 'web', legalDemand: '合同审查', status: '1', priority: '2', poolStatus: '0', ownerId: 8, ownerName: 'Alice', createTime: '2026-07-15 08:00:00' }
}

function customerRow() {
  return { customerId: 31, customerNo: 'KH031', customerName: '张三客户', customerType: 'personal', mobile: '13800000000', sourceCode: 'web', customerLevel: '2', mainDemand: '合同审查', ownerId: 8, ownerName: 'Alice', status: '0', contractCount: 0 }
}

async function json(route, data, envelope) {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope || { code: 200, msg: 'success', data }) })
}
