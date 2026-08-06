const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const { executeSql } = require('../../tests/e2e/support/mysql-e2e-runner')

const WINDOW_CODES = ['T0', 'T1_AM', 'T1_NOON', 'T1_PM', 'T2_AM', 'T2_NOON', 'T2_PM']
const FIXTURE_CODES = [
  'ASSIGN', 'LEAVE', 'VALID', 'MANUAL', 'TRUE_INVALID', 'MISJUDGED', 'RETAIN', 'NEXT',
  'CONNECTED', 'EXHAUSTED', 'DEAD_POOL', 'AUTO_REVIEW', 'SLA', 'AUTH'
]
const verifiedRuns = new WeakMap()

function required(name, env = process.env) {
  const value = String(env[name] || '').trim()
  if (!value) throw new Error(`${name} is required for the real Lead Todo E2E suite`)
  return value
}

function safeIdentifier(value, label) {
  const normalized = String(value || '').trim().replace(/[^A-Za-z0-9_]/g, '_')
  if (!normalized || normalized.length > 32) throw new Error(`${label} must be a non-empty 1..32 character identifier`)
  return normalized
}

function quote(value) {
  return `'${String(value).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`
}

function database(env = process.env) {
  const name = safeIdentifier(required('TODO_E2E_DB_NAME', env), 'TODO_E2E_DB_NAME')
  if (!/_e2e$/i.test(name)) throw new Error(`Refusing to mutate non-E2E database ${name}`)
  return name
}

function createRunContext(options = {}) {
  const env = options.env || process.env
  if (String(env.TODO_E2E_REAL_BACKEND).toLowerCase() !== 'true') {
    throw new Error('TODO_E2E_REAL_BACKEND=true is required; mocked routes are forbidden')
  }
  const marker = safeIdentifier(options.marker || required('LEAD_TODO_E2E_RUN_MARKER', env), 'LEAD_TODO_E2E_RUN_MARKER')
  const requestedRunId = safeIdentifier(options.runId || marker, 'Lead Todo E2E runId')
  const runId = requestedRunId.length <= 10
    ? requestedRunId
    : crypto.createHash('sha256').update(requestedRunId).digest('hex').slice(0, 10)
  const prefix = `LEAD_E2E_${runId}_`
  const ctx = {
    database: database(env),
    marker,
    nonce: String(options.nonce || crypto.randomUUID()),
    identitySecret: required('FOUNDATION_E2E_IDENTITY_SECRET', env),
    runId,
    prefix,
    sourceCode: `E2E_${runId}`,
    policyCode: `LEAD_E2E_POLICY_${runId}`,
    informationOfficer: required('LEAD_INFORMATION_USER', env),
    seller: required('LEAD_SALES_USER', env),
    supervisor: required('LEAD_SUPERVISOR_USER', env),
    alternateSalesPrototype: required('LEAD_ALTERNATE_SALES_USER', env),
    alternateSales: `lead_e2e_alt_${runId}`,
    policyAdmin: required('LEAD_POLICY_ADMIN_USER', env),
    storageRoot: path.resolve(env.TODO_E2E_FILE_STORAGE_ROOT ||
      path.join(env.RUOYI_PROFILE || '/tmp/law-manage/uploads', 'file-center'))
  }
  ctx.leadNos = FIXTURE_CODES.map(code => `${prefix}${code}`)
  Object.freeze(ctx.leadNos)
  return Object.freeze(ctx)
}

function backendIdentityCanonical(identity) {
  return [
    String(identity.catalog || ''),
    String(identity.schema || ''),
    String(identity.nonce || ''),
    String(identity.marker || ''),
    String(identity.buildVersion || '')
  ].join('\n')
}

function backendIdentityProof(secret, identity) {
  return crypto.createHmac('sha256', String(secret))
    .update(backendIdentityCanonical(identity))
    .digest('hex')
}

function safeProofEqual(left, right) {
  const a = Buffer.from(String(left || ''), 'utf8')
  const b = Buffer.from(String(right || ''), 'utf8')
  return a.length === b.length && crypto.timingSafeEqual(a, b)
}

async function verifyBackendIdentity(ctx, apiRequest) {
  if (!apiRequest || typeof apiRequest.get !== 'function') {
    throw new Error('An authenticated direct-backend request context is required for identity verification')
  }
  const response = await apiRequest.get('/foundation/e2e/backend-identity', {
    headers: {
      'X-E2E-Identity-Secret': ctx.identitySecret,
      'X-E2E-Run-Nonce': ctx.nonce,
      'X-E2E-Fixture-Marker': ctx.marker
    }
  })
  const envelope = await response.json().catch(() => ({}))
  if (!response.ok() || Number(envelope.code || 200) !== 200 || !envelope.data) {
    throw new Error(`Backend identity handshake failed: ${envelope.msg || response.status()}`)
  }
  const identity = envelope.data
  const expectedProof = backendIdentityProof(ctx.identitySecret, identity)
  if (!safeProofEqual(expectedProof, identity.proof)) {
    throw new Error('Backend identity handshake failed: proof mismatch')
  }
  if (identity.nonce !== ctx.nonce || identity.marker !== ctx.marker) {
    throw new Error('Backend identity handshake failed: run identity mismatch')
  }
  if (identity.catalog !== ctx.database || identity.schema !== ctx.database) {
    throw new Error(
      `Backend database identity mismatch: expected ${ctx.database}, got ${identity.catalog}/${identity.schema}`
    )
  }
  const capability = Object.freeze(Object.create(null))
  verifiedRuns.set(capability, Object.freeze({
    context: ctx,
    apiRequest,
    identity: Object.freeze({ ...identity }),
    binding: Object.freeze({
      database: ctx.database,
      marker: ctx.marker,
      nonce: ctx.nonce
    })
  }))
  return capability
}

function requireVerifiedRun(capability, options = {}) {
  const verified = capability && typeof capability === 'object'
    ? verifiedRuns.get(capability)
    : null
  if (!verified) throw new Error('A verified run capability is required before E2E mutation')
  const ctx = verified.context
  if (options.runContext && options.runContext !== ctx) {
    throw new Error('Verified run capability context mismatch')
  }
  if (options.apiRequest && options.apiRequest !== verified.apiRequest) {
    throw new Error('Verified run capability backend mismatch')
  }
  if (ctx.database !== verified.binding.database ||
      ctx.marker !== verified.binding.marker ||
      ctx.nonce !== verified.binding.nonce) {
    throw new Error('Verified run capability binding mismatch')
  }
  return verified
}

function exactList(values) {
  if (!Array.isArray(values) || !values.length) throw new Error('Exact E2E ownership list cannot be empty')
  return values.map(quote).join(',')
}

function canonicalRetryRule() {
  return JSON.stringify({
    templateVersionId: 0,
    ruleVersionId: 920053,
    timezone: 'Asia/Shanghai',
    windows: [
      { windowCode: 'T0', windowOrder: 0, dayOffset: 0, startOffsetMinutes: 0, durationMinutes: 120, maxAttempts: 3, occurrenceNo: 1 },
      { windowCode: 'T1_AM', windowOrder: 1, dayOffset: 1, startTime: '09:00:00', endTime: '11:00:00', maxAttempts: 1, occurrenceNo: 1 },
      { windowCode: 'T1_NOON', windowOrder: 2, dayOffset: 1, startTime: '11:00:00', endTime: '14:00:00', maxAttempts: 1, occurrenceNo: 1 },
      { windowCode: 'T1_PM', windowOrder: 3, dayOffset: 1, startTime: '14:00:00', endTime: '18:00:00', maxAttempts: 1, occurrenceNo: 1 },
      { windowCode: 'T2_AM', windowOrder: 4, dayOffset: 2, startTime: '09:00:00', endTime: '11:00:00', maxAttempts: 1, occurrenceNo: 1 },
      { windowCode: 'T2_NOON', windowOrder: 5, dayOffset: 2, startTime: '11:00:00', endTime: '14:00:00', maxAttempts: 1, occurrenceNo: 1 },
      { windowCode: 'T2_PM', windowOrder: 6, dayOffset: 2, startTime: '14:00:00', endTime: '18:00:00', maxAttempts: 1, occurrenceNo: 1 }
    ]
  })
}

