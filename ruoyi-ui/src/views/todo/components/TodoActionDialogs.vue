<template>
  <el-dialog :title="titles[action] || '处理待办'" :visible.sync="innerVisible" width="680px" append-to-body :close-on-click-modal="false">
    <el-alert v-if="todo" :title="`${businessNo} · ${todo.title || '待办'}`" type="info" :closable="false" />
    <div v-loading="loading">
      <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon />
      <el-form :model="form" label-width="120px">
        <el-form-item label="处理意见" :required="requiresOpinion">
          <el-input v-model="form.opinion" type="textarea" :rows="3" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item v-if="action === 'transfer'" label="新负责人" required>
          <el-input-number v-model="state.fields.targetOwnerId" :min="1" :precision="0" style="width:100%" />
        </el-form-item>
      </el-form>
      <todo-dynamic-form
        v-if="formView"
        ref="dynamicForm"
        v-model="state"
        :form-view="formView"
        business-type="TODO"
        :business-id="todoId"
      />
    </div>
    <div slot="footer">
      <el-button @click="innerVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="loading || !formView" @click="submit">确认</el-button>
    </div>
  </el-dialog>
</template>

<script>
import TodoDynamicForm from '@/components/TodoDynamicForm'
import { createFormState, createActionPayload } from '@/components/TodoDynamicForm/schema-runtime'
import { getTodoForm } from '@/api/todo'

export default {
  name: 'TodoActionDialogs',
  components: { TodoDynamicForm },
  props: {
    visible: Boolean,
    action: String,
    submitting: Boolean,
    todo: { type: Object, default: () => ({}) }
  },
  data() {
    return {
      loading: false,
      loadError: '',
      formView: null,
      form: { opinion: '' },
      state: { fields: {}, materials: [] },
      titles: { claim: '领取待办', start: '开始办理', submit: '提交待办', complete: '完成待办', return: '退回待办', transfer: '转派待办', cancel: '取消待办' }
    }
  },
  computed: {
    innerVisible: { get() { return this.visible }, set(value) { this.$emit('update:visible', value) } },
    todoId() { return this.todo.todoId || this.todo.todo_id },
    businessNo() { return this.todo.businessNo || this.todo.business_no || '-' },
    requiresOpinion() { return ['return', 'cancel'].includes(this.action) }
  },
  watch: {
    visible(value) { if (value) this.openSession() }
  },
  methods: {
    openSession() {
      this.form = { opinion: '' }
      this.state = { fields: {}, materials: [] }
      this.formView = null
      this.loadError = ''
      this.loading = true
      getTodoForm(this.todoId).then(response => {
        this.formView = { ...(response.data || {}), action: String(this.action || '').toUpperCase() }
        this.state = createFormState(this.formView)
      }).catch(error => {
        this.loadError = (error && (error.msg || error.message)) || '表单加载失败'
      }).finally(() => { this.loading = false })
    },
    submit() {
      if (this.requiresOpinion && !this.form.opinion.trim()) return this.$modal.msgError('请填写处理意见')
      if (this.action === 'transfer' && !this.state.fields.targetOwnerId) return this.$modal.msgError('请选择新负责人')
      if (!this.$refs.dynamicForm.validate()) return
      const payload = createActionPayload(this.formView, this.state)
      this.$emit('submit', { opinion: this.form.opinion, fields: payload.fields, fileObjectIds: payload.fileObjectIds })
    }
  }
}
</script>
