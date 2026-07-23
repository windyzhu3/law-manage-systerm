const assert = require('assert')
const model = require('../src/views/todo/config/journey/journey-model')

const STEP_CODES = ['EVENT', 'TRIGGER', 'OWNER', 'DOD', 'SLA', 'ROUTING', 'SIMULATION_PUBLISH']
let checks = 0

function check(name, assertion) {
  assertion()
  checks += 1
  process.stdout.write(`  pass ${name}\n`)
}

function fixture() {
  return {
    template: {
      templateId: 42,
      versionId: 101,
      versionNo: 3,
      lockVersion: 4,
      templateCode: 'LEAD-FIRST-CONTACT',
      templateName: '线索首次联系',
      businessType: 'LEAD',
      businessStage: 'FIRST_CONTACT',
      publishStatus: 'DRAFT',
      definitionHash: 'hash-before-save'
    },
    steps: [
      { code: 'EVENT', title: '业务事件', state: 'COMPLETED', issueCount: 0, value: { eventType: 'LEAD_ASSIGNED', payloadVersion: 2 } },
      { code: 'TRIGGER', title: '触发条件', state: 'COMPLETED', issueCount: 0, value: { op: 'EQ', field: 'leadStatus', value: 'ASSIGNED' } },
      { code: 'OWNER', title: '负责人', state: 'NOT_STARTED', issueCount: 1, value: { config: {} } },
      { code: 'DOD', title: '完成标准', state: 'NOT_STARTED', issueCount: 0, value: { config: {} } },
      { code: 'SLA', title: '办理时限', state: 'NOT_STARTED', issueCount: 0, value: { config: {} } },
      { code: 'ROUTING', title: '后续路由', state: 'COMPLETED', issueCount: 0, value: { config: { nodes: [], edges: [] } } },
      { code: 'SIMULATION_PUBLISH', title: '模拟发布', state: 'NOT_STARTED', issueCount: 1, value: { config: {} } }
    ],
    resources: { fields: [{ code: 'leadStatus', label: '线索状态' }] },
    employeePreview: { title: '联系线索', fields: [] },
    issues: [{ code: 'TODO_JOURNEY_OWNER_FALLBACK_REQUIRED', stepCode: 'OWNER', severity: 'BLOCKER' }],
    permissions: { canEdit: true, canSimulate: true, canPublish: true },
    payload: { manualOverrides: { contactResult: '已接通' } }
  }
}

check('hydrates the seven structured steps without reading raw definition JSON', () => {
  const input = fixture()
  input.definitionJson = '{"must":"not be consumed"}'
  const snapshot = JSON.stringify(input)
  const journey = model.hydrateJourney(input)

  assert.deepStrictEqual(journey.steps.map(step => step.code), STEP_CODES)
  assert.strictEqual(journey.definition.templateCode, 'LEAD-FIRST-CONTACT')
  assert.deepStrictEqual(journey.definition.event, {
    eventType: 'LEAD_ASSIGNED',
    payloadVersion: 2,
    condition: { op: 'EQ', field: 'leadStatus', value: 'ASSIGNED' }
  })
  assert.deepStrictEqual(journey.definition.owner, { config: {} })
  assert.deepStrictEqual(journey.definition.routing, { config: { nodes: [], edges: [] } })
  assert.deepStrictEqual(journey.definition.ui, { config: {} })
  assert.strictEqual(journey.dirty, false)
  assert.strictEqual(journey.saveState, 'SAVED')
  assert.deepStrictEqual(journey.payload.manualOverrides, { contactResult: '已接通' })
  assert.strictEqual(JSON.stringify(input), snapshot)
})

check('patches a step and its derived definition immutably', () => {
  const journey = model.hydrateJourney(fixture())
  const snapshot = JSON.stringify(journey)
  const owner = { config: { type: 'BUSINESS_OWNER', fallback: { type: 'ROLE', value: 'LEAD_MANAGER' } } }
  const changed = model.applyStepPatch(journey, 'OWNER', owner)
  owner.config.type = 'MUTATED_AFTER_CALL'

  assert.strictEqual(changed.dirty, true)
  assert.strictEqual(changed.saveState, 'IDLE')
  assert.strictEqual(changed.preflightGate, null)
  assert.strictEqual(changed.steps.find(step => step.code === 'OWNER').state, 'IN_PROGRESS')
  assert.strictEqual(changed.steps.find(step => step.code === 'OWNER').value.config.type, 'BUSINESS_OWNER')
  assert.strictEqual(changed.definition.owner.config.type, 'BUSINESS_OWNER')
  assert.strictEqual(JSON.stringify(journey), snapshot)

  const trigger = model.applyStepPatch(changed, 'TRIGGER', { op: 'IN', field: 'leadSource', value: ['官网'] })
  assert.deepStrictEqual(trigger.definition.event.condition, { op: 'IN', field: 'leadSource', value: ['官网'] })

  const simulation = model.applyStepPatch(trigger, 'SIMULATION_PUBLISH', { config: { simulationStatus: 'SUCCESS' } })
  assert.deepStrictEqual(simulation.definition.ui, { config: { simulationStatus: 'SUCCESS' } })
})