function leadInsert(ctx, code, overrides = {}) {
  const disposition = overrides.disposition || 'ACTIVE'
  const firstResult = overrides.firstResult || null
  const retryStage = overrides.retryStage || null
  const invalidStatus = overrides.invalidStatus || null
  const status = overrides.status || '1'
  return `
insert into biz_lead(
  lead_no,lead_name,contact_name,mobile,source_code,tag_confirm_status,
  first_contact_status,first_contact_result,invalid_review_status,retry_stage,
  retry_attempt_count,status,pool_status,disposition,priority,owner_id,dept_id,
  del_flag,row_version,create_by,create_time,update_by,update_time
)
select ${quote(ctx.prefix + code)},${quote(`E2E ${code}`)},${quote(`E2E ${code}`)},
  ${quote(`139${String(FIXTURE_CODES.indexOf(code) + 1).padStart(8, '0')}`)},${quote(ctx.sourceCode)},
  ${quote(overrides.tagStatus || 'CONFIRMED')},${quote(overrides.firstStatus || 'PENDING')},${firstResult ? quote(firstResult) : 'null'},
  ${invalidStatus ? quote(invalidStatus) : 'null'},${retryStage ? quote(retryStage) : 'null'},
  0,${quote(status)},${disposition === 'PUBLIC_POOL' ? "'1'" : "'0'"},${quote(disposition)},'2',
  ${overrides.unassigned ? 'null' : 'seller.user_id'},seller.dept_id,
  '0',0,'lead-e2e',sysdate(),'lead-e2e',sysdate()
from sys_user seller
where seller.user_name=${quote(ctx.seller)} and seller.status='0' and seller.del_flag='0';`
}

function todoInsert(ctx, variable, leadCode, templateCode, nodeKey, status, ownerUser, rootVariable, previousVariable, occurrenceKey) {
  const root = rootVariable ? `@${rootVariable}` : `@${variable}`
  const previous = previousVariable ? `@${previousVariable}` : 'null'
  const occurrence = occurrenceKey ? quote(`${ctx.prefix}${occurrenceKey}`) : 'null'
  return `
set @lead_${variable}=(select lead_id from biz_lead where lead_no=${quote(ctx.prefix + leadCode)});
insert into todo_instance(
  todo_no,template_id,template_version_id,template_code,title,business_type,business_id,business_no,
  owner_id,owner_dept_id,status,claimed_at,started_at,submitted_at,due_at,previous_todo_id,
  dod_snapshot_json,definition_hash,route_definition_version_id,ui_schema_snapshot,sla_snapshot,
  route_node_key,occurrence_key,payload_schema_version,version,create_by
)
select concat(${quote(ctx.prefix + 'TODO_')},${quote(variable)}),t.template_id,v.version_id,t.template_code,
  concat('E2E ',t.template_code),'LEAD',l.lead_id,l.lead_no,u.user_id,u.dept_id,${quote(status)},
  sysdate(),sysdate(),case when ${quote(status)} in ('SUBMITTED','COMPLETED') then sysdate() else null end,
  date_add(sysdate(),interval 2 hour),${previous},v.dod_rule_json,
  case when ${rootVariable ? '1' : '0'}=1
    then (select definition_hash from todo_instance where todo_id=${root})
    else v.definition_hash end,
  case when ${rootVariable ? '1' : '0'}=1
    then (select route_definition_version_id from todo_instance where todo_id=${root})
    else v.version_id end,
  v.ui_schema_json,v.sla_rule_json,${quote(nodeKey)},${occurrence},1,0,'lead-e2e'
from biz_lead l
join sys_user u on u.user_name=${quote(ownerUser)}
join todo_template t on t.template_code=${quote(templateCode)}
join todo_template_version v on v.template_id=t.template_id and v.version_no=t.current_version
where l.lead_no=${quote(ctx.prefix + leadCode)};
set @${variable}=last_insert_id();
update todo_instance
set root_todo_id=${root},
    route_token=json_object('rootTodoId',${root},'nodeKey',${quote(nodeKey)},
      'branchKey',null,'occurrence',0,'status','ACTIVE'),
    completed_at=case when status='COMPLETED' then sysdate() else completed_at end
where todo_id=@${variable};
insert into todo_relation(todo_id,business_type,business_id,business_no,relation_type)
select @${variable},'LEAD',lead_id,lead_no,'PRIMARY'
from biz_lead where lead_no=${quote(ctx.prefix + leadCode)};`
}

function reviewFixture(ctx, code, result) {
  const key = code.replace(/[^A-Z]/g, '')
  const completed = result ? 'COMPLETED' : 'PENDING'
  return `
${todoInsert(ctx, `${key}_SOURCE`, code, 'TD-001', 'td001', 'COMPLETED', ctx.seller)}
${todoInsert(ctx, `${key}_REVIEW`, code, 'TD-002', 'td002', result ? 'COMPLETED' : 'SUBMITTED',
    ctx.supervisor, `${key}_SOURCE`, `${key}_SOURCE`)}
update todo_instance
set due_at=date_add(sysdate(),interval 1 day),update_time=sysdate()
where todo_id=@${key}_REVIEW and status<>'COMPLETED';
insert into biz_lead_invalid_review(
  lead_id,reason_code,sales_explanation,submitted_by,submitted_at,reviewer_id,
  review_result,review_comment,reviewed_at,todo_id,status,idempotency_key,create_by,row_version
)
select l.lead_id,'NO_DEMAND',concat('E2E review ',${quote(code)}),seller.user_id,
  sysdate(),reviewer.user_id,${result ? quote(result) : 'null'},
  ${result ? quote('E2E confirmed invalid') : 'null'},${result ? 'sysdate()' : 'null'},
  @${key}_SOURCE,${quote(completed)},concat(${quote(ctx.prefix + 'REVIEW_')},${quote(code)}),'lead-e2e',0
from biz_lead l
join sys_user seller on seller.user_name=${quote(ctx.seller)}
join sys_user reviewer on reviewer.user_name=${quote(ctx.supervisor)}
where l.lead_no=${quote(ctx.prefix + code)};`
}

