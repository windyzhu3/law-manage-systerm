const fs = require('fs')
const path = require('path')

const workflow = fs.readFileSync(path.resolve(__dirname, '../../.github/workflows/ci.yml'), 'utf8')

if (!/^name:\s+V0\.2 Foundation quality gate$/m.test(workflow)) {
  throw new Error('CI workflow name is not scoped to V0.2 Foundation')
}
const branchMentions = (workflow.match(/v0\.2-Foundation/g) || []).length
if (branchMentions < 2) throw new Error('CI must trigger for v0.2-Foundation push and pull request')
if (/branches:\s*\[[^\]]*V0\.17/i.test(workflow)) throw new Error('stale V0.17 branch trigger remains')
if (!workflow.includes('npm run test:todo-schema')) throw new Error('CI does not execute Todo schema contracts')
if (!workflow.includes('npm run test:foundation-identities')) throw new Error('CI does not execute Foundation test identity UI contract')
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
if (!workflow.includes('echo "FOUNDATION_TEST_USER_PASSWORD=$(openssl rand -base64 32)" >> "$GITHUB_ENV"')) {
  throw new Error('CI does not generate an ephemeral Foundation test password')
}
if (/FOUNDATION_TEST_USER_PASSWORD:\s*[^$\s][^\r\n]*/.test(workflow)) {
  throw new Error('CI must not commit a fixed Foundation test password')
}

console.log('Foundation CI contract ok')
