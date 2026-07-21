const fs = require('fs')
const path = require('path')

const workflow = fs.readFileSync(path.resolve(__dirname, '../../.github/workflows/ci.yml'), 'utf8')
const applicationDruid = fs.readFileSync(
  path.resolve(__dirname, '../../ruoyi-admin/src/main/resources/application-druid.yml'),
  'utf8'
)
const logback = fs.readFileSync(
  path.resolve(__dirname, '../../ruoyi-admin/src/main/resources/logback.xml'),
  'utf8'
)

if (!/^name:\s+V0\.2 Foundation quality gate$/m.test(workflow)) {
  throw new Error('CI workflow name is not scoped to V0.2 Foundation')
}
const branchMentions = (workflow.match(/v0\.2-Foundation/g) || []).length
if (branchMentions < 2) throw new Error('CI must trigger for v0.2-Foundation push and pull request')
if (/branches:\s*\[[^\]]*V0\.17/i.test(workflow)) throw new Error('stale V0.17 branch trigger remains')
if (!workflow.includes('npm run test:todo-schema')) throw new Error('CI does not execute Todo schema contracts')
if (!workflow.includes('npm run test:foundation-identities')) throw new Error('CI does not execute Foundation test identity UI contract')
if (!workflow.includes("TODO_E2E_BROWSER: ${{ vars.TODO_E2E_BROWSER || 'chrome' }}")) {
  throw new Error('Todo configuration E2E must default to the user-selected Chrome browser')
}
if (!logback.includes('<property name="log.path" value="${LOG_PATH:-/home/ruoyi/logs}" />')) {
  throw new Error('Logback must allow a writable environment-specific log directory')
}
if ((workflow.match(/LOG_PATH:\s+\$\{\{ runner\.temp \}\}\/ruoyi-logs/g) || []).length < 2) {
  throw new Error('Database and real Todo E2E jobs must use a writable runner log directory')
}
const workflowLines = workflow.replace(/\r\n/g, '\n').split('\n')
const documentationStepName = '      - name: Verify Foundation documentation contract'
const documentationStepCommand = '        run: npm run test:foundation-docs'
const documentationStepIndices = workflowLines
  .map((line, index) => line === documentationStepName ? index : -1)
  .filter((index) => index >= 0)
const documentationCommandMentions = workflowLines.filter((line) => line.includes('test:foundation-docs'))
if (
  documentationStepIndices.length !== 1 ||
  workflowLines[documentationStepIndices[0] + 1] !== documentationStepCommand ||
  documentationCommandMentions.length !== 1 ||
  documentationCommandMentions[0] !== documentationStepCommand
) {
  throw new Error('CI must execute the exact Foundation documentation contract step')
}
if (!workflow.includes('FileMaterialEndToEndTest')) throw new Error('CI does not execute the real PRD material E2E')
if (!workflow.includes('FoundationGovernanceRoleMigrationContractTest')) {
  throw new Error('CI does not execute the Foundation governance migration contract')
}
if (!workflow.includes('FoundationTestIdentityEndToEndTest')) {
  throw new Error('CI does not execute the Foundation identity MySQL E2E')
}
if (!workflow.includes('FoundationTestIdentityRollbackTest')) {
  throw new Error('CI does not execute the Foundation identity rollback scenarios')
}
if (!workflow.includes('law_v017_trigger_metadata')) {
  throw new Error('CI must initialize a dedicated trigger metadata migration database')
}
if (!workflow.includes('TodoTriggerRuleMetadataMigrationContractTest')) {
  throw new Error('CI does not execute the trigger metadata MySQL migration E2E')
}
if (!/TODO_MIGRATION_DB_URL:\s+jdbc:mysql:\/\/127\.0\.0\.1:3306\/law_v017_trigger_metadata/.test(workflow)) {
  throw new Error('Trigger metadata migration E2E must use its dedicated database')
}
const triggerMetadataStep = /- name: Verify isolated trigger metadata migration upgrade\s+env:\s+TODO_MIGRATION_DB_URL: jdbc:mysql:\/\/127\.0\.0\.1:3306\/law_v017_trigger_metadata[^\n]*\n\s+TODO_MIGRATION_DB_USER: root\n\s+TODO_MIGRATION_DB_PASSWORD: root\n\s+run: ([^\n]+)/.exec(workflow)
if (!triggerMetadataStep || !/^-Dtest=TodoTriggerRuleMetadataMigrationContractTest(?:\s|$)/.test(triggerMetadataStep[1].replace(/^.*?\s-Dtest=/, '-Dtest='))) {
  throw new Error('Trigger metadata migration E2E must run alone against its isolated database')
}
const sharedMigrationStep = /- name: Execute and verify all Flyway migrations[\s\S]*?\n\s+run: ([^\n]+)/.exec(workflow)
if (!sharedMigrationStep || sharedMigrationStep[1].includes('TodoTriggerRuleMetadataMigrationContractTest')) {
  throw new Error('Trigger metadata migration E2E must not share the law_v017 migration command')
}
const exactV015Baseline = [
  'sql/ry_20260417.sql', 'sql/quartz.sql', 'sql/lead_module_20260602.sql', 'sql/lead_menu_20260602.sql',
  'sql/customer_contract_module_20260603.sql', 'sql/customer_contract_dict_patch_20260611.sql',
  'sql/case_module_20260611.sql', 'sql/matter_module_20260615.sql', 'sql/matter_menu_patch_20260617.sql',
  'sql/finance_module_20260624.sql', 'sql/customer_tag_assign_permission_fix_20260627.sql'
]
const triggerMetadataWorkflow = verifyTriggerMetadataWorkflow(workflow, exactV015Baseline)
const reordered = workflow.replace(
  `${triggerMetadataWorkflow.sharedMigrationStep}\n${triggerMetadataWorkflow.reportGateStep}`,
  `${triggerMetadataWorkflow.reportGateStep}\n${triggerMetadataWorkflow.sharedMigrationStep}`
)
assertWorkflowFixtureFails(reordered, 'reordered external report gate')
const extraBaseline = workflow.replace(
  'sql/customer_tag_assign_permission_fix_20260627.sql',
  'sql/customer_tag_assign_permission_fix_20260627.sql\n              sql/unapproved_twelfth_baseline.sql'
)
assertWorkflowFixtureFails(extraBaseline, 'extra baseline file')
const migrationDatabaseUrls = workflow.match(/TODO_MIGRATION_DB_URL:\s+([^\r\n]+)/g) || []
if (
  migrationDatabaseUrls.length < 2 ||
  migrationDatabaseUrls.some(url => !url.includes('connectionCollation=utf8mb4_unicode_ci'))
) {
  throw new Error('Foundation migration databases must use utf8mb4_unicode_ci connection collation')
}
if (!/url:\s+\$\{DB_URL:[^}\r\n]*connectionCollation=utf8mb4_unicode_ci[^}\r\n]*\}/.test(applicationDruid)) {
  throw new Error('Default application database URL must use utf8mb4_unicode_ci connection collation')
}
const approvedPasswordAssignment = 'run: echo "FOUNDATION_TEST_USER_PASSWORD=$(openssl rand -base64 32)" >> "$GITHUB_ENV"'
const passwordMentions = workflow.split(/\r?\n/)
  .filter(line => line.includes('FOUNDATION_TEST_USER_PASSWORD'))
  .map(line => line.trim())
