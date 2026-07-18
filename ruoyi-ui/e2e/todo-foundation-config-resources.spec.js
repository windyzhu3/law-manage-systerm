const { test, expect } = require('@playwright/test')

async function json(route, data, envelope) {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope || { code: 200, msg: 'success', data }) })
}

async function choose(page, formItem, label) {
  await formItem.locator('input').click()
  const dropdown = page.locator('.el-select-dropdown:visible').last()
  await dropdown.locator('.el-select-dropdown__item').getByText(label, { exact: true }).click()
  await expect(dropdown).toBeHidden()
}

function formItem(dialog, label) {
  return dialog.locator('.el-form-item').filter({ hasText: label }).first()
}

async function setupConfig(page, options = {}) {
  const state = {
    triggers: [], calendars: [], decisions: [], failNextTrigger: false, decisionUpdates: 0, admissionUpdates: 0, acceptanceUpdates: 0,
    foundationAdmission: {
      overallStatus: 'NOT_ADMITTED', admitted: false, readyGateCount: 2, totalGateCount: 8,
      gates: [
        { gateCode: 'G-01', title: '阻断决策责任与冻结', status: 'BLOCKED', ready: false, technicalReady: false, evidenceStatus: 'NOT_REQUIRED', completed: 0, total: 20, summary: '0/12 已分配，0/8 阶段一已关闭', blockers: ['12 个阻断决策尚未全部分配责任人、责任角色和截止时间'] },
        { gateCode: 'G-02', title: '业务字典与稳定角色键', status: 'BLOCKED', ready: false, technicalReady: false, evidenceStatus: 'OPEN', completed: 0, total: 2, summary: '技术/来源门禁 BLOCKED，证据 OPEN', blockers: ['业务字典或稳定角色键尚未全部确认并落地'] },
        { gateCode: 'G-03', title: 'TD-001～TD-025 定义包', status: 'READY', ready: true, technicalReady: true, evidenceStatus: 'NOT_REQUIRED', completed: 25, total: 25, summary: '25/25 READY，150/150 验收引用', blockers: [] },
        { gateCode: 'G-04', title: '历史数据迁移方案', status: 'BLOCKED', ready: false, technicalReady: false, evidenceStatus: 'OPEN', completed: 0, total: 2, summary: '技术/来源门禁 BLOCKED，证据 OPEN', blockers: ['历史迁移方案或运行态校验尚未全部就绪'] },
        { gateCode: 'G-05', title: '文件中心安全验收', status: 'BLOCKED', ready: false, technicalReady: false, evidenceStatus: 'OPEN', completed: 0, total: 2, summary: '技术/来源门禁 BLOCKED，证据 OPEN', blockers: ['文件安全运行态或来源要求尚未全部就绪'] },
        { gateCode: 'G-06', title: '收费节点与风险公式', status: 'BLOCKED', ready: false, technicalReady: false, evidenceStatus: 'OPEN', completed: 0, total: 2, summary: '技术/来源门禁 BLOCKED，证据 OPEN', blockers: ['财务结构、公式决策或签字尚未全部就绪'] },
        { gateCode: 'G-07', title: '阶段一验收包', status: 'BLOCKED', ready: false, technicalReady: false, evidenceStatus: 'OPEN', completed: 0, total: 2, summary: '技术/来源门禁 BLOCKED，证据 OPEN', blockers: ['阶段一场景、黄金数据、AT 映射或独立评审尚未全部就绪'] },
        { gateCode: 'G-08', title: 'Flyway 与幂等约束', status: 'READY', ready: true, technicalReady: true, evidenceStatus: 'NOT_REQUIRED', completed: 6, total: 6, summary: '迁移 READY，失败 0，幂等索引 4/4', blockers: [] }
      ]
    },
    admissionEvidence: [{ evidenceId: 1, evidenceCode: 'G02-DICTIONARY-ROLE', gateCode: 'G-02', title: '业务字典与稳定角色键', status: 'OPEN', deliveryPhase: 'PHASE_ONE', version: 0 }],
    foundationResources: {
      gateCode: 'G-02', total: 4, ready: 1, sourceUnresolved: 1, runtimeMissing: 1, runtimeIncomplete: 1, gateReady: false,
      resources: [
        { resourceId: 1, resourceType: 'DICTIONARY', resourceCode: 'law_business_line', domainCode: 'QUOTE_CONTRACT', deliveryPhase: 'PHASE_ONE', sourceStatus: 'CONFIRMED', expectedValuesJson: '[{"value":"NON_LITIGATION"},{"value":"COMPREHENSIVE"},{"value":"EXECUTION"}]', minimumActiveItems: 3, activeItemCount: 3, expectedItemCount: 3, readinessStatus: 'READY', sourceRef: 'doc/v0.2-prd-readiness-gap-analysis.md:185', remark: '仓库明确三条业务线稳定值' },
        { resourceId: 2, resourceType: 'DICTIONARY', resourceCode: 'law_lead_invalid_level', domainCode: 'LEAD_CUSTOMER', deliveryPhase: 'PHASE_ONE', sourceStatus: 'NEEDS_DECISION', decisionRef: 'Q-008', minimumActiveItems: 1, activeItemCount: 0, expectedItemCount: 0, readinessStatus: 'SOURCE_UNRESOLVED' },
        { resourceId: 3, resourceType: 'ROLE', resourceCode: 'sales', domainCode: 'CROSS_DOMAIN', deliveryPhase: 'PHASE_ONE', sourceStatus: 'CONFIRMED', minimumActiveItems: 1, activeItemCount: 0, expectedItemCount: 0, readinessStatus: 'RUNTIME_MISSING' },
        { resourceId: 4, resourceType: 'DICTIONARY', resourceCode: 'law_followup_progress_type', domainCode: 'LEAD_CUSTOMER', deliveryPhase: 'PHASE_ONE', sourceStatus: 'CONFIRMED', minimumActiveItems: 6, activeItemCount: 4, expectedItemCount: 6, readinessStatus: 'RUNTIME_INCOMPLETE' }
      ]
    },
    historicalMigration: {
      gateCode: 'G-04', total: 4, ready: 1, sourceUnresolved: 2, runtimeMissing: 1, runtimeInvalid: 0, gateReady: false,
      historicalCaseCount: 12, historicalTodoCount: 7, orphanTodoVersionCount: 0, caseBusinessLineColumnExists: false,
      requirements: [
        { requirementId: 1, requirementCode: 'CASE_BUSINESS_LINE_SCHEMA', requirementName: '历史案件业务线字段', sourceStatus: 'CONFIRMED', readinessStatus: 'RUNTIME_MISSING', sourceRef: 'doc/v0.2-prd-readiness-gap-analysis.md:220', remark: '运行态必须真实存在该列' },
        { requirementId: 2, requirementCode: 'HISTORICAL_CASE_DEFAULT', requirementName: '历史案件默认业务线策略', sourceStatus: 'NEEDS_DECISION', readinessStatus: 'SOURCE_UNRESOLVED', sourceRef: 'doc/v0.2-prd-readiness-gap-analysis.md:326', remark: '研发不得代选' },
        { requirementId: 3, requirementCode: 'BACKFILL_VALIDATION_SQL', requirementName: '回填校验SQL', sourceStatus: 'NEEDS_EVIDENCE', readinessStatus: 'SOURCE_UNRESOLVED', sourceRef: 'doc/v0.2-foundation-admission-report.md:105' },
        { requirementId: 4, requirementCode: 'TODO_VERSION_REFERENCE', requirementName: '历史待办固定版本引用', sourceStatus: 'CONFIRMED', readinessStatus: 'READY', sourceRef: 'V0_16_1__todo_engine.sql:22' }
      ]
    },
    historicalMigrationPreflight: {
      gateCode: 'G-04', generatedAt: '2026-07-18T08:30:00Z', activeCaseCount: 12, deletedCaseCount: 3,
      historicalTodoCount: 7, orphanTodoVersionCount: 0, exceptionCandidateCount: 12,
      groups: [
        { caseStatus: 'OPEN', caseType: 'LITIGATION', caseCount: 8 },
        { caseStatus: 'CLOSED', caseType: '<NULL>', caseCount: 4 }
      ]
    },
    fileSecurity: {
      gateCode: 'G-05', total: 7, ready: 6, sourceUnresolved: 1, runtimeMissing: 0, gateReady: false,
      objectModelReady: true, tokenControlReady: true, accessAuditReady: true, cleanupCompensationReady: true,
      requirements: [
        { requirementId: 1, requirementCode: 'SINGLE_USE_RELATION_TOKEN', requirementName: '单次关系绑定访问令牌', sourceStatus: 'CONFIRMED', readinessStatus: 'READY', sourceRef: 'FileObjectService.java:229-236' },
        { requirementId: 2, requirementCode: 'PRD_MATERIAL_TYPE_E2E', requirementName: 'PRD材料类型端到端验收', sourceStatus: 'CONFIRMED', readinessStatus: 'READY', sourceRef: 'FileMaterialEndToEndTest.java' },
        { requirementId: 3, requirementCode: 'SECURITY_REVIEW_SIGNOFF', requirementName: '独立安全评审签字', sourceStatus: 'NEEDS_REVIEW', readinessStatus: 'SOURCE_UNRESOLVED', sourceRef: 'gap-analysis.md:327' }
      ]
    },
    financeReadiness: {
      gateCode: 'G-06', total: 9, ready: 3, sourceUnresolved: 3, runtimeMissing: 3, gateReady: false,
      feePlanCoreColumns: 4, nodeFeeColumns: 0, financeSupportTables: 0, riskSchemaObjects: 0,
      requirements: [
        { requirementId: 1, requirementCode: 'FEE_PLAN_CORE_PRECISION', requirementName: '收费计划金额精度与状态字段', sourceStatus: 'CONFIRMED', readinessStatus: 'READY', sourceRef: 'biz_contract_fee_plan' },
        { requirementId: 2, requirementCode: 'NODE_FEE_SCHEMA', requirementName: '节点收费触发字段', sourceStatus: 'CONFIRMED', readinessStatus: 'RUNTIME_MISSING', decisionRef: 'Q-009', sourceRef: 'information_schema.columns' },
        { requirementId: 3, requirementCode: 'RECEIVABLE_SUPPORT_TABLES', requirementName: '应收、催收、退款事实表', sourceStatus: 'CONFIRMED', readinessStatus: 'RUNTIME_MISSING', decisionRef: 'Q-009', sourceRef: 'information_schema.tables' },
        { requirementId: 4, requirementCode: 'RISK_FEE_SCHEMA', requirementName: '风险收费计算结构', sourceStatus: 'CONFIRMED', readinessStatus: 'RUNTIME_MISSING', decisionRef: 'Q-012', sourceRef: 'information_schema' },
        { requirementId: 5, requirementCode: 'Q009_NODE_COLLECTION_POLICY', requirementName: '收费节点与催收责任规则', sourceStatus: 'NEEDS_DECISION', readinessStatus: 'SOURCE_UNRESOLVED', decisionRef: 'Q-009', sourceRef: 'gap-analysis.md:Q-009' },
        { requirementId: 6, requirementCode: 'Q012_RISK_FORMULA_POLICY', requirementName: '风险收费公式与舍入规则', sourceStatus: 'NEEDS_DECISION', readinessStatus: 'SOURCE_UNRESOLVED', decisionRef: 'Q-012', sourceRef: 'gap-analysis.md:Q-012' },
        { requirementId: 7, requirementCode: 'FINANCE_BUSINESS_SIGNOFF', requirementName: '财务与业务联合签字', sourceStatus: 'NEEDS_REVIEW', readinessStatus: 'SOURCE_UNRESOLVED', sourceRef: 'admission-report.md:G-06' }
      ]
    },
    acceptanceReadiness: {
      gateCode: 'G-07', total: 8, ready: 3, sourceUnresolved: 0, runtimeMissing: 4, runtimeIncomplete: 1, gateReady: false,
      phaseOneTemplateCount: 19, catalogAtCount: 114, mappingAtCount: 114, catalogMismatchCount: 0,
      scenarioCount: 0, approvedScenarioCount: 0, goldenScenarioCount: 0, goldenScenarioReadyCount: 0,
      scenarioAccountabilityCount: 0, mappedAtCount: 0, mappingAccountabilityCount: 0, inReviewAtCount: 0, approvedAtCount: 0, mockBusinessEvidenceCount: 0,
      requirements: [
        { requirementId: 1, requirementCode: 'PHASE_ONE_SCOPE_MANIFEST', requirementName: '阶段一19模板范围', readinessStatus: 'READY', sourceRef: 'template-matrix.md' },
        { requirementId: 2, requirementCode: 'PHASE_ONE_AT_CATALOG', requirementName: '阶段一114项AT目录', readinessStatus: 'READY', sourceRef: 'definition-catalog' },
        { requirementId: 3, requirementCode: 'PHASE_ONE_SCENARIO_CATALOG', requirementName: '阶段一E2E场景清单', readinessStatus: 'RUNTIME_MISSING', sourceRef: 'gap-analysis.md' },
        { requirementId: 4, requirementCode: 'PHASE_ONE_AT_MAPPING', requirementName: '114项AT到场景映射', readinessStatus: 'RUNTIME_INCOMPLETE', sourceRef: 'template-matrix.md' }
      ]
    },
    acceptanceScenarios: [],
    acceptanceMappings: [
      { mappingId: 21, acceptanceRef: 'AT-TD-001-OWNER', templateCode: 'TD-001', dimensionCode: 'OWNER', status: 'UNMAPPED', version: 0 },
      { mappingId: 22, acceptanceRef: 'AT-TD-001-SLA', templateCode: 'TD-001', dimensionCode: 'SLA', status: 'UNMAPPED', version: 0 }
    ]
  }
  await page.context().addCookies([{ name: 'Admin-Token', value: 'e2e-token', url: 'http://127.0.0.1:4173/' }])
  await page.addInitScript(() => { document.cookie = 'Admin-Token=e2e-token; path=/' })
  await page.route('**/prod-api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/prod-api', '')
    const body = request.postDataJSON ? request.postDataJSON() : {}
    if (path === '/getInfo') return json(route, null, { code: 200, user: { userId: 1, userName: 'admin', nickName: 'admin', avatar: '' }, roles: options.roles || ['admin'], permissions: options.permissions || ['*:*:*'] })
    if (path === '/getRouters') return json(route, [{ path: '/', component: 'Layout', children: [{ path: 'todo/config', component: 'todo/config/index', name: 'TodoConfig', meta: { title: '待办配置', icon: 'clipboard' } }] }])
    if (path === '/todo/template' && request.method() === 'GET') return json(route, [{ template_id: 1, template_name: '线索跟进', template_code: 'LEAD_FOLLOWUP' }])
    if (path === '/todo/event-catalog') return json(route, [{ event_type: 'LEAD_ASSIGNED', payload_version: 1, status: 'ACTIVE' }])
    if (path === '/todo/decision-governance-options') return json(route, {
      users: [{ user_id: 8, user_name: 'owner', nick_name: 'Owner', dept_name: '产品部' }],
      roles: [{ role_id: 3, role_key: 'product_owner', role_name: 'Product Owner' }],
      deliveryPhases: ['PHASE_ONE', 'PHASE_TWO', 'CROSS_PHASE']
    })
    if (path === '/todo/admission-evidence/governance-options') return json(route, {
      users: [
        { user_id: 7, user_name: 'owner', nick_name: 'Owner' },
        { user_id: 9, user_name: 'reviewer', nick_name: 'Reviewer' }
      ],
      statuses: ['OPEN', 'IN_REVIEW', 'APPROVED', 'REJECTED']
    })
    if (path === '/todo/admission-evidence' && request.method() === 'GET') return json(route, state.admissionEvidence)
    if (path === '/todo/foundation-admission') return json(route, state.foundationAdmission)
    if (path === '/todo/foundation-resources') return json(route, state.foundationResources)
    if (path === '/todo/foundation-migration') return json(route, state.historicalMigration)
    if (path === '/todo/foundation-migration/preflight') {
      if (options.preflightFailure) return route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 500, msg: 'preflight failed', data: null }) })
      return json(route, state.historicalMigrationPreflight)
    }
    if (path === '/todo/foundation-migration/exception-export') return route.fulfill({
      status: 200,
      contentType: 'application/zip',
      headers: {
        'Content-Disposition': 'attachment; filename="g04-historical-case-preflight.zip"',
        'X-Exception-Row-Count': '12',
        'X-Exception-CSV-SHA256': '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
      },
      body: Buffer.from('PK\u0003\u0004mock-g04-evidence')
    })
    if (path === '/todo/foundation-file-security') return json(route, state.fileSecurity)
    if (path === '/todo/foundation-finance') return json(route, state.financeReadiness)
    if (path === '/todo/foundation-acceptance') return json(route, state.acceptanceReadiness)
    if (path === '/todo/acceptance-governance-options') return json(route, {
      users: [
        { user_id: 7, user_name: 'owner', nick_name: 'Owner' },
        { user_id: 8, user_name: 'acceptor', nick_name: 'Acceptor' },
        { user_id: 9, user_name: 'reviewer', nick_name: 'Reviewer' }
      ],
      scenarioStatuses: ['DRAFT', 'IN_REVIEW', 'APPROVED', 'REJECTED'],
      mappingStatuses: ['UNMAPPED', 'MAPPED', 'IN_REVIEW', 'APPROVED', 'REJECTED'],
      dimensions: ['OWNER', 'SLA', 'DOD', 'ROUTE', 'HANDLER', 'UI']
    })
    if (path === '/todo/acceptance-scenarios') {
      if (request.method() === 'GET') return json(route, state.acceptanceScenarios)
      const item = { ...body, scenarioId: state.acceptanceScenarios.length + 11, deliveryPhase: 'PHASE_ONE', version: 0 }
      state.acceptanceScenarios.push(item); state.acceptanceReadiness.scenarioCount = state.acceptanceScenarios.length
      return json(route, item)
    }
    if (/^\/todo\/acceptance-scenarios\/\d+$/.test(path)) {
      const id = Number(path.split('/').pop()); state.acceptanceUpdates++
      state.acceptanceScenarios = state.acceptanceScenarios.map(row => row.scenarioId === id ? { ...row, ...body, version: row.version + 1 } : row)
      return json(route, state.acceptanceScenarios.find(row => row.scenarioId === id))
    }
    if (path === '/todo/acceptance-mappings/batch-bind') {
      state.acceptanceUpdates++
      const ids = body.mappings.map(item => item.mappingId)
      state.acceptanceMappings = state.acceptanceMappings.map(row => ids.includes(row.mappingId) ? { ...row, scenarioId: body.scenarioId, plannedTestRef: body.plannedTestRef, evidenceNote: body.evidenceNote, status: 'MAPPED', version: row.version + 1 } : row)
      return json(route, state.acceptanceMappings.filter(row => ids.includes(row.mappingId)))
    }
    if (path === '/todo/acceptance-mappings' && request.method() === 'GET') {
      const url = new URL(request.url()); let rows = state.acceptanceMappings
      for (const key of ['templateCode', 'dimensionCode', 'status']) if (url.searchParams.get(key)) rows = rows.filter(row => row[key] === url.searchParams.get(key))
      return json(route, rows)
    }
    if (/^\/todo\/acceptance-mappings\/\d+$/.test(path)) {
      const id = Number(path.split('/').pop()); state.acceptanceUpdates++
      state.acceptanceMappings = state.acceptanceMappings.map(row => row.mappingId === id ? { ...row, ...body, scenarioCode: state.acceptanceScenarios.find(item => item.scenarioId === body.scenarioId)?.scenarioCode, version: row.version + 1 } : row)
      return json(route, state.acceptanceMappings.find(row => row.mappingId === id))
    }
    if (/^\/todo\/admission-evidence\/\d+$/.test(path)) {
      state.admissionUpdates++
      const id = Number(path.split('/').pop())
      state.admissionEvidence = state.admissionEvidence.map(row => row.evidenceId === id ? {
        ...row, ...body, version: row.version + 1,
        ownerNickName: body.ownerUserId === 7 ? 'Owner' : '', reviewerNickName: body.reviewerUserId === 9 ? 'Reviewer' : ''
      } : row)
      return json(route, state.admissionEvidence.find(row => row.evidenceId === id))
    }
    if (path === '/todo/template/1/versions') return json(route, [{ version_id: 10, version_no: 1, status: 'PUBLISHED' }])
    if (path === '/todo/template/trigger') {
      if (request.method() === 'GET') return json(route, state.triggers)
      if (state.failNextTrigger) { state.failNextTrigger = false; return json(route, null, { code: 500, msg: 'stale trigger version', data: null }) }
      const id = body.triggerRuleId || state.triggers.length + 1
      const previous = state.triggers.find(row => row.trigger_rule_id === id)
      const item = {
        ...body,
        trigger_rule_id: id, triggerRuleId: id,
        event_type: body.eventType, payload_version: body.payloadVersion,
        template_id: body.templateId, template_version_id: body.templateVersionId,
        template_name: '线索跟进', business_type: body.businessType,
        condition_json: body.conditionJson, enabled: body.enabled,
        version: previous ? previous.version + 1 : 0
      }
      state.triggers = state.triggers.filter(row => row.trigger_rule_id !== id).concat(item)
      return json(route, item)
    }
    if (path === '/todo/calendar') {
      if (request.method() === 'GET') return json(route, state.calendars)
      const id = body.calendarId || state.calendars.length + 1
      const previous = state.calendars.find(row => row.calendar_id === id)
      const item = {
        ...body,
        calendar_id: id, calendarId: id,
        calendar_code: body.calendarCode, calendar_name: body.calendarName,
        timezone: body.timezone, work_days: body.workDays,
        work_start: body.workStart, work_end: body.workEnd,
        exception_json: body.exceptionJson, status: body.status,
        version: previous ? previous.version + 1 : 0
      }
      state.calendars = state.calendars.filter(row => row.calendar_id !== id).concat(item)
      return json(route, item)
    }
    if (path === '/todo/decisions') {
      if (request.method() === 'GET') return json(route, state.decisions)
      const item = { ...body, decisionId: state.decisions.length + 1, version: 0, impactedTemplateCodes: ['TD-001', 'TD-002'] }
      state.decisions.push(item)
      return json(route, item)
    }
    if (/^\/todo\/decisions\/\d+$/.test(path)) {
      state.decisionUpdates++
      const id = Number(path.split('/').pop())
      state.decisions = state.decisions.map(row => row.decisionId === id ? { ...row, ...body, version: row.version + 1 } : row)
      return json(route, state.decisions.find(row => row.decisionId === id))
    }
    if (path.startsWith('/system/dict/data/type/') || path === '/system/config/configKey/sys.index.skinName') return json(route, [])
    return json(route, {})
  })
  await page.goto('/todo/config')
  await page.getByRole('tab', { name: '触发规则' }).waitFor()
  return state
}

