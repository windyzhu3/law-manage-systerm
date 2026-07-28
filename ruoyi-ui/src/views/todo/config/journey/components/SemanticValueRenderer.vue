<template>
  <span class="semantic-value" data-testid="semantic-value">
    <strong>{{ field.displayValue || emptyLabel }}</strong>
    <small v-if="field.displayMeta && field.displayMeta.deptName">{{ field.displayMeta.deptName }}</small>
  </span>
</template>

<script>
export default {
  name: 'SemanticValueRenderer',
  props: { field: { type: Object, required: true }, emptyLabel: { type: String, default: '未填写' } }
}
</script>

<style scoped>
.semantic-value { display: inline-flex; flex-direction: column; gap: 2px; color: #0B2A55; }
.semantic-value small { color: #66758A; font-weight: 400; }
</style>
