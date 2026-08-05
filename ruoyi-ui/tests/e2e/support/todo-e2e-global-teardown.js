const { executeSql } = require('./mysql-e2e-runner')
const { assertSafeE2eDatabase } = require('./todo-config-e2e-database')

module.exports = async function todoE2eGlobalTeardown() {
  if (process.env.TODO_E2E_REAL_BACKEND !== 'true' ||
      process.env.TODO_E2E_DROP_DATABASE_AFTER !== 'true') return

  const database = process.env.TODO_E2E_DB_NAME
  assertSafeE2eDatabase(database)
  executeSql(`drop database \`${database}\`;`, database)
  console.log(`Dropped disposable Todo E2E database: ${database}`)
}
