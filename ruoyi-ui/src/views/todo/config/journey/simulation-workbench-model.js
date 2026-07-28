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
  const blockingScenarioCodes = required
    .filter(item => {
      const result = byCode[item.scenarioCode]
      return !result || !result.passed ||
        (result.evidence && result.evidence.definitionHash !== definitionHash)
    })
    .map(item => item.scenarioCode)
  return {
    publicationReady: blockingScenarioCodes.length === 0,
    blockingScenarioCodes
  }
}
