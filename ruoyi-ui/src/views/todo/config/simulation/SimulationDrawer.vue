<template>
  <config-detail-drawer ref="drawer" :visible="value" title="执行只读模拟" size="78%" @update:visible="$emit('input',$event)" @reset="reset">
    <el-alert title="只读模拟，不创建真实待办" description="仅记录脱敏后的输入摘要、结果与耗时；不会写入业务事实、创建待办或推进流程。" type="info" :closable="false" show-icon />
    <el-row :gutter="20" class="simulation-layout">
      <el-col :span="9">
        <el-card shadow="never"><div slot="header">模拟输入</div>
          <el-form ref="form" :model="form" :rules="rules" label-width="104px">
            <el-form-item label="待办模板" prop="templateId"><el-select v-model="form.templateId" filterable class="full" @change="selectTemplate"><el-option v-for="item in templates" :key="templateId(item)" :label="`${item.templateName} · ${item.templateCode}`" :value="templateId(item)" /></el-select></el-form-item>
            <el-form-item label="模板版本" prop="versionId"><el-select v-model="form.versionId" class="full" @change="selectVersion"><el-option v-for="item in versions" :key="versionId(item)" :label="`v${field(item,'versionNo','version_no')} · ${item.status}`" :value="versionId(item)" /></el-select></el-form-item>
            <el-form-item label="事件版本" prop="eventKey"><el-select v-model="form.eventKey" filterable class="full" @change="selectEvent"><el-option v-for="item in compatibleEvents" :key="eventKey(item)" :label="`${item.eventType} · v${item.payloadVersion}`" :value="eventKey(item)" /></el-select></el-form-item>
            <el-form-item label="业务类型" prop="businessType"><el-input v-model="form.businessType" readonly /></el-form-item>
            <el-form-item label="测试对象" prop="businessId"><el-input-number v-model="form.businessId" :min="1" :precision="0" class="full" /></el-form-item>
            <el-form-item label="生效时间" prop="effectiveAt"><el-date-picker v-model="form.effectiveAt" type="datetime" value-format="yyyy-MM-ddTHH:mm:ss" class="full" /></el-form-item>
            <el-form-item label="Payload">
              <div v-for="(row,index) in payloadRows" :key="index" class="payload-row">
                <el-input v-model="row.key" placeholder="字段键" />
                <el-select v-model="row.type"><el-option label="文本" value="STRING" /><el-option label="数字" value="NUMBER" /><el-option label="布尔" value="BOOLEAN" /><el-option label="JSON" value="JSON" /></el-select>
                <el-input v-model="row.value" placeholder="字段值" />
                <el-button type="text" icon="el-icon-delete" @click="payloadRows.splice(index,1)" />
              </div>
              <el-button type="text" icon="el-icon-plus" @click="payloadRows.push(emptyPayloadRow())">添加字段</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
      <el-col :span="15"><simulation-result :result="result" :loading="running" /></el-col>
    </el-row>
    <template #footer><el-button @click="$refs.drawer.requestProgrammaticClose()">关闭</el-button><el-button v-hasPermi="['todo:simulation:simulate']" type="primary" :loading="running" @click="runSimulation">开始模拟</el-button></template>
  </config-detail-drawer>
</template>

