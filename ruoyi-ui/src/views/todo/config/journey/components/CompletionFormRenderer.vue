<template>
  <section v-if="scenario" class="completion-form" data-testid="completion-form-renderer">
    <div>
      <h3>{{ scenario.scenarioName }}：完成待办信息</h3>
      <p>仅填写当前待办完成所需字段，不展示后续复核字段。</p>
    </div>
    <div class="completion-form__grid">
      <label v-for="field in fields" :key="field.path">
        <span>{{ field.label || field.path }}</span>
        <el-date-picker
          v-if="field.path === 'contactedAt'"
          :value="valueFor(field)"
          type="datetime"
          value-format="yyyy-MM-dd'T'HH:mm:ss"
          placeholder="默认使用模拟时间"
          :disabled="readonly"
          @input="update(field.path, $event)"
        />
        <semantic-option-selector
          v-else
          :field="field"
          :value="valueFor(field)"
          :placeholder="field.required ? '请选择' : '可选'"
          :disabled="readonly"
          @input="update(field.path, $event)"
        />
      </label>
    </div>
    <el-button type="primary" :loading="loading" :disabled="readonly || !businessSelected" @click="$emit('run')">
      运行当前场景
    </el-button>
  </section>
</template>

<script>
import SemanticOptionSelector from './SemanticOptionSelector'
import { scenarioFormFields } from '../simulation-workbench-model'

export default {
  name: 'CompletionFormRenderer',
  components: { SemanticOptionSelector },
  props: {
    scenario: { type: Object, default: null },
    completionFields: { type: Array, default: () => [] },
    overrides: { type: Object, default: () => ({}) },
    effectiveAt: { type: String, default: '' },
    loading: Boolean,
    businessSelected: Boolean,
    readonly: Boolean
  },
  computed: {
    fields() { return scenarioFormFields(this.scenario, this.completionFields) }
  },
  methods: {
    valueFor(field) {
      if (Object.prototype.hasOwnProperty.call(this.overrides, field.path)) return this.overrides[field.path]
      if (field.path === 'contactedAt') return this.effectiveAt
      return field.rawValue
    },
    update(path, value) { this.$emit('override', { path, value }) }
  }
}
</script>

<style scoped>
.completion-form { display: grid; gap: 16px; border: 1px solid #D9E1EA; border-radius: 8px; padding: 20px; background: #fff; }
.completion-form h3 { margin: 0 0 6px; color: #0B2A55; }
.completion-form p { margin: 0; color: #66758A; }
.completion-form__grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.completion-form label { display: grid; gap: 7px; color: #334155; font-weight: 600; }
.completion-form .el-date-editor { width: 100%; }
@media (max-width: 720px) { .completion-form__grid { grid-template-columns: 1fr; } }
</style>
