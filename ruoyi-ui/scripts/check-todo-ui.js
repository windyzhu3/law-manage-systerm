const fs = require('fs')
const required = [
  'src/api/todo.js',
  'src/views/todo/index.vue',
  'src/views/todo/components/TodoDetailDrawer.vue',
  'src/views/todo/components/TodoActionDialogs.vue',
  'src/views/todo/components/TodoSummaryCard.vue',
  'src/views/todo/components/TodoRelationPanel.vue'
  ,'src/api/todo-definition.js'
  ,'src/views/todo/config/template/index.vue'
  ,'src/views/todo/config/sla/index.vue'
  ,'src/views/todo/config/dod/index.vue'
  ,'src/views/todo/config/trigger/index.vue'
  ,'src/views/todo/config/simulation/index.vue'
  ,'src/views/todo/config/release/index.vue'
  ,'src/views/todo/config/components/TemplateList.vue'
  ,'src/views/todo/config/components/VersionDrawer.vue'
  ,'src/views/todo/config/components/DefinitionForm.vue'
  ,'src/views/todo/config/components/EventConditionBuilder.vue'
  ,'src/views/todo/config/components/OwnerRuleBuilder.vue'
  ,'src/views/todo/config/components/DodFormBuilder.vue'
  ,'src/views/todo/config/components/SlaRuleBuilder.vue'
  ,'src/views/todo/config/definition-codec.js'
  ,'src/views/todo/config/components/TriggerRuleTable.vue'
  ,'src/views/todo/config/components/WorkCalendarTable.vue'
  ,'src/views/todo/config/components/FoundationResourceReadiness.vue'
  ,'src/views/todo/config/components/HistoricalMigrationReadiness.vue'
  ,'src/views/todo/config/components/FileSecurityReadiness.vue'
  ,'src/views/todo/config/components/FinanceReadiness.vue'
  ,'src/views/todo/config/components/AcceptanceReadiness.vue'
  ,'src/views/todo/config/components/AcceptanceScenarioDialog.vue'
  ,'src/views/todo/config/components/AcceptanceMappingDialog.vue'
  ,'src/views/todo/config/components/FoundationAdmissionOverview.vue'
  ,'src/api/todo-operations.js'
  ,'src/views/todo/operations/index.vue'
  ,'src/views/todo/operations/components/OperationsMetrics.vue'
  ,'src/views/todo/operations/components/EventFailureTable.vue'
  ,'src/views/todo/operations/components/EscalationTable.vue'
  ,'src/views/todo/operations/components/ExceptionActionDialog.vue'
  ,'src/views/todo/components/BusinessTodoSummary.vue'
  ,'src/views/todo/components/BusinessTodoDrawer.vue'
  ,'src/views/todo/components/TodoChainTimeline.vue'
  ,'src/components/BusinessFile/BusinessFilePicker.vue'
]
for (const file of required) {
  if (!fs.existsSync(file)) throw new Error(`missing ${file}`)
}
const api = fs.readFileSync('src/api/todo.js', 'utf8')
for (const name of ['getTodoDashboard', 'listTodo', 'getTodo', 'claimTodo', 'startTodo', 'submitTodo', 'completeTodo', 'returnTodo', 'transferTodo', 'cancelTodo', 'addTodoAttachment', 'getBusinessFilePreviewToken', 'getBusinessFileDownloadToken', 'listBusinessFileVersions', 'openBusinessFileContent']) {
  if (!api.includes(`export function ${name}`)) throw new Error(`missing api ${name}`)
}
const page = fs.readFileSync('src/views/todo/index.vue', 'utf8')
for (const marker of ['todo-detail-drawer', 'todo-action-dialogs', "v-hasPermi", 'candidate', 'overdue']) {
  if (!page.includes(marker)) throw new Error(`missing page marker ${marker}`)
}
const detail = fs.readFileSync('src/views/todo/components/TodoDetailDrawer.vue', 'utf8')
for (const marker of ['detail.todo', 'detail.actions', 'detail.attachments', 'detail.materials', 'detail.relations']) {
  if (!detail.includes(marker)) throw new Error(`missing detail aggregate marker ${marker}`)
}
const actions = fs.readFileSync('src/views/todo/components/TodoActionDialogs.vue', 'utf8')
for (const marker of ['todo-dynamic-form', 'getTodoForm', 'fileObjectIds', 'createActionPayload']) if (!actions.includes(marker)) throw new Error(`missing schema runtime action marker ${marker}`)
if (/template_?code|templateCode/i.test(actions)) throw new Error('todo actions must not branch by template code')
const filePicker = fs.readFileSync('src/components/BusinessFile/BusinessFilePicker.vue', 'utf8')
for (const marker of ['relationId', '预览', '下载', '版本', '未授权', 'getBusinessFilePreviewToken', 'getBusinessFileDownloadToken', 'listBusinessFileVersions', 'openBusinessFileContent']) if (!filePicker.includes(marker)) throw new Error(`missing authorized file access marker ${marker}`)
console.log('todo ui contract ok')

