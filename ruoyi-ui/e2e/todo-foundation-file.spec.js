const { test, expect } = require('@playwright/test')
const fs = require('fs')
const path = require('path')

const source = file => fs.readFileSync(path.join(__dirname, '..', 'src', ...file.split('/')), 'utf8')

test('business actions and extensions keep the shared schema, material and file runtime', () => {
  const drawer = source('views/todo/components/BusinessTodoDrawer.vue')
  const dialogs = source('views/todo/components/TodoActionDialogs.vue')
  const detail = source('views/todo/components/TodoDetailDrawer.vue')
  expect(drawer).toContain('todo-action-dialogs')
  for (const marker of ['TodoDynamicForm', 'createActionPayload', 'fileObjectIds', 'getTodoForm']) expect(dialogs).toContain(marker)
  for (const marker of ['todo-extension-dialog', 'todo-material-checklist', "this.$emit('extension-requested'"]) expect(detail).toContain(marker)
})

test('lead, customer and finance embed the same summary with canonical uppercase business types', () => {
  const domains = [
    ['views/lead/components/LeadDetailDrawer.vue', 'LEAD', 'lead.leadId', 'lead.leadNo'],
    ['views/customer/components/CustomerDetailDrawer.vue', 'CUSTOMER', 'customer.customerId', 'customer.customerNo'],
    ['views/finance/components/CaseFinanceDrawer.vue', 'FINANCE', 'caseId', 'summary.caseNo']
  ]
  domains.forEach(([file, type, id, no]) => {
    const content = source(file)
    expect(content).toContain('business-todo-summary')
    expect(content).toContain(`business-type="${type}"`)
    expect(content).toContain(id)
    expect(content).toContain(no)
  })
})
