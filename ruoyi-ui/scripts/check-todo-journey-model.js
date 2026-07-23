const assert = require('assert')
const model = require('../src/views/todo/config/journey/journey-model')
const steps = require('../src/views/todo/config/journey/journey-step-model')
const runtime = require('../src/views/todo/config/journey/journey-runtime')

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

check('derives an exact least-privilege journey API read plan', () => {
  const listOnly = runtime.resolveJourneyCapabilities(['todo:template:list'])
  assert.strictEqual(listOnly.canLoadJourney, true)
  assert.strictEqual(listOnly.canSaveDraft, false)
  assert.deepStrictEqual(runtime.snapshotReadPlan(listOnly), ['JOURNEY'])

  const simulator = runtime.resolveJourneyCapabilities(['todo:simulation:simulate'])
  assert.strictEqual(simulator.canLoadJourney, true)
  assert.strictEqual(simulator.canReadDraftDetail, false)
  assert.deepStrictEqual(runtime.snapshotReadPlan(simulator), ['JOURNEY'])

  const editor = runtime.resolveJourneyCapabilities(['todo:template:edit'])
  assert.strictEqual(editor.canLoadJourney, true)
  assert.strictEqual(editor.canReadDraftDetail, true)
  assert.strictEqual(editor.canSaveDraft, true)
  assert.deepStrictEqual(runtime.snapshotReadPlan(editor), ['DETAIL', 'JOURNEY', 'DETAIL'])

  const creatorWithoutJourneyRead = runtime.resolveJourneyCapabilities(['todo:template:create'])
  assert.strictEqual(creatorWithoutJourneyRead.canLoadJourney, false)
  assert.strictEqual(creatorWithoutJourneyRead.canSaveDraft, false)
  assert.deepStrictEqual(runtime.snapshotReadPlan(creatorWithoutJourneyRead), [])

  const creatorFromWorkbench = runtime.resolveJourneyCapabilities(['todo:template:list', 'todo:template:create'])
  assert.strictEqual(creatorFromWorkbench.canSaveDraft, true)
  assert.deepStrictEqual(runtime.snapshotReadPlan(creatorFromWorkbench), ['DETAIL', 'JOURNEY', 'DETAIL'])

  const anonymous = runtime.resolveJourneyCapabilities([])
  assert.deepStrictEqual(runtime.snapshotReadPlan(anonymous), [])
})

check('guards only meaningful reused-route context changes', () => {
  const base = { query: { templateId: '42', view: 'draft', step: 'OWNER' } }
  assert.strictEqual(runtime.routeContextChanged(base, {
    query: { templateId: '42', view: 'draft', step: 'SLA' }
  }), false)
  assert.strictEqual(runtime.routeContextChanged(base, {
    query: { templateId: '42', view: 'published', step: 'OWNER' }
  }), true)
  assert.strictEqual(runtime.routeContextChanged(base, {
    query: { templateId: '43', view: 'draft', step: 'OWNER' }
  }), true)
  assert.strictEqual(runtime.routeContextChanged(base, {
    query: { templateId: '42', view: 'draft', step: 'OWNER', compare: 'published' }
  }), true)
})

check('stages and consumes an authoritative copied-template transition deterministically', () => {
  const authoritative = model.hydrateJourney(fixture())
  authoritative.template.templateId = 99
  authoritative.template.versionId = 199
  const context = { versionId: 199, sourceDefinitionJson: '{"copy":true}' }
  const journeySnapshot = JSON.stringify(authoritative)
  const contextSnapshot = JSON.stringify(context)
  const transition = runtime.createCopyTransition(99, authoritative, context)

  authoritative.template.templateId = -1
  context.versionId = -1
  assert.strictEqual(runtime.copyTransitionMatches(transition, 98), false)
  assert.strictEqual(runtime.consumeCopyTransition(transition, 98), null)
  assert.strictEqual(runtime.copyTransitionMatches(transition, 99), true)
  const consumed = runtime.consumeCopyTransition(transition, 99)
  assert.strictEqual(consumed.journey.template.templateId, 99)
  assert.strictEqual(consumed.context.versionId, 199)
  assert.strictEqual(JSON.stringify(consumed.journey), journeySnapshot)
  assert.strictEqual(JSON.stringify(consumed.context), contextSnapshot)
})

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

check('three-way merges independent nested fields within the same step', () => {
  const base = fixture()
  base.steps = base.steps.map(step => step.code === 'OWNER'
    ? {
        ...step,
        state: 'COMPLETED',
        value: {
          config: {
            type: 'ROLE',
            fallback: { type: 'ROLE', value: 'LEAD_MANAGER' },
            skipUnavailable: true
          }
        }
      }
    : step)
  const original = model.hydrateJourney(base)
  const localOwner = JSON.parse(JSON.stringify(original.steps.find(step => step.code === 'OWNER').value))
  localOwner.config.fallback.value = 'DUTY_MANAGER'
  const local = model.applyStepPatch(original, 'OWNER', localOwner)
  const server = fixture()
  server.template.lockVersion = 5
  server.steps = base.steps.map(step => step.code === 'OWNER'
    ? { ...step, value: { ...step.value, config: { ...step.value.config, skipUnavailable: false } } }
    : step)
  const conflicted = model.mergeSaveResult(local, { status: 'CONFLICT', server })
  const merged = model.mergeConflictWithServer(conflicted)

  assert.strictEqual(merged.definition.owner.config.fallback.value, 'DUTY_MANAGER')
  assert.strictEqual(merged.definition.owner.config.skipUnavailable, false)
  assert.strictEqual(merged.conflict, null)
  assert.strictEqual(merged.dirty, true)
  assert(conflicted.conflict.differences.some(item =>
    item.stepCode === 'OWNER' && item.path === 'config.fallback.value' && item.kind === 'LOCAL'
  ))
  assert(conflicted.conflict.differences.some(item =>
    item.stepCode === 'OWNER' && item.path === 'config.skipUnavailable' && item.kind === 'SERVER'
  ))
})

