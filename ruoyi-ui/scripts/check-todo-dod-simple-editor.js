const fs = require('fs')
const path = require('path')
const assert = require('assert')
const root = path.resolve(__dirname, '..')
const file = name => fs.readFileSync(path.join(root, name), 'utf8')

const codec = require(path.join(root, 'src/views/todo/config/dod/dod-recipe-codec.js'))
const recipe = { requiredFields: ['contactResult'], requiredAttachments: ['CALL_RECORD'], validatorRefs: ['LEAD_FIRST_CONTACT'], conditionalRules: [{ field: 'remark', when: { field: 'contactResult', equals: 'FAILED' } }] }
const applied = codec.applyRecipe(codec.emptySimpleRule(), recipe)
assert.deepStrictEqual(applied.requiredFields, ['contactResult'])
const legacy = codec.toLegacyJson(applied)
assert.strictEqual(legacy.requiredFieldsJson, '["contactResult"]')
assert.deepStrictEqual(codec.fromLegacyJson(legacy).validatorRefs, ['LEAD_FIRST_CONTACT'])
assert.ok(codec.summary(applied).includes('1 个必填字段'))

const editor = file('src/views/todo/config/dod/DodSimpleEditor.vue')
;['选择业务类型', '推荐配方', '必须填写', '必须上传', '系统自动校验', '高级设置', '规则摘要', '执行样例验证'].forEach(token => assert.ok(editor.includes(token), `simple editor missing ${token}`))
;['listFieldResources', 'listMaterialResources', 'listValidatorResources', 'listDodRecipeResources'].forEach(token => assert.ok(editor.includes(token), `simple editor missing ${token}`))

const drawer = file('src/views/todo/config/dod/DodRuleDrawer.vue')
assert.ok(drawer.includes('DodSimpleEditor'))
assert.ok(drawer.includes('createSimpleDodRule'))
assert.ok(drawer.includes('updateSimpleDodRule'))

// Empty configuration lists must keep creation drawers mounted. Otherwise a
// first-time user can click "新增规则" but no editor can appear.
const shell = file('src/views/todo/config/shared/ConfigPageShell.vue')
const dodIndex = file('src/views/todo/config/dod/index.vue')
assert.ok(shell.includes('<slot name="persistent" />'), 'config page shell must expose an always-mounted persistent slot')
assert.ok(dodIndex.includes('<template #persistent>'), 'DoD drawer must remain mounted when the rule list is empty')

const step = file('src/views/todo/config/template/steps/TemplateDodStep.vue')
assert.ok(step.includes('business-type'))
console.log('todo DoD simple editor contract passed')
