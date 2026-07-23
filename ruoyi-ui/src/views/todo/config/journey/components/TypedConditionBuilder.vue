<template>
  <section class="typed-condition" aria-label="可视化触发条件">
    <el-alert
      v-if="unsupported"
      title="当前条件包含普通配置暂不支持的深层结构"
      description="原条件已完整保留。你可以联系配置管理员处理，或确认后重新建立两层以内的业务条件。"
      type="warning"
      :closable="false"
      show-icon
    >
      <el-button v-if="!readonly" slot="default" type="text" @click="resetUnsupported">重新建立条件</el-button>
    </el-alert>

    <template v-else>
      <header class="typed-condition__header">
        <div>
          <h3>满足什么条件时创建待办？</h3>
          <p>字段和可选判断均来自所选事件，直接按业务名称选择即可。</p>
        </div>
        <el-radio-group
          v-model="document.$expression.root.type"
          :disabled="readonly"
          size="small"
          @change="commit"
        >
          <el-radio-button label="AND">全部满足</el-radio-button>
          <el-radio-button label="OR">任一满足</el-radio-button>
        </el-radio-group>
      </header>

      <el-alert
        v-if="!fields.length"
        title="当前事件没有可选业务字段"
        description="请先维护事件字段，再返回继续配置。"
        type="warning"
        :closable="false"
        show-icon
      />

      <div v-if="root.conditions.length" class="typed-condition__body">
        <template v-for="(node, index) in root.conditions">
          <section v-if="isGroup(node)" :key="nodeKey(node, index)" class="condition-group">
            <header class="condition-group__header">
              <span>条件组 {{ index + 1 }}</span>
              <el-radio-group v-model="node.type" :disabled="readonly" size="mini" @change="commit">
                <el-radio-button label="AND">全部满足</el-radio-button>
                <el-radio-button label="OR">任一满足</el-radio-button>
              </el-radio-group>
              <el-button v-if="!readonly" type="text" class="is-danger" @click="removeNode([index])">删除组</el-button>
            </header>
            <condition-row
              v-for="(child, childIndex) in node.conditions"
              :key="nodeKey(child, childIndex)"
              :ref="`condition-${child.field || childIndex}`"
              :node="child"
              :fields="fields"
              :readonly="readonly"
              :focused="focusedField === child.field"
              @change="replaceNode([index, childIndex], $event)"
              @remove="removeNode([index, childIndex])"
            />
            <el-button
              v-if="!readonly"
              plain
              size="mini"
              icon="el-icon-plus"
              :disabled="!fields.length"
              @click="addRule([index])"
            >添加组内条件</el-button>
          </section>
          <condition-row
            v-else
            :key="nodeKey(node, index)"
            :ref="`condition-${node.field || index}`"
            :node="node"
            :fields="fields"
            :readonly="readonly"
            :focused="focusedField === node.field"
            @change="replaceNode([index], $event)"
            @remove="removeNode([index])"
          />
        </template>
      </div>
      <el-empty v-else description="未设置条件：每次收到该业务事件都会创建待办" :image-size="72" />

      <footer v-if="!readonly" class="typed-condition__actions">
        <el-button plain size="small" icon="el-icon-plus" :disabled="!fields.length" @click="addRule([])">添加条件</el-button>
        <el-button plain size="small" icon="el-icon-folder-add" :disabled="!fields.length" @click="addGroup">添加条件组</el-button>
      </footer>
      <p class="typed-condition__summary">{{ summary }}</p>
    </template>
  </section>
</template>

<script>
import {
  operatorsForField,
  controlForField,
  emptyConditionDocument,
  normalizeConditionDocument,
  addConditionGroup,
  addPredicate,
  replaceConditionNode,
  removeConditionNode
} from '../journey-step-model'

const clone = value => JSON.parse(JSON.stringify(value))
const noValueOperators = ['EXISTS', 'NOT_EXISTS', 'EMPTY', 'NOT_EMPTY']