test('trigger rules use the active catalog and refresh create, edit and toggle state', async ({ page }) => {
  const state = await setupConfig(page)
  await page.getByRole('tab', { name: '触发规则' }).click()
  await page.getByRole('button', { name: '新增触发规则' }).click()
  let dialog = page.getByRole('dialog')
  let items = dialog.locator('.el-form-item')
  await choose(page, items.nth(0), '线索跟进')
  await choose(page, items.nth(1), 'v1')
  await items.nth(2).locator('input').fill('LEAD')
  await choose(page, items.nth(4), 'LEAD_ASSIGNED')
  await expect(items.nth(5).locator('input')).toHaveValue('1')
  await dialog.getByRole('button', { name: '保存' }).click()
  const pane = page.locator('.el-tab-pane:not([aria-hidden="true"])')
  await expect(pane.locator('.el-table').getByText('LEAD_ASSIGNED', { exact: true })).toBeVisible()
  expect(state.triggers[0].actionId).toBeTruthy()
  expect(state.triggers[0].expectedVersion).toBe(0)

  await page.getByRole('button', { name: '编辑' }).click()
  dialog = page.getByRole('dialog'); items = dialog.locator('.el-form-item')
  await items.nth(2).locator('input').fill('LEAD_EDITED')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('LEAD_EDITED', { exact: true })).toBeVisible()
  expect(state.triggers[0].version).toBe(1)

  let toggle = pane.locator('.el-table .el-switch').first()
  state.failNextTrigger = true
  await toggle.click()
  await expect.poll(() => state.triggers[0].enabled).toBe('Y')
  await expect(toggle).toHaveClass(/is-checked/)
  await toggle.click()
  await expect.poll(() => state.triggers[0].enabled).toBe('N')

  await page.reload(); await page.getByRole('tab', { name: '触发规则' }).click()
  toggle = page.locator('.el-tab-pane:not([aria-hidden="true"]) .el-table .el-switch').first()
  await expect(toggle).not.toHaveClass(/is-checked/)
})

