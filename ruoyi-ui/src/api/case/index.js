import request from '@/utils/request'

export function getCaseDashboard() { return request({ url: '/case/dashboard', method: 'get' }) }
export function listCaseLawyer() { return request({ url: '/case/lawyer/options', method: 'get' }) }
export function listCase(query) { return request({ url: '/case/list', method: 'get', params: query }) }
export function getCase(caseId) { return request({ url: '/case/' + caseId, method: 'get' }) }
export function assignCase(data) { return request({ url: '/case/assign', method: 'post', data }) }
export function batchAssignCase(data) { return request({ url: '/case/assign/batch', method: 'post', data }) }
export function listAssignment(query) { return request({ url: '/case/assign/list', method: 'get', params: query }) }
export function listLawyerLoad(query) { return request({ url: '/case/lawyer/load', method: 'get', params: query }) }
export function listLawyerSpecialty(query) { return request({ url: '/case/lawyer/specialty', method: 'get', params: query }) }
export function listLawyerProfile(query) { return request({ url: '/case/lawyer/profile/list', method: 'get', params: query }) }
export function getLawyerProfile(userId) { return request({ url: '/case/lawyer/profile/' + userId, method: 'get' }) }
export function addLawyerProfile(data) { return request({ url: '/case/lawyer/profile', method: 'post', data }) }
export function updateLawyerProfile(data) { return request({ url: '/case/lawyer/profile', method: 'put', data }) }
export function updateLawyerProfileStatus(data) { return request({ url: '/case/lawyer/profile/status', method: 'put', data }) }
export function listTransfer(query) { return request({ url: '/case/transfer/list', method: 'get', params: query }) }
export function requestTransfer(data) { return request({ url: '/case/transfer', method: 'post', data }) }
export function approveTransfer(data) { return request({ url: '/case/transfer/approve', method: 'post', data }) }
export function listConfirm(query) { return request({ url: '/case/confirm/list', method: 'get', params: query }) }
export function handleConfirm(data) { return request({ url: '/case/confirm', method: 'put', data }) }
export function listCaseStatus(query) { return request({ url: '/case/status/list', method: 'get', params: query }) }
