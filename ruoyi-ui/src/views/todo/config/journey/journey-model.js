const STEP_CODES = ['EVENT', 'TRIGGER', 'OWNER', 'DOD', 'SLA', 'ROUTING', 'SIMULATION_PUBLISH']

function clone(value) {
  return value == null ? value : JSON.parse(JSON.stringify(value))
}

function object(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? clone(value) : {}
}

function defaultStep(code) {
  return { code, title: '', state: 'NOT_STARTED', issueCount: 0, value: defaultStepValue(code) }
}

function defaultStepValue(code) {
  if (code === 'EVENT') return { eventType: '', payloadVersion: 1 }
  if (code === 'ROUTING') return { config: { nodes: [], edges: [] } }
  return { config: {} }
}

function orderedSteps(steps) {
  const byCode = new Map((Array.isArray(steps) ? steps : [])
    .filter(step => step && STEP_CODES.includes(step.code))
    .map(step => [step.code, clone(step)]))
  return STEP_CODES.map(code => byCode.get(code) || defaultStep(code))
}

function stepValue(steps, code) {
  const step = steps.find(item => item.code === code)
  return step ? object(step.value) : defaultStepValue(code)
}

function triggerCondition(value) {
  const trigger = object(value)
  if (trigger.condition && typeof trigger.condition === 'object' && !Array.isArray(trigger.condition)) {
    return object(trigger.condition)
  }
  if (trigger.config && typeof trigger.config === 'object' && !Array.isArray(trigger.config)) {
    return object(trigger.config)
  }
  return trigger
}

function deriveDefinition(template, steps) {
  const event = stepValue(steps, 'EVENT')
  event.eventType = event.eventType || ''
  event.payloadVersion = Number(event.payloadVersion) || 1
  event.condition = triggerCondition(stepValue(steps, 'TRIGGER'))
  return {
    schemaVersion: 1,
    templateCode: (template && template.templateCode) || '',
    event,
    owner: stepValue(steps, 'OWNER'),
    dod: stepValue(steps, 'DOD'),
    sla: stepValue(steps, 'SLA'),
    ui: stepValue(steps, 'SIMULATION_PUBLISH'),
    routing: stepValue(steps, 'ROUTING'),
    autoActions: [],
    decisionRefs: [],
    acceptanceRefs: []
  }
}

function hydrateJourney(payload) {
  const source = clone(payload || {})
  const template = object(source.template)
  const steps = orderedSteps(source.steps)
  const payloadState = object(source.payload)
  return {
    ...source,
    template,
    steps,
    baselineSteps: clone(steps),
    definition: deriveDefinition(template, steps),
    dirty: false,
    saveState: 'SAVED',
    saveError: null,
    conflict: null,
    preflightGate: null,
    payload: {
      ...payloadState,
      manualOverrides: object(payloadState.manualOverrides)
    }
  }
}

function applyDefinitionPatch(definition, stepCode, value) {
  const next = clone(definition)
  if (stepCode === 'EVENT') {
    const condition = object(next.event && next.event.condition)
    next.event = { ...object(value), condition }
  } else if (stepCode === 'TRIGGER') {
    next.event = { ...object(next.event), condition: triggerCondition(value) }
  } else if (stepCode === 'OWNER') {
    next.owner = object(value)
  } else if (stepCode === 'DOD') {
    next.dod = object(value)
  } else if (stepCode === 'SLA') {
    next.sla = object(value)
  } else if (stepCode === 'ROUTING') {
    next.routing = object(value)
  } else if (stepCode === 'SIMULATION_PUBLISH') {
    next.ui = object(value)
  }
  return next
}

function applyStepPatch(journey, stepCode, value) {
  if (!STEP_CODES.includes(stepCode)) throw new Error(`Unknown journey step: ${stepCode}`)
  const current = clone(journey)
  const nextValue = object(value)
  const steps = orderedSteps(current.steps).map(step => step.code === stepCode
    ? { ...step, value: clone(nextValue), state: 'IN_PROGRESS' }
    : step)
  return {
    ...current,
    steps,
    definition: applyDefinitionPatch(current.definition || deriveDefinition(current.template, current.steps), stepCode, nextValue),
    dirty: true,
    saveState: 'IDLE',
    saveError: null,
    conflict: null,
    preflightGate: null
  }
}

function currentStepCode(journey) {
  const selected = journey && journey.activeStepCode
  if (STEP_CODES.includes(selected)) return selected
  const steps = orderedSteps(journey && journey.steps)
  const incomplete = steps.find(step => step.state !== 'COMPLETED')
  return incomplete ? incomplete.code : 'SIMULATION_PUBLISH'
}

function derivePrimaryAction(journey) {
  const stepCode = currentStepCode(journey)
  if (journey && journey.dirty) {
    return { code: 'SAVE_CONTINUE', label: '保存并继续', stepCode }
  }
  if (stepCode === 'SIMULATION_PUBLISH') {
    const simulation = orderedSteps(journey && journey.steps).find(step => step.code === stepCode)
    return simulation && simulation.state === 'COMPLETED'
      ? { code: 'PUBLISH_PREFLIGHT', label: '发布预检', stepCode }
      : { code: 'RUN_SIMULATION', label: '试运行', stepCode }
  }
  return { code: 'CONTINUE_CONFIGURATION', label: '继续配置', stepCode }
}

