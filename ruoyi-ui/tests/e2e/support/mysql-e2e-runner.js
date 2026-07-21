const { execFileSync, spawnSync } = require('node:child_process')

function localMysqlAvailable() {
  const probe = spawnSync('mysql', ['--version'], { stdio: 'ignore', windowsHide: true })
  return !probe.error && probe.status === 0
}

function connectionArguments(database, host) {
  return [
    '--protocol=tcp',
    `-h${host}`,
    `-P${process.env.TODO_E2E_DB_PORT || '3306'}`,
    `-u${process.env.TODO_E2E_DB_USER || 'root'}`,
    '--batch',
    '--skip-column-names',
    database
  ]
}

function executeSql(sql, database, options = {}) {
  if (!database) throw new Error('Todo E2E database name is required')
  const password = process.env.TODO_E2E_DB_PASSWORD
  if (!password) throw new Error('TODO_E2E_DB_PASSWORD is required')
  const stdio = options.inherit ? ['pipe', 'inherit', 'inherit'] : ['pipe', 'pipe', 'pipe']
  const common = { env: { ...process.env, MYSQL_PWD: password }, input: sql, encoding: 'utf8', stdio, windowsHide: true }

  if (localMysqlAvailable()) {
    return execFileSync('mysql', connectionArguments(database, process.env.TODO_E2E_DB_HOST || '127.0.0.1'), common) || ''
  }

  const container = process.env.TODO_E2E_MYSQL_CONTAINER
  if (container) {
    const args = ['exec', '-i', '-e', `MYSQL_PWD=${password}`, container, 'mysql', ...connectionArguments(database, process.env.TODO_E2E_CONTAINER_DB_HOST || '127.0.0.1')]
    return execFileSync('docker', args, { ...common, env: process.env }) || ''
  }

  throw new Error('No MySQL execution path is available: install mysql or set TODO_E2E_MYSQL_CONTAINER explicitly')
}

module.exports = { executeSql, localMysqlAvailable }
