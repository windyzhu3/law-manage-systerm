const MAX_CONDITION_GROUP_DEPTH = 2
const RUNTIME_OPERATORS = new Set([
  'EQ', 'NE', 'IN', 'NOT_IN', 'GT', 'GTE', 'LT', 'LTE',
  'EXISTS', 'NOT_EXISTS', 'EMPTY', 'NOT_EMPTY'
])

const OPERATOR_LABELS = {
  EQ: '等于',
  NE: '不等于',
  IN: '属于其中',
  NOT_IN: '不属于其中',
  GT: '大于',
  GTE: '大于等于',
  LT: '小于',
  LTE: '小于等于',
  EXISTS: '有值',
  NOT_EXISTS: '没有值',
  EMPTY: '为空',
  NOT_EMPTY: '不为空'
}

function clone(value) {
  return value == null ? value : JSON.parse(JSON.stringify(value))
}

function object(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : {}
}

function normalizeOperator(value) {
  const operator = String(value || '').toUpperCase()
  if (operator === 'PRESENT') return 'EXISTS'
  return RUNTIME_OPERATORS.has(operator) ? operator : null
}

function operatorsForField(field) {
  const seen = new Set()
  return (Array.isArray(field && field.operators) ? field.operators : [])
    .map(normalizeOperator)
    .filter(operator => operator && !seen.has(operator) && seen.add(operator))
    .map(value => ({ value, label: OPERATOR_LABELS[value] || value }))
}

function controlForField(field, operator) {
  if (['EXISTS', 'NOT_EXISTS', 'EMPTY', 'NOT_EMPTY'].includes(normalizeOperator(operator))) return 'NONE'
  if (Array.isArray(field && field.options) && field.options.length) return 'SELECT'
  const type = String((field && field.type) || 'string').toLowerCase()
  if (type === 'boolean') return 'BOOLEAN'
  if (['integer', 'number'].includes(type)) return 'NUMBER'
  if (['date', 'datetime', 'date-time', 'time'].includes(type)) return 'DATE'
  return 'TEXT'
}

function predicate() {
  return { field: '', operator: 'EQ', value: '' }
}

function emptyConditionDocument() {
  return {
    $expression: {
      version: 1,
      root: { type: 'AND', conditions: [] }
    }
  }
}

function conditionRoot(document) {
  const envelope = object(document && document.$expression)
  if (Number(envelope.version) !== 1 || !object(envelope.root)) return null
  return envelope.root
}

function isGroup(node) {
  return Boolean(node && ['AND', 'OR'].includes(node.type) && Array.isArray(node.conditions))
}

function conditionGroupDepth(document) {
  const root = conditionRoot(document)
  if (!root) return 0
  const depth = (node, current) => isGroup(node)
    ? Math.max(current, ...node.conditions.map(child => depth(child, current + 1)))
    : current - 1
  return isGroup(root) ? depth(root, 1) : 0
}

function nodeAtPath(root, path) {
  let cursor = root
  for (const index of path) {
    if (!isGroup(cursor) || !Number.isInteger(index) || index < 0 || index >= cursor.conditions.length) {
      const error = new Error('Condition group path is invalid')
      error.code = 'TODO_CONDITION_GROUP_PATH_INVALID'
      throw error
    }
    cursor = cursor.conditions[index]
  }
  return cursor
}

function updateAtPath(root, path, transform) {
  if (!path.length) return transform(clone(root))
  const [index, ...tail] = path
  if (!isGroup(root) || !Number.isInteger(index) || index < 0 || index >= root.conditions.length) {
    const error = new Error('Condition group path is invalid')
    error.code = 'TODO_CONDITION_GROUP_PATH_INVALID'
    throw error
  }
  const next = clone(root)
  next.conditions[index] = updateAtPath(next.conditions[index], tail, transform)
  return next
}

function addConditionGroup(document, parentPath) {
  const next = clone(document || emptyConditionDocument())
  const root = conditionRoot(next)
  if (!isGroup(root)) {
    const error = new Error('Condition root must be a group')
    error.code = 'TODO_CONDITION_GROUP_PATH_INVALID'
    throw error
  }
  const path = Array.isArray(parentPath) ? parentPath : []
  const parent = nodeAtPath(root, path)
  if (!isGroup(parent)) {
    const error = new Error('Conditions can only be added to a group')
    error.code = 'TODO_CONDITION_GROUP_PATH_INVALID'
    throw error
  }
  if (path.length + 1 >= MAX_CONDITION_GROUP_DEPTH) {
    const error = new Error('普通配置最多支持两层条件组')
    error.code = 'TODO_CONDITION_GROUP_DEPTH_LIMIT'
    throw error
  }
  next.$expression.root = updateAtPath(root, path, group => ({
    ...group,
    conditions: [...group.conditions, { type: 'AND', conditions: [predicate()] }]
  }))
  return next
}

