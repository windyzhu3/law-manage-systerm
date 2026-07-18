function contentType(headers) {
  if (!headers) return ''
  if (typeof headers.get === 'function') return headers.get('content-type') || ''
  return headers['content-type'] || headers['Content-Type'] || ''
}

function isJsonContentType(value) {
  return /^(?:application|text)\/(?:[^;]+\+)?json(?:\s*;|$)/i.test(String(value || '').trim())
}

function blobText(value) {
  if (typeof value.text === 'function') return value.text()
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(reader.error)
    reader.readAsText(value, 'UTF-8')
  })
}

function utf8Text(value) {
  if (value && typeof value.text === 'function') return blobText(value)
  if (typeof value === 'string') return Promise.resolve(value)
  if (typeof ArrayBuffer !== 'undefined' && (value instanceof ArrayBuffer || ArrayBuffer.isView(value))) {
    const bytes = value instanceof ArrayBuffer
      ? new Uint8Array(value)
      : new Uint8Array(value.buffer, value.byteOffset, value.byteLength)
    return Promise.resolve(new TextDecoder('utf-8').decode(bytes))
  }
  return Promise.reject(new Error('Historical migration export returned an unreadable error response'))
}

function exportError(payload) {
  const businessCode = payload && payload.businessCode
    ? String(payload.businessCode)
    : 'TODO_MIGRATION_EXPORT_FAILED'
  const message = payload && (payload.msg || payload.message)
    ? String(payload.msg || payload.message)
    : 'Historical migration export could not be generated'
  const error = new Error(message)
  error.name = 'HistoricalMigrationExportError'
  error.businessCode = businessCode
  error.responseCode = payload && payload.code
  return error
}

async function unwrapHistoricalMigrationExportResponse(response) {
  if (!isJsonContentType(contentType(response && response.headers))) return response
  try {
    const text = await utf8Text(response && response.data)
    throw exportError(JSON.parse(text.replace(/^\ufeff/, '')))
  } catch (error) {
    if (error && error.name === 'HistoricalMigrationExportError') throw error
    throw exportError(null)
  }
}

module.exports = { unwrapHistoricalMigrationExportResponse }
