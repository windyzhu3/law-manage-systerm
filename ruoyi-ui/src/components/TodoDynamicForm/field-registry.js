export const fieldRegistry = Object.freeze({
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
})

export function resolveFieldComponent(type) {
  return fieldRegistry[type] || fieldRegistry.text
}