test('calendar edit and status survive a server reload', async ({ page }) => {
  const state = await setupConfig(page)
  await page.getByRole('tab', { name: '工作日历' }).click()
  await page.getByRole('button', { name: '新增日历' }).click()
  let dialog = page.getByRole('dialog'); let items = dialog.locator('.el-form-item')
  await items.nth(0).locator('input').fill('CN_TEST')
  await items.nth(1).locator('input').fill('中国日历')
  await dialog.getByRole('button', { name: '保存' }).click()
  expect(state.calendars[0].actionId).toBeTruthy()

  await page.getByRole('button', { name: '编辑' }).click()
  dialog = page.getByRole('dialog'); items = dialog.locator('.el-form-item')
  await items.nth(1).locator('input').fill('中国日历-停用')
  await items.nth(3).locator('.el-switch').click()
  await dialog.getByRole('button', { name: '保存' }).click()
  expect(state.calendars[0].status).toBe('1')
  expect(state.calendars[0].version).toBe(1)

  await page.reload(); await page.getByRole('tab', { name: '工作日历' }).click()
  await expect(page.getByText('中国日历-停用', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '编辑' }).click()
  await expect(page.getByRole('dialog').locator('.el-form-item').nth(3).locator('.el-switch')).not.toHaveClass(/is-checked/)
})

