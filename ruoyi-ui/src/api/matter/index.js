import request from '@/utils/request'

export function getMatterDashboard() { return request({ url: '/matter/dashboard', method: 'get' }) }
export function listMatter(query) { return request({ url: '/matter/list', method: 'get', params: query }) }
export function getMatter(caseId) { return request({ url: '/matter/' + caseId, method: 'get' }) }
export function getMatterFieldConfigs(caseType) { return request({ url: '/matter/field/' + caseType, method: 'get' }) }
export function addMatter(data) { return request({ url: '/matter', method: 'post', data }) }
export function updateMatter(data) { return request({ url: '/matter', method: 'put', data }) }

export function listProgress(query) { return request({ url: '/matter/progress/list', method: 'get', params: query }) }
export function addProgress(data) { return request({ url: '/matter/progress', method: 'post', data }) }
export function updateProgress(data) { return request({ url: '/matter/progress', method: 'put', data }) }
export function delProgress(progressId) { return request({ url: '/matter/progress/' + progressId, method: 'delete' }) }

export function listNode(query) { return request({ url: '/matter/node/list', method: 'get', params: query }) }
export function addNode(data) { return request({ url: '/matter/node', method: 'post', data }) }
export function updateNode(data) { return request({ url: '/matter/node', method: 'put', data }) }
export function delNode(nodeId) { return request({ url: '/matter/node/' + nodeId, method: 'delete' }) }
export function saveNodeMaterials(nodeId, data) { return request({ url: '/matter/node/' + nodeId + '/materials', method: 'put', data }) }

export function listExpense(query) { return request({ url: '/matter/expense/list', method: 'get', params: query }) }
export function addExpense(data) { return request({ url: '/matter/expense', method: 'post', data }) }
export function updateExpense(data) { return request({ url: '/matter/expense', method: 'put', data }) }
export function delExpense(expenseId) { return request({ url: '/matter/expense/' + expenseId, method: 'delete' }) }

export function listDocument(query) { return request({ url: '/matter/document/list', method: 'get', params: query }) }
export function addDocument(data) { return request({ url: '/matter/document', method: 'post', data }) }
export function delDocument(documentId) { return request({ url: '/matter/document/' + documentId, method: 'delete' }) }

export function getArchive(caseId) { return request({ url: '/matter/archive/' + caseId, method: 'get' }) }
export function applyArchive(data) { return request({ url: '/matter/archive/apply', method: 'post', data }) }
export function confirmClose(data) { return request({ url: '/matter/archive/close', method: 'post', data }) }
export function confirmArchive(data) { return request({ url: '/matter/archive/confirm', method: 'post', data }) }

export function listMatterStatus(query) { return request({ url: '/matter/status/list', method: 'get', params: query }) }
