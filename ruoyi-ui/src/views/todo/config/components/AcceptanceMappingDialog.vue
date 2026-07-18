<template>
  <el-dialog title="编辑 AT 映射" :visible="visible" width="680px" :close-on-click-modal="false" @close="close">
    <el-alert title="映射审批必须由独立 Reviewer 完成，且关联场景必须先获批。" type="info" :closable="false" show-icon />
    <el-form ref="form" :model="form" label-width="120px" style="margin-top: 16px">
      <el-form-item label="AT 引用"><el-input :value="form.acceptanceRef" disabled /></el-form-item>
      <el-form-item label="验收场景"><el-select v-model="form.scenarioId" filterable style="width: 100%"><el-option v-for="scenario in scenarios" :key="scenario.scenarioId" :label="`${scenario.scenarioCode}｜${scenario.scenarioName}`" :value="scenario.scenarioId" /></el-select></el-form-item>
      <el-form-item label="计划测试引用"><el-input v-model="form.plannedTestRef" placeholder="例如 e2e/phase-one.spec.js#golden-path" /></el-form-item>
      <el-form-item label="证据说明"><el-input v-model="form.evidenceNote" type="textarea" :rows="3" /></el-form-item>
      <el-row :gutter="16">
        <el-col :span="12"><el-form-item label="Owner"><el-select v-model="form.ownerUserId" clearable filterable style="width: 100%"><el-option v-for="user in users" :key="user.user_id" :label="userLabel(user)" :value="user.user_id" /></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="独立 Reviewer"><el-select v-model="form.reviewerUserId" clearable filterable style="width: 100%"><el-option v-for="user in users" :key="user.user_id" :label="userLabel(user)" :value="user.user_id" /></el-select></el-form-item></el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :span="12"><el-form-item label="截止时间"><el-date-picker v-model="form.dueAt" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" style="width: 100%" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="状态"><el-select v-model="form.status" style="width: 100%"><el-option v-for="item in mappingStatuses" :key="item" :label="item" :value="item" /></el-select></el-form-item></el-col>
      </el-row>
      <el-form-item label="评审结论"><el-input v-model="form.conclusion" type="textarea" :rows="3" /></el-form-item>
    </el-form>
    <span slot="footer"><el-button @click="close">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></span>
  </el-dialog>
</template>

<script>
import { updateAcceptanceMapping } from '@/api/todo-definition'

export default {
  name: 'AcceptanceMappingDialog',
  props: { visible: Boolean, row: { type: Object, default: null }, scenarios: { type: Array, default: () => [] }, users: { type: Array, default: () => [] }, mappingStatuses: { type: Array, default: () => ['UNMAPPED', 'MAPPED', 'IN_REVIEW', 'APPROVED', 'REJECTED'] } },
  data() { return { saving: false, form: {} } },
  watch: { visible(value) { if (value) this.form = { ...(this.row || {}) } } },
  methods: {
    userLabel(user) { return `${user.nick_name || user.user_name}（${user.user_name}）` },
    close() { if (!this.saving) this.$emit('update:visible', false) },
    save() {
      if (!this.form.scenarioId || !String(this.form.plannedTestRef || '').trim()) return this.$modal.msgError('场景和计划测试引用不能为空')
      if (this.form.ownerUserId && this.form.ownerUserId === this.form.reviewerUserId) return this.$modal.msgError('独立 Reviewer 不能与 Owner 相同')
      const payload = { ...this.form, version: this.form.version, actionId: `acceptance-mapping-${Date.now()}` }
      this.saving = true
      updateAcceptanceMapping(this.form.mappingId, payload).then(response => { this.$emit('saved', response.data); this.$emit('update:visible', false) }).finally(() => { this.saving = false })
    }
  }
}
</script>
