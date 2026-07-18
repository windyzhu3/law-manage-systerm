const assert = require('assert')
const fs = require('fs')

async function verify() {
  const {
    unwrapHistoricalMigrationExportResponse
  } = require('../src/api/historical-migration-export-response')

  const blobFailure = {
    data: new Blob([JSON.stringify({
      code: 500,
      businessCode: 'TODO_MIGRATION_EXPORT_FAILED',
      msg: 'Historical migration export could not be generated'
    })]),
    headers: { 'content-type': 'application/json;charset=UTF-8' }
  }
  await assert.rejects(() => unwrapHistoricalMigrationExportResponse(blobFailure), error => {
    assert.strictEqual(error.businessCode, 'TODO_MIGRATION_EXPORT_FAILED')
    assert.strictEqual(error.message, 'Historical migration export could not be generated')
    assert.strictEqual(error.responseCode, 500)
    return true
  })

  const problemBytes = Buffer.from(JSON.stringify({
    code: 500,
    businessCode: 'TODO_MIGRATION_GATE_UNSUPPORTED',
    msg: 'Only G-04 is supported'
  }), 'utf8')
  const problemFailure = {
    data: problemBytes.buffer.slice(problemBytes.byteOffset, problemBytes.byteOffset + problemBytes.byteLength),
    headers: { get: name => name.toLowerCase() === 'content-type' ? 'application/problem+json' : null }
  }
  await assert.rejects(() => unwrapHistoricalMigrationExportResponse(problemFailure), error => {
    assert.strictEqual(error.businessCode, 'TODO_MIGRATION_GATE_UNSUPPORTED')
    assert.strictEqual(error.message, 'Only G-04 is supported')
    return true
  })

  const zipResponse = {
    data: new Blob(['PK\u0003\u0004archive']),
    headers: { 'content-type': 'application/zip' }
  }
  assert.strictEqual(await unwrapHistoricalMigrationExportResponse(zipResponse), zipResponse)

  const requestSource = fs.readFileSync('src/utils/request.js', 'utf8')
  assert.ok(requestSource.includes('res.config.returnFullResponse ? res : res.data'),
    'binary full-response opt-in must remain backward compatible')
}

verify().then(() => console.log('historical migration export response contract ok')).catch(error => {
  console.error(error)
  process.exitCode = 1
})
