<template>
  <el-drawer :visible.sync="innerVisible" size="680px" append-to-body @close="$emit('update:visible', false)">
    <div slot="title" class="todo-drawer-title">
      <span>TODO PROFILE</span>
      <strong>{{ todo.title || '待办详情' }}</strong>
    </div>
    <div class="todo-detail">
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
        <el-link v-for="file in attachments" :key="file.attachment_id || file.attachmentId" :href="file.file_url || file.fileUrl" target="_blank" type="primary">
          {{ file.file_name || file.fileName }}
        </el-link>
        <el-empty v-if="!attachments.length" description="暂无材料" :image-size="64" />
      </section>
    </div>
  </el-drawer>
</template>

<script>
import TodoRelationPanel from './TodoRelationPanel'

export default {
  name: 'TodoDetailDrawer',
  components: { TodoRelationPanel },
  props: { visible: Boolean, detail: { type: Object, default: () => ({}) } },
  computed: {
    innerVisible: { get() { return this.visible }, set(v) { this.$emit('update:visible', v) } },
    todo() { return this.detail.todo || this.detail || {} },
    actions() { return this.detail.actions || [] },
    attachments() { return this.detail.attachments || [] }
  },
  methods: {
    value(...keys) {
      for (const key of keys) if (this.todo[key] !== undefined && this.todo[key] !== null) return this.todo[key]
      return '-'
    }
  }
}
</script>

<style scoped>
.todo-drawer-title span,.todo-drawer-title strong{display:block}.todo-drawer-title span{font-size:11px;color:#64748b}.todo-drawer-title strong{font-size:18px;color:#0f172a}.todo-detail{display:grid;gap:16px;padding:18px}.detail-section{padding:16px;border:1px solid #e8edf6;border-radius:10px}.detail-section header{margin-bottom:14px;font-weight:600;color:#1e3a8a}.detail-section .el-link{display:block;margin:8px 0}.detail-section span{margin-left:10px;color:#64748b}.detail-section p{margin:6px 0 0;color:#475569}
</style>
