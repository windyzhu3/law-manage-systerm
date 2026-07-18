<template>
  <el-dialog :title="form.scenarioId ? '编辑验收场景' : '新增验收场景'" :visible="visible" width="760px" :close-on-click-modal="false" @close="close">
    <el-alert title="Owner、业务验收人和独立 Reviewer 必须明确，Reviewer 不能与前两者相同。" type="info" :closable="false" show-icon />
    <el-form ref="form" :model="form" :rules="rules" label-width="120px" style="margin-top: 16px">
      <el-row :gutter="16">
        <el-col :span="12"><el-form-item label="场景编码" prop="scenarioCode"><el-input v-model="form.scenarioCode" :disabled="!!form.scenarioId" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="场景名称" prop="scenarioName"><el-input v-model="form.scenarioName" /></el-form-item></el-col>
      </el-row>
      <el-form-item label="业务路径" prop="businessPath"><el-input v-model="form.businessPath" /></el-form-item>
      <el-form-item label="前置条件 JSON" prop="preconditionsJson"><el-input v-model="form.preconditionsJson" type="textarea" :rows="3" /></el-form-item>
      <el-form-item label="执行步骤 JSON" prop="stepsJson"><el-input v-model="form.stepsJson" type="textarea" :rows="4" /></el-form-item>
      <el-form-item label="预期结果 JSON" prop="expectedOutcomesJson"><el-input v-model="form.expectedOutcomesJson" type="textarea" :rows="4" /></el-form-item>
      <el-row :gutter="16">
        <el-col :span="12"><el-form-item label="黄金数据引用"><el-input v-model="form.datasetRef" /></el-form-item></el-col>
        <el-col :span="6"><el-form-item label="数据版本"><el-input-number v-model="form.datasetVersion" :min="1" /></el-form-item></el-col>
        <el-col :span="6"><el-form-item label="状态" prop="status"><el-select v-model="form.status" style="width: 100%"><el-option v-for="item in scenarioStatuses" :key="item" :label="item" :value="item" /></el-select></el-form-item></el-col>
      </el-row>
      <el-form-item label="SHA-256 校验和"><el-input v-model="form.datasetChecksum" maxlength="64" /></el-form-item>
      <el-row :gutter="16">
        <el-col :span="8"><el-form-item label="Owner"><el-select v-model="form.ownerUserId" clearable filterable style="width: 100%"><el-option v-for="user in users" :key="user.user_id" :label="userLabel(user)" :value="user.user_id" /></el-select></el-form-item></el-col>
        <el-col :span="8"><el-form-item label="业务验收人"><el-select v-model="form.acceptorUserId" clearable filterable style="width: 100%"><el-option v-for="user in users" :key="user.user_id" :label="userLabel(user)" :value="user.user_id" /></el-select></el-form-item></el-col>
        <el-col :span="8"><el-form-item label="独立 Reviewer"><el-select v-model="form.reviewerUserId" clearable filterable style="width: 100%"><el-option v-for="user in users" :key="user.user_id" :label="userLabel(user)" :value="user.user_id" /></el-select></el-form-item></el-col>
      </el-row>
      <el-form-item label="截止时间"><el-date-picker v-model="form.dueAt" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" style="width: 100%" /></el-form-item>
      <el-form-item label="评审结论"><el-input v-model="form.conclusion" type="textarea" :rows="3" /></el-form-item>
    </el-form>
    <span slot="footer"><el-button @click="close">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></span>
  </el-dialog>
</template>

<script>
import { createAcceptanceScenario, updateAcceptanceScenario } from '@/api/todo-definition'

const empty = () => ({ scenarioId: null, version: 0, scenarioCode: '', scenarioName: '', businessPath: '', preconditionsJson: '[]', stepsJson: '[]', expectedOutcomesJson: '[]', datasetRef: null, datasetChecksum: null, datasetVersion: null, ownerUserId: null, acceptorUserId: null, reviewerUserId: null, dueAt: null, status: 'DRAFT', conclusion: null })

export default {
  name: 'AcceptanceScenarioDialog',
  props: { visible: Boolean, row: { type: Object, default: null }, users: { type: Array, default: () => [] }, scenarioStatuses: { type: Array, default: () => ['DRAFT', 'IN_REVIEW', 'APPROVED', 'REJECTED'] } },
  data() { return { saving: false, form: empty(), rules: { scenarioCode: [{ required: true, message: '请输入场景编码', trigger: 'blur' }], scenarioName: [{ required: true, message: '请输入场景名称', trigger: 'blur' }], businessPath: [{ required: true, message: '请输入业务路径', trigger: 'blur' }], preconditionsJson: [{ required: true, message: '请输入前置条件 JSON', trigger: 'blur' }], stepsJson: [{ required: true, message: '请输入执行步骤 JSON', trigger: 'blur' }], expectedOutcomesJson: [{ required: true, message: '请输入预期结果 JSON', trigger: 'blur' }], status: [{ required: true, message: '请选择状态', trigger: 'change' }] } } },
  watch: { visible(value) { if (value) this.form = this.row ? { ...empty(), ...this.row } : empty() } },
  methods: {
    userLabel(user) { return `${user.nick_name || user.user_name}（${user.user_name}）` },
    close() { if (!this.saving) this.$emit('update:visible', false) },
    save() {
      this.$refs.form.validate(valid => {
        if (!valid) return
        if (this.form.reviewerUserId && [this.form.ownerUserId, this.form.acceptorUserId].includes(this.form.reviewerUserId)) return this.$modal.msgError('独立 Reviewer 不能与 Owner 或业务验收人相同')
        const payload = { ...this.form, actionId: `acceptance-scenario-${Date.now()}` }
        this.saving = true
        const request = this.form.scenarioId ? updateAcceptanceScenario(this.form.scenarioId, payload) : createAcceptanceScenario(payload)
        request.then(response => { this.$emit('saved', response.data); this.$emit('update:visible', false) }).finally(() => { this.saving = false })
      })
    }
  }
}
</script>
