function valueOf(row, camel, snake) {
  return row && (row[camel] !== undefined ? row[camel] : row[snake])
}

function triggerId(row) {
  return Number(valueOf(row, 'triggerRuleId', 'trigger_rule_id'))
}

function triggerVersion(row) {
  return Number(valueOf(row, 'version', 'version') || 0)
}

function triggerOrder(row) {
  return Number(valueOf(row, 'sortOrder', 'sort_order') || 0)
}

function canonicalRows(rows) {
  return rows.map((row, index) => ({ ...row, sortOrder: index + 1 }))
}

function moveTriggerRows(rows, id, offset) {
  const ordered = (rows || []).map(row => ({ ...row }))
  const index = ordered.findIndex(row => triggerId(row) === Number(id))
  const targetIndex = index + Number(offset)
  if (index < 0 || targetIndex < 0 || targetIndex >= ordered.length) return { rows: ordered, movedIndex: index }
  const moved = ordered[index]
  ordered.splice(index, 1)
  ordered.splice(targetIndex, 0, moved)
  return { rows: canonicalRows(ordered), movedIndex: targetIndex }
}

function pageTriggerRows(rows, pageNum, pageSize) {
  const start = Math.max(0, (Number(pageNum) - 1) * Number(pageSize))
  return (rows || []).slice(start, start + Number(pageSize))
}

function changedTriggerSortItems(rows, baseline) {
  return (rows || []).filter(row => Number(baseline[triggerId(row)]) !== triggerOrder(row)).map(row => ({
    triggerRuleId: triggerId(row),
    sortOrder: triggerOrder(row),
    expectedVersion: triggerVersion(row)
  }))
}

function buildTriggerToggleCommand(enabled, expectedVersion, actionId) {
  return { enabled, actionId, expectedVersion: Number(expectedVersion || 0) }
}

function canModifyTriggerSort(keyword) {
  return !String(keyword || '').trim()
}

module.exports = { moveTriggerRows, pageTriggerRows, changedTriggerSortItems, buildTriggerToggleCommand, canModifyTriggerSort }