function retryFixture(ctx, code, maxAttempts, includeLater) {
  const key = code.replace(/[^A-Z]/g, '')
  const later = includeLater
    ? `
insert into todo_schedule_window(
  plan_id,window_code,window_order,day_offset,start_time,end_time,materialize_at,due_at,
  max_attempts,occurrence_no,status,create_time,update_time,version
) values(@PLAN_${key},'T1_AM',1,1,'09:00:00','11:00:00',
  date_add(sysdate(),interval 1 day),date_add(sysdate(),interval 26 hour),1,1,'PENDING',
  sysdate(),sysdate(),0);`
    : ''
  return `
${todoInsert(ctx, `${key}_RETRY`, code, 'TD-003', 'td003', 'SUBMITTED', ctx.seller, null, null, `OCC_${code}`)}
insert into todo_schedule_plan(
  previous_todo_id,template_version_id,business_type,business_id,schedule_purpose,idempotency_key,timezone,rule_version_id,
  assignment_policy_id,assignment_policy_version,assignment_policy_snapshot_source,
  first_contact_at,current_window_code,status,create_time,update_time,version
)
select @${key}_RETRY,t.template_version_id,'LEAD',t.business_id,'LEAD_RETRY',
  concat('LEAD_RETRY:',t.business_id,':',@${key}_RETRY),'Asia/Shanghai',920053,
  p.policy_id,p.row_version,'RESOLVED_POLICY',sysdate(),'T0','ACTIVE',sysdate(),sysdate(),0
from todo_instance t
join biz_lead_assignment_policy p on p.policy_code=${quote(ctx.policyCode)}
where t.todo_id=@${key}_RETRY;
set @PLAN_${key}=last_insert_id();
insert into todo_schedule_window(
  plan_id,window_code,window_order,day_offset,start_time,end_time,materialize_at,due_at,
  max_attempts,occurrence_no,status,create_time,update_time,version
) values(@PLAN_${key},'T0',0,0,'09:00:00','11:00:00',sysdate(),
  date_add(sysdate(),interval 2 hour),${Number(maxAttempts)},1,'MATERIALIZED',sysdate(),sysdate(),0);
set @WINDOW_${key}=last_insert_id();
insert into todo_schedule_occurrence(
  plan_id,window_id,window_code,occurrence_no,occurrence_key,due_at,todo_id,status,
  create_time,update_time,version
) values(@PLAN_${key},@WINDOW_${key},'T0',1,${quote(ctx.prefix + `OCC_${code}`)},
  date_add(sysdate(),interval 2 hour),@${key}_RETRY,'MATERIALIZED',sysdate(),sysdate(),0);
${later}`
}

