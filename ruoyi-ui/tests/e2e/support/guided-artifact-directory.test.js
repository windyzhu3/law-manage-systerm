const assert = require('node:assert/strict')
const path = require('node:path')
const test = require('node:test')

const { resolveGuidedArtifactDirectory } = require('./guided-artifact-directory')

const governedRoot = path.resolve('C:/fixture/output/playwright/lead-todo-guided-configuration')

test('requires an explicit guided evidence directory', () => {
  assert.throws(() => resolveGuidedArtifactDirectory(undefined, governedRoot), /TODO_E2E_ARTIFACT_DIR is required/)
})

test('accepts a normalized child run directory', () => {
  const requested = path.join(governedRoot, 'runs', 'run-001')
  assert.equal(resolveGuidedArtifactDirectory(requested, governedRoot), requested)
})

test('rejects traversal and sibling-prefix evidence paths', () => {
  for (const requested of [
    governedRoot,
    path.join(governedRoot, '..', 'escaped'),
    `${governedRoot}-sibling`,
    path.resolve('C:/fixture/outside'),
    path.join(governedRoot, 'runs', 'run-001', 'nested')
  ]) {
    assert.throws(
      () => resolveGuidedArtifactDirectory(requested, governedRoot),
      /exact governed runs(?:\/<safeRunId>)? directory|outside governed output root/
    )
  }
})