test('decision requires a conclusion and displays impacted template codes', async ({ page }) => {
  const state = await setupConfig(page)
  await page.getByRole('tab', { name: '决策登记' }).click()
  await page.getByRole('button', { name: '新增决策' }).click()
  let dialog = page.getByRole('dialog')
  await formItem(dialog, '编码').locator('input').fill('DECISION_01')
  await formItem(dialog, '标题').locator('input').fill('确认负责人')
  await choose(page, formItem(dialog, '负责人'), 'Owner（owner）')
  await choose(page, formItem(dialog, '责任角色'), 'Product Owner（product_owner）')
  await formItem(dialog, '截止时间').locator('input').fill('2026-07-31 18:00:00')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('TD-001, TD-002', { exact: true })).toBeVisible()
  expect(state.decisions[0].ownerUserId).toBe(8)
  expect(state.decisions[0].ownerRoleKey).toBe('product_owner')
  expect(state.decisions[0].deliveryPhase).toBe('PHASE_ONE')

  await page.getByRole('button', { name: '编辑' }).click()
  dialog = page.getByRole('dialog')
  await choose(page, formItem(dialog, '状态'), 'RESOLVED')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('conclusion is required', { exact: true })).toBeVisible()
  expect(state.decisionUpdates).toBe(0)

  await formItem(dialog, '结论').locator('input').fill('已确认')
  await formItem(dialog, '处理方案').locator('input').fill('通知团队')
  await dialog.getByRole('button', { name: '保存' }).click()
  expect(state.decisionUpdates).toBe(1)
  await expect(page.getByText('TD-001, TD-002', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: '重新打开' }).click()
  await page.getByRole('dialog').getByRole('button', { name: '保存' }).click()
  await expect(page.locator('.el-tab-pane:not([aria-hidden="true"])').getByText('OPEN', { exact: true })).toBeVisible()
})

