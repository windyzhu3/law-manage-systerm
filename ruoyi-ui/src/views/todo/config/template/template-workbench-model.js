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

module.exports = { resolveProblemSummary }
