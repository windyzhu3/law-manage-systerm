<template>
  <el-dialog title="申请延期" :visible.sync="innerVisible" width="600px" append-to-body :close-on-click-modal="false">
    <el-descriptions :column="3" border size="small" class="policy-summary">
      <el-descriptions-item label="剩余次数">{{ remainingCount }}</el-descriptions-item>
      <el-descriptions-item label="单次上限">{{ maximumDuration }}</el-descriptions-item>
      <el-descriptions-item label="证明材料">{{ proofRule }}</el-descriptions-item>
    </el-descriptions>
    <el-alert title="审批通过后截止时间才会变更；审批期间 SLA 继续计时。" type="info" :closable="false" show-icon />
    <el-form ref="form" :model="form" :rules="rules" label-width="110px">
      <el-form-item label="申请截止时间" prop="requestedDueAt">
        <el-date-picker v-model="form.requestedDueAt" type="datetime" value-format="yyyy-MM-ddTHH:mm:ss" style="width:100%" />
      </el-form-item>
      <el-form-item label="延期原因" prop="reason">
        <el-input v-model="form.reason" type="textarea" :rows="4" maxlength="1000" show-word-limit />
      </el-form-item>
      <el-form-item label="证明材料" :required="proofRequired">
        <business-file-picker v-model="form.proofFiles" :business-type="businessContext.businessType" :business-id="businessContext.businessId" material-type="EXTENSION_PROOF" :limit="5" />
      </el-form-item>
    </el-form>
    <div slot="footer">
      <el-button @click="innerVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交申请</el-button>
    </div>
  </el-dialog>
</template>

<script>
import BusinessFilePicker from '@/components/BusinessFile/BusinessFilePicker'
import { requestTodoExtension } from '@/api/todo'

export default {
  name: 'TodoExtensionDialog',
  components: { BusinessFilePicker },
  props: {
    visible: Boolean,
    todo: { type: Object, default: () => ({}) },
    policy: { type: Object, required: true },
    businessContext: { type: Object, required: true }
  },
  data() {
    return {
      submitting: false,
      form: fresh(),
      rules: {
        requestedDueAt: [{ required: true, message: '请选择申请截止时间', trigger: 'change' }],
        reason: [{ required: true, message: '请填写延期原因', trigger: 'blur' }]
      }
    }
  },
  computed: {
    innerVisible: { get() { return this.visible }, set(value) { this.$emit('update:visible', value) } },
    todoId() { return this.todo.todoId || this.todo.todo_id },
    remainingCount() { return Number(this.policy.remainingRequestCount) },
    proofRequired() { return this.policy.proofRequired === true || this.policy.proof_required === true },
    proofRule() { return this.proofRequired ? '必需' : '选填' },
    maximumDuration() {
      return `${this.policy.maxExtensionValue} ${this.policy.maxExtensionUnit}`
    }
  },
  watch: {
    visible(value) { if (value) this.form = fresh() }
  },
  methods: {
    submit() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        const proofFileObjectIds = this.form.proofFiles.map(file => Number(file.fileObjectId)).filter(id => id > 0)
        if (this.proofRequired && !proofFileObjectIds.length) return this.$modal.msgError('请上传证明材料')
        this.submitting = true
        requestTodoExtension(this.todoId, {
          actionId: `ext-${Date.now()}-${Math.random().toString(16).slice(2)}`,
          requestedDueAt: this.form.requestedDueAt,
          reason: this.form.reason,
          proofFileObjectIds
        }).then(response => {
          this.$modal.msgSuccess('延期申请已提交')
          this.form = fresh()
          this.innerVisible = false
          this.$emit('success', response.data || response)
        }).finally(() => { this.submitting = false })
      })
    }
  }
}

function fresh() { return { requestedDueAt: '', reason: '', proofFiles: [] } }
</script>

<style scoped>.policy-summary{margin-bottom:12px}.el-alert{margin-bottom:16px}</style>