test('admission evidence requires independent review and survives submit and approval reloads', async ({ page }) => {
  const state = await setupConfig(page)
  await page.getByRole('tab', { name: '准入证据' }).click()
  await expect(page.getByText('G02-DICTIONARY-ROLE', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '登记' }).click()
  let dialog = page.getByRole('dialog')
  await choose(page, formItem(dialog, '责任人'), 'Owner（owner）')
  await choose(page, formItem(dialog, '独立评审人'), 'Reviewer（reviewer）')
  await formItem(dialog, '截止时间').locator('input').fill('2026-07-31 18:00:00')
  await page.keyboard.press('Enter')
  await expect(page.locator('.el-picker-panel:visible')).toHaveCount(0)
  await choose(page, formItem(dialog, '状态'), 'IN_REVIEW')
  await formItem(dialog, '证据引用').locator('input').fill('repo://doc/g02.md')
  await formItem(dialog, '提交/评审结论').locator('textarea').fill('提交业务字典冻结清单')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(page.locator('.el-tab-pane:not([aria-hidden="true"])').getByText('IN_REVIEW', { exact: true })).toBeVisible()
  expect(state.admissionUpdates).toBe(1)
  expect(state.admissionEvidence[0].ownerUserId).toBe(7)
  expect(state.admissionEvidence[0].reviewerUserId).toBe(9)

  await page.getByRole('button', { name: '登记' }).click()
  dialog = page.getByRole('dialog')
  await choose(page, formItem(dialog, '状态'), 'APPROVED')
  await formItem(dialog, '提交/评审结论').locator('textarea').fill('独立评审通过')
  await dialog.getByRole('button', { name: '保存' }).click()
  expect(state.admissionUpdates).toBe(2)
  await page.reload(); await page.getByRole('tab', { name: '准入证据' }).click()
  await expect(page.locator('.el-tab-pane:not([aria-hidden="true"])').getByText('APPROVED', { exact: true })).toBeVisible()
  await expect(page.getByText('Owner', { exact: false }).first()).toBeVisible()
})

