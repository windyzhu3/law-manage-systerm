const fs = require('fs')
const path = require('path')

const uiRoot = path.resolve(__dirname, '..')
const read = relativePath => fs.readFileSync(path.join(uiRoot, relativePath), 'utf8')
const componentDir = path.join(uiRoot, 'src/components/TestIdentityTag')
const componentFiles = collectFiles(componentDir)
const componentSources = componentFiles.map(file => ({ file, source: fs.readFileSync(file, 'utf8') }))
const tag = read('src/components/TestIdentityTag/index.vue')
const userList = read('src/views/system/user/index.vue')
const userDetail = read('src/views/system/user/view.vue')
const userSources = [userList, userDetail].join('\n')

if (componentFiles.length === 0) throw new Error('TestIdentityTag must contain production source files')
if ((tag.match(/<el-tag\b/g) || []).length !== 1) throw new Error('TestIdentityTag must contain exactly one Element UI tag')
if (!tag.includes(`<el-tag v-if="userType === '99'" type="warning" size="mini">测试身份</el-tag>`)) {
  throw new Error('TestIdentityTag must use the exact string-99 warning mini label gate')
}
if (!tag.includes("userType: {\n      type: String,")) throw new Error('TestIdentityTag must accept userType as a string prop')

assertExactIntegration(
  userList,
  'src/views/system/user/index.vue',
  'import TestIdentityTag from "@/components/TestIdentityTag"',
  'components: { Treeselect, TreePanel, ExcelImportDialog, UserViewDrawer, TestIdentityTag },',
  '<test-identity-tag :user-type="scope.row.userType" />'
)
assertExactIntegration(
  userDetail,
  'src/views/system/user/view.vue',
  "import TestIdentityTag from '@/components/TestIdentityTag'",
  'components: { TestIdentityTag },',
  '<test-identity-tag :user-type="info.userType" />'
)

for (const { file, source } of componentSources) {
  if (/\bv-(?:html|text|model)\b/i.test(source)) throw new Error(`${file} must not bind rendered content`)
  if (/\b(?:password|passwd|pwd|hash|bcrypt|secret|token|credential)\b/i.test(source)) throw new Error(`${file} must not reference sensitive values`)
  if (/@click\b|v-on:click\b|\bcopy\b|\bdownload\b|\bhref\s*=|\.href\b/i.test(source)) {
    throw new Error(`${file} must not expose click, copy, download, or href behavior`)
  }
}

if (userSources.includes('FOUNDATION_TEST_USER_PASSWORD')) throw new Error('Test identity UI must not contain a seed password literal')
if (/\binfo\.(?:password\w*|\w*hash\w*)\b/i.test(userDetail)) {
  throw new Error('User detail must not reference password or hash values')
}
if (/(?:copy|download)[\s\S]{0,80}(?:password|hash)|(?:password|hash)[\s\S]{0,80}(?:copy|download)/i.test(userDetail)) {
  throw new Error('User detail must not add password or hash copy/download behavior')
}
if (!userList.includes('v-model="form.password"') || !userList.includes('resetUserPwd')) {
  throw new Error('System user list must retain its existing password entry and reset behavior')
}

console.log('Foundation test identity UI contract ok')

function assertExactIntegration(source, file, importLine, registrationLine, binding) {
  if (!source.includes(importLine)) throw new Error(`${file} must import TestIdentityTag exactly`)
  if (!source.includes(registrationLine)) throw new Error(`${file} must register TestIdentityTag exactly`)
  if (!source.includes(binding)) throw new Error(`${file} must bind TestIdentityTag userType exactly`)
}

function collectFiles(directory) {
  if (!fs.existsSync(directory)) return []
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const entryPath = path.join(directory, entry.name)
    return entry.isDirectory() ? collectFiles(entryPath) : [entryPath]
  })
}
