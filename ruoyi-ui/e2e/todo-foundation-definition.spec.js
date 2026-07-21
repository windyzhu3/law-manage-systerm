const { test, expect } = require('@playwright/test')

async function json(route, data, envelope) {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope || { code: 200, msg: 'success', data }) })
}

function definition(versionId, status) {
  return {
    version_id: versionId, version_no: versionId === 10 ? 2 : 1, status, template_id: 1,
    definition_json: JSON.stringify({
      schemaVersion: 1, templateCode: 'LEAD_FOLLOWUP',
      event: { eventType: 'LEAD_ASSIGNED', payloadVersion: 1, condition: {} },
      owner: { config: {} }, dod: { config: {} }, sla: { config: { minutes: 30 } }, ui: { config: {} },
      routing: { config: { layout: { zoom: 1 },
        start: 'task',
        nodes: [{ key: 'task', type: 'TASK', templateVersionId: versionId }, { key: 'decision', type: 'DECISION' }, { key: 'end', type: 'END' }],
        edges: [{ key: 'to-decision', from: 'task', to: 'decision' }, { key: 'default-end', from: 'decision', to: 'end', default: true }]
      } },
      autoActions: [], decisionRefs: [], acceptanceRefs: []
    })
  }
}

async function setup(page, report) {
  const requests = { simulation: [], diff: [], drafts: [] }
  const versions = [definition(9, 'PUBLISHED'), definition(10, 'DRAFT')]
  await page.context().addCookies([{ name: 'Admin-Token', value: 'e2e-token', url: 'http://127.0.0.1:4173/' }])
  await page.addInitScript(() => { document.cookie = 'Admin-Token=e2e-token; path=/' })
  await page.route('**/prod-api/**', async route => {
    const request = route.request(); const path = new URL(request.url()).pathname.replace('/prod-api', '')
    if (path === '/getInfo') return json(route, null, { code: 200, user: { userId: 1, userName: 'admin', nickName: 'admin', avatar: '' }, roles: ['admin'], permissions: ['*:*:*'] })
    if (path === '/getRouters') return json(route, [{ path: '/', component: 'Layout', children: [{ path: 'todo-foundation-harness', component: 'todo/config/components/FoundationConfigurationHarness', name: 'TodoFoundationConfigurationHarness', meta: { title: '待办配置', icon: 'clipboard' } }] }])
    if (path === '/todo/template' && request.method() === 'GET') return json(route, [{ template_id: 1, template_name: '线索跟进', template_code: 'LEAD_FOLLOWUP' }])
    if (path === '/todo/template/1/versions') return json(route, versions)
    if (path === '/todo/template/trigger' || path === '/todo/calendar' || path === '/todo/decisions') return json(route, [])
    if (path === '/todo/auto-action-capabilities') return json(route, [{
      actionType: 'TRANSFER', capability: 'TRANSFER', triggerAt: ['DUE', 'SLA_80', 'SLA_100', 'SLA_150'],
      retryFields: [{ name: 'maxAttempts', type: 'number', required: false, min: 1, defaultValue: 3, label: '最大尝试次数' }, { name: 'retryDelayMinutes', type: 'number', required: false, min: 1, defaultValue: 5, label: '重试间隔分钟' }, { name: 'claimTimeoutMinutes', type: 'number', required: false, min: 1, defaultValue: 15, label: '认领超时分钟' }],
      requiredFields: [{ name: 'targetOwnerId', type: 'number', required: true, min: 1, label: '目标负责人' }]
    }, {
      actionType: 'CUSTOM_NOTIFY', capability: 'CUSTOM_NOTIFY', triggerAt: ['DUE'],
      retryFields: [{ name: 'maxAttempts', type: 'number', required: false, min: 1, defaultValue: 3, label: '最大尝试次数' }, { name: 'retryDelayMinutes', type: 'number', required: false, min: 1, defaultValue: 5, label: '重试间隔分钟' }, { name: 'claimTimeoutMinutes', type: 'number', required: false, min: 1, defaultValue: 15, label: '认领超时分钟' }],
      requiredFields: [{ name: 'channel', type: 'text', required: true, label: '通知渠道' }]
    }])
    if (path === '/todo/definitions/version/10/preflight') return json(route, { versionId: 10, report })
    if (path === '/todo/template/version/10' && request.method() === 'PUT') { requests.drafts.push(request.postDataJSON()); return json(route, versions[1]) }
    if (path === '/todo/definitions/version/10/simulate') {
      requests.simulation.push(request.postDataJSON())
      return json(route, {
        versionId: 10, definitionHash: 'hash-valid',
        trigger: { status: 'MATCHED', eventType: 'LEAD_ASSIGNED', payloadVersion: 1, trace: ['event matched'] },
        owner: { status: 'RESOLVED', ownerId: 7, candidates: [7], ccUsers: [], fallbackUsed: false, trace: ['owner selected'] },
        sla: { status: 'CALCULATED', calendarCode: 'CN', dueAt: '2026-07-18T11:00:00', trace: ['sla calculated'] },
        form: { ui: { fields: ['result'] }, dod: { required: true } },
        routes: [{ order: 1, nodeKey: 'task', nodeType: 'TASK', status: 'VISITED', trace: ['route visited'] }],
        autoActions: [{ ruleKey: 'transfer-due', actionType: 'TRANSFER', status: 'SCHEDULED' }],
        handlers: [{ code: 'LEAD_HANDLER', status: 'READY', simulatable: true }],
        issues: [{ code: 'SIM_INFO', path: 'simulation', severity: 'INFO', message: 'trace complete' }]
      })
    }
    if (path === '/todo/definitions/versions/9/diff/10') {
      requests.diff.push(path)
      return json(route, { leftVersionId: 9, rightVersionId: 10, overallRisk: 'BLOCKING', changes: [{ section: 'routing', path: '$.routing.nodes[end]', type: 'MODIFIED', before: '<img src=x onerror=alert(1)>', after: { type: 'END' }, risk: 'BLOCKING', reason: 'Route behavior changed' }] })
    }
    if (path.startsWith('/system/dict/data/type/') || path === '/system/config/configKey/sys.index.skinName') return json(route, [])
    return json(route, {})
  })
  await page.goto('/todo-foundation-harness')
  await page.getByRole('button', { name: '版本' }).click()
  await page.getByTestId('definition-edit-10').click()
  return requests
}