const definitionApi = fs.readFileSync('src/api/todo-definition.js', 'utf8')
for (const name of ['listDefinitions','copyDefinition','copyDefinitionVersion','updateDefinitionDraft','publishDefinition','listTriggerRules','listWorkCalendars','buildVirtualTaskCompletion']) if (!definitionApi.includes(`export function ${name}`)) throw new Error(`missing definition api ${name}`)
for (const marker of ['taskCompletions','nodeKey','occurrence','payload','completedAt']) if (!definitionApi.includes(marker)) throw new Error(`missing simulation completion sample marker ${marker}`)
const definitionForm = fs.readFileSync('src/views/todo/config/components/DefinitionForm.vue','utf8')
for (const marker of ['event-condition-builder','owner-rule-builder','dod-form-builder','sla-rule-builder','hydrateDefinition','serializeDefinition','规则预览']) if (!definitionForm.includes(marker)) throw new Error(`missing visual definition marker ${marker}`)
const definitionCodec = fs.readFileSync('src/views/todo/config/definition-codec.js','utf8')
for (const marker of ['hydrateDefinition','serializeDefinition','toDraftPayload','autoActions','acceptanceRefs']) if (!definitionCodec.includes(marker)) throw new Error(`missing definition codec marker ${marker}`)
const autoActionEditor=fs.readFileSync('src/views/todo/config/components/AutoActionEditor.vue','utf8')
for(const marker of ['field.type === \'number\'','el-input-number','el-input','required && !String(value || \'\').trim()','listTodoAutoActionCapabilities']) if(!autoActionEditor.includes(marker)) throw new Error(`missing catalog-driven auto action field marker ${marker}`)
const configPage=[
  'src/views/todo/config/template/index.vue',
  'src/views/todo/config/template/TemplateDrawer.vue',
  'src/views/todo/config/template/steps/TemplateVersionStep.vue',
  'src/views/todo/config/components/TemplateList.vue',
  'src/views/todo/config/components/VersionDrawer.vue'
].map(file=>fs.readFileSync(file,'utf8')).join('\n')
for(const marker of ["v-hasPermi",'copyTodoTemplate','publishReleaseRecord','publishedReadOnly']) if(!configPage.includes(marker)) throw new Error(`missing config marker ${marker}`)

const operationsApi=fs.readFileSync('src/api/todo-operations.js','utf8')
for(const name of ['getOperationsDashboard','listDeadEvents','replayDeadEvent','forceCompleteTodo','forceCancelTodo','regenerateTodo','batchTransferTodos','waiveTodoSla']) if(!operationsApi.includes(`export function ${name}`)) throw new Error(`missing operations api ${name}`)
const operations=operationsApi+fs.readFileSync('src/views/todo/operations/index.vue','utf8')+fs.readFileSync('src/views/todo/operations/components/ExceptionActionDialog.vue','utf8')+fs.readFileSync('src/views/todo/operations/components/EventFailureTable.vue','utf8')
for(const marker of ['DEAD','replayDeadEvent','force-complete','force-cancel','sla-waiver','batchTransfer','必须填写操作原因','v-hasPermi','当前状态']) if(!operations.includes(marker)) throw new Error(`missing operations marker ${marker}`)