check('three-way merges independent fields added under the same new nested object', () => {
  const base = fixture()
  base.steps = base.steps.map(step => step.code === 'OWNER'
    ? { ...step, value: { config: {} } }
    : step)
  const original = model.hydrateJourney(base)
  const local = model.applyStepPatch(original, 'OWNER', {
    config: { fallback: { value: 'DUTY_MANAGER' } }
  })
  const server = fixture()
  server.template.lockVersion = 5
  server.steps = base.steps.map(step => step.code === 'OWNER'
    ? { ...step, value: { config: { fallback: { type: 'ROLE' } } } }
    : step)
  const merged = model.mergeConflictWithServer(
    model.mergeSaveResult(local, { status: 'CONFLICT', server })
  )

  assert.strictEqual(merged.definition.owner.config.fallback.type, 'ROLE')
  assert.strictEqual(merged.definition.owner.config.fallback.value, 'DUTY_MANAGER')
  assert.strictEqual(merged.conflict, null)
})

check('three-way merges independent fields on different identified array entries', () => {
  const base = fixture()
  const nodes = [
    { id: 'a', label: 'Node A', target: 'QUEUE_A' },
    { id: 'b', label: 'Node B', target: 'QUEUE_B' }
  ]
  base.steps = base.steps.map(step => step.code === 'ROUTING'
    ? { ...step, value: { config: { nodes, edges: [] } } }
    : step)
  const original = model.hydrateJourney(base)
  const local = model.applyStepPatch(original, 'ROUTING', {
    config: { nodes: [{ ...nodes[0], label: 'Local A' }, nodes[1]], edges: [] }
  })
  const server = fixture()
  server.template.lockVersion = 5
  server.steps = base.steps.map(step => step.code === 'ROUTING'
    ? { ...step, value: { config: { nodes: [nodes[0], { ...nodes[1], label: 'Server B' }], edges: [] } } }
    : step)
  const conflicted = model.mergeSaveResult(local, { status: 'CONFLICT', server })
  const merged = model.mergeConflictWithServer(conflicted)

  assert.deepStrictEqual(
    merged.definition.routing.config.nodes.map(node => [node.id, node.label]),
    [['a', 'Local A'], ['b', 'Server B']]
  )
  assert.strictEqual(merged.conflict, null)
  assert(conflicted.conflict.differences.some(item =>
    item.path === 'config.nodes[id=a].label' && item.kind === 'LOCAL'
  ))
  assert(conflicted.conflict.differences.some(item =>
    item.path === 'config.nodes[id=b].label' && item.kind === 'SERVER'
  ))
})

check('keeps same identified array entry field conflicts precise and unresolved', () => {
  const base = fixture()
  const node = { id: 'a', label: 'Node A', target: 'QUEUE_A' }
  base.steps = base.steps.map(step => step.code === 'ROUTING'
    ? { ...step, value: { config: { nodes: [node], edges: [] } } }
    : step)
  const original = model.hydrateJourney(base)
  const local = model.applyStepPatch(original, 'ROUTING', {
    config: { nodes: [{ ...node, label: 'Local A' }], edges: [] }
  })
  const server = fixture()
  server.template.lockVersion = 5
  server.steps = base.steps.map(step => step.code === 'ROUTING'
    ? { ...step, value: { config: { nodes: [{ ...node, label: 'Server A' }], edges: [] } } }
    : step)
  const merged = model.mergeConflictWithServer(
    model.mergeSaveResult(local, { status: 'CONFLICT', server })
  )

  assert.strictEqual(merged.definition.routing.config.nodes[0].label, 'Server A')
  assert.strictEqual(merged.conflict.collisions.length, 1)
  assert.strictEqual(merged.conflict.collisions[0].path, 'config.nodes[id=a].label')
})

check('preserves independent identified array additions in deterministic order', () => {
  const base = fixture()
  const nodeA = { id: 'a', label: 'Node A' }
  const nodeB = { id: 'b', label: 'Server B' }
  const nodeC = { id: 'c', label: 'Local C' }
  base.steps = base.steps.map(step => step.code === 'ROUTING'
    ? { ...step, value: { config: { nodes: [nodeA], edges: [] } } }
    : step)
  const original = model.hydrateJourney(base)
  const local = model.applyStepPatch(original, 'ROUTING', {
    config: { nodes: [nodeA, nodeC], edges: [] }
  })
  const server = fixture()
  server.template.lockVersion = 5
  server.steps = base.steps.map(step => step.code === 'ROUTING'
    ? { ...step, value: { config: { nodes: [nodeA, nodeB], edges: [] } } }
    : step)
  const merged = model.mergeConflictWithServer(
    model.mergeSaveResult(local, { status: 'CONFLICT', server })
  )

  assert.deepStrictEqual(merged.definition.routing.config.nodes.map(node => node.id), ['a', 'b', 'c'])
  assert.strictEqual(merged.conflict, null)
})

