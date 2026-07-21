import request from '@/utils/request'

export function getTodoConfigDashboard() { return request({ url: '/todo/config/dashboard', method: 'get' }) }

export function listSlaRules(params) { return request({ url: '/todo/config/sla-rules', method: 'get', params }) }
export function getSlaRule(id) { return request({ url: `/todo/config/sla-rules/${id}`, method: 'get' }) }
export function createSlaRule(data) { return request({ url: '/todo/config/sla-rules', method: 'post', data }) }
export function updateSlaRule(id, data) { return request({ url: `/todo/config/sla-rules/${id}`, method: 'put', data }) }
export function copySlaRule(id, data) { return request({ url: `/todo/config/sla-rules/${id}/copy`, method: 'post', data }) }
export function toggleSlaRule(id, data) { return request({ url: `/todo/config/sla-rules/${id}/toggle`, method: 'post', data }) }
export function testSlaRule(id, data) { return request({ url: `/todo/config/sla-rules/${id}/test`, method: 'post', data }) }

export function listDodRules(params) { return request({ url: '/todo/config/dod-rules', method: 'get', params }) }
export function getDodRule(id) { return request({ url: `/todo/config/dod-rules/${id}`, method: 'get' }) }
export function createDodRule(data) { return request({ url: '/todo/config/dod-rules', method: 'post', data }) }
export function updateDodRule(id, data) { return request({ url: `/todo/config/dod-rules/${id}`, method: 'put', data }) }
export function copyDodRule(id, data) { return request({ url: `/todo/config/dod-rules/${id}/copy`, method: 'post', data }) }
export function toggleDodRule(id, data) { return request({ url: `/todo/config/dod-rules/${id}/toggle`, method: 'post', data }) }
export function getDodRuleReferenceCount(id) { return request({ url: `/todo/config/dod-rules/${id}/reference-count`, method: 'get' }) }
export function testDodRule(id, data) { return request({ url: `/todo/config/dod-rules/${id}/test`, method: 'post', data }) }

export function listTodoTemplates(params) { return request({ url: '/todo/config/templates', method: 'get', params }) }
export function getTodoTemplate(id) { return request({ url: `/todo/config/templates/${id}`, method: 'get' }) }
export function createTodoTemplate(data) { return request({ url: '/todo/config/templates', method: 'post', data }) }
export function updateTodoTemplate(id, data) { return request({ url: `/todo/config/templates/${id}`, method: 'put', data }) }
export function copyTodoTemplate(id, data) { return request({ url: `/todo/config/templates/${id}/copy`, method: 'post', data }) }
export function importTodoTemplate(data) { return request({ url: '/todo/config/templates/import', method: 'post', data }) }
export function toggleTodoTemplate(id, data) { return request({ url: `/todo/config/templates/${id}/toggle`, method: 'post', data }) }
export function listTemplateVersions(id) { return request({ url: `/todo/config/templates/${id}/versions`, method: 'get' }) }
export function updateTemplateDraft(id, data) { return request({ url: `/todo/config/template-versions/${id}`, method: 'put', data }) }
export function preflightTemplateDraft(id) { return request({ url: `/todo/config/template-versions/${id}/preflight`, method: 'post' }) }
export function listTemplateEventCatalog() { return request({ url: '/todo/config/template-catalog/events', method: 'get' }) }
export function listTemplateOwnerCatalog() { return request({ url: '/todo/config/template-catalog/owners', method: 'get' }) }
export function listTemplateHandlerCatalog() { return request({ url: '/todo/config/template-catalog/handlers', method: 'get' }) }
export function listTemplateValidatorCatalog() { return request({ url: '/todo/config/template-catalog/validators', method: 'get' }) }
export function listTemplateAutoActionCatalog() { return request({ url: '/todo/config/template-catalog/auto-actions', method: 'get' }) }
export function listTemplateRoutingTargetCatalog() { return request({ url: '/todo/config/template-catalog/routing-targets', method: 'get' }) }
export function listTemplateSlaRuleCatalog() { return request({ url: '/todo/config/template-catalog/sla-rules', method: 'get' }) }
export function listTemplateDodRuleCatalog() { return request({ url: '/todo/config/template-catalog/dod-rules', method: 'get' }) }

export function listTriggerRules(params) { return request({ url: '/todo/config/trigger-rules', method: 'get', params }) }
export function createTriggerRule(data) { return request({ url: '/todo/config/trigger-rules', method: 'post', data }) }
export function updateTriggerRule(id, data) { return request({ url: `/todo/config/trigger-rules/${id}`, method: 'put', data }) }
export function toggleTriggerRule(id, data) { return request({ url: `/todo/config/trigger-rules/${id}/toggle`, method: 'post', data }) }
export function sortTriggerRules(data) { return request({ url: '/todo/config/trigger-rules/sort', method: 'post', data }) }
export function simulateTriggerRule(data) { return request({ url: '/todo/config/trigger-rules/simulate', method: 'post', data }) }
export function listTriggerEventCatalog() { return request({ url: '/todo/config/trigger-catalog/events', method: 'get' }) }
export function listTriggerTemplateCatalog() { return request({ url: '/todo/config/trigger-catalog/templates', method: 'get' }) }
export function listTriggerTemplateVersions(id) { return request({ url: `/todo/config/trigger-catalog/templates/${id}/versions`, method: 'get' }) }

export function simulateConfiguration(data) { return request({ url: '/todo/config/simulations', method: 'post', data }) }

export function listReleaseRecords(params) { return request({ url: '/todo/config/release-records', method: 'get', params }) }
export function getReleaseRecord(id) { return request({ url: `/todo/config/release-records/${id}`, method: 'get' }) }
export function diffReleaseRecords(left, right) { return request({ url: `/todo/config/release-records/${left}/diff/${right}`, method: 'get' }) }
export function publishReleaseRecord(id, data) { return request({ url: `/todo/config/release-records/${id}/publish`, method: 'post', data }) }
export function rollbackReleaseDraft(id, data) { return request({ url: `/todo/config/release-records/${id}/rollback-draft`, method: 'post', data }) }
