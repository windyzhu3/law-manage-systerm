const assert = require('node:assert/strict')
const test = require('node:test')
const { createMysqlRunner } = require('./mysql-e2e-runner')

const SECRET = 'not-for-process-arguments'

function dockerRunner(execute) {
  return createMysqlRunner({
    env: {
      TODO_E2E_DB_PASSWORD: SECRET,
      TODO_E2E_MYSQL_CONTAINER: 'mysql-e2e',
      TODO_E2E_DB_USER: 'root',
      TODO_E2E_DB_PORT: '3306'
    },
    spawnSync: () => ({ error: new Error('mysql is unavailable'), status: 1 }),
    execFileSync: execute
  })
}

test('docker fallback passes only MYSQL_PWD name in arguments', () => {
  let invocation
  const runner = dockerRunner((file, args, options) => {
    invocation = { file, args, options }
    return 'ok'
  })

  assert.equal(runner.executeSql('select 1', 'law_e2e'), 'ok')
  assert.equal(invocation.file, 'docker')
  assert.deepEqual(invocation.args.slice(0, 5), ['exec', '-i', '-e', 'MYSQL_PWD', 'mysql-e2e'])
  assert.equal(JSON.stringify(invocation.args).includes(SECRET), false)
  assert.equal(invocation.options.env.MYSQL_PWD, SECRET)
})

test('runner errors and diagnostic fields redact the database secret', () => {
  const runner = dockerRunner(() => {
    const error = new Error(`docker failed with ${SECRET}`)
    error.stderr = Buffer.from(`stderr=${SECRET}`)
    error.stdout = `stdout=${SECRET}`
    error.command = `docker exec -e MYSQL_PWD=${SECRET}`
    throw error
  })

  assert.throws(
    () => runner.executeSql('select 1', 'law_e2e'),
    error => {
      const rendered = JSON.stringify({
        message: error.message,
        stderr: error.stderr,
        stdout: error.stdout,
        command: error.command
      })
      assert.equal(rendered.includes(SECRET), false)
      assert.match(rendered, /\[REDACTED\]/)
      return true
    }
  )
})