<script>
import ConfigDetailDrawer from '../shared/ConfigDetailDrawer'
import SimulationResult from './SimulationResult'
import { getTodoTemplate, listTemplateVersions, preflightTemplateDraft, simulateConfiguration } from '@/api/todo-config'
export default {
  name: 'SimulationDrawer', components: { ConfigDetailDrawer, SimulationResult },
  props: { value: Boolean, templates: { type: Array, default: () => [] }, events: { type: Array, default: () => [] }, initialTemplate: { type: Object, default: null }, initialEvent: { type: Object, default: null } },
  data() { return { form: this.emptyForm(), versions: [], selectedDetail: null, payloadRows: [this.emptyPayloadRow()], result: null, running: false, rules: { templateId: [{ required: true, message: '请选择待办模板', trigger: 'change' }], versionId: [{ required: true, message: '请选择模板版本', trigger: 'change' }], eventKey: [{ required: true, message: '请选择事件版本', trigger: 'change' }], businessType: [{ required: true, message: '请选择业务类型', trigger: 'change' }], businessId: [{ required: true, message: '请选择测试业务对象', trigger: 'change' }], effectiveAt: [{ required: true, message: '请选择生效时间', trigger: 'change' }] } } },
  computed: { compatibleEvents() { return this.events.filter(item => !this.form.businessType || item.businessObjectType === this.form.businessType) } },
  watch: { value(opened) { if (opened) this.open() } },
  methods: {
    field(row, camel, snake) { return row && (row[camel] !== undefined ? row[camel] : row[snake]) }, templateId(row) { return Number(this.field(row,'templateId','template_id')) }, versionId(row) { return Number(this.field(row,'versionId','version_id')) }, eventKey(row) { return `${row.eventType}::${row.payloadVersion}` },
    emptyPayloadRow() { return { key: '', type: 'STRING', value: '' } }, emptyForm() { const date = new Date(); const pad = value => String(value).padStart(2,'0'); return { templateId: null, versionId: null, eventKey: '', eventType: '', payloadVersion: null, businessType: '', businessId: 1, effectiveAt: `${date.getFullYear()}-${pad(date.getMonth()+1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}` } },
    async open() { this.reset(); if (this.initialEvent) { this.form.eventKey = this.eventKey(this.initialEvent); this.selectEvent(this.form.eventKey) } if (this.initialTemplate) { this.form.templateId = this.templateId(this.initialTemplate); await this.selectTemplate(this.form.templateId) } },
    async selectTemplate(id) { this.result = null; if (!id) return; const responses = await Promise.all([getTodoTemplate(id), listTemplateVersions(id)]); this.selectedDetail = responses[0].data || {}; this.versions = responses[1].data || []; this.form.businessType = this.selectedDetail.businessType || ''; const preferred = this.versions.find(item => item.status === 'DRAFT') || this.versions.find(item => item.status === 'PUBLISHED') || this.versions[0]; this.form.versionId = preferred ? this.versionId(preferred) : null; this.selectVersion(this.form.versionId) },
    selectVersion(id) { const version = this.versions.find(item => this.versionId(item) === Number(id)); if (!version) return; let definition = {}; try { definition = JSON.parse(this.field(version,'definitionJson','definition_json') || '{}') } catch (_) { definition = {} } const event = definition.event || {}; this.form.eventType = event.eventType || ''; this.form.payloadVersion = Number(event.payloadVersion || 1); this.form.eventKey = this.eventKey(this.form); const catalog = this.events.find(item => this.eventKey(item) === this.form.eventKey); if (catalog) this.applyPayloadSchema(catalog.payloadSchemaJson) },
    selectEvent(key) { const event = this.events.find(item => this.eventKey(item) === key); if (!event) return; this.form.eventType = event.eventType; this.form.payloadVersion = Number(event.payloadVersion); this.form.businessType = event.businessObjectType; this.applyPayloadSchema(event.payloadSchemaJson) },
    applyPayloadSchema(raw) { let schema = {}; try { schema = typeof raw === 'string' ? JSON.parse(raw) : (raw || {}) } catch (_) { schema = {} } const properties = schema.properties || {}; const rows = Object.keys(properties).map(key => ({ key, type: this.schemaType(properties[key] && properties[key].type), value: '' })); this.payloadRows = rows.length ? rows : [this.emptyPayloadRow()] },
    schemaType(type) { return type === 'number' || type === 'integer' ? 'NUMBER' : type === 'boolean' ? 'BOOLEAN' : type === 'object' || type === 'array' ? 'JSON' : 'STRING' },
    payload() { const result = {}; this.payloadRows.filter(row => row.key && row.key.trim()).forEach(row => { const key = row.key.trim(); if (row.type === 'NUMBER') { const number = Number(row.value); if (!Number.isFinite(number)) throw new Error(`${key} 必须是数字`); result[key] = number } else if (row.type === 'BOOLEAN') { if (!['true','false'].includes(String(row.value).toLowerCase())) throw new Error(`${key} 必须是 true 或 false`); result[key] = String(row.value).toLowerCase() === 'true' } else if (row.type === 'JSON') { result[key] = JSON.parse(row.value) } else result[key] = row.value }); if (!Object.keys(result).length) throw new Error('Payload 至少需要一个字段'); return result },
    async runSimulation() { const valid = await new Promise(resolve => this.$refs.form.validate(resolve)); if (!valid) return; let payload; try { payload = this.payload() } catch (error) { this.$modal.msgError(error.message || 'Payload 无效'); return } const active = this.events.some(item => item.status === 'ACTIVE' && this.eventKey(item) === this.form.eventKey && item.businessObjectType === this.form.businessType); if (!active) { this.$modal.msgError('请选择与业务类型匹配的有效事件版本'); return } this.running = true; try { const preflight = await preflightTemplateDraft(this.form.versionId); const report = (preflight.data || {}).report || {}; if (Array.isArray(report.errors) && report.errors.length) throw new Error('模板预检未通过，无法模拟'); const response = await simulateConfiguration({ requestId: `config-simulation-${Date.now()}`, versionId: Number(this.form.versionId), eventType: this.form.eventType, businessType: this.form.businessType, businessId: Number(this.form.businessId), payload, effectiveAt: this.form.effectiveAt, taskCompletions: [], expectedDefinitionHash: report.definitionHash }); this.result = response.data || null; this.$emit('simulated', this.result) } catch (error) { this.result = null; this.$modal.msgError((error && (error.msg || error.message)) || '模拟执行失败') } finally { this.running = false } },
    reset() { this.form = this.emptyForm(); this.versions = []; this.selectedDetail = null; this.payloadRows = [this.emptyPayloadRow()]; this.result = null; this.running = false }
  }
}
</script>
<style scoped lang="scss">@import '../styles/config-center.scss';.simulation-layout{margin-top:16px}.full{width:100%}.payload-row{display:grid;grid-template-columns:1fr 92px 1.2fr 28px;gap:6px;margin-bottom:8px}</style>
