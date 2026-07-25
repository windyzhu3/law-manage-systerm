<template>
  <el-drawer title="业务待办" :visible.sync="open" size="760px" append-to-body data-testid="business-todo-drawer">
    <div class="body">
      <el-alert v-if="error" :title="error" type="error" show-icon />
      <el-table v-loading="loading" :data="rows" @row-click="openDetail">
        <el-table-column label="待办编号" width="150"><template slot-scope="{ row }"><span :data-testid="`todo-row-${todoId(row)}`">{{ row.todo_no || row.todoNo }}</span></template></el-table-column>
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="due_at" label="截止时间" width="170" />
        <el-table-column label="操作" width="250">
          <template slot-scope="{ row }">
            <el-button v-if="can(row, 'claim')" v-hasPermi="['todo:claim']" type="text" @click.stop="openAction(row, 'claim')">领取</el-button>
            <el-button v-if="can(row, 'start')" v-hasPermi="['todo:start']" type="text" @click.stop="openAction(row, 'start')">开始</el-button>
            <el-button v-if="can(row, 'submit')" v-hasPermi="['todo:submit']" type="text" @click.stop="openAction(row, 'submit')">提交</el-button>
            <el-button v-if="can(row, 'complete')" v-hasPermi="['todo:complete']" type="text" @click.stop="openAction(row, 'complete')">完成</el-button>
            <el-button v-if="can(row, 'return')" v-hasPermi="['todo:return']" type="text" @click.stop="openAction(row, 'return')">退回</el-button>
            <el-button v-if="can(row, 'transfer')" v-hasPermi="['todo:transfer']" type="text" @click.stop="openAction(row, 'transfer')">转派</el-button>
            <el-button v-if="can(row, 'cancel')" v-hasPermi="['todo:cancel']" type="text" @click.stop="openAction(row, 'cancel')">取消</el-button>
            <el-button v-hasPermi="['todo:chain:query']" type="text" @click.stop="chain(row)">链路</el-button>
          </template>
        </el-table-column>
      </el-table>
      <todo-chain-timeline v-if="chainNodes.length" :nodes="chainNodes" />
    </div>
    <todo-detail-drawer :visible.sync="detailOpen" :detail="detail" @extension-requested="refreshChanged" />
    <todo-action-dialogs :visible.sync="actionOpen" :todo="selected || {}" :action="action" :submitting="submitting" @submit="execute" />
  </el-drawer>
</template>

<script>
import { listBusinessTodos, getTodo, getTodoChain, claimTodo, startTodo, submitTodo, completeTodo, returnTodo, transferTodo, cancelTodo } from '@/api/todo'
import TodoDetailDrawer from './TodoDetailDrawer'
import TodoActionDialogs from './TodoActionDialogs'
import TodoChainTimeline from './TodoChainTimeline'

const actions = { claim: claimTodo, start: startTodo, submit: submitTodo, complete: completeTodo, return: returnTodo, transfer: transferTodo, cancel: cancelTodo }

export default {
  name: 'BusinessTodoDrawer',
  components: { TodoDetailDrawer, TodoActionDialogs, TodoChainTimeline },
  props: { visible: Boolean, businessType: String, businessId: [Number, String] },
  data() { return { loading: false, submitting: false, error: '', rows: [], chainNodes: [], detailOpen: false, detail: {}, actionOpen: false, action: '', selected: null, requestGeneration: 0 } },
  computed: {
    open: { get() { return this.visible }, set(value) { this.$emit('update:visible', value) } },
    validBusinessId() { return Number(this.businessId) > 0 }
  },
  watch: {
    visible(value) { if (value) this.reloadForBusiness() },
    businessId() { if (this.open) this.reloadForBusiness() },
    businessType() { if (this.open) this.reloadForBusiness() }
  },
  methods: {
    todoId(row) { return row.todoId || row.todo_id },
    reloadForBusiness() {
      this.rows = []; this.chainNodes = []; this.detail = {}; this.selected = null; this.detailOpen = false; this.actionOpen = false
      this.requestGeneration++
      if (this.validBusinessId) this.load()
    },
    load() {
      if (!this.validBusinessId) return
      const generation = ++this.requestGeneration
      const businessKey = `${this.businessType}/${this.businessId}`
      this.loading = true; this.error = ''
      listBusinessTodos(this.businessType, this.businessId, { pageNum: 1, pageSize: 100 }).then(response => { if (generation === this.requestGeneration && businessKey === `${this.businessType}/${this.businessId}`) this.rows = response.rows || [] })
        .catch(error => { if (generation === this.requestGeneration) this.error = error.msg || '待办加载失败' })
        .finally(() => { if (generation === this.requestGeneration) this.loading = false })
    },
    explicitActions(row) { return row.allowedActions || row.allowed_actions || row.actions || null },
    can(row, action) {
      const allowed = this.explicitActions(row)
      return Array.isArray(allowed) && allowed.map(item => String(item).toLowerCase()).includes(action)
    },
    openDetail(row) {
      const id = this.todoId(row)
      if (Number(id) <= 0) return
      const generation = this.requestGeneration
      const businessKey = `${this.businessType}/${this.businessId}`
      getTodo(id).then(response => {
        if (generation === this.requestGeneration && businessKey === `${this.businessType}/${this.businessId}`) { this.detail = response.data || {}; this.detailOpen = true }
      })
    },
    openAction(row, action) { this.selected = row; this.action = action; this.actionOpen = true },
    execute(form) {
      const id = this.todoId(this.selected || {})
      if (!(Number(id) > 0) || !actions[this.action]) return
      this.submitting = true
      const payload = { actionId: `business-${Date.now()}-${Math.random().toString(16).slice(2)}`, opinion: form.opinion, fields: form.fields, fileObjectIds: form.fileObjectIds }
      actions[this.action](id, payload).then(() => { this.$modal.msgSuccess('处理成功'); this.actionOpen = false; this.refreshChanged() }).finally(() => { this.submitting = false })
    },
    refreshChanged() { this.load(); this.$emit('changed') },
    chain(row) {
      const id = row.rootTodoId || row.root_todo_id || this.todoId(row)
      if (!(Number(id) > 0)) return
      const generation = this.requestGeneration
      const businessKey = `${this.businessType}/${this.businessId}`
      getTodoChain(id).then(response => {
        if (generation === this.requestGeneration && businessKey === `${this.businessType}/${this.businessId}`) this.chainNodes = (response.data && response.data.nodes) || []
      })
    }
  }
}
</script>

<style scoped>.body{padding:18px}</style>
