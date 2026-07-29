export function scenarioFormFields(scenario, completionFields) {
  const editable = new Set((scenario && scenario.editableFields) || [])
  const payload = (scenario && scenario.completionPayload) || {}
  return (completionFields || [])
    .filter(field => editable.has(field.path))
    .map(field => ({ ...field, rawValue: payload[field.path] }))
}

export function scenarioGate(scenarios, results, definitionHash) {
  const required = (scenarios || []).filter(item => item.requiredForPublish)
  const byCode = results || {}
  const blockingScenarios = required.flatMap(item => {
    const result = byCode[item.scenarioCode]
    let reason = null
    if (!result) reason = 'MISSING'
    else if (!result.passed) reason = 'LAST_RUN_FAILED'
    else if (!result.evidence) reason = 'MISSING'
    else if (result.evidence.definitionHash !== definitionHash) reason = 'DEFINITION_CHANGED'
    return reason ? [{
      scenarioCode: item.scenarioCode,
      scenarioName: item.scenarioName || item.scenarioCode,
      reason
    }] : []
  })
  const blockingScenarioCodes = blockingScenarios.map(item => item.scenarioCode)
  return {
    publicationReady: blockingScenarioCodes.length === 0,
    blockingScenarioCodes,
    blockingScenarios
  }
}

export function scenarioTargetLabel(templateCode, routingTargets) {
  const code = String(templateCode || '')
  const target = (routingTargets || []).find(item =>
    String(item.templateCode || item.template_code || '') === code
  )
  const name = target && (target.templateName || target.template_name)
  return name ? `${name}（${code}）` : code
}

export function semanticOptionLabel(value, options) {
  const option = (options || []).find(item =>
    String(item.rawValue) === String(value) || String(item.value) === String(value)
  )
  return option ? (option.displayValue || option.label || String(value)) : String(value == null ? '' : value)
}
