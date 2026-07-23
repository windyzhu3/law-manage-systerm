const fs = require('node:fs')
const path = require('node:path')
const { spawn } = require('node:child_process')
const { chromium } = require('@playwright/test')
const {
  cleanupTodoConfiguration,
  loadJourneyFixture,
  requireEnv
} = require('../tests/e2e/support/todo-config-e2e-database')

const uiRoot = path.resolve(__dirname, '..')
const repositoryRoot = path.resolve(uiRoot, '..')
const outputRoot = path.join(repositoryRoot, 'output', 'playwright', 'todo-phase-two-ux')
const port = 4174
const baseURL = `http://127.0.0.1:${port}`

async function waitForServer(server) {
  const deadline = Date.now() + 15000
  while (Date.now() < deadline) {
    if (server.exitCode != null) {
      throw new Error(`Todo visual QA server exited early with code ${server.exitCode}`)
    }
    try {
      const response = await fetch(baseURL)
      if (response.ok) return
    } catch (_) {
      // The server has not started listening yet.
    }
    await new Promise(resolve => setTimeout(resolve, 150))
  }
  throw new Error(`Todo visual QA server did not become ready at ${baseURL}`)
}

async function login(page, username, password) {
  await page.goto(`${baseURL}/login`)
  await page.locator('input').nth(0).fill(username)
  await page.locator('input[type="password"]').fill(password)
  const responsePromise = page.waitForResponse(response =>
    response.request().method() === 'POST' &&
    new URL(response.url()).pathname === '/prod-api/login'
  )
  await page.locator('button').filter({ hasText: '登录系统' }).click()
  const response = await responsePromise
  if (!response.ok()) throw new Error(`Todo visual QA login failed with HTTP ${response.status()}`)
  const payload = await response.json()
  if (Number(payload.code) !== 200) throw new Error(`Todo visual QA login failed: ${payload.msg || payload.code}`)
  await page.waitForURL(url => !/\/login(?:\?|$)/.test(url.pathname), { timeout: 15000 })
}

async function capture(page, fixture, width, height) {
  await page.setViewportSize({ width, height })
  await page.goto(`${baseURL}/todo-engine/todo-template-journey?templateId=${fixture.templateId}&step=DOD`)
  await page.getByTestId('template-journey-shell').waitFor({ state: 'visible' })
  await page.locator('.dod-step').waitFor({ state: 'visible' })
  await page.locator('.journey-editor').waitFor({ state: 'visible' })
  await page.locator('.journey-aside').waitFor({ state: 'visible' })
  const title = await page.locator('.journey-page__title h1').innerText()
  if (/[?\uFFFD]/.test(title)) {
    throw new Error(`Todo visual QA template title contains a replacement marker: ${title}`)
  }
  await page.addStyleTag({
    content: '*,*::before,*::after{animation-duration:0s!important;transition-duration:0s!important;caret-color:transparent!important}'
  })
  await page.waitForTimeout(250)
  const target = path.join(outputRoot, `${width}x${height}-implementation.png`)
  await page.screenshot({ path: target, fullPage: false, animations: 'disabled' })
  const size = fs.statSync(target).size
  if (!size) throw new Error(`Todo visual QA screenshot is empty: ${target}`)
  process.stdout.write(`captured ${target} (${size} bytes)\n`)
}

async function main() {
  const password = requireEnv('TODO_CONFIG_E2E_PASSWORD')
  const fixture = loadJourneyFixture('WARNING')
  fs.mkdirSync(outputRoot, { recursive: true })

  const server = spawn(process.execPath, ['scripts/serve-e2e-production.js'], {
    cwd: uiRoot,
    env: {
      ...process.env,
      TODO_E2E_FRONTEND_PORT: String(port),
      TODO_E2E_BACKEND_URL: process.env.TODO_E2E_BACKEND_URL || 'http://127.0.0.1:8080'
    },
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true
  })
  let browser
  const browserErrors = []
  try {
    server.stdout.on('data', value => process.stdout.write(value))
    server.stderr.on('data', value => process.stderr.write(value))
    await waitForServer(server)
    browser = await chromium.launch({ channel: process.env.TODO_E2E_BROWSER || 'chrome', headless: true })
    const page = await browser.newPage()
    page.on('pageerror', error => browserErrors.push(`pageerror: ${error.message}`))
    page.on('console', message => {
      if (message.type() === 'error') browserErrors.push(`console: ${message.text()}`)
    })
    await login(page, 'todo_config_admin', password)
    await capture(page, fixture, 1440, 1024)
    await capture(page, fixture, 1920, 1080)
    if (browserErrors.length) {
      throw new Error(`Todo visual QA captured browser errors:\n${browserErrors.join('\n')}`)
    }
  } finally {
    if (browser) await browser.close()
    if (server.exitCode == null) server.kill()
    cleanupTodoConfiguration(fixture.templateCode)
  }
}

main().catch(error => {
  process.stderr.write(`${error.stack || error}\n`)
  process.exitCode = 1
})