check('derives business-facing primary actions from dirty and journey state', () => {
  const journey = model.hydrateJourney(fixture())
  assert.deepStrictEqual(model.derivePrimaryAction(journey), {
    code: 'CONTINUE_CONFIGURATION',
    label: '继续配置',
    stepCode: 'OWNER'
  })

  const changed = model.applyStepPatch(journey, 'OWNER', { config: { type: 'BUSINESS_OWNER' } })
  assert.deepStrictEqual(model.derivePrimaryAction(changed), {
    code: 'SAVE_CONTINUE',
    label: '保存并继续',
    stepCode: 'OWNER'
  })

  const simulation = { ...journey, activeStepCode: 'SIMULATION_PUBLISH' }
  assert.deepStrictEqual(model.derivePrimaryAction(simulation), {
    code: 'RUN_SIMULATION',
    label: '试运行',
    stepCode: 'SIMULATION_PUBLISH'
  })

  const publish = {
    ...simulation,
    steps: simulation.steps.map(step => step.code === 'SIMULATION_PUBLISH'
      ? { ...step, state: 'COMPLETED' }
      : step)
  }
  assert.deepStrictEqual(model.derivePrimaryAction(publish), {
    code: 'PUBLISH_PREFLIGHT',
    label: '发布预检',
    stepCode: 'SIMULATION_PUBLISH'
  })
})

check('merges successful save metadata without mutating the local journey', () => {
  const local = model.applyStepPatch(model.hydrateJourney(fixture()), 'OWNER', { config: { type: 'BUSINESS_OWNER' } })
  const snapshot = JSON.stringify(local)
  const result = {
    status: 'SUCCESS',
    template: { lockVersion: 5, definitionHash: 'hash-after-save' },
    steps: local.steps.map(step => ({ ...step, state: step.code === 'OWNER' ? 'COMPLETED' : step.state })),
    issues: []
  }
  const saved = model.mergeSaveResult(local, result)

  assert.strictEqual(saved.template.lockVersion, 5)
  assert.strictEqual(saved.template.definitionHash, 'hash-after-save')
  assert.strictEqual(saved.steps.find(step => step.code === 'OWNER').state, 'COMPLETED')
  assert.strictEqual(saved.definition.owner.config.type, 'BUSINESS_OWNER')
  assert.strictEqual(saved.dirty, false)
  assert.strictEqual(saved.saveState, 'SAVED')
  assert.strictEqual(saved.conflict, null)
  assert.strictEqual(JSON.stringify(local), snapshot)
})

check('retains local edits when saving fails', () => {
  const local = model.applyStepPatch(model.hydrateJourney(fixture()), 'DOD', {
    config: { requiredFields: ['contactedAt', 'contactResult'] }
  })
  const failed = model.mergeSaveResult(local, {
    status: 'FAILED',
    error: { code: 'NETWORK_ERROR', message: '网络暂时不可用' }
  })

  assert.deepStrictEqual(failed.definition.dod, local.definition.dod)
  assert.deepStrictEqual(failed.steps, local.steps)
  assert.strictEqual(failed.dirty, true)
  assert.strictEqual(failed.saveState, 'FAILED')
  assert.deepStrictEqual(failed.saveError, { code: 'NETWORK_ERROR', message: '网络暂时不可用' })
})

check('captures independent local and server snapshots on optimistic-lock conflict', () => {
  const local = model.applyStepPatch(model.hydrateJourney(fixture()), 'SLA', {
    config: { durationValue: 2, durationUnit: 'HOUR', calendarCode: 'WORKDAY' }
  })
  const server = fixture()
  server.template.lockVersion = 5
  server.template.definitionHash = 'server-hash'
  const conflicted = model.mergeSaveResult(local, { status: 'CONFLICT', server })

  assert.strictEqual(conflicted.dirty, true)
  assert.strictEqual(conflicted.saveState, 'FAILED')
  assert.strictEqual(conflicted.conflict.local.definition.sla.config.durationValue, 2)
  assert.strictEqual(conflicted.conflict.server.template.lockVersion, 5)

  conflicted.steps[4].value.config.durationValue = 9
  server.template.lockVersion = 99
  assert.strictEqual(conflicted.conflict.local.steps[4].value.config.durationValue, 2)
  assert.strictEqual(conflicted.conflict.server.template.lockVersion, 5)
})

