<template>
  <div class="definition-builder">
    <el-form-item label="必填字段"><el-select :value="model.requiredFields" multiple filterable allow-create default-first-option :disabled="readonly" placeholder="输入稳定字段编码" @input="change('requiredFields', $event)" /></el-form-item>
    <el-form-item label="必需材料"><el-select :value="model.requiredAttachments" multiple filterable allow-create default-first-option :disabled="readonly" placeholder="输入材料类型编码" @input="change('requiredAttachments', $event)" /></el-form-item>
  </div>
</template>
<script>
export default { name: 'DodFormBuilder', props: { value: { type: Object, default: () => ({}) }, readonly: Boolean }, computed: { model() { return { requiredFields: [], requiredAttachments: [], ...this.value } } }, methods: { change(key, value) { this.$emit('input', { ...this.model, [key]: value }) } } }
</script>
