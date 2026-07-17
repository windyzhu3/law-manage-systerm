const fs = require('fs')
const required = [
  'src/api/todo.js',
  'src/views/todo/index.vue',
  'src/views/todo/components/TodoDetailDrawer.vue',
  'src/views/todo/components/TodoActionDialogs.vue',
  'src/views/todo/components/TodoSummaryCard.vue',
  'src/views/todo/components/TodoRelationPanel.vue'
  ,'src/api/todo-definition.js'
  ,'src/views/todo/config/index.vue'
  ,'src/views/todo/config/components/TemplateList.vue'
  ,'src/views/todo/config/components/VersionDrawer.vue'
  ,'src/views/todo/config/components/DefinitionForm.vue'
  ,'src/views/todo/config/components/TriggerRuleTable.vue'
  ,'src/views/todo/config/components/WorkCalendarTable.vue'
  ,'src/api/todo-operations.js'
  ,'src/views/todo/operations/index.vue'
  ,'src/views/todo/operations/components/OperationsMetrics.vue'
  ,'src/views/todo/operations/components/EventFailureTable.vue'
  ,'src/views/todo/operations/components/EscalationTable.vue'
  ,'src/views/todo/operations/components/ExceptionActionDialog.vue'
  ,'src/views/todo/components/BusinessTodoSummary.vue'
  ,'src/views/todo/components/BusinessTodoDrawer.vue'
  ,'src/views/todo/components/TodoChainTimeline.vue'
]
for (const file of required) {
  if (!fs.existsSync(file)) throw new Error(`missing ${file}`)
}
const api = fs.readFileSync('src/api/todo.js', 'utf8')
for (const name of ['getTodoDashboard', 'listTodo', 'getTodo', 'claimTodo', 'startTodo', 'submitTodo', 'completeTodo', 'returnTodo', 'transferTodo', 'cancelTodo', 'addTodoAttachment']) {
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
console.log('todo ui contract ok')

const definitionApi = fs.readFileSync('src/api/todo-definition.js', 'utf8')
for (const name of ['listDefinitions','copyDefinition','copyDefinitionVersion','updateDefinitionDraft','publishDefinition','listTriggerRules','listWorkCalendars','buildVirtualTaskCompletion']) if (!definitionApi.includes(`export function ${name}`)) throw new Error(`missing definition api ${name}`)
for (const marker of ['taskCompletions','nodeKey','occurrence','payload','completedAt']) if (!definitionApi.includes(marker)) throw new Error(`missing simulation completion sample marker ${marker}`)
const definitionForm = fs.readFileSync('src/views/todo/config/components/DefinitionForm.vue','utf8')
for (const marker of ['ownerMode','requiredFields','requiredAttachments','slaMinutes','calendarCode','nextTemplateVersionId','规则预览']) if (!definitionForm.includes(marker)) throw new Error(`missing structured definition marker ${marker}`)
const configPage=fs.readFileSync('src/views/todo/config/index.vue','utf8')
for(const marker of ["v-hasPermi",'copyDefinition','publishDefinition','发布后该版本不可修改']) if(!configPage.includes(marker)&&!fs.readFileSync('src/views/todo/config/components/TemplateList.vue','utf8').includes(marker)&&!fs.readFileSync('src/views/todo/config/components/VersionDrawer.vue','utf8').includes(marker)) throw new Error(`missing config marker ${marker}`)

const operationsApi=fs.readFileSync('src/api/todo-operations.js','utf8')
for(const name of ['getOperationsDashboard','listDeadEvents','replayDeadEvent','forceCompleteTodo','forceCancelTodo','regenerateTodo','batchTransferTodos','waiveTodoSla']) if(!operationsApi.includes(`export function ${name}`)) throw new Error(`missing operations api ${name}`)
const operations=operationsApi+fs.readFileSync('src/views/todo/operations/index.vue','utf8')+fs.readFileSync('src/views/todo/operations/components/ExceptionActionDialog.vue','utf8')+fs.readFileSync('src/views/todo/operations/components/EventFailureTable.vue','utf8')
for(const marker of ['DEAD','replayDeadEvent','force-complete','force-cancel','sla-waiver','batchTransfer','必须填写操作原因','v-hasPermi','当前状态']) if(!operations.includes(marker)) throw new Error(`missing operations marker ${marker}`)

for(const name of ['getBusinessTodoSummary','listBusinessTodos','getTodoChain']) if(!api.includes(`export function ${name}`)) throw new Error(`missing business todo api ${name}`)
for(const file of ['src/views/contract/components/ContractDetailDrawer.vue','src/views/case/components/CaseDetailDrawer.vue','src/views/matter/components/MatterDetailDrawer.vue']) if(!fs.readFileSync(file,'utf8').includes('business-todo-summary')) throw new Error(`missing embedded todo summary ${file}`)
const embedded=fs.readFileSync('src/views/todo/components/BusinessTodoSummary.vue','utf8')+fs.readFileSync('src/views/todo/components/BusinessTodoDrawer.vue','utf8')+fs.readFileSync('src/views/todo/components/TodoChainTimeline.vue','utf8')
for(const marker of ['activeCount','overdueCount','ownerIds','nearestDueAt','listBusinessTodos','getTodoChain','独立']) if(!embedded.includes(marker)&&marker!=='独立') throw new Error(`missing business todo marker ${marker}`)
for(const marker of ['visible','dept','cc','openChain','todo-chain-timeline']) if(!page.includes(marker)&&!fs.readFileSync('src/views/todo/index.vue','utf8').includes(marker)) throw new Error(`missing todo center expansion ${marker}`)
