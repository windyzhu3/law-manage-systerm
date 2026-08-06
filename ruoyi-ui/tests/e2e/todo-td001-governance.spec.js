const { test, expect } = require('@playwright/test')
const fs = require('node:fs')
const path = require('node:path')
const { executeSql } = require('./support/mysql-e2e-runner')
const {
  assertSimulationPersistenceUnchanged,
  snapshotSimulationPersistence
} = require('./support/todo-config-e2e-database')

const realBackend = process.env.TODO_E2E_REAL_BACKEND === 'true'
const username = process.env.TODO_CONFIG_GOVERNANCE_E2E_USERNAME || 'todo_config_admin'
const password = process.env.TODO_CONFIG_E2E_PASSWORD
const SAMPLE_LEAD_ID = -1001
const SCREENSHOT_DIR = path.resolve(__dirname, '../../../output/playwright/td001-template-governance')

test.use({ viewport: { width: 1672, height: 941 } })

test.describe.serial('TD-001 template governance and published revalidation', () => {
  test.skip(!realBackend, 'requires the disposable real-backend E2E environment')

  test('TD001_RETIRED_TEMPLATE_AND_PUBLISHED_REVALIDATION are truthful and operable', async ({ page }) => {
    test.setTimeout(180000)
    fs.mkdirSync(SCREENSHOT_DIR, { recursive: true })
    const td001 = loadTd001GovernanceFixture()
    await loginAs(page, username, password)
    await page.goto('/todo-engine/todo-template')

    const current = page.locator('.template-scenario').filter({ hasText: 'TD-001' }).locator('xpath=ancestor::tr').first()
    await expect(current).toBeVisible()
    await expect(current).toContainText('运行中')

    const search = page.getByPlaceholder('搜索业务场景或模板编码')
    await search.fill('LEAD_FIRST_CONTACT')
    await search.press('Enter')
    const legacy = page.locator('.template-scenario').filter({ hasText: 'LEAD_FIRST_CONTACT' }).locator('xpath=ancestor::tr').first()
    await expect(legacy).toBeVisible()
    await expect(legacy).toContainText('已停用')
    await expect(legacy).toContainText('已由首联待办（TD-001）替代')
    await page.getByRole('button', { name: '打开现行模板' }).click()
    await expect(page.getByTestId('template-journey-shell')).toContainText('TD-001')

    await page.locator('.journey-step-nav__item').filter({ hasText: '模拟发布' }).click()
    const step = page.getByTestId('simulation-publish-step')
    await expect(step).toContainText('当前为已发布不可变版本')
    const revalidate = step.getByRole('button', { name: '一键重新验证' })
    await expect(revalidate).toBeEnabled()

    const before = snapshotSimulationPersistence(td001.templateId, 'LEAD', SAMPLE_LEAD_ID)
    await revalidate.click()
    await expect(step.locator('.simulation-publish-step__header')).toContainText('模拟发布验证已全部通过', { timeout: 90000 })
    await expect(step.locator('.batch-gate')).toContainText('三个必测场景均已通过')
    const after = snapshotSimulationPersistence(td001.templateId, 'LEAD', SAMPLE_LEAD_ID)
    assertSimulationPersistenceUnchanged(before, after)

    const evidence = Number(executeSql(`
      select count(distinct scenario_code) from todo_simulation_evidence
      where template_id=${td001.templateId} and version_id=${td001.versionId}
        and definition_hash=${sqlLiteral(td001.definitionHash)}
        and scenario_version=2 and result_status='PASSED'
        and scenario_code in ('TD001_VALID','TD001_SUSPECT_INVALID','TD001_UNREACHABLE');
    `, process.env.TODO_E2E_DB_NAME).trim())
    expect(evidence).toBe(3)

    const fullEvidence = Number(executeSql(`
      select count(*) from todo_simulation_evidence
      where template_id=${td001.templateId} and version_id=${td001.versionId}
        and definition_hash=${sqlLiteral(td001.definitionHash)}
        and scenario_code='FULL_SIMULATION' and result_status='PASSED';
    `, process.env.TODO_E2E_DB_NAME).trim())
    expect(fullEvidence).toBeGreaterThanOrEqual(1)

    const activeEntry = Number(executeSql(`
      select count(*) from todo_trigger_rule r
      join todo_template t on t.template_id=r.template_id
      where r.entry_slot_code='LEAD_FIRST_CONTACT_ENTRY' and r.enabled='Y'
        and t.template_code='TD-001' and t.status='0';
    `, process.env.TODO_E2E_DB_NAME).trim())
    expect(activeEntry).toBe(1)

    await page.reload()
    await expect(page.getByTestId('simulation-publish-step')).toContainText('模拟发布验证已全部通过')
    await page.locator('.journey-step-nav__item').filter({ hasText: '后续路由' }).click()
    await page.setViewportSize({ width: 1672, height: 941 })
    await expectRoutingControlsDoNotOverlap(page)
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'routing-1672x941.png'), fullPage: true })
    await page.setViewportSize({ width: 1180, height: 820 })
    await expectRoutingControlsDoNotOverlap(page)
    await page.screenshot({ path: path.join(SCREENSHOT_DIR, 'routing-1180x820.png'), fullPage: true })
  })
})

function loadTd001GovernanceFixture() {
  const values = String(executeSql(`
    select t.template_id,v.version_id,v.definition_hash
    from todo_template t join todo_template_version v on v.template_id=t.template_id
    where t.template_code='TD-001' and v.status='PUBLISHED'
    order by v.version_no desc,v.version_id desc limit 1;
  `, process.env.TODO_E2E_DB_NAME) || '').trim().split('\t')
  if (values.length !== 3) throw new Error('Current published TD-001 was not found')
  return { templateId: Number(values[0]), versionId: Number(values[1]), definitionHash: values[2] }
}

async function expectRoutingControlsDoNotOverlap(page) {
  const effect = await page.locator('.routing-effect-card').first().boundingBox()
  const target = await page.locator('.routing-outcome__target').first().boundingBox()
  expect(effect).not.toBeNull()
  expect(target).not.toBeNull()
  const separated = effect.x + effect.width <= target.x || target.x + target.width <= effect.x ||
    effect.y + effect.height <= target.y || target.y + target.height <= effect.y
  expect(separated).toBeTruthy()
}

async function loginAs(page, loginName, secret) {
  if (!secret) throw new Error('TODO_CONFIG_E2E_PASSWORD is required')
  await page.goto('/login')
  await page.locator('input').nth(0).fill(loginName)
  await page.locator('input[type="password"]').fill(secret)
  const responsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST' && new URL(response.url()).pathname === '/prod-api/login'
  )
  await page.locator('button').filter({ hasText: '登录系统' }).click()
  const response = await responsePromise
  expect(response.status()).toBe(200)
  expect((await response.json()).code).toBe(200)
  await expect(page).not.toHaveURL(/\/login(?:\?|$)/, { timeout: 15000 })
}

function sqlLiteral(value) {
  return `'${String(value == null ? '' : value).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`
}