test('foundation resources expose repository source and runtime gaps without promoting values', async ({ page }) => {
  await setupConfig(page)
  await page.getByRole('tab', { name: '基础资源' }).click()
  const pane = page.locator('.el-tab-pane:not([aria-hidden="true"])')
  await expect(pane.getByText('G-02 资源门禁未就绪，不能批准准入证据')).toBeVisible()
  await expect(pane.getByText('law_business_line', { exact: false })).toBeVisible()
  await expect(pane.getByText('NON_LITIGATION、COMPREHENSIVE、EXECUTION', { exact: true })).toBeVisible()
  await expect(pane.getByText('Q-008', { exact: true })).toBeVisible()
  await expect(pane.getByText('来源待确认', { exact: true }).first()).toBeVisible()
  await expect(pane.getByText('运行态缺失', { exact: true }).first()).toBeVisible()
  await expect(pane.getByText('运行态不完整', { exact: true }).first()).toBeVisible()
})

test('aggregate admission truthfully remains two of eight until every gate is ready', async ({ page }) => {
  await setupConfig(page)
  await page.getByRole('tab', { name: '准入总览' }).click()
  const pane = page.locator('.el-tab-pane:not([aria-hidden="true"])')
  await expect(pane.getByText('NOT_ADMITTED', { exact: true })).toBeVisible()
  await expect(pane.getByText('2/8', { exact: true })).toBeVisible()
  await expect(pane.getByText('G-01', { exact: true })).toBeVisible()
  await expect(pane.getByText('G-03', { exact: true })).toBeVisible()
  await expect(pane.getByText('12 个阻断决策尚未全部分配责任人、责任角色和截止时间', { exact: true })).toBeVisible()
  await expect(pane.getByText('技术就绪不替代授权业务决策、独立评审或签字。')).toBeVisible()
  await expect(pane.getByText('ADMITTED_FOR_PHASE_ONE_BUSINESS_IMPLEMENTATION', { exact: true })).toHaveCount(0)
})