const ConditionRow = {
  name: 'ConditionRow',
  props: {
    node: { type: Object, required: true },
    fields: { type: Array, default: () => [] },
    readonly: Boolean,
    focused: Boolean
  },
  computed: {
    draft() { return clone(this.node) },
    field() { return this.fields.find(item => item.code === this.node.field) || null },
    operators() { return operatorsForField(this.field) },
    control() { return controlForField(this.field, this.node.operator) },
    multiple() { return ['IN', 'NOT_IN'].includes(this.node.operator) }
  },
  methods: {
    changeField(field) {
      const descriptor = this.fields.find(item => item.code === field)
      const operator = (operatorsForField(descriptor)[0] || {}).value || 'EQ'
      this.$emit('change', { field, operator, value: this.initialValue(descriptor, operator) })
    },
    changeOperator(operator) {
      this.$emit('change', {
        ...clone(this.node),
        operator,
        value: noValueOperators.includes(operator) ? null : this.initialValue(this.field, operator, this.node.value)
      })
    },
    changeValue(value) {
      this.$emit('change', { ...clone(this.node), value })
    },
    initialValue(field, operator, current) {
      if (noValueOperators.includes(operator)) return null
      if (['IN', 'NOT_IN'].includes(operator)) return Array.isArray(current) ? current : []
      if (current !== null && current !== undefined && !Array.isArray(current)) return current
      const control = controlForField(field, operator)
      if (control === 'BOOLEAN') return false
      if (control === 'NUMBER') return 0
      if (control === 'SELECT') return (field.options || [])[0] || ''
      return ''
    }
  },
  render(h) {
    const option = item => h('el-option', {
      key: String(item.value !== undefined ? item.value : item),
      props: {
        label: item.label !== undefined ? item.label : String(item),
        value: item.value !== undefined ? item.value : item
      }
    })
    const fieldSelect = h('el-select', {
      props: { value: this.node.field, disabled: this.readonly, filterable: true, placeholder: '选择业务字段' },
      on: { change: this.changeField }
    }, this.fields.map(field => option({ label: `${field.name} · ${field.type}`, value: field.code })))
    const operatorSelect = h('el-select', {
      props: { value: this.node.operator, disabled: this.readonly || !this.field, placeholder: '选择判断方式' },
      on: { change: this.changeOperator }
    }, this.operators.map(option))
    let valueControl
    if (this.control === 'NONE') {
      valueControl = h('span', { class: 'condition-row__no-value' }, '不需要填写比较值')
    } else if (this.control === 'SELECT') {
      valueControl = h('el-select', {
        props: {
          value: this.node.value,
          disabled: this.readonly,
          filterable: true,
          multiple: this.multiple,
          placeholder: '选择业务值'
        },
        on: { change: this.changeValue }
      }, (this.field.options || []).map(option))
    } else if (this.control === 'BOOLEAN') {
      valueControl = h('el-select', {
        props: { value: this.node.value, disabled: this.readonly, placeholder: '请选择' },
        on: { change: this.changeValue }
      }, [option({ label: '是', value: true }), option({ label: '否', value: false })])
    } else if (this.control === 'NUMBER') {
      const integer = String((this.field && this.field.type) || '').toLowerCase() === 'integer'
      valueControl = h('el-input-number', {
        props: {
          value: this.node.value,
          disabled: this.readonly,
          controlsPosition: 'right',
          step: integer ? 1 : 0.1,
          precision: integer ? 0 : undefined
        },
        on: { change: this.changeValue }
      })
    } else if (this.control === 'DATE') {
      valueControl = h('el-date-picker', {
        props: {
          value: this.node.value,
          disabled: this.readonly,
          type: String(this.field.type).toLowerCase() === 'date' ? 'date' : 'datetime',
          valueFormat: String(this.field.type).toLowerCase() === 'date' ? 'yyyy-MM-dd' : 'yyyy-MM-ddTHH:mm:ss',
          placeholder: '选择日期'
        },
        on: { input: this.changeValue }
      })
    } else {
      valueControl = h('el-input', {
        props: { value: this.node.value, disabled: this.readonly, placeholder: this.field ? `填写${this.field.name}` : '请先选择字段' },
        on: { input: this.changeValue }
      })
    }
    return h('div', {
      class: ['condition-row', this.focused ? 'is-focused' : ''],
      attrs: { 'data-condition-field': this.node.field || '' }
    }, [
      fieldSelect,
      operatorSelect,
      valueControl,
      this.readonly ? null : h('el-button', {
        class: 'is-danger',
        props: { type: 'text', icon: 'el-icon-delete' },
        on: { click: () => this.$emit('remove') }
      }, '删除')
    ])
  }
}

