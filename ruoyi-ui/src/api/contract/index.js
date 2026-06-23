import request from '@/utils/request'

export function getContractDashboard() { return request({ url: '/contract/dashboard', method: 'get' }) }
export function listContractOwner() { return request({ url: '/contract/owner/options', method: 'get' }) }
export function listContract(query) { return request({ url: '/contract/list', method: 'get', params: query }) }
export function getContract(contractId) { return request({ url: '/contract/' + contractId, method: 'get' }) }
export function addContract(data) { return request({ url: '/contract', method: 'post', data }) }
export function updateContract(data) { return request({ url: '/contract', method: 'put', data }) }
export function delContract(contractIds) { return request({ url: '/contract/' + contractIds, method: 'delete' }) }
export function submitContract(contractId) { return request({ url: '/contract/submit/' + contractId, method: 'post' }) }
export function approvalContract(data) { return request({ url: '/contract/approval', method: 'post', data }) }
export function signContract(data) { return request({ url: '/contract/sign', method: 'post', data }) }
export function archiveContract(data) { return request({ url: '/contract/archive', method: 'post', data }) }
export function voidContract(data) { return request({ url: '/contract/void', method: 'post', data }) }
export function terminateContract(data) { return request({ url: '/contract/terminate', method: 'post', data }) }
export function listRule() { return request({ url: '/contract/rule/list', method: 'get' }) }
export function updateRule(data) { return request({ url: '/contract/rule', method: 'put', data }) }
export function listTemplate(query) { return request({ url: '/contract/template/list', method: 'get', params: query }) }
export function addTemplate(data) { return request({ url: '/contract/template', method: 'post', data }) }
export function updateTemplate(data) { return request({ url: '/contract/template', method: 'put', data }) }
export function delTemplate(templateId) { return request({ url: '/contract/template/' + templateId, method: 'delete' }) }
export function listApproval(query) { return request({ url: '/contract/approval/list', method: 'get', params: query }) }
export function listFee(query) { return request({ url: '/contract/fee/list', method: 'get', params: query }) }
export function addFee(data) { return request({ url: '/contract/fee', method: 'post', data }) }
export function updateFee(data) { return request({ url: '/contract/fee', method: 'put', data }) }
export function delFee(planId) { return request({ url: '/contract/fee/' + planId, method: 'delete' }) }
export function confirmFee(data) { return request({ url: '/contract/fee/confirm', method: 'post', data }) }
export function rejectFee(data) { return request({ url: '/contract/fee/reject', method: 'post', data }) }
export function invoiceFee(data) { return request({ url: '/contract/fee/invoice', method: 'post', data }) }
export function listAttachment(query) { return request({ url: '/contract/attachment/list', method: 'get', params: query }) }
export function addAttachment(data) { return request({ url: '/contract/attachment', method: 'post', data }) }
export function delAttachment(attachmentId) { return request({ url: '/contract/attachment/' + attachmentId, method: 'delete' }) }
export function listStatus(query) { return request({ url: '/contract/status/list', method: 'get', params: query }) }
