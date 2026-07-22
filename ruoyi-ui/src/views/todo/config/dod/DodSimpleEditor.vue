<template>
  <section class="dod-simple-editor" v-loading="loading">
    <div class="guided-step">
      <div class="guided-step__number">1</div>
      <div class="guided-step__body">
        <strong>选择业务类型</strong><p>系统将只展示该业务可以使用的字段、材料和校验器。</p>
        <el-select :value="businessType" :disabled="readonly" class="full-width" @input="businessTypeChanged"><el-option v-for="item in businessTypes" :key="item" :label="businessLabel(item)" :value="item" /></el-select>
      </div>
    </div>

    <div class="guided-step">
      <div class="guided-step__number">2</div>
      <div class="guided-step__body">
        <strong>推荐配方</strong><p>选择常用场景后可一键带出完成要求，也可以继续微调。</p>
        <div v-if="recipes.length" class="recipe-list"><button v-for="item in recipes" :key="item.code" type="button" :disabled="readonly" @click="chooseRecipe(item)"><i class="el-icon-magic-stick" /><span><strong>{{ item.name }}</strong><small>{{ item.description }}</small></span></button></div>
        <el-empty v-else description="该业务类型暂无推荐配方，可直接配置下方条件" :image-size="54" />
      </div>
    </div>

    <div class="guided-step">
      <div class="guided-step__number">3</div>
      <div class="guided-step__body">
        <strong>完成时需要什么</strong><p>用户在待办上提交时，系统会按这里的要求逐项检查。</p>
        <el-form label-position="top" class="requirement-grid">
          <el-form-item label="必须填写"><el-select v-model="draft.requiredFields" multiple filterable :disabled="readonly" class="full-width" placeholder="选择业务字段" @change="emitValue"><el-option v-for="item in fields" :key="item.code" :label="`${item.name} · ${item.code}`" :value="item.code" /></el-select></el-form-item>
          <el-form-item label="必须上传"><el-select v-model="draft.requiredAttachments" multiple filterable :disabled="readonly" class="full-width" placeholder="选择材料类型" @change="emitValue"><el-option v-for="item in materials" :key="item.code" :label="item.name" :value="item.code" /></el-select></el-form-item>
          <el-form-item label="系统自动校验"><el-select v-model="draft.validatorRefs" multiple filterable :disabled="readonly" class="full-width" placeholder="选择校验能力" @change="emitValue"><el-option v-for="item in selectableValidators" :key="item.code" :label="`${item.name || item.code} · ${item.description || ''}`" :value="item.code" /></el-select></el-form-item>
        </el-form>
      </div>
    </div>

    <el-collapse class="advanced-settings">
      <el-collapse-item title="高级设置：条件必填" name="conditions">
        <p class="section-note">只有满足指定条件时，才要求用户补充某个字段。普通规则可以跳过本节。</p>
        <div v-for="(condition, index) in draft.conditionalRules" :key="index" class="condition-row">
          <span>当</span>
          <el-select v-model="condition.when.field" :disabled="readonly" filterable placeholder="条件字段" @change="emitValue"><el-option v-for="item in fields" :key="item.code" :label="item.name" :value="item.code" /></el-select>
          <el-select v-model="condition.kind" :disabled="readonly" @change="kindChanged(condition)"><el-option label="等于" value="equals" /><el-option label="有值" value="present" /></el-select>
          <el-input v-if="condition.kind === 'equals'" v-model="condition.when.equals" :disabled="readonly" placeholder="期望值" @input="emitValue" />
          <span>时，必须填写</span>
          <el-select v-model="condition.field" :disabled="readonly" filterable placeholder="目标字段" @change="emitValue"><el-option v-for="item in fields" :key="item.code" :label="item.name" :value="item.code" /></el-select>
          <el-button v-if="!readonly" type="text" class="danger-text" @click="removeCondition(index)">删除</el-button>
        </div>
        <el-button v-if="!readonly" plain size="small" icon="el-icon-plus" @click="addCondition">添加条件要求</el-button>
      </el-collapse-item>
    </el-collapse>

    <div class="rule-summary"><i class="el-icon-document-checked" /><div><strong>规则摘要</strong><p>{{ ruleSummary }}</p></div></div>

    <el-card v-if="persisted" shadow="never" class="sample-test">
      <div slot="header"><strong>执行样例验证</strong><span>保存后可用一组样例检查规则是否符合预期。</span></div>
      <el-input v-model="samplePayloadText" type="textarea" :rows="4" :disabled="testing" placeholder='样例业务数据，如 {"contactResult":"SUCCESS"}' />
      <el-select v-model="sampleAttachments" multiple filterable class="full-width top-gap" :disabled="testing" placeholder="选择样例中已上传的材料"><el-option v-for="item in materials" :key="item.code" :label="item.name" :value="item.code" /></el-select>
      <el-button type="primary" plain class="top-gap" :loading="testing" @click="runTest">执行样例验证</el-button>
      <el-alert v-if="testResult" class="top-gap" :title="testResult.passed ? '样例满足全部完成条件' : resultSummary" :type="testResult.passed ? 'success' : 'warning'" :closable="false" show-icon />
    </el-card>
  </section>