for(const name of ['getBusinessTodoSummary','listBusinessTodos','getTodoChain']) if(!api.includes(`export function ${name}`)) throw new Error(`missing business todo api ${name}`)
for(const name of ['listAdmissionEvidence','getAdmissionEvidenceGovernanceOptions','updateAdmissionEvidence']) if(!definitionApi.includes(`export function ${name}`)) throw new Error(`missing admission evidence api ${name}`)
if(!definitionApi.includes('export function getFoundationResourceReadiness')) throw new Error('missing foundation resource readiness api')
if(!definitionApi.includes('export function getHistoricalMigrationReadiness')) throw new Error('missing historical migration readiness api')
for(const name of ['getHistoricalMigrationPreflight','exportHistoricalMigrationExceptions']) if(!definitionApi.includes(`export function ${name}`)) throw new Error(`missing historical migration evidence api ${name}`)
if(!definitionApi.includes('export function getFileSecurityReadiness')) throw new Error('missing file security readiness api')
if(!definitionApi.includes('export function getFinanceReadiness')) throw new Error('missing finance readiness api')
for(const name of ['getAcceptanceReadiness','listAcceptanceScenarios','listAcceptanceMappings','getAcceptanceGovernanceOptions','createAcceptanceScenario','updateAcceptanceScenario','updateAcceptanceMapping','batchBindAcceptanceMappings']) if(!definitionApi.includes(`export function ${name}`)) throw new Error(`missing acceptance readiness api ${name}`)
if(!definitionApi.includes('export function getFoundationAdmissionReadiness')) throw new Error('missing aggregate Foundation admission readiness api')
const foundationResources=fs.readFileSync('src/views/todo/config/components/FoundationResourceReadiness.vue','utf8')
for(const marker of ['SOURCE_UNRESOLVED','RUNTIME_MISSING','RUNTIME_INCOMPLETE','READY','gateReady','expectedValuesJson']) if(!foundationResources.includes(marker)) throw new Error(`missing foundation resource marker ${marker}`)
const historicalMigration=fs.readFileSync('src/views/todo/config/components/HistoricalMigrationReadiness.vue','utf8')
for(const marker of ['NEEDS_DECISION','NEEDS_EVIDENCE','SOURCE_UNRESOLVED','RUNTIME_MISSING','RUNTIME_INVALID','gateReady','orphanTodoVersionCount','caseBusinessLineColumnExists','todo:admission:export','exceptionCandidateCount','csvSha256','UNREVIEWED']) if(!historicalMigration.includes(marker)) throw new Error(`missing historical migration marker ${marker}`)
const fileSecurity=fs.readFileSync('src/views/todo/config/components/FileSecurityReadiness.vue','utf8')
for(const marker of ['NEEDS_EVIDENCE','NEEDS_REVIEW','SOURCE_UNRESOLVED','RUNTIME_MISSING','gateReady','tokenControlReady','accessAuditReady','cleanupCompensationReady']) if(!fileSecurity.includes(marker)) throw new Error(`missing file security marker ${marker}`)
const financeReadiness=fs.readFileSync('src/views/todo/config/components/FinanceReadiness.vue','utf8');for(const marker of ['NEEDS_DECISION','NEEDS_REVIEW','SOURCE_UNRESOLVED','RUNTIME_MISSING','gateReady','nodeFeeColumns','riskSchemaObjects'])if(!financeReadiness.includes(marker))throw new Error(`missing finance readiness marker ${marker}`)
const acceptanceReadiness=fs.readFileSync('src/views/todo/config/components/AcceptanceReadiness.vue','utf8')
for(const marker of ['19','114','现有 28 个 Mock E2E 不计入 G-07','RUNTIME_INCOMPLETE','gateReady','独立 Reviewer','batchBindAcceptanceMappings','templateCode','dimensionCode','status']) if(!acceptanceReadiness.includes(marker)) throw new Error(`missing acceptance readiness marker ${marker}`)
const acceptanceScenario=fs.readFileSync('src/views/todo/config/components/AcceptanceScenarioDialog.vue','utf8')
for(const marker of ['datasetRef','datasetChecksum','datasetVersion','ownerUserId','acceptorUserId','reviewerUserId','version','actionId']) if(!acceptanceScenario.includes(marker)) throw new Error(`missing acceptance scenario marker ${marker}`)
const acceptanceMapping=fs.readFileSync('src/views/todo/config/components/AcceptanceMappingDialog.vue','utf8')
for(const marker of ['plannedTestRef','ownerUserId','reviewerUserId','version','actionId']) if(!acceptanceMapping.includes(marker)) throw new Error(`missing acceptance mapping marker ${marker}`)
const admissionEvidence=fs.readFileSync('src/views/todo/config/components/AdmissionEvidenceRegistry.vue','utf8')
for(const marker of ['todo:admission:edit','独立评审人','artifactRef','APPROVED','validateAdmissionEvidence']) if(!admissionEvidence.includes(marker)) throw new Error(`missing admission evidence marker ${marker}`)
const admissionOverview=fs.readFileSync('src/views/todo/config/components/FoundationAdmissionOverview.vue','utf8')
for(const marker of ['overallStatus','readyGateCount','totalGateCount','NOT_ADMITTED','blockers','技术就绪不替代']) if(!admissionOverview.includes(marker)) throw new Error(`missing aggregate admission marker ${marker}`)
for(const file of ['src/views/contract/components/ContractDetailDrawer.vue','src/views/case/components/CaseDetailDrawer.vue','src/views/matter/components/MatterDetailDrawer.vue']) if(!fs.readFileSync(file,'utf8').includes('business-todo-summary')) throw new Error(`missing embedded todo summary ${file}`)
const embedded=fs.readFileSync('src/views/todo/components/BusinessTodoSummary.vue','utf8')+fs.readFileSync('src/views/todo/components/BusinessTodoDrawer.vue','utf8')+fs.readFileSync('src/views/todo/components/TodoChainTimeline.vue','utf8')
for(const marker of ['activeCount','overdueCount','ownerIds','nearestDueAt','listBusinessTodos','getTodoChain','独立']) if(!embedded.includes(marker)&&marker!=='独立') throw new Error(`missing business todo marker ${marker}`)
for(const marker of ['data-testid="business-todo-summary"','Number(this.businessId) > 0','todo-detail-drawer','todo-action-dialogs','getTodo(','@extension-requested="refreshChanged"','fileObjectIds']) if(!embedded.includes(marker)) throw new Error(`missing business todo runtime marker ${marker}`)
for(const [file,type,id,no] of [
  ['src/views/lead/components/LeadDetailDrawer.vue','LEAD','lead.leadId','lead.leadNo'],
  ['src/views/customer/components/CustomerDetailDrawer.vue','CUSTOMER','customer.customerId','customer.customerNo'],
  ['src/views/finance/components/CaseFinanceDrawer.vue','FINANCE','caseId','summary.caseNo']
]) { const content=fs.readFileSync(file,'utf8');for(const marker of ['business-todo-summary',`business-type="${type}"`,id,no]) if(!content.includes(marker)) throw new Error(`missing ${type} todo embedding ${marker}`) }
for(const marker of ['visible','dept','cc','openChain','todo-chain-timeline']) if(!page.includes(marker)&&!fs.readFileSync('src/views/todo/index.vue','utf8').includes(marker)) throw new Error(`missing todo center expansion ${marker}`)