test('historical migration readiness exposes inventory and blockers without selecting defaults', async ({ page }) => {
  await setupConfig(page)
  await page.getByRole('tab', { name: '历史迁移' }).click()
  const pane = page.locator('.el-tab-pane:not([aria-hidden="true"])')
  await expect(pane.getByText('G-04 历史迁移门禁未就绪，不能批准准入证据')).toBeVisible()
  await expect(pane.getByText('CASE_BUSINESS_LINE_SCHEMA', { exact: true })).toBeVisible()
  await expect(pane.getByText('HISTORICAL_CASE_DEFAULT', { exact: true })).toBeVisible()
  await expect(pane.getByText('待业务决策', { exact: true })).toBeVisible()
  await expect(pane.getByText('待迁移证据', { exact: true })).toBeVisible()
  await expect(pane.getByText('缺失', { exact: true }).first()).toBeVisible()
  await expect(pane.getByText('7', { exact: true }).first()).toBeVisible()
  await expect(pane.getByText('异常候选案件')).toBeVisible()
  await expect(pane.getByText('12', { exact: true }).last()).toBeVisible()
  const openGroup = pane.locator('.preflight-groups .el-table__row').filter({ hasText: 'OPEN' })
  await expect(openGroup.getByText('LITIGATION', { exact: true })).toBeVisible()
  await expect(openGroup.getByText('8', { exact: true })).toBeVisible()
  await expect(pane.getByText('清单尚未分类、尚未签字，不会自动改变 G-04')).toBeVisible()

  const downloadPromise = page.waitForEvent('download')
  await pane.getByRole('button', { name: '导出异常候选清单' }).click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe('g04-historical-case-preflight.zip')
  await expect(pane.getByText('导出行数：12', { exact: true })).toBeVisible()
  await expect(pane.getByText('0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef', { exact: true })).toBeVisible()
  await expect(pane.getByText('UNREVIEWED', { exact: true })).toBeVisible()
})

test('historical migration preflight failure preserves the existing readiness requirements', async ({ page }) => {
  await setupConfig(page, { preflightFailure: true })
  await page.getByRole('tab', { name: '历史迁移' }).click()
  const pane = page.locator('.el-tab-pane:not([aria-hidden="true"])')
  await expect(pane.getByText('历史数据预检加载失败，原准入目录未受影响', { exact: true })).toBeVisible()
  await expect(pane.getByText('CASE_BUSINESS_LINE_SCHEMA', { exact: true })).toBeVisible()
  await expect(pane.getByText('HISTORICAL_CASE_DEFAULT', { exact: true })).toBeVisible()
  await expect(pane.getByText('G-04 历史迁移门禁未就绪，不能批准准入证据', { exact: true })).toBeVisible()
})

test('historical migration inventory hides evidence export without permission', async ({ page }) => {
  await setupConfig(page, { roles: ['common'], permissions: ['todo:admission:view'] })
  await page.getByRole('tab', { name: '历史迁移' }).click()
  const pane = page.locator('.el-tab-pane:not([aria-hidden="true"])')
  await expect(pane.getByText('异常候选案件')).toBeVisible()
  await expect(pane.locator('.preflight-groups').getByText('LITIGATION', { exact: true })).toBeVisible()
  await expect(pane.getByRole('button', { name: '导出异常候选清单' })).toHaveCount(0)
})

test('file security readiness separates technical controls from independent review', async ({ page }) => {
  await setupConfig(page)
  await page.getByRole('tab', { name: '文件安全' }).click()
  const pane = page.locator('.el-tab-pane:not([aria-hidden="true"])')
  await expect(pane.getByText('G-05 文件安全门禁未就绪，不能批准准入证据')).toBeVisible()
  await expect(pane.getByText('SINGLE_USE_RELATION_TOKEN', { exact: true })).toBeVisible()
  await expect(pane.getByText('PRD_MATERIAL_TYPE_E2E', { exact: true })).toBeVisible()
  await expect(pane.getByText('SECURITY_REVIEW_SIGNOFF', { exact: true })).toBeVisible()
  const materialRow = pane.locator('.el-table__row').filter({ hasText: 'PRD_MATERIAL_TYPE_E2E' })
  await expect(materialRow.getByText('仓库已确认', { exact: true })).toBeVisible()
  await expect(materialRow.getByText('已就绪', { exact: true })).toBeVisible()
  await expect(pane.getByText('待安全评审', { exact: true })).toBeVisible()
})

