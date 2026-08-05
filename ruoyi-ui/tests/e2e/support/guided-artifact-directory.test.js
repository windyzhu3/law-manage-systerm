const assert = require('node:assert/strict')
const path = require('node:path')
const test = require('node:test')

const { resolveGuidedArtifactDirectory } = require('./guided-artifact-directory')

const governedRoot = path.resolve('C:/fixture/output/playwright/lead-todo-guided-configuration')

test('defaults guided evidence to the governed root', () => {
  assert.equal(resolveGuidedArtifactDirectory(undefined, governedRoot), governedRoot)
})

test('accepts a normalized child run directory', () => {
  const requested = path.join(governedRoot, 'runs', 'run-001')
  assert.equal(resolveGuidedArtifactDirectory(requested, governedRoot), requested)
})

test('rejects traversal and sibling-prefix evidence paths', () => {
  for (const requested of [
    path.join(governedRoot, '..', 'escaped'),
    `${governedRoot}-sibling`,
    path.resolve('C:/fixture/outside')
  ]) {
    assert.throws(
      () => resolveGuidedArtifactDirectory(requested, governedRoot),
      /outside governed output root/
    )
  }
})
