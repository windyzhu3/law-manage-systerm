<template>
  <section>
    <el-alert title="可组合多个完成条件；列表顺序即运行时校验顺序。" type="info" :closable="false" show-icon />
    <el-form ref="form" :model="model" :rules="rules" label-width="110px" class="top-gap">
      <el-form-item label="组合方式">
        <el-radio-group v-model="model.composition" :disabled="readonly">
          <el-radio-button label="ALL">全部满足</el-radio-button>
          <el-radio-button label="ANY">任一满足</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="DoD 规则" prop="dodRuleIds">
        <el-select v-model="model.dodRuleIds" :disabled="readonly" multiple filterable class="full">
          <el-option v-for="item in rulesCatalog" :key="ruleId(item)" :label="`${field(item,'ruleCode','rule_code')} · ${field(item,'ruleName','rule_name')}`" :value="ruleId(item)" />
        </el-select>
        <el-button v-if="!readonly" v-hasPermi="['todo:dod-rule:create']" type="text" icon="el-icon-plus" @click="openNested">新建 DoD 规则</el-button>
      </el-form-item>
      <el-form-item label="完成表单字段" prop="uiFields">
        <el-select v-model="model.uiFields" data-testid="template-ui-fields" :disabled="readonly" multiple filterable allow-create default-first-option class="full" placeholder="输入字段编码后回车，例如 contactResult">
          <el-option v-for="fieldCode in model.uiFields" :key="fieldCode" :label="fieldCode" :value="fieldCode" />
        </el-select>
        <div class="field-help">仅需要为所选 DoD 规则实际引用、且不能由系统派生的字段配置控件。</div>
      </el-form-item>
      <el-form-item label="系统派生字段">
        <el-select v-model="model.systemDerivedFields" data-testid="template-system-derived-fields" :disabled="readonly" multiple filterable allow-create default-first-option class="full" placeholder="无需用户填写、由系统生成的字段编码">
          <el-option v-for="fieldCode in model.systemDerivedFields" :key="fieldCode" :label="fieldCode" :value="fieldCode" />
        </el-select>
      </el-form-item>
      <el-alert v-if="missingUiFields.length" :title="`DoD 仍缺少可采集字段：${missingUiFields.join('、')}`" type="error" :closable="false" show-icon />
    </el-form>
    <el-table :data="selectedRules" border size="mini">
      <el-table-column type="index" label="顺序" width="70" />
      <el-table-column prop="ruleCode" label="规则编码" min-width="150" />
      <el-table-column prop="ruleName" label="规则名称" min-width="180" />
      <el-table-column v-if="!readonly" label="调整" width="120">
        <template slot-scope="{ $index }"><el-button type="text" :disabled="$index===0" @click="move($index,-1)">上移</el-button><el-button type="text" :disabled="$index===selectedRules.length-1" @click="move($index,1)">下移</el-button></template>
      </el-table-column>
    </el-table>
    <dod-rule-drawer :visible.sync="nestedOpen" mode="create" :rule="null" :provided-validators="validatorCatalog" :business-type="businessType" @saved="nestedSaved" />
  </section>
</template>

<script>
import DodRuleDrawer from '../../dod/DodRuleDrawer'
import { listTemplateDodRuleCatalog, listTemplateValidatorCatalog } from '@/api/todo-config'
import { missingUiFields as findMissingUiFields } from '../template-dod-ui-rules'

export default {
  name: 'TemplateDodStep',
  components: { DodRuleDrawer },
  dicts: ['law_todo_rule_status'],
  props: {
    value: { type: Array, default: () => [] },
    composition: { type: String, default: 'ALL' },
    uiFields: { type: Array, default: () => [] },
    systemDerivedFields: { type: Array, default: () => [] },
    businessType: { type: String, default: 'LEAD' },
    readonly: Boolean
  },
  data() {
    return {
      syncing: false,
      model: { dodRuleIds: this.value.map(Number), composition: this.composition || 'ALL', uiFields: this.uiFields.slice(), systemDerivedFields: this.systemDerivedFields.slice() },
      rulesCatalog: [], validatorCatalog: [], nestedOpen: false,
      rules: {
        dodRuleIds: [{ type: 'array', required: true, message: '请选择至少一条 DoD 规则', trigger: 'change' }],
        uiFields: [{ validator: (rule, value, callback) => this.missingUiFields.length ? callback(new Error(`DoD 缺少字段：${this.missingUiFields.join('、')}`)) : callback(), trigger: 'change' }]
      }
    }
  },
  computed: {
    selectedRules() {
      return this.model.dodRuleIds.map(id => {
        const row = this.rulesCatalog.find(item => this.ruleId(item) === Number(id)) || {}
        return { ...row, ruleCode: this.field(row, 'ruleCode', 'rule_code') || id, ruleName: this.field(row, 'ruleName', 'rule_name') || '规则不可用' }
      })
    },
    missingUiFields() { return findMissingUiFields(this.rulesCatalog, this.model.dodRuleIds, this.model.uiFields, this.model.systemDerivedFields) }
  },
  watch: {
    value: { deep: true, handler(value) { this.syncArray('dodRuleIds', (value || []).map(Number)) } },
    composition(value) { if (this.model.composition !== (value || 'ALL')) this.model.composition = value || 'ALL' },
    uiFields: { deep: true, handler(value) { this.syncArray('uiFields', value || []) } },
    systemDerivedFields: { deep: true, handler(value) { this.syncArray('systemDerivedFields', value || []) } },
    model: { deep: true, handler(value) {
      if (this.syncing) return
      this.$emit('input', value.dodRuleIds.slice())
      this.$emit('composition-change', value.composition)
      this.$emit('ui-fields-change', value.uiFields.slice())
      this.$emit('system-derived-fields-change', value.systemDerivedFields.slice())
      this.$emit('dirty-change')
      this.$nextTick(() => this.validateUiFields())
    } }
  },
  created() { this.loadRules(); this.loadValidatorCatalog() },
  methods: {
    field(row, camel, snake) { return row && (row[camel] !== undefined ? row[camel] : row[snake]) },
    ruleId(row) { return Number(this.field(row, 'id', 'id')) },
    syncArray(key, value) {
      const next = (value || []).slice()
      if (JSON.stringify(next) === JSON.stringify(this.model[key])) return
      this.syncing = true
      this.model[key] = next
      this.$nextTick(() => { this.syncing = false })
    },
    validateUiFields() { if (this.$refs.form) this.$refs.form.validateField('uiFields') },
    async loadRules() { const response = await listTemplateDodRuleCatalog(); this.rulesCatalog = response.data || []; this.$nextTick(() => this.validateUiFields()) },
    async loadValidatorCatalog() { const response = await listTemplateValidatorCatalog(); this.validatorCatalog = response.data || [] },
    move(index, delta) { const copy = this.model.dodRuleIds.slice(); const target = index + delta; copy.splice(target, 0, copy.splice(index, 1)[0]); this.model.dodRuleIds = copy },
    openNested() { this.nestedOpen = true },
    async nestedSaved(savedId) { this.nestedOpen = false; await this.loadRules(); const exact = Number(savedId); if (exact > 0 && this.rulesCatalog.some(item => this.ruleId(item) === exact) && !this.model.dodRuleIds.includes(exact)) this.model.dodRuleIds.push(exact) },
    validate() { return new Promise(resolve => this.$refs.form.validate(valid => resolve(valid))) }
  }
}
</script>

<style scoped>.full{width:100%}.top-gap{margin-top:16px}.field-help{color:#64748b;font-size:12px;line-height:20px}</style>
