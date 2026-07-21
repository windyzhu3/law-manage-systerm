const fs = require('fs')
const path = require('path')

const workflow = fs.readFileSync(path.resolve(__dirname, '../../.github/workflows/ci.yml'), 'utf8')
const applicationDruid = fs.readFileSync(
  path.resolve(__dirname, '../../ruoyi-admin/src/main/resources/application-druid.yml'),
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
if (!workflow.includes('for database in law_v017 law_v017_foundation law_v017_trigger_metadata; do') || exactV015Baseline.some(file => !workflow.includes(file))) {
  throw new Error('Trigger metadata database must receive the exact eleven-file v0.15 baseline')
}
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
