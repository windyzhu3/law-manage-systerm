<template>
  <config-detail-drawer
    ref="drawer"
    :visible="visible"
    :title="drawerTitle"
    size="720px"
    :dirty="isDirty"
    @update:visible="$emit('update:visible', $event)"
    @reset="reset"
  >
    <el-form ref="form" :model="form" :rules="rules" label-width="118px" class="trigger-rule-form">
      <el-collapse v-model="sections">
        <el-collapse-item title="事件来源" name="event">
          <el-form-item label="触发方式">
            <el-input :value="triggerModeLabel" disabled />
            <p class="form-help">当前运行时仅支持事件驱动，来源模板为“—（事件触发）”。</p>
          </el-form-item>
          <el-form-item label="事件与版本" prop="eventKey">
            <el-select v-model="form.eventKey" filterable :disabled="readonly" class="full-width" @change="eventChanged">
              <el-option v-for="item in selectableEventCatalog" :key="catalogKey(item)" :label="eventLabel(item)" :value="catalogKey(item)" :disabled="!eventActive(item)" />
            </el-select>
            <el-alert v-if="selectedEvent && !selectedEventActive" title="当前规则引用的是已停用历史事件，仅供查看；保存前请选择启用的事件版本。" type="warning" :closable="false" show-icon />
          </el-form-item>
          <el-row :gutter="16">
            <el-col :span="12"><el-form-item label="Payload 版本"><el-input :value="form.payloadVersion" disabled /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="业务对象"><el-input :value="form.businessType" disabled /></el-form-item></el-col>
          </el-row>
        </el-collapse-item>

        <el-collapse-item title="触发条件" name="condition">
          <trigger-condition-builder
            ref="conditionBuilder"
            v-model="form.conditionJson"
            :payload-schema-json="selectedPayloadSchemaJson"
            :readonly="readonly"
            @validity-change="conditionValid = $event"
            @dirty-change="conditionDraftDirty = $event"
          />
        </el-collapse-item>

        <el-collapse-item title="目标待办模板" name="target">
          <el-form-item label="目标动作"><el-input value="创建待办" disabled /></el-form-item>
          <el-form-item label="待办模板" prop="templateId">
            <el-select v-model="form.templateId" filterable :disabled="readonly" class="full-width" @change="templateChanged">
              <el-option v-for="item in templateCatalog" :key="templateId(item)" :label="templateLabel(item)" :value="templateId(item)" />
            </el-select>
          </el-form-item>
          <el-form-item label="已发布版本" prop="templateVersionId">
            <el-select v-model="form.templateVersionId" :disabled="readonly || !form.templateId" class="full-width" placeholder="请选择已发布版本">
              <el-option v-for="item in publishedVersions" :key="versionId(item)" :label="versionLabel(item)" :value="versionId(item)" />
            </el-select>
            <el-alert v-if="form.templateId && !versionLoading && !publishedVersions.length" title="该模板没有已发布版本，不能保存触发规则" type="warning" :closable="false" show-icon />
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="状态与排序" name="governance">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="规则状态" prop="enabled">
                <el-input v-if="persisted" :value="enabledLabel(form.serverEnabled)" disabled />
                <el-select v-else v-model="form.enabled" :disabled="readonly" class="full-width">
                  <el-option v-for="item in enabledOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
                <p v-if="persisted" class="form-help">已保存规则只能通过列表中的启用/停用操作变更状态。</p>
              </el-form-item>
            </el-col>
            <el-col :span="12"><el-form-item label="排序号"><el-input :value="form.sortOrder" disabled /><p class="form-help">排序请在列表中使用上移、下移和保存排序操作。</p></el-form-item></el-col>
          </el-row>
          <el-descriptions v-if="persisted" :column="2" border size="small">
            <el-descriptions-item label="规则 ID">{{ form.triggerRuleId }}</el-descriptions-item>
            <el-descriptions-item label="乐观锁版本">{{ form.version }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ format(form.updateTime) }}</el-descriptions-item>
            <el-descriptions-item label="目标版本 ID">{{ form.templateVersionId }}</el-descriptions-item>
          </el-descriptions>
        </el-collapse-item>
      </el-collapse>
    </el-form>

    <template #footer>
      <el-button :disabled="saving" @click="$refs.drawer.requestProgrammaticClose()">取消</el-button>
      <el-button v-if="readonly" v-hasPermi="['todo:trigger:edit']" type="primary" @click="$emit('edit', form)">编辑</el-button>
      <el-button
        v-else
        v-hasPermi="[persisted ? 'todo:trigger:edit' : 'todo:trigger:create']"
        type="primary"
        :loading="saving"
        :disabled="!conditionValid || versionLoading || !publishedVersions.length"
        @click="save"
      >保存</el-button>
    </template>
  </config-detail-drawer>
</template>

<script>
import ConfigDetailDrawer from '../shared/ConfigDetailDrawer'
import TriggerConditionBuilder from './TriggerConditionBuilder'
import { createTriggerRule, updateTriggerRule, listTriggerEventCatalog, listTriggerTemplateCatalog, listTriggerTemplateVersions } from '@/api/todo-config'

