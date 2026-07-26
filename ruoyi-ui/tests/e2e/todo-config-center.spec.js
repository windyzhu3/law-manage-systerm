const { test, expect } = require('@playwright/test')
const { executeSql } = require('./support/mysql-e2e-runner')
const { requireEnv } = require('./support/todo-config-e2e-database')

const realBackend = process.env.TODO_E2E_REAL_BACKEND === 'true'
const password = process.env.TODO_CONFIG_E2E_PASSWORD

test.use({ viewport: { width: 1672, height: 941 } })

test.describe('Todo configuration centre compatibility with real backend', () => {
  test.skip(!realBackend, 'requires the disposable real-backend E2E environment')

  test('legacy configuration routes remain available without mutating published versions', async ({ page }) => {
    await loginAs(page, 'todo_config_admin', password)
    const before = publishedVersionSnapshot()

    for (const route of [
      '/todo-engine/todo-template',
      '/todo-engine/todo-trigger-rule',
      '/todo-engine/todo-dod-rule',
      '/todo-engine/todo-sla-rule',
      '/todo-engine/todo-release-record'
    ]) {
      await page.goto(route)
      await expect(page.locator('.app-main')).toBeVisible()
      await expect(page.locator('img[alt="404"]')).toHaveCount(0)
      await expect(page.getByText(/当前操作没有权限|页面不存在/)).toHaveCount(0)
    }

    await page.goto('/todo-engine/todo-template')
    await expect(page.getByRole('button', { name: '新建配置' })).toBeVisible()
    expect(publishedVersionSnapshot()).toBe(before)
  })
})

async function loginAs(page, username, secret) {
  if (!secret) throw new Error('TODO_CONFIG_E2E_PASSWORD is required')
  await page.goto('/login')
  await page.getByPlaceholder('请输入账号/手机号/邮箱').fill(username)
  await page.getByPlaceholder('请输入密码').fill(secret)
  const loginResponsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST' && new URL(response.url()).pathname === '/prod-api/login',
    { timeout: 15000 }
  )
  await page.getByRole('button', { name: '登录系统' }).click()
  await expectSuccessfulApiResponse(loginResponsePromise)
  await expect(page).not.toHaveURL(/\/login(?:\?|$)/, { timeout: 15000 })
}

function publishedVersionSnapshot() {
  const database = requireEnv('TODO_E2E_DB_NAME')
  return String(executeSql(`
    select count(*),coalesce(sum(version_id),0),
      coalesce(group_concat(concat(version_id,':',status,':',coalesce(definition_hash,'')) order by version_id separator '|'),'')
    from todo_template_version where status='PUBLISHED';
  `, database)).trim()
}

async function expectSuccessfulApiResponse(responsePromise) {
  const response = await responsePromise
  expect(response.status()).toBe(200)
  expect((await response.json()).code).toBe(200)
}
