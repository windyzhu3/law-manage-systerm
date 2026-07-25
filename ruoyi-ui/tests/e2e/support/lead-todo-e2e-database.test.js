const assert = require('node:assert/strict')
const test = require('node:test')
const {
  FIXTURE_CODES,
  createRunContext,
  backendIdentityProof,
  verifyBackendIdentity,
  setupLeadTodoFixtures,
  cleanupLeadTodoFixtures,
  withLeadTodoFixtures,
  withVerifiedLeadTodoFixtures
} = require('../../../e2e/support/lead-todo-e2e-database')

function environment() {
  return {
    TODO_E2E_REAL_BACKEND: 'true',
    TODO_E2E_DB_NAME: 'lead_fixture_e2e',
    FOUNDATION_E2E_IDENTITY_SECRET: 'a-dedicated-e2e-secret-that-is-long-enough',
    LEAD_SALES_USER: 'ft_sales',
    LEAD_SUPERVISOR_USER: 'ft_partner_manager',
    LEAD_POLICY_ADMIN_USER: 'ft_product_owner',
    TODO_E2E_FILE_STORAGE_ROOT: 'C:\\isolated-e2e-storage'
  }
}

function identityResponse(ctx, overrides = {}) {
  const identity = {
    catalog: ctx.database,
    schema: ctx.database,
    nonce: ctx.nonce,
    marker: ctx.marker,
    buildVersion: '0.20.54',
    ...overrides
  }
  identity.proof = backendIdentityProof(
    'a-dedicated-e2e-secret-that-is-long-enough',
    identity
  )
  return identity
}

function apiResponse(status, body) {
  return {
    ok: () => status >= 200 && status < 300,
    status: () => status,
    json: async () => body
  }
}

function validApiRequest(ctx, calls = []) {
  return {
    get: async () => apiResponse(200, { code: 200, data: identityResponse(ctx) }),
    post: async (...args) => calls.push(args)
  }
}

test('unverified or forged capabilities cannot reach SQL or file mutation', async () => {
  const ctx = createRunContext({ marker: 'UNVERIFIED', nonce: 'run-nonce-unverified', env: environment() })
  const sqlCalls = []
  const fileCalls = []
  const options = {
    executeSql: (...args) => sqlCalls.push(args),
    apiRequest: { post: async (...args) => fileCalls.push(args) }
  }

  assert.throws(() => setupLeadTodoFixtures(ctx, options), /verified run capability/i)
  await assert.rejects(cleanupLeadTodoFixtures(Object.freeze({ ctx }), options), /verified run capability/i)
  await assert.rejects(withLeadTodoFixtures(Object.freeze({ ctx }), options, async () => {}), /verified run capability/i)
  assert.deepEqual(sqlCalls, [])
  assert.deepEqual(fileCalls, [])
})

test('verified capability is bound to its exact run context', async () => {
  const left = createRunContext({ marker: 'BOUND_LEFT', nonce: 'run-nonce-left', env: environment() })
  const right = createRunContext({ marker: 'BOUND_RIGHT', nonce: 'run-nonce-right', env: environment() })
  const capability = await verifyBackendIdentity(left, validApiRequest(left))
  const sqlCalls = []

  assert.throws(
    () => setupLeadTodoFixtures(capability, { executeSql: (...args) => sqlCalls.push(args), runContext: right }),
    /context mismatch/i
  )
  assert.deepEqual(sqlCalls, [])
})

test('database identity mismatch blocks the complete mutation lifecycle with zero writes', async () => {
  const ctx = createRunContext({ marker: 'WRONG_DB', nonce: 'run-nonce-wrong-db', env: environment() })
  const sqlCalls = []
  const fileCalls = []
  const apiRequest = {
    get: async () => apiResponse(200, {
      code: 200,
      data: identityResponse(ctx, { catalog: 'neighbour_e2e', schema: 'neighbour_e2e' })
    }),
    post: async (...args) => fileCalls.push(args)
  }

  await assert.rejects(
    withVerifiedLeadTodoFixtures(
      ctx,
      { apiRequest, executeSql: (...args) => sqlCalls.push(args) },
      async () => assert.fail('fixture work must not run')
    ),
    /database identity mismatch/i
  )
  assert.deepEqual(sqlCalls, [])
  assert.deepEqual(fileCalls, [])
})

