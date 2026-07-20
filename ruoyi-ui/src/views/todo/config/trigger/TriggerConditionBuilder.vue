<template>
  <section class="condition-builder">
    <el-alert
      v-if="schemaError"
      :title="schemaError"
      type="warning"
      :closable="false"
      show-icon
    />

    <template v-if="!rawMode">
      <div class="condition-builder__toolbar">
        <div>
          <strong>条件组合</strong>
          <span class="condition-builder__hint">字段来自当前事件的 Payload Schema</span>
        </div>
        <el-radio-group v-model="groupOperator" :disabled="readonly" size="mini" @change="emitBuilderValue">
          <el-radio-button label="AND">全部满足</el-radio-button>
          <el-radio-button label="OR">任一满足</el-radio-button>
        </el-radio-group>
      </div>

      <el-empty v-if="!conditions.length" description="未设置条件，事件到达后直接触发" :image-size="64" />
      <div v-for="(condition, index) in conditions" :key="condition.key" class="condition-builder__row">
        <el-select
          v-model="condition.fieldPath"
          filterable
          :disabled="readonly"
          placeholder="Payload 字段"
          @change="emitBuilderValue"
        >
          <el-option v-for="field in schemaFields" :key="field.path" :label="field.label" :value="field.path" />
        </el-select>
        <el-select
          v-model="condition.operator"
          :disabled="readonly"
          placeholder="运算符"
          @change="operatorChanged(condition)"
        >
          <el-option v-for="item in operatorOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input
          v-if="requiresValue(condition.operator)"
          v-model="condition.valueJson"
          :disabled="readonly"
          placeholder='比较值，如 READY、10 或 ["A","B"]'
          @input="emitBuilderValue"
        />
        <span v-else class="condition-builder__no-value">无需比较值</span>
        <el-button v-if="!readonly" type="text" class="danger-text" @click="removeCondition(index)">删除</el-button>
      </div>
      <el-button v-if="!readonly" plain size="small" icon="el-icon-plus" @click="addCondition">添加条件</el-button>
      <el-button v-if="value" type="text" size="small" @click="showRawJson">查看原始 JSON</el-button>
    </template>

    <div v-else class="condition-builder__raw">
      <el-alert
        :title="malformed ? '条件 JSON 格式异常，原文已保留；修复成功前不能保存' : '当前条件使用原始 JSON 表达，可校验后继续保存'"
        :type="malformed ? 'error' : 'info'"
        :closable="false"
        show-icon
      />
      <el-input :value="rawJson" type="textarea" :rows="8" :disabled="readonly" spellcheck="false" @input="rawInput" />
      <div v-if="!readonly" class="condition-builder__raw-actions">
        <el-button type="primary" plain size="small" @click="repairRawJson">校验并修复</el-button>
        <el-button v-if="!malformed" size="small" @click="tryVisualMode">尝试切换为可视化编辑</el-button>
      </div>
    </div>
  </section>
</template>

<script>
const emptyCondition = () => ({ key: `condition-${Date.now()}-${Math.random()}`, fieldPath: '', operator: 'EQ', valueJson: '' })
const fieldValue = (row, camel, snake) => row && (row[camel] !== undefined ? row[camel] : row[snake])

