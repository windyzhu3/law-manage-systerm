async function collectReleasePages(fetchPage, query, options) {
  const pageSize = Number((options && options.pageSize) || 500)
  const maxRows = Number((options && options.maxRows) || 10000)
  const result = []
  let offset = 0
  let total = null
  do {
    const response = await fetchPage({ ...(query || {}), offset, limit: pageSize })
    const rows = (response && response.rows) || []
    total = Number((response && response.total) || 0)
    if (total > maxRows) throw new Error(`Release export exceeds maximum ${maxRows} rows`)
    if (!rows.length && result.length < total) throw new Error('Release export stopped before all pages were returned')
    result.push(...rows)
    offset += rows.length
  } while (result.length < total)
  return result
}

function createActionIdRegistry(factory) {
  const values = Object.create(null)
  return {
    idFor(action) { if (!values[action]) values[action] = factory(action); return values[action] },
    complete(action) { delete values[action] },
    reset() { Object.keys(values).forEach(key => delete values[key]) }
  }
}

module.exports = { collectReleasePages, createActionIdRegistry }
