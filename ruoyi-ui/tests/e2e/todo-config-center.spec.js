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

  test('TD-001 governed scenarios resolve all three business outcomes through real APIs', async ({ page }) => {
    await loginAs(page, 'todo_config_admin', password)
    const templates = await authenticatedEnvelope(page, 'GET',
      '/todo/config/templates?keyword=TD-001&pageNum=1&pageSize=20')
    const template = (templates.rows || []).find(row => row.templateCode === 'TD-001')
    expect(template, JSON.stringify(templates.rows || [])).toBeTruthy()

    const journey = (await authenticatedEnvelope(page, 'GET',
      `/todo/config/templates/${template.templateId}/journey`)).data
    const scenarios = (await authenticatedEnvelope(page, 'GET',
      `/todo/config/templates/${template.templateId}/journey/scenarios`)).data
    expect(scenarios.map(item => item.scenarioCode)).toEqual([
      'TD001_VALID',
      'TD001_SUSPECT_INVALID',
      'TD001_UNREACHABLE'
    ])

    const expected = {
      TD001_VALID: 'TD-004',
      TD001_SUSPECT_INVALID: 'TD-002',
      TD001_UNREACHABLE: 'TD-003'
    }
    const baseCommand = {
      versionId: Number(journey.template.versionId),
      definitionHash: journey.template.definitionHash,
      businessType: 'LEAD',
      businessId: Number(process.env.TODO_CONFIG_E2E_BUSINESS_ID),
      manualOverrides: {},
      effectiveAt: '2026-07-29T09:00:00'
    }
    for (const scenario of scenarios) {
      const result = (await authenticatedEnvelope(page, 'POST',
        `/todo/config/templates/${template.templateId}/journey/scenarios/${scenario.scenarioCode}/simulate`,
        {
          ...baseCommand,
          requestId: `todo-config-${scenario.scenarioCode}-${Date.now()}`
        })).data
      expect(result.scenarioCode).toBe(scenario.scenarioCode)
      expect(result.actualNextTemplateCode).toBe(expected[scenario.scenarioCode])
      expect(result.passed, JSON.stringify(result)).toBe(true)
    }

    const batch = (await authenticatedEnvelope(page, 'POST',
      `/todo/config/templates/${template.templateId}/journey/scenarios/batch-simulate`,
      { ...baseCommand, requestId: `todo-config-batch-${Date.now()}` })).data
    expect(batch.results.map(item => [item.scenarioCode, item.actualNextTemplateCode])).toEqual([
      ['TD001_VALID', 'TD-004'],
      ['TD001_SUSPECT_INVALID', 'TD-002'],
      ['TD001_UNREACHABLE', 'TD-003']
    ])
    expect(batch.results.every(item => item.passed)).toBe(true)
    expect(batch.publicationReady, JSON.stringify(batch)).toBe(true)
    expect(batch.blockingScenarioCodes).toEqual([])
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

async function authenticatedEnvelope(page, method, path, data) {
  const result = await page.evaluate(async request => {
    const token = document.cookie.split(';')
      .map(item => item.trim())
      .find(item => item.startsWith('Admin-Token='))
    if (!token) throw new Error('Admin-Token cookie is missing after login')
    const response = await fetch(`/prod-api${request.path}`, {
      method: request.method,
      headers: {
        Authorization: `Bearer ${decodeURIComponent(token.substring('Admin-Token='.length))}`,
        'Content-Type': 'application/json;charset=utf-8'
      },
      body: request.data === undefined ? undefined : JSON.stringify(request.data)
    })
    return {
      status: response.status,
      body: await response.json().catch(() => ({}))
    }
  }, { method, path, data })
  expect(result.status, JSON.stringify(result.body)).toBe(200)
  expect(Number(result.body.code || 200), JSON.stringify(result.body)).toBe(200)
  return result.body
}