check('preserves local insertion position when the server has no structural array change', () => {
  const base = fixture()
  const nodes = [
    { id: 'a', label: 'Node A' },
    { id: 'b', label: 'Node B' }
  ]
  const localNode = { id: 'c', label: 'Local C' }
  base.steps = base.steps.map(step => step.code === 'ROUTING'
    ? { ...step, value: { config: { nodes, edges: [] } } }
    : step)
  const original = model.hydrateJourney(base)
  const local = model.applyStepPatch(original, 'ROUTING', {
    config: { nodes: [nodes[0], localNode, nodes[1]], edges: [] }
  })
  const server = fixture()
  server.template.lockVersion = 5
  server.steps = base.steps.map(step => step.code === 'ROUTING'
    ? { ...step, value: { config: { nodes: [nodes[0], { ...nodes[1], label: 'Server B' }], edges: [] } } }
    : step)
  const merged = model.mergeConflictWithServer(
    model.mergeSaveResult(local, { status: 'CONFLICT', server })
  )

  assert.deepStrictEqual(merged.definition.routing.config.nodes.map(node => node.id), ['a', 'c', 'b'])
  assert.strictEqual(merged.definition.routing.config.nodes[2].label, 'Server B')
  assert.strictEqual(merged.conflict, null)
})

check('preserves a local reorder when the server only edits an entry field', () => {
  const base = fixture()
  const nodes = [
    { id: 'a', label: 'Node A' },
    { id: 'b', label: 'Node B' },
    { id: 'c', label: 'Node C' }
  ]
  base.steps = base.steps.map(step => step.code === 'ROUTING'
    ? { ...step, value: { config: { nodes, edges: [] } } }
    : step)
  const original = model.hydrateJourney(base)
  const local = model.applyStepPatch(original, 'ROUTING', {
    config: { nodes: [nodes[1], nodes[0], nodes[2]], edges: [] }
  })
  const server = fixture()
  server.template.lockVersion = 5
  server.steps = base.steps.map(step => step.code === 'ROUTING'
    ? { ...step, value: { config: { nodes: [nodes[0], { ...nodes[1], label: 'Server B' }, nodes[2]], edges: [] } } }
    : step)
  const merged = model.mergeConflictWithServer(
    model.mergeSaveResult(local, { status: 'CONFLICT', server })
  )

  assert.deepStrictEqual(merged.definition.routing.config.nodes.map(node => node.id), ['b', 'a', 'c'])
  assert.strictEqual(merged.definition.routing.config.nodes[0].label, 'Server B')
  assert.strictEqual(merged.conflict, null)
})

check('raises a precise order collision for incompatible concurrent reorders', () => {
  const base = fixture()
  const nodes = [
    { id: 'a', label: 'Node A' },
    { id: 'b', label: 'Node B' },
    { id: 'c', label: 'Node C' }
  ]
  base.steps = base.steps.map(step => step.code === 'ROUTING'
    ? { ...step, value: { config: { nodes, edges: [] } } }
    : step)
  const original = model.hydrateJourney(base)
  const local = model.applyStepPatch(original, 'ROUTING', {
    config: { nodes: [nodes[1], nodes[0], nodes[2]], edges: [] }
  })
  const server = fixture()
  server.template.lockVersion = 5
  server.steps = base.steps.map(step => step.code === 'ROUTING'
    ? { ...step, value: { config: { nodes: [nodes[0], nodes[2], nodes[1]], edges: [] } } }
    : step)
  const merged = model.mergeConflictWithServer(
    model.mergeSaveResult(local, { status: 'CONFLICT', server })
  )

  assert.deepStrictEqual(merged.definition.routing.config.nodes.map(node => node.id), ['a', 'c', 'b'])
  assert.strictEqual(merged.conflict.collisions.length, 1)
  assert.strictEqual(merged.conflict.collisions[0].path, 'config.nodes[@order]')
})

check('treats identified array delete versus edit as an entry-level collision', () => {
  const base = fixture()
  const node = { id: 'a', label: 'Node A', target: 'QUEUE_A' }
  base.steps = base.steps.map(step => step.code === 'ROUTING'
    ? { ...step, value: { config: { nodes: [node], edges: [] } } }
    : step)
  const original = model.hydrateJourney(base)
  const local = model.applyStepPatch(original, 'ROUTING', {
    config: { nodes: [], edges: [] }
  })
  const server = fixture()
  server.template.lockVersion = 5
  server.steps = base.steps.map(step => step.code === 'ROUTING'
    ? { ...step, value: { config: { nodes: [{ ...node, label: 'Server A' }], edges: [] } } }
    : step)
  const merged = model.mergeConflictWithServer(
    model.mergeSaveResult(local, { status: 'CONFLICT', server })
  )

  assert.strictEqual(merged.definition.routing.config.nodes[0].label, 'Server A')
  assert.strictEqual(merged.conflict.collisions.length, 1)
  assert.strictEqual(merged.conflict.collisions[0].path, 'config.nodes[id=a]')
})

check('keeps primitive arrays atomic and conflicts on divergent concurrent changes', () => {
  const base = fixture()
  base.steps = base.steps.map(step => step.code === 'ROUTING'
    ? { ...step, value: { config: { nodes: [], edges: ['a', 'b'] } } }
    : step)
  const original = model.hydrateJourney(base)
  const local = model.applyStepPatch(original, 'ROUTING', {
    config: { nodes: [], edges: ['a', 'b', 'c'] }
  })
  const server = fixture()
  server.template.lockVersion = 5
  server.steps = base.steps.map(step => step.code === 'ROUTING'
    ? { ...step, value: { config: { nodes: [], edges: ['a', 'b', 'd'] } } }
    : step)
  const merged = model.mergeConflictWithServer(
    model.mergeSaveResult(local, { status: 'CONFLICT', server })
  )

  assert.deepStrictEqual(merged.definition.routing.config.edges, ['a', 'b', 'd'])
  assert.strictEqual(merged.conflict.collisions.length, 1)
  assert.strictEqual(merged.conflict.collisions[0].path, 'config.edges')
})

