const fs = require('fs')
const path = require('path')
const assert = require('assert')
const root = path.resolve(__dirname, '..')
const read = file => fs.readFileSync(path.join(root, file), 'utf8')

const picker = read('src/views/todo/config/simulation/BusinessObjectPicker.vue')
;['listBusinessObjects', 'remote-method', 'sampleFallback', 'emptyReason', '示例对象', 'selected-card'].forEach(token => assert.ok(picker.includes(token), `object picker missing ${token}`))

const payload = read('src/views/todo/config/simulation/SchemaPayloadForm.vue')
;['schema.properties', 'requiredFields', 'el-input-number', 'el-switch', '示例值', 'validate'].forEach(token => assert.ok(payload.includes(token), `payload form missing ${token}`))

const drawer = read('src/views/todo/config/simulation/SimulationDrawer.vue')
;['BusinessObjectPicker', 'SchemaPayloadForm', 'selectedBusinessObject', 'samplePayloadJson', 'expectedDefinitionHash'].forEach(token => assert.ok(drawer.includes(token), `simulation drawer missing ${token}`))
assert.ok(!drawer.includes('payloadRows'), 'simulation must not require key/type/value rows in the common path')

const result = read('src/views/todo/config/simulation/SimulationResult.vue')
;['事件匹配', '模板预检', '负责人解析', 'SLA 计算', '完成条件', '下一步路由', '待办卡片预览', '技术诊断'].forEach(token => assert.ok(result.includes(token), `result missing ${token}`))
assert.ok(result.includes('orderedSteps'))

console.log('todo simulation closure contract passed')
