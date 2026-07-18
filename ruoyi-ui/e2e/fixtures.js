async function setupTodo(page, todo, capture, formView = {}) {
  await page.context().addCookies([{ name: 'Admin-Token', value: 'e2e-token', url: 'http://127.0.0.1:4173/' }])
  await page.addInitScript(() => { document.cookie = 'Admin-Token=e2e-token; path=/' })
  await page.route('**/prod-api/**', async route => {
    const url = new URL(route.request().url()); const path = url.pathname.replace('/prod-api', '')
    if (path === '/getInfo') return json(route, null, { code: 200, user: { userId: 1, userName: 'admin', nickName: '管理员', avatar: '' }, roles: ['admin'], permissions: ['*:*:*'] })
    if (path === '/getRouters') return json(route, [{ path: '/', component: 'Layout', children: [{ path: 'todo-center', component: 'todo/index', name: 'TodoCenter', meta: { title: '待办中心', icon: 'clipboard' } }] }])
    if (path === '/todo/dashboard') return json(route, { mine: 1, candidate: 0, overdue: 0 })
    if (path.startsWith('/todo/list')) return json(route, null, { rows: [todo], total: 1 })
    if (path === `/todo/${todo.todo_id || todo.todoId}/form`) {
      return json(route, {
        todoId: todo.todo_id || todo.todoId,
        businessType: formView.businessType || 'CASE',
        businessId: formView.businessId || 1,
        ui: formView.ui || { config: { fields: [] } },
        dod: formView.dod || { config: {} },
        defaults: formView.defaults || {},
        materials: formView.materials || []
      })
    }
    if (/\/todo\/\d+\/complete$/.test(path)) { capture.body = route.request().postDataJSON(); return json(route, todo) }
    if (path.startsWith('/system/dict/data/type/')) return json(route, [])
    if (path === '/system/config/configKey/sys.index.skinName') return json(route, '')
    return json(route, {})
  })
  await page.goto('/todo-center'); await page.locator('.el-table__body').getByText(todo.title, { exact: true }).first().waitFor()
}
async function json(route, data, envelope) { await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(envelope || { code: 200, msg: 'success', data }) }) }
module.exports = { setupTodo }
