<template>
  <el-drawer :visible.sync="innerVisible" size="680px" append-to-body @close="$emit('update:visible', false)">
    <div slot="title" class="todo-drawer-title">
      <span>TODO PROFILE</span>
      <strong>{{ todo.title || '待办详情' }}</strong>
    </div>
    <div class="todo-detail">
      <div class="detail-actions">
        <el-button
          v-if="canRequestExtension"
          v-hasPermi="['todo:extension:request']"
          size="mini"
          type="primary"
          plain
          @click="extensionOpen = true"
        >申请延期</el-button>
      </div>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="待办编号">{{ value('todoNo', 'todo_no') }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ value('status') }}</el-descriptions-item>
        <el-descriptions-item label="优先级">{{ value('priority') }}</el-descriptions-item>
        <el-descriptions-item label="SLA">{{ value('slaStatus', 'sla_status') }}</el-descriptions-item>
        <el-descriptions-item label="负责人">{{ value('ownerName', 'owner_name') || value('ownerId', 'owner_id') }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">{{ value('dueAt', 'due_at') || '-' }}</el-descriptions-item>
      </el-descriptions>
      <todo-relation-panel :detail="todo" :relations="detail.relations || []" />
      <section class="detail-section">
        <header>办理记录</header>
        <el-timeline v-if="actions.length">
          <el-timeline-item v-for="item in actions" :key="item.action_log_id || item.actionLogId" :timestamp="item.create_time || item.createTime">
            <strong>{{ item.action_type || item.actionType }}</strong>
            <span>{{ item.operator_name || item.operatorName || '-' }}</span>
            <p v-if="item.opinion">{{ item.opinion }}</p>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无办理记录" :image-size="64" />
      </section>
      <section class="detail-section">
        <header>完成材料</header>
        <todo-material-checklist
          v-if="materials.length || requirements.length"
          :value="materials"
          :requirements="requirements"
          business-type="TODO"
          :business-id="todoId"
          readonly
        />
        <el-empty v-else description="暂无材料" :image-size="64" />
      </section>
    </div>
    <todo-extension-dialog :visible.sync="extensionOpen" :todo="todo" :policy="extensionPolicy" @success="extensionSuccess" />
  </el-drawer>
</template>

<script>
import TodoRelationPanel from './TodoRelationPanel'
import TodoExtensionDialog from './TodoExtensionDialog'
import TodoMaterialChecklist from '@/components/BusinessFile/TodoMaterialChecklist'
import { getTodoForm } from '@/api/todo'
import { getMaterialRequirements } from '@/components/TodoDynamicForm/schema-runtime'

export default {
  name: 'TodoDetailDrawer',
  components: { TodoRelationPanel, TodoExtensionDialog, TodoMaterialChecklist },
  props: { visible: Boolean, detail: { type: Object, default: () => ({}) } },
  data() { return { formView: null, extensionOpen: false } },
  computed: {
    innerVisible: { get() { return this.visible }, set(value) { this.$emit('update:visible', value) } },
    todo() { return this.detail.todo || this.detail || {} },
    todoId() { return this.todo.todoId || this.todo.todo_id },
    actions() { return this.detail.actions || [] },
    attachments() { return this.detail.attachments || [] },
    materials() {
      if (this.formView && this.formView.materials) return this.formView.materials
      return this.attachments.map(file => ({
        fileObjectId: Number(file.file_object_id || file.fileObjectId),
        materialType: file.attachment_type || file.attachmentType,
        fileName: file.file_name || file.fileName
      })).filter(file => file.fileObjectId > 0)
    },
    requirements() { return this.formView ? getMaterialRequirements({ ...this.formView, action: 'COMPLETE' }) : [] },
    canRequestExtension() { return !['COMPLETED', 'CANCELLED'].includes(this.todo.status) },
    extensionPolicy() {
      const snapshot = parseJson(this.todo.slaSnapshot || this.todo.sla_snapshot)
      return { ...(snapshot.config || snapshot), ...this.todo }
    }
  },
  watch: {
    visible(value) { if (value) this.loadFormView() }
  },
  methods: {
    value(...keys) {
      for (const key of keys) if (this.todo[key] !== undefined && this.todo[key] !== null) return this.todo[key]
      return '-'
    },
    loadFormView() {
      if (!this.todoId) return
      getTodoForm(this.todoId).then(response => { this.formView = response.data || {} })
    },
    extensionSuccess(value) {
      this.$emit('extension-requested', value)
      this.loadFormView()
    }
  }
}

function parseJson(value) {
  if (!value || typeof value === 'object') return value || {}
  try { return JSON.parse(value) } catch (error) { return {} }
}
</script>

<style scoped>
.todo-drawer-title span,.todo-drawer-title strong{display:block}.todo-drawer-title span{font-size:11px;color:#64748b}.todo-drawer-title strong{font-size:18px;color:#0f172a}.todo-detail{display:grid;gap:16px;padding:18px}.detail-actions{text-align:right}.detail-section{padding:16px;border:1px solid #e8edf6;border-radius:10px}.detail-section header{margin-bottom:14px;font-weight:600;color:#1e3a8a}.detail-section span{margin-left:10px;color:#64748b}.detail-section p{margin:6px 0 0;color:#475569}
</style>
