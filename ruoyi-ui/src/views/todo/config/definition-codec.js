const SECTIONS = ['schemaVersion', 'templateCode', 'event', 'owner', 'dod', 'sla', 'ui', 'routing', 'autoActions', 'decisionRefs', 'acceptanceRefs']

function parse(value, fallback) {
  if (value == null || value === '') return fallback
  if (typeof value !== 'string') return value
  try { return JSON.parse(value) } catch (e) { return fallback }
}

function object(value) {
  return value && typeof value === 'object' && !Array.isArray(value) ? clone(value) : {}
}

function array(value) {
  return Array.isArray(value) ? clone(value) : []
}

function clone(value) {
  return value == null ? value : JSON.parse(JSON.stringify(value))
}

function value(row, snake, camel) {
  return row && row[snake] !== undefined ? row[snake] : row && row[camel]
}

function section(input, name) {
  return object(input && input[name])
}

function canonical(input) {
  const source = object(input)
  return {
    schemaVersion: Number(source.schemaVersion) || 1,
    templateCode: source.templateCode || '',
    event: { eventType: '', payloadVersion: 1, condition: {}, ...section(source, 'event') },
    owner: { config: {}, ...section(source, 'owner') },
    dod: { config: {}, ...section(source, 'dod') },
    sla: { config: {}, ...section(source, 'sla') },
    ui: { config: {}, ...section(source, 'ui') },
    routing: { config: {}, ...section(source, 'routing') },
    autoActions: array(source.autoActions),
    decisionRefs: array(source.decisionRefs),
    acceptanceRefs: array(source.acceptanceRefs)
  }
}

function hydrateDefinition(row) {
  const stored = parse(value(row, 'definition_json', 'definitionJson'), null)
  if (stored && typeof stored === 'object') return canonical(stored)
  return canonical({
    schemaVersion: value(row, 'definition_schema_version', 'definitionSchemaVersion') || 1,
    templateCode: value(row, 'template_code', 'templateCode') || '',
    event: {
      eventType: value(row, 'event_type', 'eventType') || '',
      payloadVersion: Number(value(row, 'payload_version', 'payloadVersion')) || 1,
      condition: object(parse(value(row, 'condition_json', 'conditionJson'), {}))
    },
    owner: { config: object(parse(value(row, 'owner_rule_json', 'ownerRuleJson'), {})) },
    dod: { config: object(parse(value(row, 'dod_rule_json', 'dodRuleJson'), {})) },
    sla: { config: object(parse(value(row, 'sla_rule_json', 'slaRuleJson'), {})) },
    ui: { config: object(parse(value(row, 'ui_schema_json', 'uiSchemaJson'), {})) },
    routing: { config: object(parse(value(row, 'next_rule_json', 'nextRuleJson'), {})) },
    autoActions: array(parse(value(row, 'auto_actions_json', 'autoActionsJson'), [])).map(config => ({ config: object(config) })),
    decisionRefs: array(parse(value(row, 'decision_refs_json', 'decisionRefsJson'), [])),
    acceptanceRefs: array(parse(value(row, 'acceptance_refs_json', 'acceptanceRefsJson'), []))
  })
}

function serializeDefinition(form) {
  const document = canonical(form)
  return SECTIONS.reduce((result, key) => { result[key] = document[key]; return result }, {})
}

function toDraftPayload(form) {
  const document = serializeDefinition(form)
  return {
    definitionJson: JSON.stringify(document),
    ownerRuleJson: JSON.stringify(document.owner.config),
    dodRuleJson: JSON.stringify(document.dod.config),
    slaRuleJson: JSON.stringify(document.sla.config),
    nextRuleJson: JSON.stringify(document.routing.config),
    uiSchemaJson: JSON.stringify(document.ui.config)
  }
}

module.exports = { hydrateDefinition, serializeDefinition, toDraftPayload }
