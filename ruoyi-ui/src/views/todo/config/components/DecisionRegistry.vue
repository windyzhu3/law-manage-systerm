<template>
  <div>
    <el-button v-hasPermi="['todo:decision:edit']" type="primary" size="mini" @click="show({})">新增决策</el-button>
    <el-table :data="rows">
      <el-table-column prop="code" label="编码" width="100" />
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column label="责任治理" min-width="220">
        <template slot-scope="scope">
          <div>{{ ownerLabel(scope.row) }} / {{ value(scope.row, 'owner_role_key', 'ownerRoleKey') || '未分配角色' }}</div>
          <el-tag v-if="!governanceReady(scope.row)" size="mini" type="danger">未完成责任治理</el-tag>
          <el-tag v-else-if="overdue(scope.row)" size="mini" type="warning">已逾期</el-tag>
          <span v-else>{{ value(scope.row, 'due_at', 'dueAt') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="阶段" width="110">
        <template slot-scope="scope">{{ phaseLabel(value(scope.row, 'delivery_phase', 'deliveryPhase')) }}</template>
      </el-table-column>
      <el-table-column label="影响模板" min-width="150">
        <template slot-scope="scope">{{ (scope.row.impactedTemplateCodes || []).join(', ') }}</template>
      </el-table-column>
      <el-table-column label="操作" width="130">
        <template slot-scope="scope">
          <el-button v-hasPermi="['todo:decision:edit']" type="text" @click="show(scope.row)">编辑</el-button>
          <el-button v-if="scope.row.status !== 'OPEN'" v-hasPermi="['todo:decision:edit']" type="text" @click="reopen(scope.row)">重新打开</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog :title="form.decisionId ? '编辑决策' : '新增决策'" :visible.sync="open" :close-on-click-modal="false" width="680px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="编码" required><el-input v-model="form.code" :disabled="!!form.decisionId" /></el-form-item>
        <el-form-item label="标题" required><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
        <el-form-item label="阻塞"><el-switch v-model="form.blocking" /></el-form-item>
        <el-form-item label="负责人" :required="form.blocking">
          <el-select v-model="form.ownerUserId" filterable clearable style="width:100%" placeholder="选择具体责任人">
            <el-option v-for="user in options.users" :key="userValue(user)" :label="userLabel(user)" :value="userValue(user)" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任角色" :required="form.blocking">
          <el-select v-model="form.ownerRoleKey" filterable clearable style="width:100%" placeholder="选择稳定角色编码">
            <el-option v-for="role in options.roles" :key="value(role, 'role_key', 'roleKey')" :label="roleLabel(role)" :value="value(role, 'role_key', 'roleKey')" />
          </el-select>
        </el-form-item>
        <el-form-item label="截止时间" :required="form.blocking">
          <el-date-picker v-model="form.dueAt" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" style="width:100%" placeholder="选择截止时间" />
        </el-form-item>
        <el-form-item label="交付阶段" required>
          <el-select v-model="form.deliveryPhase" style="width:100%">
            <el-option v-for="phase in options.deliveryPhases" :key="phase" :label="phaseLabel(phase)" :value="phase" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status"><el-option label="OPEN" value="OPEN" /><el-option label="RESOLVED" value="RESOLVED" /><el-option label="CLOSED" value="CLOSED" /></el-select>
        </el-form-item>
        <el-form-item v-if="form.status !== 'OPEN'" label="结论" required><el-input v-model="form.conclusion" /></el-form-item>
        <el-form-item v-if="form.status !== 'OPEN'" label="处理方案" required><el-input v-model="form.resolution" /></el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="open=false">取消</el-button>
        <el-button v-hasPermi="['todo:decision:edit']" type="primary" :loading="saving" @click="save">保存</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { createTodoDecision, updateTodoDecision, getDecisionGovernanceOptions } from '@/api/todo-definition'
import { validateDecision } from '../resource-contract'

const PHASE_LABELS = { PHASE_ONE: '阶段一', PHASE_TWO: '阶段二', CROSS_PHASE: '跨阶段' }

export default {
  props: { rows: Array, onRefresh: Function },
  data() {
    return { open: false, saving: false, options: { users: [], roles: [], deliveryPhases: ['PHASE_ONE', 'PHASE_TWO', 'CROSS_PHASE'] }, form: {} }
  },
  created() { this.loadOptions() },
  methods: {
    value(row, snake, camel) { return row && (row[camel] !== undefined ? row[camel] : row[snake]) },
    async loadOptions() {
      try {
        const response = await getDecisionGovernanceOptions()
        this.options = { ...this.options, ...(response.data || {}) }
      } catch (error) { this.$modal.msgError(error.message || '责任治理选项加载失败') }
    },
    show(row) {
      this.form = {
        decisionId: row.decisionId, version: row.version || 0, code: row.code || '', title: row.title || '',
        description: row.description || '', blocking: row.blocking !== false, status: row.status || 'OPEN',
        conclusion: row.conclusion || '', resolution: row.resolution || '',
        ownerUserId: this.value(row, 'owner_user_id', 'ownerUserId') || null,
        ownerRoleKey: this.value(row, 'owner_role_key', 'ownerRoleKey') || '',
        dueAt: this.value(row, 'due_at', 'dueAt') || '',
        deliveryPhase: this.value(row, 'delivery_phase', 'deliveryPhase') || 'PHASE_ONE'
      }
      this.open = true
    },
    reopen(row) { this.show({ ...row, status: 'OPEN', conclusion: '', resolution: '' }) },
    async save() {
      try {
        const form = validateDecision(this.form)
        this.saving = true
        const data = { ...form, actionId: `decision-${form.decisionId || 'create'}-${Date.now()}` }
        if (form.decisionId) await updateTodoDecision(form.decisionId, data)
        else await createTodoDecision(data)
        await this.onRefresh()
        this.open = false
      } catch (error) { this.$modal.msgError(error.message || '保存失败') } finally { this.saving = false }
    },
    governanceReady(row) {
      return Number(this.value(row, 'owner_user_id', 'ownerUserId')) > 0 && !!this.value(row, 'owner_role_key', 'ownerRoleKey') &&
        !!this.value(row, 'due_at', 'dueAt') && !!this.value(row, 'delivery_phase', 'deliveryPhase')
    },
    overdue(row) { const due = this.value(row, 'due_at', 'dueAt'); return row.status === 'OPEN' && due && new Date(String(due).replace(' ', 'T')).getTime() < Date.now() },
    phaseLabel(phase) { return PHASE_LABELS[phase] || phase || '未分阶段' },
    userValue(user) { return Number(this.value(user, 'user_id', 'userId')) },
    userLabel(user) { return `${this.value(user, 'nick_name', 'nickName') || this.value(user, 'user_name', 'userName')}（${this.value(user, 'user_name', 'userName')}）` },
    ownerLabel(row) {
      const direct = this.value(row, 'owner_nick_name', 'ownerNickName') || this.value(row, 'owner_user_name', 'ownerUserName')
      const option = this.options.users.find(user => this.userValue(user) === Number(this.value(row, 'owner_user_id', 'ownerUserId')))
      return direct || (option && this.userLabel(option)) || '未分配负责人'
    },
    roleLabel(role) { return `${this.value(role, 'role_name', 'roleName')}（${this.value(role, 'role_key', 'roleKey')}）` }
  }
}
</script>
