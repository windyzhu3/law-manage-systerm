<template>
  <section>
    <el-alert title="可组合多个完成条件。列表顺序就是运行时校验顺序，可拖动调整。" type="info" :closable="false" show-icon />
    <el-form ref="form" :model="model" :rules="rules" label-width="110px" class="top-gap">
      <el-form-item label="组合方式"><el-radio-group v-model="model.composition" :disabled="readonly"><el-radio-button label="ALL">全部满足</el-radio-button><el-radio-button label="ANY">任一满足</el-radio-button></el-radio-group></el-form-item>
      <el-form-item label="DoD 规则" prop="dodRuleIds"><el-select v-model="model.dodRuleIds" :disabled="readonly" multiple filterable class="full"><el-option v-for="item in rulesCatalog" :key="ruleId(item)" :label="`${field(item,'ruleCode','rule_code')} · ${field(item,'ruleName','rule_name')}`" :value="ruleId(item)" /></el-select><el-button v-if="!readonly" type="text" icon="el-icon-plus" @click="openNested">新建 DoD 规则</el-button></el-form-item>
    </el-form>
    <el-table :data="selectedRules" border size="mini"><el-table-column type="index" label="顺序" width="70" /><el-table-column prop="ruleCode" label="规则编码" min-width="150" /><el-table-column prop="ruleName" label="规则名称" min-width="180" /><el-table-column v-if="!readonly" label="调整" width="120"><template slot-scope="{ $index }"><el-button type="text" :disabled="$index===0" @click="move($index,-1)">上移</el-button><el-button type="text" :disabled="$index===selectedRules.length-1" @click="move($index,1)">下移</el-button></template></el-table-column></el-table>
    <dod-rule-drawer :visible.sync="nestedOpen" mode="create" :rule="null" @saved="nestedSaved" />
  </section>
</template>
<script>
import DodRuleDrawer from '../../dod/DodRuleDrawer'
import { listDodRules, listTemplateValidatorCatalog } from '@/api/todo-config'
export default {
  name: 'TemplateDodStep', components: { DodRuleDrawer }, dicts: ['law_todo_rule_status'],
  props: { value: { type: Array, default: () => [] }, composition: { type: String, default: 'ALL' }, readonly: Boolean },
  data() { return { syncing: false, model: { dodRuleIds: this.value.map(Number), composition: this.composition || 'ALL' }, rulesCatalog: [], validatorCatalog: [], nestedOpen: false, knownIds: [], rules: { dodRuleIds: [{ type: 'array', required: true, message: '请选择至少一条 DoD 规则', trigger: 'change' }] } } },
  computed: { selectedRules() { return this.model.dodRuleIds.map(id => { const row = this.rulesCatalog.find(item => this.ruleId(item) === Number(id)) || {}; return { ruleCode: this.field(row,'ruleCode','rule_code') || id, ruleName: this.field(row,'ruleName','rule_name') || '规则不可用' } }) } },
  watch: { value: { deep: true, handler(value) { const next = (value || []).map(Number); if (JSON.stringify(next) === JSON.stringify(this.model.dodRuleIds)) return; this.syncing = true; this.model.dodRuleIds = next; this.$nextTick(() => { this.syncing = false }) } }, composition(value) { if (this.model.composition !== (value || 'ALL')) this.model.composition = value || 'ALL' }, model: { deep: true, handler(value) { if (this.syncing) return; this.$emit('input', value.dodRuleIds.slice()); this.$emit('composition-change', value.composition); this.$emit('dirty-change') } } },
  created() { this.loadRules(); this.loadValidatorCatalog() },
  methods: { field(row, camel, snake) { return row && (row[camel] !== undefined ? row[camel] : row[snake]) }, ruleId(row) { return Number(this.field(row, 'dodRuleId', 'dod_rule_id')) }, async loadRules() { const response = await listDodRules({ pageNum: 1, pageSize: 200, status: '0' }); this.rulesCatalog = response.rows || [] }, async loadValidatorCatalog() { const response = await listTemplateValidatorCatalog(); this.validatorCatalog = response.data || [] }, move(index, delta) { const copy = this.model.dodRuleIds.slice(); const target = index + delta; copy.splice(target, 0, copy.splice(index, 1)[0]); this.model.dodRuleIds = copy }, openNested() { this.knownIds = this.rulesCatalog.map(this.ruleId); this.nestedOpen = true }, async nestedSaved() { this.nestedOpen = false; await this.loadRules(); const created = this.rulesCatalog.find(item => !this.knownIds.includes(this.ruleId(item))); if (created) this.model.dodRuleIds.push(this.ruleId(created)) }, validate() { return new Promise(resolve => this.$refs.form.validate(valid => resolve(valid))) } }
}
</script>
<style scoped>.full{width:100%}.top-gap{margin-top:16px}</style>