test('finance readiness separates repository capabilities from schema and decision blockers', async ({ page }) => {
  await setupConfig(page)
  await page.getByRole('tab', { name: '财务准入' }).click()
  const pane = page.locator('.el-tab-pane:not([aria-hidden="true"])')
  await expect(pane.getByText('G-06 财务门禁未就绪，不能批准准入证据')).toBeVisible()
  await expect(pane.getByText('FEE_PLAN_CORE_PRECISION', { exact: true })).toBeVisible()
  await expect(pane.getByText('NODE_FEE_SCHEMA', { exact: true })).toBeVisible()
  await expect(pane.getByText('Q009_NODE_COLLECTION_POLICY', { exact: true })).toBeVisible()
  await expect(pane.getByText('Q012_RISK_FORMULA_POLICY', { exact: true })).toBeVisible()
  await expect(pane.getByText('FINANCE_BUSINESS_SIGNOFF', { exact: true })).toBeVisible()
  await expect(pane.getByText('Q-009', { exact: true }).first()).toBeVisible()
  await expect(pane.getByText('Q-012', { exact: true }).first()).toBeVisible()
  await expect(pane.getByText('待业务决策', { exact: true }).first()).toBeVisible()
  await expect(pane.getByText('待财务签字', { exact: true })).toBeVisible()
  await expect(pane.getByText('结构缺失', { exact: true }).first()).toBeVisible()
})

test('phase-one acceptance governs scenarios mappings and batch bind without mock credit', async ({ page }) => {
  const state = await setupConfig(page)
  await page.getByRole('tab', { name: '阶段一验收' }).click()
  let pane = page.locator('.el-tab-pane:not([aria-hidden="true"])')
  await expect(pane.getByText('G-07 阶段一验收门禁未就绪，不能批准准入证据')).toBeVisible()
  await expect(pane.getByText('现有 28 个 Mock E2E 不计入 G-07')).toBeVisible()
  await expect(pane.getByText('19/19', { exact: true })).toBeVisible()
  await expect(pane.getByText('114/114', { exact: true })).toBeVisible()
  await expect(pane.getByText('独立 Reviewer', { exact: true }).first()).toBeVisible()

  await pane.getByRole('button', { name: '新增场景' }).click()
  let dialog = page.getByRole('dialog')
  await formItem(dialog, '场景编码').locator('input').fill('PHASE_ONE_GOLDEN_PATH')
  await formItem(dialog, '场景名称').locator('input').fill('一期黄金路径')
  await formItem(dialog, '业务路径').locator('input').fill('线索→客户/合同→转案→案管分类→综法办理→归档')
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(pane.getByText('PHASE_ONE_GOLDEN_PATH', { exact: true }).first()).toBeVisible()
  expect(state.acceptanceScenarios).toHaveLength(1)

  const rows = pane.locator('.el-table').last().locator('.el-table__body-wrapper tbody tr')
  await rows.nth(0).locator('.el-checkbox').click()
  await rows.nth(1).locator('.el-checkbox').click()
  await pane.getByRole('button', { name: '批量绑定' }).click()
  dialog = page.getByRole('dialog')
  await choose(page, formItem(dialog, '验收场景'), 'PHASE_ONE_GOLDEN_PATH｜一期黄金路径')
  await formItem(dialog, '计划测试引用').locator('input').fill('e2e/phase-one-golden.spec.js#golden')
  await dialog.getByRole('button', { name: '绑定为 MAPPED' }).click()
  await expect.poll(() => state.acceptanceMappings.every(row => row.status === 'MAPPED')).toBeTruthy()
  expect(state.acceptanceMappings.some(row => row.status === 'APPROVED')).toBeFalsy()

  await pane.locator('.section-card').last().getByRole('button', { name: '编辑', exact: true }).first().click()
  dialog = page.getByRole('dialog')
  await expect(formItem(dialog, '计划测试引用').locator('input')).toHaveValue('e2e/phase-one-golden.spec.js#golden')
  await dialog.getByRole('button', { name: '保存' }).click()
  expect(state.acceptanceUpdates).toBeGreaterThanOrEqual(2)

  await page.reload(); await page.getByRole('tab', { name: '阶段一验收' }).click()
  pane = page.locator('.el-tab-pane:not([aria-hidden="true"])')
  await expect(pane.getByText('PHASE_ONE_GOLDEN_PATH', { exact: true }).first()).toBeVisible()
  await choose(page, pane.locator('.filters .el-form-item').filter({ hasText: '状态' }).first(), 'MAPPED')
  await pane.getByRole('button', { name: '查询' }).click()
  await expect(pane.getByText('AT-TD-001-OWNER', { exact: true }).first()).toBeVisible()
  await expect(pane.getByText('AT-TD-001-SLA', { exact: true }).first()).toBeVisible()
})
