<template>
  <section class="event-input" data-testid="event-input-panel">
    <h4>事件输入</h4>
    <div class="event-input__grid">
      <div v-for="field in fields" :key="field.path" class="event-input__field">
        <label>{{ field.label }} <em v-if="field.required">*</em></label>
        <semantic-option-selector
          v-if="field.editable"
          :field="field"
          :value="field.rawValue"
          :disabled="readonly"
          :placeholder="field.required && field.missing ? '请选择或补充' : '可选'"
          @input="$emit('override', { path: field.path, value: $event })"
        />
        <semantic-value-renderer v-else :field="field" />
        <small v-if="field.issueMessage" class="event-input__issue">{{ field.issueMessage }}</small>
      </div>
    </div>
  </section>
</template>

<script>
import SemanticOptionSelector from './SemanticOptionSelector'
import SemanticValueRenderer from './SemanticValueRenderer'
export default {
  name: 'EventInputPanel',
  components: { SemanticOptionSelector, SemanticValueRenderer },
  props: { fields: { type: Array, default: () => [] }, readonly: Boolean }
}
</script>

<style scoped>
.event-input h4 { color: #0B2A55; }
.event-input__grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.event-input__field { display: grid; gap: 7px; }
.event-input__field label { color: #334155; font-weight: 600; }
.event-input__field em, .event-input__issue { color: #D14343; }
@media (max-width: 720px) { .event-input__grid { grid-template-columns: 1fr; } }
</style>
