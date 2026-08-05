const path = require('node:path')

function resolveGuidedArtifactDirectory(requested, governedRoot) {
  if (typeof requested !== 'string' || !requested.trim()) {
    throw new Error('TODO_E2E_ARTIFACT_DIR is required for guided Lead Todo evidence')
  }
  const root = path.resolve(governedRoot)
  const resolved = path.resolve(requested)
  const relative = path.relative(root, resolved)
  if (relative === '..' || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) {
    throw new Error('Refusing guided evidence path outside governed output root')
  }
  const segments = relative.split(path.sep)
  if (segments.length !== 2 || segments[0] !== 'runs' || !/^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$/.test(segments[1])) {
    throw new Error('Refusing guided evidence path that is not an exact governed runs/<safeRunId> directory')
  }
  return resolved
}

module.exports = { resolveGuidedArtifactDirectory }
