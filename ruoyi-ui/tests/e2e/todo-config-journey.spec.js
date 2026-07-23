const { test, expect } = require('@playwright/test')
const {
  assertSimulationPersistenceUnchanged,
  cleanupTodoConfiguration,
  loadJourneyEventBinding,
  loadJourneyFixture,
  loadRepairEventResource,
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
    if (fixtures && fixtures.warning) {
      cleanupTodoConfiguration(fixtures.warning.templateCode)
    }
  })

  test('SCENARIO_SCHEMA_REPAIR_ACTIVATE_BIND_RERUN repairs, activates and binds the exact event version before rerun', async ({ page }) => {
    await loginAs(page, 'todo_config_admin', password)
    const originalBinding = loadJourneyEventBinding('REPAIR')
    expect(originalBinding.schemaStatus === 'INCOMPLETE').toBeTruthy()
    expect(originalBinding.resourceStatus === 'ACTIVE').toBeTruthy()
    await openJourney(page, fixtures.repair, 'EVENT')

    const eventDetail = page.locator('.event-detail')
    await expect(eventDetail.locator('.event-detail__facts')).toContainText(`v${originalBinding.payloadVersion}`)
    await expect(eventDetail.locator('.event-detail__heading .el-tag')).toHaveClass(/el-tag--danger/)
    await expect(eventDetail.getByRole('button', { name: '维护事件字段' })).toBeVisible()
    await eventDetail.getByRole('button', { name: '维护事件字段' }).click()

    const drawer = page.locator('.el-drawer__wrapper:visible')
    await expect(drawer).toBeVisible()
    const versionCreation = page.waitForResponse(response =>
      response.request().method() === 'POST' &&
      /\/prod-api\/todo\/config\/resources\/events\/\d+\/versions$/.test(new URL(response.url()).pathname)
    )
    await drawer.getByRole('button', { name: '创建新版本' }).click()
    await expectSuccessfulApiResponse(await versionCreation)
    await expect(drawer.locator('.event-resource-meta')).toContainText(`v${originalBinding.payloadVersion + 1}`)
    await expect(drawer.locator('.schema-designer')).toBeVisible()
    await drawer.locator('.schema-designer').getByRole('button', { name: '添加第一个字段' }).click()
    const field = drawer.locator('.schema-designer__row:not(.schema-designer__row--header)').first()
    await field.locator('input').nth(0).fill('ownerId')
    await field.locator('input').nth(1).fill('负责人')
    await field.locator('.el-select').click()
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').nth(1).click()
    await field.locator('.el-input input').last().fill('11')
    await drawer.locator('button:has(.el-icon-magic-stick)').click()
    await drawer.getByRole('button', { name: '保存草稿' }).click()
    await expect(drawer).toBeHidden()

    const repairedDraft = loadRepairEventResource('DRAFT')
    expect(repairedDraft.eventType === originalBinding.eventType).toBeTruthy()
    expect(repairedDraft.payloadVersion > originalBinding.payloadVersion).toBeTruthy()
    expect(repairedDraft.schemaStatus === 'READY').toBeTruthy()
    expect(repairedDraft.schemaFieldCount > 0).toBeTruthy()
    await activateEventResource(page, repairedDraft)
    const repairedResource = loadRepairEventResource('ACTIVE')
    expect(repairedResource.eventCatalogId === repairedDraft.eventCatalogId).toBeTruthy()

    await openJourney(page, fixtures.repair, 'EVENT')
    const repairedOption = page.locator('.event-option').filter({ hasText: `v${repairedResource.payloadVersion}` })
      .filter({ hasText: 'E2E schema repair' }).first()
    await expect(repairedOption).toBeVisible()
    const templateSave = page.waitForResponse(response =>
      response.request().method() === 'PUT' &&
      new URL(response.url()).pathname === `/prod-api/todo/config/template-versions/${fixtures.repair.versionId}`
    )
    await repairedOption.click()
    await page.locator('.journey-footer__actions').getByRole('button', { name: '保存', exact: true }).click()
    await expectSuccessfulApiResponse(await templateSave)

    await expect(eventDetail.locator('.event-detail__facts')).toContainText(`v${repairedResource.payloadVersion}`)
    await expect(eventDetail.locator('.event-detail__heading .el-tag')).toHaveClass(/el-tag--success/)
    await expect(eventDetail.locator('.event-detail__field-list')).toContainText('线索负责人')
    const repairedBinding = loadJourneyEventBinding('REPAIR')
    expect(repairedBinding.eventType === repairedResource.eventType).toBeTruthy()
    expect(repairedBinding.payloadVersion === repairedResource.payloadVersion).toBeTruthy()
    expect(repairedBinding.payloadVersion > originalBinding.payloadVersion).toBeTruthy()
    expect(repairedBinding.schemaStatus === 'READY').toBeTruthy()
    expect(repairedBinding.resourceStatus === 'ACTIVE').toBeTruthy()
    expect(repairedBinding.schemaFieldCount > 0).toBeTruthy()

    await openJourney(page, fixtures.repair, 'SIMULATION_PUBLISH')
    const step = page.getByTestId('simulation-publish-step')
    await selectBusinessObject(page, step, 'DEMO-L-001')
    await step.getByTestId('run-journey-simulation').click()
    await expectFixedTrace(step)
    await expect(step.locator('.simulation-trace li.is-blocked')).toHaveCount(0)
  })

  test('SCENARIO_FAILED_SIMULATION_REPAIR_RERUN blocks publish, repairs the trigger and reruns successfully', async ({ page }) => {
    await loginAs(page, 'todo_config_admin', password)
    await openJourney(page, fixtures.failed, 'SIMULATION_PUBLISH')
    let step = page.getByTestId('simulation-publish-step')
    await selectBusinessObject(page, step, process.env.TODO_CONFIG_E2E_LEAD_NO)

    await step.getByTestId('run-journey-simulation').click()
    await expectFixedTrace(step)
    await expect(step.locator('.simulation-trace li').first()).toHaveClass(/is-blocked/)
    await expect(step.getByTestId('publish-current-draft')).toBeDisabled()

    await openJourney(page, fixtures.failed, 'TRIGGER')
    await expect(page.locator('.condition-row')).toHaveCount(1)
    const templateSave = page.waitForResponse(response =>
      response.request().method() === 'PUT' &&
      new URL(response.url()).pathname === `/prod-api/todo/config/template-versions/${fixtures.failed.versionId}`
    )
    await page.locator('.condition-row .is-danger').click()
    await page.locator('.journey-footer__actions').getByRole('button', { name: '保存', exact: true }).click()
    await expectSuccessfulApiResponse(await templateSave)

    await openJourney(page, fixtures.failed, 'SIMULATION_PUBLISH')
    step = page.getByTestId('simulation-publish-step')
    await selectBusinessObject(page, step, process.env.TODO_CONFIG_E2E_LEAD_NO)
    await step.getByTestId('run-journey-simulation').click()
    await expectFixedTrace(step)
    await expect(step.locator('.simulation-trace li.is-blocked')).toHaveCount(0)
    await expect(step.getByTestId('publish-current-draft')).toBeEnabled()
  })

  test('SCENARIO_WARNING_REASON_REQUIRED and SCENARIO_SAMPLE_NO_RUNTIME_WRITES publish only after review', async ({ page }) => {
    await loginAs(page, 'todo_config_admin', password)
    await openJourney(page, fixtures.warning, 'SIMULATION_PUBLISH')
    await expect(page.locator('.journey-page__title h1')).not.toContainText(/[?\uFFFD]/)
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
    await expectForbidden(page, 'POST', '/prod-api/todo/config/resources/events', {
      eventType: `E2E_FORBIDDEN_${process.env.TODO_CONFIG_E2E_RUN_MARKER}`,
      payloadVersion: 1,
      eventName: 'Forbidden resource probe',
      businessObjectType: 'LEAD',
      sourceModule: 'lead',
      payloadSchemaJson: '{"type":"object","properties":{"ownerId":{"type":"integer"}}}',
      samplePayloadJson: '{"ownerId":11}',
      status: 'DRAFT',
      actionId: `forbidden-resource-${process.env.TODO_CONFIG_E2E_RUN_MARKER}`,
      expectedVersion: 0
    })
  })

  test('SCENARIO_RESOURCE_ADMIN_BOUNDARY can maintain resources but cannot read or edit template journeys', async ({ page }) => {
    await loginAs(page, 'todo_resource_admin', password)
    await page.goto('/todo-engine/todo-config-resource')
    await expect(page.getByRole('button', { name: /新增事件/ })).toBeVisible()
    await expectForbidden(page, 'GET', `/prod-api/todo/config/templates/${fixtures.failed.templateId}/journey`)
  })

  test('SCENARIO_PUBLISHER_BOUNDARY can simulate and publish but cannot edit draft definitions', async ({ page }) => {
    await loginAs(page, 'todo_publisher', password)
    await openJourney(page, fixtures.failed, 'SIMULATION_PUBLISH')
    const step = page.getByTestId('simulation-publish-step')
    await expect(step.getByTestId('run-journey-simulation')).toBeVisible()
    await expect(step.getByTestId('publish-current-draft')).toBeVisible()
    await expect(page.locator('.journey-footer__actions').getByRole('button', { name: '保存', exact: true })).toHaveCount(0)
    await expectForbidden(page, 'PUT', `/prod-api/todo/config/template-versions/${fixtures.failed.versionId}`, {
      actionId: `forbidden-draft-${process.env.TODO_CONFIG_E2E_RUN_MARKER}`,
      versionId: fixtures.failed.versionId
    })
  })

  test('SCENARIO_AUDITOR_BOUNDARY is read-only and cannot simulate, edit or publish', async ({ page }) => {
    await loginAs(page, 'todo_auditor', password)
    await openJourney(page, fixtures.failed, 'SIMULATION_PUBLISH')
    const step = page.getByTestId('simulation-publish-step')
    await expect(step).toBeVisible()
    await expect(step.getByTestId('run-journey-simulation')).toHaveCount(0)
    await expect(step.getByTestId('publish-current-draft')).toHaveCount(0)
    await expect(page.locator('.journey-footer__actions').getByRole('button', { name: '保存', exact: true })).toHaveCount(0)
    await expectForbidden(page, 'POST', `/prod-api/todo/config/release-records/${fixtures.failed.versionId}/publish`, {
      actionId: `forbidden-publish-${process.env.TODO_CONFIG_E2E_RUN_MARKER}`,
      versionId: fixtures.failed.versionId,
      expectedDefinitionHash: 'permission-probe'
    })
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
  await expect(trace.locator('ol > li')).toHaveCount(6)
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
    return { status: response.status, code: payload.code, msg: payload.msg }
  }, { method, path, body })
  expect(result.status === 403 || Number(result.code) === 403, JSON.stringify(result)).toBeTruthy()
}

async function activateEventResource(page, resource) {
  const path = `/prod-api/todo/config/resources/events/${resource.eventCatalogId}/status`
  const result = await authenticatedApi(page, 'POST', path, {
    status: 'ACTIVE',
    actionId: `e2e-activate-event-${resource.eventCatalogId}-${Date.now()}`,
    expectedVersion: resource.version
  })
  expect(result.status).toBe(200)
  expect(Number(result.code)).toBe(200)
}

async function authenticatedApi(page, method, path, body) {
  return page.evaluate(async ({ method, path, body }) => {
    const token = document.cookie.split(';').map(item => item.trim()).find(item => item.startsWith('Admin-Token='))
    if (!token) throw new Error('Authenticated Todo E2E API call requires Admin-Token')
    const response = await fetch(path, {
      method,
      headers: {
        Authorization: `Bearer ${decodeURIComponent(token.split('=').slice(1).join('='))}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(body)
    })
    const payload = await response.json()
    return { status: response.status, code: payload.code, message: payload.msg, data: payload.data }
  }, { method, path, body })
}

async function expectSuccessfulApiResponse(response) {
  expect(response.status()).toBe(200)
  expect((await response.json()).code).toBe(200)
}
