const MAX_CONDITION_GROUP_DEPTH = 2
const { effectKind, routeTargetsFor } = require('./business-effect-model')
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
const NO_VALUE_OPERATORS = new Set(['EXISTS', 'NOT_EXISTS', 'EMPTY', 'NOT_EMPTY'])
const SCENARIO_LABELS = {
  TD001_VALID: '有效线索',
  TD001_SUSPECT_INVALID: '疑似无效',
  TD001_UNREACHABLE: '无法联系'
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
  if (NO_VALUE_OPERATORS.has(normalizeOperator(operator))) return 'NONE'
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

function conditionNodes(document) {
  const normalized = normalizeConditionDocument(document)
  if (!normalized.supported) return null
  const root = conditionRoot(normalized.document)
  const visit = node => isGroup(node)
    ? node.conditions.flatMap(visit)
    : [node]
  return root ? visit(root) : []
}

function missingConditionValue(value, operator) {
  if (NO_VALUE_OPERATORS.has(operator)) return false
  if (value === null || value === undefined) return true
  if (typeof value === 'string') return value.trim() === ''
  return ['IN', 'NOT_IN'].includes(operator) && (!Array.isArray(value) || value.length === 0)
}

function conditionValueMatches(field, value) {
  const type = String((field && field.type) || '').toLowerCase()
  if (!type) return true
  if (type === 'integer') return typeof value === 'number' && Number.isInteger(value)
  if (type === 'number') return typeof value === 'number' && Number.isFinite(value)
  if (type === 'boolean') return typeof value === 'boolean'
  if (type === 'array') return Array.isArray(value)
  if (type === 'object') return Boolean(value && typeof value === 'object' && !Array.isArray(value))
  return typeof value === 'string'
}

function conditionDraftBlocker(document, fields) {
  const nodes = conditionNodes(document)
  if (nodes === null) {
    return {
      code: 'TODO_CONDITION_INVALID',
      severity: 'BLOCKER',
      stepCode: 'TRIGGER',
      fieldPath: 'event.condition',
      message: '触发条件结构无效，请重新建立'
    }
  }
  const descriptors = Array.isArray(fields) ? fields : []
  for (const node of nodes) {
    const operator = normalizeOperator(node && node.operator)
    const field = descriptors.find(item => item.code === (node && node.field))
    const fieldName = field ? (field.name || field.label || field.code) : (node && node.field) || '未选择字段'
    const fieldPath = `event.condition.${(node && node.field) || ''}`.replace(/\.$/, '')
    if (!field) {
      return {
        code: 'TODO_CONDITION_FIELD_UNKNOWN',
        severity: 'BLOCKER',
        stepCode: 'TRIGGER',
        fieldPath,
        message: `当前事件中不存在“${fieldName}”字段`
      }
    }
    if (NO_VALUE_OPERATORS.has(operator)) {
      if (node.value !== null && node.value !== undefined) {
        return {
          code: 'TODO_CONDITION_VALUE_NOT_ALLOWED',
          severity: 'BLOCKER',
          stepCode: 'TRIGGER',
          fieldPath,
          message: `“${fieldName}”当前判断方式不需要比较值`
        }
      }
      continue
    }
    if (missingConditionValue(node.value, operator)) {
      return {
        code: 'TODO_CONDITION_VALUE_REQUIRED',
        severity: 'BLOCKER',
        stepCode: 'TRIGGER',
        fieldPath,
        message: `请为“${fieldName}”选择或填写比较值`
      }
    }
    const values = ['IN', 'NOT_IN'].includes(operator) ? node.value : [node.value]
    if (!values.every(value => conditionValueMatches(field, value))) {
      return {
        code: 'TODO_CONDITION_VALUE_TYPE_INVALID',
        severity: 'BLOCKER',
        stepCode: 'TRIGGER',
        fieldPath,
        message: `条件值与“${fieldName}”字段类型不匹配`
      }
    }
  }
  return null
}

function conditionDisplayValue(field, value) {
  const options = Array.isArray(field && field.options) ? field.options : []
  const label = raw => {
    const option = options.find(item =>
      String(item && typeof item === 'object' ? item.value : item) === String(raw)
    )
    if (option && typeof option === 'object') return String(option.label !== undefined ? option.label : raw)
    return option !== undefined ? String(option) : String(raw)
  }
  return Array.isArray(value) ? value.map(label).join('、') : label(value)
}

function conditionSummary(document, fields) {
  const normalized = normalizeConditionDocument(document)
  if (!normalized.supported) return '当前规则包含高级条件，请在高级模式查看。'
  const root = conditionRoot(normalized.document)
  if (!root || !root.conditions.length) return '当前规则：业务事件到达后直接创建待办。'
  const descriptors = Array.isArray(fields) ? fields : []
  const describe = node => {
    if (isGroup(node)) {
      return `（${node.conditions.map(describe).join(node.type === 'AND' ? ' 且 ' : ' 或 ')}）`
    }
    const field = descriptors.find(item => item.code === node.field)
    const operator = normalizeOperator(node.operator)
    const fieldName = field ? (field.name || field.label || field.code) : '未选择字段'
    const operatorName = OPERATOR_LABELS[operator] || '未选择判断'
    if (NO_VALUE_OPERATORS.has(operator)) return `${fieldName} ${operatorName}`
    const value = missingConditionValue(node.value, operator)
      ? '（待选择比较值）'
      : conditionDisplayValue(field, node.value)
    return `${fieldName} ${operatorName}${value.startsWith('（') ? '' : ' '}${value}`
  }
  return `当前规则：${root.conditions.map(describe).join(root.type === 'AND' ? '，并且 ' : '，或者 ')}。`
}

function localizedJourneyIssue(issue, context) {
  const source = object(issue)
  const code = String(source.code || '')
  const fieldPath = String(source.fieldPath || source.path || '')
  const fieldCode = fieldPath.startsWith('event.condition.')
    ? fieldPath.slice('event.condition.'.length)
    : ''
  const fields = Array.isArray(context && context.fields) ? context.fields : []
  const field = fields.find(item => item.code === fieldCode)
  const fieldName = (field && (field.name || field.label || field.code)) || fieldCode || '所选业务字段'
  const stepCode = source.stepCode || (
    code.startsWith('TODO_CONDITION_') || fieldPath.startsWith('event.condition') ? 'TRIGGER'
      : code.startsWith('TODO_OWNER_') || fieldPath.startsWith('owner') ? 'OWNER'
        : code.startsWith('TODO_ROUTING_') || fieldPath.startsWith('routing') ? 'ROUTING'
          : code.includes('SIMULATION') || fieldPath.startsWith('simulation') ? 'SIMULATION_PUBLISH'
            : fieldPath.startsWith('event') ? 'EVENT' : 'SIMULATION_PUBLISH'
  )
  let message = source.message || code
  if (code === 'TODO_CONDITION_VALUE_REQUIRED') message = `请为“${fieldName}”选择或填写比较值`
  if (code === 'TODO_CONDITION_VALUE_TYPE_INVALID') message = `条件值与“${fieldName}”字段类型不匹配`
  if (code === 'TODO_CONDITION_VALUE_NOT_ALLOWED') message = `“${fieldName}”当前判断方式不需要比较值`
  if (code === 'TODO_OWNER_FIELD_NOT_ELIGIBLE') message = `“${fieldName}”不是当前事件声明的负责人字段`
  if (code === 'TODO_ROUTING_OUTCOME_INCOMPLETE') message = '还有业务结果尚未设置后续待办'
  if (code === 'TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE') {
    const codes = Array.isArray(source.missingScenarioCodes)
      ? source.missingScenarioCodes
      : String(source.message || '').match(/TD001_[A-Z_]+/g) || []
    const names = [...new Set(codes)].map(item => SCENARIO_LABELS[item] || item)
    message = names.length ? `还有必测场景未通过：${names.join('、')}` : '还有必测场景未通过'
  }
  return {
    ...source,
    fieldPath,
    stepCode,
    message
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
    governedFields.some(field => String(field.code) === ownerFieldSelection(value) && isOwnerField(field))
  if ((configuredOwner(value) && payloadOwnerReady) || configuredOwner(value.fallback)) return null
  return {
    code: 'TODO_JOURNEY_OWNER_FALLBACK_REQUIRED',
    severity: 'BLOCKER',
    message: '负责人无法解析，且没有可用的兜底负责人'
  }
}

function ownerFieldSelection(config) {
  const value = object(config)
  return String(value.field || value.operand || value.value || '')
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
  if (Object.prototype.hasOwnProperty.call(value, 'ownerEligible')) {
    return ['integer', 'number'].includes(type) && value.ownerEligible === true
  }
  return ['integer', 'number'].includes(type) &&
    (semanticType === 'USER_ID' || /(owner|user|lawyer|manager)([._]?id)?$/i.test(code))
}

function scopeOwnerFields(fields, event) {
  const selected = object(event)
  const eventType = String(selected.eventType || '')
  const payloadVersion = Number(selected.payloadVersion || 0)
  if (!eventType || payloadVersion <= 0) return []
  const sourceKey = `${eventType}@${payloadVersion}`
  const exactVersionFields = (Array.isArray(fields) ? fields : []).filter(field =>
    list(object(field).sourceEventVersions).includes(sourceKey)
  )
  return scopeEventFields(exactVersionFields, event, 'OWNER')
}

function filterEventsByPolicy(events, policy) {
  const rows = Array.isArray(events) ? events : []
  const value = object(policy)
  if (value.locked !== true) return rows.slice()
  const allowed = new Set(list(value.allowedEventTypes).map(item => String(item)))
  const payloadVersion = Number(value.payloadVersion || 0)
  return rows.filter(event =>
    allowed.has(String(object(event).eventType || '')) &&
    (!payloadVersion || Number(object(event).payloadVersion || 0) === payloadVersion)
  )
}

function scopeEventFields(fields, event, purpose) {
  const selected = object(event)
  const eventType = String(selected.eventType || '')
  const payloadVersion = Number(selected.payloadVersion || 0)
  if (!eventType || payloadVersion <= 0) return []
  const sourceKey = `${eventType}@${payloadVersion}`
  const targetPurpose = String(purpose || 'OVERVIEW').toUpperCase()
  return (Array.isArray(fields) ? fields : []).reduce((scoped, field) => {
    const value = object(field)
    const exactSources = list(value.sourceEventVersions)
    const matches = exactSources.length
      ? exactSources.includes(sourceKey)
      : list(value.sourceEvents).includes(eventType)
    if (!matches) return scoped
    const configurable = Object.prototype.hasOwnProperty.call(value, 'businessConfigurable')
      ? value.businessConfigurable === true
      : true
    if (!configurable) return scoped

    const purposeScopes = {
      OWNER: 'ownerSourceEventVersions',
      CONDITION: 'conditionSourceEventVersions',
      DEFAULT_VALUE: 'defaultValueSourceEventVersions'
    }
    const purposeFlags = {
      OWNER: 'ownerEligible',
      CONDITION: 'conditionEligible',
      DEFAULT_VALUE: 'defaultValueEligible'
    }
    const exactEligibility = purposeName => {
      const sourceProperty = purposeScopes[purposeName]
      if (Object.prototype.hasOwnProperty.call(value, sourceProperty)) {
        return list(value[sourceProperty]).includes(sourceKey)
      }
      if (purposeName === 'OWNER') return isOwnerField(value)
      return value[purposeFlags[purposeName]] === true
    }
    const projected = {
      ...value,
      ownerEligible: exactEligibility('OWNER'),
      conditionEligible: exactEligibility('CONDITION'),
      defaultValueEligible: exactEligibility('DEFAULT_VALUE')
    }
    const hasExactPurposeScopes = Object.values(purposeScopes)
      .some(property => Object.prototype.hasOwnProperty.call(value, property))
    const eligibleForOverview = projected.ownerEligible ||
      projected.conditionEligible || projected.defaultValueEligible
    const include = targetPurpose === 'OVERVIEW'
      ? (!hasExactPurposeScopes || eligibleForOverview)
      : projected[purposeFlags[targetPurpose]] === true
    if (include) scoped.push(projected)
    return scoped
  }, [])
}

function eventOverviewFields(fields, event) {
  const selected = object(event)
  const eventType = String(selected.eventType || '')
  const payloadVersion = Number(selected.payloadVersion || 0)
  const governed = scopeEventFields(fields, selected, 'OVERVIEW')
  if (!eventType || payloadVersion <= 0) return governed

  let schema = selected.payloadSchemaJson
  try {
    schema = typeof schema === 'string' ? JSON.parse(schema) : object(schema)
  } catch (_) {
    return governed
  }

  const technicalTypes = new Set([
    'SYSTEM_ID', 'SYSTEM_VERSION', 'SYSTEM_CODE', 'SYSTEM_COUNTER', 'BUSINESS_ID'
  ])
  const sourceKey = `${eventType}@${payloadVersion}`
  const knownCodes = new Set(governed.map(field => String(object(field).code || '')))
  const schemaFields = Object.entries(object(object(schema).properties)).reduce((result, [code, rawField]) => {
    if (knownCodes.has(code)) return result
    const definition = object(rawField)
    const semanticType = String(definition['x-semantic-type'] || '').toUpperCase()
    if (technicalTypes.has(semanticType)) return result
    result.push({
      code,
      fieldKey: `${sourceKey}:${code}`,
      name: definition.title || code,
      description: definition.description || '',
      type: definition.format === 'date-time' ? 'date-time' : definition.type,
      semanticType,
      businessConfigurable: false,
      ownerEligible: false,
      conditionEligible: false,
      defaultValueEligible: false,
      sourceEvents: [eventType],
      sourceEventVersions: [sourceKey]
    })
    return result
  }, [])
  return governed.concat(schemaFields)
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

const JOURNEY_STEP_NUMBERS = Object.freeze({
  EVENT: 1,
  TRIGGER: 2,
  OWNER: 3,
  DOD: 4,
  SLA: 5,
  ROUTING: 6,
  SIMULATION_PUBLISH: 7
})

const RESOURCE_LOCATIONS = Object.freeze({
  EVENT: { stepCode: 'EVENT' },
  TRIGGER: { stepCode: 'TRIGGER' },
  OWNER: { stepCode: 'OWNER' },
  DOD: { stepCode: 'DOD' },
  SLA: { stepCode: 'SLA' },
  ROUTING: { stepCode: 'ROUTING' },
  SIMULATION: { stepCode: 'SIMULATION_PUBLISH' },
  FIELD: { resourceType: 'FIELD', openResourceDrawer: true },
  MATERIAL: { stepCode: 'DOD', resourceType: 'MATERIAL', openResourceDrawer: true },
  DOD_RECIPE: { stepCode: 'DOD', resourceType: 'DOD_RECIPE', openResourceDrawer: true },
  VALIDATOR: { stepCode: 'DOD' },
  CALENDAR: { stepCode: 'SLA', resourceType: 'CALENDAR' },
  ROUTING_TARGET: { stepCode: 'ROUTING' }
})

function stepFromFieldPath(fieldPath) {
  if (fieldPath.startsWith('event.condition')) return 'TRIGGER'
  if (fieldPath.startsWith('event')) return 'EVENT'
  if (fieldPath.startsWith('owner')) return 'OWNER'
  if (fieldPath.startsWith('dod')) return 'DOD'
  if (fieldPath.startsWith('sla')) return 'SLA'
  if (fieldPath.startsWith('routing') || fieldPath.startsWith('taskCompletions')) return 'ROUTING'
  if (fieldPath.startsWith('simulation')) return 'SIMULATION_PUBLISH'
  return ''
}

function resourceFocusTarget(resourceKey, fieldPath, stepCode) {
  const key = String(resourceKey || '').toUpperCase()
  const path = String(fieldPath || '').toLowerCase()
  if (key === 'DOD_RECIPE') return 'recipeCard'
  if (key === 'MATERIAL') return 'requiredAttachments'
  if (key === 'VALIDATOR') return 'validatorRefs'
  if (key === 'CALENDAR') return 'calendarCode'
  if (key === 'ROUTING_TARGET') return 'targetVersionId'
  if (stepCode === 'OWNER') {
    if (path.includes('fallback')) {
      return /(value|rolekey|selection|operand)$/.test(path)
        ? 'ownerFallbackSelection'
        : 'ownerFallbackType'
    }
    if (/(field|value|rolekey|selection|operand)$/.test(path)) return 'ownerSelection'
    return 'ownerSource'
  }
  if (stepCode === 'DOD') {
    if (path.includes('recipe')) return 'recipeCard'
    if (path.includes('material') || path.includes('attachment')) return 'requiredAttachments'
    if (path.includes('validator')) return 'validatorRefs'
    if (path.includes('conditional') || path.includes('condition')) return 'conditionalRules'
    if (path.includes('instruction')) return 'employeeInstructions'
    if (path.includes('requiredfield')) return 'requiredFields'
    return 'recipeCard'
  }
  if (stepCode === 'ROUTING') {
    if (path.includes('nodes') || path.includes('edges') || path.endsWith('routing.config')) return 'routingGraph'
    if (path.includes('target')) return 'targetVersionId'
    if (path.includes('effect') || path.includes('resulttype')) return 'effectKind'
    return 'businessOutcomes'
  }
  if (stepCode === 'TRIGGER') return 'conditionBuilder'
  if (stepCode === 'SLA') {
    if (path.includes('calendar')) return 'calendarCode'
    if (path.includes('schedule')) return 'schedulePanel'
    return 'durationValue'
  }
  if (stepCode === 'EVENT') return repairFocusTarget(fieldPath)
  if (stepCode === 'SIMULATION_PUBLISH') {
    if (fieldPath === 'simulation.scenarios') return 'scenario-selector'
    if (fieldPath === 'simulation.full') return 'full-simulation'
  }
  return fieldPath.split('.').filter(Boolean).pop() || ''
}

function fixLocation(issue) {
  const source = object(issue)
  const fieldPath = String(source.fieldPath || source.path || '')
  const resourceKey = String(source.resourceKey || '').toUpperCase()
  const resourceLocation = RESOURCE_LOCATIONS[resourceKey] || {}
  let stepCode = resourceLocation.stepCode ||
    (resourceKey === 'FIELD' ? stepFromFieldPath(fieldPath) : '') ||
    String(source.stepKey || source.stepCode || '').toUpperCase()
  if (stepCode === 'SIMULATION') stepCode = 'SIMULATION_PUBLISH'
  if (!JOURNEY_STEP_NUMBERS[stepCode]) {
    stepCode = stepFromFieldPath(fieldPath) || 'SIMULATION_PUBLISH'
  }
  const location = {
    step: JOURNEY_STEP_NUMBERS[stepCode],
    stepCode,
    resourceKey,
    fieldPath,
    focusTarget: resourceFocusTarget(resourceKey, fieldPath, stepCode)
  }
  if (resourceLocation.resourceType) location.resourceType = resourceLocation.resourceType
  if (resourceLocation.openResourceDrawer) location.openResourceDrawer = true
  return location
}

function slaSchedulePresentation(value, template) {
  const config = object(value)
  const schedule = object(config.schedule)
  const windows = list(schedule.windows)
  const templateCode = String(object(template).templateCode || config.templateCode || '').toUpperCase()
  const identifiers = [
    config.effectKind,
    config.schedulePurpose,
    config.scheduleType,
    schedule.effectKind,
    schedule.kind,
    schedule.type,
    schedule.purpose
  ].map(item => String(item || '').toUpperCase())
  const explicitRetry = identifiers.some(item => item.includes('RETRY')) || templateCode === 'TD-003'
  const explicitSelfCycle = identifiers.some(item => item === 'SCHEDULE_SELF' || item.includes('SELF') || item.includes('CYCLE')) ||
    templateCode === 'TD-004'
  const governedDays = Number(config.cycleDays || schedule.cycleDays) ||
    (Number(config.minutes) > 0 ? Number(config.minutes) / 1440 : 0)
  const selfCycle = explicitSelfCycle || (!explicitRetry && governedDays === 5)
  return {
    mode: selfCycle ? 'SELF_CYCLE' : (windows.length ? 'RETRY_WINDOWS' : 'DURATION'),
    hasWindows: windows.length > 0,
    cycleDays: selfCycle ? (governedDays > 0 ? governedDays : 5) : 0
  }
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

function createOwnerStrategyDrafts(config) {
  const source = object(config)
  const strategy = ownerStrategy(source)
  const drafts = {
    EVENT_OWNER: { field: '' },
    BUSINESS_OWNER: {},
    ROLE: { value: '' },
    USER: { value: '' },
    CANDIDATE_POOL: { value: '' },
    options: {
      skipUnavailable: source.skipUnavailable !== false,
      useDelegation: source.useDelegation !== false
    },
    fallback: clone(object(source.fallback))
  }
  if (strategy === 'EVENT_OWNER') drafts.EVENT_OWNER.field = ownerFieldSelection(source)
  if (strategy === 'ROLE' || strategy === 'CANDIDATE_POOL') {
    drafts[strategy].value = source.roleKey || source.value || source.operand || ''
  }
  if (strategy === 'USER') drafts.USER.value = source.value || source.operand || ''
  return drafts
}

function updateOwnerStrategyDraft(drafts, strategy, value) {
  const next = clone(object(drafts))
  const code = String(strategy || '').toUpperCase()
  if (!['EVENT_OWNER', 'BUSINESS_OWNER', 'ROLE', 'USER', 'CANDIDATE_POOL'].includes(code)) return next
  next[code] = { ...object(next[code]), ...clone(object(value)) }
  return next
}

function applyOwnerStrategyDraft(drafts, strategy, fallback) {
  const source = object(drafts)
  const code = String(strategy || '').toUpperCase()
  const selection = {
    ...object(source[code]),
    ...object(source.options)
  }
  const resolvedFallback = fallback === undefined ? object(source.fallback) : object(fallback)
  return buildOwnerConfig(code, selection, resolvedFallback)
}

function list(value) {
  return Array.isArray(value) ? value.filter(item => item !== null && item !== undefined) : []
}

function contextMatches(values, selected) {
  const candidates = list(values).map(String)
  return candidates.length === 0 || candidates.includes(String(selected || ''))
}

function rankDodRecipes(recipes, context) {
  const selected = object(context)
  const businessType = String(selected.businessType || '')
  const businessAction = String(selected.businessAction || '')
  const templateStage = String(selected.templateStage || '')
  const scored = list(recipes)
    .filter(recipe => {
      const type = String((recipe && recipe.businessType) || '')
      return (!type || type === 'ALL' || type === businessType) &&
        contextMatches(recipe && recipe.businessActions, businessAction) &&
        contextMatches(recipe && recipe.templateStages, templateStage)
    })
    .map((recipe, index) => ({
      recipe,
      index,
      action: list(recipe.businessActions).length ? 1 : 0,
      stage: list(recipe.templateStages).length ? 1 : 0,
      business: String(recipe.businessType || '') === businessType ? 1 : 0,
      priority: Number(recipe.recommendationPriority) || 0
    }))
  scored.sort((left, right) =>
    right.action - left.action ||
    right.stage - left.stage ||
    right.business - left.business ||
    right.priority - left.priority ||
    String(left.recipe.name || '').localeCompare(String(right.recipe.name || ''), 'zh-CN') ||
    String(left.recipe.code || '').localeCompare(String(right.recipe.code || ''), 'zh-CN') ||
    left.index - right.index
  )
  return scored.map(item => clone(item.recipe))
}

function canonicalDodMaterials(config) {
  const source = object(config)
  const values = Object.prototype.hasOwnProperty.call(source, 'materials')
    ? list(source.materials)
    : list(source.requiredAttachments)
  return values.map(value => {
    if (typeof value === 'string') return { type: value, minCount: 1 }
    const material = clone(object(value))
    const type = String(material.type || material.code || '')
    return type ? {
      ...material,
      type,
      minCount: Object.prototype.hasOwnProperty.call(material, 'minCount') ? material.minCount : 1
    } : null
  }).filter(Boolean)
}

function canonicalDodConditions(config) {
  const source = object(config)
  const rules = clone(Object.prototype.hasOwnProperty.call(source, 'conditionalRequired')
    ? list(source.conditionalRequired)
    : list(source.conditionalRules))
  return rules.flatMap(rule => {
    const canonical = clone(object(rule))
    const groupedFields = list(canonical.requiredFields).map(String).filter(Boolean)
    delete canonical.requiredFields
    const when = clone(object(canonical.when))
    if (!Object.prototype.hasOwnProperty.call(when, 'equals') &&
        String(when.operator || '').toUpperCase() === 'EQ' &&
        Object.prototype.hasOwnProperty.call(when, 'value')) {
      when.equals = clone(when.value)
      delete when.operator
      delete when.value
    }
    canonical.when = when
    if (canonical.field) return [canonical]
    if (!groupedFields.length) return [canonical]
    return groupedFields.map(field => ({ ...clone(canonical), field, when: clone(when) }))
  })
}

const DOD_CONDITION_IDENTITY = '__dodConditionIdentity'
const DOD_HYDRATED_IDENTITY_PREFIX = 'hydrated:'
const DOD_NEW_IDENTITY_PREFIX = 'new:'
let nextDodConditionSequence = 0

function stableConditionValue(value) {
  if (Array.isArray(value)) return value.map(stableConditionValue)
  if (!value || typeof value !== 'object') return value
  return Object.keys(value).sort().reduce((result, key) => {
    result[key] = stableConditionValue(value[key])
    return result
  }, {})
}

function dodConditionIdentity(rule) {
  const source = object(rule)
  for (const key of ['id', 'key', 'code']) {
    if (source[key] != null && String(source[key]) !== '') {
      return `${key}:${String(source[key])}`
    }
  }
  // Rules without a governed identifier use their original field + condition
  // composite. DodStep carries this token while the condition itself is edited.
  return `composite:${String(source.field || '')}:${JSON.stringify(stableConditionValue(source.when || {}))}`
}

function hydrateDodConditions(config) {
  return canonicalDodConditions(config).map(rule => ({
    ...clone(rule),
    field: rule.field || '',
    when: { field: '', equals: '', ...clone(rule.when || {}) },
    [DOD_CONDITION_IDENTITY]: `${DOD_HYDRATED_IDENTITY_PREFIX}${dodConditionIdentity(rule)}`
  }))
}

function createDodCondition(fieldCode) {
  nextDodConditionSequence += 1
  const field = String(fieldCode || '')
  return {
    field,
    when: { field, equals: '' },
    [DOD_CONDITION_IDENTITY]: `${DOD_NEW_IDENTITY_PREFIX}${Date.now().toString(36)}:${nextDodConditionSequence.toString(36)}`
  }
}

function canonicalConditionalRule(rule) {
  const next = clone(object(rule))
  delete next[DOD_CONDITION_IDENTITY]
  return next
}

function mergeDodConditions(existingConfig, incomingRules) {
  const existing = canonicalDodConditions(existingConfig)
  const existingByIdentity = new Map(existing.map(rule => [dodConditionIdentity(rule), rule]))
  return list(incomingRules).map(rule => {
    const source = object(rule)
    const identity = String(source[DOD_CONDITION_IDENTITY] || '')
    const previous = identity.startsWith(DOD_HYDRATED_IDENTITY_PREFIX)
      ? existingByIdentity.get(identity.slice(DOD_HYDRATED_IDENTITY_PREFIX.length))
      : null
    const incoming = canonicalConditionalRule(source)
    if (!previous) return incoming
    return {
      ...clone(previous),
      ...incoming,
      when: {
        ...clone(object(previous.when)),
        ...clone(object(incoming.when))
      }
    }
  })
}

function normalizeDodConfig(config) {
  const source = object(config)
  const next = clone(source)
  const hasMaterials = Object.prototype.hasOwnProperty.call(source, 'materials') ||
    Object.prototype.hasOwnProperty.call(source, 'requiredAttachments')
  const hasConditions = Object.prototype.hasOwnProperty.call(source, 'conditionalRequired') ||
    Object.prototype.hasOwnProperty.call(source, 'conditionalRules')
  delete next.requiredAttachments
  delete next.conditionalRules
  if (hasMaterials) next.materials = canonicalDodMaterials(source)
  if (hasConditions) next.conditionalRequired = canonicalDodConditions(source)
  return next
}

function materializeDodRecipe(recipe, current) {
  const source = object(recipe)
  const existing = normalizeDodConfig(current && current.config)
  return {
    config: {
      ...clone(existing),
      recipeCode: String(source.code || ''),
      requiredFields: clone(list(source.requiredFields)),
      materials: canonicalDodMaterials(source),
      conditionalRequired: canonicalDodConditions(source),
      validatorRefs: clone(list(source.validatorRefs)),
      employeeInstructions: clone(list(source.employeeInstructions))
    }
  }
}

function updateGovernedDod(current, changes) {
  const existing = normalizeDodConfig(current && current.config)
  const governed = object(changes)
  const next = { ...clone(existing) }
  if (Object.prototype.hasOwnProperty.call(governed, 'requiredFields')) {
    next.requiredFields = clone(list(governed.requiredFields))
  }
  if (Object.prototype.hasOwnProperty.call(governed, 'materials')) {
    next.materials = canonicalDodMaterials(governed)
  } else if (Object.prototype.hasOwnProperty.call(governed, 'requiredAttachments')) {
    const existingByType = new Map(canonicalDodMaterials(existing).map(material => [String(material.type), material]))
    next.materials = list(governed.requiredAttachments).map(value => {
      const item = object(value)
      const type = String(typeof value === 'string' ? value : item.type || item.code || '')
      return clone(existingByType.get(type) || { type, minCount: 1 })
    }).filter(material => material.type)
  }
  if (Object.prototype.hasOwnProperty.call(governed, 'conditionalRequired')) {
    next.conditionalRequired = mergeDodConditions(existing, governed.conditionalRequired)
  } else if (Object.prototype.hasOwnProperty.call(governed, 'conditionalRules')) {
    next.conditionalRequired = mergeDodConditions(existing, governed.conditionalRules)
  }
  return { config: next }
}

function resourceLabel(catalog, code, fallback) {
  const item = list(catalog).find(row => String(row.code) === String(code))
  return item
    ? String(item.name || item.label || item.description || code)
    : String(fallback || code)
}

function projectEmployeePreview(serverPreview, dodConfig, resources) {
  const base = object(serverPreview)
  const dod = object(dodConfig)
  const catalogs = object(resources)
  const conditionalByField = new Map()
  canonicalDodConditions(dod).forEach(rule => {
    const value = object(rule)
    if (value.field) conditionalByField.set(String(value.field), value)
  })
  const fieldCodes = [...list(dod.requiredFields)]
  conditionalByField.forEach((rule, code) => {
    if (!fieldCodes.includes(code)) fieldCodes.push(code)
  })
  return {
    ...clone(base),
    fields: fieldCodes.map(code => {
      const resource = list(catalogs.fields).find(row => String(row.code) === String(code)) || {}
      const conditional = conditionalByField.get(String(code))
      return {
        code,
        label: resourceLabel(catalogs.fields, code, '必填信息'),
        type: resource.type || 'string',
        required: list(dod.requiredFields).includes(code),
        conditional: Boolean(conditional),
        condition: conditional ? clone(conditional.when) : null
      }
    }),
    materials: canonicalDodMaterials(dod).map(material => ({
      ...clone(material),
      code: material.type,
      label: String(material.label || resourceLabel(catalogs.materials, material.type, '必传材料')),
      required: true
    })),
    completionInstructions: clone(list(dod.employeeInstructions))
  }
}

function durationMinutes(value, unit) {
  const amount = Number(value)
  if (!(amount > 0)) return null
  switch (String(unit || '').toUpperCase()) {
    case 'MINUTE': return Math.round(amount)
    case 'HOUR': return Math.round(amount * 60)
    case 'DAY': return null
    default: return null
  }
}

function buildSlaPatch(form, current) {
  const source = object(form)
  const existing = object(current && current.config)
  const calculated = durationMinutes(source.durationValue, source.durationUnit)
  const minutes = calculated || (String(source.durationUnit).toUpperCase() === 'DAY' &&
    Number(source.governedMinutes) > 0 ? Number(source.governedMinutes) : null)
  return {
    config: {
      ...clone(existing),
      durationValue: Number(source.durationValue) || null,
      durationUnit: String(source.durationUnit || ''),
      minutes,
      calendarCode: String(source.calendarCode || ''),
      startStrategy: 'TODO_CREATED',
      pauseRules: [],
      actions: {
        reminder80: ['REMIND_OWNER'],
        overdue100: ['MARK_OVERDUE', 'REMIND_OWNER'],
        escalation150: ['ESCALATE', 'REMIND_OWNER', 'NOTIFY_MANAGER']
      },
      softRemindPercent: 80,
      hardRemindPercent: 100,
      escalatePercent: 150
    }
  }
}

function pointTime(calculation, camel, aliases) {
  const source = object(calculation)
  if (source[camel]) return source[camel]
  for (const key of aliases) if (source[key]) return source[key]
  return null
}

function buildSlaTimeline(config, calculation) {
  const rule = object(config)
  const result = object(calculation)
  const actions = object(rule.actions)
  const points = [
    {
      percent: 0,
      semantic: 'CREATED',
      title: '创建待办',
      at: pointTime(result, 'startAt', ['createdAt']),
      actions: ['START_TIMER']
    },
    {
      percent: 80,
      semantic: 'REMINDER',
      title: '80% 提醒',
      at: pointTime(result, 'remind80At', ['remind80DueAt', 'softReminderAt']),
      actions: clone(list(actions.reminder80))
    },
    {
      percent: 100,
      semantic: 'OVERDUE',
      title: '100% 超时',
      at: pointTime(result, 'overdue100At', ['dueAt', 'overdueAt']),
      actions: clone(list(actions.overdue100))
    },
    {
      percent: 150,
      semantic: 'ESCALATION',
      title: '150% 升级',
      at: pointTime(result, 'escalate150At', ['escalateAt', 'escalationAt']),
      actions: clone(list(actions.escalation150))
    }
  ]
  return points.every(point => point.at) ? points : []
}

function slaRepairBlocker(config, calendars, timeline) {
  const rule = object(config)
  const calendarReady = list(calendars).some(calendar =>
    String(calendar.calendarCode || calendar.calendar_code || '') === String(rule.calendarCode || '')
  )
  if (!calendarReady) {
    return {
      code: 'TODO_JOURNEY_SLA_CALENDAR_REQUIRED',
      severity: 'BLOCKER',
      message: '所选工作日历不存在或已停用，请先修复工作日历'
    }
  }
  if (list(timeline).length !== 4) {
    return {
      code: 'TODO_JOURNEY_SLA_CALCULATION_REQUIRED',
      severity: 'BLOCKER',
      message: '当前时限无法计算，请检查时长、起算时间和工作日历'
    }
  }
  return null
}

function routeKey(value, index) {
  const normalized = String(value || `result_${index + 1}`)
    .replace(/[^A-Za-z0-9_-]/g, '_')
    .replace(/^_+|_+$/g, '')
  return normalized || `result_${index + 1}`
}

function normalizedOutcome(row, index) {
  const value = object(row)
  const kind = effectKind(value)
  const legacyResult = (value.resultValue === null || value.resultValue === undefined) &&
    value.value !== null && value.value !== undefined
  const resultValue = legacyResult ? value.value : value.resultValue
  return {
    ...clone(value),
    id: routeKey(value.id, index),
    label: String(value.label || `业务结果 ${index + 1}`),
    resultField: value.resultField || value.field ? String(value.resultField || value.field) : null,
    resultValue: resultValue === null || resultValue === undefined ? null : String(resultValue),
    resultLabel: value.resultLabel
      ? String(value.resultLabel)
      : (legacyResult && value.label ? String(value.label) : null),
    effectKind: kind,
    resultType: kind === 'NEXT_TEMPLATE' ? 'NEXT' : 'END',
    targetVersionId: Number(value.targetVersionId) || null,
    default: value.default === true
  }
}

function literalCondition(field, value) {
  return {
    $expression: {
      version: 1,
      root: {
        type: 'AND',
        conditions: [{ field, operator: 'EQ', value }]
      }
    }
  }
}

function materializeOutcomeRouting(outcomeSet, targets, currentVersionId, current) {
  const catalog = object(outcomeSet)
  const routingTargets = list(targets)
  const resultField = String(catalog.resultField || '')
  const resultFieldName = String(catalog.resultFieldName || resultField)
  const options = list(catalog.options)
  const outcomes = options.map((option, index) => {
    const value = String(option.value || '')
    const kind = effectKind(option)
    const configuredTargetCode = String(option.targetTemplateCode || '')
    const target = configuredTargetCode
      ? routingTargets.find(item =>
        String(item.templateCode || item.template_code || '') === configuredTargetCode)
      : routingTargets.find(item =>
        Number(item.versionId || item.version_id) === Number(option.targetVersionId))
    const targetVersionId = kind === 'SCHEDULE_SELF'
      ? Number(currentVersionId) || null
      : (target
          ? Number(target.versionId || target.version_id)
          : Number(option.targetVersionId) || null)
    const targetTemplateCode = configuredTargetCode ||
      String(target && (target.templateCode || target.template_code) || '') || null
    const resultLabel = String(option.label || value)
    return {
      id: routeKey(`${resultField}_${value}`, index),
      label: `当${resultFieldName}为${resultLabel}时`,
      resultField,
      resultValue: value,
      resultLabel,
      effectKind: kind,
      resultType: kind === 'NEXT_TEMPLATE' ? 'NEXT' : 'END',
      targetVersionId: kind === 'NEXT_TEMPLATE' || targetTemplateCode ? targetVersionId : null,
      targetTemplateCode,
      default: index === options.length - 1,
      condition: literalCondition(resultField, value)
    }
  })
  return buildBusinessRoutingPatch(outcomes, {
    mode: 'SEQUENTIAL',
    joinMode: 'ALL',
    currentVersionId,
    outcomeSet: catalog
  }, current)
}

function buildLeadFirstContactRoute(outcomes, currentVersionId) {
  const byResult = result => outcomes.find(row => row.resultValue === result)
  const valid = byResult('VALID')
  const suspect = byResult('SUSPECT_INVALID')
  if (!valid || !suspect || !valid.targetVersionId || !suspect.targetVersionId) return null

  return {
    start: 'td001',
    nodes: [
      { key: 'td001', type: 'TASK', templateCode: 'TD-001', templateVersionId: Number(currentVersionId) || null },
      { key: 'firstResult', type: 'DECISION' },
      { key: 'td002', type: 'TASK', templateCode: 'TD-002', templateVersionId: suspect.targetVersionId },
      { key: 'reviewResult', type: 'DECISION' },
      { key: 'td004', type: 'TASK', templateCode: 'TD-004', templateVersionId: valid.targetVersionId },
      { key: 'reopenedTd001', type: 'TASK', templateCode: 'TD-001', templateVersionId: Number(currentVersionId) || null },
      { key: 'end', type: 'END' }
    ],
    edges: [
      { key: 'td001-result', from: 'td001', to: 'firstResult', priority: 0 },
      { key: 'first-valid', from: 'firstResult', to: 'td004', priority: 30, condition: literalCondition('contactResult', 'VALID') },
      { key: 'first-suspect', from: 'firstResult', to: 'td002', priority: 20, condition: literalCondition('contactResult', 'SUSPECT_INVALID') },
      { key: 'first-unreachable', from: 'firstResult', to: 'end', priority: 10, condition: literalCondition('contactResult', 'UNREACHABLE') },
      { key: 'first-default', from: 'firstResult', to: 'end', priority: -1, default: true },
      { key: 'td002-result', from: 'td002', to: 'reviewResult', priority: 0 },
      { key: 'review-invalid', from: 'reviewResult', to: 'end', priority: 20, condition: literalCondition('reviewResult', 'TRUE_INVALID') },
      { key: 'review-reopen', from: 'reviewResult', to: 'reopenedTd001', priority: 10, condition: literalCondition('reviewResult', 'MISJUDGED_VALID') },
      { key: 'review-default', from: 'reviewResult', to: 'end', priority: -1, default: true },
      { key: 'reopened-end', from: 'reopenedTd001', to: 'end', priority: 0 },
      { key: 'td004-end', from: 'td004', to: 'end', priority: 0 }
    ]
  }
}

function governedCurrentTaskKey(current, currentVersionId) {
  const config = object(current && current.config)
  const start = String(config.start || '')
  const node = list(config.nodes).find(item =>
    String(item && item.key) === start &&
    String(item && item.type) === 'TASK' &&
    Number(item && item.templateVersionId) === Number(currentVersionId)
  )
  return node ? start : 'current_task'
}

function buildSequentialRoute(outcomes, currentVersionId, currentTaskKey = 'current_task') {
  const nodes = [{ key: currentTaskKey, type: 'TASK', templateVersionId: Number(currentVersionId) || null }]
  const edges = []
  const endKey = 'route_end'
  if (!outcomes.length) {
    nodes.push({ key: endKey, type: 'END' })
    edges.push({ key: 'edge_current_end', from: currentTaskKey, to: endKey })
    return { start: currentTaskKey, nodes, edges }
  }
  if (outcomes.length === 1 && !outcomes[0].condition) {
    const row = outcomes[0]
    if (row.resultType === 'END') {
      nodes.push({ key: endKey, type: 'END' })
      edges.push({ key: 'edge_current_end', from: currentTaskKey, to: endKey })
    } else {
      const taskKey = `task_${row.id}`
      nodes.push({ key: taskKey, type: 'TASK', templateVersionId: row.targetVersionId })
      nodes.push({ key: endKey, type: 'END' })
      edges.push({ key: `edge_current_${row.id}`, from: currentTaskKey, to: taskKey })
      edges.push({ key: `edge_${row.id}_end`, from: taskKey, to: endKey })
    }
    return { start: currentTaskKey, nodes, edges }
  }
  nodes.push({ key: 'business_result', type: 'DECISION' }, { key: endKey, type: 'END' })
  edges.push({ key: 'edge_current_result', from: currentTaskKey, to: 'business_result' })
  const hasDefault = outcomes.some(row => row.default)
  outcomes.forEach((row, index) => {
    const target = row.resultType === 'END' ? endKey : `task_${row.id}`
    if (row.resultType !== 'END') {
      nodes.push({ key: target, type: 'TASK', templateVersionId: row.targetVersionId })
      edges.push({ key: `edge_${row.id}_end`, from: target, to: endKey })
    }
    const edge = {
      key: `edge_result_${row.id}`,
      from: 'business_result',
      to: target,
      default: row.default || (!hasDefault && index === outcomes.length - 1),
      priority: outcomes.length - index
    }
    if (!edge.default && row.condition) edge.condition = clone(row.condition)
    edges.push(edge)
  })
  return { start: currentTaskKey, nodes, edges }
}

function buildParallelRoute(outcomes, currentVersionId, joinMode, currentTaskKey = 'current_task') {
  const rows = outcomes.filter(row => row.resultType !== 'END')
  const branches = rows.map(row => row.id)
  const nodes = [
    { key: currentTaskKey, type: 'TASK', templateVersionId: Number(currentVersionId) || null },
    { key: 'parallel_start', type: 'FORK' },
    { key: 'parallel_join', type: 'JOIN', joinMode, branches },
    { key: 'route_end', type: 'END' }
  ]
  const edges = [
    { key: 'edge_current_parallel', from: currentTaskKey, to: 'parallel_start' },
    { key: 'edge_join_end', from: 'parallel_join', to: 'route_end' }
  ]
  rows.forEach((row, index) => {
    const key = `task_${row.id}`
    nodes.push({ key, type: 'TASK', templateVersionId: row.targetVersionId })
    const outbound = {
      key: `edge_parallel_${row.id}`,
      from: 'parallel_start',
      to: key,
      branchKey: row.id,
      priority: index
    }
    edges.push(outbound, {
      key: `edge_${row.id}_join`,
      from: key,
      to: 'parallel_join'
    })
  })
  return { start: currentTaskKey, nodes, edges }
}

function buildBusinessRoutingPatch(rows, options, current) {
  const settings = object(options)
  const outcomes = list(rows).map(normalizedOutcome)
  const mode = String(settings.mode || 'SEQUENTIAL').toUpperCase()
  const joinMode = String(settings.joinMode || 'ALL').toUpperCase() === 'ANY' ? 'ANY' : 'ALL'
  const governedFirstContact = String(object(settings.outcomeSet).recommendationCode || '') === 'TD001_STANDARD_ROUTE'
    ? buildLeadFirstContactRoute(outcomes, settings.currentVersionId)
    : null
  const currentTaskKey = governedCurrentTaskKey(current, settings.currentVersionId)
  const graph = governedFirstContact || (mode === 'PARALLEL'
    ? buildParallelRoute(outcomes, settings.currentVersionId, joinMode, currentTaskKey)
    : buildSequentialRoute(outcomes, settings.currentVersionId, currentTaskKey))
  return {
    config: {
      ...clone(object(current && current.config)),
      ...graph,
      businessRouting: { mode, joinMode },
      businessOutcomes: clone(outcomes)
    }
  }
}

function routingDraftBlocker(rows, options) {
  const outcomes = list(rows).map(normalizedOutcome)
  const settings = object(options)
  const mode = String(settings.mode || 'SEQUENTIAL').toUpperCase()
  const outcomeSet = object(settings.outcomeSet)
  const governedOptions = list(outcomeSet.options)
  const governedOption = row => governedOptions.find(option =>
    String(option.value || '') === String(row.resultValue || ''))
  const requiresTarget = row => row.effectKind === 'NEXT_TEMPLATE' ||
    Boolean(governedOption(row) && governedOption(row).targetTemplateCode)
  if (outcomes.some(row => requiresTarget(row) && !row.targetVersionId)) {
    return {
      code: 'TODO_JOURNEY_ROUTING_TARGET_REQUIRED',
      severity: 'BLOCKER',
      message: '继续办理的业务结果必须选择下一张待办'
    }
  }
  if (mode === 'PARALLEL' && outcomes.filter(row => row.resultType === 'NEXT').length < 2) {
    return {
      code: 'TODO_JOURNEY_ROUTING_PARALLEL_BRANCHES_REQUIRED',
      severity: 'BLOCKER',
      message: '并行办理至少需要两个有效的后续待办'
    }
  }
  if (mode === 'SEQUENTIAL' && outcomeSet.resultField && list(outcomeSet.options).length) {
    const expected = list(outcomeSet.options).map(option => String(option.value))
    const actual = outcomes.map(row => String(row.resultValue || ''))
    if (actual.some(value => !value)) {
      return {
        code: 'TODO_ROUTING_OUTCOME_INCOMPLETE',
        severity: 'BLOCKER',
        message: '请选择每个分支对应的业务结果'
      }
    }
    if (new Set(actual).size !== actual.length) {
      return {
        code: 'TODO_ROUTING_OUTCOME_DUPLICATE',
        severity: 'BLOCKER',
        message: '同一个业务结果只能配置一个后续分支'
      }
    }
    const missing = expected.filter(value => !actual.includes(value))
    if (missing.length) {
      return {
        code: 'TODO_ROUTING_OUTCOME_INCOMPLETE',
        severity: 'BLOCKER',
        message: '还有业务结果尚未设置后续待办'
      }
    }
    const targets = routeTargetsFor(settings.businessType, list(settings.routingTargets))
    const businessType = String(settings.businessType || '')
    const invalidTarget = outcomes.find(row => {
      const option = governedOption(row)
      const expectedCode = String(option && option.targetTemplateCode || '')
      if (!expectedCode && row.effectKind !== 'NEXT_TEMPLATE') return false
      if (row.effectKind === 'SCHEDULE_SELF') {
        return !Number(settings.currentVersionId) ||
          String(row.targetTemplateCode || '') !== expectedCode ||
          Number(row.targetVersionId) !== Number(settings.currentVersionId)
      }
      const target = expectedCode
        ? targets.find(item => String(item.templateCode || item.template_code || '') === expectedCode)
        : targets.find(item => Number(item.versionId || item.version_id) === Number(row.targetVersionId))
      return !target || String(target.status || '').toUpperCase() !== 'PUBLISHED' ||
        (businessType && String(target.businessType || target.business_type || '') !== businessType) ||
        (expectedCode && String(row.targetTemplateCode || '') !== expectedCode) ||
        Number(target.versionId || target.version_id) !== Number(row.targetVersionId)
    })
    if (invalidTarget) {
      return {
        code: 'TODO_JOURNEY_ROUTING_TARGET_INVALID',
        severity: 'BLOCKER',
        message: '后续待办不存在、未发布或业务类型不一致'
      }
    }
  }
  return null
}

function createRepairRequest(value) {
  const source = value || {}
  const item = source.item && typeof source.item === 'object' && !Array.isArray(source.item)
    ? clone(source.item)
    : null
  const rawResourceId = source.resourceId == null
    ? (item && (item.resourceItemId || item.resource_item_id))
    : source.resourceId
  const resourceId = Number(rawResourceId)
  const request = {
    type: String(source.type || '').toUpperCase(),
    eventType: source.eventType ? String(source.eventType) : null,
    payloadVersion: source.payloadVersion == null ? null : Number(source.payloadVersion),
    resourceId: Number.isFinite(resourceId) && resourceId > 0 ? resourceId : null,
    businessType: source.businessType ? String(source.businessType) : null,
    returnStep: String(source.returnStep || 'EVENT').toUpperCase(),
    focusField: source.focusField ? String(source.focusField) : null
  }
  const itemKey = source.itemKey || source.resourceItemKey || source.resourceCode ||
    source.referenceKey || source.referencedResourceKey || source.resourceRef || null
  if (item) request.item = item
  if (itemKey) request.itemKey = String(itemKey)
  return request
}

function resolveRepairResourceItem(request, resources) {
  const source = request || {}
  const type = String(source.type || '').toUpperCase()
  const catalogKey = ({ FIELD: 'fields', MATERIAL: 'materials', DOD_RECIPE: 'recipes' })[type]
  if (!catalogKey) return null
  const catalogs = resources || {}
  const catalog = Array.isArray(catalogs[catalogKey]) ? catalogs[catalogKey] : []
  const direct = source.item && typeof source.item === 'object' && !Array.isArray(source.item)
    ? source.item
    : null
  const idValue = source.resourceId || (direct && (direct.resourceItemId || direct.resource_item_id))
  const id = Number(idValue)
  const key = String(source.itemKey || source.resourceItemKey || source.resourceCode ||
    source.referenceKey || source.referencedResourceKey || source.resourceRef ||
    (direct && (direct.code || direct.resourceCode || direct.resource_code)) || '').trim()
  const match = catalog.find(item => {
    const itemId = Number(item && (item.resourceItemId || item.resource_item_id))
    const itemKey = String(item && (item.code || item.resourceCode || item.resource_code) || '')
    return (Number.isFinite(id) && id > 0 && itemId === id) || (key && itemKey === key)
  })
  if (match) return clone(match)
  return null
}

function resourceRepairAccess(permissions, request) {
  const granted = Array.isArray(permissions) ? permissions : []
  const all = granted.includes('*:*:*')
  const type = String(request && request.type || '').toUpperCase()
  if (type === 'CALENDAR') {
    const allowed = all || granted.includes('todo:calendar:manage')
    return {
      allowed,
      code: allowed ? null : 'TODO_RESOURCE_REPAIR_ACCESS_DENIED',
      message: allowed ? '' : '当前账号没有维护工作日历的权限'
    }
  }
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

const SIMULATION_TRACE_ORDER = ['EVENT', 'OWNER', 'DOD', 'SLA', 'ROUTING', 'TODO_PREVIEW']
const SIMULATION_REPAIR_STEPS = {
  EVENT: 'EVENT',
  OWNER: 'OWNER',
  DOD: 'DOD',
  SLA: 'SLA',
  ROUTING: 'ROUTING',
  TODO_PREVIEW: 'DOD'
}

function buildHydratedPayloadRows(hydration, manualOverrides) {
  const source = object(hydration)
  const overrides = object(manualOverrides)
  return list(source.fields).map(row => {
    const field = object(row)
    const path = String(field.path || field.code || '')
    const overridden = Object.prototype.hasOwnProperty.call(overrides, path)
    const sensitive = field.sensitive === true
    return {
      ...clone(field),
      path,
      source: overridden ? 'MANUAL_OVERRIDE' : String(field.source || 'MISSING'),
      displayValue: sensitive ? '••••••' : (overridden ? overrides[path] : field.value),
      editable: !sensitive
    }
  })
}

function updateManualOverrides(current, path, value) {
  const next = clone(object(current))
  const key = String(path || '')
  if (!key) return next
  if (value === '' || value == null) delete next[key]
  else next[key] = clone(value)
  return next
}

function hasHydrationOverride(manualOverrides, path) {
  const overrides = object(manualOverrides)
  const key = String(path || '')
  return Object.prototype.hasOwnProperty.call(overrides, key) &&
    overrides[key] !== '' && overrides[key] != null
}

function applyHydrationOverrides(fields, manualOverrides) {
  const overrides = object(manualOverrides)
  return list(fields).map(row => {
    const field = object(row)
    const path = String(field.path || field.code || '')
    if (!hasHydrationOverride(overrides, path)) return clone(field)
    const value = clone(overrides[path])
    return {
      ...clone(field),
      rawValue: value,
      displayValue: value,
      source: 'MANUAL_OVERRIDE',
      missing: false,
      issueCode: null,
      issueMessage: null
    }
  })
}

function remainingHydrationBlockers(hydration, manualOverrides) {
  const source = object(hydration)
  return list(source.blockingIssues).filter(issue =>
    !hasHydrationOverride(manualOverrides, object(issue).fieldPath)
  ).map(clone)
}

function creationCoverage(hydration, manualOverrides) {
  const source = object(hydration)
  const required = list(source.eventInput).filter(field => object(field).required === true)
  if (!required.length) return Number(source.creationCoveragePercent || source.coveragePercent || source.coverage || 0)
  const ready = required.filter(field => {
    const row = object(field)
    const path = String(row.path || row.code || '')
    return !row.missing || hasHydrationOverride(manualOverrides, path)
  }).length
  return Math.round((ready * 100) / required.length)
}

function orderedSimulationTrace(trace) {
  const rows = list(trace)
  const byCode = new Map(rows.map(row => [String(row && row.code || '').toUpperCase(), clone(row)]))
  return SIMULATION_TRACE_ORDER
    .filter(code => byCode.has(code))
    .map(code => ({
      ...byCode.get(code),
      code,
      repairStep: SIMULATION_REPAIR_STEPS[code]
    }))
}

function publishPreflightGate(simulation, preflight, warningReason) {
  const result = object(simulation)
  const report = object(preflight)
  const errors = list(report.errors)
  const warnings = list(report.warnings)
  const hashMatches = Boolean(result.successful) &&
    Boolean(result.definitionHash) &&
    String(result.definitionHash) === String(report.definitionHash || '')
  const reasonReady = !warnings.length || String(warningReason || '').trim().length > 0
  let message = ''
  if (!hashMatches) message = '请使用当前草稿重新试运行'
  else if (errors.length) message = '发布预检仍有阻塞项'
  else if (!reasonReady) message = '存在警告，请填写复核说明'
  return {
    allowed: hashMatches && !errors.length && reasonReady,
    hashMatches,
    blockerCount: errors.length,
    warningCount: warnings.length,
    message
  }
}

function simulationPublishCapabilities(permissions) {
  const granted = Array.isArray(permissions) ? permissions : []
  const all = granted.includes('*:*:*')
  const canListObjects = all || granted.includes('todo:simulation:list')
  const canRunEngine = all || granted.includes('todo:simulation:simulate')
  return {
    canSimulate: canListObjects && canRunEngine,
    canPublish: (all || granted.includes('todo:release:publish')) && canListObjects && canRunEngine,
    canDiff: all || granted.includes('todo:definition:diff')
  }
}

function leadReleaseVersions(currentVersionId, definition) {
  const versions = {}
  const current = Number(currentVersionId)
  if (current > 0) versions['TD-001'] = current

  const routing = object(object(object(definition).routing).config)
  const downstreamCodes = new Set(['TD-002', 'TD-003', 'TD-004'])
  ;(Array.isArray(routing.businessOutcomes) ? routing.businessOutcomes : []).forEach(outcome => {
    const code = String((outcome && outcome.targetTemplateCode) || '')
    const versionId = Number(outcome && outcome.targetVersionId)
    if (downstreamCodes.has(code) && versionId > 0) versions[code] = versionId
  })
  Object.entries(object(routing.releaseDependencies)).forEach(([code, rawVersionId]) => {
    const versionId = Number(rawVersionId)
    if (downstreamCodes.has(code) && versionId > 0) versions[code] = versionId
  })
  return versions
}

function canActivateLeadRelease(permissions) {
  const granted = Array.isArray(permissions) ? permissions : []
  return granted.includes('*:*:*') || granted.includes('todo:definition:publish')
}

module.exports = {
  MAX_CONDITION_GROUP_DEPTH,
  operatorsForField,
  controlForField,
  conditionDraftBlocker,
  conditionSummary,
  localizedJourneyIssue,
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
  ownerFieldSelection,
  ownerStrategy,
  isOwnerField,
  scopeOwnerFields,
  filterEventsByPolicy,
  scopeEventFields,
  eventOverviewFields,
  ownerSelectionStillValid,
  repairFocusTarget,
  fixLocation,
  buildOwnerConfig,
  createOwnerStrategyDrafts,
  updateOwnerStrategyDraft,
  applyOwnerStrategyDraft,
  rankDodRecipes,
  normalizeDodConfig,
  hydrateDodConditions,
  createDodCondition,
  materializeDodRecipe,
  updateGovernedDod,
  projectEmployeePreview,
  buildSlaPatch,
  buildSlaTimeline,
  slaRepairBlocker,
  slaSchedulePresentation,
  buildBusinessRoutingPatch,
  materializeOutcomeRouting,
  routingDraftBlocker,
  createRepairRequest,
  resolveRepairResourceItem,
  resourceRepairAccess,
  completeResourceRepair,
  buildHydratedPayloadRows,
  updateManualOverrides,
  applyHydrationOverrides,
  remainingHydrationBlockers,
  creationCoverage,
  orderedSimulationTrace,
  publishPreflightGate,
  simulationPublishCapabilities,
  leadReleaseVersions,
  canActivateLeadRelease
}
