<template>
  <el-drawer
    :visible.sync="open"
    title="首联处理"
    size="860px"
    append-to-body
    :close-on-click-modal="false"
    custom-class="lead-first-contact-drawer"
    data-testid="lead-first-contact-drawer"
  >
    <div v-if="lead" class="first-contact">
      <section class="lead-drawer-section first-contact__identity">
        <div>
          <small>{{ lead.leadNo || '-' }}</small>
          <h2>{{ lead.contactName || lead.leadName }}</h2>
          <p>{{ lead.mobile || '未填写电话' }} · {{ lead.companyName || '个人客户' }}</p>
        </div>
        <div class="first-contact__todo">
          <span>当前待办</span>
          <strong>{{ todo.title || '未生成首联待办' }}</strong>
          <el-tag size="mini" :type="sla.type">{{ sla.label }}</el-tag>
          <time>截止：{{ timeText(todo.dueAt || todo.due_at) }}</time>
        </div>
      </section>

      <div v-if="loadError" role="alert">
        <el-alert :title="loadError" type="error" :closable="false" show-icon>
          <el-button type="text" @click="load">重新加载</el-button>
        </el-alert>
      </div>

      <div v-loading="loading">
        <section class="lead-drawer-section" data-flow-field="contactResult">
          <lead-call-timeline
            v-if="lead.leadId"
            ref="calls"
            :lead-id="lead.leadId"
            editable
            @changed="refresh"
          />
        </section>
        <section class="lead-drawer-section">
          <h3>首联结论</h3>
          <p class="first-contact__help">
            请选择 VALID、SUSPECT_INVALID 或 UNREACHABLE。选择有效时，姓名、城市、诉求和是否到所将按
            showWhen 条件显示并成为必填项（contactName、city、legalDemand、visited）。
          </p>
          <todo-dynamic-form
            v-if="formView"
            ref="dynamicForm"
            v-model="state"
            :form-view="formView"
            business-type="LEAD"
            :business-id="lead.leadId"
          />
          <el-empty v-else-if="!loading && !loadError" description="当前没有可处理的首联待办" :image-size="64" />
        </section>
      </div>

      <div v-if="submitError" role="alert" class="first-contact__error">
        <el-alert :title="submitError" type="error" :closable="false" show-icon />
      </div>
      <div class="lead-fixed-actions">
        <el-button @click="open=false">关闭</el-button>
        <el-button
          v-hasPermi="['lead:first-contact:handle']"
          type="primary"
          :loading="submitting"
          :disabled="loading || submitting || !canComplete"
          data-testid="lead-first-contact-submit"
          @click="complete"
        >完成首联并进入下一节点</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script>
import TodoDynamicForm from '@/components/TodoDynamicForm'
import LeadCallTimeline from './LeadCallTimeline'
import { listBusinessTodos, getTodoForm, completeTodo } from '@/api/todo'
import { createActionPayload, createFormState } from '@/components/TodoDynamicForm/schema-runtime'
import { errorMessage, isPendingTodo, slaMeta, stableActionId, timeText } from '../lead-todo-ui'

export default {
  name: 'LeadFirstContactDrawer',
  components: { TodoDynamicForm, LeadCallTimeline },
  props: { value: Boolean, lead: { type: Object, default: null } },
  data() {
    return { loading: false, submitting: false, loadError: '', submitError: '', todo: {}, formView: null, state: { fields: {}, materials: [] }, actionId: '', generation: 0 }
  },
  computed: {
    open: { get() { return this.value }, set(value) { this.$emit('input', value) } },
    todoId() { return Number(this.todo.todoId || this.todo.todo_id || 0) },
    canComplete() {
      const actions = this.todo.allowedActions || this.todo.allowed_actions || this.todo.actions
      return isPendingTodo({ ...this.todo, todoId: this.todoId, todoStatus: this.todo.status || this.todo.todoStatus }) &&
        Array.isArray(actions) && actions.map(item => String(item).toLowerCase()).includes('complete') && !!this.formView
    },
    sla() { return slaMeta({ ...this.todo, slaStatus: this.todo.slaStatus || this.todo.sla_status }) }
  },
  watch: {
    value(open) { if (open) this.load() },
    'lead.leadId'() { if (this.open) this.load() },
    state: {
      deep: true,
      handler() {
        if (this.submitError) {
          this.actionId = stableActionId('TD001_COMPLETE', this.todoId)
          this.submitError = ''
        }
      }
    }
  },
  methods: {
    timeText,
    async load() {
      if (!this.lead || !(Number(this.lead.leadId) > 0)) return
      const generation = ++this.generation
      this.loading = true; this.loadError = ''; this.submitError = ''; this.todo = {}; this.formView = null; this.actionId = ''
      try {
        const response = await listBusinessTodos('LEAD', this.lead.leadId, { pageNum: 1, pageSize: 100 })
        if (generation !== this.generation) return
        const rows = response.rows || []
        this.todo = rows.find(row => {
          const code = row.templateCode || row.template_code
          const status = row.status || row.todoStatus
          return code === 'TD-001' && !['COMPLETED', 'CANCELLED'].includes(status)
        }) || {}
        if (!this.todoId) return
        const form = await getTodoForm(this.todoId)
        if (generation !== this.generation) return
        this.formView = { ...(form.data || {}), action: 'COMPLETE' }
        this.state = createFormState(this.formView)
        this.actionId = stableActionId('TD001_COMPLETE', this.todoId)
      } catch (error) {
        if (generation === this.generation) this.loadError = errorMessage(error, '首联待办加载失败')
      } finally {
        if (generation === this.generation) this.loading = false
      }
    },
    complete() {
      if (!this.canComplete) return
      if (!this.$refs.dynamicForm || !this.$refs.dynamicForm.validate()) return
      const payload = createActionPayload(this.formView, this.state)
      this.submitting = true; this.submitError = ''
      completeTodo(this.todoId, {
        actionId: this.actionId,
        opinion: '线索首联处理完成',
        fields: payload.fields,
        fileObjectIds: payload.fileObjectIds
      }).then(() => {
        this.$modal.msgSuccess('首联已完成，下一待办将由流程引擎生成')
        this.$emit('completed'); this.actionId = ''; this.open = false
      }).catch(error => { this.submitError = errorMessage(error, '首联提交失败') })
        .finally(() => { this.submitting = false })
    },
    refresh() { this.$emit('changed') }
  }
}
</script>

<style scoped lang="scss">
.first-contact{min-height:100%;padding:0 18px 12px;background:#f8fafc}.first-contact__identity{display:flex;align-items:center;justify-content:space-between;gap:18px}.first-contact__identity small{color:#64748b}.first-contact__identity h2{margin:5px 0;color:#020617}.first-contact__identity p{margin:0;color:#475569}.first-contact__todo{min-width:240px;padding:12px;border-left:3px solid #0369a1;background:#f0f9ff}.first-contact__todo span,.first-contact__todo strong,.first-contact__todo time{display:block}.first-contact__todo span,.first-contact__todo time{color:#64748b;font-size:12px}.first-contact__todo strong{margin:4px 0;color:#0f172a}.first-contact__todo time{margin-top:5px}.first-contact__help{margin:0 0 8px!important;font-size:12px}.first-contact__error{margin:10px 0}@media(max-width:760px){.first-contact__identity{align-items:stretch;flex-direction:column}.first-contact__todo{min-width:0}}
</style>

<style lang="scss">
.lead-first-contact-drawer{max-width:96%;.el-drawer__body{overflow:auto;background:#f8fafc}}
</style>
