const { test, expect } = require('@playwright/test')

test('system user UI labels only test identities without exposing credentials', async ({ page }) => {
  const seedPassword = 'e2e-seed-password'
  const bcryptHash = '$2a$10$7EqJtq98hPqEX7fNZaFWoOeR6Tq4U6iD5R4L4J1dJqAZfU.U2Wcqe'
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => {
    if (message.type() === 'error') errors.push(message.text())
  })
  await setupSystemUserApp(page, seedPassword, bcryptHash)

  await page.goto('/system/user')
  const testUserRow = page.locator('.el-table__body tr').filter({ hasText: 'foundation-test-user' }).first()
  await expect(testUserRow).toBeVisible()
  await expect(page.getByText('测试身份', { exact: true })).toHaveCount(1)

  await testUserRow.getByText('foundation-test-user', { exact: true }).click()
  const drawer = page.locator('.el-drawer__wrapper:visible')
  await expect(drawer).toBeVisible()
  await expect(drawer.getByText('测试身份', { exact: true })).toHaveCount(1)
  await expect(page.getByText('测试身份', { exact: true })).toHaveCount(2)

  const dom = await page.locator('body').textContent()
  expect(dom).not.toContain(seedPassword)
  expect(dom).not.toContain(bcryptHash)
  expect(dom).not.toMatch(/\$2[aby]\$/)
  expect(errors).toEqual([])
})

async function setupSystemUserApp(page, seedPassword, bcryptHash) {
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
    if (path.startsWith('/system/user/list')) return json(route, null, { code: 200, rows: users(), total: 2 })
    if (path === '/system/user/deptTree') return json(route, [])
    if (path === '/system/user/99') return json(route, null, { code: 200, data: testUser(seedPassword, bcryptHash), postIds: [], roleIds: [], posts: [], roles: [] })
    return json(route, {})
  })
}

function routes() {
  return [{ path: '/', component: 'Layout', children: [
    { path: 'system/user', component: 'system/user/index', name: 'SystemUserE2E', meta: { title: '用户管理', icon: 'user' } }
  ] }]
}

function users() {
  return [testUser('e2e-seed-password', '$2a$10$7EqJtq98hPqEX7fNZaFWoOeR6Tq4U6iD5R4L4J1dJqAZfU.U2Wcqe'), {
    userId: 100,
    userName: 'ordinary-user',
    nickName: 'Ordinary User',
    userType: '00',
    status: '0',
    dept: { deptName: 'Foundation' },
    createTime: '2026-07-19 10:00:00'
  }]
}

function testUser(password, passwordHash) {
  return {
    userId: 99,
    userName: 'foundation-test-user',
    nickName: 'Foundation Test User',
    userType: '99',
    status: '0',
    dept: { deptName: 'Foundation' },
    createTime: '2026-07-19 10:00:00',
    password,
    passwordHash
  }
}

function dictionaries() {
  return [{ dictValue: '0', dictLabel: '正常', listClass: 'default', isDefault: 'Y' }]
}

async function json(route, data, envelope) {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope || { code: 200, msg: 'success', data }) })
}
