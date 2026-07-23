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
  return clone(Object.prototype.hasOwnProperty.call(source, 'conditionalRequired')
    ? list(source.conditionalRequired)
    : list(source.conditionalRules))
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
  return {
    ...clone(value),
    id: routeKey(value.id, index),
    label: String(value.label || `业务结果 ${index + 1}`),
    resultType: String(value.resultType || 'NEXT').toUpperCase(),
    targetVersionId: Number(value.targetVersionId) || null,
    default: value.default === true
  }
}

function buildSequentialRoute(outcomes, currentVersionId) {
  const nodes = [{ key: 'current_task', type: 'TASK', templateVersionId: Number(currentVersionId) || null }]
  const edges = []
  const endKey = 'route_end'
  if (!outcomes.length) {
    nodes.push({ key: endKey, type: 'END' })
    edges.push({ key: 'edge_current_end', from: 'current_task', to: endKey })
    return { start: 'current_task', nodes, edges }
  }
  if (outcomes.length === 1 && !outcomes[0].condition) {
    const row = outcomes[0]
    if (row.resultType === 'END') {
      nodes.push({ key: endKey, type: 'END' })
      edges.push({ key: 'edge_current_end', from: 'current_task', to: endKey })
    } else {
      const taskKey = `task_${row.id}`
      nodes.push({ key: taskKey, type: 'TASK', templateVersionId: row.targetVersionId })
      nodes.push({ key: endKey, type: 'END' })
      edges.push({ key: `edge_current_${row.id}`, from: 'current_task', to: taskKey })
      edges.push({ key: `edge_${row.id}_end`, from: taskKey, to: endKey })
    }
    return { start: 'current_task', nodes, edges }
  }
  nodes.push({ key: 'business_result', type: 'DECISION' }, { key: endKey, type: 'END' })
  edges.push({ key: 'edge_current_result', from: 'current_task', to: 'business_result' })
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
  return { start: 'current_task', nodes, edges }
}

function buildParallelRoute(outcomes, currentVersionId, joinMode) {
  const rows = outcomes.filter(row => row.resultType !== 'END')
  const branches = rows.map(row => row.id)
  const nodes = [
    { key: 'current_task', type: 'TASK', templateVersionId: Number(currentVersionId) || null },
    { key: 'parallel_start', type: 'FORK' },
    { key: 'parallel_join', type: 'JOIN', joinMode, branches },
    { key: 'route_end', type: 'END' }
  ]
  const edges = [
    { key: 'edge_current_parallel', from: 'current_task', to: 'parallel_start' },
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
  return { start: 'current_task', nodes, edges }
}

function buildBusinessRoutingPatch(rows, options, current) {
  const settings = object(options)
  const outcomes = list(rows).map(normalizedOutcome)
  const mode = String(settings.mode || 'SEQUENTIAL').toUpperCase()
  const joinMode = String(settings.joinMode || 'ALL').toUpperCase() === 'ANY' ? 'ANY' : 'ALL'
  const graph = mode === 'PARALLEL'
    ? buildParallelRoute(outcomes, settings.currentVersionId, joinMode)
    : buildSequentialRoute(outcomes, settings.currentVersionId)
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
  const mode = String(object(options).mode || 'SEQUENTIAL').toUpperCase()
  if (outcomes.some(row => row.resultType === 'NEXT' && !row.targetVersionId)) {
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
  return null
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
  ownerFieldSelection,
  ownerStrategy,
  isOwnerField,
  scopeOwnerFields,
  ownerSelectionStillValid,
  repairFocusTarget,
  buildOwnerConfig,
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
  buildBusinessRoutingPatch,
  routingDraftBlocker,
  createRepairRequest,
  resourceRepairAccess,
  completeResourceRepair,
  buildHydratedPayloadRows,
  updateManualOverrides,
  orderedSimulationTrace,
  publishPreflightGate,
  simulationPublishCapabilities
}
