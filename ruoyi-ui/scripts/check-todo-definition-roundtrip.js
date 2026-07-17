const assert = require('assert')
const fs = require('fs')

const fixture = {
  schemaVersion: 2,
  templateCode: 'LEAD_FIRST_CONTACT',
  event: {
    eventType: 'LEAD_ASSIGNED',
    payloadVersion: 3,
    condition: { operator: 'AND', conditions: [{ field: 'priority', operator: 'IN', value: ['HIGH', 'URGENT'] }], extension: { audit: true } }
  },
  owner: { config: { type: 'ROLE', operand: 'law_partner_manager', candidates: { departmentCode: 'LEGAL', fallback: 'PAYLOAD:ownerId' } } },
  dod: { config: { requiredFields: ['contactResult'], requiredAttachments: ['CONTACT_PROOF'], conditionalRequired: [{ field: 'contactProof', when: { field: 'contactResult', operator: 'EQ', value: 'VISITED' } }], extension: { retained: true } } },
  sla: { config: { calendarCode: 'LEGAL_WORKDAY', minutes: 30, pauseOn: ['RETURNED'], escalation: { departmentCode: 'LEGAL' } } },
  ui: { config: { formCode: 'LEAD_FIRST_CONTACT', fields: [{ key: 'contactResult', type: 'text' }], extension: { renderer: 'compact' } } },
  routing: { config: { templateVersionId: 72, transitions: [{ on: 'COMPLETE', to: 'ARCHIVE' }], extension: { preserve: 'yes' } } },
  autoActions: [{ config: { ruleKey: 'notify-owner', actionType: 'NOTIFY', triggerAt: 'SLA_80', recipients: { departmentCode: 'LEGAL' } } }],
  decisionRefs: ['DECISION_LEAD_CONTACT'],
  acceptanceRefs: ['ACCEPT_CONTACT_PROOF']
}

function normalize(value) {
  if (Array.isArray(value)) return value.map(normalize)
  if (value && typeof value === 'object') return Object.keys(value).sort().reduce((result, key) => {
    if (value[key] !== undefined) result[key] = normalize(value[key])
    return result
  }, {})
  return value
}

function run() {
  const { hydrateDefinition, serializeDefinition, toDraftPayload } = require('../src/views/todo/config/definition-codec')
  const hydrated = hydrateDefinition({ definition_json: JSON.stringify(fixture) })
  const serialized = serializeDefinition(hydrated)
  assert.deepStrictEqual(normalize(serialized), normalize(fixture), 'canonical document must survive hydrate/serialize without data loss')
  assert.deepStrictEqual(Object.keys(serialized).sort(), ['acceptanceRefs', 'autoActions', 'decisionRefs', 'dod', 'event', 'owner', 'routing', 'schemaVersion', 'sla', 'templateCode', 'ui'], 'serializer must emit only canonical definition sections')
  const payload = toDraftPayload(hydrated)
  assert.deepStrictEqual(normalize(JSON.parse(payload.definitionJson)), normalize(fixture), 'draft payload must persist the complete canonical definition document')
  assert.strictEqual(payload.expectedDefinitionJson, null, 'codec leaves source token ownership to the loaded form row')
  const tokenPayload = toDraftPayload(hydrated, JSON.stringify(fixture))
  assert.strictEqual(tokenPayload.expectedDefinitionJson, JSON.stringify(fixture), 'draft payload must preserve the loaded source token for optimistic concurrency')

  const legacy = hydrateDefinition({
    template_code: 'CASE_ASSIGN', event_type: 'CASE_CREATED', payload_version: 1,
    condition_json: JSON.stringify({ field: 'priority', operator: 'EQ', value: 'HIGH' }),
    owner_rule_json: JSON.stringify({ type: 'DEPT', operand: 'CASE_MANAGEMENT', extension: { source: 'catalog' } }),
    dod_rule_json: JSON.stringify({ requiredFields: ['lawyerId'], custom: { preserve: true } }),
    sla_rule_json: JSON.stringify({ calendarCode: 'DEFAULT', minutes: 480, escalation: { departmentCode: 'CASE_MANAGEMENT' } }),
    next_rule_json: JSON.stringify({ templateVersionId: 9, custom: { retain: true } }),
    ui_schema_json: JSON.stringify({ formCode: 'CASE_ASSIGN', fields: [{ key: 'lawyerId', type: 'user' }], custom: { retain: true } }),
    auto_actions_json: JSON.stringify([{ ruleKey: 'remind', actionType: 'NOTIFY' }]),
    decision_refs_json: JSON.stringify(['DECISION_ASSIGN']), acceptance_refs_json: JSON.stringify(['ACCEPT_ASSIGN'])
  })
  assert.deepStrictEqual(normalize(serializeDefinition(legacy)), normalize({
    schemaVersion: 1, templateCode: 'CASE_ASSIGN', event: { eventType: 'CASE_CREATED', payloadVersion: 1, condition: { field: 'priority', operator: 'EQ', value: 'HIGH' } },
    owner: { config: { type: 'DEPT', operand: 'CASE_MANAGEMENT', extension: { source: 'catalog' } } },
    dod: { config: { requiredFields: ['lawyerId'], custom: { preserve: true } } },
    sla: { config: { calendarCode: 'DEFAULT', minutes: 480, escalation: { departmentCode: 'CASE_MANAGEMENT' } } },
    ui: { config: { formCode: 'CASE_ASSIGN', fields: [{ key: 'lawyerId', type: 'user' }], custom: { retain: true } } },
    routing: { config: { templateVersionId: 9, custom: { retain: true } } }, autoActions: [{ config: { ruleKey: 'remind', actionType: 'NOTIFY' } }], decisionRefs: ['DECISION_ASSIGN'], acceptanceRefs: ['ACCEPT_ASSIGN']
  }), 'legacy JSON columns must hydrate into the canonical document without dropping nested configuration')

  const form = fs.readFileSync('src/views/todo/config/components/DefinitionForm.vue', 'utf8')
  for (const marker of ['event-condition-builder', 'owner-rule-builder', 'dod-form-builder', 'sla-rule-builder', 'hydrateDefinition', 'serializeDefinition']) assert.ok(form.includes(marker), `DefinitionForm is missing ${marker}`)
  const ownerBuilder = fs.readFileSync('src/views/todo/config/components/OwnerRuleBuilder.vue', 'utf8')
  assert.ok(ownerBuilder.includes('listRole') && ownerBuilder.includes('listDept'), 'owner controls must be fed by repository role and department catalogs')
  assert.ok(!/placeholder="[^"\n]*(ID|id)/.test(ownerBuilder), 'owner controls must not ask administrators to type raw IDs')
  const eventBuilder = fs.readFileSync('src/views/todo/config/components/EventConditionBuilder.vue', 'utf8')
  assert.ok(eventBuilder.includes('请在触发规则页编辑') && eventBuilder.includes('Task14'), 'event builder must direct edits to the Task14 trigger-rules resource')
  assert.ok(!eventBuilder.includes("$emit('input'"), 'Task13 event builder must never mutate trigger rules')
  assert.ok((eventBuilder.match(/disabled/g) || []).length >= 3, 'Task13 event inputs must be display-only')
  console.log('todo definition round-trip contract ok')
}

run()