export default {
  name: 'TypedConditionBuilder',
  components: { ConditionRow },
  props: {
    value: { type: Object, default: () => ({}) },
    fields: { type: Array, default: () => [] },
    readonly: Boolean
  },
  data() {
    return {
      document: emptyConditionDocument(),
      unsupported: false,
      syncing: false,
      focusedField: ''
    }
  },
  computed: {
    root() { return this.document.$expression.root },
    summary() {
      if (!this.root.conditions.length) return '当前规则：业务事件到达后直接创建待办。'
      const describe = node => {
        if (this.isGroup(node)) {
          return `（${node.conditions.map(describe).join(node.type === 'AND' ? ' 且 ' : ' 或 ')}）`
        }
        const field = this.fields.find(item => item.code === node.field)
        const operator = operatorsForField(field).find(item => item.value === node.operator)
        const value = noValueOperators.includes(node.operator) ? '' : ` ${Array.isArray(node.value) ? node.value.join('、') : node.value}`
        return `${field ? field.name : '未选择字段'} ${operator ? operator.label : '未选择判断'}${value}`
      }
      return `当前规则：${this.root.conditions.map(describe).join(this.root.type === 'AND' ? '，并且 ' : '，或者 ')}。`
    }
  },
  watch: {
    value: {
      immediate: true,
      deep: true,
      handler(value) {
        if (this.syncing) return
        const normalized = normalizeConditionDocument(value)
        this.document = normalized.document
        this.unsupported = !normalized.supported
      }
    }
  },
  methods: {
    isGroup(node) { return Boolean(node && ['AND', 'OR'].includes(node.type) && Array.isArray(node.conditions)) },
    nodeKey(node, index) { return `${node.type || node.field || 'condition'}-${index}` },
    commit() {
      this.syncing = true
      this.$emit('input', clone(this.document))
      this.$nextTick(() => { this.syncing = false })
    },
    replaceNode(path, node) {
      this.document = replaceConditionNode(this.document, path, node)
      this.commit()
    },
    removeNode(path) {
      this.document = removeConditionNode(this.document, path)
      this.commit()
    },
    addRule(path) {
      this.document = addPredicate(this.document, path)
      const field = this.fields[0]
      const operator = (operatorsForField(field)[0] || {}).value || 'EQ'
      const root = this.document.$expression.root
      const group = path.reduce((node, index) => node.conditions[index], root)
      const index = group.conditions.length - 1
      this.document = replaceConditionNode(this.document, [...path, index], {
        field: field.code,
        operator,
        value: this.defaultValue(field, operator)
      })
      this.commit()
    },
    addGroup() {
      try {
        this.document = addConditionGroup(this.document, [])
        const root = this.document.$expression.root
        const groupIndex = root.conditions.length - 1
        const field = this.fields[0]
        const operator = (operatorsForField(field)[0] || {}).value || 'EQ'
        this.document = replaceConditionNode(this.document, [groupIndex, 0], {
          field: field.code,
          operator,
          value: this.defaultValue(field, operator)
        })
        this.commit()
      } catch (error) {
        this.$modal.msgWarning(error.message || '普通配置最多支持两层条件组')
      }
    },
    defaultValue(field, operator) {
      if (noValueOperators.includes(operator)) return null
      if (['IN', 'NOT_IN'].includes(operator)) return []
      const control = controlForField(field, operator)
      if (control === 'BOOLEAN') return false
      if (control === 'NUMBER') return 0
      if (control === 'SELECT') return (field.options || [])[0] || ''
      return ''
    },
    resetUnsupported() {
      this.$confirm('重新建立条件会清空当前无法展示的条件内容，是否继续？', '重新建立触发条件', {
        type: 'warning',
        confirmButtonText: '确认重建',
        cancelButtonText: '保留原条件'
      }).then(() => {
        this.document = emptyConditionDocument()
        this.unsupported = false
        this.commit()
      }).catch(() => {})
    },
    focusField(path) {
      this.focusedField = path || ''
      this.$nextTick(() => {
        const target = this.$el.querySelector(`[data-condition-field="${String(path || '').replace(/"/g, '\\"')}"]`)
        if (target && target.scrollIntoView) target.scrollIntoView({ behavior: 'smooth', block: 'center' })
      })
      setTimeout(() => { this.focusedField = '' }, 1800)
    }
  }
}
</script>

<style scoped lang="scss">
.typed-condition {
  color: #34465B;
}

.typed-condition__header,
.condition-group__header,
.typed-condition__actions {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.typed-condition__header {
  margin-bottom: 18px;

  h3 {
    margin: 0;
    font-size: 16px;
    line-height: 24px;
    color: #0B2A55;
  }

  p {
    margin: 4px 0 0;
    font-size: 13px;
    line-height: 20px;
    color: #65758A;
  }
}

.typed-condition__body {
  display: grid;
  gap: 10px;
  margin-top: 16px;
}

::v-deep .condition-row {
  display: grid;
  grid-template-columns: minmax(180px, 1.2fr) minmax(130px, .8fr) minmax(180px, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 10px;
  background: #F7F9FC;
  border: 1px solid #E0E6ED;
  border-radius: 8px;
  transition: border-color .2s, background-color .2s;

  &.is-focused {
    background: #FFF9EC;
    border-color: #C89A3D;
  }

  .el-select,
  .el-date-editor,
  .el-input-number {
    width: 100%;
  }
}

::v-deep .condition-row__no-value {
  padding: 9px 12px;
  font-size: 13px;
  color: #65758A;
  background: #FFFFFF;
  border-radius: 8px;
}

.condition-group {
  padding: 14px;
  border: 1px solid #C9D5E3;
  border-left: 3px solid #C89A3D;
  border-radius: 8px;

  ::v-deep .condition-row {
    margin: 10px 0;
  }
}

.condition-group__header {
  font-size: 13px;
  color: #0B2A55;
}

.typed-condition__actions {
  justify-content: flex-start;
  margin-top: 14px;
}

.typed-condition__summary {
  padding: 12px 14px;
  margin: 16px 0 0;
  font-size: 13px;
  line-height: 21px;
  color: #3F536A;
  background: #F0F4F8;
  border-radius: 8px;
}

.is-danger {
  color: #C43D3D;
}

@media (max-width: 760px) {
  .typed-condition__header {
    align-items: stretch;
    flex-direction: column;
  }

  ::v-deep .condition-row {
    grid-template-columns: 1fr;
  }
}
</style>
