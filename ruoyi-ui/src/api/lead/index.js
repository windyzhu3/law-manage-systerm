import request from '@/utils/request'

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