function replaceConditionNode(document, path, value) {
  const next = clone(document || emptyConditionDocument())
  const root = conditionRoot(next)
  next.$expression.root = updateAtPath(root, path, () => clone(value))
  return next
}

function addPredicate(document, groupPath) {
  const next = clone(document || emptyConditionDocument())
  const root = conditionRoot(next)
  const path = Array.isArray(groupPath) ? groupPath : []
  next.$expression.root = updateAtPath(root, path, group => {
    if (!isGroup(group)) {
      const error = new Error('Conditions can only be added to a group')
      error.code = 'TODO_CONDITION_GROUP_PATH_INVALID'
      throw error
    }
    return { ...group, conditions: [...group.conditions, predicate()] }
  })
  return next
}

function removeConditionNode(document, path) {
  if (!Array.isArray(path) || !path.length) return emptyConditionDocument()
  const parentPath = path.slice(0, -1)
  const index = path[path.length - 1]
  const next = clone(document || emptyConditionDocument())
  const root = conditionRoot(next)
  const updated = updateAtPath(root, parentPath, group => ({
    ...group,
    conditions: group.conditions.filter((_, itemIndex) => itemIndex !== index)
  }))
  const pruneEmptyGroups = node => {
    if (!isGroup(node)) return node
    const conditions = node.conditions
      .map(pruneEmptyGroups)
      .filter(child => !(isGroup(child) && child.conditions.length === 0))
    return { ...node, conditions }
  }
  next.$expression.root = pruneEmptyGroups(updated)
  return next
}

function normalizeConditionDocument(condition) {
  if (!condition || (typeof condition === 'object' && !Array.isArray(condition) && !Object.keys(condition).length)) {
    return { document: emptyConditionDocument(), supported: true }
  }
  const source = clone(condition)
  const root = conditionRoot(source)
  if (!root) return { document: source, supported: false }
  const validNode = (node, depth, rootNode) => {
    if (isGroup(node)) {
      return depth <= MAX_CONDITION_GROUP_DEPTH &&
        (rootNode || node.conditions.length > 0) &&
        node.conditions.every(child => validNode(child, depth + 1, false))
    }
    return Boolean(node && typeof node.field === 'string' && normalizeOperator(node.operator))
  }
  if (!validNode(root, 1, true)) return { document: source, supported: false }
  return {
    document: isGroup(root)
      ? source
      : { $expression: { version: 1, root: { type: 'AND', conditions: [root] } } },
    supported: true
  }
}

function buildEventPatch(event) {
  return {
    eventType: String((event && event.eventType) || ''),
    payloadVersion: Number((event && event.payloadVersion) || 1)
  }
}

function buildTriggerPatch(document) {
  const root = conditionRoot(document)
  if (isGroup(root) && !root.conditions.length) return { condition: {} }
  return { condition: clone(document || {}) }
}

function buildOwnerPatch(config) {
  return { config: clone(object(config)) }
}

function eventSchemaHealth(event, fields) {
  const selected = event || {}
  const eventType = String(selected.eventType || '')
  const readyStatus = String(selected.schemaStatus || '').toUpperCase() === 'READY'
  const descriptors = Array.isArray(fields) ? fields : []
  const hasFields = descriptors.some(field =>
    (Array.isArray(field.sourceEvents) ? field.sourceEvents : []).includes(eventType)
  )
  return {
    ready: Boolean(eventType && Number(selected.payloadVersion) > 0 && readyStatus && hasFields),
    code: readyStatus && hasFields ? null : 'TODO_JOURNEY_EVENT_SCHEMA_REQUIRED',
    message: readyStatus && hasFields ? '事件字段完整，可继续配置' : '事件字段尚未维护完整'
  }
}

function configuredOwner(config) {
  const value = object(config)
  switch (String(value.type || '').toUpperCase()) {
    case 'BUSINESS_OWNER':
      return true
    case 'PAYLOAD':
      return Boolean(value.field || value.operand || value.value)
    case 'USER':
      return Number(value.value || value.operand) > 0
    case 'ROLE':
      return Boolean(value.roleKey || value.value || value.operand)
    case 'ROUND_ROBIN':
      return Boolean(value.source || value.strategyKey)
    default:
      return Array.isArray(value.candidates) && value.candidates.length > 0
  }
}

function ownerBlocker(config, fields) {
  const value = object(config)
  const governedFields = Array.isArray(fields) ? fields : null
  const payloadOwnerReady = String(value.type || '').toUpperCase() !== 'PAYLOAD' ||
    !governedFields ||
    governedFields.some(field => field.code === value.field && isOwnerField(field))
  if ((configuredOwner(value) && payloadOwnerReady) || configuredOwner(value.fallback)) return null
  return {
    code: 'TODO_JOURNEY_OWNER_FALLBACK_REQUIRED',
    severity: 'BLOCKER',
    message: '负责人无法解析，且没有可用的兜底负责人'
  }
}