</template>

<script>
import { listFieldResources, listMaterialResources, listValidatorResources, listDodRecipeResources } from '@/api/todo-resources'
const { emptySimpleRule, applyRecipe, summary } = require('./dod-recipe-codec')

export default {
  name: 'DodSimpleEditor',
  props: { value: { type: Object, default: () => emptySimpleRule() }, businessType: { type: String, default: 'LEAD' }, readonly: Boolean, persisted: Boolean, testing: Boolean, testResult: { type: Object, default: null } },
  data() { return { loading: false, fields: [], materials: [], validators: [], recipes: [], draft: { ...emptySimpleRule(), ...(this.value || {}) }, businessTypes: ['LEAD', 'CUSTOMER', 'CONTRACT', 'CASE', 'MATTER'], samplePayloadText: '{}', sampleAttachments: [] } },
  computed: {
    selectableValidators() { return this.validators.filter(item => item.selectable) },
    ruleSummary() { return summary(this.draft) },
    resultSummary() { const missingFields = (this.testResult && this.testResult.missingFields) || []; const missingAttachments = (this.testResult && this.testResult.missingAttachments) || []; const issues = (this.testResult && this.testResult.validatorIssues) || []; return `未通过：缺少 ${missingFields.length} 个字段、${missingAttachments.length} 类材料，${issues.length} 项自动校验未满足` }
  },
  watch: { value: { deep: true, handler(value) { if (JSON.stringify(value || {}) !== JSON.stringify(this.draft)) this.draft = { ...emptySimpleRule(), ...(value || {}) } } }, businessType() { this.loadResources() } },
  created() { this.loadResources() },
  methods: {
    businessLabel(value) { return ({ LEAD: '线索', CUSTOMER: '客户', CONTRACT: '合同', CASE: '案件', MATTER: '事项' })[value] || value },
    async loadResources() { this.loading = true; try { const params = { businessType: this.businessType }; const [fields, materials, validators, recipes] = await Promise.all([listFieldResources(params), listMaterialResources(params), listValidatorResources(params), listDodRecipeResources(params)]); this.fields = fields.data || []; this.materials = materials.data || []; this.validators = validators.data || []; this.recipes = recipes.data || [] } catch (error) { this.fields = []; this.materials = []; this.validators = []; this.recipes = []; this.$modal.msgError('加载完成条件资源失败') } finally { this.loading = false } },
    businessTypeChanged(value) { this.$emit('business-type', value); this.draft = emptySimpleRule(); this.draft.businessType = value; this.emitValue() },
    chooseRecipe(recipe) { this.draft = applyRecipe(this.draft, recipe); this.emitValue(); this.$modal.msgSuccess(`已应用“${recipe.name}”配方`) },
    emitValue() { this.$emit('input', { ...this.draft, requiredFields: this.draft.requiredFields.slice(), requiredAttachments: this.draft.requiredAttachments.slice(), validatorRefs: this.draft.validatorRefs.slice(), conditionalRules: this.draft.conditionalRules.map(item => ({ field: item.field, when: { ...(item.when || {}) } })) }) },
    addCondition() { this.draft.conditionalRules.push({ field: '', kind: 'equals', when: { field: '', equals: '' } }); this.emitValue() },
    removeCondition(index) { this.draft.conditionalRules.splice(index, 1); this.emitValue() },
    kindChanged(condition) { condition.when = condition.kind === 'equals' ? { field: condition.when.field || '', equals: '' } : { field: condition.when.field || '', present: true }; this.emitValue() },
    runTest() { try { const payload = JSON.parse(this.samplePayloadText || '{}'); if (!payload || Array.isArray(payload) || typeof payload !== 'object') throw new Error(); this.$emit('test', { payload, attachments: this.sampleAttachments.slice() }) } catch (error) { this.$modal.msgError('样例业务数据必须是 JSON 对象') } },
    validate() { if (!this.businessType) throw new Error('请选择业务类型'); const knownFields = new Set(this.fields.map(item => item.code)); const knownMaterials = new Set(this.materials.map(item => item.code)); const selectable = new Set(this.selectableValidators.map(item => item.code)); if (this.draft.requiredFields.some(item => !knownFields.has(item))) throw new Error('必填字段中存在不可用选项'); if (this.draft.requiredAttachments.some(item => !knownMaterials.has(item))) throw new Error('必传材料中存在不可用选项'); if (this.draft.validatorRefs.some(item => !selectable.has(item))) throw new Error('自动校验中存在不可用能力'); return true }
  }
}
</script>

