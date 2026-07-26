const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const Vue = require('vue')
const {
  hasTagConfirmPermission,
  tagPermissionRenderKey
} = require('../../../src/views/lead/tag-confirm-permission')

function permissionViewModel(initialPermissions = []) {
  const state = Vue.observable({ permissions: initialPermissions })
  const vm = new Vue({
    data: () => ({ state }),
    computed: {
      allowed() {
        return hasTagConfirmPermission(this.state.permissions)
      },
      renderKey() {
        return tagPermissionRenderKey('all', this.state.permissions)
      },
      operationCopies() {
        return this.allowed ? ['main', 'fixed'] : []
      }
    }
  })
  return { state, vm }
}

test('tag-confirm permission reacts identically in main and fixed operation copies', async () => {
  const { state, vm } = permissionViewModel([])
  assert.deepEqual(vm.operationCopies, [])
  const initialKey = vm.renderKey

  state.permissions = ['lead:query', 'lead:tag:confirm']
  await Vue.nextTick()
  assert.notEqual(vm.renderKey, initialKey)
  assert.deepEqual(vm.operationCopies, ['main', 'fixed'])

  state.permissions = ['lead:query']
  await Vue.nextTick()
  assert.deepEqual(vm.operationCopies, [])
  vm.$destroy()
})

test('sales permissions never expose tag confirmation while wildcard remains supported', async () => {
  const { state, vm } = permissionViewModel(['lead:first-contact:handle'])
  assert.deepEqual(vm.operationCopies, [])

  state.permissions = ['*:*:*']
  await Vue.nextTick()
  assert.deepEqual(vm.operationCopies, ['main', 'fixed'])
  vm.$destroy()
})

test('fixed operation slots use a permission-keyed wrapper without destructive tag directive', () => {
  const source = fs.readFileSync(
    path.resolve(__dirname, '../../../src/views/lead/index.vue'),
    'utf8'
  )
  assert.match(source, /:key="`actions:\$\{permissionRenderKey\}:\$\{row\.leadNo\}`"/)
  assert.match(source, /:key="`tag-confirm:\$\{permissionRenderKey\}:\$\{row\.leadNo\}`"/)
  assert.match(
    source,
    /<el-button v-if="canConfirmTag\(row\) && canConfirmTagPermission"[^>]+data-testid="`lead-tag-confirm-\$\{row\.leadNo\}`"/
  )
  assert.doesNotMatch(
    source,
    /<el-button[^>]+v-hasPermi[^>]+data-testid="`lead-tag-confirm-\$\{row\.leadNo\}`"/
  )
})