check('refreshes a conflict with server changes while retaining only local edits', () => {
  const original = model.hydrateJourney(fixture())
  const local = model.applyStepPatch(original, 'SLA', {
    config: { durationValue: 2, durationUnit: 'HOUR', calendarCode: 'WORKDAY' }
  })
  const server = fixture()
  server.template.lockVersion = 5
  server.steps = server.steps.map(step => step.code === 'OWNER'
    ? { ...step, value: { config: { type: 'ROLE', value: 'LEAD_MANAGER' } }, state: 'COMPLETED' }
    : step)
  const conflicted = model.mergeSaveResult(local, { status: 'CONFLICT', server })
  const merged = model.mergeConflictWithServer(conflicted)

  assert.strictEqual(merged.template.lockVersion, 5)
  assert.strictEqual(merged.definition.owner.config.type, 'ROLE')
  assert.strictEqual(merged.definition.sla.config.durationValue, 2)
  assert.strictEqual(merged.dirty, true)
  assert.strictEqual(merged.saveState, 'IDLE')
  assert.strictEqual(merged.conflict, null)
})

check('rebases edits made during a save onto the fresh server baseline', () => {
  const original = model.hydrateJourney(fixture())
  const saving = model.applyStepPatch(original, 'OWNER', {
    config: { type: 'BUSINESS_OWNER' }
  })
  const current = model.applyStepPatch(saving, 'SLA', {
    config: { durationValue: 4, durationUnit: 'HOUR' }
  })
  const fresh = fixture()
  fresh.template.lockVersion = 5
  fresh.steps = fresh.steps.map(step => step.code === 'OWNER'
    ? { ...step, value: saving.steps.find(item => item.code === 'OWNER').value, state: 'COMPLETED' }
    : step)
  const rebased = model.rebaseJourneyAfterSave(current, saving, fresh)

  assert.strictEqual(rebased.template.lockVersion, 5)
  assert.strictEqual(rebased.definition.owner.config.type, 'BUSINESS_OWNER')
  assert.strictEqual(rebased.definition.sla.config.durationValue, 4)
  assert.strictEqual(rebased.dirty, true)
  assert.strictEqual(rebased.saveState, 'IDLE')
  assert.deepStrictEqual(
    rebased.baselineSteps.find(step => step.code === 'OWNER').value,
    rebased.steps.find(step => step.code === 'OWNER').value
  )
  assert.notDeepStrictEqual(
    rebased.baselineSteps.find(step => step.code === 'SLA').value,
    rebased.steps.find(step => step.code === 'SLA').value
  )
})

check('builds a read-only simulation command without runtime-write flags', () => {
  const journey = model.hydrateJourney(fixture())
  const object = { businessId: '9001', title: '测试线索' }
  const snapshot = JSON.stringify(object)
  const command = model.toSimulationCommand(journey, object, '2026-07-23T10:00:00')

  assert.strictEqual(command.templateId, 42)
  assert.strictEqual(command.versionId, 101)
  assert.strictEqual(command.eventType, 'LEAD_ASSIGNED')
  assert.strictEqual(command.payloadVersion, 2)
  assert.strictEqual(command.businessType, 'LEAD')
  assert.strictEqual(command.businessId, 9001)
  assert.deepStrictEqual(command.manualOverrides, { contactResult: '已接通' })
  assert.strictEqual(command.effectiveAt, '2026-07-23T10:00:00')
  assert.strictEqual(command.expectedDefinitionHash, 'hash-before-save')
  assert.deepStrictEqual(command.taskCompletions, [])
  assert(command.requestId.startsWith('journey-'))
  for (const forbidden of ['sample', 'dryRun', 'runtimeWrite', 'createRuntimeTasks', 'persist']) {
    assert.strictEqual(Object.prototype.hasOwnProperty.call(command, forbidden), false)
  }
  command.manualOverrides.contactResult = '被修改'
  assert.strictEqual(journey.payload.manualOverrides.contactResult, '已接通')
  assert.strictEqual(JSON.stringify(object), snapshot)
})

check('allows navigation only when there are no unsaved edits', () => {
  const journey = model.hydrateJourney(fixture())
  const changed = model.applyStepPatch(journey, 'OWNER', { config: { type: 'BUSINESS_OWNER' } })

  assert.strictEqual(model.canLeave(journey), true)
  assert.strictEqual(model.canLeave(changed), false)
  assert.strictEqual(model.canLeave({ ...changed, saveState: 'SAVED' }), true)
})

console.log(`todo phase two journey model contract passed (${checks} checks)`)
