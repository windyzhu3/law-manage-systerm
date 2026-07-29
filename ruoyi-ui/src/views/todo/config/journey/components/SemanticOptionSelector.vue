<template>
  <el-select
    v-if="controlled"
    :value="value"
    filterable
    remote
    clearable
    :remote-method="load"
    :loading="loading"
    :disabled="disabled"
    :placeholder="placeholder"
    @focus="load('')"
    @change="$emit('input', $event)"
  >
    <el-option v-for="option in options" :key="String(option.rawValue)" :label="option.displayValue" :value="option.rawValue" :disabled="!option.selectable">
      <span>{{ option.displayValue }}</span>
      <small v-if="option.meta && option.meta.deptName">{{ option.meta.deptName }}</small>
    </el-option>
  </el-select>
  <el-input v-else :value="value" :disabled="disabled" :placeholder="placeholder" @input="$emit('input', $event)" />
</template>

<script>
import { listTodoFieldOptions } from '@/api/todo-config'

export default {
  name: 'SemanticOptionSelector',
  props: { field: { type: Object, required: true }, value: null, disabled: Boolean, placeholder: { type: String, default: '' } },
  data: () => ({ options: [], loading: false }),
  computed: { controlled() { return ['USER_ID', 'DEPT_ID', 'POST_ID', 'ROLE_KEY', 'DICT', 'BUSINESS_REF'].includes(this.field.semanticType) } },
  watch: {
    field: { immediate: true, deep: true, handler() { this.ensureCurrentOptions() } },
    value() { this.ensureCurrentOptions() }
  },
  methods: {
    ensureCurrentOptions() {
      if (!this.controlled || this.value === '' || this.value == null) return
      this.seedCurrentOption()
      if (this.options.some(option =>
        String(option.rawValue) === String(this.value) && option.displayValue !== '正在解析…'
      )) return
      if (!this.options.some(option => String(option.rawValue) === String(this.value))) {
        this.options = [{
          rawValue: this.value,
          displayValue: '正在解析…',
          selectable: false,
          meta: {}
        }, ...this.options]
      }
      this.load('')
    },
    seedCurrentOption() {
      if (!this.controlled || this.value === '' || this.value == null || !this.field.displayValue) return
      const current = {
        rawValue: this.value,
        displayValue: this.field.displayValue,
        selectable: true,
        meta: this.field.displayMeta || {}
      }
      const rest = this.options.filter(option => String(option.rawValue) !== String(this.value))
      this.options = [current, ...rest]
    },
    async load(keyword) {
      if (!this.controlled || this.loading) return
      this.loading = true
      try {
        const response = await listTodoFieldOptions({
          semanticType: this.field.semanticType,
          optionSource: this.field.optionSource || (this.field.semanticType === 'DICT' ? 'SYSTEM_DICTIONARY' : ''),
          dictType: this.field.dictType || undefined,
          keyword: keyword || undefined,
          pageNum: 1,
          pageSize: 30
        })
        const data = response.data || {}
        const loaded = data.rows || []
        const current = this.options.find(option => String(option.rawValue) === String(this.value))
        this.options = current && !loaded.some(option => String(option.rawValue) === String(this.value))
          ? [current, ...loaded]
          : loaded
      } finally { this.loading = false }
    }
  }
}
</script>

<style scoped>
.el-select { width: 100%; }
.el-select-dropdown__item small { float: right; color: #8492A6; }
</style>
