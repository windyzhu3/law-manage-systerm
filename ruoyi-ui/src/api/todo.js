import request from '@/utils/request'
export function getTodoDashboard() { return request({ url: '/todo/dashboard', method: 'get' }) }
export function listTodo(query) { return request({ url: '/todo/list', method: 'get', params: query }) }
export function getTodo(id) { return request({ url: '/todo/' + id, method: 'get' }) }
export function getTodoForm(id) { return request({ url: `/todo/${id}/form`, method: 'get' }) }
function action(id, name, data) { return request({ url: `/todo/${id}/${name}`, method: 'post', data }) }
export function claimTodo(id, data) { return action(id, 'claim', data) }
export function startTodo(id, data) { return action(id, 'start', data) }
export function submitTodo(id, data) { return action(id, 'submit', data) }
export function completeTodo(id, data) { return action(id, 'complete', data) }
export function returnTodo(id, data) { return action(id, 'return', data) }
export function transferTodo(id, data) { return action(id, 'transfer', data) }
export function cancelTodo(id, data) { return action(id, 'cancel', data) }
export function addTodoAttachment(id, data) { return action(id, 'attachment', data) }
export function addTodoCc(id, data) { return action(id, 'cc', data) }
export function addTodoCandidate(id, data) { return action(id, 'candidate', data) }
export function getBusinessTodoSummary(type, id) { return request({ url: `/todo/business/${type}/${id}/summary`, method: 'get' }) }
export function listBusinessTodos(type, id, params) { return request({ url: `/todo/business/${type}/${id}/list`, method: 'get', params }) }
export function getTodoChain(rootTodoId) { return request({ url: `/todo/chain/${rootTodoId}`, method: 'get' }) }
export function requestTodoExtension(id, data) { return request({ url: `/todo/${id}/extension-requests`, method: 'post', data }) }
export function registerBusinessFile(data) { return request({ url: '/files/register', method: 'post', data }) }
export function completeBusinessFileUpload(uploadIntentId, file) {
  const data = new FormData()
  data.append('file', file)
  return request({
    url: `/files/${uploadIntentId}/complete`,
    method: 'post',
    data,
    headers: { 'Content-Type': 'multipart/form-data', repeatSubmit: false }
  })
}
export function getBusinessFilePreviewToken(fileObjectId, relationId) { return request({ url: `/files/${fileObjectId}/preview-token`, method: 'get', params: { relationId } }) }
export function getBusinessFileDownloadToken(fileObjectId, relationId) { return request({ url: `/files/${fileObjectId}/download-token`, method: 'get', params: { relationId } }) }
export function listBusinessFileVersions(fileObjectId) { return request({ url: `/files/${fileObjectId}/versions`, method: 'get' }) }
export function listBusinessFileMaterials(businessType, businessId) { return request({ url: '/files/materials', method: 'get', params: { businessType, businessId } }) }
export function openBusinessFileContent(token) { return request({ url: `/files/access/${encodeURIComponent(token)}`, method: 'get', responseType: 'blob' }) }
