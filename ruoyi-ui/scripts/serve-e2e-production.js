const fs = require('node:fs')
const http = require('node:http')
const https = require('node:https')
const path = require('node:path')

const host = '127.0.0.1'
const port = Number(process.env.TODO_E2E_FRONTEND_PORT || 4173)
const distRoot = path.resolve(__dirname, '../dist')
const backend = new URL(process.env.TODO_E2E_BACKEND_URL || 'http://127.0.0.1:8080')
const apiPrefix = '/prod-api'

if (!fs.existsSync(path.join(distRoot, 'index.html'))) {
  throw new Error(`Production E2E bundle is missing: ${path.join(distRoot, 'index.html')}`)
}

const mimeTypes = {
  '.css': 'text/css; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.ico': 'image/x-icon',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.map': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2'
}

function proxy(request, response) {
  const incoming = new URL(request.url, `http://${request.headers.host || host}`)
  const strippedPath = incoming.pathname.slice(apiPrefix.length) || '/'
  const headers = { ...request.headers, host: backend.host }
  const client = backend.protocol === 'https:' ? https : http
  const upstream = client.request({
    protocol: backend.protocol,
    hostname: backend.hostname,
    port: backend.port,
    method: request.method,
    path: `${strippedPath}${incoming.search}`,
    headers
  }, upstreamResponse => {
    response.writeHead(upstreamResponse.statusCode || 502, upstreamResponse.headers)
    upstreamResponse.pipe(response)
  })
  upstream.on('error', error => {
    if (!response.headersSent) response.writeHead(502, { 'content-type': 'application/json; charset=utf-8' })
    response.end(JSON.stringify({ message: `E2E backend proxy failed: ${error.message}` }))
  })
  request.pipe(upstream)
}

function serveStatic(request, response) {
  const incoming = new URL(request.url, `http://${request.headers.host || host}`)
  let pathname
  try { pathname = decodeURIComponent(incoming.pathname) } catch (_) { response.writeHead(400); response.end('Bad request'); return }
  const relative = pathname.replace(/^\/+/, '')
  const candidate = path.resolve(distRoot, relative || 'index.html')
  const withinDist = candidate === distRoot || candidate.startsWith(`${distRoot}${path.sep}`)
  if (!withinDist) { response.writeHead(403); response.end('Forbidden'); return }
  const file = fs.existsSync(candidate) && fs.statSync(candidate).isFile() ? candidate : path.join(distRoot, 'index.html')
  response.writeHead(200, { 'content-type': mimeTypes[path.extname(file).toLowerCase()] || 'application/octet-stream' })
  fs.createReadStream(file).pipe(response)
}

const server = http.createServer((request, response) => {
  if (request.url === apiPrefix || request.url.startsWith(`${apiPrefix}/`) || request.url.startsWith(`${apiPrefix}?`)) proxy(request, response)
  else serveStatic(request, response)
})

server.listen(port, host, () => process.stdout.write(`Todo production E2E server listening at http://${host}:${port}\n`))

for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close(() => process.exit(0)))
