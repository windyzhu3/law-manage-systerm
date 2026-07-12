const fs = require('fs')
const required = [
  'src/api/todo.js',
  'src/views/todo/index.vue',
  'src/views/todo/components/TodoDetailDrawer.vue',
  'src/views/todo/components/TodoActionDialogs.vue',
  'src/views/todo/components/TodoSummaryCard.vue',
  'src/views/todo/components/TodoRelationPanel.vue'
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
console.log('todo ui contract ok')
