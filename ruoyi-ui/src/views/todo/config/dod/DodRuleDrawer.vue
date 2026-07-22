<template>
  <config-detail-drawer ref="drawer" :visible="visible" :title="drawerTitle" size="820px" :dirty="isDirty" @update:visible="$emit('update:visible', $event)" @reset="reset">
    <el-form ref="form" :model="form" :rules="rules" label-width="112px" class="rule-drawer-form">
      <el-collapse v-model="sections">
        <el-collapse-item title="基本信息" name="basic">
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item label="规则编码" prop="ruleCode"><el-input v-model.trim="form.ruleCode" :disabled="readonly || persisted" maxlength="64" placeholder="如 LEAD_CONTACT_DOD" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="规则名称" prop="ruleName"><el-input v-model.trim="form.ruleName" :disabled="readonly || copyMode" maxlength="128" placeholder="如 线索首联完成条件" /></el-form-item></el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item label="规则类型" prop="ruleType"><el-select v-model="form.ruleType" :disabled="readonly || copyMode" class="full-width"><el-option v-for="item in dict.type.law_todo_dod_rule_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="状态"><el-input v-if="persisted && !copyMode" :value="statusLabel" disabled /><el-select v-else v-model="form.status" :disabled="readonly || copyMode" class="full-width"><el-option v-for="item in dict.type.law_todo_rule_status" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          </el-row>
        </el-collapse-item>

        <el-collapse-item title="完成要求向导" name="guide">
          <div class="editor-mode-bar"><span>向导模式面向业务管理员，所有内容均可选择。</span><el-switch v-model="advancedMode" :disabled="readonly || copyMode" active-text="高级兼容模式" /></div>
          <dod-simple-editor
            v-if="!advancedMode"
            ref="simpleEditor"
            v-model="simpleModel"
            :business-type="form.businessType"
            :readonly="readonly || copyMode"
            :persisted="persisted && !copyMode"
            :testing="testing"
            :test-result="testResult"
            @business-type="businessTypeChanged"
            @test="runSimpleTest"
          />
          <section v-else class="advanced-editor">
            <el-alert title="高级兼容模式仅用于修复历史规则或维护特殊 JSON。格式异常时不会覆盖原文。" type="warning" :closable="false" show-icon />
            <el-form-item label="业务类型"><el-select v-model="form.businessType" :disabled="readonly" class="full-width"><el-option v-for="item in businessTypes" :key="item" :label="item" :value="item" /></el-select></el-form-item>
            <el-form-item label="必填字段 JSON"><el-input v-model="raw.requiredFieldsJson" type="textarea" :rows="3" :disabled="readonly" /></el-form-item>
            <el-form-item label="必需材料 JSON"><el-input v-model="raw.requiredAttachmentsJson" type="textarea" :rows="3" :disabled="readonly" /></el-form-item>
            <el-form-item label="条件规则 JSON"><el-input v-model="raw.conditionalRulesJson" type="textarea" :rows="5" :disabled="readonly" /></el-form-item>
            <el-form-item label="校验器 JSON"><el-input v-model="raw.validatorRefsJson" type="textarea" :rows="3" :disabled="readonly" /></el-form-item>
            <el-form-item label="错误文案 JSON"><el-input v-model="raw.errorMessagesJson" type="textarea" :rows="3" :disabled="readonly" /></el-form-item>
          </section>
        </el-collapse-item>

        <el-collapse-item v-if="persisted" title="绑定与引用" name="binding">
          <el-descriptions :column="2" border size="small"><el-descriptions-item label="模板引用数">{{ form.referenceCount || 0 }}</el-descriptions-item><el-descriptions-item label="版本">{{ form.version }}</el-descriptions-item><el-descriptions-item label="最后更新">{{ format(form.updateTime) }}</el-descriptions-item><el-descriptions-item label="更新人">{{ form.updateBy || '-' }}</el-descriptions-item></el-descriptions>
        </el-collapse-item>
      </el-collapse>
    </el-form>

    <template #footer>
      <el-button :disabled="busy" @click="$refs.drawer.requestProgrammaticClose()">取消</el-button>
      <el-button v-if="persisted && !copyMode" v-hasPermi="['todo:dod-rule:toggle']" :loading="toggling" :type="form.status === '0' ? 'warning' : 'success'" @click="toggle">{{ form.status === '0' ? '停用规则' : '启用规则' }}</el-button>
      <el-button v-if="persisted && !copyMode" v-hasPermi="['todo:dod-rule:copy']" @click="$emit('copy', form)">复制</el-button>
      <el-button v-if="readonly" v-hasPermi="['todo:dod-rule:edit']" type="primary" @click="$emit('edit', form)">编辑</el-button>
      <el-button v-else-if="copyMode" v-hasPermi="['todo:dod-rule:copy']" type="primary" :loading="copying" @click="saveCopy">创建副本</el-button>
      <el-button v-else v-hasPermi="[persisted ? 'todo:dod-rule:edit' : 'todo:dod-rule:create']" type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </config-detail-drawer>