<style scoped lang="scss">
.guided-step { display: grid; grid-template-columns: 34px 1fr; gap: 12px; padding: 16px 0; border-bottom: 1px solid #eef2f7; }.guided-step__number { display: flex; justify-content: center; align-items: center; width: 30px; height: 30px; border-radius: 50%; background: #2563eb; color: #fff; font-weight: 700; }.guided-step__body > p,.section-note { margin: 5px 0 12px; color: #64748b; font-size: 12px; }.recipe-list { display: grid; grid-template-columns: repeat(auto-fit,minmax(220px,1fr)); gap: 10px; }.recipe-list button { display: flex; gap: 10px; padding: 12px; border: 1px solid #dbe5f1; border-radius: 8px; background: #fff; text-align: left; cursor: pointer; }.recipe-list button:hover { border-color: #2563eb; background: #eff6ff; }.recipe-list i { color: #2563eb; font-size: 19px; }.recipe-list span,.recipe-list small { display: block; }.recipe-list small { margin-top: 4px; color: #64748b; line-height: 1.4; }.requirement-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 14px; }.requirement-grid .el-form-item:last-child { grid-column: 1 / -1; }.full-width { width: 100%; }.advanced-settings { margin: 12px 0; }.condition-row { display: grid; grid-template-columns: auto 1fr 90px 1fr auto 1fr auto; align-items: center; gap: 7px; margin-bottom: 8px; }.rule-summary { display: flex; gap: 12px; padding: 14px; border-radius: 8px; background: #eff6ff; color: #1e3a8a; }.rule-summary i { font-size: 24px; }.rule-summary p { margin: 5px 0 0; color: #475569; }.sample-test { margin-top: 14px; }.sample-test [slot="header"] span { margin-left: 10px; color: #64748b; font-size: 12px; }.top-gap { margin-top: 10px; }.danger-text { color: #ef4444; }
@media (max-width: 760px) { .requirement-grid { grid-template-columns: 1fr; }.requirement-grid .el-form-item:last-child { grid-column: auto; }.condition-row { grid-template-columns: 1fr; } }
</style>
