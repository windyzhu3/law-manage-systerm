<template>
  <div class="definition-builder">
    <el-alert v-if="!triggerEditor" title="触发事件和条件由触发规则页维护" type="info" :closable="false" />
    <el-form-item label="触发事件"><el-input :value="model.eventType" :disabled="!triggerEditor" @input="update({ eventType: $event })" /></el-form-item>
    <el-form-item label="负载版本"><el-input-number :value="model.payloadVersion" :disabled="!triggerEditor" :min="1" @change="update({ payloadVersion: $event })" /></el-form-item>
    <el-form-item label="条件字段"><el-input :value="condition.field" :disabled="!triggerEditor" @input="conditionUpdate({ field: $event })" /></el-form-item>
    <el-form-item label="运算符"><el-select :value="condition.operator" :disabled="!triggerEditor" @change="conditionUpdate({ operator: $event })"><el-option v-for="operator in operators" :key="operator" :label="operator" :value="operator" /></el-select></el-form-item>
    <el-form-item label="比较值"><el-input :value="valueText" :disabled="!triggerEditor" @input="conditionUpdate({ value: $event })" /></el-form-item>
    <el-alert v-if="parseError" :title="parseError" type="error" :closable="false" />
  </div>
</template>
<script>
export default {
  name: 'EventConditionBuilder', props: { value: { type: Object, default: () => ({}) }, triggerEditor: { type: Boolean, default: false } },
  data() { return { operators: ['EQ', 'NE', 'IN', 'NOT_IN', 'GT', 'GTE', 'LT', 'LTE', 'CONTAINS'], parseError: '' } },
  computed: { model() { return { eventType: '', payloadVersion: 1, condition: {}, ...this.value } }, condition() { return this.model.condition || {} }, valueText() { const value = this.condition.value; return value == null ? '' : typeof value === 'object' ? JSON.stringify(value) : String(value) } },
  methods: { update(change) { if (this.triggerEditor) this.$emit('input', { ...this.model, ...change }) }, conditionUpdate(change) { if (!this.triggerEditor) return; let value = change.value; this.parseError = ''; if (typeof value === 'string' && /^[\[{]/.test(value.trim())) { try { value = JSON.parse(value) } catch (_) { this.parseError = '条件值中的 JSON 格式无效'; return } } this.update({ condition: { ...this.condition, ...change, ...(change.value !== undefined ? { value } : {}) } }) } }
}
</script>