const valueOf = (row, camel, snake) => row && (row[camel] !== undefined ? row[camel] : row[snake])
const emptyForm = () => ({ triggerRuleId: null, eventKey: '', eventType: '', payloadVersion: 1, businessType: '', templateId: null, templateVersionId: null, enabled: 'Y', serverEnabled: 'Y', conditionJson: '', sortOrder: 0, version: 0, updateTime: '' })

export default {
  name: 'TriggerRuleDrawer',
  components: { ConfigDetailDrawer, TriggerConditionBuilder },
  dicts: ['law_todo_trigger_mode', 'law_todo_rule_status'],
  props: { visible: Boolean, rule: { type: Object, default: null }, mode: { type: String, default: 'view' } },
  data() {
    return {
      form: emptyForm(),
      eventCatalog: [],
      templateCatalog: [],
      versions: [],
      sections: ['event', 'condition', 'target'],
      initialSnapshot: '',
      saving: false,
      versionLoading: false,
      conditionValid: true,
      conditionDraftDirty: false,
      rules: {
        eventKey: [{ required: true, message: '请选择事件与 Payload 版本', trigger: 'change' }],
        templateId: [{ required: true, message: '请选择目标待办模板', trigger: 'change' }],
        templateVersionId: [{ required: true, message: '请选择已发布的目标模板版本', trigger: 'change' }],
        enabled: [{ required: true, message: '请选择规则状态', trigger: 'change' }]
      }
    }
  },
  computed: {
    persisted() { return Boolean(this.form.triggerRuleId) },
    readonly() { return this.mode === 'view' },
    drawerTitle() { return ({ create: '新增触发规则', edit: '编辑触发规则', view: '触发规则详情' })[this.mode] || '触发规则详情' },
    activeEventCatalog() { return this.eventCatalog.filter(item => valueOf(item, 'status', 'status') === 'ACTIVE') },
    selectableEventCatalog() { const active = this.activeEventCatalog.slice(); const selected = this.eventCatalog.find(item => this.catalogKey(item) === this.form.eventKey); if (selected && !active.some(item => this.catalogKey(item) === this.form.eventKey)) active.push(selected); return active },
    selectedEvent() { return this.eventCatalog.find(item => this.catalogKey(item) === this.form.eventKey) || null },
    selectedEventActive() { return this.eventActive(this.selectedEvent) },
    selectedPayloadSchemaJson() { return valueOf(this.selectedEvent, 'payloadSchemaJson', 'payload_schema_json') || '' },
    publishedVersions() { return this.versions.filter(item => valueOf(item, 'status', 'status') === 'PUBLISHED') },
    enabledOptions() {
      return (this.dict.type.law_todo_rule_status || []).map(item => ({ label: item.label, value: item.value === '0' ? 'Y' : 'N' }))
    },
    triggerModeLabel() {
      const item = (this.dict.type.law_todo_trigger_mode || []).find(option => option.value === 'EVENT')
      return item ? item.label : '事件触发'
    },
    isDirty() { return !this.readonly && (this.conditionDraftDirty || (Boolean(this.initialSnapshot) && this.snapshot() !== this.initialSnapshot)) }
  },
  watch: {
    visible(open) { if (open) this.hydrate() },
    rule() { if (this.visible) this.hydrate() }
  },
  created() { this.loadCatalogs() },
  methods: {
    async loadCatalogs() {
      try {
        const [events, templates] = await Promise.all([listTriggerEventCatalog(), listTriggerTemplateCatalog()])
        this.eventCatalog = events.data || []
        this.templateCatalog = templates.data || []
        if (this.visible) this.hydrate()
      } catch (error) {
        this.eventCatalog = []
        this.templateCatalog = []
        this.showError(error, '加载事件或模板目录失败')
      }
    },
    async hydrate() {
      const source = this.rule || {}
      const form = emptyForm()
      const keys = ['triggerRuleId', 'eventType', 'payloadVersion', 'businessType', 'templateId', 'templateVersionId', 'enabled', 'conditionJson', 'sortOrder', 'version', 'updateTime']
      keys.forEach(key => {
        const snake = key.replace(/[A-Z]/g, letter => `_${letter.toLowerCase()}`)
        const value = valueOf(source, key, snake)
        if (value !== undefined && value !== null) form[key] = value
      })
      form.triggerRuleId = Number(form.triggerRuleId) || null
      form.templateId = Number(form.templateId) || null
      form.templateVersionId = Number(form.templateVersionId) || null
      form.payloadVersion = Number(form.payloadVersion) || 1
      form.sortOrder = Number(form.sortOrder) || 0
      form.version = Number(form.version) || 0
      form.serverEnabled = form.enabled
      form.eventKey = form.eventType ? `${form.eventType}@@${form.payloadVersion}` : ''
      this.form = form
      this.conditionValid = true
      this.conditionDraftDirty = false
      this.versions = []
      if (form.templateId) await this.loadPublishedVersions(form.templateId, true)
      this.initialSnapshot = this.snapshot()
      this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate())
    },
    reset() { this.form = emptyForm(); this.versions = []; this.initialSnapshot = ''; this.conditionValid = true; this.conditionDraftDirty = false; this.$emit('reset') },
    snapshot() { return JSON.stringify(this.form) },
    catalogKey(item) { return `${valueOf(item, 'eventType', 'event_type')}@@${Number(valueOf(item, 'payloadVersion', 'payload_version') || 1)}` },
    eventActive(item) { return Boolean(item) && valueOf(item, 'status', 'status') === 'ACTIVE' },
    eventLabel(item) { return `${valueOf(item, 'eventType', 'event_type')} · v${Number(valueOf(item, 'payloadVersion', 'payload_version') || 1)}${this.eventActive(item) ? '' : '（历史停用）'}` },
    eventChanged(key) {
      const item = this.eventCatalog.find(entry => this.catalogKey(entry) === key)
      this.form.eventType = valueOf(item, 'eventType', 'event_type') || ''
      this.form.payloadVersion = Number(valueOf(item, 'payloadVersion', 'payload_version') || 1)
      this.form.businessType = this.catalogBusinessType(item)
      this.form.conditionJson = ''
    },
    templateId(item) { return Number(valueOf(item, 'templateId', 'template_id')) },
    catalogBusinessType(item) { return valueOf(item, 'businessObjectType', 'business_object_type') || valueOf(item, 'businessType', 'business_type') || '' },
    templateLabel(item) { const code = valueOf(item, 'templateCode', 'template_code'); const name = valueOf(item, 'templateName', 'template_name'); return code ? `${name || code} (${code})` : (name || '-') },
    versionId(item) { return Number(valueOf(item, 'versionId', 'version_id')) },
    versionLabel(item) { const versionNo = valueOf(item, 'versionNo', 'version_no'); return `v${versionNo || '-'} · ID ${this.versionId(item)}` },
    templateChanged(id) { this.form.templateVersionId = null; this.loadPublishedVersions(id, false) },
    async loadPublishedVersions(templateId, retain) {
      this.versionLoading = true
      try {
        const response = await listTriggerTemplateVersions(templateId)
        this.versions = response.data || []
        if (!retain || !this.publishedVersions.some(item => this.versionId(item) === Number(this.form.templateVersionId))) this.form.templateVersionId = null
      } catch (error) {
        this.versions = []
        this.form.templateVersionId = null
        this.showError(error, '加载模板版本失败')
      } finally { this.versionLoading = false }
    },
    enabledLabel(enabled) {
      const status = enabled === 'Y' ? '0' : '1'
      const item = (this.dict.type.law_todo_rule_status || []).find(option => option.value === status)
      return item ? item.label : (enabled === 'Y' ? '启用' : '停用')
    },
    actionId(action) { return `trigger-${action}-${Date.now()}-${Math.random().toString(16).slice(2)}` },
    async validate() {
      await this.$refs.form.validate()
      if (!this.selectedEventActive) throw new Error('当前事件版本已停用，请选择启用的事件版本后再保存')
      if (!this.form.businessType) throw new Error('事件目录未提供业务对象，无法保存规则')
      if (!this.publishedVersions.some(item => this.versionId(item) === Number(this.form.templateVersionId))) throw new Error('触发规则必须绑定当前模板的已发布版本')
      return this.$refs.conditionBuilder.validate()
    },
    async save() {
      if (this.saving) return
      try {
        const conditionJson = await this.validate()
        this.saving = true
        const payload = {
          triggerRuleId: this.persisted ? this.form.triggerRuleId : null,
          eventType: this.form.eventType,
          payloadVersion: Number(this.form.payloadVersion),
          templateId: Number(this.form.templateId),
          templateVersionId: Number(this.form.templateVersionId),
          businessType: this.form.businessType,
          enabled: this.persisted ? this.form.serverEnabled : this.form.enabled,
          conditionJson,
          actionId: this.actionId('save'),
          expectedVersion: this.persisted ? Number(this.form.version) : 0
        }
        await (payload.triggerRuleId ? updateTriggerRule(payload.triggerRuleId, payload) : createTriggerRule(payload))
        this.$modal.msgSuccess('触发规则已保存')
        this.$emit('saved')
        this.$refs.drawer.closeAfterSave()
      } catch (error) { this.showError(error, '保存触发规则失败') } finally { this.saving = false }
    },
    format(value) { return value ? this.parseTime(value, '{y}-{m}-{d} {h}:{i}:{s}') : '-' },
    showError(error, fallback) { if (error && error !== 'cancel') this.$modal.msgError((error && (error.msg || error.message)) || fallback) }
  }
}
</script>

<style scoped lang="scss">
.full-width { width: 100%; }
.form-help { margin: 7px 0 0; color: #64748b; font-size: 12px; line-height: 1.5; }
.trigger-rule-form .el-alert { margin-top: 8px; }
</style>
