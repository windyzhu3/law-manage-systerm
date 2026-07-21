const fs = require('fs')
const os = require('os')
const path = require('path')
const { spawnSync } = require('child_process')

const repoRoot = path.resolve(__dirname, '..', '..')
const failures = []

function runNode(scriptPath) {
  return spawnSync(process.execPath, [scriptPath], { encoding: 'utf8' })
}

function outputOf(result) {
  return `${result.stdout || ''}${result.stderr || ''}`
}

function expectSuccess(name, result) {
  if (result.status !== 0) {
    failures.push(`${name} baseline failed unexpectedly:\n${outputOf(result)}`)
  }
}

function expectRejected(name, result, expectedMessage) {
  const output = outputOf(result)
  if (result.status === 0) {
    failures.push(`${name} mutation was not rejected`)
  } else if (!output.includes(expectedMessage)) {
    failures.push(`${name} failed for the wrong reason; expected ${expectedMessage}:\n${output}`)
  }
}

function copyFile(relativePath, targetRoot) {
  const target = path.join(targetRoot, relativePath)
  fs.mkdirSync(path.dirname(target), { recursive: true })
  fs.copyFileSync(path.join(repoRoot, relativePath), target)
}

function makeCiFixture(workflowMutation) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'foundation-ci-contract-'))
  copyFile('ruoyi-ui/scripts/check-foundation-ci.js', root)
  copyFile('ruoyi-admin/src/main/resources/application-druid.yml', root)
  copyFile('ruoyi-admin/src/main/resources/logback.xml', root)
  const workflowRelative = '.github/workflows/ci.yml'
  const workflow = fs.readFileSync(path.join(repoRoot, workflowRelative), 'utf8').replace(/\r\n/g, '\n')
  const target = path.join(root, workflowRelative)
  fs.mkdirSync(path.dirname(target), { recursive: true })
  fs.writeFileSync(target, workflowMutation(workflow), 'utf8')
  return root
}

function makeDocumentationFixture() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'foundation-doc-contract-'))
  copyFile('ruoyi-ui/scripts/check-foundation-documentation.js', root)
  fs.cpSync(path.join(repoRoot, 'doc'), path.join(root, 'doc'), { recursive: true })
  const sources = [
    'ruoyi-admin/src/main/resources/db/migration/V0_20_18__foundation_historical_migration_readiness.sql',
    'ruoyi-admin/src/main/resources/db/migration/V0_20_19__foundation_file_security_readiness.sql',
    'ruoyi-admin/src/main/resources/db/migration/V0_20_21__foundation_phase_one_acceptance.sql',
    'ruoyi-ui/e2e/todo-foundation-config-resources.spec.js'
  ]
  sources.forEach((source) => copyFile(source, root))
  return root
}

function mutateRecordSourceRef(root, relativePath, requirementCode, expectedRef, replacementRef) {
  const target = path.join(root, relativePath)
  const lines = fs.readFileSync(target, 'utf8').split(/\r?\n/)
  let mutations = 0
  const mutated = lines.map((line) => {
    if (!line.includes(`'${requirementCode}'`)) return line
    if (!line.includes(`'${expectedRef}'`)) {
      failures.push(`mutation fixture could not find ${requirementCode} -> ${expectedRef} in ${relativePath}`)
      return line
    }
    mutations += 1
    return line.replace(`'${expectedRef}'`, `'${replacementRef}'`)
  })
  if (mutations !== 1) {
    failures.push(`mutation fixture expected one ${requirementCode} record in ${relativePath}, found ${mutations}`)
  }
  fs.writeFileSync(target, mutated.join('\n'), 'utf8')
}

expectSuccess('CI contract', runNode(path.join(repoRoot, 'ruoyi-ui/scripts/check-foundation-ci.js')))
expectSuccess('documentation contract', runNode(path.join(repoRoot, 'ruoyi-ui/scripts/check-foundation-documentation.js')))

const documentationStep = [
  '      - name: Verify Foundation documentation contract',
  '        run: npm run test:foundation-docs'
].join('\n')

const removedStepRoot = makeCiFixture((workflow) => workflow.replace(`${documentationStep}\n`, ''))
expectRejected(
  'CI documentation-step removal',
  runNode(path.join(removedStepRoot, 'ruoyi-ui/scripts/check-foundation-ci.js')),
  'CI must execute the exact Foundation documentation contract step'
)

const alteredStepRoot = makeCiFixture((workflow) => workflow.replace(
  '        run: npm run test:foundation-docs',
  '        run: npm run test:foundation-docs -- --skip-contract'
))
expectRejected(
  'CI documentation-step alteration',
  runNode(path.join(alteredStepRoot, 'ruoyi-ui/scripts/check-foundation-ci.js')),
  'CI must execute the exact Foundation documentation contract step'
)

const swappedReferenceRoot = makeDocumentationFixture()
mutateRecordSourceRef(
  swappedReferenceRoot,
  'ruoyi-admin/src/main/resources/db/migration/V0_20_18__foundation_historical_migration_readiness.sql',
  'BACKFILL_VALIDATION_SQL',
  'doc/v0.2-foundation-admission-report.md:105',
  'doc/v0.2-foundation-admission-report.md:109'
)
mutateRecordSourceRef(
  swappedReferenceRoot,
  'ruoyi-admin/src/main/resources/db/migration/V0_20_19__foundation_file_security_readiness.sql',
  'PRD_MATERIAL_TYPE_E2E',
  'doc/v0.2-foundation-admission-report.md:109',
  'doc/v0.2-foundation-admission-report.md:105'
)
expectRejected(
  'semantic source_ref line swap',
  runNode(path.join(swappedReferenceRoot, 'ruoyi-ui/scripts/check-foundation-documentation.js')),
  'BACKFILL_VALIDATION_SQL must reference doc/v0.2-foundation-admission-report.md:105'
)

for (const root of [removedStepRoot, alteredStepRoot, swappedReferenceRoot]) {
  fs.rmSync(root, { recursive: true, force: true })
}

if (failures.length > 0) {
  throw new Error(`Foundation contract mutation checks failed:\n- ${failures.join('\n- ')}`)
}

console.log('Foundation contract mutation checks passed (2 baselines, 3 negative mutations).')
