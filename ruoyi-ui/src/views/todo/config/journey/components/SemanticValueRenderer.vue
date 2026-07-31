<template>
  <span class="semantic-value" data-testid="semantic-value">
    <strong>{{ semanticText }}</strong>
    <details v-if="technicalText" class="semantic-value__technical">
      <summary>技术详情</summary>
      <small>原始值：{{ technicalText }}</small>
    </details>
  </span>
</template>

<script>
import { renderSemantic, technicalValue } from '../business-effect-model'

export default {
  name: 'SemanticValueRenderer',
  props: { field: { type: Object, required: true }, emptyLabel: { type: String, default: '未填写' } },
  computed: {
    semanticText() { return renderSemantic(this.field, this.emptyLabel) },
    technicalText() { return technicalValue(this.field) }
  }
}
</script>

<style scoped>
.semantic-value { display: inline-flex; flex-direction: column; gap: 2px; color: #0B2A55; }
.semantic-value__technical { color: #66758A; font-weight: 400; }
.semantic-value__technical summary { font-size: 12px; cursor: pointer; }
.semantic-value__technical small { display: block; margin-top: 2px; }
</style>