check('keeps true same-field collisions unresolved and never overwrites the server value', () => {
  const base = fixture()
  base.steps = base.steps.map(step => step.code === 'OWNER'
    ? { ...step, value: { config: { fallback: { type: 'ROLE', value: 'LEAD_MANAGER' } } } }
    : step)
  const original = model.hydrateJourney(base)
  const local = model.applyStepPatch(original, 'OWNER', {
    config: { fallback: { type: 'ROLE', value: 'DUTY_MANAGER' } }
  })
  const server = fixture()
  server.template.lockVersion = 5
  server.steps = base.steps.map(step => step.code === 'OWNER'
    ? { ...step, value: { config: { fallback: { type: 'ROLE', value: 'CASE_MANAGER' } } } }
    : step)
  const conflicted = model.mergeSaveResult(local, { status: 'CONFLICT', server })
  const merged = model.mergeConflictWithServer(conflicted)

  assert.strictEqual(conflicted.conflict.collisions.length, 1)
  assert.strictEqual(conflicted.conflict.collisions[0].path, 'config.fallback.value')
  assert.strictEqual(merged.definition.owner.config.fallback.value, 'CASE_MANAGER')
  assert.strictEqual(merged.saveState, 'FAILED')
  assert.strictEqual(merged.dirty, true)
  assert.strictEqual(merged.conflict.collisions.length, 1)
  assert.strictEqual(model.hasUnresolvedFieldConflicts(merged), true)
  assert.strictEqual(model.hasUnresolvedFieldConflicts(original), false)
})

check('builds a local-preserving copy and applies the authoritative saved copy result', () => {
  const base = fixture()
  base.steps = base.steps.map(step => step.code === 'OWNER'
    ? { ...step, value: { config: { fallback: { type: 'ROLE', value: 'LEAD_MANAGER' } } } }
    : step)
  const local = model.applyStepPatch(model.hydrateJourney(base), 'OWNER', {
    config: { fallback: { type: 'ROLE', value: 'DUTY_MANAGER' } }
  })
  const server = fixture()
  server.template.lockVersion = 5
  server.steps = base.steps.map(step => step.code === 'OWNER'
    ? { ...step, value: { config: { fallback: { type: 'ROLE', value: 'CASE_MANAGER' } } } }
    : step)
  const conflicted = model.mergeSaveResult(local, { status: 'CONFLICT', server })
  const snapshot = JSON.stringify(conflicted)
  const copiedServer = JSON.parse(JSON.stringify(server))
  copiedServer.template.templateId = 99
  copiedServer.template.versionId = 199
  copiedServer.template.templateCode = 'LEAD-FIRST-CONTACT-COPY'
  const copyDraft = model.buildConflictCopyJourney(conflicted, copiedServer)

  assert.strictEqual(copyDraft.template.templateId, 99)
  assert.strictEqual(copyDraft.definition.owner.config.fallback.value, 'DUTY_MANAGER')
  assert.strictEqual(copyDraft.dirty, true)
  assert.strictEqual(copyDraft.conflict, null)

  const savedCopyAggregate = JSON.parse(JSON.stringify(copiedServer))
  savedCopyAggregate.template.lockVersion = 6
  savedCopyAggregate.steps = savedCopyAggregate.steps.map(step => step.code === 'OWNER'
    ? { ...step, value: copyDraft.steps.find(item => item.code === 'OWNER').value, state: 'COMPLETED' }
    : step)
  const authoritativeCopy = model.mergeSaveResult(copyDraft, savedCopyAggregate)
  assert.strictEqual(authoritativeCopy.template.templateId, 99)
  assert.strictEqual(authoritativeCopy.template.lockVersion, 6)
  assert.strictEqual(authoritativeCopy.definition.owner.config.fallback.value, 'DUTY_MANAGER')
  assert.strictEqual(authoritativeCopy.dirty, false)
  assert.strictEqual(authoritativeCopy.conflict, null)
  assert.strictEqual(JSON.stringify(conflicted), snapshot)
})

check('save-copy preserves independent server fields in identified arrays', () => {
  const base = fixture()
  const nodes = [
    { id: 'a', label: 'Node A', target: 'QUEUE_A' },
    { id: 'b', label: 'Node B', target: 'QUEUE_B' }
  ]
  base.steps = base.steps.map(step => step.code === 'ROUTING'
    ? { ...step, value: { config: { nodes, edges: [] } } }
    : step)
  const local = model.applyStepPatch(model.hydrateJourney(base), 'ROUTING', {
    config: { nodes: [{ ...nodes[0], label: 'Local A' }, nodes[1]], edges: [] }
  })
  const server = fixture()
  server.template.lockVersion = 5
  server.steps = base.steps.map(step => step.code === 'ROUTING'
    ? { ...step, value: { config: { nodes: [nodes[0], { ...nodes[1], label: 'Server B' }], edges: [] } } }
    : step)
  const conflicted = model.mergeSaveResult(local, { status: 'CONFLICT', server })
  const copiedServer = JSON.parse(JSON.stringify(server))
  copiedServer.template.templateId = 99
  copiedServer.template.versionId = 199
  const copyDraft = model.buildConflictCopyJourney(conflicted, copiedServer)

  assert.deepStrictEqual(
    copyDraft.definition.routing.config.nodes.map(node => [node.id, node.label]),
    [['a', 'Local A'], ['b', 'Server B']]
  )
})

