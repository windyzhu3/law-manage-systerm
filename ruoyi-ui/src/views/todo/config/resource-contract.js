function field(row, snake, camel) { return row && (row[camel] !== undefined ? row[camel] : row[snake]) }

function validateCalendar(form) {
  try { Intl.DateTimeFormat(undefined, { timeZone: form.timezone }) } catch (_) { throw new Error('timezone is invalid') }
  const days = (Array.isArray(form.workDays) ? form.workDays : String(form.workDays || '').split(','))
    .map(Number).filter((day, index, values) => Number.isInteger(day) && day >= 1 && day <= 7 && values.indexOf(day) === index).sort()
  if (!days.length) throw new Error('work days are required')
  const time = /^([01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?$/
  if (!time.test(form.workStart || '') || !time.test(form.workEnd || '') || form.workStart >= form.workEnd) throw new Error('work time is invalid')
  const exceptions = typeof form.exceptions === 'string' ? JSON.parse(form.exceptions || '{}') : (form.exceptions || {})
  Object.entries(exceptions).forEach(([date, working]) => {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || Number.isNaN(Date.parse(`${date}T00:00:00Z`)) || typeof working !== 'boolean') throw new Error('exception date is invalid')
  })
  return { ...form, workDays: days.join(','), exceptionJson: JSON.stringify(exceptions) }
}

function validateDecision(form) {
  if (!['OPEN', 'RESOLVED', 'CLOSED'].includes(form.status)) throw new Error('status is invalid')
  if (form.status !== 'OPEN' && !String(form.conclusion || '').trim()) throw new Error('conclusion is required')
  if (form.status !== 'OPEN' && !String(form.resolution || '').trim()) throw new Error('resolution is required')
  return { ...form }
}

function triggerPayload(form) {
  const event = form.event || {}
  return {
    triggerRuleId: field(form, 'trigger_rule_id', 'triggerRuleId'), eventType: event.eventType,
    payloadVersion: Number(event.payloadVersion || 1), conditionJson: JSON.stringify(event.condition || {}),
    templateId: Number(form.templateId), templateVersionId: Number(form.templateVersionId), businessType: form.businessType,
    enabled: form.enabled === false || form.enabled === 'N' ? 'N' : 'Y'
  }
}
module.exports = { field, validateCalendar, validateDecision, triggerPayload }
