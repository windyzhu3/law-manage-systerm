const { hydrateDefinition, toDraftPayload, definitionSourceToken } = require('../definition-codec')

function clone(value) { return value == null ? value : JSON.parse(JSON.stringify(value)) }
function field(row, camel, snake) { return row && (row[camel] !== undefined ? row[camel] : row[snake]) }

function emptyDraft() {
  return {
    templateId: null, versionId: null, versionNo: 1, versionStatus: 'DRAFT', templateStatus: '0', templateVersion: 0,
    templateCode: '', templateName: '', businessType: '', businessStage: '', templateType: 'STANDARD', priority: 'NORMAL', description: '',
    schemaVersion: 1, event: { eventType: '', payloadVersion: 1, condition: {} },
    owner: { config: { type: 'BUSINESS_OWNER', candidates: [], cc: [], skipUnavailable: true, useDelegation: true } },
    dod: { config: { composition: 'ALL' } }, sla: { config: {} }, ui: { config: {} },
    routing: { config: { nodes: [], edges: [] } }, autoActions: [], decisionRefs: [], acceptanceRefs: [],
    slaRuleId: null, dodRuleIds: [], ruleReferences: [], changeSummary: '', impactScope: '', sourceDefinitionJson: null
  }
}

function hydrateTemplateDraft(detail = {}) {
  const version = detail.editableVersion || detail.editable_version || {}
  const row = { ...version, templateCode: field(detail, 'templateCode', 'template_code') || field(version, 'templateCode', 'template_code') || '' }
  const definition = hydrateDefinition(row)
  const ui = definition.ui && definition.ui.config ? definition.ui.config : {}
  const references = (detail.ruleReferences || detail.rule_references || []).map(reference => ({
    type: field(reference, 'type', 'ref_type'), id: Number(field(reference, 'id', 'ref_id_value')),
    order: Number(field(reference, 'order', 'sort_order')), ruleCode: field(reference, 'ruleCode', 'rule_code'),
    ruleName: field(reference, 'ruleName', 'rule_name'), ruleStatus: field(reference, 'ruleStatus', 'rule_status')
  })).filter(reference => reference.type && reference.id > 0).sort((left, right) => left.order - right.order)
  return {
    ...emptyDraft(), ...definition,
    templateId: field(detail, 'templateId', 'template_id') || null,
    versionId: field(version, 'versionId', 'version_id') || field(detail, 'draftVersionId', 'draft_version_id') || null,
    versionNo: field(version, 'versionNo', 'version_no') || 1,
    versionStatus: field(version, 'status', 'status') || field(detail, 'draftStatus', 'draft_status') || 'DRAFT',
    templateStatus: field(detail, 'status', 'status') || '0', templateVersion: Number(field(detail, 'version', 'version') || 0),
    templateCode: field(detail, 'templateCode', 'template_code') || definition.templateCode || '',
    templateName: field(detail, 'templateName', 'template_name') || '', businessType: field(detail, 'businessType', 'business_type') || '',
    businessStage: ui.businessStage || '', templateType: ui.templateType || 'STANDARD', priority: ui.priority || 'NORMAL', description: ui.description || '',
    ui: { ...definition.ui, config: { ...ui } }, ruleReferences: references,
    slaRuleId: (references.find(reference => reference.type === 'SLA') || {}).id || null,
    dodRuleIds: references.filter(reference => reference.type === 'DOD').map(reference => reference.id),
    changeSummary: field(version, 'changeSummary', 'change_summary') || '', impactScope: field(version, 'impactScope', 'impact_scope') || '',
    sourceDefinitionJson: definitionSourceToken(row)
  }
}

function orderedReferences(draft) {
  const configured = Array.isArray(draft.ruleReferences) ? draft.ruleReferences : []
  const byKey = new Map(configured.map(reference => [`${reference.type}:${Number(reference.id)}`, reference]))
  const selected = []
  if (Number(draft.slaRuleId) > 0) selected.push({ type: 'SLA', id: Number(draft.slaRuleId) })
  ;(draft.dodRuleIds || []).forEach(id => { if (Number(id) > 0) selected.push({ type: 'DOD', id: Number(id) }) })
  return selected.map((reference, order) => ({ ...reference, order, ...(byKey.get(`${reference.type}:${reference.id}`) || {}), type: reference.type, id: reference.id, order }))
}

function toTemplateDraftPayload(draft, actionId) {
  const uiConfig = { ...(draft.ui && draft.ui.config), businessStage: draft.businessStage, templateType: draft.templateType, priority: draft.priority, description: draft.description }
  const definition = { ...clone(draft), templateCode: draft.templateCode, ui: { config: uiConfig } }
  const payload = toDraftPayload(definition, draft.sourceDefinitionJson)
  return {
    actionId, versionId: Number(draft.versionId), ...payload,
    expectedDefinitionJson: draft.sourceDefinitionJson,
    ruleReferences: orderedReferences(draft).map(reference => ({ type: reference.type, id: reference.id, order: reference.order })),
    changeSummary: draft.changeSummary || '', impactScope: draft.impactScope || ''
  }
}

function createPreflightGate(versionId, definitionToken, hash) { return { versionId: Number(versionId), definitionToken, hash } }
function canPublishFromGate(gate, versionId, definitionToken, dirty) {
  return Boolean(gate && !dirty && gate.hash && gate.versionId === Number(versionId) && gate.definitionToken === definitionToken)
}
function invalidatePreflightGate() { return null }

function mergeCreatedAggregate(unsavedDraft, identity, persistedDraft) {
  const draft = clone(unsavedDraft || emptyDraft())
  const persisted = persistedDraft || {}
  return {
    ...draft,
    templateId: Number(identity && identity.templateId) || Number(persisted.templateId) || null,
    versionId: Number(identity && identity.versionId) || Number(persisted.versionId) || null,
    versionNo: Number(identity && identity.versionNo) || Number(persisted.versionNo) || 1,
    versionStatus: persisted.versionStatus || 'DRAFT',
    templateVersion: Number(persisted.templateVersion || 0),
    templateStatus: persisted.templateStatus || '0',
    sourceDefinitionJson: persisted.sourceDefinitionJson == null ? null : persisted.sourceDefinitionJson
  }
}

module.exports = { emptyDraft, hydrateTemplateDraft, toTemplateDraftPayload, orderedReferences, createPreflightGate, canPublishFromGate, invalidatePreflightGate, mergeCreatedAggregate }
