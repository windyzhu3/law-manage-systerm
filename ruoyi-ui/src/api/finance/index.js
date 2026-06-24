import request from '@/utils/request'

export function getFinanceDashboard() {
  return request({ url: '/finance/dashboard', method: 'get' })
}

export function listReceivable(query) {
  return request({ url: '/finance/receivable/list', method: 'get', params: query })
}

export function listPayment(query) {
  return request({ url: '/finance/payment/list', method: 'get', params: query })
}

export function listInvoice(query) {
  return request({ url: '/finance/invoice/list', method: 'get', params: query })
}

export function listFinanceExpense(query) {
  return request({ url: '/finance/expense/list', method: 'get', params: query })
}

export function getFinanceReport(query) {
  return request({ url: '/finance/report', method: 'get', params: query })
}

export function confirmPayment(data) {
  return request({ url: '/finance/payment/confirm', method: 'post', data })
}

export function rejectPayment(data) {
  return request({ url: '/finance/payment/reject', method: 'post', data })
}

export function handleInvoice(data) {
  return request({ url: '/finance/invoice/handle', method: 'post', data })
}

export function updateFinanceExpense(data) {
  return request({ url: '/finance/expense', method: 'put', data })
}
