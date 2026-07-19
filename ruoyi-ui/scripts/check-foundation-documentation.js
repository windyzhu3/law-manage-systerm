const fs = require('fs')
const path = require('path')

const repoRoot = path.resolve(__dirname, '..', '..')
const canonicalRelative = 'doc/reviews/v0.2-foundation-admission-report.md'
const compatibilityRelative = 'doc/v0.2-foundation-admission-report.md'
const canonicalPath = path.join(repoRoot, canonicalRelative)
const compatibilityPath = path.join(repoRoot, compatibilityRelative)

const inboundSources = [
  'ruoyi-admin/src/main/resources/db/migration/V0_20_18__foundation_historical_migration_readiness.sql',
  'ruoyi-admin/src/main/resources/db/migration/V0_20_19__foundation_file_security_readiness.sql',
  'ruoyi-admin/src/main/resources/db/migration/V0_20_21__foundation_phase_one_acceptance.sql',
  'ruoyi-ui/e2e/todo-foundation-config-resources.spec.js'
]

const errors = []

function read(relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), 'utf8')
}

function lineAt(content, lineNumber) {
  return content.split(/\r?\n/)[lineNumber - 1]
}

function requireTokens(line, lineNumber, tokens) {
  const missing = tokens.filter((token) => !line || !line.includes(token))
  if (missing.length > 0) {
    errors.push(`${compatibilityRelative}:${lineNumber} is not a meaningful compatibility anchor; missing ${missing.join(', ')}`)
  }
}

function checkLocalMarkdownLinks(relativePath, content) {
  const linkPattern = /\[[^\]]+\]\(([^)]+)\)/g
  let match
  while ((match = linkPattern.exec(content)) !== null) {
    const target = match[1].split('#')[0]
    if (!target || /^(?:https?:|mailto:)/.test(target)) {
      continue
    }
    const resolved = path.resolve(repoRoot, path.dirname(relativePath), target)
    if (!fs.existsSync(resolved)) {
      errors.push(`${relativePath} has a broken local link: ${match[1]}`)
    }
  }
}

if (!fs.existsSync(canonicalPath)) {
  errors.push(`canonical admission report is missing: ${canonicalRelative}`)
} else {
  const canonical = read(canonicalRelative)
  if (!canonical.includes('### 2.1 后端 21 项基础能力')) {
    errors.push('canonical admission report must label the 21-item backend capability list accurately')
  }
  if (!canonical.includes('MySQL 8.0.46')) {
    errors.push('canonical admission report must identify MySQL 8.0.46 as the current verification server')
  }
  canonical.split(/\r?\n/).forEach((line, index) => {
    const historicalMysql84 = /(?:历史证据|此前|曾).{0,60}MySQL 8\.4|MySQL 8\.4.{0,60}(?:历史证据|此前|曾)/
    if (line.includes('MySQL 8.4') && !historicalMysql84.test(line)) {
      errors.push(`${canonicalRelative}:${index + 1} presents MySQL 8.4 as current rather than historical evidence`)
    }
  })
  checkLocalMarkdownLinks(canonicalRelative, canonical)
}

const referenceCounts = new Map()
for (const relativePath of inboundSources) {
  const content = read(relativePath)
  const referencePattern = /doc\/v0\.2-foundation-admission-report\.md:(\d+)/g
  let match
  while ((match = referencePattern.exec(content)) !== null) {
    const lineNumber = Number(match[1])
    referenceCounts.set(lineNumber, (referenceCounts.get(lineNumber) || 0) + 1)
  }
}

const expectedReferenceCounts = new Map([[105, 5], [109, 1], [117, 2]])
for (const [lineNumber, count] of expectedReferenceCounts) {
  if (referenceCounts.get(lineNumber) !== count) {
    errors.push(`expected ${count} immutable source_ref references to ${compatibilityRelative}:${lineNumber}, found ${referenceCounts.get(lineNumber) || 0}`)
  }
}
for (const lineNumber of referenceCounts.keys()) {
  if (!expectedReferenceCounts.has(lineNumber)) {
    errors.push(`unexpected immutable source_ref target: ${compatibilityRelative}:${lineNumber}`)
  }
}

if (!fs.existsSync(compatibilityPath)) {
  errors.push(`compatibility evidence document is missing: ${compatibilityRelative}`)
} else {
  const compatibility = read(compatibilityRelative)
  if (!compatibility.includes('[当前准入报告](reviews/v0.2-foundation-admission-report.md)')) {
    errors.push(`${compatibilityRelative} must link to the canonical current admission report`)
  }
  requireTokens(lineAt(compatibility, 105), 105, ['G-04', 'NEEDS_EVIDENCE', '异常清单', '批次', '幂等', '校验', '回滚'])
  requireTokens(lineAt(compatibility, 109), 109, ['G-05', 'NEEDS_EVIDENCE', 'PRD', '上传', '预览', '下载', '审计'])
  requireTokens(lineAt(compatibility, 117), 117, ['G-07', 'NEEDS_EVIDENCE', '黄金数据', '独立 Reviewer'])
  checkLocalMarkdownLinks(compatibilityRelative, compatibility)
}

if (errors.length > 0) {
  throw new Error(`Foundation documentation contract failed:\n- ${errors.join('\n- ')}`)
}

console.log('Foundation documentation compatibility contract passed.')
