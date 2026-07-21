<template>
  <section>
    <el-alert title="模板只保存 SLA 规则引用；规则内容由 SLA 规则库独立版本化管理。" type="info" :closable="false" show-icon />
    <el-form ref="form" :model="model" :rules="rules" label-width="110px" class="top-gap">
      <el-form-item label="SLA 规则" prop="slaRuleId">
        <el-select v-model="model.slaRuleId" :disabled="readonly" clearable filterable class="full">
          <el-option v-for="item in rulesCatalog" :key="ruleId(item)" :label="`${field(item,'ruleCode','rule_code')} · ${field(item,'ruleName','rule_name')}`" :value="ruleId(item)" />
        </el-select>
        <el-button v-if="!readonly" v-hasPermi="['todo:sla-rule:create']" type="text" icon="el-icon-plus" @click="openNested">新建 SLA 规则</el-button>
      </el-form-item>
      <el-descriptions v-if="selected" :column="3" border size="small"><el-descriptions-item label="类型">{{ field(selected,'slaType','sla_type') }}</el-descriptions-item><el-descriptions-item label="时长">{{ field(selected,'durationValue','duration_value') }} {{ field(selected,'durationUnit','duration_unit') }}</el-descriptions-item><el-descriptions-item label="日历">{{ field(selected,'calendarCode','calendar_code') }}</el-descriptions-item></el-descriptions>
    </el-form>
    <sla-rule-drawer :visible.sync="nestedOpen" mode="create" :rule="null" @saved="nestedSaved" />
  </section>
</template>
<script>
import SlaRuleDrawer from '../../sla/SlaRuleDrawer'
import { listTemplateSlaRuleCatalog } from '@/api/todo-config'
export default {
  name: 'TemplateSlaStep', components: { SlaRuleDrawer }, dicts: ['law_todo_rule_status'],
  props: { value: [Number, String], readonly: Boolean },
  data() { return { model: { slaRuleId: this.value ? Number(this.value) : null }, rulesCatalog: [], nestedOpen: false, rules: { slaRuleId: [{ required: true, message: '请选择 SLA 规则', trigger: 'change' }] } } },
  computed: { selected() { return this.rulesCatalog.find(item => this.ruleId(item) === Number(this.model.slaRuleId)) } },
  watch: { value(value) { this.model.slaRuleId = value ? Number(value) : null }, 'model.slaRuleId'(value) { this.$emit('input', value || null); this.$emit('dirty-change') } },
  created() { this.loadRules() },
  methods: { field(row, camel, snake) { return row && (row[camel] !== undefined ? row[camel] : row[snake]) }, ruleId(row) { return Number(this.field(row, 'id', 'id')) }, async loadRules() { const response = await listTemplateSlaRuleCatalog(); this.rulesCatalog = response.data || [] }, openNested() { this.nestedOpen = true }, async nestedSaved(savedId) { this.nestedOpen = false; await this.loadRules(); const exact = Number(savedId); if (exact > 0 && this.rulesCatalog.some(item => this.ruleId(item) === exact)) this.model.slaRuleId = exact }, validate() { return new Promise(resolve => this.$refs.form.validate(valid => resolve(valid))) } }
}
</script>
<style scoped>.full{width:100%}.top-gap{margin-top:16px}</style>
