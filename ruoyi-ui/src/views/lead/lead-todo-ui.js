export function stableActionId(prefix, identity) {
  const session = `${Date.now()}-${Math.random().toString(16).slice(2)}`
  return `${prefix}:${identity}:${session}`
}

export function errorMessage(error, fallback) {
  const data = error && error.response && error.response.data
  const code = error && (error.businessCode || error.code || (data && (data.businessCode || data.code)))
  const message = error && (error.msg || error.message || (error.response && error.response.data && error.response.data.msg))
  if (code === 'CONCURRENT_MODIFICATION' || code === 'STATE_CONFLICT') {
    return '数据已被其他人员更新，请刷新后重试'
  }
  return message || fallback
}

export function errorCode(error) {
  const data = error && error.response && error.response.data
  return error && (error.businessCode || error.code || (data && (data.businessCode || data.code)))
}

export function maskedMobile(value) {
  return value ? String(value).replace(/(\d{3})\d{4}(\d{4})/, '$1****$2') : '-'
}

export function timeText(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ')
}

export function secondsText(value) {
  const seconds = Number(value || 0)
  if (!seconds) return '0 秒'
  const minutes = Math.floor(seconds / 60)
  return minutes ? `${minutes} 分 ${seconds % 60} 秒` : `${seconds} 秒`
}

export function slaMeta(row) {
  if (row && row.overdue) return { label: '已超时', type: 'danger' }
  if (row && row.escalated) return { label: '已升级', type: 'warning' }
  const status = row && row.slaStatus
  const values = {
    NORMAL: { label: '时限正常', type: 'success' },
    WARNING: { label: '即将到期', type: 'warning' },
    OVERDUE: { label: '已超时', type: 'danger' },
    ESCALATED: { label: '已升级', type: 'warning' }
  }
  return values[status] || { label: status || '未计时', type: 'info' }
}

export function todoStatusMeta(status) {
  return {
    CREATED: ['待领取', 'info'],
    CLAIMED: ['待开始', 'warning'],
    IN_PROGRESS: ['办理中', 'primary'],
    SUBMITTED: ['待确认', 'warning'],
    COMPLETED: ['已完成', 'success'],
    CANCELLED: ['已取消', 'info']
  }[status] || [status || '-', 'info']
}

export function isPendingTodo(row) {
  return row && row.todoId && !['COMPLETED', 'CANCELLED'].includes(row.todoStatus)
}

export function allowed(row, action) {
  const values = row && (row.allowedActions || row.allowed_actions || row.actions)
  return Array.isArray(values) && values.map(item => String(item).toLowerCase()).includes(String(action).toLowerCase())
}

export function policyWindows() {
  return [
    { windowCode: 'T0', windowOrder: 0, dayOffset: 0, startOffsetMinutes: 0, durationMinutes: 120, maxAttempts: 3, occurrenceNo: 1 },
    { windowCode: 'T1_AM', windowOrder: 1, dayOffset: 1, startTime: '09:00', endTime: '11:30', maxAttempts: 1, occurrenceNo: 1 },
    { windowCode: 'T1_NOON', windowOrder: 2, dayOffset: 1, startTime: '11:30', endTime: '14:00', maxAttempts: 1, occurrenceNo: 1 },
    { windowCode: 'T1_PM', windowOrder: 3, dayOffset: 1, startTime: '14:00', endTime: '18:00', maxAttempts: 1, occurrenceNo: 1 },
    { windowCode: 'T2_AM', windowOrder: 4, dayOffset: 2, startTime: '09:00', endTime: '11:30', maxAttempts: 1, occurrenceNo: 1 },
    { windowCode: 'T2_NOON', windowOrder: 5, dayOffset: 2, startTime: '11:30', endTime: '14:00', maxAttempts: 1, occurrenceNo: 1 },
    { windowCode: 'T2_PM', windowOrder: 6, dayOffset: 2, startTime: '14:00', endTime: '18:00', maxAttempts: 1, occurrenceNo: 1 }
  ]
}

export function parseRetryRule(policy) {
  try {
    const source = JSON.parse((policy && policy.retryRuleJson) || '{}')
    return {
      templateVersionId: source.templateVersionId || null,
      ruleVersionId: source.ruleVersionId || null,
      timezone: source.timezone || 'Asia/Shanghai',
      windows: Array.isArray(source.windows) && source.windows.length ? source.windows : policyWindows()
    }
  } catch (error) {
    return { templateVersionId: null, ruleVersionId: null, timezone: 'Asia/Shanghai', windows: policyWindows() }
  }
}