test('valid TASK to DECISION to END preflight enables publish and edits invalidate the gate', async ({ page }) => {
  const requests = await setup(page, { errors: [], warnings: [], compiledJson: '{}', definitionHash: 'hash-valid' })
  await expect(page.getByTestId('routing-graph-editor')).toBeVisible()
  await expect(page.getByTestId('auto-action-editor')).toContainText('TRANSFER')
  await page.getByRole('button', { name: '保存草稿' }).click()
  await expect.poll(() => requests.drafts.length).toBe(1)
  expect(JSON.parse(requests.drafts[0].definitionJson).routing.config.layout).toEqual({ zoom: 1 })
  await page.getByTestId('preflight-run').click()
  await expect(page.getByText('预检通过', { exact: true })).toBeVisible()
  await page.getByText('编译产物', { exact: true }).click()
  await expect(page.getByTestId('preflight-compiled-json')).toContainText('{}')
  await expect(page.getByTestId('publish-10')).toBeEnabled()
  await page.getByTestId('auto-action-editor').getByRole('button', { name: '新增动作' }).click()
  await expect(page.getByTestId('auto-action-editor')).toContainText('目标负责人')
  await expect(page.getByTestId('publish-10')).toBeDisabled()
})

test('unresolved decisions and invalid LOOP/JOIN are visible and keep publish blocked', async ({ page }) => {
  await setup(page, { errors: [
    { code: 'TODO_DECISION_UNRESOLVED', path: 'decisionRefs', message: 'Decision is unresolved' },
    { code: 'TODO_ROUTE_LOOP_BOUND_REQUIRED', path: 'routing.nodes[3]', message: 'Loop needs a bound' },
    { code: 'TODO_ROUTE_JOIN_BRANCH_MISMATCH', path: 'routing.nodes[4]', message: 'Join branches mismatch' }
  ], warnings: [], compiledJson: '{}', definitionHash: 'hash-invalid' })
  await page.getByTestId('preflight-run').click()
  for (const code of ['TODO_DECISION_UNRESOLVED', 'TODO_ROUTE_LOOP_BOUND_REQUIRED', 'TODO_ROUTE_JOIN_BRANCH_MISMATCH']) await expect(page.getByText(code, { exact: true })).toBeVisible()
  await expect(page.getByTestId('publish-10')).toBeDisabled()
})

