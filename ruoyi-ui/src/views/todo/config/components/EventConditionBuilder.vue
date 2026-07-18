<template>
  <div class="definition-builder">
    <el-alert v-if="!triggerEditor" title="触发事件和条件由触发规则页维护" type="info" :closable="false" />
    <el-form-item label="触发事件"><el-select v-if="triggerEditor" :value="model.eventType" @change="update({ eventType: $event, payloadVersion: versions($event)[0] || 1 })"><el-option v-for="type in eventTypes" :key="type" :label="type" :value="type" /></el-select><el-input v-else :value="model.eventType" disabled /></el-form-item>
    <el-form-item label="负载版本"><el-select v-if="triggerEditor" :value="model.payloadVersion" @change="update({ payloadVersion: $event })"><el-option v-for="version in versions(model.eventType)" :key="version" :label="String(version)" :value="version" /></el-select><el-input v-else :value="String(model.payloadVersion)" disabled /></el-form-item>
    <el-form-item label="条件字段"><el-input :value="condition.field" :disabled="!triggerEditor" @input="conditionUpdate({ field: $event })" /></el-form-item>
    <el-form-item label="运算符"><el-select :value="condition.operator" :disabled="!triggerEditor" @change="conditionUpdate({ operator: $event })"><el-option v-for="operator in operators" :key="operator" :label="operator" :value="operator" /></el-select></el-form-item>
    <el-form-item label="比较值"><el-input :value="valueText" :disabled="!triggerEditor" @input="conditionUpdate({ value: $event })" /></el-form-item>
    <el-alert v-if="parseError" :title="parseError" type="error" :closable="false" />
  </div>
</template>
<script>
import { listTodoEventCatalog } from '@/api/todo-definition'
export default {
  name: 'EventConditionBuilder', props: { value: { type: Object, default: () => ({}) }, triggerEditor: { type: Boolean, default: false } },
  data() { return { operators: ['EQ', 'NE', 'IN', 'NOT_IN', 'GT', 'GTE', 'LT', 'LTE', 'CONTAINS'], parseError: '', catalog: [] } },
  created() { if (this.triggerEditor) listTodoEventCatalog().then(response => { this.catalog = (response.data || []).filter(item => item.status === 'ACTIVE') }) },
  computed: { model() { return { eventType: '', payloadVersion: 1, condition: {}, ...this.value } }, condition() { return this.model.condition || {} }, valueText() { const value = this.condition.value; return value == null ? '' : typeof value === 'object' ? JSON.stringify(value) : String(value) }, eventTypes() { return [...new Set(this.catalog.map(item => item.event_type || item.eventType))] } },
  methods: { versions(type) { return this.catalog.filter(item => (item.event_type || item.eventType) === type).map(item => Number(item.payload_version || item.payloadVersion)).sort() }, update(change) { if (this.triggerEditor) this.$emit('input', { ...this.model, ...change }) }, conditionUpdate(change) { if (!this.triggerEditor) return; let value = change.value; this.parseError = ''; if (typeof value === 'string' && /^[\[{]/.test(value.trim())) { try { value = JSON.parse(value) } catch (_) { this.parseError = '条件值中的 JSON 格式无效'; return } } this.update({ condition: { ...this.condition, ...change, ...(change.value !== undefined ? { value } : {}) } }) } }
}
</script>
