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

export function failedScenarioResult(scenario, error) {
  const source = scenario || {}
  const failure = error || {}
  return {
    scenarioCode: source.scenarioCode || '',
    scenarioName: source.scenarioName || source.scenarioCode || '',
    passed: false,
    businessCode: failure.businessCode || failure.code || '',
    message: failure.message || ''
  }
}

export function scenarioFailureMessage(result) {
  const source = result || {}
  if (source.businessCode === 'TODO_SIMULATION_SCENARIO_NODE_UNRESOLVED') {
    return '当前场景无法定位待办完成节点，请保存后续路由后重新验证'
  }
  return source.message || '当前场景验证未通过，请检查配置后重新运行'
}

export function scenarioRepairTarget(issue, gate, scenarios) {
  const source = issue || {}
  const stepCode = String(source.stepCode || source.section || 'SIMULATION_PUBLISH').toUpperCase()
  if (stepCode !== 'SIMULATION_PUBLISH' &&
      source.code !== 'TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE') {
    return { stepCode, scenarioCode: '', focusTarget: '' }
  }
  const blockers = (gate && gate.blockingScenarios) || []
  const codes = blockers.map(item => item.scenarioCode)
    .concat((gate && gate.blockingScenarioCodes) || [])
  const first = codes.find(code => (scenarios || []).some(item => item.scenarioCode === code)) || ''
  return {
    stepCode: 'SIMULATION_PUBLISH',
    scenarioCode: first,
    focusTarget: 'scenario-selector'
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

export function versionIdentity(row) {
  const source = row || {}
  const value = source.versionId !== undefined
    ? source.versionId
    : (source.version_id !== undefined ? source.version_id : source.id)
  const number = Number(value)
  return Number.isInteger(number) && number > 0 ? number : null
}

export function versionStatus(row) {
  const source = row || {}
  return String(source.status || source.publishStatus || source.publish_status || '').toUpperCase()
}

export function versionDiffPlan(versions, currentVersionId) {
  const rightVersionId = versionIdentity({ versionId: currentVersionId })
  const published = (versions || []).find(item =>
    ['PUBLISHED', 'RETIRED'].includes(versionStatus(item))
  )
  if (!published) {
    return {
      available: false,
      leftVersionId: null,
      rightVersionId,
      reason: 'NO_PUBLISHED_VERSION'
    }
  }
  const leftVersionId = versionIdentity(published)
  if (!leftVersionId || !rightVersionId) {
    return {
      available: false,
      leftVersionId: null,
      rightVersionId,
      reason: 'INVALID_VERSION_ID'
    }
  }
  return {
    available: true,
    leftVersionId,
    rightVersionId,
    reason: ''
  }
}
