const EFFECT_LABELS = Object.freeze({
  NEXT_TEMPLATE: '生成下一待办',
  END: '结束当前路径',
  RETAIN_CURRENT: '保留当前待办',
  SCHEDULE_NEXT: '等待系统计划下一窗口',
  SCHEDULE_SELF: '完成后开启下一周期',
  EXPECTED_VALIDATION_FAILURE: '预期校验失败'
})

const EFFECT_TONES = Object.freeze({
  NEXT_TEMPLATE: 'success',
  END: 'info',
  RETAIN_CURRENT: 'warning',
  SCHEDULE_NEXT: 'primary',
  SCHEDULE_SELF: 'primary',
  EXPECTED_VALIDATION_FAILURE: 'danger'
})

function effectKind(effect) {
  const source = effect || {}
  const explicit = source.kind || source.effectKind
  if (explicit) return String(explicit).toUpperCase()
  const legacyType = String(source.resultType || '').toUpperCase()
  if (legacyType === 'NEXT') return 'NEXT_TEMPLATE'
  if (legacyType === 'END') return 'END'
  if (source.targetTemplateCode || source.targetVersionId) return 'NEXT_TEMPLATE'
  return 'END'
}

function effectPresentation(effect) {
  const source = effect || {}
  const kind = effectKind(source)
  const startsRetryPlan = kind === 'END' &&
    String(source.businessAction || '').toUpperCase() === 'START_RETRY'
  const isFiveDayCycle = kind === 'SCHEDULE_SELF' &&
    String(source.targetTemplateCode || '') === 'TD-004'
  return {
    label: startsRetryPlan
      ? '结束当前待办并建立重试计划'
      : isFiveDayCycle ? '完成后开启下一轮5天待办' : (EFFECT_LABELS[kind] || '执行系统动作'),
    needsTarget: kind === 'NEXT_TEMPLATE',
    tone: startsRetryPlan ? 'primary' : (EFFECT_TONES[kind] || 'info')
  }
}

function semanticMeta(field) {
  const source = field || {}
  const meta = source.displayMeta && typeof source.displayMeta === 'object'
    ? source.displayMeta
    : {}
  return { ...meta, ...source }
}

function renderSemantic(field, emptyLabel) {
  const source = semanticMeta(field)
  const primary = source.displayValue || source.userName || source.dictLabel ||
    source.businessDisplayName || source.departmentName || source.deptName || ''
  const department = source.departmentName || source.deptName || ''
  if (primary && department && String(primary) !== String(department)) {
    return `${primary} / ${department}`
  }
  return String(primary || emptyLabel || '未填写')
}

function technicalValue(field) {
  const source = field || {}
  if (source.rawValue !== undefined && source.rawValue !== null && source.rawValue !== '') {
    return String(source.rawValue)
  }
  if (source.value !== undefined && source.value !== null && source.value !== '') {
    return String(source.value)
  }
  return ''
}

function routeTargetsFor(businessType, targets) {
  const type = String(businessType || '').toUpperCase()
  if (!type) return (targets || []).slice()
  return (targets || []).filter(target =>
    String((target && (target.businessType || target.business_type)) || '').toUpperCase() === type
  )
}

function mergeRoutingTargets(existing, latest) {
  const merged = new Map()
  ;[...(existing || []), ...(latest || [])].forEach(target => {
    const versionId = Number(target && (target.versionId || target.version_id))
    if (versionId > 0) merged.set(versionId, { ...(merged.get(versionId) || {}), ...target })
  })
  return Array.from(merged.values())
}

module.exports = {
  EFFECT_LABELS,
  effectKind,
  effectPresentation,
  renderSemantic,
  technicalValue,
  routeTargetsFor,
  mergeRoutingTargets
}
