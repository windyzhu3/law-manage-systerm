<template>
  <config-detail-drawer ref="drawer" :visible="visible" :title="drawerTitle" size="720px" :dirty="isDirty" @update:visible="$emit('update:visible', $event)" @reset="reset">
    <el-form ref="form" :model="form" :rules="rules" label-width="120px" class="rule-drawer-form">
      <el-collapse v-model="sections">
        <el-collapse-item title="基本信息" name="basic">
          <el-row :gutter="16"><el-col :span="12"><el-form-item label="规则编码" prop="ruleCode"><el-input v-model.trim="form.ruleCode" :disabled="readonly || persisted" maxlength="64" /></el-form-item></el-col><el-col :span="12"><el-form-item label="规则名称" prop="ruleName"><el-input v-model.trim="form.ruleName" :disabled="readonly || copyMode" maxlength="128" /></el-form-item></el-col></el-row>
          <el-row :gutter="16"><el-col :span="12"><el-form-item label="SLA 类型" prop="slaType"><el-select v-model="form.slaType" :disabled="readonly || copyMode" class="full-width"><el-option v-for="item in dict.type.law_todo_sla_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="状态" prop="status"><el-input v-if="persisted && !copyMode" :value="statusLabel" disabled /><el-select v-else v-model="form.status" :disabled="readonly || copyMode" class="full-width"><el-option v-for="item in dict.type.law_todo_rule_status" :key="item.value" :label="item.label" :value="item.value" /></el-select><p v-if="persisted && !copyMode" class="status-note">已保存规则的状态仅可通过“启用/停用”操作修改。</p></el-form-item></el-col></el-row>
        </el-collapse-item>
        <el-collapse-item title="时限、日历与计时策略" name="duration">
          <el-row :gutter="16"><el-col :span="8"><el-form-item label="时长" prop="durationValue"><el-input-number v-model="form.durationValue" :disabled="readonly || copyMode" :min="1" :precision="0" class="full-width" /></el-form-item></el-col><el-col :span="8"><el-form-item label="时长单位" prop="durationUnit"><el-select v-model="form.durationUnit" :disabled="readonly || copyMode" class="full-width"><el-option v-for="item in dict.type.law_todo_sla_unit" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col><el-col :span="8"><el-form-item label="计时起点" prop="startStrategy"><el-select v-model="form.startStrategy" :disabled="readonly || copyMode" class="full-width"><el-option v-for="item in dict.type.law_todo_sla_start_strategy" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col></el-row>
          <el-form-item label="工作日历" prop="calendarCode"><el-select v-model="form.calendarCode" :disabled="readonly || copyMode" filterable class="full-width" placeholder="选择已管理的工作日历"><el-option v-for="calendar in calendars" :key="calendarCode(calendar)" :label="calendarName(calendar)" :value="calendarCode(calendar)" /></el-select></el-form-item>
        </el-collapse-item>
        <el-collapse-item title="固定超时阈值" name="thresholds">
          <el-alert title="后端固定使用 80% 提醒、100% 超时、150% 升级；此处仅展示受控规则，不支持任意阈值。" type="info" :closable="false" show-icon />
          <sla-timeline :result="calculation" :preview="!persisted" />
          <el-row :gutter="16"><el-col :span="8"><el-form-item label="提醒阈值"><el-input :value="`${form.softRemindPercent}%`" disabled /></el-form-item></el-col><el-col :span="8"><el-form-item label="超时阈值"><el-input :value="`${form.hardRemindPercent}%`" disabled /></el-form-item></el-col><el-col :span="8"><el-form-item label="升级阈值"><el-input :value="`${form.escalatePercent}%`" disabled /></el-form-item></el-col></el-row>
        </el-collapse-item>
        <el-collapse-item title="暂停、升级与自动动作策略" name="policies">
          <el-form-item label="暂停策略 JSON"><el-input v-model.trim="form.pausePolicyJson" :disabled="readonly || copyMode" type="textarea" :rows="3" placeholder='可选 JSON 对象或数组，例如 {"reason":"等待材料"}' /></el-form-item>
          <el-form-item label="升级策略 JSON"><el-input v-model.trim="form.escalationPolicyJson" :disabled="readonly || copyMode" type="textarea" :rows="3" placeholder='可选 JSON 对象或数组' /></el-form-item>
          <el-form-item label="超时处理策略"><el-select v-model="form.timeoutStrategy" :disabled="readonly || copyMode" clearable class="full-width" placeholder="选择受控超时策略" @change="applyTimeoutStrategy"><el-option v-for="item in dict.type.law_todo_timeout_strategy" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
          <el-form-item label="自动动作 JSON"><el-input v-model.trim="form.autoActionJson" :disabled="readonly || copyMode" type="textarea" :rows="3" placeholder='可选 JSON 对象或数组；超时处理策略保存于 timeoutStrategy 字段' /></el-form-item>
          <p v-if="policyError" class="validation-tip">{{ policyError }}</p>
        </el-collapse-item>
        <el-collapse-item title="绑定与引用" name="binding"><el-descriptions :column="2" border size="small"><el-descriptions-item label="模板引用数">{{ form.referenceCount || 0 }}</el-descriptions-item><el-descriptions-item label="版本">{{ form.version }}</el-descriptions-item><el-descriptions-item label="最后更新">{{ format(form.updateTime) }}</el-descriptions-item><el-descriptions-item label="更新人">{{ form.updateBy || '-' }}</el-descriptions-item></el-descriptions></el-collapse-item>
        <el-collapse-item title="计算示例" name="test"><el-form-item label="计时起点"><el-date-picker v-model="testCreatedAt" type="datetime" value-format="yyyy-MM-ddTHH:mm:ss" format="yyyy-MM-dd HH:mm:ss" :disabled="!persisted || testing" /></el-form-item><el-button v-if="persisted" v-hasPermi="['todo:sla-rule:list']" :loading="testing" @click="runTest">按工作日历测算</el-button><p v-else class="drawer-note">请先保存规则，再按服务端工作日历进行权威测算。</p><sla-timeline v-if="calculation" :result="calculation" /></el-collapse-item>
      </el-collapse>
    </el-form>
    <template #footer>
      <el-button :disabled="saving || copying || toggling || testing" @click="$refs.drawer.requestProgrammaticClose()">取消</el-button>
      <el-button v-if="persisted && !copyMode" v-hasPermi="['todo:sla-rule:toggle']" :loading="toggling" :type="form.status === '0' ? 'warning' : 'success'" @click="toggle">{{ form.status === '0' ? '停用规则' : '启用规则' }}</el-button>
      <el-button v-if="persisted && !copyMode" v-hasPermi="['todo:sla-rule:copy']" :disabled="saving || toggling || testing" @click="$emit('copy', form)">复制</el-button>
      <el-button v-if="readonly" v-hasPermi="['todo:sla-rule:edit']" type="primary" @click="$emit('edit', form)">编辑</el-button>
      <el-button v-else-if="copyMode" v-hasPermi="['todo:sla-rule:copy']" type="primary" :loading="copying" @click="saveCopy">创建副本</el-button>
      <el-button v-else v-hasPermi="[persisted ? 'todo:sla-rule:edit' : 'todo:sla-rule:create']" type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </config-detail-drawer>
