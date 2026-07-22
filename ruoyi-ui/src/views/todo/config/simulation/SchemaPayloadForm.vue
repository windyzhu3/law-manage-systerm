<template>
  <section class="schema-payload-form">
    <el-alert v-if="schemaError" :title="schemaError" type="warning" :closable="false" show-icon />
    <el-empty v-else-if="!fields.length" description="事件没有可配置字段，请先到事件目录维护 Payload Schema" :image-size="58" />
    <el-form v-else ref="form" :model="draft" label-position="top" class="payload-grid">
      <el-form-item v-for="field in fields" :key="field.code" :label="fieldLabel(field)" :required="field.required">
        <el-switch v-if="field.type === 'boolean'" v-model="draft[field.code]" active-text="是" inactive-text="否" @change="emitValue" />
        <el-input-number v-else-if="field.type === 'integer'" v-model="draft[field.code]" :step="1" :precision="0" controls-position="right" class="full-width" @change="emitValue" />
        <el-input-number v-else-if="field.type === 'number'" v-model="draft[field.code]" controls-position="right" class="full-width" @change="emitValue" />
        <el-date-picker v-else-if="field.format === 'date-time'" v-model="draft[field.code]" type="datetime" value-format="yyyy-MM-ddTHH:mm:ss" class="full-width" @change="emitValue" />
        <el-select v-else-if="field.enumValues.length" v-model="draft[field.code]" filterable class="full-width" @change="emitValue"><el-option v-for="item in field.enumValues" :key="String(item)" :label="String(item)" :value="item" /></el-select>
        <el-input v-else-if="field.type === 'array' || field.type === 'object'" v-model="draft[field.code]" type="textarea" :rows="3" :placeholder="field.type === 'array' ? '[&quot;A&quot;,&quot;B&quot;]' : '{&quot;key&quot;:&quot;value&quot;}'" @input="emitValue" />
        <el-input v-else v-model="draft[field.code]" :placeholder="field.title || field.code" @input="emitValue" />
        <p class="field-help"><span>字段：{{ field.code }}</span><span v-if="field.example !== undefined">示例值：{{ exampleText(field.example) }}</span></p>
      </el-form-item>
    </el-form>
  </section>
</template>

<script>
export default {
  name: 'SchemaPayloadForm',
  props: { value: { type: Object, default: () => ({}) }, schema: { type: [String, Object], default: '' }, samplePayloadJson: { type: [String, Object], default: '' } },
  data() { return { draft: {}, schemaError: '', hydrating: false } },
  computed: {
    schemaObject() { try { return typeof this.schema === 'string' ? JSON.parse(this.schema || '{}') : (this.schema || {}) } catch (error) { return {} } },
    requiredFields() { return new Set(Array.isArray(this.schemaObject.required) ? this.schemaObject.required : []) },
    fields() { const schema = this.schemaObject; return Object.keys(schema.properties || {}).map(code => { const item = schema.properties[code] || {}; return { code, title: item.title || code, type: item.type || 'string', format: item.format || '', enumValues: Array.isArray(item.enum) ? item.enum : [], example: item.example, required: this.requiredFields.has(code) } }) }
  },
  watch: { schema: { immediate: true, handler() { this.hydrate() } }, samplePayloadJson() { this.hydrate() }, value: { deep: true, handler(value) { if (!this.hydrating && JSON.stringify(value || {}) !== JSON.stringify(this.normalizedPayload())) this.draft = { ...(value || {}) } } } },
  methods: {
    parseSample() { try { const value = typeof this.samplePayloadJson === 'string' ? JSON.parse(this.samplePayloadJson || '{}') : (this.samplePayloadJson || {}); return value && !Array.isArray(value) && typeof value === 'object' ? value : {} } catch (error) { return {} } },
    hydrate() {
      this.hydrating = true
      try {
        const schema = typeof this.schema === 'string' ? JSON.parse(this.schema || '{}') : (this.schema || {})
        if (!schema || schema.type !== 'object' || !schema.properties) throw new Error()
        this.schemaError = ''
        const sample = this.parseSample(); const incoming = this.value || {}; const draft = {}
        Object.keys(schema.properties).forEach(code => { const definition = schema.properties[code] || {}; let selected = incoming[code] !== undefined ? incoming[code] : (sample[code] !== undefined ? sample[code] : definition.example); if (selected === undefined) selected = definition.type === 'boolean' ? false : (['integer', 'number'].includes(definition.type) ? undefined : ''); if (['array', 'object'].includes(definition.type) && selected !== '' && typeof selected !== 'string') selected = JSON.stringify(selected); draft[code] = selected })
        this.draft = draft; this.$nextTick(() => { this.hydrating = false; this.emitValue() })
      } catch (error) { this.schemaError = 'Payload Schema 无法解析，请先到事件目录修复。'; this.draft = {}; this.hydrating = false; this.$emit('input', {}) }
    },
    normalizedPayload() { const result = {}; this.fields.forEach(field => { let value = this.draft[field.code]; if ((field.type === 'array' || field.type === 'object') && typeof value === 'string' && value.trim()) { try { value = JSON.parse(value) } catch (error) { value = this.draft[field.code] } } if (value !== '' && value !== undefined && value !== null) result[field.code] = value }); return result },
    emitValue() { if (!this.hydrating) this.$emit('input', this.normalizedPayload()) },
    fieldLabel(field) { return `${field.title}${field.required ? ' *' : ''}` },
    exampleText(value) { return typeof value === 'string' ? value : JSON.stringify(value) },
    validate() { if (this.schemaError) throw new Error(this.schemaError); const payload = {}; this.fields.forEach(field => { let value = this.draft[field.code]; if (field.required && (value === '' || value === undefined || value === null)) throw new Error(`请填写${field.title}`); if (['array', 'object'].includes(field.type) && value !== '' && value !== undefined) { try { value = typeof value === 'string' ? JSON.parse(value) : value } catch (error) { throw new Error(`${field.title}不是有效 JSON`) } if (field.type === 'array' && !Array.isArray(value)) throw new Error(`${field.title}必须是数组`); if (field.type === 'object' && (!value || Array.isArray(value) || typeof value !== 'object')) throw new Error(`${field.title}必须是对象`) } if (value !== '' && value !== undefined && value !== null) payload[field.code] = value }); if (!Object.keys(payload).length) throw new Error('Payload 至少需要一个字段'); this.$emit('input', payload); return payload }
  }
}
</script>

<style scoped lang="scss">
.payload-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 12px}.full-width{width:100%}.field-help{display:flex;justify-content:space-between;gap:8px;margin:4px 0 0;color:#94a3b8;font-size:11px}.schema-payload-form>.el-alert{margin-bottom:10px}@media(max-width:760px){.payload-grid{grid-template-columns:1fr}}
</style>