test('missing or wrong backend blocks the complete mutation lifecycle with zero writes', async () => {
  const ctx = createRunContext({ marker: 'WRONG_BACKEND', nonce: 'run-nonce-wrong-backend', env: environment() })
  const sqlCalls = []
  const fileCalls = []
  const apiRequest = {
    get: async () => apiResponse(404, { code: 404, msg: 'not found' }),
    post: async (...args) => fileCalls.push(args)
  }

  await assert.rejects(
    withVerifiedLeadTodoFixtures(
      ctx,
      { apiRequest, executeSql: (...args) => sqlCalls.push(args) },
      async () => assert.fail('fixture work must not run')
    ),
    /backend identity handshake failed/i
  )
  assert.deepEqual(sqlCalls, [])
  assert.deepEqual(fileCalls, [])
})

test('partial setup failure still runs exact-ownership cleanup from a pre-created run context', async () => {
  const ctx = createRunContext({ marker: 'PARTIAL_SETUP', env: environment() })
  const capability = await verifyBackendIdentity(ctx, validApiRequest(ctx))
  const calls = []
  const executeSql = sql => {
    calls.push(sql)
    if (sql.includes('insert into biz_lead_setting')) {
      assert.match(sql, /start transaction;/i)
      assert.match(sql, /commit;/i)
      assert.doesNotMatch(sql, /\blike\b/i)
      throw new Error('injected setup failure')
    }
    if (/^\s*select\s/i.test(sql) && sql.includes('select count(*)')) {
      return Array.from({ length: 40 }, () => '0').join('\t')
    }
    return ''
  }

  await assert.rejects(
    withLeadTodoFixtures(capability, { executeSql }, async () => {}),
    /injected setup failure/
  )

  const cleanup = calls.find(sql => sql.includes('delete from biz_lead where'))
  assert.ok(cleanup, 'cleanup must execute even when setup throws before manifest is returned')
  assert.doesNotMatch(cleanup, /\blike\b/i)
  ctx.leadNos.forEach(leadNo => assert.ok(cleanup.includes(`'${leadNo}'`)))
})

test('overlapping run markers clean only their exact manifest and never the neighbouring run', async () => {
  const left = createRunContext({ marker: 'OVERLAP', runId: 'OVERLAP', env: environment() })
  const right = createRunContext({ marker: 'OVERLAP_MORE', runId: 'OVERLAP_M', env: environment() })
  const leftCapability = await verifyBackendIdentity(left, validApiRequest(left))
  const surviving = new Set([...left.leadNos, ...right.leadNos])
  const calls = []
  const executeSql = sql => {
    calls.push(sql)
    if (/^\s*select lead_id,lead_no from biz_lead/i.test(sql)) {
      const ctx = sql.includes(right.leadNos[0]) ? right : left
      return ctx.leadNos.map((leadNo, index) => `${1000 + index}\t${leadNo}`).join('\n')
    }
    if (/^\s*select todo_id from todo_instance/i.test(sql) ||
        /^\s*select plan_id from todo_schedule_plan/i.test(sql) ||
        /^\s*select distinct r\.file_object_id/i.test(sql)) return ''
    if (sql.includes('delete from biz_lead where')) {
      left.leadNos.forEach(leadNo => surviving.delete(leadNo))
      return ''
    }
    return ''
  }

  await cleanupLeadTodoFixtures(leftCapability, { executeSql })

  assert.equal(left.leadNos.some(leadNo => surviving.has(leadNo)), false)
  assert.equal(right.leadNos.every(leadNo => surviving.has(leadNo)), true)
  const cleanup = calls.find(sql => sql.includes('delete from biz_lead where'))
  assert.doesNotMatch(cleanup, /\blike\b/i)
  assert.equal(cleanup.includes(right.leadNos[0]), false)
  assert.equal(FIXTURE_CODES.length, 9)
})