if (passwordMentions.length !== 1 || passwordMentions[0] !== approvedPasswordAssignment) {
  throw new Error('CI must contain only the approved ephemeral Foundation test password assignment')
}

console.log('Foundation CI contract ok')

function assertWorkflowFixtureFails(candidate, label) {
  try {
    verifyTriggerMetadataWorkflow(candidate, exactV015Baseline)
  } catch (error) {
    return
  }
  throw new Error(`${label} fixture unexpectedly passed`)
}

function verifyTriggerMetadataWorkflow(source, expectedBaseline) {
  const createDatabaseStep = workflowStep(source, 'Create isolated Foundation and trigger metadata test databases')
  const baselineStep = workflowStep(source, 'Initialize v0.15 baseline schemas in empty databases')
  const isolatedMigrationStep = workflowStep(source, 'Verify isolated trigger metadata migration upgrade')
  const sharedMigrationStep = workflowStep(source, 'Execute and verify all Flyway migrations')
  const reportGateStep = workflowStep(source, 'Assert external-database Todo tests were not skipped')
  const ordered = [createDatabaseStep, baselineStep, isolatedMigrationStep, sharedMigrationStep, reportGateStep]
  for (let index = 1; index < ordered.length; index += 1) {
    if (ordered[index - 1].index >= ordered[index].index) {
      throw new Error('Trigger metadata workflow steps must be ordered: create database, baseline, isolated migration, shared migrations, report gate')
    }
  }
  if (!baselineStep.text.includes('for database in law_v017 law_v017_foundation law_v017_trigger_metadata; do')) {
    throw new Error('Trigger metadata database must participate in the exact v0.15 baseline loop')
  }
  if (!createDatabaseStep.text.includes('alter database law_v017 character set utf8mb4 collate utf8mb4_unicode_ci')) {
    throw new Error('CI must normalize the auto-created law_v017 database collation before importing the baseline')
  }
  const baselinePaths = Array.from(baselineStep.text.matchAll(/^\s+(sql\/[^\s]+\.sql)\s*(?:\\|;\s+do)?\s*$/gm)).map(match => match[1])
  if (JSON.stringify(baselinePaths) !== JSON.stringify(expectedBaseline)) {
    throw new Error(`Trigger metadata baseline SQL files must exactly match the approved ordered eleven-file baseline: ${JSON.stringify(baselinePaths)}`)
  }
  if (!isolatedMigrationStep.text.includes('-Dtest=TodoTriggerRuleMetadataMigrationContractTest')) {
    throw new Error('Trigger metadata migration E2E must run alone against its isolated database')
  }
  if (sharedMigrationStep.text.includes('TodoTriggerRuleMetadataMigrationContractTest')) {
    throw new Error('Trigger metadata migration E2E must not share the law_v017 migration command')
  }
  const sharedMigrationRuns = Array.from(sharedMigrationStep.text.matchAll(/-Dtest=([^\s]+)/g))
    .map(match => match[1])
  if (sharedMigrationRuns.length !== 2 || sharedMigrationRuns[0] !== 'FlywayMigrationTest' ||
      sharedMigrationRuns[1].split(',').includes('FlywayMigrationTest')) {
    throw new Error('FlywayMigrationTest must run alone before the remaining shared-database migration tests')
  }
  return { createDatabaseStep, baselineStep, isolatedMigrationStep, sharedMigrationStep: sharedMigrationStep.text, reportGateStep: reportGateStep.text }
}

function workflowStep(source, name) {
  const marker = `      - name: ${name}`
  const index = source.indexOf(marker)
  if (index < 0) throw new Error(`Missing workflow step: ${name}`)
  const next = source.indexOf('\n      - name:', index + marker.length)
  return { index, text: source.slice(index, next < 0 ? source.length : next) }
}
