const path = require('node:path')

function resolveGuidedArtifactDirectory(requested, governedRoot) {
  const root = path.resolve(governedRoot)
  const resolved = path.resolve(requested || root)
  const relative = path.relative(root, resolved)
  if (relative === '..' || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) {
    throw new Error('Refusing guided evidence path outside governed output root')
  }
  return resolved
}

module.exports = { resolveGuidedArtifactDirectory }