function ownerStrategy(config) {
  const value = object(config)
  if (value.selectionMode === 'CANDIDATE_POOL') return 'CANDIDATE_POOL'
  if (value.selectionMode === 'EVENT_OWNER' || value.type === 'PAYLOAD') return 'EVENT_OWNER'
  return String(value.type || '')
}

function isOwnerField(field) {
  const value = object(field)
  const type = String(value.type || '').toLowerCase()
  const semanticType = String(value.semanticType || value.semantic_type || '').toUpperCase()
  const code = String(value.code || '')
  return ['integer', 'number'].includes(type) &&
    (semanticType === 'USER_ID' || /(owner|user|lawyer|manager)([._]?id)?$/i.test(code))
}

function scopeOwnerFields(fields, event) {
  const selected = object(event)
  const eventType = String(selected.eventType || '')
  const payloadVersion = Number(selected.payloadVersion || 0)
  if (!eventType || payloadVersion <= 0) return []
  const sourceKey = `${eventType}@${payloadVersion}`
  return (Array.isArray(fields) ? fields : []).filter(field =>
    isOwnerField(field) &&
    (Array.isArray(field.sourceEventVersions) ? field.sourceEventVersions : []).includes(sourceKey)
  )
}

function ownerSelectionStillValid(selection, fields) {
  return Boolean(selection) && (Array.isArray(fields) ? fields : [])
    .some(field => String(field.code) === String(selection))
}

function repairFocusTarget(fieldPath) {
  return String(fieldPath || '').toLowerCase().includes('payloadschema')
    ? 'schemaRepair'
    : 'eventSearch'
}

function buildOwnerConfig(strategy, selection, fallback) {
  const selected = selection || {}
  let config
  switch (strategy) {
    case 'EVENT_OWNER':
      config = { type: 'PAYLOAD', field: selected.field || '', selectionMode: 'EVENT_OWNER' }
      break
    case 'BUSINESS_OWNER':
      config = { type: 'BUSINESS_OWNER' }
      break
    case 'ROLE':
      config = { type: 'ROLE', roleKey: selected.value || '', selectionMode: 'ROLE' }
      break
    case 'USER':
      config = { type: 'USER', value: Number(selected.value) || null }
      break
    case 'CANDIDATE_POOL':
      config = { type: 'ROLE', roleKey: selected.value || '', selectionMode: 'CANDIDATE_POOL' }
      break
    default:
      config = {}
  }
  config.skipUnavailable = selected.skipUnavailable !== false
  config.useDelegation = selected.useDelegation !== false
  if (configuredOwner(fallback)) config.fallback = clone(fallback)
  return config
}

function createRepairRequest(value) {
  const source = value || {}
  return {
    type: String(source.type || '').toUpperCase(),
    eventType: source.eventType ? String(source.eventType) : null,
    payloadVersion: source.payloadVersion == null ? null : Number(source.payloadVersion),
    resourceId: source.resourceId == null ? null : Number(source.resourceId),
    businessType: source.businessType ? String(source.businessType) : null,
    returnStep: String(source.returnStep || 'EVENT').toUpperCase(),
    focusField: source.focusField ? String(source.focusField) : null
  }
}

function resourceRepairAccess(permissions, request) {
  const granted = Array.isArray(permissions) ? permissions : []
  const all = granted.includes('*:*:*')
  const editing = Boolean(request && request.resourceId)
  const required = editing ? 'todo:resource:edit' : 'todo:resource:add'
  const allowed = all || granted.includes(required)
  return {
    allowed,
    code: allowed ? null : 'TODO_RESOURCE_REPAIR_ACCESS_DENIED',
    message: allowed ? '' : '当前账号没有维护该配置资源的权限'
  }
}

function completeResourceRepair(request, draft, resourceId) {
  return {
    close: true,
    reloadResource: String(request && request.type || '').toUpperCase(),
    resourceId: resourceId == null ? null : Number(resourceId),
    returnStep: String(request && request.returnStep || 'EVENT').toUpperCase(),
    focusField: request && request.focusField ? String(request.focusField) : null,
    draft
  }
}

module.exports = {
  MAX_CONDITION_GROUP_DEPTH,
  operatorsForField,
  controlForField,
  emptyConditionDocument,
  normalizeConditionDocument,
  conditionGroupDepth,
  addConditionGroup,
  addPredicate,
  replaceConditionNode,
  removeConditionNode,
  buildEventPatch,
  buildTriggerPatch,
  buildOwnerPatch,
  eventSchemaHealth,
  ownerBlocker,
  ownerStrategy,
  isOwnerField,
  scopeOwnerFields,
  ownerSelectionStillValid,
  repairFocusTarget,
  buildOwnerConfig,
  createRepairRequest,
  resourceRepairAccess,
  completeResourceRepair
}
