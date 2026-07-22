const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')
const requireFile = file => {
  if (!fs.existsSync(path.join(root, file))) throw new Error(`missing ${file}`)
  return read(file)
}
const requireTokens = (label, source, tokens) => tokens.forEach(token => {
  if (!source.includes(token)) throw new Error(`${label} missing ${token}`)
})

const api = requireFile('src/api/todo-resources.js')
requireTokens('resource APIs', api, [
  '/todo/config/resources/events',
  '/todo/config/resources/validators',
  '/todo/config/resources/fields',
  '/todo/config/resources/materials',
  '/todo/config/resources/dod-recipes',
  'createEventResourceVersion',
  'changeEventResourceStatus'
])

const page = requireFile('src/views/todo/config/resource/index.vue')
requireTokens('resource page', page, [
  '事件目录', '校验器目录', '业务字段与材料',
  'todo:resource:add', 'EventResourceDrawer', 'ValidatorCatalogPanel', 'BusinessResourcePanel'
])

const drawer = requireFile('src/views/todo/config/resource/EventResourceDrawer.vue')
requireTokens('event drawer', drawer, [
  'PayloadSchemaDesigner', '示例 Payload', '发布启用', '创建新版本',
  'todo:resource:edit', 'payloadSchemaJson', 'samplePayloadJson'
])

const designer = requireFile('src/views/todo/config/resource/PayloadSchemaDesigner.vue')
requireTokens('schema designer', designer, [
  '字段名称', '字段类型', '是否必填', '示例值', '高级 JSON', 'properties'
])

const validators = requireFile('src/views/todo/config/resource/ValidatorCatalogPanel.vue')
requireTokens('validator panel', validators, ['effectiveStatus', 'selectable', 'referenceCount', '实现状态'])

const business = requireFile('src/views/todo/config/resource/BusinessResourcePanel.vue')
requireTokens('business panel', business, ['listFieldResources', 'listMaterialResources', 'listDodRecipeResources', '业务类型'])

const trigger = requireFile('src/views/todo/config/trigger/TriggerConditionBuilder.vue')
requireTokens('trigger condition builder', trigger, ['schemaFields', '字段类型', '前往事件目录维护', "this.$emit('open-resource-center')"])

console.log('todo configurable closure frontend contract passed')