function setupLeadTodoFixtures(capability, dependencies = {}) {
  const { context: ctx } = requireVerifiedRun(capability, dependencies)
  const runSql = dependencies.executeSql || executeSql
  const retryRule = canonicalRetryRule()
  const setup = `
set names utf8mb4 collate utf8mb4_unicode_ci;
create temporary table tmp_lead_e2e_identity_guard(
  information_count int not null,seller_count int not null,alternate_count int not null,
  supervisor_count int not null,admin_count int not null,
  constraint chk_lead_e2e_information check(information_count=1),
  constraint chk_lead_e2e_seller check(seller_count=1),
  constraint chk_lead_e2e_alternate check(alternate_count=1),
  constraint chk_lead_e2e_supervisor check(supervisor_count=1),
  constraint chk_lead_e2e_admin check(admin_count=1)
) engine=innodb;
insert into tmp_lead_e2e_identity_guard
select
  (select count(*) from sys_user where user_name=${quote(ctx.informationOfficer)} and status='0' and del_flag='0'),
  (select count(*) from sys_user where user_name=${quote(ctx.seller)} and status='0' and del_flag='0'),
  (select count(*) from sys_user where user_name=${quote(ctx.alternateSalesPrototype)} and status='0' and del_flag='0'),
  (select count(*) from sys_user where user_name=${quote(ctx.supervisor)} and status='0' and del_flag='0'),
  (select count(*) from sys_user where user_name=${quote(ctx.policyAdmin)} and status='0' and del_flag='0');
drop temporary table tmp_lead_e2e_identity_guard;

start transaction;
-- LEAD_E2E_DISPOSABLE_ALTERNATE: clone only the minimum account attributes
-- needed by the assignment policy and place the disposable user in the
-- seller's department so the fixture exercises the production validator.
insert into sys_user(
  dept_id,user_name,nick_name,user_type,email,phonenumber,sex,avatar,password,
  status,del_flag,pwd_update_date,create_by,create_time,remark
)
select seller.dept_id,${quote(ctx.alternateSales)},'Lead E2E alternate',prototype.user_type,
  '', '',prototype.sex,prototype.avatar,prototype.password,
  '0','0',sysdate(),'lead-e2e',sysdate(),${quote(ctx.marker)}
from sys_user seller
join sys_user prototype on prototype.user_name=${quote(ctx.alternateSalesPrototype)}
  and prototype.status='0' and prototype.del_flag='0'
where seller.user_name=${quote(ctx.seller)} and seller.status='0' and seller.del_flag='0'
  and not exists(select 1 from sys_user existing where existing.user_name=${quote(ctx.alternateSales)});
-- LEAD_E2E_POLICY_ADMIN_NAVIGATION: the disposable policy administrator needs
-- the parent route, page route and only the two assignment-policy operations.
set @policy_admin_role_key=concat('lead_e2e_policy_admin_',${quote(ctx.runId)});
insert into sys_role(
  role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,
  status,del_flag,create_by,create_time,remark
)
select 'Lead E2E policy admin',@policy_admin_role_key,98,'1',1,1,
  '0','0','lead-e2e',sysdate(),${quote(ctx.marker)}
where not exists(select 1 from sys_role where role_key=@policy_admin_role_key);
set @policy_admin_role_id=(select role_id from sys_role
  where role_key=@policy_admin_role_key and create_by='lead-e2e' and remark=${quote(ctx.marker)} limit 1);
insert ignore into sys_user_role(user_id,role_id)
select user_id,@policy_admin_role_id from sys_user
where user_name=${quote(ctx.policyAdmin)} and @policy_admin_role_id is not null;
insert ignore into sys_role_menu(role_id,menu_id)
select @policy_admin_role_id,m.menu_id from sys_menu m
where @policy_admin_role_id is not null
  and (
    (m.menu_type='M' and m.path='lead')
    or m.component='lead/policy/index'
    or m.perms in('lead:assignment-policy:list','lead:assignment-policy:edit','lead:setting:options','system:user:list','todo:definition:list')
  );
insert into biz_lead_setting(
  setting_type,setting_code,setting_name,color,order_num,status,create_by,create_time
) values('source',${quote(ctx.sourceCode)},${quote(`E2E ${ctx.marker}`)},'#0369a1',999,'0','lead-e2e',sysdate());

set @td003_version=(
  select v.version_id from todo_template t
  join todo_template_version v on v.template_id=t.template_id and v.version_no=t.current_version
  where t.template_code='TD-003'
);
set @retry_rule=cast(${quote(retryRule)} as json);
set @retry_rule=json_set(@retry_rule,'$.templateVersionId',@td003_version);
insert into biz_lead_assignment_policy(
  policy_code,policy_name,sales_dept_id,source_code,business_type,retry_rule_json,
  status,row_version,create_by,create_time
)
select ${quote(ctx.policyCode)},${quote(`E2E seven-window ${ctx.marker}`)},seller.dept_id,
  ${quote(ctx.sourceCode)},'LEAD',@retry_rule,'ACTIVE',0,'lead-e2e',sysdate()
from sys_user seller where seller.user_name=${quote(ctx.seller)};
set @policy_id=last_insert_id();
insert into biz_lead_assignment_policy_candidate(
  policy_id,user_id,sort_order,status,create_by,create_time
)
select @policy_id,user_id,
  case when user_name=${quote(ctx.seller)} then 0 else 1 end,
  'ACTIVE','lead-e2e',sysdate()
from sys_user where user_name in(${quote(ctx.seller)},${quote(ctx.alternateSales)});

${leadInsert(ctx, 'ASSIGN', { tagStatus: 'PENDING', unassigned: true })}
${leadInsert(ctx, 'LEAVE', { tagStatus: 'PENDING', unassigned: true })}
${leadInsert(ctx, 'VALID')}
${leadInsert(ctx, 'MANUAL')}
${leadInsert(ctx, 'TRUE_INVALID', { firstResult: 'SUSPECT_INVALID', invalidStatus: 'PENDING' })}
${leadInsert(ctx, 'MISJUDGED', { firstResult: 'SUSPECT_INVALID', invalidStatus: 'PENDING' })}
${leadInsert(ctx, 'RETAIN', { firstResult: 'UNREACHABLE', retryStage: 'T0' })}
${leadInsert(ctx, 'NEXT', { firstResult: 'UNREACHABLE', retryStage: 'T0' })}
${leadInsert(ctx, 'CONNECTED', { firstResult: 'UNREACHABLE', retryStage: 'T0' })}
${leadInsert(ctx, 'EXHAUSTED', { firstResult: 'UNREACHABLE', retryStage: 'T0' })}
${leadInsert(ctx, 'DEAD_POOL', {
    firstResult: 'SUSPECT_INVALID', invalidStatus: 'CONFIRMED', disposition: 'DEAD_POOL',
    firstStatus: 'COMPLETED', status: '4'
  })}
${leadInsert(ctx, 'AUTO_REVIEW', { firstResult: 'SUSPECT_INVALID', invalidStatus: 'PENDING' })}
${leadInsert(ctx, 'SLA')}
${leadInsert(ctx, 'AUTH', { firstResult: 'SUSPECT_INVALID', invalidStatus: 'PENDING' })}

${todoInsert(ctx, 'VALID_TODO', 'VALID', 'TD-001', 'td001', 'SUBMITTED', ctx.seller)}
${todoInsert(ctx, 'MANUAL_TODO', 'MANUAL', 'TD-001', 'td001', 'SUBMITTED', ctx.seller)}
${reviewFixture(ctx, 'TRUE_INVALID')}
${reviewFixture(ctx, 'MISJUDGED')}
${retryFixture(ctx, 'RETAIN', 3, true)}
${retryFixture(ctx, 'NEXT', 1, true)}
${retryFixture(ctx, 'CONNECTED', 2, true)}
${retryFixture(ctx, 'EXHAUSTED', 1, false)}
${reviewFixture(ctx, 'DEAD_POOL', 'TRUE_INVALID')}
${reviewFixture(ctx, 'AUTO_REVIEW')}
${todoInsert(ctx, 'SLA_TODO', 'SLA', 'TD-001', 'td001', 'IN_PROGRESS', ctx.seller)}
${reviewFixture(ctx, 'AUTH')}

update todo_instance
set due_at=date_sub(sysdate(),interval 1 minute),update_time=sysdate()
where todo_id=@AUTOREVIEW_REVIEW;

insert into todo_sla_record(
  todo_id,calendar_id,start_at,due_at,original_due_at,remind80_due_at,
  overdue100_due_at,escalate150_due_at,status,version
)
select @SLA_TODO,c.calendar_id,date_sub(sysdate(),interval 10 minute),
  date_sub(sysdate(),interval 3 minute),date_sub(sysdate(),interval 3 minute),
  date_sub(sysdate(),interval 5 minute),date_sub(sysdate(),interval 3 minute),
  date_sub(sysdate(),interval 1 minute),'RUNNING',0
from todo_work_calendar c where c.calendar_code='DEFAULT' and c.status='0';
update todo_instance
set due_at=date_sub(sysdate(),interval 3 minute),sla_status='NORMAL',update_time=sysdate()
where todo_id=@SLA_TODO;

update sys_dept sales_dept
join sys_user seller on seller.dept_id=sales_dept.dept_id
join sys_user supervisor on supervisor.user_name=${quote(ctx.supervisor)}
set sales_dept.leader=supervisor.user_name,
    sales_dept.update_by=concat('lead-e2e-',${quote(ctx.runId)}),
    sales_dept.update_time=sysdate()
where seller.user_name=${quote(ctx.seller)}
  and (sales_dept.leader is null or sales_dept.leader=supervisor.user_name);

insert into biz_business_tag(
  tag_code,tag_name,tag_level,applicable_business_type,status,create_by,create_time
) values(${quote(`LEAD_E2E_TAG_${ctx.runId}`)},${quote(`E2E source ${ctx.marker}`)},
  'SOURCE','LEAD','0','lead-e2e',sysdate());
set @e2e_tag=last_insert_id();
insert into biz_business_tag_rel(
  business_type,business_id,tag_id,tag_source,confirm_status,create_by,create_time
)
select 'LEAD',lead_id,@e2e_tag,'SYSTEM','PENDING','lead-e2e',sysdate()
from biz_lead where lead_no in(
  ${quote(ctx.prefix + 'ASSIGN')},${quote(ctx.prefix + 'LEAVE')});

update biz_lead
set dead_pool_time=sysdate(),dead_pool_reason='E2E confirmed invalid'
where lead_no=${quote(ctx.prefix + 'DEAD_POOL')};
insert into biz_lead_dead_pool_log(
  lead_id,action_type,reason_code,reason_detail,from_disposition,to_disposition,
  operator_id,source_todo_id,action_time,idempotency_key,create_by
)
select l.lead_id,'ENTER','NO_DEMAND','E2E confirmed invalid','ACTIVE','DEAD_POOL',
  reviewer.user_id,@DEADPOOL_REVIEW,sysdate(),${quote(ctx.prefix + 'DEAD_ENTER')},'lead-e2e'
from biz_lead l
join sys_user reviewer on reviewer.user_name=${quote(ctx.supervisor)}
where l.lead_no=${quote(ctx.prefix + 'DEAD_POOL')};

create temporary table tmp_lead_e2e_manifest_guard(
  lead_count int not null,policy_count int not null,source_count int not null,
  alternate_count int not null,
  constraint chk_lead_e2e_leads check(lead_count=${FIXTURE_CODES.length}),
  constraint chk_lead_e2e_policy check(policy_count=1),
  constraint chk_lead_e2e_source check(source_count=1),
  constraint chk_lead_e2e_disposable_alternate check(alternate_count=1)
) engine=innodb;
insert into tmp_lead_e2e_manifest_guard
select
  (select count(*) from biz_lead where lead_no in(${exactList(ctx.leadNos)})),
  (select count(*) from biz_lead_assignment_policy where policy_code=${quote(ctx.policyCode)}),
  (select count(*) from biz_lead_setting where setting_type='source' and setting_code=${quote(ctx.sourceCode)}),
  (select count(*) from sys_user alternate_user
    join sys_user seller on seller.user_name=${quote(ctx.seller)}
    where alternate_user.user_name=${quote(ctx.alternateSales)}
      and alternate_user.dept_id=seller.dept_id
      and alternate_user.create_by='lead-e2e' and alternate_user.remark=${quote(ctx.marker)});
drop temporary table tmp_lead_e2e_manifest_guard;
commit;
`
  runSql(setup, ctx.database)
  return fixtureManifest(ctx, dependencies)
}

function fixtureManifest(ctx = createRunContext(), dependencies = {}) {
  const rows = queryRows(`
select substring(lead_no,${ctx.prefix.length + 1}) code,lead_id,lead_no
from biz_lead where lead_no in(${exactList(ctx.leadNos)}) order by lead_no;`, ctx, dependencies)
  const leads = {}
  rows.forEach(([code, leadId, leadNo]) => {
    leads[code] = { leadId: Number(leadId), leadNo }
  })
  const policy = queryRows(`
select policy_id,policy_code,row_version from biz_lead_assignment_policy
where policy_code=${quote(ctx.policyCode)};`, ctx, dependencies)[0]
  if (Object.keys(leads).length !== FIXTURE_CODES.length || !policy) {
    throw new Error('Lead Todo E2E fixture setup is incomplete')
  }
  return {
    marker: ctx.marker,
    runId: ctx.runId,
    prefix: ctx.prefix,
    sourceCode: ctx.sourceCode,
    leads,
    policy: { policyId: Number(policy[0]), policyCode: policy[1], rowVersion: Number(policy[2]) }
  }
}

