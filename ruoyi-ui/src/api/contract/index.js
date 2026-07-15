import request from '@/utils/request'

export function getContractDashboard() { return request({ url: '/contract/dashboard', method: 'get' }) }
export function listContractOwner() { return request({ url: '/contract/owner/options', method: 'get' }) }
export function listContract(query) { return request({ url: '/contract/list', method: 'get', params: query }) }
export function getContract(contractId) { return request({ url: '/contract/' + contractId, method: 'get' }) }
/** @param {{contractName:string, customerId:number, caseType:string, signAmount:number, lawyerId?:number, ownerId?:number, deptId?:number, feeType?:string, signDate?:string, effectiveDate?:string, expireDate?:string, signMethod?:string, riskLevel?:string, remark?:string}} data */
export function addContract(data) { return request({ url: '/contract', method: 'post', data }) }
/** @param {{contractId:number, contractName:string, customerId:number, caseType:string, signAmount:number, lawyerId?:number, ownerId?:number, deptId?:number, feeType?:string, signDate?:string, effectiveDate?:string, expireDate?:string, signMethod?:string, riskLevel?:string, remark?:string}} data */
export function updateContract(data) { return request({ url: '/contract', method: 'put', data }) }
export function delContract(contractIds) { return request({ url: '/contract/' + contractIds, method: 'delete' }) }
export function submitContract(contractId) { return request({ url: '/contract/submit/' + contractId, method: 'post' }) }
export function approvalContract(data) { return request({ url: '/contract/approval', method: 'post', data }) }
export function signContract(data) { return request({ url: '/contract/sign', method: 'post', data }) }
export function archiveContract(data) { return request({ url: '/contract/archive', method: 'post', data }) }
export function voidContract(data) { return request({ url: '/contract/void', method: 'post', data }) }
export function terminateContract(data) { return request({ url: '/contract/terminate', method: 'post', data }) }
export function listRule() { return request({ url: '/contract/rule/list', method: 'get' }) }
/** @param {{ruleId:number, ruleName:string, prefix:string, datePattern:string, serialLength:number, status:'0'|'1', remark?:string}} data */
export function updateRule(data) { return request({ url: '/contract/rule', method: 'put', data }) }
export function listTemplate(query) { return request({ url: '/contract/template/list', method: 'get', params: query }) }
/** @param {{templateName:string, caseType:string, fileName:string, fileUrl:string, versionNo?:string, status?:'0'|'1', remark?:string}} data */
export function addTemplate(data) { return request({ url: '/contract/template', method: 'post', data }) }
/** @param {{templateId:number, templateName:string, caseType:string, fileName:string, fileUrl:string, versionNo?:string, status?:'0'|'1', remark?:string}} data */
export function updateTemplate(data) { return request({ url: '/contract/template', method: 'put', data }) }
export function delTemplate(templateId) { return request({ url: '/contract/template/' + templateId, method: 'delete' }) }
export function listApproval(query) { return request({ url: '/contract/approval/list', method: 'get', params: query }) }
export function listFee(query) { return request({ url: '/contract/fee/list', method: 'get', params: query }) }
/** @param {{contractId:number, periodNo:number, receivableAmount:number, planReceiveDate:string, financeUserId?:number, remark?:string}} data */
export function addFee(data) { return request({ url: '/contract/fee', method: 'post', data }) }
/** @param {{planId:number, contractId:number, periodNo:number, receivableAmount:number, planReceiveDate:string, financeUserId?:number, remark?:string}} data */
export function updateFee(data) { return request({ url: '/contract/fee', method: 'put', data }) }
export function delFee(planId) { return request({ url: '/contract/fee/' + planId, method: 'delete' }) }
export function confirmFee(data) { return request({ url: '/contract/fee/confirm', method: 'post', data }) }
export function rejectFee(data) { return request({ url: '/contract/fee/reject', method: 'post', data }) }
export function invoiceFee(data) { return request({ url: '/contract/fee/invoice', method: 'post', data }) }
export function listAttachment(query) { return request({ url: '/contract/attachment/list', method: 'get', params: query }) }
/** @param {{contractId:number, fileName:string, fileUrl:string, fileType:string, fileSize?:number, remark?:string}} data */
export function addAttachment(data) { return request({ url: '/contract/attachment', method: 'post', data }) }
export function delAttachment(attachmentId) { return request({ url: '/contract/attachment/' + attachmentId, method: 'delete' }) }
export function listStatus(query) { return request({ url: '/contract/status/list', method: 'get', params: query }) }
