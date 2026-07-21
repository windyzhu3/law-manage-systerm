const { defineConfig } = require('@playwright/test')

const realBackend = process.env.TODO_E2E_REAL_BACKEND === 'true'
const browserChannel = process.env.TODO_E2E_BROWSER

module.exports = defineConfig({
  testDir: '.',
  testMatch: ['e2e/**/*.spec.js', 'tests/e2e/**/*.spec.js'],
  timeout: realBackend ? 90000 : 30000,
  retries: 0,
  use: {
    baseURL: 'http://127.0.0.1:4173',
    trace: 'retain-on-failure',
    ...(browserChannel ? { channel: browserChannel } : {})
  },
  webServer: realBackend
    ? { command: 'npm run dev -- --port 4173', port: 4173, reuseExistingServer: true, timeout: 120000, env: { BROWSER: 'none' } }
    : { command: 'node node_modules/serve/build/main.js -s dist -l 4173', port: 4173, reuseExistingServer: true },
  reporter: [['list'], ['html', { outputFolder: 'output/playwright/report', open: 'never' }]]
})
