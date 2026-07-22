<template>
  <config-detail-drawer ref="drawer" :visible="value" title="执行只读模拟" size="86%" @update:visible="$emit('input',$event)" @reset="reset">
    <el-alert title="只读模拟，不创建真实待办" description="模拟只读取模板和业务对象，记录脱敏结果；不会修改业务状态、创建待办或发送通知。" type="info" :closable="false" show-icon />
    <el-row :gutter="20" class="simulation-layout">
      <el-col :span="10">
        <el-card shadow="never" class="input-card"><div slot="header"><strong>模拟输入</strong><span>按业务顺序选择，无需填写技术编码</span></div>
          <el-form ref="form" :model="form" :rules="rules" label-position="top">
            <el-row :gutter="12">
              <el-col :span="12"><el-form-item label="待办模板" prop="templateId"><el-select v-model="form.templateId" filterable remote :remote-method="searchTemplates" :loading="templateLoading" class="full" placeholder="搜索模板" @change="selectTemplate"><el-option v-for="item in templateOptions" :key="templateId(item)" :label="templateLabel(item)" :value="templateId(item)" /></el-select></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="模板版本" prop="versionId"><el-select v-model="form.versionId" class="full" placeholder="选择版本" @change="selectVersion"><el-option v-for="item in versions" :key="versionId(item)" :label="`v${field(item,'versionNo','version_no')} · ${versionStatus(item.status)}`" :value="versionId(item)" /></el-select></el-form-item></el-col>
            </el-row>
            <el-form-item label="触发事件" prop="eventKey"><el-select v-model="form.eventKey" filterable class="full" placeholder="选择事件" @change="selectEvent"><el-option v-for="item in compatibleEvents" :key="eventKey(item)" :label="`${item.eventName || item.eventType} · v${item.payloadVersion}`" :value="eventKey(item)" /></el-select></el-form-item>
            <el-form-item label="测试对象" prop="businessId"><business-object-picker ref="businessPicker" v-model="form.businessId" :business-type="form.businessType || 'LEAD'" @select="selectedBusinessObject = $event" /></el-form-item>
            <el-form-item label="模拟生效时间" prop="effectiveAt"><el-date-picker v-model="form.effectiveAt" type="datetime" value-format="yyyy-MM-ddTHH:mm:ss" class="full" /></el-form-item>
            <el-divider content-position="left">事件业务数据</el-divider>
            <schema-payload-form ref="payloadForm" v-model="payloadData" :schema="selectedPayloadSchemaJson" :sample-payload-json="samplePayloadJson" />
          </el-form>
        </el-card>
      </el-col>
      <el-col :span="14"><simulation-result :result="result" :loading="running" :business-object="selectedBusinessObject" /></el-col>
    </el-row>
    <template #footer><el-button @click="$refs.drawer.requestProgrammaticClose()">关闭</el-button><el-button v-hasPermi="['todo:simulation:simulate']" type="primary" :loading="running" @click="runSimulation">开始模拟</el-button></template>
  </config-detail-drawer>
</template>

<script>
import ConfigDetailDrawer from '../shared/ConfigDetailDrawer'
import SimulationResult from './SimulationResult'
import BusinessObjectPicker from './BusinessObjectPicker'
import SchemaPayloadForm from './SchemaPayloadForm'
import { getTodoTemplate, listTodoTemplates, listTemplateVersions, preflightTemplateDraft, simulateConfiguration } from '@/api/todo-config'

