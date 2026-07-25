const childProcess = require('node:child_process')

function redact(value, secrets) {
  let text = String(value == null ? '' : value)
  secrets.filter(Boolean).forEach(secret => { text = text.split(secret).join('[REDACTED]') })
  return text
}

function sanitizedError(error, secrets) {
  const safe = new Error(redact(error && error.message, secrets))
  ;['code', 'status', 'signal'].forEach(key => {
    if (error && error[key] != null) safe[key] = error[key]
  })
  ;['stdout', 'stderr'].forEach(key => {
    if (error && error[key] != null) safe[key] = redact(error[key], secrets)
  })
  if (error && error.command) safe.command = redact(error.command, secrets)
  return safe
}

function connectionArguments(database, host, env) {
  return [
    '--protocol=tcp',
    `-h${host}`,
    `-P${env.TODO_E2E_DB_PORT || '3306'}`,
    `-u${env.TODO_E2E_DB_USER || 'root'}`,
    '--batch',
    '--skip-column-names',
    '--init-command=SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci',
    database
  ]
}

function createMysqlRunner(options = {}) {
  const env = options.env || process.env
  const exec = options.execFileSync || childProcess.execFileSync
  const spawn = options.spawnSync || childProcess.spawnSync

  function localMysqlAvailable() {
    const probe = spawn('mysql', ['--version'], {
      env,
      stdio: 'ignore',
      windowsHide: true
    })
    return !probe.error && probe.status === 0
  }

  function executeSql(sql, database, executeOptions = {}) {
    if (!database) throw new Error('Todo E2E database name is required')
    const password = env.TODO_E2E_DB_PASSWORD
    if (!password) throw new Error('TODO_E2E_DB_PASSWORD is required')
    const stdio = executeOptions.inherit ? ['pipe', 'inherit', 'inherit'] : ['pipe', 'pipe', 'pipe']
    const common = {
      env: { ...env, MYSQL_PWD: password },
      input: sql,
      encoding: 'utf8',
      stdio,
      windowsHide: true
    }
    try {
      if (localMysqlAvailable()) {
        return exec('mysql', connectionArguments(database, env.TODO_E2E_DB_HOST || '127.0.0.1', env), common) || ''
      }

      const container = env.TODO_E2E_MYSQL_CONTAINER
      if (container) {
        const args = [
          'exec', '-i', '-e', 'MYSQL_PWD', container, 'mysql',
          ...connectionArguments(database, env.TODO_E2E_CONTAINER_DB_HOST || '127.0.0.1', env)
        ]
        return exec('docker', args, common) || ''
      }
    } catch (error) {
      throw sanitizedError(error, [password])
    }

    throw new Error('No MySQL execution path is available: install mysql or set TODO_E2E_MYSQL_CONTAINER explicitly')
  }

  return { executeSql, localMysqlAvailable }
}

const defaultRunner = createMysqlRunner()

module.exports = {
  executeSql: defaultRunner.executeSql,
  localMysqlAvailable: defaultRunner.localMysqlAvailable,
  createMysqlRunner
}
