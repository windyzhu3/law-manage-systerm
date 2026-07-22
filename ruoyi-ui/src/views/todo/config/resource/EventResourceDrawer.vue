<template>
  <config-detail-drawer
    ref="drawer"
    :visible="visible"
    :title="title"
    size="820px"
    :dirty="dirty"
    @update:visible="$emit('update:visible', $event)"
    @reset="reset"
  >
    <div v-loading="loading">
      <el-alert v-if="immutable" title="已启用版本不可直接修改。需要调整字段时，请先创建新版本。" type="info" :closable="false" show-icon />
      <el-form ref="form" :model="form" :rules="rules" label-width="110px" class="event-resource-form">
        <el-divider content-position="left">事件基本信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="事件编码" prop="eventType"><el-input v-model.trim="form.eventType" :disabled="readonly || persisted" placeholder="如 CONTRACT_APPROVED" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="事件名称" prop="eventName"><el-input v-model.trim="form.eventName" :disabled="readonly" placeholder="如 合同审批通过" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="业务对象" prop="businessObjectType"><el-select v-model="form.businessObjectType" :disabled="readonly" filterable allow-create class="full-width"><el-option v-for="item in businessTypes" :key="item" :label="item" :value="item" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="来源模块" prop="sourceModule"><el-input v-model.trim="form.sourceModule" :disabled="readonly" placeholder="如 contract" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="用途说明"><el-input v-model.trim="form.description" type="textarea" :rows="2" :disabled="readonly" placeholder="说明事件何时发生，以及供哪些规则使用" /></el-form-item>

        <el-divider content-position="left">Payload 结构</el-divider>
        <payload-schema-designer ref="schemaDesigner" v-model="form.payloadSchemaJson" :readonly="readonly" @validity-change="schemaValid = $event" />

        <el-divider content-position="left">示例 Payload</el-divider>
        <p class="form-help">用于配置预览和模拟测试，应与上方字段结构一致。</p>
        <el-input v-model="form.samplePayloadJson" type="textarea" :rows="7" :disabled="readonly" spellcheck="false" />

        <el-descriptions v-if="persisted" :column="3" border size="small" class="event-resource-meta">
          <el-descriptions-item label="Payload 版本">v{{ form.payloadVersion }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="statusType(form.status)" size="small">{{ statusLabel(form.status) }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="引用数">{{ form.references.length }}</el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="persisted && form.references.length" :title="`当前被 ${form.references.length} 项配置引用，停用前请确认影响范围。`" type="warning" :closable="false" show-icon />
      </el-form>
    </div>

    <template #footer>
      <el-button @click="$refs.drawer.requestProgrammaticClose()">关闭</el-button>
      <el-button v-if="persisted && immutable" v-hasPermi="['todo:resource:add']" plain @click="newVersion">创建新版本</el-button>
      <el-button v-if="persisted && form.status === 'DRAFT'" v-hasPermi="['todo:resource:status']" type="success" plain @click="publish">发布启用</el-button>
      <el-button v-if="persisted && form.status === 'ACTIVE'" v-hasPermi="['todo:resource:status']" type="warning" plain @click="disable">停用</el-button>
      <el-button v-if="!readonly" v-hasPermi="[persisted ? 'todo:resource:edit' : 'todo:resource:add']" type="primary" :loading="saving" :disabled="!schemaValid" @click="save">保存草稿</el-button>
    </template>
  </config-detail-drawer>
</template>

<script>
import ConfigDetailDrawer from '../shared/ConfigDetailDrawer'
import PayloadSchemaDesigner from './PayloadSchemaDesigner'
import { getEventResource, createEventResource, updateEventResource, createEventResourceVersion, changeEventResourceStatus } from '@/api/todo-resources'

const emptyForm = () => ({ eventCatalogId: null, eventType: '', eventName: '', description: '', payloadVersion: 1, businessObjectType: '', sourceModule: '', payloadSchemaJson: '{"type":"object","properties":{}}', samplePayloadJson: '{}', status: 'DRAFT', version: 0, references: [] })

export default {
  name: 'EventResourceDrawer',
  components: { ConfigDetailDrawer, PayloadSchemaDesigner },
  props: { visible: Boolean, resourceId: { type: [Number, String], default: null }, mode: { type: String, default: 'view' } },
  data() {
    return {
      form: emptyForm(), loading: false, saving: false, schemaValid: true, initialSnapshot: '',
      businessTypes: ['LEAD', 'CUSTOMER', 'CONTRACT', 'CASE', 'MATTER'],
      rules: {
        eventType: [{ required: true, message: '请输入事件编码', trigger: 'blur' }, { pattern: /^[A-Z][A-Z0-9_]*$/, message: '仅支持大写字母、数字和下划线', trigger: 'blur' }],
        eventName: [{ required: true, message: '请输入事件名称', trigger: 'blur' }],
        businessObjectType: [{ required: true, message: '请选择业务对象', trigger: 'change' }],
        sourceModule: [{ required: true, message: '请输入来源模块', trigger: 'blur' }]
      }
    }
  },
  computed: {
    persisted() { return Boolean(this.form.eventCatalogId) },
    immutable() { return this.persisted && this.form.status !== 'DRAFT' },
    readonly() { return this.mode === 'view' || this.immutable },
    title() { return this.persisted ? `${this.form.eventName || this.form.eventType} · v${this.form.payloadVersion}` : '新增事件资源' },
    dirty() { return !this.readonly && Boolean(this.initialSnapshot) && this.snapshot() !== this.initialSnapshot }
  },
  watch: { visible(open) { if (open) this.load() }, resourceId() { if (this.visible) this.load() } },
  methods: {
    actionId(action) { return `event-resource-${action}-${Date.now()}-${Math.random().toString(16).slice(2)}` },
    snapshot() { return JSON.stringify(this.form) },
    async load() {
      this.reset()
      if (!this.resourceId) { this.initialSnapshot = this.snapshot(); return }
      this.loading = true
      try {
        const response = await getEventResource(this.resourceId)
        this.form = { ...emptyForm(), ...(response.data || {}) }
        this.form.references = this.form.references || []
        this.initialSnapshot = this.snapshot()
      } catch (error) { this.$modal.msgError((error && (error.msg || error.message)) || '加载事件资源失败') } finally { this.loading = false }
    },
    reset() { this.form = emptyForm(); this.initialSnapshot = ''; this.schemaValid = true },
    parseSample() {
      const value = JSON.parse(this.form.samplePayloadJson || '{}')
      if (!value || Array.isArray(value) || typeof value !== 'object') throw new Error('示例 Payload 必须是 JSON 对象')
      return JSON.stringify(value)
    },
    async save() {
      if (this.saving) return
      try {
        await this.$refs.form.validate()
        const payloadSchemaJson = this.$refs.schemaDesigner.validate()
        const samplePayloadJson = this.parseSample()
        this.saving = true
        const payload = { ...this.form, payloadSchemaJson, samplePayloadJson, status: 'DRAFT', actionId: this.actionId('save'), expectedVersion: Number(this.form.version || 0) }
        delete payload.references
        const response = await (this.persisted ? updateEventResource(this.form.eventCatalogId, payload) : createEventResource(payload))
        this.$modal.msgSuccess('事件草稿已保存')
        this.$emit('saved', response.data)
        this.$refs.drawer.closeAfterSave()
      } catch (error) { if (error !== false) this.$modal.msgError((error && (error.msg || error.message)) || '保存事件资源失败') } finally { this.saving = false }
    },
    async newVersion() {
      try {
        const response = await createEventResourceVersion(this.form.eventCatalogId, { actionId: this.actionId('version') })
        this.$modal.msgSuccess('已创建可编辑的新版本')
        this.$emit('version-created', response.data)
      } catch (error) { this.$modal.msgError((error && (error.msg || error.message)) || '创建新版本失败') }
    },
    async changeStatus(status) {
      try {
        await changeEventResourceStatus(this.form.eventCatalogId, { status, actionId: this.actionId(status.toLowerCase()), expectedVersion: Number(this.form.version || 0) })
        this.$modal.msgSuccess(status === 'ACTIVE' ? '事件已发布启用' : '事件已停用')
        this.$emit('saved')
        this.$refs.drawer.closeAfterSave()
      } catch (error) { this.$modal.msgError((error && (error.msg || error.message)) || '状态变更失败') }
    },
    publish() { this.changeStatus('ACTIVE') },
    disable() { this.$confirm('停用后，新触发规则将不能再选择这个事件版本。是否继续？', '停用事件', { type: 'warning' }).then(() => this.changeStatus('DISABLED')).catch(() => {}) },
    statusLabel(status) { return ({ DRAFT: '草稿', ACTIVE: '已启用', DISABLED: '已停用' })[status] || status },
    statusType(status) { return ({ ACTIVE: 'success', DISABLED: 'info', DRAFT: 'warning' })[status] || '' }
  }
}
</script>

<style scoped lang="scss">
.full-width { width: 100%; }
.event-resource-form > .el-alert { margin-bottom: 16px; }
.form-help { margin: -2px 0 9px; color: #64748b; font-size: 12px; }
.event-resource-meta { margin: 20px 0 12px; }
</style>
