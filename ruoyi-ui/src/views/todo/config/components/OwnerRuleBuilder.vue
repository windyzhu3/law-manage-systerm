<template>
  <div class="definition-builder">
    <el-form-item label="负责人规则" prop="owner.config.type">
      <el-select :value="model.type" :disabled="readonly" @input="change('type', $event)">
        <el-option label="业务载荷字段" value="PAYLOAD" /><el-option label="指定角色" value="ROLE" /><el-option label="指定部门" value="DEPT" /><el-option label="指定用户" value="USER" />
      </el-select>
      <el-input v-if="model.type === 'PAYLOAD'" :value="model.operand" :disabled="readonly" placeholder="载荷字段，例如 ownerId" @input="change('operand', $event)" />
      <el-select v-else-if="model.type === 'ROLE'" :value="model.roleKey" :disabled="readonly" filterable placeholder="选择角色" @input="change('roleKey', $event)">
        <el-option v-for="role in roles" :key="role.roleKey" :label="`${role.roleName}（${role.roleKey}）`" :value="role.roleKey" />
      </el-select>
      <el-select v-else-if="model.type === 'DEPT'" :value="model.departmentCode" :disabled="readonly" filterable placeholder="选择部门" @input="change('departmentCode', $event)">
        <el-option v-for="dept in selectableDepartments" :key="dept.deptCode" :label="`${dept.deptName}（${dept.deptCode}）`" :value="dept.deptCode" />
      </el-select>
      <el-input v-else :value="model.operand" :disabled="readonly" placeholder="用户标识" @input="change('operand', $event)" />
    </el-form-item>
  </div>
</template>
<script>
import { listRole } from '@/api/system/role'
import { listDept } from '@/api/system/dept'
export default {
  name: 'OwnerRuleBuilder', props: { value: { type: Object, default: () => ({}) }, readonly: Boolean },
  data() { return { roles: [], departments: [] } }, computed: { model() { return { type: 'PAYLOAD', operand: 'ownerId', ...this.value } }, selectableDepartments() { return this.departments.filter(dept => dept.deptCode && !/^DEPT_\d+$/.test(dept.deptCode)) } },
  created() { listRole({ pageNum: 1, pageSize: 1000 }).then(response => { this.roles = response.rows || [] }); listDept().then(response => { this.departments = response.data || [] }) },
  methods: { change(key, value) { const stableKey = this.model.type === 'ROLE' ? 'roleKey' : this.model.type === 'DEPT' ? 'departmentCode' : key; this.$emit('input', { ...this.model, [stableKey]: value, ...(stableKey !== 'operand' ? { operand: undefined } : {}) }) } }
}
</script>
