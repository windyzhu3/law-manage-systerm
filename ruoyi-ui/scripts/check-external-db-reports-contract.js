const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { spawnSync } = require('node:child_process')

const gate = path.join(__dirname, 'assert-external-db-reports.js')
const packageName = 'com.ruoyi.web.migration'
const classes = [
  'FlywayMigrationTest',
  'FileMaterialEndToEndTest',
  'HistoricalMigrationPreflightEndToEndTest',
  'FoundationCollationMigrationTest',
  'PhaseTwoDatabaseInvariantTest',
  'TodoPhaseTwoTransactionTest',
  'TodoRoutingJoinConcurrencyTest',
  'TodoAutoActionFencingConcurrencyTest',
  'TodoDefinitionLedgerConcurrencyTest'
  ,'TodoTriggerRuleMetadataMigrationContractTest'
]
const root = fs.mkdtempSync(path.join(os.tmpdir(), 'todo-external-reports-'))

function reportPath(className) { return path.join(root, `TEST-${packageName}.${className}.xml`) }
function writeReport(className, values = {}) {
  const attributes = { tests: 1, skipped: 0, failures: 0, errors: 0, ...values }
  fs.writeFileSync(reportPath(className), `<?xml version="1.0"?><testsuite name="${className}" tests="${attributes.tests}" skipped="${attributes.skipped}" failures="${attributes.failures}" errors="${attributes.errors}"><testcase name="contract"/></testsuite>`)
}
function run() { return spawnSync(process.execPath, [gate, root], { encoding: 'utf8' }) }
function requirePass(label) { const result = run(); if (result.status !== 0) throw new Error(`${label} unexpectedly failed: ${result.stderr}`) }
function requireFailure(label) { const result = run(); if (result.status === 0) throw new Error(`${label} unexpectedly passed`) }

try {
  classes.forEach(className => writeReport(className))
  requirePass('complete reports')

  fs.unlinkSync(reportPath(classes[0]))
  requireFailure('missing report')
  writeReport(classes[0])

  writeReport(classes[1], { tests: 0 })
  requireFailure('zero tests')
  writeReport(classes[1])

  writeReport(classes[2], { skipped: 1 })
  requireFailure('skipped test')
  writeReport(classes[2])

  writeReport(classes[3], { failures: 1 })
  requireFailure('failed test')
  writeReport(classes[3])

  writeReport(classes[4], { errors: 1 })
  requireFailure('errored test')
  console.log('External-database Surefire report gate negative contract passed')
} finally {
  fs.rmSync(root, { recursive: true, force: true })
}
