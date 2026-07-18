<template>
  <div class="admission-evidence-registry">
    <el-alert title="登记机制不等于业务审批：仅当独立评审人提交证据并确认后，准入项才可标记为 APPROVED。" type="warning" :closable="false" show-icon />
    <el-table :data="rows" style="margin-top: 12px">
      <el-table-column label="门禁" width="90"><template slot-scope="scope">{{ value(scope.row, 'gate_code', 'gateCode') }}</template></el-table-column>
      <el-table-column label="证据项" min-width="220"><template slot-scope="scope"><div>{{ value(scope.row, 'title', 'title') }}</div><small>{{ value(scope.row, 'evidence_code', 'evidenceCode') }}</small></template></el-table-column>
      <el-table-column label="状态" width="120"><template slot-scope="scope"><el-tag :type="statusType(scope.row)">{{ value(scope.row, 'status', 'status') }}</el-tag></template></el-table-column>
      <el-table-column label="责任与评审" min-width="190"><template slot-scope="scope"><div>Owner：{{ userLabel(scope.row, 'owner') }}</div><div>Reviewer：{{ userLabel(scope.row, 'reviewer') }}</div><el-tag v-if="!assigned(scope.row)" size="mini" type="danger">未分配</el-tag></template></el-table-column>
      <el-table-column label="截止时间" min-width="160"><template slot-scope="scope"><span>{{ value(scope.row, 'due_at', 'dueAt') || '-' }}</span><el-tag v-if="overdue(scope.row)" size="mini" type="danger">已逾期</el-tag></template></el-table-column>
      <el-table-column label="证据引用" min-width="220" show-overflow-tooltip><template slot-scope="scope">{{ value(scope.row, 'artifact_ref', 'artifactRef') || '-' }}</template></el-table-column>
      <el-table-column label="操作" width="90"><template slot-scope="scope"><el-button v-hasPermi="['todo:admission:edit']" type="text" @click="edit(scope.row)">登记</el-button></template></el-table-column>
    </el-table>

    <el-dialog title="准入证据登记" :visible.sync="visible" width="680px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="证据项"><el-input :value="form.title" disabled /></el-form-item>
        <el-form-item label="责任人"><el-select v-model="form.ownerUserId" filterable><el-option v-for="user in options.users" :key="value(user, 'user_id', 'userId')" :label="optionLabel(user)" :value="Number(value(user, 'user_id', 'userId'))" /></el-select></el-form-item>
        <el-form-item label="独立评审人"><el-select v-model="form.reviewerUserId" filterable><el-option v-for="user in options.users" :key="value(user, 'user_id', 'userId')" :label="optionLabel(user)" :value="Number(value(user, 'user_id', 'userId'))" /></el-select></el-form-item>
        <el-form-item label="截止时间"><el-date-picker v-model="form.dueAt" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="form.status"><el-option v-for="status in options.statuses" :key="status" :label="status" :value="status" /></el-select></el-form-item>
        <el-form-item label="证据引用"><el-input v-model="form.artifactRef" placeholder="仓库文档路径、评审记录或文件对象引用" /></el-form-item>
        <el-form-item label="提交/评审结论"><el-input v-model="form.conclusion" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <span slot="footer"><el-button @click="visible=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></span>
    </el-dialog>
  </div>
</template>

<script>
import { getAdmissionEvidenceGovernanceOptions, updateAdmissionEvidence } from '@/api/todo-definition'
import { validateAdmissionEvidence } from '../resource-contract'

export default {
  name: 'AdmissionEvidenceRegistry',
  props: { rows: { type: Array, default: () => [] }, onRefresh: { type: Function, required: true } },
  data() { return { visible: false, options: { users: [], statuses: ['OPEN', 'IN_REVIEW', 'APPROVED', 'REJECTED'] }, form: {} } },
  methods: {
    value(row, snake, camel) { return row && (row[camel] !== undefined ? row[camel] : row[snake]) },
    loadOptions() { return getAdmissionEvidenceGovernanceOptions().then(response => { this.options = response.data || this.options }) },
    edit(row) {
      this.form = {
        evidenceId: Number(this.value(row, 'evidence_id', 'evidenceId')), version: Number(this.value(row, 'version', 'version') || 0),
        title: this.value(row, 'title', 'title'), ownerUserId: this.value(row, 'owner_user_id', 'ownerUserId') || null,
        reviewerUserId: this.value(row, 'reviewer_user_id', 'reviewerUserId') || null, dueAt: this.value(row, 'due_at', 'dueAt') || '',
        status: this.value(row, 'status', 'status') || 'OPEN', artifactRef: this.value(row, 'artifact_ref', 'artifactRef') || '',
        conclusion: this.value(row, 'conclusion', 'conclusion') || ''
      }
      this.loadOptions().then(() => { this.visible = true })
    },
    save() {
      let payload
      try { payload = validateAdmissionEvidence({ ...this.form, actionId: `admission-${this.form.evidenceId}-${Date.now()}` }) } catch (error) { this.$message.error(error.message); return }
      updateAdmissionEvidence(this.form.evidenceId, payload).then(() => { this.$modal.msgSuccess('准入证据已更新'); this.visible = false; return this.onRefresh() })
    },
    optionLabel(user) { return `${this.value(user, 'nick_name', 'nickName') || this.value(user, 'user_name', 'userName')}（${this.value(user, 'user_name', 'userName')}）` },
    userLabel(row, prefix) { return this.value(row, `${prefix}_nick_name`, `${prefix}NickName`) || this.value(row, `${prefix}_user_name`, `${prefix}UserName`) || '未分配' },
    assigned(row) { return Number(this.value(row, 'owner_user_id', 'ownerUserId')) > 0 && Number(this.value(row, 'reviewer_user_id', 'reviewerUserId')) > 0 && !!this.value(row, 'due_at', 'dueAt') },
    overdue(row) { const due = this.value(row, 'due_at', 'dueAt'); return !!due && !['APPROVED', 'REJECTED'].includes(this.value(row, 'status', 'status')) && new Date(due).getTime() < Date.now() },
    statusType(row) { return ({ APPROVED: 'success', REJECTED: 'danger', IN_REVIEW: 'warning', OPEN: 'info' })[this.value(row, 'status', 'status')] || 'info' }
  }
}
</script>
