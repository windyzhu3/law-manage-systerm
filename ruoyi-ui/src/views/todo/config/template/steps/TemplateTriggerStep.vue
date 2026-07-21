<template>
  <section>
    <el-alert title="仅可选择后端已注册且启用的业务事件；事件载荷结构由服务端目录提供。" type="info" :closable="false" show-icon />
    <el-form ref="form" :model="model" :rules="rules" label-width="110px" class="top-gap">
      <el-row :gutter="16">
        <el-col :span="16"><el-form-item label="触发事件" prop="eventType"><el-select v-model="model.eventType" :disabled="readonly" filterable class="full" @change="selectEvent"><el-option v-for="item in selectableEvents" :key="eventKey(item)" :label="eventLabel(item)" :value="field(item,'eventType','event_type')" /></el-select></el-form-item></el-col>
        <el-col :span="8"><el-form-item label="载荷版本"><el-input-number v-model="model.payloadVersion" :disabled="readonly" :min="1" :precision="0" class="full" /></el-form-item></el-col>
      </el-row>
      <el-form-item label="触发条件"><trigger-condition-builder ref="conditionBuilder" v-model="conditionJson" :payload-schema-json="payloadSchemaJson" :readonly="readonly" @dirty-change="$emit('dirty-change')" /></el-form-item>
    </el-form>
  </section>
</template>
<script>
import TriggerConditionBuilder from '../../trigger/TriggerConditionBuilder'
import { listTemplateEventCatalog } from '@/api/todo-config'
export default {
  name: 'TemplateTriggerStep', components: { TriggerConditionBuilder },
  dicts: ['law_todo_condition_operator'],
  props: { value: { type: Object, required: true }, businessType: String, readonly: Boolean },
  data() { return { syncing: false, model: { ...this.value }, events: [], conditionJson: JSON.stringify(this.value.condition || {}), rules: { eventType: [{ required: true, message: '请选择触发事件', trigger: 'change' }] } } },
  computed: { selectableEvents() { return this.events.filter(item => !this.businessType || !this.field(item, 'businessObjectType', 'business_object_type') || this.field(item, 'businessObjectType', 'business_object_type') === this.businessType) }, selectedEvent() { return this.events.find(item => this.field(item, 'eventType', 'event_type') === this.model.eventType && Number(this.field(item, 'payloadVersion', 'payload_version') || 1) === Number(this.model.payloadVersion || 1)) }, payloadSchemaJson() { return this.field(this.selectedEvent, 'payloadSchemaJson', 'payload_schema_json') || '{}' } },
  watch: { value: { deep: true, handler(value) { if (JSON.stringify(value) === JSON.stringify(this.model)) return; this.syncing = true; this.model = { ...value }; this.conditionJson = JSON.stringify(value.condition || {}); this.$nextTick(() => { this.syncing = false }) } }, model: { deep: true, handler(value) { if (!this.syncing) this.emitValue(value) } }, conditionJson(value) { try { this.model.condition = JSON.parse(value || '{}') } catch (_) { /* builder keeps invalid text locally */ } } },
  created() { this.loadCatalog() },
  methods: { field(row, camel, snake) { return row && (row[camel] !== undefined ? row[camel] : row[snake]) }, eventKey(item) { return `${this.field(item, 'eventType', 'event_type')}:${this.field(item, 'payloadVersion', 'payload_version')}` }, eventLabel(item) { return `${this.field(item, 'eventType', 'event_type')} · v${this.field(item, 'payloadVersion', 'payload_version') || 1}` }, async loadCatalog() { const response = await listTemplateEventCatalog(); this.events = response.data || [] }, selectEvent() { const item = this.selectableEvents.find(row => this.field(row, 'eventType', 'event_type') === this.model.eventType); if (item) this.model.payloadVersion = Number(this.field(item, 'payloadVersion', 'payload_version') || 1) }, emitValue(value) { this.$emit('input', { ...value, condition: value.condition || {} }); this.$emit('dirty-change') }, async validate() { const valid = await new Promise(resolve => this.$refs.form.validate(ok => resolve(ok))); if (!valid) return false; try { if (this.$refs.conditionBuilder) this.$refs.conditionBuilder.validate(); return true } catch (error) { this.$modal.msgError(error.message); return false } } }
}
</script>
<style scoped>.full{width:100%}.top-gap{margin-top:16px}</style>