export default {
  name: 'SimulationDrawer',
  components: { ConfigDetailDrawer, SimulationResult, BusinessObjectPicker, SchemaPayloadForm },
  props: { value: Boolean, events: { type: Array, default: () => [] }, initialTemplate: { type: Object, default: null }, initialEvent: { type: Object, default: null } },
  data() { return { form: this.emptyForm(), versions: [], selectedDetail: null, selectedEventResource: null, selectedBusinessObject: null, payloadData: {}, templateOptions: [], templateLoading: false, templateQuery: { keyword: '', pageNum: 1, pageSize: 20 }, result: null, running: false, rules: { templateId: [{ required: true, message: '请选择待办模板', trigger: 'change' }], versionId: [{ required: true, message: '请选择模板版本', trigger: 'change' }], eventKey: [{ required: true, message: '请选择事件版本', trigger: 'change' }], businessId: [{ required: true, message: '请选择测试对象', trigger: 'change' }], effectiveAt: [{ required: true, message: '请选择生效时间', trigger: 'change' }] } } },
  computed: {
    compatibleEvents() { return this.events.filter(item => !this.form.businessType || item.businessObjectType === this.form.businessType) },
    selectedPayloadSchemaJson() { return (this.selectedEventResource && this.selectedEventResource.payloadSchemaJson) || '{"type":"object","properties":{}}' },
    samplePayloadJson() { return (this.selectedEventResource && this.selectedEventResource.samplePayloadJson) || '{}' }
  },
  watch: { value(opened) { if (opened) this.open() } },
  methods: {
    field(row, camel, snake) { return row && (row[camel] !== undefined ? row[camel] : row[snake]) },
    templateId(row) { return Number(this.field(row, 'templateId', 'template_id')) },
    templateLabel(row) { return `${this.field(row, 'templateName', 'template_name')} · ${this.field(row, 'templateCode', 'template_code')}` },
    versionId(row) { return Number(this.field(row, 'versionId', 'version_id')) },
    eventKey(row) { return `${row.eventType}::${row.payloadVersion}` },
    versionStatus(value) { return ({ DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '已归档' })[value] || value },
    emptyForm() { const date = new Date(); const pad = value => String(value).padStart(2, '0'); return { templateId: null, versionId: null, eventKey: '', eventType: '', payloadVersion: null, businessType: '', businessId: null, effectiveAt: `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}` } },
    async open() { this.reset(); await this.loadTemplates(); if (this.initialEvent) { this.form.eventKey = this.eventKey(this.initialEvent); await this.selectEvent(this.form.eventKey) } if (this.initialTemplate) { if (!this.templateOptions.some(item => this.templateId(item) === this.templateId(this.initialTemplate))) this.templateOptions.unshift(this.initialTemplate); this.form.templateId = this.templateId(this.initialTemplate); await this.selectTemplate(this.form.templateId) } },
    async searchTemplates(keyword) { this.templateQuery.keyword = keyword || ''; this.templateQuery.pageNum = 1; await this.loadTemplates() },
    async loadTemplates() { this.templateLoading = true; try { const response = await listTodoTemplates(this.templateQuery); this.templateOptions = response.rows || [] } catch (error) { this.templateOptions = []; this.$modal.msgError('加载模板失败') } finally { this.templateLoading = false } },
    async selectTemplate(id) { this.result = null; this.form.businessId = null; this.selectedBusinessObject = null; if (!id) return; try { const responses = await Promise.all([getTodoTemplate(id), listTemplateVersions(id)]); this.selectedDetail = responses[0].data || {}; this.versions = responses[1].data || []; this.form.businessType = this.selectedDetail.businessType || ''; const preferred = this.versions.find(item => item.status === 'DRAFT') || this.versions.find(item => item.status === 'PUBLISHED') || this.versions[0]; this.form.versionId = preferred ? this.versionId(preferred) : null; this.selectVersion(this.form.versionId) } catch (error) { this.$modal.msgError('加载模板详情失败') } },
    selectVersion(id) { const version = this.versions.find(item => this.versionId(item) === Number(id)); if (!version) return; let definition = {}; try { definition = JSON.parse(this.field(version, 'definitionJson', 'definition_json') || '{}') } catch (error) { definition = {} } const event = definition.event || {}; if (event.eventType) { this.form.eventType = event.eventType; this.form.payloadVersion = Number(event.payloadVersion || 1); this.form.eventKey = this.eventKey(this.form); this.selectedEventResource = this.events.find(item => this.eventKey(item) === this.form.eventKey) || null } },
    selectEvent(key) { const event = this.events.find(item => this.eventKey(item) === key); if (!event) return; this.selectedEventResource = event; this.form.eventType = event.eventType; this.form.payloadVersion = Number(event.payloadVersion); this.form.businessType = event.businessObjectType; this.form.businessId = null; this.selectedBusinessObject = null; this.payloadData = {} },
    async runSimulation() {
      const valid = await new Promise(resolve => this.$refs.form.validate(resolve)); if (!valid) return
      let payload; try { payload = this.$refs.payloadForm.validate() } catch (error) { this.$modal.msgError(error.message || '事件业务数据无效'); return }
      const active = this.events.some(item => item.status === 'ACTIVE' && this.eventKey(item) === this.form.eventKey && item.businessObjectType === this.form.businessType); if (!active) return this.$modal.msgError('请选择与业务类型匹配的有效事件版本')
      this.running = true
      try {
        const preflight = await preflightTemplateDraft(this.form.versionId); const report = (preflight.data || {}).report || {}; if (Array.isArray(report.errors) && report.errors.length) throw new Error('模板预检未通过，请先修复配置问题')
        const expectedDefinitionHash = report.definitionHash
        const response = await simulateConfiguration({ requestId: `config-simulation-${Date.now()}`, versionId: Number(this.form.versionId), eventType: this.form.eventType, payloadVersion: Number(this.form.payloadVersion), businessType: this.form.businessType, businessId: Number(this.form.businessId), payload, effectiveAt: this.form.effectiveAt, taskCompletions: [], expectedDefinitionHash })
        this.result = response.data || null; this.$emit('simulated', this.result)
      } catch (error) { this.result = null; this.$modal.msgError((error && (error.msg || error.message)) || '模拟执行失败') } finally { this.running = false }
    },
    reset() { this.form = this.emptyForm(); this.versions = []; this.selectedDetail = null; this.selectedEventResource = null; this.selectedBusinessObject = null; this.payloadData = {}; this.templateOptions = []; this.templateQuery = { keyword: '', pageNum: 1, pageSize: 20 }; this.result = null; this.running = false }
  }
}
</script>

<style scoped lang="scss">
@import '../styles/config-center.scss';.simulation-layout{margin-top:16px}.full{width:100%}.input-card [slot="header"] span{margin-left:10px;color:#64748b;font-size:12px}
</style>
