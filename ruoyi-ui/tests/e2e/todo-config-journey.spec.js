const { test, expect } = require('@playwright/test')
const {
  assertSimulationPersistenceUnchanged,
  cleanupTodoConfiguration,
  loadJourneyFixture,
  snapshotSimulationPersistence
} = require('./support/todo-config-e2e-database')

const realBackend = process.env.TODO_E2E_REAL_BACKEND === 'true'
const password = process.env.TODO_CONFIG_E2E_PASSWORD
const SAMPLE_LEAD_ID = -1001

test.use({ viewport: { width: 1672, height: 941 } })

test.describe.serial('Todo journey deterministic real-backend acceptance', () => {
  test.skip(!realBackend, 'requires the disposable real-backend E2E environment')

  let fixtures

  test.beforeAll(() => {
    fixtures = {
      repair: loadJourneyFixture('REPAIR'),
      failed: loadJourneyFixture('FAILED'),
      warning: loadJourneyFixture('WARNING')
    }
  })

  test.afterAll(() => {
    cleanupTodoConfiguration(fixtures.warning.templateCode)
  })

  test('SCENARIO_SCHEMA_REPAIR_RERUN repairs the actual event schema, returns and reruns', async ({ page }) => {
    await loginAs(page, 'todo_config_admin', password)
    await openJourney(page, fixtures.repair, 'EVENT')

    const eventDetail = page.locator('.event-detail')
    await expect(eventDetail.getByRole('button', { name: '维护事件字段' })).toBeVisible()
    await eventDetail.getByRole('button', { name: '维护事件字段' }).click()

    const drawer = page.locator('.el-drawer__wrapper:visible')
    await expect(drawer).toBeVisible()
    await drawer.getByRole('button', { name: '创建新版本' }).click()
    await expect(drawer.locator('.schema-designer')).toBeVisible()
    await drawer.locator('.schema-designer').getByRole('button', { name: '添加第一个字段' }).click()
    const field = drawer.locator('.schema-designer__row:not(.schema-designer__row--header)').first()
    await field.locator('input').nth(0).fill('ownerId')
    await field.locator('input').nth(1).fill('负责人')
    await field.locator('input').nth(2).fill('11')
    await drawer.getByRole('button', { name: '保存草稿' }).click()
    await expect(drawer).toBeHidden()

    await expect(page).toHaveURL(/step=EVENT/)
    await expect(eventDetail.locator('.event-detail__field-list')).toContainText('ownerId')
    await openJourney(page, fixtures.repair, 'SIMULATION_PUBLISH')
    const step = page.getByTestId('simulation-publish-step')
    await selectBusinessObject(page, step, process.env.TODO_CONFIG_E2E_LEAD_NO)
    await step.getByTestId('run-journey-simulation').click()
    await expectFixedTrace(step)
  })

  test('SCENARIO_FAILED_SIMULATION_BLOCKS_PUBLISH deliberately fails simulation and keeps publish blocked', async ({ page }) => {
    await loginAs(page, 'todo_config_admin', password)
    await openJourney(page, fixtures.failed, 'SIMULATION_PUBLISH')
    const step = page.getByTestId('simulation-publish-step')
    await selectBusinessObject(page, step, process.env.TODO_CONFIG_E2E_LEAD_NO)

    await step.getByTestId('run-journey-simulation').click()
    await expect(step.locator('.simulation-trace li.is-blocked')).toBeVisible()
    await expect(step.getByTestId('publish-current-draft')).toBeDisabled()
  })

  test('SCENARIO_WARNING_REASON_REQUIRED and SCENARIO_SAMPLE_NO_RUNTIME_WRITES publish only after review', async ({ page }) => {
    await loginAs(page, 'todo_config_admin', password)
    await openJourney(page, fixtures.warning, 'SIMULATION_PUBLISH')
    const step = page.getByTestId('simulation-publish-step')
    await selectBusinessObject(page, step, 'DEMO-L-001')

    const before = snapshotSimulationPersistence(fixtures.warning.templateId, 'LEAD', SAMPLE_LEAD_ID)
    await step.getByTestId('run-journey-simulation').click()
    await expectFixedTrace(step)
    const after = snapshotSimulationPersistence(fixtures.warning.templateId, 'LEAD', SAMPLE_LEAD_ID)
    assertSimulationPersistenceUnchanged(before, after)

    const preflight = step.getByTestId('publish-preflight-panel')
    await expect(preflight.locator('.preflight-panel__issue.is-warning')).toContainText('Non-blocking decision')
    const publish = step.getByTestId('publish-current-draft')
    await expect(publish).toBeDisabled()
    await preflight.getByTestId('publish-warning-reason').fill('E2E reviewed the advisory decision before release')
    await expect(publish).toBeEnabled()

    const responsePromise = page.waitForResponse(response =>
      response.request().method() === 'POST' &&
      new URL(response.url()).pathname === `/prod-api/todo/config/release-records/${fixtures.warning.versionId}/publish`
    )
    await publish.click()
    await page.getByRole('button', { name: '确认发布' }).click()
    await expectSuccessfulApiResponse(await responsePromise)
    const published = loadJourneyFixture('WARNING', 'PUBLISHED')
    expect(published.status === 'PUBLISHED').toBeTruthy()
  })

  test('SCENARIO_BUSINESS_ADMIN_BOUNDARY can edit and simulate but cannot maintain resources or publish', async ({ page }) => {
    await loginAs(page, 'todo_business_admin', password)
    await openJourney(page, fixtures.failed, 'SIMULATION_PUBLISH')
    const step = page.getByTestId('simulation-publish-step')
    await expect(step.getByTestId('run-journey-simulation')).toBeVisible()
    await expect(step.getByTestId('publish-current-draft')).toHaveCount(0)
    await expectForbidden(page, 'POST', '/prod-api/todo/config/resources/events', {})
  })

  test('SCENARIO_RESOURCE_ADMIN_BOUNDARY can maintain resources but cannot read or edit template journeys', async ({ page }) => {
    await loginAs(page, 'todo_resource_admin', password)
    await page.goto('/todo-engine/todo-config-resource')
    await expect(page.locator('.app-container').getByRole('button', { name: '新增事件' })).toBeVisible()
    await expectForbidden(page, 'GET', `/prod-api/todo/config/templates/${fixtures.failed.templateId}/journey`)
  })

  test('SCENARIO_PUBLISHER_BOUNDARY can simulate and publish but cannot edit draft definitions', async ({ page }) => {
    await loginAs(page, 'todo_publisher', password)
    await openJourney(page, fixtures.failed, 'SIMULATION_PUBLISH')
    const step = page.getByTestId('simulation-publish-step')
    await expect(step.getByTestId('run-journey-simulation')).toBeVisible()
    await expect(step.getByTestId('publish-current-draft')).toBeVisible()
    await expect(page.locator('.journey-footer__actions').getByRole('button', { name: '保存', exact: true })).toHaveCount(0)
    await expectForbidden(page, 'PUT', `/prod-api/todo/config/template-versions/${fixtures.failed.versionId}`, {})
  })

  test('SCENARIO_AUDITOR_BOUNDARY is read-only and cannot simulate, edit or publish', async ({ page }) => {
    await loginAs(page, 'todo_auditor', password)
    await openJourney(page, fixtures.failed, 'SIMULATION_PUBLISH')
    const step = page.getByTestId('simulation-publish-step')
    await expect(step).toBeVisible()
    await expect(step.getByTestId('run-journey-simulation')).toHaveCount(0)
    await expect(step.getByTestId('publish-current-draft')).toHaveCount(0)
    await expect(page.locator('.journey-footer__actions').getByRole('button', { name: '保存', exact: true })).toHaveCount(0)
    await expectForbidden(page, 'POST', `/prod-api/todo/config/release-records/${fixtures.failed.versionId}/publish`, {})
  })
})

