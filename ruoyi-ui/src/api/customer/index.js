import request from '@/utils/request'

/**
 * @typedef {Object} CustomerContactCommand
 * @property {number} customerId
 * @property {string} contactName
 * @property {string} mobile
 * @property {string} relationType
 */

/**
 * @typedef {Object} CustomerMergeCommand
 * @property {number} mainCustomerId
 * @property {number} mergedCustomerId
 * @property {string=} content
 */

export function getCustomerDashboard() { return request({ url: '/customer/dashboard', method: 'get' }) }
export function listCustomerOwner() { return request({ url: '/customer/owner/options', method: 'get' }) }
export function listCustomer(query) { return request({ url: '/customer/list', method: 'get', params: query }) }
export function getCustomer(customerId) { return request({ url: '/customer/' + customerId, method: 'get' }) }
export function addCustomer(data) { return request({ url: '/customer', method: 'post', data }) }
export function updateCustomer(data) { return request({ url: '/customer', method: 'put', data }) }
export function delCustomer(customerIds) { return request({ url: '/customer/' + customerIds, method: 'delete' }) }
export function listContact(query) { return request({ url: '/customer/contact/list', method: 'get', params: query }) }
/** @param {CustomerContactCommand} data */
export function addContact(data) { return request({ url: '/customer/contact', method: 'post', data }) }
export function updateContact(data) { return request({ url: '/customer/contact', method: 'put', data }) }
export function delContact(contactId) { return request({ url: '/customer/contact/' + contactId, method: 'delete' }) }
export function listFollowup(query) { return request({ url: '/customer/followup/list', method: 'get', params: query }) }
export function addFollowup(data) { return request({ url: '/customer/followup', method: 'post', data }) }
export function delFollowup(followupId) { return request({ url: '/customer/followup/' + followupId, method: 'delete' }) }
export function listTag(query) { return request({ url: '/customer/tag/list', method: 'get', params: query }) }
export function addTag(data) { return request({ url: '/customer/tag', method: 'post', data }) }
export function updateTag(data) { return request({ url: '/customer/tag', method: 'put', data }) }
export function delTag(tagId) { return request({ url: '/customer/tag/' + tagId, method: 'delete' }) }
export function getCustomerTags(customerId) { return request({ url: '/customer/' + customerId + '/tags', method: 'get' }) }
export function setCustomerTags(customerId, data) { return request({ url: '/customer/' + customerId + '/tags', method: 'post', data }) }
export function listMergeCandidates(query) { return request({ url: '/customer/merge/candidates', method: 'get', params: query }) }
export function listMergeLogs(query) { return request({ url: '/customer/merge/logs', method: 'get', params: query }) }
/** @param {CustomerMergeCommand} data */
export function mergeCustomer(data) { return request({ url: '/customer/merge', method: 'post', data }) }