function queryRows(sql, ctx = createRunContext(), dependencies = {}) {
  const runSql = dependencies.executeSql || executeSql
  const output = runSql(sql, ctx.database)
  return String(output).trim().split(/\r?\n/).filter(Boolean).map(line => line.split('\t'))
}

function leadState(leadNo, ctx = createRunContext(), dependencies = {}) {
  const row = queryRows(`
select json_object(
  'leadNo',l.lead_no,'disposition',l.disposition,'firstContactResult',l.first_contact_result,
  'tagConfirmStatus',l.tag_confirm_status,'ownerId',l.owner_id,
  'ownerName',(select user_name from sys_user where user_id=l.owner_id),
  'assignedEventStatus',(select e.event_status from business_event e
    where e.aggregate_type='LEAD' and e.aggregate_id=l.lead_id and e.event_type='LEAD_ASSIGNED'
    order by e.event_id desc limit 1),
  'assignedEventError',(select e.error_message from business_event e
    where e.aggregate_type='LEAD' and e.aggregate_id=l.lead_id and e.event_type='LEAD_ASSIGNED'
    order by e.event_id desc limit 1),
  'td001Owner',(select u.user_name from todo_instance t join sys_user u on u.user_id=t.owner_id
    where t.business_type='LEAD' and t.business_id=l.lead_id and t.template_code='TD-001'
    order by t.todo_id desc limit 1),
  'invalidReviewStatus',l.invalid_review_status,'retryStage',l.retry_stage,
  'retryAttemptCount',l.retry_attempt_count,
  'td001Open',(select count(*) from todo_instance t where t.business_type='LEAD'
    and t.business_id=l.lead_id and t.template_code='TD-001' and t.status<>'COMPLETED'),
  'td004Count',(select count(*) from todo_instance t where t.business_type='LEAD'
    and t.business_id=l.lead_id and t.template_code='TD-004'),
  'manualCalls',(select count(*) from biz_lead_call_record c where c.lead_id=l.lead_id
    and c.call_channel='MANUAL'),
  'retryFacts',(select count(*) from biz_lead_retry_record r where r.lead_id=l.lead_id),
  'latestRetryResult',(select r.contact_result from biz_lead_retry_record r
    where r.lead_id=l.lead_id order by r.retry_record_id desc limit 1),
  'activeTodoCount',(select count(*) from todo_instance t where t.business_type='LEAD'
    and t.business_id=l.lead_id and t.status not in ('COMPLETED','CANCELLED')),
  'pendingWindows',(select count(*) from todo_schedule_plan p
    join todo_schedule_window w on w.plan_id=p.plan_id
    where p.business_type='LEAD' and p.business_id=l.lead_id and w.status='PENDING'),
  'cancelledWindows',(select count(*) from todo_schedule_plan p
    join todo_schedule_window w on w.plan_id=p.plan_id
    where p.business_type='LEAD' and p.business_id=l.lead_id and w.status='CANCELLED'),
  'restoreCount',(select count(*) from biz_lead_dead_pool_log d
    where d.lead_id=l.lead_id and d.action_type='RESTORE'),
  'reviewTodoId',(select t.todo_id from todo_instance t
    where t.business_type='LEAD' and t.business_id=l.lead_id and t.template_code='TD-002'
    order by t.todo_id desc limit 1),
  'reviewStatus',(select r.status from biz_lead_invalid_review r
    where r.lead_id=l.lead_id order by r.review_id desc limit 1),
  'reviewResult',(select r.review_result from biz_lead_invalid_review r
    where r.lead_id=l.lead_id order by r.review_id desc limit 1),
  'reviewerUser',(select reviewer.user_name from biz_lead_invalid_review r
    left join sys_user reviewer on reviewer.user_id=r.reviewer_id
    where r.lead_id=l.lead_id order by r.review_id desc limit 1),
  'reviewSystemDefault',(select r.system_default from biz_lead_invalid_review r
    where r.lead_id=l.lead_id order by r.review_id desc limit 1),
  'deadPoolEnterCount',(select count(*) from biz_lead_dead_pool_log d
    where d.lead_id=l.lead_id and d.action_type='ENTER'),
  'invalidConfirmedEventCount',(select count(*) from business_event e
    where e.aggregate_type='LEAD' and e.aggregate_id=l.lead_id
      and e.event_type='LEAD_INVALID_REVIEW_CONFIRMED')
)
from biz_lead l where l.lead_no=${quote(leadNo)};`, ctx, dependencies)[0]
  if (!row) throw new Error(`No E2E Lead state found for ${leadNo}`)
  return JSON.parse(row[0])
}

function todoRuntimeState(leadNo, templateCode, ctx = createRunContext(), dependencies = {}) {
  const row = queryRows(`
select json_object(
  'todoId',t.todo_id,'status',t.status,'slaStatus',t.sla_status,'version',t.version,
  'remind80At',s.remind80_at,'overdue100At',s.overdue100_at,'escalate150At',s.escalate150_at,
  'ownerReminderCount',(select count(*) from todo_notification n
    where n.todo_id=t.todo_id and n.user_id=t.owner_id
      and n.notification_type in('REMINDED_80','OVERDUE_100','ESCALATED_150')),
  'supervisorEscalationCount',(select count(*) from todo_notification n
    join sys_user supervisor on supervisor.user_id=n.user_id
    where n.todo_id=t.todo_id and supervisor.user_name=${quote(ctx.supervisor)}
      and n.notification_type='ESCALATED_150'),
  'autoActionSuccessCount',(select count(*) from todo_auto_action_execution a
    where a.todo_id=t.todo_id and a.status='SUCCESS'),
  'actionCount',(select count(*) from todo_action_log a where a.todo_id=t.todo_id)
)
from todo_instance t
join biz_lead l on l.lead_id=t.business_id and t.business_type='LEAD'
left join todo_sla_record s on s.todo_id=t.todo_id
where l.lead_no=${quote(leadNo)} and t.template_code=${quote(templateCode)}
order by t.todo_id desc limit 1;`, ctx, dependencies)[0]
  if (!row) throw new Error(`No E2E Todo state found for ${leadNo}/${templateCode}`)
  return JSON.parse(row[0])
}

function slaWorkerState(ctx = createRunContext(), dependencies = {}) {
  const row = queryRows(`
select json_object(
  'jobId',j.job_id,'status',j.status,'cronExpression',j.cron_expression,
  'successfulRuns',(select count(*) from sys_job_log l
    where l.invoke_target=j.invoke_target and l.status='0'),
  'lastSuccessAt',(select max(l.end_time) from sys_job_log l
    where l.invoke_target=j.invoke_target and l.status='0')
)
from sys_job j where j.invoke_target='todoSlaTask.scan' limit 1;`, ctx, dependencies)[0]
  if (!row) throw new Error('Todo SLA production worker is not registered')
  return JSON.parse(row[0])
}

