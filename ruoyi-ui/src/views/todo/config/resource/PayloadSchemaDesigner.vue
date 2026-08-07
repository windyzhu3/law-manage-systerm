<template>
  <section class="schema-designer">
    <div class="schema-designer__head">
      <div>
        <strong>Payload 字段</strong>
        <p>这些字段会直接出现在触发条件、模拟测试和完成条件的下拉选项中。</p>
      </div>
      <el-button v-if="!readonly && !advanced" size="small" plain icon="el-icon-plus" @click="addField">添加字段</el-button>
    </div>

    <template v-if="!advanced">
      <el-empty v-if="!fields.length" description="尚未定义字段，规则将无法配置触发条件" :image-size="64">
        <el-button v-if="!readonly" type="primary" plain size="small" @click="addField">添加第一个字段</el-button>
      </el-empty>
      <div v-else class="schema-designer__table">
        <div class="schema-designer__row schema-designer__row--header">
          <span>字段名称</span><span>显示名称</span><span>字段说明</span><span>字段类型</span><span>业务语义</span><span>是否必填</span><span>示例值</span><span />
        </div>
        <div v-for="(field, index) in fields" :key="field.key" class="schema-designer__row">
          <el-input v-model.trim="field.name" :disabled="readonly" placeholder="如 ownerId" @input="nameChanged(field)" />
          <el-input v-model.trim="field.title" :disabled="readonly" placeholder="如 线索负责人" @input="titleChanged(field)" />
          <el-input class="schema-field-description" v-model.trim="field.description" :disabled="readonly" placeholder="说明字段的业务含义" @input="descriptionChanged(field)" />
          <el-select class="schema-field-type" v-model="field.type" :disabled="readonly" @change="typeChanged(field)">
            <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select class="schema-field-semantic" v-model="field.semanticType" :disabled="readonly" filterable @change="semanticChanged(field)">
            <el-option v-for="item in semanticOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-switch v-model="field.required" :disabled="readonly" @change="emitSchema" />
          <el-input v-model="field.exampleText" :disabled="readonly" :placeholder="examplePlaceholder(field.type)" @input="emitSchema" />
          <el-button v-if="!readonly" type="text" class="danger-text" @click="removeField(index)">删除</el-button>
        </div>
      </div>
    </template>

    <div class="schema-designer__advanced-toggle">
      <el-button type="text" size="small" @click="toggleAdvanced">{{ advanced ? '返回可视化设计' : '高级 JSON' }}</el-button>
      <span>仅在需要嵌套对象、数组约束等高级结构时使用。</span>
    </div>
    <div v-if="advanced" class="schema-designer__advanced">
      <el-alert v-if="jsonError" :title="jsonError" type="error" :closable="false" show-icon />
      <el-input v-model="rawJson" type="textarea" :rows="12" :disabled="readonly" spellcheck="false" @input="rawChanged" />
      <el-button v-if="!readonly" size="small" plain @click="formatRaw">校验并格式化</el-button>
    </div>
  </section>
</template>

<script>
const { applyFieldDefaults, buildPayloadSchema, inferSemanticType } = require('./payload-schema-model')

const newField = () => ({
  key: `field-${Date.now()}-${Math.random()}`,
  name: '', title: '', description: '', descriptionAuto: true,
  type: 'string', semanticType: '', semanticAuto: true,
  required: false, exampleText: ''
})

