const assert = require('node:assert')
const { spawn } = require('node:child_process')
const http = require('node:http')
const path = require('node:path')

async function listen(server, port = 0) {
  await new Promise((resolve, reject) => server.once('error', reject).listen(port, '127.0.0.1', resolve))
  return server.address().port
}

async function close(server) {
  if (!server.listening) return
  await new Promise(resolve => server.close(resolve))
}

async function waitUntilReady(url, child) {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    if (child.exitCode != null) throw new Error(`Production E2E server exited early with ${child.exitCode}`)
    try { if ((await fetch(url)).ok) return } catch (_) { /* still starting */ }
    await new Promise(resolve => setTimeout(resolve, 100))
  }
  throw new Error(`Production E2E server did not become ready: ${url}`)
}

async function main() {
  const backend = http.createServer((request, response) => {
    response.writeHead(200, { 'content-type': 'application/json; charset=utf-8' })
    response.end(JSON.stringify({ path: request.url, method: request.method }))
  })
  const reservation = http.createServer()
  let child
  try {
    const backendPort = await listen(backend)
    const frontendPort = await listen(reservation)
    await close(reservation)
    child = spawn(process.execPath, [path.join(__dirname, 'serve-e2e-production.js')], {
      cwd: path.resolve(__dirname, '..'),
      env: { ...process.env, TODO_E2E_FRONTEND_PORT: String(frontendPort), TODO_E2E_BACKEND_URL: `http://127.0.0.1:${backendPort}` },
      stdio: ['ignore', 'pipe', 'pipe'],
      windowsHide: true
    })
    await waitUntilReady(`http://127.0.0.1:${frontendPort}/`, child)

    const root = await fetch(`http://127.0.0.1:${frontendPort}/`)
    const spa = await fetch(`http://127.0.0.1:${frontendPort}/todo-template`)
    assert.strictEqual(root.status, 200)
    assert.strictEqual(spa.status, 200)
    assert.match(root.headers.get('content-type') || '', /text\/html;\s*charset=utf-8/i)
    const rootHtml = await root.text()
    assert.match(rootHtml, /<meta charset=utf-8>/i, 'Production HTML must declare UTF-8')
    assert.strictEqual(await spa.text(), rootHtml, 'SPA fallback must serve production index.html')

    const scriptPath = rootHtml.match(/<script src=([^ >]+\.js)>/i)
    assert.ok(scriptPath, 'Production HTML must reference a JavaScript bundle')
    const script = await fetch(`http://127.0.0.1:${frontendPort}${scriptPath[1]}`)
    assert.match(script.headers.get('content-type') || '', /application\/javascript;\s*charset=utf-8/i)

    const proxy = await fetch(`http://127.0.0.1:${frontendPort}/prod-api/probe?source=contract`)
    assert.strictEqual(proxy.status, 200)
    assert.deepStrictEqual(await proxy.json(), { path: '/probe?source=contract', method: 'GET' })
    console.log('Production E2E static/proxy contract passed')
  } finally {
    if (child && child.exitCode == null) child.kill()
    await close(reservation)
    await close(backend)
  }
}

main().catch(error => { console.error(error); process.exitCode = 1 })