function makeSellerUnavailable(ctx = createRunContext(), dependencies = {}) {
  return queryRows(`
insert into sys_user_availability(
  user_id,status,effective_from,effective_to,reason,create_by,create_time,update_time
)
select user_id,'UNAVAILABLE',date_sub(sysdate(),interval 1 minute),
  date_add(sysdate(),interval 1 day),'E2E leave skip',
  concat('lead-e2e-',${quote(ctx.runId)}),sysdate(),sysdate()
from sys_user where user_name=${quote(ctx.seller)};
select count(*) from sys_user_availability availability
join sys_user user_account on user_account.user_id=availability.user_id
where user_account.user_name=${quote(ctx.seller)}
  and availability.create_by=concat('lead-e2e-',${quote(ctx.runId)})
  and availability.status='UNAVAILABLE'
  and availability.effective_from<=sysdate()
  and availability.effective_to>sysdate();`, ctx, dependencies)
}

function evidenceNames(leadNo, ctx = createRunContext(), dependencies = {}) {
  return queryRows(`
select v.original_file_name
from biz_lead l
join file_business_relation r on r.business_type='LEAD' and r.business_id=l.lead_id and r.active=1
join file_object f on f.file_object_id=r.file_object_id and f.status='ACTIVE'
join file_object_version v on v.file_object_id=f.file_object_id and v.version_no=f.current_version_no
where l.lead_no=${quote(leadNo)}
order by v.file_version_id;`, ctx, dependencies).map(row => row[0])
}

function policySnapshot(ctx = createRunContext(), dependencies = {}) {
  const row = queryRows(`
select json_object(
  'rowVersion',row_version,
  'windows',json_extract(retry_rule_json,'$.windows')
)
from biz_lead_assignment_policy where policy_code=${quote(ctx.policyCode)};`, ctx, dependencies)[0]
  if (!row) throw new Error('E2E assignment policy disappeared')
  return JSON.parse(row[0])
}

function ownershipManifest(ctx, dependencies = {}) {
  const leadRows = queryRows(`
select lead_id,lead_no from biz_lead
where lead_no in(${exactList(ctx.leadNos)}) order by lead_id;`, ctx, dependencies)
  const leadIds = leadRows.map(row => Number(row[0]))
  const todoRows = queryRows(`
select todo_id from todo_instance where business_type='LEAD'
and business_id in(select lead_id from biz_lead where lead_no in(${exactList(ctx.leadNos)}))
order by todo_id;`, ctx, dependencies)
  const planRows = queryRows(`
select plan_id from todo_schedule_plan where business_type='LEAD'
and business_id in(select lead_id from biz_lead where lead_no in(${exactList(ctx.leadNos)}))
order by plan_id;`, ctx, dependencies)
  const policyRows = queryRows(`
select policy_id from biz_lead_assignment_policy
where policy_code=${quote(ctx.policyCode)} order by policy_id;`, ctx, dependencies)
  const fileRows = queryRows(`
select distinct r.file_object_id,r.relation_id,v.version_no,v.object_key,f.status,r.active,
  coalesce(all_scope.active_relation_count,0) active_relation_count
from file_business_relation r
join file_object f on f.file_object_id=r.file_object_id
join file_object_version v on v.file_object_id=r.file_object_id
left join (
  select file_object_id,count(1) active_relation_count
  from file_business_relation where active=1 group by file_object_id
) all_scope on all_scope.file_object_id=r.file_object_id
where r.business_type='LEAD'
and r.business_id in(select lead_id from biz_lead where lead_no in(${exactList(ctx.leadNos)}))
  order by r.file_object_id,r.relation_id,v.version_no;`, ctx, dependencies)
  const files = new Map()
  fileRows.forEach(([fileObjectId, relationId, , objectKey, status, active, activeRelationCount]) => {
    const id = Number(fileObjectId)
    if (!files.has(id)) {
      files.set(id, {
        fileObjectId: id,
        relationIds: new Set(),
        activeFixtureRelationIds: new Set(),
        objectKeys: new Set(),
        totalActiveRelationCount: Number(activeRelationCount),
        objectRetired: status === 'DISABLED'
      })
    }
    files.get(id).relationIds.add(Number(relationId))
    if (Number(active) === 1) files.get(id).activeFixtureRelationIds.add(Number(relationId))
    files.get(id).objectKeys.add(objectKey)
    files.get(id).totalActiveRelationCount = Math.max(
      files.get(id).totalActiveRelationCount,
      Number(activeRelationCount)
    )
  })
  return {
    leadIds,
    todoIds: todoRows.map(row => Number(row[0])),
    planIds: planRows.map(row => Number(row[0])),
    policyIds: policyRows.map(row => Number(row[0])),
    files: [...files.values()].map(file => ({
      fileObjectId: file.fileObjectId,
      relationIds: [...file.relationIds],
      activeFixtureRelationIds: [...file.activeFixtureRelationIds],
      objectKeys: [...file.objectKeys],
      exclusive: file.totalActiveRelationCount === file.activeFixtureRelationIds.size,
      objectRetired: file.objectRetired
    }))
  }
}

async function retireOwnedFiles(capability, ownership, dependencies = {}) {
  const verified = requireVerifiedRun(capability, dependencies)
  const ctx = verified.context
  const apiRequest = verified.apiRequest
  if (!ownership.files.length) return
  if (!apiRequest || typeof apiRequest.post !== 'function' || typeof apiRequest.get !== 'function') {
    throw new Error('An authenticated Playwright request context is required to retire E2E file objects')
  }
  for (const file of ownership.files) {
    for (let index = 0; index < file.activeFixtureRelationIds.length; index++) {
      const relationId = file.activeFixtureRelationIds[index]
      const retireObjectIfUnreferenced = file.exclusive &&
        index === file.activeFixtureRelationIds.length - 1
      const request = {
        headers: {
          'X-E2E-Identity-Secret': ctx.identitySecret,
          'X-E2E-Run-Nonce': ctx.nonce,
          'X-E2E-Fixture-Marker': ctx.marker
        },
        data: {
          actionId: `lead-e2e-retire-${ctx.runId}-${file.fileObjectId}-${relationId}`,
          relationId,
          runId: ctx.runId,
          database: ctx.database
        }
      }
      const response = await apiRequest.post(
        `/foundation/e2e/files/${file.fileObjectId}/retire-owned-fixture`, request)
      const body = await response.json().catch(() => ({}))
      if (!response.ok() || Number(body.code || 200) !== 200) {
        throw new Error(`Production file retire failed for ${file.fileObjectId}: ${body.msg || response.status()}`)
      }
      if (retireObjectIfUnreferenced && !body.data?.objectRetired) {
        throw new Error(`Exclusive file ${file.fileObjectId} was not retired after its final relation`)
      }
      if (body.data?.objectRetired) file.objectRetired = true

      // The exact same signed cleanup command must be safely replayable after
      // its relation has become inactive; this exercises production idempotency.
      const replay = await apiRequest.post(
        `/foundation/e2e/files/${file.fileObjectId}/retire-owned-fixture`, request)
      const replayBody = await replay.json().catch(() => ({}))
      if (!replay.ok() || Number(replayBody.code || 200) !== 200 ||
          Number(replayBody.data?.fileObjectId) !== file.fileObjectId ||
          Number(replayBody.data?.relationId) !== relationId ||
          Boolean(replayBody.data?.objectRetired) !== Boolean(body.data?.objectRetired)) {
        throw new Error(`Production file retire idempotency replay failed for ${file.fileObjectId}`)
      }
    }
    for (const relationId of file.relationIds) {
      const token = await apiRequest.get(
        `/files/${file.fileObjectId}/download-token?relationId=${relationId}`
      )
      const tokenBody = await token.json().catch(() => ({}))
      if (token.ok() && Number(tokenBody.code || 200) === 200) {
        throw new Error(`Retired file ${file.fileObjectId} still issued a download token`)
      }
    }
  }
  await waitForRetiredStorage(ctx, ownership, dependencies)
}

