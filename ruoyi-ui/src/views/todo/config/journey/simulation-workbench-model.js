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
  const currentHash = String(definitionHash || '').trim()
  const blockingScenarios = required.flatMap(item => {
    const result = byCode[item.scenarioCode]
    const evidenceHash = String((result && result.evidence && result.evidence.definitionHash) || '').trim()
    let reason = null
    if (!result) reason = 'MISSING'
    else if (!result.passed) reason = 'LAST_RUN_FAILED'
    else if (!result.evidence) reason = 'MISSING'
    else if (!currentHash || !evidenceHash || evidenceHash !== currentHash) reason = 'DEFINITION_CHANGED'
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

export function persistedScenarioResults(scenarios, readiness, definitionHash) {
  const state = readiness || {}
  const hash = String(definitionHash || state.definitionHash || '')
  if (!hash || String(state.definitionHash || '') !== hash) return {}
  const blockers = (state.blockingScenarios || [])
    .map(item => typeof item === 'string' ? item : item && item.scenarioCode)
    .concat(state.blockingScenarioCodes || [])
    .filter(Boolean)
  const blocked = new Set(blockers)
  const scenarioGateBlocked = (state.issues || []).some(issue =>
    String((issue && issue.code) || '') === 'TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE'
  )
  if (scenarioGateBlocked && !blocked.size) return {}
  return (scenarios || []).reduce((results, scenario) => {
    if (!scenario.requiredForPublish || blocked.has(scenario.scenarioCode)) return results
    results[scenario.scenarioCode] = {
      scenarioCode: scenario.scenarioCode,
      scenarioName: scenario.scenarioName,
      expectedNextTemplateCode: scenario.expectedNextTemplateCode,
      actualNextTemplateCode: scenario.expectedNextTemplateCode,
      passed: true,
      message: '当前草稿已通过验证',
      evidence: { definitionHash: hash }
    }
    return results
  }, {})
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

export function simulationCompletionMessage(readiness) {
  return readiness && readiness.publicationReady
    ? '模拟发布验证已全部通过'
    : ''
}

export function readinessRepairTarget(issue, scenarios) {
  const source = issue || {}
  const code = String(source.code || '')
  const stepCode = String(source.stepCode || source.section || 'SIMULATION_PUBLISH').toUpperCase()
  if (['TODO_FULL_SIMULATION_REQUIRED', 'TODO_FULL_SIMULATION_STALE',
    'TODO_JOURNEY_SIMULATION_REQUIRED'].includes(code)) {
    return {
      stepCode: 'SIMULATION_PUBLISH',
      scenarioCode: '',
      focusTarget: 'full-simulation'
    }
  }
  if (code !== 'TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE') {
    return { stepCode, scenarioCode: '', focusTarget: '' }
  }
  const blockers = source.blockingScenarios || []
  const blockerCodes = blockers
    .map(item => typeof item === 'string' ? item : item && item.scenarioCode)
    .concat(source.blockingScenarioCodes || [])
    .filter(Boolean)
  const scenarioCode = blockerCodes.find(candidate =>
    (scenarios || []).some(item => item.scenarioCode === candidate)
  ) || ''
  return {
    stepCode: 'SIMULATION_PUBLISH',
    scenarioCode,
    focusTarget: 'scenario-selector'
  }
}

const SIMULATION_READINESS_CODES = [
  'TODO_REQUIRED_SIMULATION_SCENARIOS_INCOMPLETE',
  'TODO_FULL_SIMULATION_REQUIRED',
  'TODO_FULL_SIMULATION_STALE',
  'TODO_JOURNEY_SIMULATION_REQUIRED'
]

function isSimulationReadinessIssue(issue) {
  return (issue && issue.stepCode === 'SIMULATION_PUBLISH') ||
    SIMULATION_READINESS_CODES.includes(String((issue && issue.code) || ''))
}

export function journeySimulationReadiness(journey) {
  if (!journey) return null
  const template = journey.template || {}
  if (journey.simulationReadiness) {
    const readiness = journey.simulationReadiness
    const currentHash = String(template.definitionHash || '')
    if (!currentHash || String(readiness.definitionHash || '') !== currentHash) {
      const otherIssues = (readiness.issues || []).filter(issue =>
        String((issue && issue.code) || '') !== 'TODO_FULL_SIMULATION_STALE'
      )
      return {
        ...readiness,
        definitionHash: currentHash,
        fullSimulationPassed: false,
        publicationReady: false,
        issues: otherIssues.concat({
          code: 'TODO_FULL_SIMULATION_STALE',
          severity: 'BLOCKER',
          stepCode: 'SIMULATION_PUBLISH',
          resourceKey: 'SIMULATION',
          fieldPath: 'simulation.full',
          message: '完整试运行已失效，请使用当前草稿重新运行'
        })
      }
    }
    return readiness
  }
  const step = (journey.steps || []).find(item => item.code === 'SIMULATION_PUBLISH') || {}
  const issues = (journey.issues || []).filter(isSimulationReadinessIssue)
  const fullBlocked = issues.some(issue => [
    'TODO_FULL_SIMULATION_REQUIRED',
    'TODO_FULL_SIMULATION_STALE',
    'TODO_JOURNEY_SIMULATION_REQUIRED'
  ].includes(String(issue.code || '')))
  return {
    templateId: Number(template.templateId),
    versionId: Number(template.versionId),
    definitionHash: template.definitionHash || '',
    blockingScenarios: [],
    fullSimulationPassed: step.state === 'COMPLETED' ||
      (step.state !== 'NOT_STARTED' && !fullBlocked),
    publicationReady: step.state === 'COMPLETED',
    issues
  }
}

export function shouldInvalidateSimulationForTemplateHashChange(previousHash, nextHash) {
  const previous = String(previousHash || '').trim()
  const next = String(nextHash || '').trim()
  return Boolean(previous) && previous !== next
}

export function mergeJourneySimulationReadiness(journey, readiness) {
  if (!journey || !readiness) return journey
  const template = journey.template || {}
  const sameTemplate = Number(readiness.templateId) === Number(template.templateId)
  const sameVersion = Number(readiness.versionId) === Number(template.versionId)
  const authoritativeHash = String(readiness.definitionHash || '')
  if (!sameTemplate || !sameVersion || !authoritativeHash) return journey
  const otherIssues = (journey.issues || []).filter(issue => !isSimulationReadinessIssue(issue))
  const simulationIssues = readiness.issues || []
  const steps = (journey.steps || []).map(step =>
    step.code === 'SIMULATION_PUBLISH'
      ? {
          ...step,
          state: readiness.publicationReady ? 'COMPLETED' : 'BLOCKED',
          issueCount: simulationIssues.length
        }
      : step
  )
  return {
    ...journey,
    template: {
      ...template,
      definitionHash: authoritativeHash
    },
    simulationReadiness: readiness,
    issues: otherIssues.concat(simulationIssues),
    steps
  }
}
