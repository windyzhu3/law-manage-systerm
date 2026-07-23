const JOURNEY_READ_PERMISSIONS = [
  'todo:template:list',
  'todo:template:edit',
  'todo:simulation:simulate',
  'todo:release:publish'
]
const DETAIL_READ_PERMISSIONS = [
  'todo:template:list',
  'todo:template:create',
  'todo:template:copy',
  'todo:template:edit',
  'todo:simulation:list',
  'todo:release:publish'
]
const DRAFT_WRITE_PERMISSIONS = [
  'todo:template:edit',
  'todo:template:create',
  'todo:template:copy'
]

function clone(value) {
  return value == null ? value : JSON.parse(JSON.stringify(value))
}

function hasAny(granted, required) {
  const permissions = Array.isArray(granted) ? granted : []
  return permissions.includes('*:*:*') || required.some(permission => permissions.includes(permission))
}

function resolveJourneyCapabilities(permissions) {
  const granted = Array.isArray(permissions) ? permissions.slice() : []
  const canLoadJourney = hasAny(granted, JOURNEY_READ_PERMISSIONS)
  const canReadDraftDetail = hasAny(granted, DETAIL_READ_PERMISSIONS)
  const canWriteDraftEndpoint = hasAny(granted, DRAFT_WRITE_PERMISSIONS)
  return {
    canLoadJourney,
    canReadDraftDetail,
    canWriteDraftEndpoint,
    canSaveDraft: canLoadJourney && canReadDraftDetail && canWriteDraftEndpoint,
    canCopyTemplate: granted.includes('*:*:*') || granted.includes('todo:template:copy')
  }
}

function snapshotReadPlan(capabilities) {
  const value = capabilities || {}
  if (!value.canLoadJourney) return []
  return value.canSaveDraft ? ['DETAIL', 'JOURNEY', 'DETAIL'] : ['JOURNEY']
}

function normalizedRouteContext(route) {
  const query = clone((route && route.query) || {})
  delete query.step
  return Object.keys(query).sort().reduce((result, key) => {
    const value = query[key]
    result[key] = Array.isArray(value) ? value.map(String) : String(value == null ? '' : value)
    return result
  }, {})
}

function routeContextChanged(from, to) {
  return JSON.stringify(normalizedRouteContext(from)) !== JSON.stringify(normalizedRouteContext(to))
}

function createCopyTransition(templateId, journey, context) {
  const id = Number(templateId)
  if (!Number.isFinite(id) || id <= 0) throw new Error('Copied template id is required')
  if (!journey || Number(journey.template && journey.template.templateId) !== id) {
    throw new Error('Authoritative copied journey does not match the target template')
  }
  return {
    templateId: id,
    journey: clone(journey),
    context: clone(context || null)
  }
}

function copyTransitionMatches(transition, templateId) {
  return Boolean(transition && Number(transition.templateId) === Number(templateId))
}

function consumeCopyTransition(transition, templateId) {
  if (!copyTransitionMatches(transition, templateId)) return null
  return {
    journey: clone(transition.journey),
    context: clone(transition.context)
  }
}

module.exports = {
  resolveJourneyCapabilities,
  snapshotReadPlan,
  routeContextChanged,
  createCopyTransition,
  copyTransitionMatches,
  consumeCopyTransition
}
