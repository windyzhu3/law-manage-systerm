<template>
  <el-form ref="form" :model="state.fields" label-width="120px" class="todo-dynamic-form">
    <el-form-item
      v-for="field in visibleFields"
      :key="field.key"
      :prop="field.key"
      :for="field.type === 'materialChecklist' ? null : controlId(field)"
      :required="field.required"
    >
      <span slot="label" :id="labelId(field)">{{ field.label }}</span>
      <component
        :is="componentFor(field)"
        v-bind="controlAttrs(field)"
        :value="valueFor(field)"
        v-bind="propsFor(field)"
        @input="setValue(field, $event)"
      />
      <small v-if="field.help" class="field-help">{{ field.help }}</small>
    </el-form-item>
  </el-form>
</template>

<script>
import BusinessFilePicker from '@/components/BusinessFile/BusinessFilePicker'
import TodoMaterialChecklist from '@/components/BusinessFile/TodoMaterialChecklist'
import { fieldRegistry, resolveFieldComponent } from './field-registry'
import { normalizeFields, validateFormState, getMaterialRequirements, getFileFieldContext } from './schema-runtime'

const TodoDictField = {
  name: 'TodoDictField',
  props: { value: null, options: { type: Array, default: () => [] }, disabled: Boolean, placeholder: String },
  render(h) {
    if (!this.options.length) {
      return h('el-alert', {
        props: {
          title: 'Dictionary options are unavailable. Contact an administrator.',
          type: 'error',
          closable: false,
          showIcon: true
        }
      })
    }
    return h('el-select', {
      style: { width: '100%' },
      props: { value: this.value, disabled: this.disabled, clearable: true, placeholder: this.placeholder },
      on: { input: value => this.$emit('input', value) }
    }, this.options.map(option => h('el-option', { props: { label: option.label, value: option.value }, key: String(option.value) })))
  }
}

function numericField(name) {
  return {
    name,
    props: { value: null, disabled: Boolean, placeholder: String },
    render(h) {
      return h('el-input-number', {
        style: { width: '100%' },
        props: { value: this.value, disabled: this.disabled, min: 1, precision: 0, placeholder: this.placeholder },
        on: { input: value => this.$emit('input', value) }
      })
    }
  }
}

export default {
  name: 'TodoDynamicForm',
  components: {
    BusinessFilePicker,
    TodoMaterialChecklist,
    TodoDictField,
    TodoUserField: numericField('TodoUserField'),
    TodoDeptField: numericField('TodoDeptField')
  },
  props: {
    value: { type: Object, required: true },
    formView: { type: Object, required: true },
    businessType: { type: String, required: true },
    businessId: { type: [Number, String], required: true },
    disabled: Boolean
  },
  data() { return { fieldRegistry } },
  computed: {
    state() { return this.value },
    fields() { return normalizeFields(this.formView, this.state) },
    visibleFields() { return this.fields.filter(field => field.visible !== false) },
    requirements() { return getMaterialRequirements(this.formView) }
  },
  methods: {
    controlId(field) { return `todo-field-${this.formView.todoId || this.businessId}-${field.key}` },
    labelId(field) { return `${this.controlId(field)}-label` },
    controlAttrs(field) {
      if (field.type === 'file') {
        return {
          controlId: this.controlId(field),
          labelledBy: this.labelId(field)
        }
      }
      if (field.type === 'materialChecklist') {
        return {
          id: this.controlId(field),
          labelledBy: this.labelId(field)
        }
      }
      return {
        id: this.controlId(field),
        'aria-label': field.label
      }
    },
    componentFor(field) { return resolveFieldComponent(field.type) },
    valueFor(field) { return field.type === 'materialChecklist' ? this.state.materials : this.state.fields[field.key] },
    propsFor(field) {
      const common = { disabled: this.disabled || field.disabled, placeholder: field.placeholder || `请输入${field.label}` }
      if (field.type === 'textarea') return { ...common, type: 'textarea', rows: field.rows || 4, maxlength: field.maxlength || 1000, showWordLimit: true }
      if (field.type === 'date') return { ...common, type: 'date', valueFormat: 'yyyy-MM-dd', style: 'width:100%' }
      if (field.type === 'datetime') return { ...common, type: 'datetime', valueFormat: 'yyyy-MM-ddTHH:mm:ss', style: 'width:100%' }
      if (field.type === 'number') return { ...common, min: field.min, max: field.max, precision: field.precision, style: 'width:100%' }
      if (field.type === 'dict') return { ...common, options: field.options || [] }
      if (field.type === 'file') return { ...common, ...getFileFieldContext(this.formView, field), limit: field.limit || 1 }
      if (field.type === 'materialChecklist') return { ...common, businessType: this.businessType, businessId: this.businessId, requirements: field.requirements || this.requirements }
      return common
    },
    setValue(field, value) {
      if (field.type === 'materialChecklist') this.$set(this.state, 'materials', value)
      else {
        this.$set(this.state.fields, field.key, value)
        if (field.type === 'file') {
          const materialType = field.materialType || field.key
          const retained = this.state.materials.filter(file => file.materialType !== materialType)
          const files = value == null ? [] : (Array.isArray(value) ? value : [value])
          files.forEach(file => {
            const selected = typeof file === 'object' ? file : { fileObjectId: Number(file) }
            if (Number(selected.fileObjectId) > 0) retained.push({ ...selected, fileObjectId: Number(selected.fileObjectId), materialType })
          })
          this.$set(this.state, 'materials', retained)
        }
      }
      this.$emit('input', this.state)
    },
    validate() {
      const errors = validateFormState(this.formView, this.state)
      if (errors.length) this.$modal.msgError(errors[0])
      return errors.length === 0
    }
  }
}
</script>

<style scoped>.todo-dynamic-form{margin-top:16px}.field-help{display:block;line-height:1.5;color:#909399}</style>