async function waitForRetiredStorage(ctx, ownership, dependencies = {}) {
  const sleep = dependencies.sleep || (milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds)))
  const exists = dependencies.storageExists || (key => fs.existsSync(storagePath(ctx, key)))
  const ids = ownership.files.map(file => file.fileObjectId)
  const relationIds = ownership.files.flatMap(file => file.relationIds)
  const retired = ownership.files.filter(file => file.objectRetired)
  const retiredIds = retired.map(file => file.fileObjectId)
  if (!ids.length) return
  for (let attempt = 0; attempt < 40; attempt++) {
    const row = queryRows(`
select
  (select count(*) from file_object where file_object_id in(${retiredIds.length ? retiredIds.join(',') : '0'}) and status<>'DISABLED'),
  (select count(*) from file_business_relation where relation_id in(${relationIds.length ? relationIds.join(',') : '0'}) and active=1),
  (select count(*) from file_storage_cleanup where file_object_id in(${retiredIds.length ? retiredIds.join(',') : '0'})
    and status in('PENDING','FAILED'));`, ctx, dependencies)[0]
    const physical = retired.flatMap(file => file.objectKeys).filter(exists)
    if (row && row.every(value => Number(value) === 0) && !physical.length) return
    await sleep(250)
  }
  throw new Error('Retired E2E file objects did not reach terminal cleanup state')
}

function storagePath(ctx, objectKey) {
  const target = path.resolve(ctx.storageRoot, String(objectKey || ''))
  if (target !== ctx.storageRoot && !target.startsWith(`${ctx.storageRoot}${path.sep}`)) {
    throw new Error('E2E file object key escaped the configured storage root')
  }
  return target
}

async function cleanupLeadTodoFixtures(capability, options = {}) {
  const { context: ctx } = requireVerifiedRun(capability, options)
  const ownership = ownershipManifest(ctx, options)
  await retireOwnedFiles(capability, ownership, options)
  const cleanup = `
set names utf8mb4 collate utf8mb4_unicode_ci;
start transaction;
set @policy_admin_role_key=concat('lead_e2e_policy_admin_',${quote(ctx.runId)});
set @policy_admin_role_id=(select role_id from sys_role
  where role_key=@policy_admin_role_key and create_by='lead-e2e' and remark=${quote(ctx.marker)} limit 1);
create temporary table tmp_lead_e2e_leads as
select lead_id from biz_lead where lead_no in(${exactList(ctx.leadNos)});
create temporary table tmp_lead_e2e_todos as
select todo_id,root_todo_id from todo_instance
where business_type='LEAD' and business_id in(select lead_id from tmp_lead_e2e_leads);
create temporary table tmp_lead_e2e_plans as
select plan_id from todo_schedule_plan where business_type='LEAD'
and business_id in(select lead_id from tmp_lead_e2e_leads);

delete from business_event where aggregate_type='LEAD'
  and aggregate_id in(select lead_id from tmp_lead_e2e_leads);
delete from todo_schedule_occurrence where plan_id in(select plan_id from tmp_lead_e2e_plans);
delete from todo_schedule_window where plan_id in(select plan_id from tmp_lead_e2e_plans);
delete from todo_schedule_plan where plan_id in(select plan_id from tmp_lead_e2e_plans);
delete from biz_lead_retry_record where lead_id in(select lead_id from tmp_lead_e2e_leads);
delete from biz_lead_dead_pool_log where lead_id in(select lead_id from tmp_lead_e2e_leads);
delete from biz_lead_quality_record where lead_id in(select lead_id from tmp_lead_e2e_leads);
delete from biz_lead_invalid_review where lead_id in(select lead_id from tmp_lead_e2e_leads);
delete from biz_lead_call_record where lead_id in(select lead_id from tmp_lead_e2e_leads);
delete from todo_auto_action_audit where todo_id in(select todo_id from tmp_lead_e2e_todos);
delete from todo_auto_action_execution where todo_id in(select todo_id from tmp_lead_e2e_todos);
delete from todo_extension_action where todo_id in(select todo_id from tmp_lead_e2e_todos);
delete from todo_extension_request where todo_id in(select todo_id from tmp_lead_e2e_todos);
delete from todo_cycle_occurrence where todo_id in(select todo_id from tmp_lead_e2e_todos);
delete from todo_exception_log where todo_id in(select todo_id from tmp_lead_e2e_todos);
delete from todo_notification where todo_id in(select todo_id from tmp_lead_e2e_todos);
delete from todo_sla_waiver where todo_id in(select todo_id from tmp_lead_e2e_todos);
delete from todo_sla_record where todo_id in(select todo_id from tmp_lead_e2e_todos);
delete from todo_attachment where todo_id in(select todo_id from tmp_lead_e2e_todos);
delete from todo_action_log where todo_id in(select todo_id from tmp_lead_e2e_todos);
delete from todo_candidate where todo_id in(select todo_id from tmp_lead_e2e_todos);
delete from todo_cc where todo_id in(select todo_id from tmp_lead_e2e_todos);
delete from todo_relation where todo_id in(select todo_id from tmp_lead_e2e_todos);
delete from todo_route_join where root_todo_id in(select root_todo_id from tmp_lead_e2e_todos);
delete from todo_route_token where root_todo_id in(select root_todo_id from tmp_lead_e2e_todos);
delete from todo_instance where todo_id in(select todo_id from tmp_lead_e2e_todos);
delete from biz_business_tag_rel where business_type='LEAD'
  and business_id in(select lead_id from tmp_lead_e2e_leads);
delete from biz_lead_followup where lead_id in(select lead_id from tmp_lead_e2e_leads);
delete from biz_lead_assignment_log where lead_id in(select lead_id from tmp_lead_e2e_leads);
delete from biz_lead where lead_id in(select lead_id from tmp_lead_e2e_leads);
delete from biz_lead_assignment_policy_candidate where policy_id in(
  select policy_id from biz_lead_assignment_policy where policy_code=${quote(ctx.policyCode)});
delete from todo_round_robin_cursor where strategy_key=concat('LEAD_ASSIGNMENT_POLICY:',(
  select policy_id from biz_lead_assignment_policy where policy_code=${quote(ctx.policyCode)}));
delete from biz_lead_assignment_policy where policy_code=${quote(ctx.policyCode)};
delete from biz_lead_setting where setting_type='source' and setting_code=${quote(ctx.sourceCode)};
delete from sys_user_availability where create_by=concat('lead-e2e-',${quote(ctx.runId)});
update sys_dept sales_dept
join sys_user seller on seller.dept_id=sales_dept.dept_id
set sales_dept.leader=null,sales_dept.update_by=null,sales_dept.update_time=sysdate()
where seller.user_name=${quote(ctx.seller)}
  and sales_dept.leader=${quote(ctx.supervisor)}
  and sales_dept.update_by=concat('lead-e2e-',${quote(ctx.runId)});
delete from biz_business_tag where tag_code=${quote(`LEAD_E2E_TAG_${ctx.runId}`)}
  and create_by='lead-e2e';
delete from sys_role_menu where role_id=@policy_admin_role_id;
delete from sys_user_role where role_id=@policy_admin_role_id;
delete from sys_role where role_key=@policy_admin_role_key
  and create_by='lead-e2e' and remark=${quote(ctx.marker)};
delete from sys_user where user_name=${quote(ctx.alternateSales)}
  and create_by='lead-e2e' and remark=${quote(ctx.marker)};
drop temporary table tmp_lead_e2e_plans;
drop temporary table tmp_lead_e2e_todos;
drop temporary table tmp_lead_e2e_leads;
commit;`
  const runSql = options.executeSql || executeSql
  runSql(cleanup, ctx.database)
  return ownership
}