</template>

<script>
import ConfigDetailDrawer from '../shared/ConfigDetailDrawer'
import DodSimpleEditor from './DodSimpleEditor'
import { createDodRule, updateDodRule, createSimpleDodRule, updateSimpleDodRule, copyDodRule, toggleDodRule, getDodRuleReferenceCount, testDodRule } from '@/api/todo-config'
const { emptySimpleRule, fromLegacyJson } = require('./dod-recipe-codec')

const valueOf = (row, camel, snake) => row && (row[camel] !== undefined ? row[camel] : row[snake])
const emptyForm = () => ({ dodRuleId: null, ruleCode: '', ruleName: '', ruleType: '', businessType: 'LEAD', status: '0', serverStatus: '0', version: 0, referenceCount: 0, updateTime: '', updateBy: '' })
const emptyRaw = () => ({ requiredFieldsJson: '[]', requiredAttachmentsJson: '[]', conditionalRulesJson: '[]', validatorRefsJson: '[]', errorMessagesJson: '{}' })

export default {
  name: 'DodRuleDrawer',
  components: { ConfigDetailDrawer, DodSimpleEditor },
  dicts: ['law_todo_dod_rule_type', 'law_todo_rule_status'],
  props: { visible: Boolean, rule: { type: Object, default: null }, mode: { type: String, default: 'view' }, providedValidators: { type: Array, default: () => null }, businessType: { type: String, default: 'LEAD' } },
  data() { return { form: emptyForm(), simpleModel: emptySimpleRule(), raw: emptyRaw(), advancedMode: false, sections: ['basic', 'guide'], initialSnapshot: '', testResult: null, saving: false, copying: false, toggling: false, testing: false, businessTypes: ['LEAD', 'CUSTOMER', 'CONTRACT', 'CASE', 'MATTER'], rules: { ruleCode: [{ required: true, message: '请输入规则编码', trigger: 'blur' }, { pattern: /^[A-Z][A-Z0-9_]*$/, message: '仅支持大写字母、数字和下划线', trigger: 'blur' }], ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }], ruleType: [{ required: true, message: '请选择规则类型', trigger: 'change' }] } } },
  computed: {
    persisted() { return Boolean(this.form.dodRuleId) }, readonly() { return this.mode === 'view' }, copyMode() { return this.mode === 'copy' }, busy() { return this.saving || this.copying || this.toggling || this.testing },
    drawerTitle() { return ({ create: '新增完成条件', edit: '编辑完成条件', copy: '复制完成条件', view: '完成条件详情' })[this.mode] || '完成条件' },
    statusLabel() { const item = (this.dict.type.law_todo_rule_status || []).find(option => option.value === this.form.serverStatus); return item ? item.label : this.form.serverStatus },
    isDirty() { return !this.readonly && Boolean(this.initialSnapshot) && this.snapshot() !== this.initialSnapshot }
  },
  watch: { visible(open) { if (open) this.hydrate() }, rule() { if (this.visible) this.hydrate() } },
  methods: {
    hydrate() {
      const source = this.rule || {}; const form = emptyForm(); form.businessType = this.businessType || 'LEAD'
      ;['dodRuleId', 'ruleCode', 'ruleName', 'ruleType', 'businessType', 'status', 'version', 'referenceCount', 'updateTime', 'updateBy'].forEach(key => { const snake = key.replace(/[A-Z]/g, item => `_${item.toLowerCase()}`); const value = valueOf(source, key, snake); if (value !== undefined && value !== null && value !== '') form[key] = value })
      form.dodRuleId = Number(form.dodRuleId) || null; form.version = Number(form.version) || 0; form.referenceCount = Number(form.referenceCount) || 0; form.serverStatus = form.status
      if (this.copyMode) { form.dodRuleId = null; form.version = 0; form.referenceCount = 0; form.ruleCode = `${form.ruleCode || 'DOD'}_COPY` }
      this.form = form
      this.raw = { requiredFieldsJson: this.text(source, 'requiredFieldsJson', 'required_fields_json', '[]'), requiredAttachmentsJson: this.text(source, 'requiredAttachmentsJson', 'required_attachments_json', '[]'), conditionalRulesJson: this.text(source, 'conditionalRulesJson', 'conditional_rules_json', '[]'), validatorRefsJson: this.text(source, 'validatorRefsJson', 'validator_refs_json', '[]'), errorMessagesJson: this.text(source, 'errorMessagesJson', 'error_messages_json', '{}') }
      this.simpleModel = { ...fromLegacyJson(this.raw), businessType: form.businessType }
      this.advancedMode = this.requiresAdvancedCompatibility()
      this.testResult = null; this.initialSnapshot = this.snapshot(); this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    },
    text(source, camel, snake, fallback) { const value = valueOf(source, camel, snake); return value === undefined || value === null || value === '' ? fallback : (typeof value === 'string' ? value : JSON.stringify(value)) },
    requiresAdvancedCompatibility() { try { const messages = JSON.parse(this.raw.errorMessagesJson || '{}'); Object.values(this.raw).forEach(value => JSON.parse(value)); return messages && Object.keys(messages).length > 0 } catch (error) { return true } },
    businessTypeChanged(value) { this.form.businessType = value },
    reset() { this.form = emptyForm(); this.simpleModel = emptySimpleRule(); this.raw = emptyRaw(); this.advancedMode = false; this.initialSnapshot = ''; this.testResult = null; this.$emit('reset') },
    snapshot() { return JSON.stringify({ form: this.form, simpleModel: this.simpleModel, raw: this.raw, advancedMode: this.advancedMode }) },
    actionId(action) { return `dod-${action}-${Date.now()}-${Math.random().toString(16).slice(2)}` },
    validateRaw() { const requiredFields = JSON.parse(this.raw.requiredFieldsJson); const requiredAttachments = JSON.parse(this.raw.requiredAttachmentsJson); const conditionalRules = JSON.parse(this.raw.conditionalRulesJson); const validatorRefs = JSON.parse(this.raw.validatorRefsJson); const errorMessages = JSON.parse(this.raw.errorMessagesJson); if (![requiredFields, requiredAttachments, conditionalRules, validatorRefs].every(Array.isArray) || !errorMessages || Array.isArray(errorMessages) || typeof errorMessages !== 'object') throw new Error('高级设置中的字段、材料、条件和校验器必须是数组，错误文案必须是对象') },
    simplePayload() { const conditionalRules = (this.simpleModel.conditionalRules || []).map(item => ({ field: item.field, when: { ...(item.when || {}) } })); return { dodRuleId: this.persisted ? this.form.dodRuleId : null, ruleCode: this.form.ruleCode, ruleName: this.form.ruleName, ruleType: this.form.ruleType, businessType: this.form.businessType, requiredFields: this.simpleModel.requiredFields || [], requiredAttachments: this.simpleModel.requiredAttachments || [], validatorRefs: this.simpleModel.validatorRefs || [], conditionalRules, status: this.persisted && !this.copyMode ? this.form.serverStatus : this.form.status, actionId: this.actionId('simple-save'), expectedVersion: this.persisted ? Number(this.form.version) : 0 } },
    advancedPayload() { this.validateRaw(); return { dodRuleId: this.persisted ? this.form.dodRuleId : null, ruleCode: this.form.ruleCode, ruleName: this.form.ruleName, ruleType: this.form.ruleType, ...this.raw, status: this.persisted && !this.copyMode ? this.form.serverStatus : this.form.status, actionId: this.actionId('advanced-save'), expectedVersion: this.persisted ? Number(this.form.version) : 0 } },
    async save() { try { await this.$refs.form.validate(); if (!this.advancedMode && this.$refs.simpleEditor) this.$refs.simpleEditor.validate(); this.saving = true; const payload = this.advancedMode ? this.advancedPayload() : this.simplePayload(); const response = this.advancedMode ? await (payload.dodRuleId ? updateDodRule(payload.dodRuleId, payload) : createDodRule(payload)) : await (payload.dodRuleId ? updateSimpleDodRule(payload.dodRuleId, payload) : createSimpleDodRule(payload)); this.$modal.msgSuccess('完成条件已保存'); this.finishSaved(response.data) } catch (error) { this.showError(error) } finally { this.saving = false } },
    async saveCopy() { try { await this.$refs.form.validate(); const sourceId = valueOf(this.rule, 'dodRuleId', 'dod_rule_id'); if (!sourceId) throw new Error('原规则不存在，无法复制'); this.copying = true; const response = await copyDodRule(sourceId, { newRuleCode: this.form.ruleCode, actionId: this.actionId('copy') }); this.$modal.msgSuccess('完成条件副本已创建'); this.finishSaved(response.data) } catch (error) { this.showError(error) } finally { this.copying = false } },
    async toggle() { try { const targetStatus = this.form.serverStatus === '0' ? '1' : '0'; if (targetStatus === '1') { const response = await getDodRuleReferenceCount(this.form.dodRuleId); await this.$confirm(`该规则当前被 ${Number(response.data || 0)} 个模板引用。确认停用吗？`, '停用完成条件', { type: 'warning' }) } else await this.$confirm('确认启用此完成条件吗？', '启用完成条件', { type: 'warning' }); this.toggling = true; await toggleDodRule(this.form.dodRuleId, { status: targetStatus, actionId: this.actionId('toggle'), expectedVersion: Number(this.form.version) }); this.$modal.msgSuccess(targetStatus === '0' ? '规则已启用' : '规则已停用'); this.finishSaved() } catch (error) { if (error !== 'cancel') this.showError(error) } finally { this.toggling = false } },
    async runSimpleTest(value) { if (!this.persisted) return; try { this.testing = true; const response = await testDodRule(this.form.dodRuleId, value); this.testResult = response.data || { passed: false, missingFields: [], missingAttachments: [], validatorIssues: [] } } catch (error) { this.showError(error) } finally { this.testing = false } },
    finishSaved(savedId) { this.$emit('saved', savedId); this.$refs.drawer.closeAfterSave() }, format(value) { return value ? this.parseTime(value, '{y}-{m}-{d} {h}:{i}:{s}') : '-' }, showError(error) { if (error && error !== 'cancel' && error !== false) this.$modal.msgError((error && (error.msg || error.message)) || '操作失败，请稍后重试') }
  }
}
</script>

<style scoped lang="scss">
.full-width { width: 100%; }.editor-mode-bar { display: flex; justify-content: space-between; align-items: center; gap: 14px; margin-bottom: 8px; padding: 10px 12px; border-radius: 6px; background: #f8fafc; color: #64748b; font-size: 12px; }.advanced-editor > .el-alert { margin-bottom: 16px; }
</style>