export default {
  name: 'PayloadSchemaDesigner',
  props: { value: { type: [String, Object], default: '' }, readonly: Boolean },
  data() {
    return {
      fields: [], advanced: false, rawJson: '', jsonError: '', hydrating: false,
      typeOptions: [
        { label: '文本', value: 'string' }, { label: '整数', value: 'integer' },
        { label: '数字', value: 'number' }, { label: '是/否', value: 'boolean' },
        { label: '日期时间', value: 'string:date-time' }, { label: '数组', value: 'array' },
        { label: '对象', value: 'object' }
      ],
      semanticOptions: [
        { label: '普通业务值', value: 'PLAIN_VALUE' }, { label: '人员', value: 'USER_ID' },
        { label: '部门', value: 'DEPT_ID' }, { label: '业务对象', value: 'BUSINESS_REF' },
        { label: '字典选项', value: 'DICT' }, { label: '日期时间', value: 'DATE_TIME' },
        { label: '系统标识', value: 'SYSTEM_ID' }, { label: '版本号', value: 'SYSTEM_VERSION' },
        { label: '计数值', value: 'SYSTEM_COUNTER' }
      ]
    }
  },
  watch: { value: { immediate: true, handler(value) { if (!this.hydrating) this.hydrate(value) } } },
  methods: {
    parse(value) {
      if (!value) return { type: 'object', properties: {}, required: [] }
      return typeof value === 'string' ? JSON.parse(value) : value
    },
    hydrate(value) {
      this.hydrating = true
      try {
        const schema = this.parse(value)
        const required = new Set(Array.isArray(schema.required) ? schema.required : [])
        this.fields = Object.keys(schema.properties || {}).map(name => {
          const item = schema.properties[name] || {}
          const type = item.format === 'date-time' ? 'string:date-time' : (item.type || 'string')
          return {
            key: `field-${name}-${Math.random()}`, name, title: item.title || '',
            description: item.description || '', descriptionAuto: !item.description,
            type, semanticType: item['x-semantic-type'] || '', semanticAuto: !item['x-semantic-type'],
            required: required.has(name), exampleText: this.stringifyExample(item.example)
          }
        })
        this.rawJson = JSON.stringify(schema, null, 2)
        this.jsonError = ''
      } catch (error) {
        this.fields = []
        this.rawJson = typeof value === 'string' ? value : JSON.stringify(value || {}, null, 2)
        this.jsonError = '当前 Schema 不是有效 JSON，请在高级模式中修复。'
        this.advanced = true
      } finally { this.$nextTick(() => { this.hydrating = false }) }
    },
    stringifyExample(value) { return value === undefined ? '' : (typeof value === 'string' ? value : JSON.stringify(value)) },
    buildSchema() {
      return buildPayloadSchema(this.fields)
    },
    emitSchema() {
      if (this.hydrating || this.advanced) return
      const names = this.fields.map(item => item.name).filter(Boolean)
      if (new Set(names).size !== names.length) { this.$emit('validity-change', false, '字段名称不能重复'); return }
      const json = JSON.stringify(this.buildSchema())
      this.rawJson = JSON.stringify(JSON.parse(json), null, 2)
      this.$emit('input', json)
      this.$emit('validity-change', true, '')
    },
    addField() {
      this.fields.push(newField())
      this.$emit('validity-change', false, '请填写字段名称')
    },
    removeField(index) { this.fields.splice(index, 1); this.emitSchema() },
    nameChanged(field) {
      if (field.semanticAuto !== false) field.semanticType = inferSemanticType(field)
      applyFieldDefaults(field)
      this.emitSchema()
    },
    titleChanged(field) {
      if (field.descriptionAuto !== false) field.description = field.title || field.name
      this.emitSchema()
    },
    descriptionChanged(field) { field.descriptionAuto = false; this.emitSchema() },
    semanticChanged(field) { field.semanticAuto = false; this.emitSchema() },
    typeChanged(field) {
      field.exampleText = ''
      if (field.semanticAuto !== false) field.semanticType = inferSemanticType(field)
      this.emitSchema()
    },
    examplePlaceholder(type) { return ({ boolean: 'true', integer: '1', number: '99.5', array: '["A","B"]', object: '{"key":"value"}', 'string:date-time': '2026-07-22T10:00:00' })[type] || '示例文本' },
    toggleAdvanced() {
      if (this.advanced) {
        try { const parsed = JSON.parse(this.rawJson); this.advanced = false; this.hydrate(parsed); this.$emit('input', JSON.stringify(parsed)); this.$emit('validity-change', true, '') } catch (error) { this.jsonError = '请先修复 JSON 格式后再返回可视化设计。' }
      } else { this.rawJson = JSON.stringify(this.buildSchema(), null, 2); this.advanced = true }
    },
    rawChanged() {
      try { const parsed = JSON.parse(this.rawJson); if (!parsed || parsed.type !== 'object' || !parsed.properties) throw new Error(); this.jsonError = ''; this.$emit('input', JSON.stringify(parsed)); this.$emit('validity-change', true, '') } catch (error) { this.jsonError = 'Schema 必须是包含 properties 的 JSON 对象。'; this.$emit('validity-change', false, this.jsonError) }
    },
    formatRaw() {
      try { this.rawJson = JSON.stringify(JSON.parse(this.rawJson), null, 2); this.rawChanged(); if (!this.jsonError) this.$modal.msgSuccess('Schema 已通过校验') } catch (error) { this.rawChanged() }
    },
    validate() {
      if (this.jsonError) throw new Error(this.jsonError)
      const schema = this.advanced ? JSON.parse(this.rawJson) : this.buildSchema()
      if (!Object.keys(schema.properties || {}).length) throw new Error('请至少定义一个 Payload 字段')
      return JSON.stringify(schema)
    }
  }
}
</script>

<style scoped lang="scss">
.schema-designer__head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 14px; }
.schema-designer__head p { margin: 5px 0 0; color: #64748b; font-size: 12px; }
.schema-designer__table { overflow-x: auto; }
.schema-designer__row { display: grid; grid-template-columns: 150px 170px 220px 130px 150px 82px 170px 44px; gap: 8px; align-items: center; min-width: 1230px; padding: 7px 0; border-bottom: 1px solid #eef2f7; }
.schema-designer__row--header { color: #64748b; font-size: 12px; font-weight: 600; }
.schema-designer__advanced-toggle { display: flex; align-items: center; gap: 10px; margin-top: 12px; color: #94a3b8; font-size: 12px; }
.schema-designer__advanced .el-alert, .schema-designer__advanced .el-textarea { margin-bottom: 10px; }
.danger-text { color: #ef4444; }
</style>
