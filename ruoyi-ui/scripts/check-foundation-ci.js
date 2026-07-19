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
const migrationDatabaseUrls = workflow.match(/TODO_MIGRATION_DB_URL:\s+([^\r\n]+)/g) || []
if (
  migrationDatabaseUrls.length !== 2 ||
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