export default {
  name: 'TriggerConditionBuilder',
  dicts: ['law_todo_condition_operator'],
  props: {
    value: { type: String, default: '' },
    payloadSchemaJson: { type: [String, Object], default: '' },
    readonly: Boolean
  },
  data() {
    return {
      groupOperator: 'AND',
      conditions: [],
      rawJson: '',
      rawMode: false,
      malformed: false,
      hydrating: false,
      lastEmittedValue: null
    }
  },
  computed: {
    operatorOptions() {
      return (this.dict.type.law_todo_condition_operator || []).map(item => ({ label: item.label, value: item.value }))
    },
    schemaDescriptor() {
      if (!this.payloadSchemaJson) return { fields: [], error: '' }
      try {
        const schema = typeof this.payloadSchemaJson === 'string' ? JSON.parse(this.payloadSchemaJson) : this.payloadSchemaJson
        return { fields: this.flattenSchema(schema && schema.properties, ''), error: '' }
      } catch (error) {
        return { fields: [], error: '事件 Payload Schema 无法解析，请联系管理员修复事件目录' }
      }
    },
    schemaFields() { return this.schemaDescriptor.fields },
    schemaError() { return this.schemaDescriptor.error }
  },
  watch: {
    value: { immediate: true, handler(value) { if (value === this.lastEmittedValue) { this.lastEmittedValue = null; return } this.hydrate(value) } }
  },
  methods: {
    fieldValue,
    flattenSchema(properties, prefix) {
      if (!properties || Array.isArray(properties) || typeof properties !== 'object') return []
      return Object.keys(properties).reduce((result, name) => {
        const definition = properties[name] || {}
        const path = prefix ? `${prefix}.${name}` : name
        const types = Array.isArray(definition.type) ? definition.type : [definition.type].filter(Boolean)
        result.push({ path, label: `${path}${types.length ? ` (${types.join('|')})` : ''}` })
        if (definition.properties) result.push(...this.flattenSchema(definition.properties, path))
        return result
      }, [])
    },
    hydrate(value) {
      if (this.hydrating) return
      this.hydrating = true
      const original = value || ''
      this.rawJson = original
      if (!original.trim()) {
        this.conditions = []
        this.groupOperator = 'AND'
        this.rawMode = false
        this.malformed = false
        this.hydrating = false
        this.$emit('dirty-change', false)
        return
      }
      try {
        const document = JSON.parse(original)
        const decoded = this.decodeVisual(document)
        if (!decoded) {
          this.rawMode = true
          this.malformed = false
        } else {
          this.groupOperator = decoded.groupOperator
          this.conditions = decoded.conditions
          this.rawMode = false
          this.malformed = false
        }
      } catch (error) {
        this.rawMode = true
        this.malformed = true
      }
      this.hydrating = false
      this.$emit('dirty-change', false)
    },
    decodeVisual(document) {
      const envelope = document && document.$expression
      if (!envelope || Number(envelope.version) !== 1 || !envelope.root) return null
      const root = envelope.root
      if (root.field && root.operator) return { groupOperator: 'AND', conditions: [this.toEditorCondition(root)] }
      if (!['AND', 'OR'].includes(root.type) || !Array.isArray(root.conditions) || root.conditions.some(item => !item || !item.field || !item.operator)) return null
      return { groupOperator: root.type, conditions: root.conditions.map(this.toEditorCondition) }
    },
    toEditorCondition(node) {
      const valueJson = node.value === null || node.value === undefined ? '' : (typeof node.value === 'string' ? node.value : JSON.stringify(node.value))
      return { key: `condition-${Date.now()}-${Math.random()}`, fieldPath: node.field, operator: node.operator, valueJson }
    },
    addCondition() { this.conditions.push(emptyCondition()); this.emitBuilderValue() },
    removeCondition(index) { this.conditions.splice(index, 1); this.emitBuilderValue() },
    requiresValue(operator) { return !['EXISTS', 'NOT_EXISTS', 'EMPTY', 'NOT_EMPTY'].includes(operator) },
    operatorChanged(condition) { if (!this.requiresValue(condition.operator)) condition.valueJson = ''; this.emitBuilderValue() },
    parseValue(text, operator) {
      if (!this.requiresValue(operator)) return null
      const trimmed = String(text == null ? '' : text).trim()
      if (!trimmed) return ''
      try { return JSON.parse(trimmed) } catch (error) { return trimmed }
    },
    buildDocument() {
      if (!this.conditions.length) return ''
      const predicates = this.conditions.map(condition => {
        if (!condition.fieldPath) throw new Error('每条触发条件都必须选择 Payload 字段')
        if (!condition.operator) throw new Error('每条触发条件都必须选择运算符')
        return { field: condition.fieldPath, operator: condition.operator, value: this.parseValue(condition.valueJson, condition.operator) }
      })
      const root = predicates.length === 1 ? predicates[0] : { type: this.groupOperator, conditions: predicates }
      return JSON.stringify({ $expression: { version: 1, root } })
    },
    emitBuilderValue() {
      if (this.hydrating || this.rawMode) return
      try {
        const next = this.buildDocument()
        this.rawJson = next
        this.lastEmittedValue = next
        this.$emit('input', next)
        this.$emit('validity-change', true)
      } catch (error) {
        this.$emit('validity-change', false)
      }
    },
    showRawJson() { this.rawJson = this.value || this.buildDocument(); this.rawMode = true; this.malformed = false },
    rawInput(value) { this.rawJson = value; this.malformed = true; this.$emit('dirty-change', true); this.$emit('validity-change', false) },
    repairRawJson() {
      try {
        const parsed = JSON.parse(this.rawJson)
        if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error('条件必须是 JSON 对象')
        const normalized = JSON.stringify(parsed)
        this.rawJson = normalized
        this.malformed = false
        this.lastEmittedValue = normalized
        this.$emit('input', normalized)
        this.$emit('validity-change', true)
        this.$emit('dirty-change', false)
        this.$modal.msgSuccess('条件 JSON 已通过格式校验')
      } catch (error) {
        this.malformed = true
        this.$emit('validity-change', false)
        this.$modal.msgError(error.message || '条件 JSON 格式无效')
      }
    },
    tryVisualMode() {
      try {
        const decoded = this.decodeVisual(JSON.parse(this.rawJson))
        if (!decoded) throw new Error('该表达式包含嵌套或 NOT 条件，请继续使用原始 JSON 编辑')
        this.groupOperator = decoded.groupOperator
        this.conditions = decoded.conditions
        this.rawMode = false
        this.malformed = false
      } catch (error) { this.$modal.msgError(error.message || '无法切换为可视化编辑') }
    },
    validate() {
      if (this.rawMode) {
        if (this.malformed) throw new Error('请先修复异常的条件 JSON')
        const parsed = JSON.parse(this.rawJson)
        if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error('条件必须是 JSON 对象')
        return this.rawJson
      }
      return this.buildDocument()
    }
  }
}
</script>

<style scoped lang="scss">
.condition-builder { padding: 4px 0; }
.condition-builder__toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.condition-builder__hint { margin-left: 10px; color: #94a3b8; font-size: 12px; }
.condition-builder__row { display: grid; grid-template-columns: minmax(150px,1.2fr) 120px minmax(160px,1fr) auto; align-items: center; gap: 8px; margin-bottom: 8px; }
.condition-builder__no-value { padding: 8px 12px; border-radius: 4px; color: #94a3b8; background: #f8fafc; font-size: 12px; }
.condition-builder__raw .el-alert { margin-bottom: 10px; }
.condition-builder__raw-actions { margin-top: 10px; }
.danger-text { color: #ef4444; }
@media (max-width: 720px) { .condition-builder__row { grid-template-columns: 1fr; } .condition-builder__toolbar { align-items: flex-start; flex-direction: column; } }
</style>
