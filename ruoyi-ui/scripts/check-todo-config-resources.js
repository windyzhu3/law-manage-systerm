const assert = require('assert')
const { validateCalendar, validateDecision, triggerPayload } = require('../src/views/todo/config/resource-contract')

assert.throws(() => validateCalendar({ timezone: 'Invalid/Timezone', workDays: [1], workStart: '18:00', workEnd: '09:00', exceptions: { 'not-a-date': true } }), /timezone|time|date/i)
assert.deepStrictEqual(validateCalendar({ timezone: 'Asia/Shanghai', workDays: [1, 2, 3, 4, 5], workStart: '09:00', workEnd: '18:00', exceptions: { '2026-07-20': false } }).workDays, '1,2,3,4,5')
assert.throws(() => validateDecision({ status: 'RESOLVED', conclusion: '', resolution: 'approved' }), /conclusion/i)
assert.deepStrictEqual(triggerPayload({ triggerRuleId: 4, event: { eventType: 'LEAD_ASSIGNED', payloadVersion: 1, condition: { field: 'stage', operator: 'EQ', value: 'READY' } }, templateId: 2, templateVersionId: 3, businessType: 'LEAD', enabled: true }), { triggerRuleId: 4, eventType: 'LEAD_ASSIGNED', payloadVersion: 1, conditionJson: JSON.stringify({ field: 'stage', operator: 'EQ', value: 'READY' }), templateId: 2, templateVersionId: 3, businessType: 'LEAD', enabled: 'Y' })
console.log('todo config resource contract ok')
