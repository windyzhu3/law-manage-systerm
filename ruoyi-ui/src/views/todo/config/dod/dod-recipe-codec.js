const list = value => Array.isArray(value) ? value.slice() : []
const parseList = value => {
  if (Array.isArray(value)) return value.slice()
  if (!value) return []
  try { const parsed = JSON.parse(value); return Array.isArray(parsed) ? parsed : [] } catch (error) { return [] }
}
const normalizeCondition = item => {
  const when = { ...((item && item.when) || {}) }
  const kind = Object.prototype.hasOwnProperty.call(when, 'equals') ? 'equals' : 'present'
  if (kind === 'present' && !Object.prototype.hasOwnProperty.call(when, 'present')) when.present = true
  return { field: (item && item.field) || '', kind, when }
}

function emptySimpleRule() {
  return { businessType: 'LEAD', requiredFields: [], requiredAttachments: [], validatorRefs: [], conditionalRules: [] }
}

function applyRecipe(current, recipe) {
  return {
    ...emptySimpleRule(), ...(current || {}),
    requiredFields: list(recipe && recipe.requiredFields),
    requiredAttachments: list(recipe && recipe.requiredAttachments),
    validatorRefs: list(recipe && recipe.validatorRefs),
    conditionalRules: list(recipe && recipe.conditionalRules).map(normalizeCondition)
  }
}

function toLegacyJson(simple) {
  const value = { ...emptySimpleRule(), ...(simple || {}) }
  return {
    requiredFieldsJson: JSON.stringify(value.requiredFields || []),
    requiredAttachmentsJson: JSON.stringify(value.requiredAttachments || []),
    validatorRefsJson: JSON.stringify(value.validatorRefs || []),
    conditionalRulesJson: JSON.stringify(value.conditionalRules || []),
    errorMessagesJson: '{}'
  }
}

function fromLegacyJson(source) {
  return {
    ...emptySimpleRule(),
    requiredFields: parseList(source && (source.requiredFieldsJson !== undefined ? source.requiredFieldsJson : source.required_fields_json)),
    requiredAttachments: parseList(source && (source.requiredAttachmentsJson !== undefined ? source.requiredAttachmentsJson : source.required_attachments_json)),
    validatorRefs: parseList(source && (source.validatorRefsJson !== undefined ? source.validatorRefsJson : source.validator_refs_json)),
    conditionalRules: parseList(source && (source.conditionalRulesJson !== undefined ? source.conditionalRulesJson : source.conditional_rules_json)).map(normalizeCondition)
  }
}

function summary(simple) {
  const value = { ...emptySimpleRule(), ...(simple || {}) }
  const parts = [`${value.requiredFields.length} 个必填字段`, `${value.requiredAttachments.length} 类必传材料`, `${value.validatorRefs.length} 项自动校验`]
  if (value.conditionalRules.length) parts.push(`${value.conditionalRules.length} 条条件要求`)
  return parts.join('，')
}

module.exports = { emptySimpleRule, applyRecipe, toLegacyJson, fromLegacyJson, summary }