check('save-copy preserves a local reorder over an incompatible server reorder', () => {
  const base = fixture()
  const nodes = [
    { id: 'a', label: 'Node A' },
    { id: 'b', label: 'Node B' },
    { id: 'c', label: 'Node C' }
  ]
  base.steps = base.steps.map(step => step.code === 'ROUTING'
    ? { ...step, value: { config: { nodes, edges: [] } } }
    : step)
  const local = model.applyStepPatch(model.hydrateJourney(base), 'ROUTING', {
    config: { nodes: [nodes[1], nodes[0], nodes[2]], edges: [] }
  })
  const server = fixture()
  server.template.lockVersion = 5
  server.steps = base.steps.map(step => step.code === 'ROUTING'
    ? { ...step, value: { config: { nodes: [nodes[0], nodes[2], nodes[1]], edges: [] } } }
    : step)
  const conflicted = model.mergeSaveResult(local, { status: 'CONFLICT', server })
  const copiedServer = JSON.parse(JSON.stringify(server))
  copiedServer.template.templateId = 99
  copiedServer.template.versionId = 199
  const copyDraft = model.buildConflictCopyJourney(conflicted, copiedServer)

  assert.deepStrictEqual(copyDraft.definition.routing.config.nodes.map(node => node.id), ['b', 'a', 'c'])
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

check('derives safe operators and business controls from governed field descriptors', () => {
  const fields = [
    { code: 'amount', name: '合同金额', type: 'number', operators: ['EQ', 'GTE', 'PRESENT'] },
    { code: 'approved', name: '是否通过', type: 'boolean', operators: ['EQ', 'NE', 'PRESENT'] },
    { code: 'signedAt', name: '签署日期', type: 'date', operators: ['EQ', 'GTE'] },
    { code: 'level', name: '客户等级', type: 'string', operators: ['EQ', 'IN'], options: ['A', 'B'] },
    { code: 'remark', name: '备注', type: 'string', operators: ['EQ', 'CONTAINS'] }
  ]

  assert.deepStrictEqual(steps.operatorsForField(fields[0]).map(item => item.value), ['EQ', 'GTE', 'EXISTS'])
  assert.strictEqual(steps.controlForField(fields[0], 'GTE'), 'NUMBER')
  assert.strictEqual(steps.controlForField(fields[1], 'EQ'), 'BOOLEAN')
  assert.strictEqual(steps.controlForField(fields[2], 'EQ'), 'DATE')
  assert.strictEqual(steps.controlForField(fields[3], 'IN'), 'SELECT')
  assert.strictEqual(steps.controlForField(fields[4], 'EQ'), 'TEXT')
  assert.strictEqual(steps.controlForField(fields[4], 'EXISTS'), 'NONE')
  assert.strictEqual(steps.operatorsForField(fields[4]).some(item => item.value === 'CONTAINS'), false)
})

check('enforces two condition-group levels while preserving canonical patch shapes', () => {
  const empty = steps.emptyConditionDocument()
  const withNested = steps.addConditionGroup(empty, [])
  assert.strictEqual(steps.conditionGroupDepth(withNested), 2)
  assert.throws(
    () => steps.addConditionGroup(withNested, [0]),
    error => error && error.code === 'TODO_CONDITION_GROUP_DEPTH_LIMIT'
  )
  const predicate = { field: 'amount', operator: 'GTE', value: 1000 }
  const trigger = steps.buildTriggerPatch(steps.replaceConditionNode(withNested, [0, 0], predicate))

  assert.strictEqual(trigger.condition.$expression.version, 1)
  assert.deepStrictEqual(trigger.condition.$expression.root.conditions[0].conditions[0], predicate)
  assert.deepStrictEqual(steps.buildEventPatch({ eventType: 'CONTRACT_APPROVED', payloadVersion: 3 }), {
    eventType: 'CONTRACT_APPROVED',
    payloadVersion: 3
  })
  assert.deepStrictEqual(steps.buildOwnerPatch({ type: 'BUSINESS_OWNER', skipUnavailable: true }), {
    config: { type: 'BUSINESS_OWNER', skipUnavailable: true }
  })
  assert.deepStrictEqual(
    steps.buildOwnerConfig('ROLE', { value: 'case_manager' }),
    { type: 'ROLE', roleKey: 'case_manager', selectionMode: 'ROLE', skipUnavailable: true, useDelegation: true }
  )
  assert.deepStrictEqual(
    steps.buildOwnerConfig('EVENT_OWNER', { field: 'ownerId' }),
    { type: 'PAYLOAD', field: 'ownerId', selectionMode: 'EVENT_OWNER', skipUnavailable: true, useDelegation: true }
  )
  assert.deepStrictEqual(
    steps.buildOwnerConfig('CANDIDATE_POOL', { value: 'duty_pool' }),
    { type: 'ROLE', roleKey: 'duty_pool', selectionMode: 'CANDIDATE_POOL', skipUnavailable: true, useDelegation: true }
  )

  const pruned = steps.removeConditionNode(withNested, [0, 0])
  assert.deepStrictEqual(steps.buildTriggerPatch(pruned), { condition: {} })
  assert.strictEqual(steps.normalizeConditionDocument({
    $expression: {
      version: 1,
      root: { type: 'AND', conditions: [{ type: 'AND', conditions: [] }] }
    }
  }).supported, false)
})

check('models schema health, owner blockers, and contextual resource return semantics', () => {
  const healthy = steps.eventSchemaHealth(
    { eventType: 'CONTRACT_APPROVED', payloadVersion: 2, schemaStatus: 'READY' },
    [{ code: 'ownerId', sourceEvents: ['CONTRACT_APPROVED'] }]
  )
  assert.strictEqual(healthy.ready, true)
  assert.strictEqual(steps.eventSchemaHealth({ schemaStatus: 'INCOMPLETE' }, []).ready, false)

  assert.strictEqual(steps.ownerBlocker({ type: 'ROLE', roleKey: 'case_manager' }), null)
  assert.strictEqual(steps.ownerBlocker({ type: 'PAYLOAD', field: 'ownerId' }), null)
  assert.strictEqual(steps.ownerBlocker({ type: 'ROLE' }).code, 'TODO_JOURNEY_OWNER_FALLBACK_REQUIRED')
  assert.strictEqual(
    steps.ownerBlocker({ type: 'ROLE', fallback: { type: 'BUSINESS_OWNER' } }),
    null
  )

  const request = steps.createRepairRequest({
    type: 'EVENT',
    eventType: 'CONTRACT_APPROVED',
    payloadVersion: 2,
    returnStep: 'TRIGGER',
    focusField: 'customer.level'
  })
  assert.strictEqual(steps.resourceRepairAccess(['todo:resource:add'], request).allowed, true)
  assert.strictEqual(steps.resourceRepairAccess(['todo:resource:list'], request).allowed, false)
  const draft = { definition: { event: { eventType: 'CONTRACT_APPROVED' } }, dirty: true }
  const completed = steps.completeResourceRepair(request, draft, 91)
  assert.strictEqual(completed.close, true)
  assert.strictEqual(completed.reloadResource, 'EVENT')
  assert.strictEqual(completed.returnStep, 'TRIGGER')
  assert.strictEqual(completed.focusField, 'customer.level')
  assert.strictEqual(completed.draft, draft)

  assert.strictEqual(steps.resourceRepairAccess(['todo:resource:add'], {
    type: 'EVENT',
    resourceId: null
  }).allowed, true)
  assert.strictEqual(steps.resourceRepairAccess(['todo:resource:edit'], {
    type: 'EVENT',
    resourceId: null
  }).allowed, false)
  assert.strictEqual(steps.resourceRepairAccess(['todo:resource:edit'], {
    type: 'EVENT',
    resourceId: 91
  }).allowed, true)
  assert.strictEqual(steps.resourceRepairAccess(['todo:resource:add'], {
    type: 'EVENT',
    resourceId: 91
  }).allowed, false)
  assert.strictEqual(steps.resourceRepairAccess(['todo:calendar:manage'], {
    type: 'CALENDAR'
  }).allowed, true)
  assert.strictEqual(steps.resourceRepairAccess(['todo:resource:add'], {
    type: 'CALENDAR'
  }).allowed, false)

  assert.strictEqual(steps.isOwnerField({ type: 'integer', semanticType: 'USER_ID' }), true)
  assert.strictEqual(steps.isOwnerField({ type: 'number', code: 'ownerId' }), true)
  assert.strictEqual(steps.isOwnerField({ type: 'string', code: 'ownerName' }), false)
  assert.strictEqual(steps.ownerBlocker(
    { type: 'PAYLOAD', field: 'ownerName' },
    [{ code: 'ownerName', type: 'string' }]
  ).code, 'TODO_JOURNEY_OWNER_FALLBACK_REQUIRED')

  const scopedOwners = steps.scopeOwnerFields([
    { code: 'leadOwnerId', type: 'integer', sourceEventVersions: ['LEAD_ASSIGNED@1'] },
    { code: 'contractOwnerId', type: 'integer', sourceEventVersions: ['CONTRACT_APPROVED@2'] },
    { code: 'legacyOwnerId', type: 'integer', sourceEvents: ['CONTRACT_APPROVED'] },
    { code: 'ownerName', type: 'string', sourceEventVersions: ['CONTRACT_APPROVED@2'] }
  ], { eventType: 'CONTRACT_APPROVED', payloadVersion: 2 })
  assert.deepStrictEqual(scopedOwners.map(item => item.code), ['contractOwnerId'])
  assert.strictEqual(steps.ownerSelectionStillValid('contractOwnerId', scopedOwners), true)
  assert.strictEqual(steps.ownerSelectionStillValid('leadOwnerId', scopedOwners), false)

  assert.strictEqual(steps.repairFocusTarget('payloadSchema'), 'schemaRepair')
  assert.strictEqual(steps.repairFocusTarget('eventType'), 'eventSearch')
})

check('ranks and materializes contextual DoD recipes without mutating catalogs or other drafts', () => {
  const recipes = [
    {
      code: 'GENERIC',
      name: '通用完成标准',
      businessType: '',
      businessActions: [],
      templateStages: [],
      recommendationPriority: 99,
      requiredFields: ['remark']
    },
    {
      code: 'LEAD-FIRST-CONTACT',
      name: '首次联系已完成',
      businessType: 'LEAD',
      businessActions: ['LEAD_ASSIGNED'],
      templateStages: ['FIRST_CONTACT'],
      recommendationPriority: 10,
      requiredFields: ['contactedAt', 'contactResult'],
      requiredAttachments: ['CONTACT_NOTE'],
      conditionalRules: [{ field: 'contactResult', when: { field: 'connected', equals: true } }],
      validatorRefs: ['CONTACT_TIME_VALID'],
      employeeInstructions: ['记录联系时间和结果']
    },
    {
      code: 'LEAD-ANY-STAGE',
      name: '线索办理已完成',
      businessType: 'LEAD',
      businessActions: ['LEAD_ASSIGNED'],
      templateStages: [],
      recommendationPriority: 1,
      requiredFields: ['contactResult']
    }
  ]
  const sourceSnapshot = JSON.stringify(recipes)
  const ranked = steps.rankDodRecipes(recipes, {
    businessType: 'LEAD',
    businessAction: 'LEAD_ASSIGNED',
    templateStage: 'FIRST_CONTACT'
  })
  assert.deepStrictEqual(ranked.map(item => item.code), [
    'LEAD-FIRST-CONTACT',
    'LEAD-ANY-STAGE',
    'GENERIC'
  ])

  const otherDraft = { config: { requiredFields: ['untouched'] } }
  const patch = steps.materializeDodRecipe(ranked[0], {
    config: { customAdvancedFlag: true }
  })
  assert.deepStrictEqual(patch.config.requiredFields, ['contactedAt', 'contactResult'])
  assert(!Object.prototype.hasOwnProperty.call(patch.config, 'requiredAttachments'))
  assert(!Object.prototype.hasOwnProperty.call(patch.config, 'conditionalRules'))
  assert.deepStrictEqual(patch.config.conditionalRequired, recipes[1].conditionalRules)
  assert.deepStrictEqual(patch.config.materials, [{ type: 'CONTACT_NOTE', minCount: 1 }])
  assert.deepStrictEqual(patch.config.validatorRefs, ['CONTACT_TIME_VALID'])
  assert.deepStrictEqual(patch.config.employeeInstructions, ['记录联系时间和结果'])
  assert.strictEqual(patch.config.recipeCode, 'LEAD-FIRST-CONTACT')
  assert.strictEqual(patch.config.customAdvancedFlag, true)
  patch.config.requiredFields.push('local-only')
  assert.strictEqual(JSON.stringify(recipes), sourceSnapshot)
  assert.deepStrictEqual(otherDraft, { config: { requiredFields: ['untouched'] } })
})

check('updates only governed DoD collections and projects an employee-visible preview from the live draft', () => {
  const current = {
    config: {
      requiredFields: ['contactedAt'],
      requiredAttachments: ['CONTACT_NOTE'],
      validatorRefs: ['CONTACT_TIME_VALID'],
      conditionalRules: [{ field: 'contactResult', when: { field: 'connected', equals: true } }],
      employeeInstructions: ['填写联系结果'],
      advancedRuntimeFlag: 'preserve'
    }
  }
  const patch = steps.updateGovernedDod(current, {
    requiredFields: ['contactedAt', 'contactResult'],
    requiredAttachments: ['CONTACT_NOTE'],
    conditionalRules: [{ field: 'contactResult', when: { field: 'connected', equals: true } }]
  })
  assert.deepStrictEqual(patch.config.conditionalRequired,
    [{ field: 'contactResult', when: { field: 'connected', equals: true } }])
  assert(!Object.prototype.hasOwnProperty.call(patch.config, 'requiredAttachments'))
  assert(!Object.prototype.hasOwnProperty.call(patch.config, 'conditionalRules'))
  assert.deepStrictEqual(patch.config.materials, [{ type: 'CONTACT_NOTE', minCount: 1 }])
  assert.deepStrictEqual(patch.config.validatorRefs, ['CONTACT_TIME_VALID'])
  assert.deepStrictEqual(patch.config.employeeInstructions, ['填写联系结果'])
  assert.strictEqual(patch.config.advancedRuntimeFlag, 'preserve')

  const preview = steps.projectEmployeePreview({
    title: '首次联系',
    assigneeSummary: '线索负责人',
    fields: [{ code: 'serverOnly', label: '旧字段', required: true }],
    materials: [{ code: 'OLD', label: '旧材料', required: true }],
    completionInstructions: ['旧说明']
  }, patch.config, {
    fields: [
      { code: 'contactedAt', name: '联系时间', type: 'datetime' },
      { code: 'contactResult', name: '联系结果', type: 'string' }
    ],
    materials: [{ code: 'CONTACT_NOTE', name: '联系记录' }]
  })
  assert.deepStrictEqual(preview.fields.map(item => item.label), ['联系时间', '联系结果'])
  assert.strictEqual(preview.fields[1].conditional, true)
  assert.deepStrictEqual(preview.materials.map(item => item.label), ['联系记录'])
  assert.deepStrictEqual(preview.completionInstructions, ['填写联系结果'])
})

check('round-trips canonical DoD evidence without losing governed metadata', () => {
  const conditional = {
    field: 'contactResult',
    when: { field: 'contactRequired', equals: true },
    message: 'Contact result is required'
  }
  const material = {
    type: 'CONTACT_NOTE',
    minCount: 2,
    maxCount: 4,
    label: 'Contact note',
    retentionPolicy: 'CASE_FILE'
  }
  const current = {
    config: {
      requiredFields: ['contactedAt'],
      materials: [material],
      conditionalRequired: [conditional],
      validatorRefs: ['CONTACT_TIME_VALID']
    }
  }

  const patch = steps.updateGovernedDod(current, {
    requiredFields: ['contactedAt', 'contactResult'],
    requiredAttachments: ['CONTACT_NOTE'],
    conditionalRules: [conditional]
  })

  assert.deepStrictEqual(patch.config.requiredFields, ['contactedAt', 'contactResult'])
  assert.deepStrictEqual(patch.config.materials, [material])
  assert.deepStrictEqual(patch.config.conditionalRequired, [conditional])
  assert(!Object.prototype.hasOwnProperty.call(patch.config, 'requiredAttachments'))
  assert(!Object.prototype.hasOwnProperty.call(patch.config, 'conditionalRules'))

  const preview = steps.projectEmployeePreview({}, patch.config, {
    fields: [
      { code: 'contactedAt', name: 'Contact time', type: 'datetime' },
      { code: 'contactResult', name: 'Contact result', type: 'string' }
    ],
    materials: [{ code: 'CONTACT_NOTE', name: 'Contact note' }]
  })
  assert.deepStrictEqual(preview.fields.map(item => [item.code, item.required, item.conditional]), [
    ['contactedAt', true, false],
    ['contactResult', true, true]
  ])
  assert.deepStrictEqual(preview.materials.map(item => item.code), ['CONTACT_NOTE'])
})

check('builds natural-language SLA patches and four semantic timeline points with repair blockers', () => {
  const patch = steps.buildSlaPatch({
    durationValue: 2,
    durationUnit: 'HOUR',
    calendarCode: 'DEFAULT',
    startStrategy: 'TODO_ACCEPTED',
    pauseRules: ['WAIT_CUSTOMER']
  }, { config: { advancedPolicy: { timezoneLock: true } } })
  assert.strictEqual(patch.config.minutes, 120)
  assert.strictEqual(patch.config.calendarCode, 'DEFAULT')
  assert.strictEqual(patch.config.startStrategy, 'TODO_CREATED')
  assert.deepStrictEqual(patch.config.pauseRules, [])
  assert.deepStrictEqual(patch.config.advancedPolicy, { timezoneLock: true })
  assert.strictEqual(steps.buildSlaPatch({
    durationValue: 1,
    durationUnit: 'DAY',
    calendarCode: 'DEFAULT'
  }, {}).config.minutes, null)
  assert.strictEqual(steps.buildSlaPatch({
    durationValue: 1,
    durationUnit: 'DAY',
    calendarCode: 'DEFAULT',
    governedMinutes: 540
  }, {}).config.minutes, 540)

  const timeline = steps.buildSlaTimeline(patch.config, {
    startAt: '2026-07-24T09:00:00',
    remind80At: '2026-07-24T10:36:00',
    overdue100At: '2026-07-24T11:00:00',
    escalate150At: '2026-07-24T12:00:00'
  })
  assert.deepStrictEqual(timeline.map(item => item.percent), [0, 80, 100, 150])
  assert.deepStrictEqual(timeline.map(item => item.semantic), ['CREATED', 'REMINDER', 'OVERDUE', 'ESCALATION'])
  assert.deepStrictEqual(timeline[1].actions, ['REMIND_OWNER'])
  assert.deepStrictEqual(timeline[2].actions, ['MARK_OVERDUE', 'REMIND_OWNER'])
  assert.deepStrictEqual(timeline[3].actions, ['ESCALATE', 'REMIND_OWNER', 'NOTIFY_MANAGER'])

  assert.strictEqual(steps.slaRepairBlocker(patch.config, [{ calendarCode: 'DEFAULT' }], timeline), null)
  assert.strictEqual(
    steps.slaRepairBlocker({ ...patch.config, calendarCode: 'MISSING' }, [{ calendarCode: 'DEFAULT' }], timeline).code,
    'TODO_JOURNEY_SLA_CALENDAR_REQUIRED'
  )
  assert.strictEqual(
    steps.slaRepairBlocker(patch.config, [{ calendarCode: 'DEFAULT' }], []).code,
    'TODO_JOURNEY_SLA_CALCULATION_REQUIRED'
  )
  const request = steps.createRepairRequest({
    type: 'CALENDAR',
    businessType: 'LEAD',
    returnStep: 'SLA',
    focusField: 'calendarCode'
  })
  assert.deepStrictEqual(request, {
    type: 'CALENDAR',
    eventType: null,
    payloadVersion: null,
    resourceId: null,
    businessType: 'LEAD',
    returnStep: 'SLA',
    focusField: 'calendarCode'
  })
})

check('builds ordered terminal and parallel routing graphs while preserving advanced configuration', () => {
  const current = { config: { advancedGraphOption: 'preserve' } }
  const sequential = steps.buildBusinessRoutingPatch([
    { id: 'approved', label: '审批通过', resultType: 'NEXT', targetVersionId: 201, default: false, condition: { status: 'APPROVED' } },
    { id: 'rejected', label: '审批拒绝', resultType: 'END', default: true }
  ], {
    mode: 'SEQUENTIAL',
    currentVersionId: 101
  }, current)
  assert.strictEqual(sequential.config.start, 'current_task')
  assert.deepStrictEqual(sequential.config.businessOutcomes.map(item => item.label), ['审批通过', '审批拒绝'])
  assert(sequential.config.nodes.some(node => node.type === 'DECISION'))
  assert(sequential.config.nodes.some(node => node.type === 'END'))
  assert.strictEqual(sequential.config.advancedGraphOption, 'preserve')
  const decisionEdges = sequential.config.edges.filter(edge => edge.from === 'business_result')
  assert(decisionEdges[0].priority > decisionEdges[1].priority, 'top outcome must execute first at runtime')

  const terminal = steps.buildBusinessRoutingPatch([], {
    mode: 'SEQUENTIAL',
    currentVersionId: 101
  }, current)
  assert(terminal.config.nodes.some(node => node.type === 'END'))
  assert(terminal.config.edges.some(edge => edge.from === 'current_task' && edge.to === 'route_end'))

  assert.strictEqual(
    steps.routingDraftBlocker([
      { id: 'missing', label: '继续办理', resultType: 'NEXT', targetVersionId: null }
    ], { mode: 'SEQUENTIAL' }).code,
    'TODO_JOURNEY_ROUTING_TARGET_REQUIRED'
  )
  assert.strictEqual(
    steps.routingDraftBlocker([
      { id: 'only', label: '唯一分支', resultType: 'NEXT', targetVersionId: 201 }
    ], { mode: 'PARALLEL' }).code,
    'TODO_JOURNEY_ROUTING_PARALLEL_BRANCHES_REQUIRED'
  )

  const parallel = steps.buildBusinessRoutingPatch([
    { id: 'finance', label: '财务确认', resultType: 'NEXT', targetVersionId: 301 },
    { id: 'archive', label: '归档检查', resultType: 'NEXT', targetVersionId: 302 }
  ], {
    mode: 'PARALLEL',
    joinMode: 'ALL',
    currentVersionId: 101
  }, current)
  assert(parallel.config.nodes.some(node => node.type === 'FORK'))
  assert(parallel.config.nodes.some(node => node.type === 'JOIN' && node.joinMode === 'ALL'))
  assert.deepStrictEqual(parallel.config.businessRouting, { mode: 'PARALLEL', joinMode: 'ALL' })
  assert.deepStrictEqual(parallel.config.businessOutcomes.map(item => item.label), ['财务确认', '归档检查'])
  assert.strictEqual(
    parallel.config.edges.filter(edge => edge.from === 'parallel_start').some(edge => edge.condition),
    false
  )
})

console.log(`todo phase two journey model contract passed (${checks} checks)`)