test('catalog text field can be edited and saved into the canonical definition', async ({ page }) => {
  const requests = await setup(page, { errors: [], warnings: [], compiledJson: '{}', definitionHash: 'hash-valid' })
  const editor = page.getByTestId('auto-action-editor')
  await editor.getByRole('button', { name: '新增动作' }).click()
  await editor.getByTestId('auto-action-capability').locator('input').click()
  await page.getByText('CUSTOM_NOTIFY', { exact: true }).last().click()
  await editor.getByText('通知渠道', { exact: true }).locator('..').locator('input').fill('WECHAT')
  await page.getByRole('button', { name: '保存草稿' }).click()
  await expect.poll(() => requests.drafts.length).toBe(1)
  const action = JSON.parse(requests.drafts[0].definitionJson).autoActions[0].config
  expect(action).toMatchObject({ actionType: 'CUSTOM_NOTIFY', capability: 'CUSTOM_NOTIFY', channel: 'WECHAT' })
})

test('simulation sends the complete command and semantic diff renders server content as text', async ({ page }) => {
  const requests = await setup(page, { errors: [], warnings: [], compiledJson: '{}', definitionHash: 'hash-valid' })
  await page.getByTestId('simulation-open').click()
  await page.getByTestId('simulation-run').click()
  await expect.poll(() => requests.simulation.length).toBe(1)
  expect(requests.simulation[0]).toMatchObject({ businessType: 'LEAD', businessId: 1, effectiveAt: expect.any(String), payload: { sample: true }, taskCompletions: [{ nodeKey: 'task', occurrence: 0, payload: {}, completedAt: expect.any(String) }] })
  const simulation = page.getByTestId('simulation-result')
  for (const marker of ['MATCHED', 'RESOLVED', 'CALCULATED', 'VISITED', 'SCHEDULED', 'LEAD_HANDLER', 'SIM_INFO']) await expect(simulation).toContainText(marker)
  await page.getByTestId('simulation-completed-at').locator('input').fill('')
  await page.getByTestId('simulation-run').click()
  await expect(page.getByText('虚拟任务完成需要非负整数路由次数和完成时间', { exact: true })).toBeVisible()
  await expect(page.getByTestId('simulation-run')).not.toHaveClass(/is-loading/)
  await page.getByTestId('simulation-node-key').fill('')
  await page.getByTestId('simulation-completion-payload').fill('{')
  await page.getByTestId('simulation-run').click()
  await expect.poll(() => requests.simulation.length).toBe(2)
  expect(requests.simulation[1].taskCompletions).toEqual([])
  await page.keyboard.press('Escape')

  await page.getByTestId('diff-open').click()
  await page.getByTestId('diff-run').click()
  await expect.poll(() => requests.diff.length).toBe(1)
  const diff = page.getByTestId('diff-result')
  for (const marker of ['BLOCKING', 'routing', 'MODIFIED', 'Route behavior changed', '<img src=x onerror=alert(1)>']) await expect(diff).toContainText(marker)
  await expect(diff.locator('img')).toHaveCount(0)
})
