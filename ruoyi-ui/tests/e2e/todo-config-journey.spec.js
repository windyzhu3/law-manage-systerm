const { test, expect } = require('@playwright/test')
const fs = require('node:fs')
const path = require('node:path')
const { executeSql } = require('./support/mysql-e2e-runner')
const { resolveGuidedArtifactDirectory } = require('./support/guided-artifact-directory')
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
const GUIDED_OUTPUT_ROOT = path.resolve(__dirname, '../../output/playwright/lead-todo-guided-configuration')
const GUIDED_SCREENSHOT_DIR = resolveGuidedArtifactDirectory(process.env.TODO_E2E_ARTIFACT_DIR, GUIDED_OUTPUT_ROOT)
const GUIDED_LEAD_TEMPLATES = Object.freeze([
  {
    code: 'TD-004',
    recipe: '五天实质进展完成',
    eventName: '首联有效',
    eventFields: ['线索负责人', '首联结果'],
    ownerLabels: ['业务对象负责人', '当前业务对象负责人'],
    scheduleLabels: ['每 5 天循环', '自动开启下一轮'],
    labels: ['进展类型', '进展发生时间', '跟进凭证'],
    dodFields: [{ code: 'progressType', label: '进展类型' }, { code: 'progressAt', label: '进展发生时间' }],
    dodMaterials: [{ code: 'FOLLOWUP_PROOF', label: '实质进展凭证' }],
    routes: [
      { value: 'PROGRESS_RECORDED', label: '已记录实质进展', effect: '完成后开启下一轮5天待办',
        targetCode: 'TD-004', targetVersionCode: 'TD-004' }
    ],
    rawCodes: ['PROGRESS_RECORDED', 'FOLLOWUP_PROOF'],
    scenarios: 3,
    screenshot: 'td004-five-day-cycle.png'
  },
  {
    code: 'TD-003',
    recipe: '重试拨打完成',
    eventName: '线索重试窗口到期',
    eventFields: ['当前重试窗口', '线索负责人'],
    ownerLabels: ['业务对象负责人', '当前业务对象负责人'],
    scheduleLabels: ['重试窗口时间轴', 'T0', 'T+1', 'T+2'],
    labels: ['联系结果', '客户姓名', '所在城市', '客户诉求', '是否到所', '联系凭证'],
    dodFields: [{ code: 'contactResult', label: '联系结果' }],
    dodMaterials: [{ code: 'CONTACT_PROOF', label: '联系凭证' }],
    conditionalFields: ['name', 'city', 'demand', 'visited'],
    routes: [
      { value: 'CONNECTED', label: '联系成功', effect: '生成下一待办', targetCode: 'TD-004',
        targetVersionCode: 'TD-004', targetLabel: '5天实质进展待办' },
      { value: 'CONTINUE_CURRENT_WINDOW', label: '本窗口继续', effect: '保留当前待办', targetCode: null,
        targetVersionCode: null },
      { value: 'NEXT_WINDOW', label: '进入下一窗口', effect: '等待系统计划下一窗口', targetCode: null,
        targetVersionCode: null },
      { value: 'EXHAUSTED', label: '全部重试耗尽', effect: '结束当前路径', targetCode: null,
        targetVersionCode: null }
    ],
    rawCodes: ['CONNECTED', 'CONTINUE_CURRENT_WINDOW', 'NEXT_WINDOW', 'EXHAUSTED', 'CONTACT_PROOF'],
    scenarios: 4,
    screenshot: 'td003-retry-timeline.png'
  },
  {
    code: 'TD-002',
    recipe: '主管复核完成',
    eventName: '疑似无效线索已标记',
    eventFields: ['复核主管', '疑似无效原因'],
    ownerLabels: ['事件中的负责人', '复核主管'],
    scheduleLabels: ['办理时长', '工作分钟内'],
    labels: ['复核结果', '复核意见'],
    dodFields: [{ code: 'reviewResult', label: '复核结果' }, { code: 'reviewOpinion', label: '复核意见' }],
    dodMaterials: [],
    routes: [
      { value: 'TRUE_INVALID', label: '确认无效', effect: '结束当前路径', targetCode: null,
        targetVersionCode: null },
      { value: 'MISJUDGED_VALID', label: '误判有效', effect: '生成下一待办', targetCode: 'TD-001',
        targetVersionCode: 'TD-001', targetLabel: '首联待办' }
    ],
    rawCodes: ['TRUE_INVALID', 'MISJUDGED_VALID'],
    scenarios: 3,
    screenshot: 'td002-seven-steps.png'
  }
])

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
    await field.locator('input').nth(1).fill('线索负责人')
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
      new URL(response.url()).pathname === `/prod-api/todo/config/templates/${fixtures.repair.templateId}/journey`
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
    await page.locator('.trigger-step__advanced .el-collapse-item__header').click()
    await expect(page.locator('.condition-row .is-danger')).toBeVisible()
    const templateSave = page.waitForResponse(response =>
      response.request().method() === 'PUT' &&
      new URL(response.url()).pathname === `/prod-api/todo/config/templates/${fixtures.failed.templateId}/journey`
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

  test('GUIDED_LEAD_TEMPLATES keep seven-step configuration, pass governed scenarios, publish and activate one release bundle', async ({ page }) => {
    test.setTimeout(180000)
    fs.mkdirSync(GUIDED_SCREENSHOT_DIR, { recursive: true })
    await loginAs(page, 'todo_config_admin', password)
    const guided = loadGuidedLeadDrafts()

    for (const metadata of GUIDED_LEAD_TEMPLATES) {
      const fixture = guided[metadata.code]
      expect(fixture.status).toBe('DRAFT')
      await assertSevenStepPersistence(page, fixture, metadata)
      await runGovernedScenarioBatch(page, fixture, metadata.scenarios)
      await publishCurrentGuidedDraft(page, fixture)
      expect(loadPublishedVersion(fixture.templateId, fixture.versionId).status).toBe('PUBLISHED')
    }

    const td001 = guided['TD-001']
    await openJourney(page, td001, 'ROUTING')
    const recommendation = page.getByRole('button', { name: '应用首联推荐路由' })
    await expect(recommendation).toBeVisible()
    await recommendation.click()
    await saveCurrentJourney(page, td001)
    await runGovernedScenarioBatch(page, td001, 3)
    await publishCurrentGuidedDraft(page, td001)

    const publishedBundle = loadPublishedLeadReleaseBundle()
    expect(publishedBundle['TD-001']).toBe(td001.versionId)
    for (const metadata of GUIDED_LEAD_TEMPLATES) {
      expect(publishedBundle[metadata.code]).toBe(guided[metadata.code].versionId)
    }
    alignRuntimeAssignmentPolicy(publishedBundle['TD-003'])

    await openJourney(page, td001, 'SIMULATION_PUBLISH')
    const releasePanel = page.getByTestId('lead-release-panel')
    await expect(releasePanel).toBeVisible()
    const activation = releasePanel.getByTestId('activate-lead-release')
    const activationDisabled = await activation.isDisabled()
    if (!activationDisabled) {
      const activationResponse = page.waitForResponse(response =>
        response.request().method() === 'POST' &&
        new URL(response.url()).pathname === '/prod-api/todo/config/lead-release/activate'
      )
      await activation.click()
      await page.getByRole('button', { name: '确认启用' }).click()
      await expectSuccessfulApiResponse(await activationResponse)
    }
    await expect(releasePanel).toContainText('当前组合已启用')
    await page.screenshot({
      path: path.join(GUIDED_SCREENSHOT_DIR, 'lead-release-active-binding.png'),
      fullPage: true
    })

    await page.goto('/todo-engine/todo-release-record')
    const releaseHistory = page.getByRole('main')
    await expect(releaseHistory).toBeVisible()
    for (const metadata of GUIDED_LEAD_TEMPLATES) {
      await expect(releaseHistory).toContainText(metadata.code)
    }
  })

  test('GUIDED_LEAD_RUNTIME executes governed lead branches and five-day recurrence', async ({ page }) => {
    test.setTimeout(240000)
    fs.mkdirSync(GUIDED_SCREENSHOT_DIR, { recursive: true })
    const evidence = {}
    const activeBundle = loadPublishedLeadReleaseBundle()

    await switchIdentity(page, 'todo_config_admin')
    const trueInvalid = await createAndAssignRuntimeLead(page, 'TD001_SUSPECT_INVALID_TRUE_INVALID')
    const trueInvalidProof = createRuntimeProof(trueInvalid, 'CONTACT_PROOF', 'true-invalid')
    const trueInvalidTd001 = await waitForRuntimeTodo(trueInvalid.leadId, 'TD-001')
    await completeTodoLifecycle(page, trueInvalidTd001.todoId, 'true-invalid-td001', suspectInvalidFields('true-invalid'), [trueInvalidProof])
    const trueInvalidTd002 = await waitForRuntimeTodo(trueInvalid.leadId, 'TD-002', trueInvalidTd001.todoId)
    expect(trueInvalidTd002.templateVersionId).toBe(activeBundle['TD-002'])
    await switchIdentity(page, 'todo_publisher')
    await completeTodoLifecycle(page, trueInvalidTd002.todoId, 'true-invalid-td002', {
      reviewResult: 'TRUE_INVALID', reviewOpinion: 'Task 11 confirmed invalid'
    })
    const trueInvalidState = loadRuntimeLeadState(trueInvalid.leadId)
    expect(trueInvalidState.disposition).toBe('DEAD_POOL')
    expect(trueInvalidState.invalidReviewStatus).toBe('CONFIRMED')
    expect(runtimeCount(`select count(*) from todo_instance where business_type='LEAD' and business_id=${trueInvalid.leadId} and previous_todo_id=${trueInvalidTd002.todoId}`)).toBe(0)
    evidence.TD001_SUSPECT_INVALID_TRUE_INVALID = runtimeEvidence(trueInvalid, trueInvalidTd001, trueInvalidTd002)

    await switchIdentity(page, 'todo_config_admin')
    const misjudged = await createAndAssignRuntimeLead(page, 'TD001_SUSPECT_INVALID_MISJUDGED_VALID')
    const misjudgedProof = createRuntimeProof(misjudged, 'CONTACT_PROOF', 'misjudged')
    const misjudgedTd001 = await waitForRuntimeTodo(misjudged.leadId, 'TD-001')
    await completeTodoLifecycle(page, misjudgedTd001.todoId, 'misjudged-td001', suspectInvalidFields('misjudged'), [misjudgedProof])
    const misjudgedTd002 = await waitForRuntimeTodo(misjudged.leadId, 'TD-002', misjudgedTd001.todoId)
    await switchIdentity(page, 'todo_publisher')
    await completeTodoLifecycle(page, misjudgedTd002.todoId, 'misjudged-td002', {
      reviewResult: 'MISJUDGED_VALID', reviewOpinion: 'Task 11 returns to first contact'
    })
    const returnedTd001 = await waitForRuntimeTodo(misjudged.leadId, 'TD-001', misjudgedTd002.todoId)
    expect(returnedTd001.status).toBe('CREATED')
    expect(returnedTd001.templateVersionId).toBe(activeBundle['TD-001'])
    evidence.TD001_SUSPECT_INVALID_MISJUDGED_VALID = runtimeEvidence(misjudged, misjudgedTd001, misjudgedTd002, returnedTd001)

    await switchIdentity(page, 'todo_config_admin')
    const retry = await createAndAssignRuntimeLead(page, 'TD001_UNREACHABLE_TD003_CONNECTED')
    const unreachableProof = createRuntimeProof(retry, 'CONTACT_PROOF', 'unreachable')
    const unreachableTd001 = await waitForRuntimeTodo(retry.leadId, 'TD-001')
    await completeTodoLifecycle(page, unreachableTd001.todoId, 'unreachable-td001', unreachableFields('unreachable'), [unreachableProof])
    const retryTd003 = await waitForRuntimeTodo(retry.leadId, 'TD-003')
    expect(retryTd003.templateVersionId).toBe(activeBundle['TD-003'])
    const retryProof = createRuntimeProof(retry, 'CONTACT_PROOF', 'retry-connected')
    await completeTodoLifecycle(page, retryTd003.todoId, 'retry-td003', connectedRetryFields('retry-connected'), [retryProof])
    const firstTd004 = await waitForRuntimeTodo(retry.leadId, 'TD-004', retryTd003.todoId)
    expect(firstTd004.status).toBe('CREATED')
    expect(firstTd004.templateVersionId).toBe(activeBundle['TD-004'])
    evidence.TD001_UNREACHABLE_TD003_CONNECTED = runtimeEvidence(retry, unreachableTd001, retryTd003, firstTd004)

    const progressProof = createRuntimeProof(retry, 'FOLLOWUP_PROOF', 'progress')
    const progressAt = localDateTime(new Date(Date.now() - 60000))
    const progressBody = await completeTodoLifecycle(page, firstTd004.todoId, 'progress-td004', {
      progressType: 'PHONE', progressAt, remark: 'Task 11 five-day cycle'
    }, [progressProof])
    assertApiSuccess(await authenticatedApi(page, 'POST', `/prod-api/todo/${firstTd004.todoId}/complete`, progressBody))
    const nextTd004 = await waitForRuntimeTodo(retry.leadId, 'TD-004', firstTd004.todoId)
    expect(nextTd004.templateVersionId).toBe(activeBundle['TD-004'])
    const cycle = loadFiveDayCycleEvidence(firstTd004.todoId, nextTd004.todoId)
    expect(cycle.followupCount).toBe(1)
    expect(cycle.planCount).toBe(1)
    expect(cycle.occurrenceCount).toBe(1)
    expect(cycle.nextTodoCount).toBe(1)
    expect(cycle.dueOffsetSeconds).toBe(432000)
    evidence.TD004_PROGRESS_RECORDED_NEXT_TD004 = {
      ...runtimeEvidence(retry, firstTd004, nextTd004),
      followupId: cycle.followupId,
      planId: cycle.planId,
      occurrenceId: cycle.occurrenceId,
      dueOffsetSeconds: cycle.dueOffsetSeconds
    }

    await page.goto('/todo')
    const todoPage = page.locator('.todo-page')
    await expect(todoPage).toBeVisible()
    const search = todoPage.locator('.biz-filter-main .el-input input').first()
    await search.fill(retry.leadNo)
    await search.press('Enter')
    await expect(todoPage).toContainText(retry.leadNo)
    await expect(todoPage).toContainText('5天实质进展待办')
    await page.screenshot({ path: path.join(GUIDED_SCREENSHOT_DIR, 'lead-runtime-next-td004.png'), fullPage: true })
    fs.writeFileSync(path.join(GUIDED_SCREENSHOT_DIR, 'runtime-evidence.json'), `${JSON.stringify(evidence, null, 2)}\n`, 'utf8')
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
  await expect(page.locator('.journey-page > .el-loading-mask')).toBeHidden()
}

function parseMysqlRows(output, columns) {
  return String(output || '').trim().split(/\r?\n/).filter(Boolean).map(line => {
    const values = line.split('\t')
    if (values.length !== columns.length) throw new Error(`Guided Todo query returned ${values.length} columns; expected ${columns.length}`)
    return columns.reduce((row, column, index) => {
      row[column] = values[index] === 'NULL' ? null : values[index]
      return row
    }, {})
  })
}

function loadGuidedLeadDrafts() {
  const database = process.env.TODO_E2E_DB_NAME
  const rows = parseMysqlRows(executeSql(`
    select t.template_code,t.template_id,v.version_id,v.version_no,v.status,v.definition_hash
    from todo_template t
    join todo_template_version v on v.template_id=t.template_id
    join (
      select template_id,max(version_no) version_no
      from todo_template_version where status='DRAFT' group by template_id
    ) latest on latest.template_id=v.template_id and latest.version_no=v.version_no
    where t.template_code in ('TD-001','TD-002','TD-003','TD-004')
    order by t.template_code;
  `, database), ['templateCode', 'templateId', 'versionId', 'versionNo', 'status', 'definitionHash'])
  const result = {}
  for (const row of rows) {
    result[row.templateCode] = {
      ...row,
      templateId: Number(row.templateId),
      versionId: Number(row.versionId),
      versionNo: Number(row.versionNo)
    }
  }
  expect(Object.keys(result)).toEqual(['TD-001', 'TD-002', 'TD-003', 'TD-004'])
  return result
}

function loadPublishedVersion(templateId, versionId) {
  const rows = parseMysqlRows(executeSql(`
    select status,definition_hash from todo_template_version
    where template_id=${Number(templateId)} and version_id=${Number(versionId)};
  `, process.env.TODO_E2E_DB_NAME), ['status', 'definitionHash'])
  if (rows.length !== 1) throw new Error(`Published Todo version ${versionId} was not found`)
  return rows[0]
}

function loadPublishedLeadReleaseBundle() {
  const rows = parseMysqlRows(executeSql(`
    select t.template_code,v.version_id
    from todo_template t join todo_template_version v on v.template_id=t.template_id
    where t.template_code in ('TD-001','TD-002','TD-003','TD-004')
      and v.status='PUBLISHED'
      and v.version_no=(select max(candidate.version_no) from todo_template_version candidate
        where candidate.template_id=t.template_id and candidate.status='PUBLISHED')
    order by t.template_code;
  `, process.env.TODO_E2E_DB_NAME), ['templateCode', 'versionId'])
  return rows.reduce((result, row) => {
    result[row.templateCode] = Number(row.versionId)
    return result
  }, {})
}

function alignRuntimeAssignmentPolicy(td003VersionId) {
  const marker = process.env.TODO_CONFIG_E2E_RUN_MARKER
  executeSql(`
    update biz_lead_assignment_policy
    set retry_rule_json=json_set(retry_rule_json,
          '$.templateVersionId',cast(${Number(td003VersionId)} as unsigned),
          '$.ruleVersionId',cast(${Number(td003VersionId)} as unsigned)),
        row_version=row_version+1,update_by=${sqlLiteral(marker)},update_time=now()
    where create_by=${sqlLiteral(marker)} and business_type='LEAD' and status='ACTIVE';
  `, e2eDatabase())
  expect(runtimeCount(`
    select count(*) from biz_lead_assignment_policy
    where create_by=${sqlLiteral(marker)} and business_type='LEAD' and status='ACTIVE'
      and cast(json_unquote(json_extract(retry_rule_json,'$.templateVersionId')) as unsigned)=${Number(td003VersionId)}
      and cast(json_unquote(json_extract(retry_rule_json,'$.ruleVersionId')) as unsigned)=${Number(td003VersionId)}
  `)).toBe(1)
}

async function assertSevenStepPersistence(page, fixture, metadata) {
  await openJourney(page, fixture, 'EVENT')
  const navigation = page.locator('.journey-step-nav')
  const steps = navigation.locator('.journey-step-nav__item')
  await expect(steps).toHaveCount(7)
  await expect(steps.locator('strong')).toHaveText([
    '业务事件', '触发条件', '负责人', '完成标准', '办理时限', '后续路由', '模拟发布'
  ])
  const event = page.locator('.event-detail')
  await expect(event).toContainText(metadata.eventName)
  for (const label of metadata.eventFields) await expect(event).toContainText(label)
  await expect(event).not.toContainText(/业务字段\s*\d+|undefined|\uFFFD/i)

  const expectedSections = [
    '.event-step', '.trigger-step', '.owner-step', '.dod-step',
    '.sla-step', '.routing-step', '.simulation-publish-step'
  ]
  for (let index = 0; index < expectedSections.length; index += 1) {
    await steps.nth(index).click()
    await expect(page.locator(expectedSections[index])).toBeVisible()
  }

  await steps.nth(2).click()
  const owner = page.locator('.owner-step')
  for (const label of metadata.ownerLabels) await expect(owner).toContainText(label)
  await expect(owner).not.toContainText(/业务字段\s*\d+|用户\s*ID|undefined|\uFFFD/i)

  await steps.nth(4).click()
  const sla = page.locator('.sla-step')
  for (const label of metadata.scheduleLabels) await expect(sla).toContainText(label)
  if (metadata.code !== 'TD-002') {
    await page.screenshot({ path: path.join(GUIDED_SCREENSHOT_DIR, metadata.screenshot), fullPage: true })
  }

  // A newly published downstream template retires the previous version. Refresh
  // governed routing before saving any other step so stale targets remain
  // fail-closed instead of being silently accepted by an unrelated save.
  await refreshGovernedRouteTargets(page, steps, fixture)

  await steps.nth(3).click()
  const recommendedRecipe = page.locator('.recipe-card').filter({ hasText: metadata.recipe }).first()
  await expect(recommendedRecipe).toBeVisible()
  await recommendedRecipe.click()
  await saveCurrentJourney(page, fixture)
  await expect(recommendedRecipe).toHaveClass(/is-selected/)
  for (const label of metadata.labels) await expect(page.locator('.dod-step')).toContainText(label)
  await assertGuidedDodDropdownLabels(page, metadata)
  if (metadata.code === 'TD-002') {
    await page.screenshot({ path: path.join(GUIDED_SCREENSHOT_DIR, metadata.screenshot), fullPage: true })
  }

  await steps.nth(0).click()
  await steps.nth(3).click()
  await expect(page.locator('.recipe-card.is-selected')).toContainText(metadata.recipe)
  assertGuidedDodPersistence(fixture, metadata)

  await steps.nth(0).click()
  await steps.nth(5).click()
  await expect(page.locator('.routing-step .routing-outcomes')).toBeVisible()
  await assertGuidedRouteUi(page, metadata)
  assertGuidedRoutePersistence(fixture, metadata)
  await steps.nth(0).click()
  await steps.nth(5).click()
  await assertGuidedRouteUi(page, metadata)
  if (metadata.code === 'TD-004') {
    await page.reload()
    await expect(page.locator('.journey-step-nav')).toBeVisible()
    await page.locator('.journey-step-nav__item').nth(5).click()
    await expect(page.locator('.routing-step .routing-outcomes')).toBeVisible()
    await assertGuidedRouteUi(page, metadata)
    assertGuidedRoutePersistence(fixture, metadata)
  }
  await expect(page.locator('.journey-page')).not.toContainText(/undefined|业务字段\s*\d+|用户\s*ID/i)
}

async function assertGuidedDodDropdownLabels(page, metadata) {
  const cards = page.locator('.dod-step .dod-config-card')
  await expect(cards).toHaveCount(2)
  const fieldSelect = cards.nth(0).locator('.el-select')
  let dropdown = await openElementSelect(page, fieldSelect)
  for (const field of metadata.dodFields) await expect(dropdown).toContainText(field.label)
  await expect(dropdown).not.toContainText(new RegExp(metadata.rawCodes.join('|')))
  await page.keyboard.press('Escape')
  if (metadata.dodMaterials.length) {
    const materialSelect = cards.nth(1).locator('.el-select')
    dropdown = await openElementSelect(page, materialSelect)
    for (const material of metadata.dodMaterials) await expect(dropdown).toContainText(material.label)
    await expect(dropdown).not.toContainText(new RegExp(metadata.rawCodes.join('|')))
    await page.keyboard.press('Escape')
  }
}

async function openElementSelect(page, select) {
  const filterInput = select.locator('.el-select__input')
  const trigger = await filterInput.count() ? filterInput : select.locator('.el-input__inner').first()
  await trigger.focus()
  await trigger.press('ArrowDown')
  const dropdown = page.locator('.el-select-dropdown:visible').last()
  await expect(dropdown).toBeVisible()
  return dropdown
}

async function assertGuidedRouteUi(page, metadata) {
  const routing = page.locator('.routing-step .routing-outcomes')
  const cards = routing.locator('.routing-outcome')
  await expect(cards).toHaveCount(metadata.routes.length)
  for (let index = 0; index < metadata.routes.length; index += 1) {
    const expected = metadata.routes[index]
    const card = cards.nth(index)
    const selects = card.locator('.routing-outcome__main > .el-select')
    await expect(selects.first().locator('input')).toHaveValue(expected.label)
    await expect(card.locator('.routing-effect-card strong')).toHaveText(expected.effect)
    await expect(card.locator('.routing-outcome__sentence')).toContainText(`“${expected.label}”`)
    const outcomeDropdown = await openElementSelect(page, selects.first())
    await expect(outcomeDropdown).toContainText(expected.label)
    await expect(outcomeDropdown).not.toContainText(new RegExp(metadata.rawCodes.join('|')))
    await page.keyboard.press('Escape')
    if (expected.targetLabel) {
      await expect(selects.nth(1).locator('input')).toHaveValue(expected.targetLabel)
      const targetDropdown = await openElementSelect(page, selects.nth(1))
      await expect(targetDropdown).toContainText(expected.targetLabel)
      await page.keyboard.press('Escape')
    }
  }
  for (const rawCode of metadata.rawCodes) await expect(routing).not.toContainText(rawCode)
}

async function refreshGovernedRouteTargets(page, steps, fixture) {
  await steps.nth(5).click()
  const recommendation = page.locator('.routing-step .business-routing__actions .el-button--primary')
  await expect(recommendation).toBeVisible()
  await recommendation.click()
  await saveCurrentJourney(page, fixture)
}

function assertGuidedRouteTarget(fixture, resultValue, resultLabel, targetTemplateCode, targetVersionId) {
  const rows = parseMysqlRows(executeSql(`
    select coalesce(outcome.result_label,outcome.legacy_label),outcome.target_template_code,
      outcome.target_version_id,outcome.effect_kind
    from todo_template_version version
    join json_table(version.definition_json,'$.routing.config.businessOutcomes[*]' columns(
      legacy_value varchar(64) path '$.value',
      result_value varchar(64) path '$.resultValue',
      legacy_label varchar(128) path '$.label',
      result_label varchar(128) path '$.resultLabel',
      effect_kind varchar(64) path '$.effectKind',
      target_template_code varchar(64) path '$.targetTemplateCode',
      target_version_id bigint path '$.targetVersionId'
    )) outcome
    where version.version_id=${Number(fixture.versionId)}
      and coalesce(outcome.result_value,outcome.legacy_value)=${sqlLiteral(resultValue)};
  `, e2eDatabase()), ['resultLabel', 'targetTemplateCode', 'targetVersionId', 'effectKind'])
  expect(rows).toHaveLength(1)
  expect(rows[0].resultLabel).toBe(resultLabel)
  expect(rows[0].targetTemplateCode).toBe(targetTemplateCode)
  expect(rows[0].targetVersionId == null ? null : Number(rows[0].targetVersionId))
    .toBe(targetVersionId == null ? null : Number(targetVersionId))
  return rows[0]
}

function assertGuidedDodPersistence(fixture, metadata) {
  const definition = loadGuidedDefinition(fixture.versionId)
  const dod = definition.dod && definition.dod.config
  expect(dod.requiredFields).toEqual(metadata.dodFields.map(field => field.code))
  expect((dod.materials || []).map(material => material.type || material.code))
    .toEqual(metadata.dodMaterials.map(material => material.code))
  expect((dod.conditionalRequired || []).map(rule => rule.field))
    .toEqual(metadata.conditionalFields || [])
}

function assertGuidedRoutePersistence(fixture, metadata) {
  const bundle = loadPublishedLeadReleaseBundle()
  for (const route of metadata.routes) {
    const expectedVersion = route.effect === '完成后开启下一轮5天待办'
      ? fixture.versionId
      : (route.targetVersionCode ? bundle[route.targetVersionCode] : null)
    expect(assertGuidedRouteTarget(fixture, route.value, route.label, route.targetCode, expectedVersion).effectKind)
      .toBe(route.effect === '生成下一待办' ? 'NEXT_TEMPLATE' : ({
        '结束当前路径': 'END',
        '保留当前待办': 'RETAIN_CURRENT',
        '等待系统计划下一窗口': 'SCHEDULE_NEXT',
        '完成后开启下一轮5天待办': 'SCHEDULE_SELF'
      })[route.effect])
  }
}

function loadGuidedDefinition(versionId) {
  const rows = parseMysqlRows(executeSql(`
    select definition_json from todo_template_version where version_id=${Number(versionId)};
  `, e2eDatabase()), ['definitionJson'])
  if (rows.length !== 1) throw new Error(`Guided Todo definition ${versionId} was not found`)
  return JSON.parse(rows[0].definitionJson)
}

async function saveCurrentJourney(page, fixture) {
  const response = page.waitForResponse(item =>
    item.request().method() === 'PUT' &&
    new URL(item.url()).pathname === `/prod-api/todo/config/templates/${fixture.templateId}/journey`
  )
  await page.locator('.journey-footer__actions').getByRole('button', { name: '保存', exact: true }).click()
  await expectSuccessfulApiResponse(await response)
}

async function runGovernedScenarioBatch(page, fixture, expectedScenarioCount) {
  await openJourney(page, fixture, 'SIMULATION_PUBLISH')
  const step = page.getByTestId('simulation-publish-step')
  const payloadResponse = page.waitForResponse(response =>
    response.request().method() === 'POST' &&
    new URL(response.url()).pathname === `/prod-api/todo/config/templates/${fixture.templateId}/journey/payload`
  )
  await step.getByRole('button', { name: '一键加载只读样例' }).click()
  await expectSuccessfulApiResponse(await payloadResponse)
  await expect(step.getByTestId('business-object-payload-editor')).toContainText('只读样例')
  const scenarioCards = step.locator('.scenario-card')
  await expect(scenarioCards).toHaveCount(expectedScenarioCount)
  const batchResponse = page.waitForResponse(response =>
    response.request().method() === 'POST' &&
    new URL(response.url()).pathname === `/prod-api/todo/config/templates/${fixture.templateId}/journey/scenarios/batch-simulate`
  )
  await step.getByTestId('batch-scenario-gate').getByRole('button').click()
  await expectSuccessfulApiResponse(await batchResponse)
  await expect(scenarioCards.locator('.el-tag--success')).toHaveCount(expectedScenarioCount)

  const simulationResponse = page.waitForResponse(response =>
    response.request().method() === 'POST' &&
    new URL(response.url()).pathname === `/prod-api/todo/config/templates/${fixture.templateId}/journey/simulate`
  )
  await step.getByTestId('run-journey-simulation').click()
  await expectSuccessfulApiResponse(await simulationResponse)
  await expectFixedTrace(step)
  await expect(step.locator('.simulation-trace li.is-blocked')).toHaveCount(0)
  await expect(step.getByTestId('publish-current-draft')).toBeEnabled()
}

async function publishCurrentGuidedDraft(page, fixture) {
  const response = page.waitForResponse(item =>
    item.request().method() === 'POST' &&
    new URL(item.url()).pathname === `/prod-api/todo/config/release-records/${fixture.versionId}/publish`
  )
  await page.getByTestId('publish-current-draft').click()
  await page.getByRole('button', { name: '确认发布' }).click()
  await expectSuccessfulApiResponse(await response)
  await expect(page.locator('.el-message--success').filter({ hasText: '当前版本已发布' })).toBeVisible()
  await assertPublishedVersionHistory(page, fixture)
}

async function assertPublishedVersionHistory(page, fixture) {
  await page.goto('/todo-engine/todo-release-record')
  await expect(page.getByRole('heading', { name: '发布记录' })).toBeVisible()
  const keyword = page.getByPlaceholder('模板名称或编码')
  await keyword.fill(fixture.templateCode)
  const response = page.waitForResponse(item =>
    item.request().method() === 'GET' &&
    new URL(item.url()).pathname === '/prod-api/todo/config/release-records'
  )
  await keyword.press('Enter')
  const listResponse = await response
  expect(listResponse.status()).toBe(200)
  expect([0, 200]).toContain(Number((await listResponse.json()).code))
  const row = page.locator('.el-table__row').filter({ hasText: fixture.templateCode }).first()
  await expect(row).toContainText(`v${fixture.versionNo}`)
  await expect(row).toContainText('已发布')
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

async function switchIdentity(page, username) {
  await page.context().clearCookies()
  await page.goto('/login')
  await page.evaluate(() => {
    window.localStorage.clear()
    window.sessionStorage.clear()
  })
  await loginAs(page, username, password)
}

function e2eDatabase() {
  const database = process.env.TODO_E2E_DB_NAME
  if (!database) throw new Error('TODO_E2E_DB_NAME is required for runtime acceptance')
  return database
}

function sqlLiteral(value) {
  return `'${String(value == null ? '' : value).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`
}

async function createAndAssignRuntimeLead(page, scenario) {
  const unique = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`.toUpperCase()
  const leadNo = `T11${unique}`.slice(0, 32)
  const marker = process.env.TODO_CONFIG_E2E_RUN_MARKER || 'task11-runtime'
  const rows = parseMysqlRows(executeSql(`
    set @runtime_user_id=(select user_id from sys_user where user_name='todo_config_admin' and del_flag='0' limit 1);
    set @runtime_dept_id=(select dept_id from sys_user where user_id=@runtime_user_id);
    insert into biz_lead(lead_no,lead_name,contact_name,mobile,source_code,status,pool_status,priority,
      owner_id,dept_id,disposition,del_flag,create_by,create_time,update_time,remark)
    values(${sqlLiteral(leadNo)},${sqlLiteral(`Task 11 ${scenario}`)},'Task 11 contact','13800000000','online',
      '0','0','2',null,@runtime_dept_id,'ACTIVE','0',${sqlLiteral(marker)},sysdate(),sysdate(),${sqlLiteral(scenario)});
    set @runtime_lead_id=last_insert_id();
    select @runtime_lead_id,${sqlLiteral(leadNo)},@runtime_user_id,@runtime_dept_id;
  `, e2eDatabase()), ['leadId', 'leadNo', 'ownerId', 'deptId'])
  if (rows.length !== 1) throw new Error(`Runtime lead ${leadNo} could not be created`)
  const lead = {
    leadId: Number(rows[0].leadId),
    leadNo: rows[0].leadNo,
    ownerId: Number(rows[0].ownerId),
    deptId: Number(rows[0].deptId),
    scenario
  }
  assertApiSuccess(await authenticatedApi(page, 'POST', '/prod-api/lead/assign', {
    leadId: lead.leadId,
    ownerId: lead.ownerId,
    reason: `Task 11 runtime ${scenario}`
  }))
  return lead
}

function createRuntimeProof(lead, materialType, label) {
  const unique = `${lead.leadId}-${label}-${Date.now()}`
  const rows = parseMysqlRows(executeSql(`
    set @runtime_user_id=(select user_id from sys_user where user_name='todo_config_admin' and del_flag='0' limit 1);
    set @runtime_dept_id=(select dept_id from sys_user where user_id=@runtime_user_id);
    insert into file_object(logical_name,current_version_no,next_version_no,status,created_by,create_time,update_time,version)
    values(${sqlLiteral(`Task 11 ${label}`)},1,2,'ACTIVE',@runtime_user_id,sysdate(),sysdate(),0);
    set @runtime_file_id=last_insert_id();
    insert into file_object_version(file_object_id,version_no,storage_provider,object_key,original_file_name,
      content_type,size_bytes,sha256,change_description,created_by,create_time)
    values(@runtime_file_id,1,'LOCAL',${sqlLiteral(`todo-e2e/${unique}.txt`)},${sqlLiteral(`${label}.txt`)},
      'text/plain',1,sha2(${sqlLiteral(unique)},256),'Task 11 governed runtime evidence',@runtime_user_id,sysdate());
    insert into file_business_relation(file_object_id,business_type,business_id,material_type,visibility,
      scope_dept_id,scope_user_id,created_by,created_dept_id,active,create_time)
    values(@runtime_file_id,'LEAD',${Number(lead.leadId)},${sqlLiteral(materialType)},'BUSINESS',0,0,
      @runtime_user_id,@runtime_dept_id,1,sysdate());
    select @runtime_file_id;
  `, e2eDatabase()), ['fileObjectId'])
  if (rows.length !== 1) throw new Error(`Runtime proof ${label} could not be created`)
  return Number(rows[0].fileObjectId)
}

async function waitForRuntimeTodo(leadId, templateCode, previousTodoId) {
  const deadline = Date.now() + 45000
  const previous = previousTodoId == null ? '' : ` and previous_todo_id=${Number(previousTodoId)}`
  while (Date.now() < deadline) {
    const rows = parseMysqlRows(executeSql(`
      select todo_id,todo_no,template_code,template_version_id,status,owner_id,owner_dept_id,
        previous_todo_id,root_todo_id,date_format(created_at,'%Y-%m-%dT%H:%i:%s'),
        date_format(due_at,'%Y-%m-%dT%H:%i:%s')
      from todo_instance
      where business_type='LEAD' and business_id=${Number(leadId)}
        and template_code=${sqlLiteral(templateCode)}${previous}
      order by todo_id desc limit 1;
    `, e2eDatabase()), [
      'todoId', 'todoNo', 'templateCode', 'templateVersionId', 'status', 'ownerId', 'ownerDeptId',
      'previousTodoId', 'rootTodoId', 'createdAt', 'dueAt'
    ])
    if (rows.length === 1) {
      const row = rows[0]
      return {
        ...row,
        todoId: Number(row.todoId),
        templateVersionId: Number(row.templateVersionId),
        ownerId: row.ownerId == null ? null : Number(row.ownerId),
        ownerDeptId: row.ownerDeptId == null ? null : Number(row.ownerDeptId),
        previousTodoId: row.previousTodoId == null ? null : Number(row.previousTodoId),
        rootTodoId: row.rootTodoId == null ? null : Number(row.rootTodoId)
      }
    }
    await new Promise(resolve => setTimeout(resolve, 500))
  }
  throw new Error(`Timed out waiting for ${templateCode} for lead ${leadId}`)
}

async function completeTodoLifecycle(page, todoId, actionPrefix, fields, fileObjectIds = []) {
  const prefix = `${actionPrefix}-${todoId}-${Date.now()}`
  const transition = async action => {
    const body = {
      actionId: `${prefix}-${action}`,
      opinion: `Task 11 ${action}`,
      fields: {},
      fileObjectIds: []
    }
    assertApiSuccess(await authenticatedApi(page, 'POST', `/prod-api/todo/${todoId}/${action}`, body))
  }
  await transition('claim')
  await transition('start')
  await transition('submit')
  const completion = {
    actionId: `${prefix}-complete`,
    opinion: 'Task 11 governed completion',
    fields: { ...fields },
    fileObjectIds: [...fileObjectIds]
  }
  assertApiSuccess(await authenticatedApi(page, 'POST', `/prod-api/todo/${todoId}/complete`, completion))
  return completion
}

function localDateTime(value = new Date()) {
  const pad = number => String(number).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}` +
    `T${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
}

function suspectInvalidFields(label) {
  const contactedAt = localDateTime()
  return {
    contactResult: 'SUSPECT_INVALID',
    contactedAt,
    startedAt: contactedAt,
    callChannel: 'MANUAL',
    invalidReasonCode: 'OTHER',
    salesExplanation: `Task 11 ${label} requires supervisor review`,
    manualNotes: `Task 11 ${label} first contact`
  }
}

function unreachableFields(label) {
  const contactedAt = localDateTime()
  return {
    contactResult: 'UNREACHABLE',
    contactedAt,
    startedAt: contactedAt,
    callChannel: 'MANUAL',
    manualNotes: `Task 11 ${label} first contact`
  }
}

function connectedRetryFields(label) {
  const startedAt = localDateTime()
  return {
    contactResult: 'CONNECTED',
    result: 'CONNECTED',
    startedAt,
    callChannel: 'MANUAL',
    attemptCount: 1,
    name: `Task 11 ${label}`,
    city: 'Shanghai',
    demand: 'Runtime acceptance demand',
    visited: '0',
    manualNotes: `Task 11 ${label} retry`
  }
}

function loadRuntimeLeadState(leadId) {
  const rows = parseMysqlRows(executeSql(`
    select disposition,invalid_review_status,first_contact_result,retry_stage,retry_attempt_count
    from biz_lead where lead_id=${Number(leadId)};
  `, e2eDatabase()), ['disposition', 'invalidReviewStatus', 'firstContactResult', 'retryStage', 'retryAttemptCount'])
  if (rows.length !== 1) throw new Error(`Runtime lead ${leadId} was not found`)
  return { ...rows[0], retryAttemptCount: Number(rows[0].retryAttemptCount) }
}

function runtimeCount(sql) {
  const rows = parseMysqlRows(executeSql(sql, e2eDatabase()), ['count'])
  if (rows.length !== 1) throw new Error('Runtime count query must return exactly one row')
  return Number(rows[0].count)
}

function runtimeEvidence(lead, ...todos) {
  return {
    leadId: lead.leadId,
    leadNo: lead.leadNo,
    scenario: lead.scenario,
    leadState: loadRuntimeLeadState(lead.leadId),
    todos: todos.filter(Boolean).map(todo => {
      const persisted = loadRuntimeTodo(todo.todoId)
      const actions = parseMysqlRows(executeSql(`
        select action_type from todo_action_log where todo_id=${Number(todo.todoId)} order by action_log_id;
      `, e2eDatabase()), ['actionType']).map(row => row.actionType)
      if (actions.includes('COMPLETE')) expect(persisted.status).toBe('COMPLETED')
      return {
        todoId: persisted.todoId,
        todoNo: persisted.todoNo,
        templateCode: persisted.templateCode,
        templateVersionId: persisted.templateVersionId,
        status: persisted.status,
        previousTodoId: persisted.previousTodoId,
        actions
      }
    })
  }
}

function loadRuntimeTodo(todoId) {
  const rows = parseMysqlRows(executeSql(`
    select todo_id,todo_no,template_code,template_version_id,status,previous_todo_id
    from todo_instance where todo_id=${Number(todoId)};
  `, e2eDatabase()), [
    'todoId', 'todoNo', 'templateCode', 'templateVersionId', 'status', 'previousTodoId'
  ])
  if (rows.length !== 1) throw new Error(`Runtime Todo ${todoId} was not found during terminal evidence refresh`)
  return {
    ...rows[0],
    todoId: Number(rows[0].todoId),
    templateVersionId: Number(rows[0].templateVersionId),
    previousTodoId: rows[0].previousTodoId == null ? null : Number(rows[0].previousTodoId)
  }
}

function loadFiveDayCycleEvidence(firstTodoId, nextTodoId) {
  const rows = parseMysqlRows(executeSql(`
    select f.followup_id,p.plan_id,o.occurrence_id,
      (select count(*) from biz_lead_followup fact where fact.source_todo_id=${Number(firstTodoId)}) followup_count,
      (select count(*) from todo_schedule_plan plan where plan.previous_todo_id=${Number(firstTodoId)}
        and plan.schedule_purpose='LEAD_PROGRESS_5D') plan_count,
      (select count(*) from todo_schedule_occurrence occurrence where occurrence.plan_id=p.plan_id) occurrence_count,
      (select count(*) from todo_instance next_todo where next_todo.todo_id=${Number(nextTodoId)}
        and next_todo.previous_todo_id=${Number(firstTodoId)} and next_todo.template_code='TD-004') next_todo_count,
      timestampdiff(second,p.first_contact_at,w.due_at) due_offset_seconds
    from biz_lead_followup f
    join todo_schedule_plan p on p.plan_id=f.schedule_plan_id
    join todo_schedule_window w on w.plan_id=p.plan_id and w.window_code='P5D'
    join todo_schedule_occurrence o on o.plan_id=p.plan_id and o.todo_id=${Number(nextTodoId)}
    where f.source_todo_id=${Number(firstTodoId)};
  `, e2eDatabase()), [
    'followupId', 'planId', 'occurrenceId', 'followupCount', 'planCount', 'occurrenceCount',
    'nextTodoCount', 'dueOffsetSeconds'
  ])
  if (rows.length !== 1) throw new Error(`Five-day cycle for Todo ${firstTodoId} was not materialized exactly once`)
  return Object.fromEntries(Object.entries(rows[0]).map(([key, value]) => [key, Number(value)]))
}

function assertApiSuccess(result) {
  expect(result.status, JSON.stringify(result)).toBe(200)
  expect([0, 200], JSON.stringify(result)).toContain(Number(result.code))
  return result
}

async function expectSuccessfulApiResponse(response) {
  expect(response.status()).toBe(200)
  expect((await response.json()).code).toBe(200)
}
