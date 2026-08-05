const fs = require('node:fs')
const path = require('node:path')

const reportDir = path.resolve(process.argv[2] || path.join(__dirname, '../../ruoyi-admin/target/surefire-reports'))
const packageName = 'com.ruoyi.web.migration'
const requiredClasses = [
  'FlywayMigrationTest',
  'LeadTemplateConfigurationMySqlIT',
  'FileMaterialEndToEndTest',
  'FileObjectLockOrderExternalMysqlIT',
  'HistoricalMigrationPreflightEndToEndTest',
  'FoundationCollationMigrationTest',
  'PhaseTwoDatabaseInvariantTest',
  'TodoPhaseTwoTransactionTest',
  'TodoRoutingJoinConcurrencyTest',
  'TodoAutoActionFencingConcurrencyTest',
  'TodoDefinitionLedgerConcurrencyTest',
  'TodoAssignmentDelegationMapperExternalMysqlIT',
  'TodoScheduleLockOrderExternalMysqlIT',
  'NavigationMenuEncodingExternalMysqlIT',
  'SystemManagementEncodingExternalMysqlIT',
  'TodoScenarioSimulationExternalMysqlIT',
  'TodoTriggerRuleMetadataMigrationContractTest',
  'LeadFlowMapperExternalMysqlIT',
  'LeadTodoProductionPortsExternalMysqlIT',
  'LeadTodoFlowEndToEndTest',
  'LeadTodoScheduleEndToEndTest',
  'LeadTodoReadModelExternalMysqlIT',
  'LeadProgressCycleRuntimeMySqlTest',
  'LeadTodoReleaseVersionLockExternalMysqlIT',
  'LeadTodoGuidedConfigurationExternalMysqlIT'
]
const exactTestCounts = new Map([
  ['LeadTodoFlowEndToEndTest', 7],
  ['LeadTodoScheduleEndToEndTest', 4],
  ['LeadProgressCycleRuntimeMySqlTest', 6],
  ['LeadTodoReleaseVersionLockExternalMysqlIT', 3]
])

function suiteAttributes(xml, className) {
  const match = xml.match(/<testsuite\b([^>]*)>/)
  if (!match) throw new Error(`${className}: missing <testsuite> root`)
  const attributes = {}
  for (const attribute of match[1].matchAll(/([A-Za-z]+)="([^"]*)"/g)) attributes[attribute[1]] = attribute[2]
  return attributes
}

for (const className of requiredClasses) {
  const reportPackage = className === 'LeadTemplateConfigurationMySqlIT'
    ? 'com.ruoyi.web.todo'
    : packageName
  const report = path.join(reportDir, `TEST-${reportPackage}.${className}.xml`)
  if (!fs.existsSync(report)) throw new Error(`${className}: required Surefire report is missing`)
  const attributes = suiteAttributes(fs.readFileSync(report, 'utf8'), className)
  const tests = Number(attributes.tests)
  const skipped = Number(attributes.skipped)
  const failures = Number(attributes.failures)
  const errors = Number(attributes.errors)
  if (!(tests > 0)) throw new Error(`${className}: tests must be greater than zero`)
  const expectedTests = exactTestCounts.get(className)
  if (expectedTests !== undefined && tests !== expectedTests)
    throw new Error(`${className}: expected exactly ${expectedTests} tests, found ${tests}`)
  if (!(skipped === 0)) throw new Error(`${className}: skipped tests are forbidden`)
  if (!(failures === 0)) throw new Error(`${className}: failures are forbidden`)
  if (!(errors === 0)) throw new Error(`${className}: errors are forbidden`)
}

console.log(`Verified ${requiredClasses.length} external-database Surefire reports with no skips or failures`)