function resultStatus(result) {
  const source = result || {}
  const status = String(source.status || source.state || source.outcome || '').toUpperCase()
  if (status === 'CONFLICT' || source.conflict === true) return 'CONFLICT'
  if (status === 'FAILED' || status === 'FAILURE' || status === 'ERROR' || source.error) return 'FAILED'
  return 'SUCCESS'
}

function saveFailure(journey, result) {
  return {
    ...clone(journey),
    dirty: true,
    saveState: 'FAILED',
    saveError: clone(result && (result.error || result.saveError)) || { code: 'TODO_JOURNEY_SAVE_FAILED', message: '保存失败，请重试' },
    conflict: null
  }
}

function localSnapshot(journey) {
  const local = clone(journey)
  local.conflict = null
  local.saveError = null
  return local
}

function saveConflict(journey, result) {
  const source = result || {}
  const server = source.server || source.serverJourney || (source.conflict && source.conflict.server) || {}
  return {
    ...clone(journey),
    dirty: true,
    saveState: 'FAILED',
    saveError: clone(source.error) || { code: 'TODO_JOURNEY_VERSION_CONFLICT', message: '草稿已被其他用户更新' },
    conflict: {
      server: clone(server),
      local: localSnapshot(journey)
    }
  }
}

function definedMetadata(source) {
  const metadata = {}
  for (const key of ['templateId', 'versionId', 'versionNo', 'lockVersion', 'definitionHash', 'publishStatus']) {
    if (source && source[key] !== undefined) metadata[key] = clone(source[key])
  }
  return metadata
}

function saveSuccess(journey, result) {
  const response = result || {}
  const aggregate = response.journey || response
  const steps = aggregate.steps ? orderedSteps(aggregate.steps) : orderedSteps(journey.steps)
  const template = {
    ...object(journey.template),
    ...definedMetadata(response),
    ...definedMetadata(aggregate.template)
  }
  const next = {
    ...clone(journey),
    template,
    steps,
    baselineSteps: clone(steps),
    definition: deriveDefinition(template, steps),
    dirty: false,
    saveState: 'SAVED',
    saveError: null,
    conflict: null,
    preflightGate: null
  }
  for (const key of ['resources', 'employeePreview', 'issues', 'permissions']) {
    if (aggregate[key] !== undefined) next[key] = clone(aggregate[key])
  }
  return next
}

function mergeSaveResult(journey, result) {
  const status = resultStatus(result)
  if (status === 'CONFLICT') return saveConflict(journey, result)
  if (status === 'FAILED') return saveFailure(journey, result)
  return saveSuccess(journey, result)
}

function mergeConflictWithServer(journey) {
  const current = clone(journey || {})
  const conflict = current.conflict || {}
  const local = conflict.local || current
  const server = hydrateJourney(conflict.server || {})
  const baselineByCode = new Map(orderedSteps(local.baselineSteps).map(step => [step.code, step]))
  let merged = server
  let hasLocalChanges = false

  for (const step of orderedSteps(local.steps)) {
    const baseline = baselineByCode.get(step.code)
    if (JSON.stringify(step.value) !== JSON.stringify(baseline && baseline.value)) {
      merged = applyStepPatch(merged, step.code, step.value)
      hasLocalChanges = true
    }
  }

  return {
    ...merged,
    dirty: hasLocalChanges,
    saveState: hasLocalChanges ? 'IDLE' : 'SAVED',
    saveError: null,
    conflict: null
  }
}

function rebaseJourneyAfterSave(currentJourney, savingJourney, result) {
  const current = clone(currentJourney || {})
  const saving = clone(savingJourney || {})
  const savingByCode = new Map(orderedSteps(saving.steps).map(step => [step.code, step]))
  let rebased = saveSuccess(saving, result)

  for (const step of orderedSteps(current.steps)) {
    const savingStep = savingByCode.get(step.code)
    if (JSON.stringify(step.value) !== JSON.stringify(savingStep && savingStep.value)) {
      rebased = applyStepPatch(rebased, step.code, step.value)
    }
  }

  return rebased
}

function toSimulationCommand(journey, businessObject, effectiveAt) {
  const current = journey || {}
  const template = current.template || {}
  const definition = current.definition || deriveDefinition(template, current.steps)
  const event = definition.event || {}
  const objectValue = businessObject || {}
  return {
    requestId: `journey-${Date.now()}`,
    templateId: Number(template.templateId),
    versionId: Number(template.versionId),
    eventType: event.eventType || '',
    payloadVersion: Number(event.payloadVersion) || 1,
    businessType: template.businessType || '',
    businessId: Number(objectValue.businessId),
    manualOverrides: object(current.payload && current.payload.manualOverrides),
    effectiveAt,
    expectedDefinitionHash: template.definitionHash,
    taskCompletions: []
  }
}

function canLeave(journey) {
  return !journey || !journey.dirty || journey.saveState === 'SAVED'
}

module.exports = {
  hydrateJourney,
  applyStepPatch,
  derivePrimaryAction,
  mergeSaveResult,
  mergeConflictWithServer,
  rebaseJourneyAfterSave,
  toSimulationCommand,
  canLeave
}
