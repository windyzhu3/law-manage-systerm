const { test, expect } = require('@playwright/test')
const { randomBytes } = require('node:crypto')

test('system user UI labels only test identities without exposing credentials', async ({ page }) => {
  const sensitive = sensitiveFixture()
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => {
    if (message.type() === 'error') errors.push(message.text())
  })
  await setupSystemUserApp(page, sensitive)

  await page.goto('/system/user')
  const testUserRow = page.locator('.el-table__body tr').filter({ hasText: 'foundation-test-user' }).first()
  const ordinaryUserRow = page.locator('.el-table__body tr').filter({ hasText: 'ordinary-user' }).first()
  await expect(testUserRow).toBeVisible()
  await expect(ordinaryUserRow).toBeVisible()
  await expect(testUserRow.getByText('测试身份', { exact: true })).toHaveCount(1)
  await expect(ordinaryUserRow.getByText('测试身份', { exact: true })).toHaveCount(0)
  await expect(page.getByText('测试身份', { exact: true })).toHaveCount(1)

  await testUserRow.getByText('foundation-test-user', { exact: true }).click()
  const drawer = page.locator('.el-drawer__wrapper:visible')
  await expect(drawer).toBeVisible()
  await expect(drawer.getByText('测试身份', { exact: true })).toHaveCount(1)
  await expect(page.getByText('测试身份', { exact: true })).toHaveCount(2)

  const dom = await page.locator('body').textContent()
  const markup = await page.content()
  const controlValues = await page.locator('input, textarea, select').evaluateAll(controls => controls.map(control => control.value))
  expect(dom).not.toContain(sensitive.password)
  expect(dom).not.toContain(sensitive.storedHash)
  expect(dom).not.toMatch(/\$2[aby]\$/)
  expect(markup).not.toContain(sensitive.password)
  expect(markup).not.toContain(sensitive.storedHash)
  expect(markup).not.toMatch(/\$2[aby]\$/)
  expect(controlValues.join('\n')).not.toContain(sensitive.password)
  expect(controlValues.join('\n')).not.toContain(sensitive.storedHash)
  expect(controlValues.join('\n')).not.toMatch(/\$2[aby]\$/)
  expect(errors).toEqual([])
})

test('system user drawer clears a prior test identity while an ordinary detail loads', async ({ page }) => {
  const controller = await setupSystemUserApp(page, sensitiveFixture())

  await page.goto('/system/user')
  const testUserRow = page.locator('.el-table__body tr').filter({ hasText: 'foundation-test-user' }).first()
  const ordinaryUserRow = page.locator('.el-table__body tr').filter({ hasText: 'ordinary-user' }).first()
  await testUserRow.getByText('foundation-test-user', { exact: true }).click()
  const drawer = page.locator('.el-drawer__wrapper:visible')
  await expect(drawer.getByText('测试身份', { exact: true })).toHaveCount(1)

  await drawer.locator('.el-drawer__close-btn').click()
  await expect(page.locator('.el-drawer__wrapper:visible')).toHaveCount(0)
  await ordinaryUserRow.getByText('ordinary-user', { exact: true }).click()
  await expect(drawer).toBeVisible()
  await expect(drawer.getByText('测试身份', { exact: true })).toHaveCount(0)

  controller.resolveOrdinaryDetail()
  await expect(drawer.getByText('ordinary-user', { exact: true })).toBeVisible()
  await expect(drawer.getByText('测试身份', { exact: true })).toHaveCount(0)
})

async function setupSystemUserApp(page, sensitive) {
  let resolveOrdinaryDetail
  const ordinaryDetailRequested = new Promise(resolve => { resolveOrdinaryDetail = resolve })
  await page.context().addCookies([{ name: 'Admin-Token', value: 'e2e-token', url: 'http://127.0.0.1:4173/' }])
  await page.addInitScript(() => { document.cookie = 'Admin-Token=e2e-token; path=/' })
  await page.route('**/prod-api/**', async route => {
    const url = new URL(route.request().url())
    const path = url.pathname.replace('/prod-api', '')
    if (path === '/getInfo') return json(route, null, { code: 200, user: { userId: 1, userName: 'admin', nickName: '管理员', avatar: '' }, roles: ['admin'], permissions: ['*:*:*'] })
    if (path === '/getRouters') return json(route, routes())
    if (path.startsWith('/system/dict/data/type/')) return json(route, dictionaries())
    if (path === '/system/config/configKey/sys.index.skinName') return json(route, '')
    if (path.startsWith('/system/notice/list')) return json(route, null, { code: 200, rows: [], total: 0 })
    if (path.startsWith('/system/user/list')) return json(route, null, { code: 200, rows: users(sensitive), total: 2 })
    if (path === '/system/user/deptTree') return json(route, [])
    if (path === '/system/user/99') return json(route, null, { code: 200, data: testUser(sensitive), postIds: [], roleIds: [], posts: [], roles: [] })
    if (path === '/system/user/100') {
      await ordinaryDetailRequested
      return json(route, null, { code: 200, data: ordinaryUser(), postIds: [], roleIds: [], posts: [], roles: [] })
    }
    return json(route, {})
  })
  return { resolveOrdinaryDetail }
}

function routes() {
  return [{ path: '/', component: 'Layout', children: [
    { path: 'system/user', component: 'system/user/index', name: 'SystemUserE2E', meta: { title: '用户管理', icon: 'user' } }
  ] }]
}

function users(sensitive) {
  return [testUser(sensitive), ordinaryUser()]
}

function ordinaryUser() {
  return {
    userId: 100,
    userName: 'ordinary-user',
    nickName: 'Ordinary User',
    userType: '00',
    status: '0',
    dept: { deptName: 'Foundation' },
    createTime: '2026-07-19 10:00:00'
  }
}

function testUser(sensitive) {
  return {
    userId: 99,
    userName: 'foundation-test-user',
    nickName: 'Foundation Test User',
    userType: '99',
    status: '0',
    dept: { deptName: 'Foundation' },
    createTime: '2026-07-19 10:00:00',
    password: sensitive.password,
    passwordHash: sensitive.storedHash
  }
}

function sensitiveFixture() {
  return {
    password: randomBytes(24).toString('base64url'),
    storedHash: randomBytes(32).toString('base64url')
  }
}

function dictionaries() {
  return [{ dictValue: '0', dictLabel: '正常', listClass: 'default', isDefault: 'Y' }]
}

async function json(route, data, envelope) {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope || { code: 200, msg: 'success', data }) })
}
