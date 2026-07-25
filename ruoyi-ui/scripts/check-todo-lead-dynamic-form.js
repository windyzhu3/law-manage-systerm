const fs = require('fs')
const path = require('path')
const strict = require('assert').strict

const root = path.resolve(__dirname, '..')
const runtimeSource = fs.readFileSync(path.join(root, 'src/components/TodoDynamicForm/schema-runtime.js'), 'utf8')
const dynamicFormSource = fs.readFileSync(path.join(root, 'src/components/TodoDynamicForm/index.vue'), 'utf8')
const executableRuntime = runtimeSource.replace(/export function /g, 'function ')
const runtime = new Function(`${executableRuntime}; return { normalizeFields, validateFormState }`)()

const formView = {
  action: 'COMPLETE',
  ui: {
    config: {
      fields: [
        { key: 'contactResult', type: 'dict', dictType: 'law_first_contact_result', options: [] },
        { key: 'name', type: 'text', showWhen: { field: 'contactResult', equals: 'VALID' } },
        { key: 'city', type: 'text', showWhen: { field: 'contactResult', equals: 'VALID' } }
      ]
    }
  },
  dod: {
    config: {
      conditionalRequired: [
        { field: 'name', when: { field: 'contactResult', equals: 'VALID' } },
        { field: 'city', when: { field: 'contactResult', equals: 'VALID' } }
      ]
    }
  }
}

const dictRenderer = dynamicFormSource.slice(
  dynamicFormSource.indexOf('const TodoDictField'),
  dynamicFormSource.indexOf('function numericField'))
const dictFieldWithoutOptionsRendersInput = /h\(['"]el-input['"]/.test(dictRenderer)
strict.equal(dictFieldWithoutOptionsRendersInput, false)

const missingDictionaryErrors = runtime.validateFormState(formView, {
  fields: { contactResult: 'VALID', name: 'Alice', city: 'Shanghai' },
  materials: []
})
const missingDictionaryShowsBlockingError = missingDictionaryErrors.some(error => /dictionary|字典/i.test(error))
strict.equal(missingDictionaryShowsBlockingError, true)

const invalidBranch = runtime.normalizeFields(formView, {
  fields: { contactResult: 'SUSPECT_INVALID' },
  materials: []
})
const validBranch = runtime.normalizeFields(formView, {
  fields: { contactResult: 'VALID' },
  materials: []
})
const conditionalValidFieldsAreVisibleOnlyForVALID =
  invalidBranch.filter(field => ['name', 'city'].includes(field.key)).every(field => field.visible === false) &&
  validBranch.filter(field => ['name', 'city'].includes(field.key)).every(field => field.visible === true)
strict.equal(conditionalValidFieldsAreVisibleOnlyForVALID, true)

const hiddenUserRequiredView = {
  action: 'COMPLETE',
  ui: { config: { fields: [{ key: 'note', type: 'text', required: true, showWhen: { field: 'branch', equals: 'A' } }] } },
  dod: { config: {} }
}
strict.deepEqual(runtime.validateFormState(hiddenUserRequiredView, {
  fields: { branch: 'B' },
  materials: []
}), [])

const hiddenDodRequiredView = {
  ...hiddenUserRequiredView,
  dod: { config: { requiredFields: ['note'] } }
}
strict.equal(runtime.validateFormState(hiddenDodRequiredView, {
  fields: { branch: 'B' },
  materials: []
}).length, 1)

console.log('todo lead dynamic form contract passed')
