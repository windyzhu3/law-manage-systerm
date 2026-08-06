function number(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function hasServerSummary(response) {
  return ['blockerTemplates', 'warningTemplates', 'readyTemplates']
    .every(key => response && response[key] !== null && response[key] !== undefined && Number.isFinite(Number(response[key])))
}

function pageSummary(rows) {
  return (rows || []).reduce((summary, row) => {
    if (number(row && row.blockerCount)) summary.blockerTemplates += 1
    else if (number(row && row.warningCount)) summary.warningTemplates += 1
    else summary.readyTemplates += 1
    return summary
  }, { blockerTemplates: 0, warningTemplates: 0, readyTemplates: 0 })
}

function resolveProblemSummary(response, rows) {
  if (!hasServerSummary(response)) return pageSummary(rows)
  return {
    blockerTemplates: number(response.blockerTemplates),
    warningTemplates: number(response.warningTemplates),
    readyTemplates: number(response.readyTemplates)
  }
}

function templateRuntimePresentation(row) {
  const state = String((row && row.runtimeState) || 'INACTIVE')
  if (state === 'ACTIVE') return { state, label: '运行中', type: 'success', replacementText: '' }
  if (state === 'REPLACED') {
    const name = row.replacementTemplateName || row.replacementTemplateCode || '现行模板'
    const code = row.replacementTemplateCode ? `（${row.replacementTemplateCode}）` : ''
    return { state, label: '已停用', type: 'info', replacementText: `已由${name}${code}替代` }
  }
  return { state: 'INACTIVE', label: '已停用', type: 'info', replacementText: '' }
}

function templateNavigationTarget(row) {
  const replacement = Number(row && row.replacementTemplateId)
  if (row && row.primaryAction === 'OPEN_REPLACEMENT' && replacement > 0) {
    return { templateId: replacement, view: 'published' }
  }
  return {
    templateId: Number(row && row.templateId),
    view: row && row.primaryAction === 'VIEW_PUBLISHED' ? 'published' : 'draft'
  }
}

function templateTogglePresentation(row) {
  const presentation = templateRuntimePresentation(row)
  if (presentation.state === 'REPLACED') return null
  const active = presentation.state === 'ACTIVE'
  return {
    targetStatus: active ? '1' : '0',
    label: active ? '停用模板' : '启用模板',
    confirmText: active
      ? '停用后不会再为新业务生成该模板待办，确认继续？'
      : '启用后模板可被有效触发规则使用，确认继续？'
  }
}

module.exports = {
  resolveProblemSummary,
  templateRuntimePresentation,
  templateNavigationTarget,
  templateTogglePresentation
}
