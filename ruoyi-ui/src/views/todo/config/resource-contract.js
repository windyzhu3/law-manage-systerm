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
    const parsed = new Date(`${date}T00:00:00Z`)
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== date || typeof working !== 'boolean') throw new Error('exception date is invalid')
  })
  return { ...form, workDays: days.join(','), exceptionJson: JSON.stringify(exceptions) }
}

function validateDecision(form) {
  if (!/^[A-Z][A-Z0-9_-]{1,63}$/.test(String(form.code || ''))) throw new Error('code is required')
  if (!String(form.title || '').trim()) throw new Error('title is required')
  if (!['OPEN', 'RESOLVED', 'CLOSED'].includes(form.status)) throw new Error('status is invalid')
  if (form.status !== 'OPEN' && !String(form.conclusion || '').trim()) throw new Error('conclusion is required')
  if (form.status !== 'OPEN' && !String(form.resolution || '').trim()) throw new Error('resolution is required')
  if (form.status === 'OPEN' && (String(form.conclusion || '').trim() || String(form.resolution || '').trim())) throw new Error('open decisions cannot have results')
  return { ...form }
}

function triggerPayload(form) {
  const event = form.event || {}
  return {
    triggerRuleId: field(form, 'trigger_rule_id', 'triggerRuleId'), eventType: event.eventType,
    payloadVersion: Number(event.payloadVersion || 1), conditionJson: JSON.stringify(event.condition || {}),
    templateId: Number(field(form, 'template_id', 'templateId')), templateVersionId: Number(field(form, 'template_version_id', 'templateVersionId')), businessType: field(form, 'business_type', 'businessType'),
    enabled: form.enabled === false || form.enabled === 'N' ? 'N' : 'Y'
  }
}
module.exports = { field, validateCalendar, validateDecision, triggerPayload }
