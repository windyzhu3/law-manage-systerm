const { test, expect } = require('@playwright/test')

const realBackend = process.env.TODO_E2E_REAL_BACKEND === 'true'
const password = process.env.TODO_CONFIG_E2E_PASSWORD
const templateId = Number(process.env.TODO_CONFIG_E2E_JOURNEY_TEMPLATE_ID || 0)

test.use({ viewport: { width: 1672, height: 941 } })

test.describe.serial('Todo journey simulation publish with real backend', () => {
  test.skip(!realBackend || !templateId, 'requires a disposable preflight-valid journey draft')

  test('simulation publish hydrates real and sample payloads, repairs, reruns and publishes immutably', async ({ page }) => {
    await loginAs(page, 'todo_config_admin', password)
    await page.goto(`/todo-engine/todo-template-journey?templateId=${templateId}&step=SIMULATION_PUBLISH`)

    const step = page.getByTestId('simulation-publish-step')
    await expect(step).toBeVisible()
    await expect(step.getByText('用真实业务对象试运行，再安全发布')).toBeVisible()

    const selector = step.getByTestId('business-object-selector')
    await selector.click()
    await selector.locator('input').fill(process.env.TODO_CONFIG_E2E_LEAD_NO)
    const realOption = page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first()
    await expect(realOption).toContainText(process.env.TODO_CONFIG_E2E_LEAD_NO)
    await realOption.click()

    await step.getByTestId('run-journey-simulation').click()
    await expect(step.getByText('载荷覆盖率')).toBeVisible()
    await expect(step.getByLabel('敏感字段已脱敏')).toHaveText('••••••')
    await expectFixedTrace(step)
    await expect(step.getByText('当前草稿试运行通过')).toBeVisible()

    const repair = step.getByRole('button', { name: '返回修复' }).first()
    if (await repair.isVisible()) {
      await repair.click()
      await expect(page).toHaveURL(/step=(EVENT|OWNER|DOD|SLA|ROUTING)/)
      await page.goto(`/todo-engine/todo-template-journey?templateId=${templateId}&step=SIMULATION_PUBLISH`)
      await step.getByTestId('run-journey-simulation').click()
      await expectFixedTrace(step)
    }

    // Searching a value unavailable to the real actor-scoped directory falls back to the governed sample catalog.
    await selector.click()
    await selector.locator('input').fill('示例')
    const sampleOption = page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: '只读样例' }).first()
    await expect(sampleOption).toBeVisible()
    await sampleOption.click()
    await expect(step.getByText('这是只读样例，仅用于验证配置，绝不会生成或写入运行时待办。')).toBeVisible()
    await step.getByTestId('run-journey-simulation').click()
    await expectFixedTrace(step)

    const preflight = step.getByTestId('publish-preflight-panel')
    await preflight.getByRole('button', { name: '重新预检' }).click()
    await expect(preflight.getByText('草稿版本一致')).toBeVisible()
    const warningReason = preflight.getByTestId('publish-warning-reason')
    if (await warningReason.isVisible()) await warningReason.fill('真实后端 E2E 已复核警告')

    const publish = step.getByTestId('publish-current-draft')
    await expect(publish).toBeEnabled()
    await publish.click()
    await page.getByRole('button', { name: '确认发布' }).click()
    await expect(page.getByText('当前版本已发布')).toBeVisible()
    await expect(page.getByText('已发布 · 只读')).toBeVisible()
  })

  test('role boundaries hide simulation and publish controls without client-side privilege escalation', async ({ page }) => {
    const readOnlyUser = process.env.TODO_CONFIG_E2E_AUDITOR_USER
    test.skip(!readOnlyUser, 'requires the test-only auditor identity')
    await loginAs(page, readOnlyUser, password)
    await page.goto(`/todo-engine/todo-template-journey?templateId=${templateId}&step=SIMULATION_PUBLISH`)
    const step = page.getByTestId('simulation-publish-step')
    await expect(step).toBeVisible()
    await expect(step.getByTestId('run-journey-simulation')).toHaveCount(0)
    await expect(step.getByTestId('publish-current-draft')).toHaveCount(0)
  })
})

async function expectFixedTrace(step) {
  const trace = step.getByTestId('simulation-trace')
  for (const title of [
    '1. 事件与触发',
    '2. 负责人解析',
    '3. 完成标准',
    '4. 办理时限',
    '5. 后续路由',
    '6. 员工待办预览'
  ]) await expect(trace.getByText(title, { exact: true })).toBeVisible()
}

async function loginAs(page, username, secret) {
  if (!secret) throw new Error('TODO_CONFIG_E2E_PASSWORD is required')
  await page.goto('/login')
  await page.getByPlaceholder('请输入账号/手机号/邮箱').fill(username)
  await page.getByPlaceholder('请输入密码').fill(secret)
  const responsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST' && new URL(response.url()).pathname === '/prod-api/login'
  )
  await page.getByRole('button', { name: '登录系统' }).click()
  const response = await responsePromise
  expect(response.status()).toBe(200)
  expect((await response.json()).code).toBe(200)
  await expect(page).not.toHaveURL(/\/login(?:\?|$)/)
}
