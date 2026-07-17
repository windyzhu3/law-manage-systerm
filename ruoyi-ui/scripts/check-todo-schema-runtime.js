const fs = require('fs')
const path = require('path')
const assert = require('assert')

const root = path.resolve(__dirname, '..')

function read(relativePath) {
  const absolutePath = path.join(root, relativePath)
  if (!fs.existsSync(absolutePath)) throw new Error(`missing runtime file: ${relativePath}`)
  return fs.readFileSync(absolutePath, 'utf8')
}

function includes(source, marker, message) {
  if (!source.includes(marker)) throw new Error(message || `missing source contract: ${marker}`)
}

const registry = read('src/components/TodoDynamicForm/field-registry.js')
const expected = {
  text: 'el-input',
  textarea: 'el-input',
  number: 'el-input-number',
  date: 'el-date-picker',
  datetime: 'el-date-picker',
  dict: 'TodoDictField',
  user: 'TodoUserField',
  department: 'TodoDeptField',
  file: 'BusinessFilePicker',
  materialChecklist: 'TodoMaterialChecklist'
}
for (const [fieldType, componentName] of Object.entries(expected)) {
  const mapping = new RegExp(`${fieldType}\\s*:\\s*['\"]${componentName}['\"]`)
  if (!mapping.test(registry)) throw new Error(`field registry must export ${fieldType}: ${componentName}`)
}
includes(registry, 'export const fieldRegistry', 'field registry must provide the fieldRegistry export')

const runtime = read('src/components/TodoDynamicForm/schema-runtime.js')
for (const exported of ['createFormState', 'createActionPayload', 'validateFormState', 'getBusinessContext', 'getFileFieldContext']) {
  includes(runtime, `export function ${exported}`, `schema runtime must export ${exported}`)
}
const executableRuntime = runtime.replace(/export function /g, 'function ')
const runtimeExports = new Function(`${executableRuntime}; return { createFormState, createActionPayload, validateFormState, getBusinessContext, getFileFieldContext }`)()
const fixture = {
  action: 'COMPLETE',
  businessType: 'CONTRACT',
  businessId: 77,
  ui: { config: { fields: [{ key: 'proof', type: 'file', materialType: 'PROOF' }] } },
  dod: { config: { requiredFields: ['proof'], materials: [{ type: 'PROOF', minCount: 1 }] } },
  defaults: {}, materials: []
}
const state = runtimeExports.createFormState(fixture)
state.fields.proof = { fileObjectId: 41, materialType: 'PROOF', fileName: 'proof.pdf' }
state.materials = [{ fileObjectId: 41, materialType: 'PROOF', fileName: 'proof.pdf' }]
assert.deepStrictEqual(runtimeExports.createActionPayload(fixture, state).fileObjectIds, [41])
assert.deepStrictEqual(runtimeExports.validateFormState(fixture, state), [])
assert.deepStrictEqual(runtimeExports.getBusinessContext(fixture), { businessType: 'CONTRACT', businessId: 77 })
assert.deepStrictEqual(runtimeExports.getFileFieldContext(fixture, fixture.ui.config.fields[0]), {
  businessType: 'CONTRACT', businessId: 77, materialType: 'PROOF'
})

const dynamicForm = read('src/components/TodoDynamicForm/index.vue')
includes(dynamicForm, "import { fieldRegistry", 'dynamic form must consume the shared field registry')

const actionDialog = read('src/views/todo/components/TodoActionDialogs.vue')
const todoPage = read('src/views/todo/index.vue')
includes(actionDialog, "import TodoDynamicForm", 'action dialog must render TodoDynamicForm')
includes(actionDialog, 'getTodoForm', 'action dialog must load the backend form view')
if (/template_?code|templateCode/i.test(actionDialog + todoPage + runtime)) {
  throw new Error('runtime actions must not branch directly or indirectly on template code')
}
if (/business-type=["']TODO["']/.test(actionDialog + dynamicForm + read('src/views/todo/components/TodoExtensionDialog.vue'))) {
  throw new Error('runtime uploads must never use TODO/todoId as a material relation context')
}

const filePicker = read('src/components/BusinessFile/BusinessFilePicker.vue')
includes(filePicker, 'fileObjectId', 'business file picker must emit strong file object IDs')
const materials = read('src/components/BusinessFile/TodoMaterialChecklist.vue')
includes(materials, 'materialType', 'material checklist must preserve material relation types')

const extensionDialog = read('src/views/todo/components/TodoExtensionDialog.vue')
includes(extensionDialog, 'requestTodoExtension', 'normal extension dialog must call the extension request API')
if (/sla-waiver|waiveTodoSla/.test(extensionDialog)) throw new Error('normal extension dialog must never call the administrator waiver API')

const api = read('src/api/todo.js')
for (const exported of ['getTodoForm', 'requestTodoExtension']) {
  includes(api, `export function ${exported}`, `todo API must export ${exported}`)
}
includes(api, '`/todo/${id}/extension-requests`', 'normal SLA API must use /todo/{id}/extension-requests')
if (/`\/todo\/sla\/\$\{id\}\/extension-requests`/.test(api)) throw new Error('extension requests must not use the SLA administration path')

console.log('todo schema runtime contract passed')