</template>

<script>
import ConfigDetailDrawer from '../shared/ConfigDetailDrawer'
import SlaTimeline from './SlaTimeline'
import { listWorkCalendars } from '@/api/todo-definition'
import { createSlaRule, updateSlaRule, copySlaRule, toggleSlaRule, testSlaRule } from '@/api/todo-config'

const emptyForm = () => ({ slaRuleId: null, ruleCode: '', ruleName: '', slaType: '', durationValue: 60, durationUnit: '', calendarCode: '', startStrategy: '', softRemindPercent: 80, hardRemindPercent: 100, escalatePercent: 150, pausePolicyJson: '', escalationPolicyJson: '', autoActionJson: '', timeoutStrategy: '', status: '0', serverStatus: '0', version: 0, referenceCount: 0, updateTime: '', updateBy: '' })
const valueOf = (row, camel, snake) => row && (row[camel] !== undefined ? row[camel] : row[snake])
export default {
  name: 'SlaRuleDrawer',
  components: { ConfigDetailDrawer, SlaTimeline },
  dicts: ['law_todo_sla_type', 'law_todo_sla_unit', 'law_todo_sla_start_strategy', 'law_todo_timeout_strategy', 'law_todo_rule_status'],
  props: { visible: Boolean, rule: { type: Object, default: null }, mode: { type: String, default: 'view' } },
  data() { return { form: emptyForm(), calendars: [], sections: ['basic', 'duration', 'thresholds'], initialSnapshot: '', calculation: null, testCreatedAt: '', saving: false, copying: false, toggling: false, testing: false, rules: { ruleCode: [{ required: true, message: '请输入规则编码', trigger: 'blur' }], ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }], slaType: [{ required: true, message: '请选择 SLA 类型', trigger: 'change' }], durationValue: [{ required: true, message: '请输入时长', trigger: 'change' }], durationUnit: [{ required: true, message: '请选择时长单位', trigger: 'change' }], calendarCode: [{ required: true, message: '请选择工作日历', trigger: 'change' }], startStrategy: [{ required: true, message: '请选择计时起点', trigger: 'change' }] } } },
  computed: { persisted() { return Boolean(this.form.slaRuleId) }, readonly() { return this.mode === 'view' }, copyMode() { return this.mode === 'copy' }, drawerTitle() { return ({ create: '新增 SLA 规则', edit: '编辑 SLA 规则', copy: '复制 SLA 规则', view: 'SLA 规则详情' })[this.mode] || 'SLA 规则' }, statusLabel() { const item = (this.dict.type.law_todo_rule_status || []).find(option => option.value === this.form.serverStatus); return item ? item.label : this.form.serverStatus }, isDirty() { return !this.readonly && this.initialSnapshot && this.snapshot() !== this.initialSnapshot }, policyError() { return ['pausePolicyJson', 'escalationPolicyJson', 'autoActionJson'].some(key => !this.validOptionalJson(this.form[key])) ? '策略 JSON 必须为对象或数组；请修正后再保存。' : '' } },
  watch: { visible(open) { if (open) this.hydrate() }, rule() { if (this.visible) this.hydrate() } },
  created() { this.loadCalendars() },
  methods: {
    loadCalendars() { return listWorkCalendars().then(response => { this.calendars = response.data || [] }).catch(() => { this.calendars = [] }) },
    calendarCode(calendar) { return valueOf(calendar, 'calendarCode', 'calendar_code') },
    calendarName(calendar) { return valueOf(calendar, 'calendarName', 'calendar_name') || this.calendarCode(calendar) },
    hydrate() { const source = this.rule || {}; const form = emptyForm(); Object.keys(form).forEach(key => { const snake = key.replace(/[A-Z]/g, item => `_${item.toLowerCase()}`); const value = valueOf(source, key, snake); if (value !== undefined && value !== null) form[key] = value }); try { const action = form.autoActionJson ? JSON.parse(form.autoActionJson) : null; if (action && !Array.isArray(action) && action.timeoutStrategy) form.timeoutStrategy = action.timeoutStrategy } catch (error) { /* preserve malformed policy text for explicit correction */ } form.slaRuleId = Number(form.slaRuleId) || null; form.version = Number(form.version) || 0; form.referenceCount = Number(form.referenceCount) || 0; form.durationValue = Number(form.durationValue) || 1; form.softRemindPercent = 80; form.hardRemindPercent = 100; form.escalatePercent = 150; form.serverStatus = form.status; if (this.copyMode) { form.slaRuleId = null; form.version = 0; form.referenceCount = 0; form.ruleCode = `${form.ruleCode || 'SLA'}_COPY`; } this.form = form; this.calculation = null; this.testCreatedAt = ''; this.initialSnapshot = this.snapshot(); this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate()) },
    reset() { this.form = emptyForm(); this.calculation = null; this.initialSnapshot = ''; this.$emit('reset') },
    snapshot() { return JSON.stringify({ ...this.form, pausePolicyJson: this.form.pausePolicyJson || '', escalationPolicyJson: this.form.escalationPolicyJson || '', autoActionJson: this.form.autoActionJson || '' }) },
    actionId(action) { return `sla-${action}-${Date.now()}-${Math.random().toString(16).slice(2)}` },
    applyTimeoutStrategy(value) { let action = {}; try { const parsed = this.form.autoActionJson ? JSON.parse(this.form.autoActionJson) : {}; if (parsed && !Array.isArray(parsed) && typeof parsed === 'object') action = parsed } catch (error) { this.$modal.msgError('请先修复自动动作 JSON，再选择超时处理策略'); return } if (value) action.timeoutStrategy = value; else delete action.timeoutStrategy; this.form.autoActionJson = JSON.stringify(action) },
    validOptionalJson(value) { if (!value || !String(value).trim()) return true; try { const parsed = JSON.parse(value); return Array.isArray(parsed) || (parsed && typeof parsed === 'object') } catch (error) { return false } },
    optionalJson(value) { return value && String(value).trim() ? String(value).trim() : null },
    payload() { return { slaRuleId: this.persisted && !this.copyMode ? this.form.slaRuleId : null, ruleCode: this.form.ruleCode, ruleName: this.form.ruleName, slaType: this.form.slaType, durationValue: Number(this.form.durationValue), durationUnit: this.form.durationUnit, calendarCode: this.form.calendarCode, startStrategy: this.form.startStrategy, softRemindPercent: 80, hardRemindPercent: 100, escalatePercent: 150, pausePolicyJson: this.optionalJson(this.form.pausePolicyJson), escalationPolicyJson: this.optionalJson(this.form.escalationPolicyJson), autoActionJson: this.optionalJson(this.form.autoActionJson), status: this.persisted && !this.copyMode ? this.form.serverStatus : this.form.status, actionId: this.actionId('save'), expectedVersion: this.persisted && !this.copyMode ? Number(this.form.version) : 0 } },
    async validate() { await this.$refs.form.validate(); if (this.policyError) throw new Error(this.policyError) },
    async save() { try { await this.validate(); this.saving = true; const payload = this.payload(); const response = await (payload.slaRuleId ? updateSlaRule(payload.slaRuleId, payload) : createSlaRule(payload)); this.$modal.msgSuccess('SLA 规则已保存'); this.finishSaved(response.data) } catch (error) { this.showError(error) } finally { this.saving = false } },
    async saveCopy() { try { await this.validate(); if (!this.rule || !valueOf(this.rule, 'slaRuleId', 'sla_rule_id')) throw new Error('原规则不存在，无法复制'); this.copying = true; const response = await copySlaRule(valueOf(this.rule, 'slaRuleId', 'sla_rule_id'), { newRuleCode: this.form.ruleCode, actionId: this.actionId('copy') }); this.$modal.msgSuccess('SLA 规则副本已创建'); this.finishSaved(response.data) } catch (error) { this.showError(error) } finally { this.copying = false } },
    async toggle() { try { const targetStatus = this.form.serverStatus === '0' ? '1' : '0'; const label = targetStatus === '0' ? '启用' : '停用'; await this.$confirm(`确认${label}此 SLA 规则吗？`, '确认操作', { type: 'warning' }); this.toggling = true; await toggleSlaRule(this.form.slaRuleId, { status: targetStatus, actionId: this.actionId('toggle'), expectedVersion: Number(this.form.version) }); this.$modal.msgSuccess(`规则已${label}`); this.finishSaved() } catch (error) { if (error !== 'cancel') this.showError(error) } finally { this.toggling = false } },
    async runTest() { try { if (!this.testCreatedAt) throw new Error('请选择计时起点'); this.testing = true; const response = await testSlaRule(this.form.slaRuleId, { createdAt: this.testCreatedAt }); this.calculation = response.data || null; if (!this.calculation) throw new Error('未返回测算结果') } catch (error) { this.showError(error) } finally { this.testing = false } },
    finishSaved(savedId) { this.$emit('saved', savedId); this.$refs.drawer.closeAfterSave() },
    format(value) { return value ? this.parseTime(value, '{y}-{m}-{d} {h}:{i}:{s}') : '-' },
    showError(error) { if (error && error !== 'cancel') this.$modal.msgError((error && (error.msg || error.message)) || '操作失败，请稍后重试') }
  }
}
</script>

<style scoped lang="scss">
.full-width { width: 100%; }.validation-tip { margin: -8px 0 0 120px; color: #dc2626; font-size: 12px; }.drawer-note,.status-note { margin: 8px 0; color: #64748b; font-size: 12px; }::v-deep .el-collapse-item__content { padding-bottom: 12px; }
</style>
