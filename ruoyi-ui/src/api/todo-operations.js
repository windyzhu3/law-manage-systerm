import request from '@/utils/request'
export function getOperationsDashboard() { return request({ url: '/todo/operations/dashboard', method: 'get' }) }
export function listDeadEvents(params) { return request({ url: '/system/business-event/list', method: 'get', params: { eventStatus: 'DEAD', ...params } }) }
export function replayDeadEvent(eventId) { return request({ url: `/system/business-event/${eventId}/requeue`, method: 'post' }) }
export function forceCompleteTodo(id, data) { return request({ url: `/todo/operations/${id}/force-complete`, method: 'post', data }) }
export function forceCancelTodo(id, data) { return request({ url: `/todo/operations/${id}/force-cancel`, method: 'post', data }) }
export function regenerateTodo(id, data) { return request({ url: `/todo/operations/${id}/regenerate`, method: 'post', data }) }
export function batchTransferTodos(data) { return request({ url: '/todo/operations/batch-transfer', method: 'post', data }) }
export function waiveTodoSla(id, data) { return request({ url: `/todo/operations/${id}/sla-waiver`, method: 'post', data }) }
