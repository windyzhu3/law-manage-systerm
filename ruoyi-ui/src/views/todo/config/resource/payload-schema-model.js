function text(value) {
  return String(value == null ? '' : value).trim()
}

function inferSemanticType(field) {
  const name = text(field && field.name).toLowerCase()
  const type = text(field && field.type).toLowerCase()
  if (/(?:owner|assignee|user|lawyer|reviewer|operator|handler|sales|supervisor)id$/.test(name)) return 'USER_ID'
  if (/(?:dept|department)id$/.test(name)) return 'DEPT_ID'
  if (/(?:business|lead|customer|contract|case|matter)id$/.test(name)) return 'BUSINESS_REF'
  if (type === 'string:date-time' || /(?:at|time|date)$/.test(name)) return 'DATE_TIME'
  if (/version/.test(name)) return 'SYSTEM_VERSION'
  if (/(?:count|number|no)$/.test(name) && type === 'integer') return 'SYSTEM_COUNTER'
  if (/id$/.test(name)) return 'SYSTEM_ID'
  return 'PLAIN_VALUE'
}

function applyFieldDefaults(field) {
  if (!field) return field
  if (!text(field.description) && field.descriptionAuto !== false) {
    field.description = text(field.title) || text(field.name)
  }
  if (!text(field.semanticType) && field.semanticAuto !== false) {
    field.semanticType = inferSemanticType(field)
  }
  return field
}

function parseExample(value, type) {
  const source = text(value)
  if (!source) return undefined
  if (type === 'string' || type === 'string:date-time') return source
  try { return JSON.parse(source) } catch (_) { return source }
}

function buildPayloadSchema(fields) {
  const properties = {}
  const required = []
  ;(Array.isArray(fields) ? fields : []).forEach(source => {
    const field = applyFieldDefaults(source)
    if (!text(field && field.name)) return
    const definition = { type: field.type === 'string:date-time' ? 'string' : field.type }
    if (field.type === 'string:date-time') definition.format = 'date-time'
    if (text(field.title)) definition.title = text(field.title)
    if (text(field.description)) definition.description = text(field.description)
    if (text(field.semanticType)) definition['x-semantic-type'] = text(field.semanticType)
    const example = parseExample(field.exampleText, field.type)
    if (example !== undefined) definition.example = example
    if (field.type === 'array') definition.items = { type: 'string' }
    properties[field.name] = definition
    if (field.required) required.push(field.name)
  })
  const schema = {
    $schema: 'https://json-schema.org/draft/2020-12/schema',
    type: 'object',
    properties,
    additionalProperties: true
  }
  if (required.length) schema.required = required
  return schema
}

module.exports = { applyFieldDefaults, buildPayloadSchema, inferSemanticType }
