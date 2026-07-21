function parseArray(value) {
  if (Array.isArray(value)) return value
  if (typeof value !== 'string' || !value.trim()) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch (_) {
    return []
  }
}

function field(row, camel, snake) {
  return row && (row[camel] !== undefined ? row[camel] : row[snake])
}

function referencedFields(rule) {
  const result = new Set(parseArray(field(rule, 'requiredFieldsJson', 'required_fields_json'))
    .filter(value => typeof value === 'string' && value.trim()).map(value => value.trim()))
  parseArray(field(rule, 'conditionalRulesJson', 'conditional_rules_json')).forEach(condition => {
    if (!condition || typeof condition !== 'object') return
    if (typeof condition.field === 'string' && condition.field.trim()) result.add(condition.field.trim())
    if (condition.when && typeof condition.when.field === 'string' && condition.when.field.trim()) result.add(condition.when.field.trim())
  })
  return Array.from(result)
}

function missingUiFields(catalog, selectedIds, uiFields, systemDerivedFields) {
  const selected = new Set((selectedIds || []).map(Number))
  const available = new Set([...(uiFields || []), ...(systemDerivedFields || [])]
    .filter(value => typeof value === 'string' && value.trim()).map(value => value.trim()))
  const required = new Set()
  ;(catalog || []).filter(rule => selected.has(Number(field(rule, 'id', 'id'))))
    .forEach(rule => referencedFields(rule).forEach(value => required.add(value)))
  return Array.from(required).filter(value => !available.has(value)).sort()
}

module.exports = { missingUiFields, referencedFields }