function assertNoLeadTodoFixtures(existingContext,
  ownership = { leadIds: [], todoIds: [], planIds: [], policyIds: [], files: [] },
  dependencies = {}) {
  const ctx = existingContext || createRunContext()
  const leadIds = ownership.leadIds.length ? ownership.leadIds.join(',') : '0'
  const todoIds = ownership.todoIds.length ? ownership.todoIds.join(',') : '0'
  const planIds = ownership.planIds.length ? ownership.planIds.join(',') : '0'
  const policyIds = ownership.policyIds?.length ? ownership.policyIds.join(',') : '0'
  const fileIds = ownership.files.length ? ownership.files.map(file => file.fileObjectId).join(',') : '0'
  const relationIds = ownership.files.length
    ? ownership.files.flatMap(file => file.relationIds).join(',')
    : '0'
  const retiredFiles = ownership.files.filter(file => file.objectRetired)
  const retiredFileIds = retiredFiles.length ? retiredFiles.map(file => file.fileObjectId).join(',') : '0'
  const row = queryRows(`
select
  (select count(*) from biz_lead where lead_no in(${exactList(ctx.leadNos)})),
  (select count(*) from todo_instance where todo_id in(${todoIds})),
  (select count(*) from todo_schedule_plan where plan_id in(${planIds})),
  (select count(*) from todo_schedule_window where plan_id in(${planIds})),
  (select count(*) from todo_schedule_occurrence where plan_id in(${planIds})),
  (select count(*) from business_event where aggregate_type='LEAD' and aggregate_id in(${leadIds})),
  (select count(*) from biz_lead_retry_record where lead_id in(${leadIds})),
  (select count(*) from biz_lead_call_record where lead_id in(${leadIds})),
  (select count(*) from biz_lead_invalid_review where lead_id in(${leadIds})),
  (select count(*) from biz_lead_dead_pool_log where lead_id in(${leadIds})),
  (select count(*) from biz_lead_quality_record where lead_id in(${leadIds})),
  (select count(*) from biz_lead_followup where lead_id in(${leadIds})),
  (select count(*) from biz_lead_assignment_log where lead_id in(${leadIds})),
  (select count(*) from biz_business_tag_rel where business_type='LEAD' and business_id in(${leadIds})),
  (select count(*) from todo_auto_action_audit where todo_id in(${todoIds})),
  (select count(*) from todo_auto_action_execution where todo_id in(${todoIds})),
  (select count(*) from todo_extension_action where todo_id in(${todoIds})),
  (select count(*) from todo_extension_request where todo_id in(${todoIds})),
  (select count(*) from todo_cycle_occurrence where todo_id in(${todoIds})),
  (select count(*) from todo_exception_log where todo_id in(${todoIds})),
  (select count(*) from todo_notification where todo_id in(${todoIds})),
  (select count(*) from todo_sla_waiver where todo_id in(${todoIds})),
  (select count(*) from todo_sla_record where todo_id in(${todoIds})),
  (select count(*) from todo_attachment where todo_id in(${todoIds})),
  (select count(*) from todo_action_log where todo_id in(${todoIds})),
  (select count(*) from todo_candidate where todo_id in(${todoIds})),
  (select count(*) from todo_cc where todo_id in(${todoIds})),
  (select count(*) from todo_relation where todo_id in(${todoIds})),
  (select count(*) from todo_route_join where root_todo_id in(${todoIds})),
  (select count(*) from todo_route_token where root_todo_id in(${todoIds})),
  (select count(*) from biz_lead_assignment_policy where policy_code=${quote(ctx.policyCode)}),
  (select count(*) from biz_lead_assignment_policy_candidate where policy_id in(
    select policy_id from biz_lead_assignment_policy where policy_code=${quote(ctx.policyCode)})),
  (select count(*) from todo_round_robin_cursor
    where strategy_key in(select concat('LEAD_ASSIGNMENT_POLICY:',policy_id)
      from biz_lead_assignment_policy where policy_id in(${policyIds}))),
  (select count(*) from biz_lead_setting where setting_type='source' and setting_code=${quote(ctx.sourceCode)}),
  (select count(*) from sys_user_availability
    where create_by=concat('lead-e2e-',${quote(ctx.runId)})),
  (select count(*) from biz_business_tag
    where tag_code=${quote(`LEAD_E2E_TAG_${ctx.runId}`)} and create_by='lead-e2e'),
  (select count(*) from sys_role
    where role_key=concat('lead_e2e_policy_admin_',${quote(ctx.runId)})
      and create_by='lead-e2e' and remark=${quote(ctx.marker)}),
  (select count(*) from sys_user where user_name=${quote(ctx.alternateSales)}
    and create_by='lead-e2e' and remark=${quote(ctx.marker)}),
  (select count(*) from sys_dept
    where update_by=concat('lead-e2e-',${quote(ctx.runId)})),
  (select count(*) from file_business_relation where relation_id in(${relationIds}) and active=1),
  (select count(*) from file_object where file_object_id in(${retiredFileIds}) and status<>'DISABLED'),
  (select count(*) from file_storage_cleanup where file_object_id in(${retiredFileIds}) and status<>'COMPLETED');`,
  ctx, dependencies)[0]
  const physical = retiredFiles.flatMap(file => file.objectKeys)
    .filter(key => (dependencies.storageExists || (value => fs.existsSync(storagePath(ctx, value))))(key))
  if (!row || row.some(value => Number(value) !== 0) || physical.length) {
    throw new Error(`Lead Todo E2E cleanup leaked state: ${JSON.stringify({ row, physical })}`)
  }
  const fileAudit = queryRows(`
select
  (select count(*) from file_object where file_object_id in(${retiredFileIds}) and status='DISABLED'),
  (select count(*) from file_object_version where file_object_id in(${retiredFileIds})),
  (select count(*) from file_storage_cleanup where file_object_id in(${retiredFileIds}) and status='COMPLETED');`,
  ctx, dependencies)[0]
  if (retiredFiles.length && (!fileAudit || fileAudit.some(value => Number(value) < 1))) {
    throw new Error(`Retired file lifecycle audit is incomplete: ${JSON.stringify(fileAudit)}`)
  }
}

async function withLeadTodoFixtures(capability, options, work) {
  requireVerifiedRun(capability, options)
  const ctx = verifiedRuns.get(capability).context
  let fixtures
  let cleanupEvidence
  let setupAttempted = false
  try {
    setupAttempted = true
    fixtures = setupLeadTodoFixtures(capability, options)
    return await work(fixtures)
  } finally {
    if (setupAttempted) {
      cleanupEvidence = await cleanupLeadTodoFixtures(capability, options)
      assertNoLeadTodoFixtures(ctx, cleanupEvidence, options)
    }
  }
}

async function withVerifiedLeadTodoFixtures(ctx, options, work) {
  const apiRequest = options && options.apiRequest
  const capability = await verifyBackendIdentity(ctx, apiRequest)
  return withLeadTodoFixtures(capability, options, work)
}

module.exports = {
  WINDOW_CODES,
  FIXTURE_CODES,
  createRunContext,
  backendIdentityProof,
  verifyBackendIdentity,
  setupLeadTodoFixtures,
  cleanupLeadTodoFixtures,
  assertNoLeadTodoFixtures,
  withLeadTodoFixtures,
  withVerifiedLeadTodoFixtures,
  ownershipManifest,
  leadState,
  todoRuntimeState,
  slaWorkerState,
  makeSellerUnavailable,
  evidenceNames,
  policySnapshot
}