async function openJourney(page, fixture, step) {
  await page.goto(`/todo-engine/todo-template-journey?templateId=${fixture.templateId}&step=${step}`)
  await expect(page.getByTestId('template-journey-shell')).toBeVisible()
}

async function selectBusinessObject(page, step, keyword) {
  const selector = step.getByTestId('business-object-selector')
  await selector.click()
  await selector.locator('input').fill(keyword)
  const option = page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: keyword }).first()
  await expect(option).toBeVisible()
  await option.click()
}

async function expectFixedTrace(step) {
  const trace = step.getByTestId('simulation-trace')
  await expect(trace.locator('.simulation-trace li')).toHaveCount(6)
}

async function loginAs(page, username, secret) {
  if (!secret) throw new Error('TODO_CONFIG_E2E_PASSWORD is required')
  await page.goto('/login')
  await page.locator('input').nth(0).fill(username)
  await page.locator('input[type="password"]').fill(secret)
  const responsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST' && new URL(response.url()).pathname === '/prod-api/login'
  )
  await page.locator('button').filter({ hasText: '登录系统' }).click()
  await expectSuccessfulApiResponse(await responsePromise)
  await expect(page).not.toHaveURL(/\/login(?:\?|$)/, { timeout: 15000 })
}

async function expectForbidden(page, method, path, body) {
  const result = await page.evaluate(async ({ method, path, body }) => {
    const token = document.cookie.split(';').map(item => item.trim()).find(item => item.startsWith('Admin-Token='))
    const headers = token ? { Authorization: `Bearer ${decodeURIComponent(token.split('=').slice(1).join('='))}` } : {}
    if (method !== 'GET') headers['Content-Type'] = 'application/json'
    const response = await fetch(path, {
      method,
      headers,
      body: method === 'GET' ? undefined : JSON.stringify(body)
    })
    let payload = {}
    try { payload = await response.json() } catch (_) { payload = {} }
    return { status: response.status, code: payload.code }
  }, { method, path, body })
  expect(result.status === 403 || Number(result.code) === 403).toBeTruthy()
}

async function expectSuccessfulApiResponse(response) {
  expect(response.status()).toBe(200)
  expect((await response.json()).code).toBe(200)
}
