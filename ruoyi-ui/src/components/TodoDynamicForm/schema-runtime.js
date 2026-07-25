function clone(value) {
  if (value === undefined) return undefined
  return JSON.parse(JSON.stringify(value))
}

function config(value) {
  if (!value || typeof value !== 'object') return {}
  return value.config && typeof value.config === 'object' ? value.config : value
}

export function getBusinessContext(formView) {
  const businessType = formView && formView.businessType
  const businessId = Number(formView && formView.businessId)
  if (!businessType || !Number.isFinite(businessId) || businessId <= 0) throw new Error('Todo form is missing its governed business context')
  return { businessType, businessId }
}

export function getFileFieldContext(formView, field) {
  return { ...getBusinessContext(formView), materialType: field.materialType || field.key }
}

function effectiveRules(formView) {
  const dod = config(formView && formView.dod)
  const action = String((formView && formView.action) || '').toUpperCase()
  const rules = action === 'COMPLETE' ? { ...dod } : {}
  const actions = dod.actions && typeof dod.actions === 'object' ? dod.actions : {}
  if (actions[action] && typeof actions[action] === 'object') Object.assign(rules, actions[action])
  return rules
}

function materialRules(formView) {
  const dod = effectiveRules(formView)
  const rules = Array.isArray(dod.materials) ? dod.materials : (Array.isArray(dod.requiredAttachments) ? dod.requiredAttachments : [])
  return rules.map((value, index) => typeof value === 'string'
    ? { materialType: value, label: value, minCount: 1 }
    : {
        ...value,
        materialType: value.materialType || value.type || value.attachmentType || `MATERIAL_${index + 1}`,
        label: value.label || value.name || value.materialType || value.type || `材料 ${index + 1}`,
        minCount: Number(value.minCount || value.count || 1)
      })
}

export function normalizeFields(formView, state) {
  const ui = config(formView && formView.ui)
  const rulesForAction = effectiveRules(formView)
  const required = new Set(rulesForAction.requiredFields || [])
  const values = (state && state.fields) || (formView && formView.defaults) || {}
  const conditionalRequired = new Set((rulesForAction.conditionalRequired || [])
    .filter(rule => conditionMatches(rule.when, values))
    .map(rule => rule.field))
  const raw = Array.isArray(ui.fields) ? ui.fields : []
  const fields = raw.map((value, index) => {
    const field = typeof value === 'string' ? { key: value } : { ...value }
    const key = field.key || field.name || field.field || `field_${index + 1}`
    return {
      ...field,
      key,
      type: field.type || field.component || 'text',
      label: field.label || field.title || key,
      required: required.has(key) || conditionalRequired.has(key) ||
        (field.required === true && (!field.showWhen || conditionMatches(field.showWhen, values))),
      requiredByDod: required.has(key) || conditionalRequired.has(key),
      visible: !field.showWhen || conditionMatches(field.showWhen, values)
    }
  })
  const rules = materialRules(formView)
  if (rules.length && !fields.some(field => field.type === 'materialChecklist')) {
    fields.push({ key: 'materials', type: 'materialChecklist', label: '材料清单', requirements: rules, required: false })
  }
  return fields
}

export function createFormState(formView) {
  const defaults = clone((formView && formView.defaults) || {}) || {}
  const materials = ((formView && formView.materials) || []).map(value => ({
    fileObjectId: Number(value.fileObjectId),
    materialType: value.materialType,
    fileName: value.fileName || ''
  })).filter(value => Number.isFinite(value.fileObjectId) && value.fileObjectId > 0)
  return { fields: defaults, materials }
}

function fileId(value) {
  const candidate = value && typeof value === 'object' ? value.fileObjectId : value
  const id = Number(candidate)
  return Number.isFinite(id) && id > 0 ? id : null
}

function normalizeValue(value, ids) {
  if (Array.isArray(value)) return value.map(item => normalizeValue(item, ids)).filter(item => item !== undefined)
  if (value && typeof value === 'object' && Object.prototype.hasOwnProperty.call(value, 'fileObjectId')) {
    const id = fileId(value)
    if (!id) return undefined
    ids.push(id)
    if (value.materialType) return { fileObjectId: id, materialType: value.materialType }
    return id
  }
  if (value && typeof value === 'object') {
    return Object.keys(value).reduce((result, key) => {
      const normalized = normalizeValue(value[key], ids)
      if (normalized !== undefined) result[key] = normalized
      return result
    }, {})
  }
  return value
}

export function createActionPayload(formView, state) {
  const fileObjectIds = []
  const fields = normalizeValue(clone((state && state.fields) || {}), fileObjectIds)
  const materialField = normalizeFields(formView, state).find(field => field.type === 'materialChecklist')
  const materials = normalizeValue(clone((state && state.materials) || []), fileObjectIds)
  if (materialField) fields[materialField.key] = materials
  return { fields, fileObjectIds: [...new Set(fileObjectIds)] }
}

function empty(value) {
  return value === undefined || value === null || value === '' || (Array.isArray(value) && value.length === 0)
}

function conditionMatches(when, fields) {
  if (!when || !when.field) return false
  if (Object.prototype.hasOwnProperty.call(when, 'equals')) return fields[when.field] === when.equals
  if (Object.prototype.hasOwnProperty.call(when, 'present')) return when.present === Object.prototype.hasOwnProperty.call(fields, when.field)
  return false
}

export function validateFormState(formView, state) {
  const errors = []
  const normalizedFields = normalizeFields(formView, state)
  for (const field of normalizedFields) {
    if (field.visible && field.type === 'dict' && (!Array.isArray(field.options) || field.options.length === 0)) {
      errors.push(`Dictionary options are unavailable: ${field.label}`)
    }
    const value = field.type === 'materialChecklist' ? state.materials : state.fields[field.key]
    if (field.required && empty(value)) errors.push(`${field.label}不能为空`)
  }
  const rules = effectiveRules(formView)
  for (const conditional of rules.conditionalRequired || []) {
    if (conditionMatches(conditional.when, state.fields) && empty(state.fields[conditional.field])) {
      const field = normalizedFields.find(value => value.key === conditional.field)
      errors.push(`${field ? field.label : conditional.field}不能为空`)
    }
  }
  const materials = (state && state.materials) || []
  for (const rule of materialRules(formView)) {
    const count = materials.filter(value => value.materialType === rule.materialType && fileId(value)).length
    if (count < rule.minCount) errors.push(`${rule.label}至少需要 ${rule.minCount} 份`)
    const maximum = Number(rule.maxCount)
    if (Number.isFinite(maximum) && count > maximum) errors.push(`${rule.label}最多允许 ${maximum} 份`)
  }
  return errors
}

export function getMaterialRequirements(formView) {
  return materialRules(formView)
}
