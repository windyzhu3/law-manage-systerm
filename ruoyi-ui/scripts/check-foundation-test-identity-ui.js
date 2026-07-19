const fs = require('fs')
const path = require('path')

const uiRoot = path.resolve(__dirname, '..')
const read = relativePath => fs.readFileSync(path.join(uiRoot, relativePath), 'utf8')
const tag = read('src/components/TestIdentityTag/index.vue')
const userList = read('src/views/system/user/index.vue')
const userDetail = read('src/views/system/user/view.vue')
const sources = [tag, userList, userDetail].join('\n')

if ((tag.match(/<el-tag\b/g) || []).length !== 1) {
  throw new Error('TestIdentityTag must contain exactly one Element UI tag')
}
if (!/<el-tag\s+v-if="userType === '99'"\s+type="warning"\s+size="mini">测试身份<\/el-tag>/.test(tag)) {
  throw new Error('TestIdentityTag must render exactly one warning mini tag for string userType 99')
}
if (!userList.includes('TestIdentityTag') || !userDetail.includes('TestIdentityTag')) {
  throw new Error('System user list and detail must both reuse TestIdentityTag')
}
if (sources.includes('FOUNDATION_TEST_USER_PASSWORD')) {
  throw new Error('Test identity UI must not contain a seed password literal')
}
if (/\b(?:copyPassword|copy-password|passwordCopy|password-copy)\b/i.test(sources)) {
  throw new Error('Test identity UI must not add a password copy control')
}
if (/\{\{\s*(?:info\.|scope\.row\.)?password\s*\}\}|\{\{\s*(?:info\.|scope\.row\.)?(?:passwordHash|hash)\s*\}\}/.test(sources)) {
  throw new Error('Test identity UI must not display a password or hash value')
}

console.log('Foundation test identity UI contract ok')
