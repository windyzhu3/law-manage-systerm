import request from '@/utils/request'

/**
 * @typedef {Object} LeadAssignCommand
 * @property {number} leadId
 * @property {number} ownerId
 * @property {string=} reason
 */

/**
 * @typedef {Object} LeadFollowupCommand
 * @property {number} leadId
 * @property {string} followType
 * @property {string} followResult
 * @property {string} content
 * @property {string=} nextFollowTime
 */

export function getDashboard() {
  return request({ url: '/lead/dashboard', method: 'get' })
}

export function listLeadOwner() {
  return request({ url: '/lead/owner/options', method: 'get' })
}

export function listLead(query) {
  return request({ url: '/lead/list', method: 'get', params: query })
}

export function getLead(leadId) {
  return request({ url: '/lead/' + leadId, method: 'get' })
}

export function addLead(data) {
  return request({ url: '/lead', method: 'post', data })
}

export function updateLead(data) {
  return request({ url: '/lead', method: 'put', data })
}

export function delLead(leadIds) {
  return request({ url: '/lead/' + leadIds, method: 'delete' })
}

export function restoreLead(leadIds) {
  return request({ url: '/lead/restore/' + leadIds, method: 'post' })
}

export function purgeLead(leadIds) {
  return request({ url: '/lead/purge/' + leadIds, method: 'delete' })
}

/** @param {LeadAssignCommand} data */
export function assignLead(data) {
  return request({ url: '/lead/assign', method: 'post', data })
}

export function moveLeadToPool(data) {
  return request({ url: '/lead/pool', method: 'post', data })
}

export function claimLead(leadId) {
  return request({ url: '/lead/claim/' + leadId, method: 'post' })
}

export function convertLead(leadId) {
  return request({ url: '/lead/convert/' + leadId, method: 'post' })
}

export function listFollowup(query) {
  return request({ url: '/lead/followup/list', method: 'get', params: query })
}

/** @param {LeadFollowupCommand} data */
export function addFollowup(data) {
  return request({ url: '/lead/followup', method: 'post', data })
}

export function delFollowup(followupId) {
  return request({ url: '/lead/followup/' + followupId, method: 'delete' })
}

export function listSetting(query) {
  return request({ url: '/lead/setting/list', method: 'get', params: query })
}

export function addSetting(data) {
  return request({ url: '/lead/setting', method: 'post', data })
}

export function updateSetting(data) {
  return request({ url: '/lead/setting', method: 'put', data })
}

export function delSetting(settingId) {
  return request({ url: '/lead/setting/' + settingId, method: 'delete' })
}

export function confirmLeadTag(data) {
  return request({ url: '/lead/tag/confirm', method: 'post', data })
}

export function listLeadCallRecords(leadId) {
  return request({ url: `/lead/${leadId}/call-records`, method: 'get' })
}

export function addLeadCallRecord(leadId, data) {
  return request({ url: `/lead/${leadId}/call-records`, method: 'post', data })
}

export function listInvalidReview(query) {
  return request({ url: '/lead/invalid-review/list', method: 'get', params: query })
}

export function completeInvalidReview(todoId, data) {
  return request({ url: `/lead/invalid-review/${todoId}/complete`, method: 'post', data })
}

export function listLeadRetry(query) {
  return request({ url: '/lead/retry/list', method: 'get', params: query })
}

export function getLeadRetryTimeline(leadId) {
  return request({ url: `/lead/${leadId}/retry-timeline`, method: 'get' })
}

export function listDeadPool(query) {
  return request({ url: '/lead/dead-pool/list', method: 'get', params: query })
}

export function restoreDeadPoolLead(leadId, data) {
  return request({ url: `/lead/dead-pool/${leadId}/restore`, method: 'post', data })
}

export function listAssignmentPolicy() {
  return request({ url: '/lead/assignment-policy', method: 'get' })
}

export function saveAssignmentPolicy(data) {
  return request({ url: '/lead/assignment-policy', method: 'put', data })
}
