import request from '@/utils/request'
export function listDefinitions() { return request({ url: '/todo/template', method: 'get' }) }
export function createDefinition(data) { return request({ url: '/todo/template', method: 'post', data }) }
export function updateDefinition(data) { return request({ url: '/todo/template', method: 'put', data }) }
export function listDefinitionVersions(id) { return request({ url: `/todo/template/${id}/versions`, method: 'get' }) }
export function copyDefinition(id, data) { return request({ url: `/todo/template/${id}/copy`, method: 'post', data }) }
export function copyDefinitionVersion(id, version, data) { return request({ url: `/todo/template/${id}/version/${version}/copy`, method: 'post', data }) }
export function updateDefinitionDraft(versionId, data) { return request({ url: `/todo/template/version/${versionId}`, method: 'put', data }) }
export function publishDefinition(versionId, data) { return request({ url: `/todo/template/version/${versionId}/publish`, method: 'post', data }) }
export function listTriggerRules() { return request({ url: '/todo/template/trigger', method: 'get' }) }
export function saveTriggerRule(data) { return request({ url: '/todo/template/trigger', method: data.triggerRuleId ? 'put' : 'post', data }) }
export function listWorkCalendars() { return request({ url: '/todo/calendar', method: 'get' }) }
export function saveWorkCalendar(data) { return request({ url: '/todo/calendar', method: data.calendarId ? 'put' : 'post', data }) }
export function preflightDefinition(versionId) { return request({ url: `/todo/definitions/version/${versionId}/preflight`, method: 'post' }) }
export function buildVirtualTaskCompletion(nodeKey, occurrence, payload, completedAt) {
  if (!nodeKey || !Number.isInteger(occurrence) || occurrence < 0 || payload == null || !completedAt) {
    throw new Error('nodeKey, non-negative route occurrence, payload and completedAt are required')
  }
  return { nodeKey, occurrence, payload: { ...payload }, completedAt }
}
export function simulateDefinition(versionId, data) {
  const taskCompletions = (data.taskCompletions || []).map(sample =>
    buildVirtualTaskCompletion(sample.nodeKey, sample.occurrence, sample.payload, sample.completedAt))
  return request({ url: `/todo/definitions/version/${versionId}/simulate`, method: 'post', data: { ...data, taskCompletions } })
}
export function rollbackDefinitionDraft(versionId, data) { return request({ url: `/todo/definitions/version/${versionId}/rollback-draft`, method: 'post', data }) }
export function diffDefinitionVersions(leftVersionId, rightVersionId) { return request({ url: `/todo/definitions/versions/${leftVersionId}/diff/${rightVersionId}`, method: 'get' }) }
export function listTodoEventCatalog() { return request({ url: '/todo/event-catalog', method: 'get' }) }
export function listTodoHandlerCatalog() { return request({ url: '/todo/handler-catalog', method: 'get' }) }
export function listTodoValidatorCatalog() { return request({ url: '/todo/validator-catalog', method: 'get' }) }
export function listTodoAutoActionCapabilities() { return request({ url: '/todo/auto-action-capabilities', method: 'get' }) }
export function listTodoDecisions() { return request({ url: '/todo/decisions', method: 'get' }) }
export function getDecisionGovernanceOptions() { return request({ url: '/todo/decision-governance-options', method: 'get' }) }
export function createTodoDecision(data) { return request({ url: '/todo/decisions', method: 'post', data }) }
export function updateTodoDecision(decisionId, data) { return request({ url: `/todo/decisions/${decisionId}`, method: 'put', data }) }
export function listAdmissionEvidence() { return request({ url: '/todo/admission-evidence', method: 'get' }) }
export function getAdmissionEvidenceGovernanceOptions() { return request({ url: '/todo/admission-evidence/governance-options', method: 'get' }) }
export function updateAdmissionEvidence(evidenceId, data) { return request({ url: `/todo/admission-evidence/${evidenceId}`, method: 'put', data }) }
export function getFoundationResourceReadiness(gateCode = 'G-02') { return request({ url: '/todo/foundation-resources', method: 'get', params: { gateCode } }) }
