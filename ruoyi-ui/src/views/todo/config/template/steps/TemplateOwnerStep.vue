<template>
  <el-form ref="form" :model="model" :rules="rules" label-width="120px">
    <el-form-item label="负责人规则" prop="type"><el-select v-model="model.type" :disabled="readonly" class="full" @change="resetCandidates"><el-option v-for="item in dict.type.law_todo_owner_rule_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
    <el-form-item v-if="catalogType" label="候选负责人" prop="candidates"><el-select v-model="model.candidates" :disabled="readonly" multiple filterable class="full"><el-option v-for="item in ownerOptions" :key="item.value" :label="item.secondaryLabel ? `${item.label}（${item.secondaryLabel}）` : item.label" :value="item.value" /></el-select></el-form-item>
    <el-form-item v-if="model.type === 'PAYLOAD'" label="载荷字段" prop="payloadPath"><el-input v-model.trim="model.payloadPath" :disabled="readonly" placeholder="例如：$.ownerId" /></el-form-item>
    <el-form-item label="抄送对象"><el-select v-model="model.cc" :disabled="readonly" multiple filterable class="full"><el-option v-for="item in catalogs" :key="`${item.type}:${item.value}`" :label="`${item.type} · ${item.label}`" :value="`${item.type}:${item.value}`" /></el-select></el-form-item>
    <el-row :gutter="16"><el-col :span="12"><el-form-item label="跳过不可用人员"><el-switch v-model="model.skipUnavailable" :disabled="readonly" /></el-form-item></el-col><el-col :span="12"><el-form-item label="应用委派关系"><el-switch v-model="model.useDelegation" :disabled="readonly" /></el-form-item></el-col></el-row>
  </el-form>
</template>
<script>
import { listTemplateOwnerCatalog } from '@/api/todo-config'
export default {
  name: 'TemplateOwnerStep', dicts: ['law_todo_owner_rule_type'],
  props: { value: { type: Object, required: true }, readonly: Boolean },
  data() { return { syncing: false, model: { type: '', candidates: [], cc: [], skipUnavailable: true, useDelegation: true, ...this.value }, catalogs: [], rules: { type: [{ required: true, message: '请选择负责人规则', trigger: 'change' }], candidates: [{ type: 'array', required: true, message: '请选择至少一个候选负责人', trigger: 'change' }], payloadPath: [{ required: true, message: '请输入载荷字段路径', trigger: 'blur' }] } } },
  computed: { catalogType() { return ['USER', 'ROLE', 'DEPT', 'POST'].includes(this.model.type) ? this.model.type : '' }, ownerOptions() { return this.catalogs.filter(item => item.type === this.catalogType) } },
  watch: { value: { deep: true, handler(value) { const next = { type: '', candidates: [], cc: [], skipUnavailable: true, useDelegation: true, ...value }; if (JSON.stringify(next) === JSON.stringify(this.model)) return; this.syncing = true; this.model = next; this.$nextTick(() => { this.syncing = false }) } }, model: { deep: true, handler(value) { if (this.syncing) return; this.$emit('input', { ...value }); this.$emit('dirty-change') } } },
  created() { this.loadCatalog() },
  methods: { async loadCatalog() { const response = await listTemplateOwnerCatalog(); this.catalogs = response.data || [] }, resetCandidates() { this.model.candidates = []; this.model.payloadPath = '' }, validate() { return new Promise(resolve => this.$refs.form.validate(valid => resolve(valid))) } }
}
</script>
<style scoped>.full{width:100%}</style>
