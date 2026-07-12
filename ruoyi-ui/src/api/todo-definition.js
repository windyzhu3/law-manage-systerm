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
export function saveTriggerRule(data) { return request({ url: '/todo/template/trigger', method: 'post', data }) }
export function listWorkCalendars() { return request({ url: '/todo/calendar', method: 'get' }) }
export function saveWorkCalendar(data) { return request({ url: '/todo/calendar', method: data.calendarId ? 'put' : 'post', data }) }
