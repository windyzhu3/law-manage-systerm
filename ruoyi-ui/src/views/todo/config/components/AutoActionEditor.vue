<template>
  <section class="definition-section" data-testid="auto-action-editor">
    <header>
      <div><h4>自动动作</h4><small>动作类型及参数来自服务端已注册能力</small></div>
      <el-button size="mini" :disabled="readonly || loading || !catalog.length" @click="add">新增动作</el-button>
    </header>
    <el-alert v-if="catalogError" :title="catalogError" type="error" :closable="false" />
    <div v-if="catalog.length" class="catalog-tags"><span>服务端能力：</span><el-tag v-for="item in catalog" :key="item.actionType" size="mini">{{ item.actionType }}</el-tag></div>
    <el-table v-loading="loading" :data="actions" size="mini" border>
      <el-table-column label="规则 Key" min-width="130"><template slot-scope="{ row }"><el-input v-model.trim="row.config.ruleKey" :disabled="readonly" maxlength="96" @change="commit" /></template></el-table-column>
      <el-table-column label="能力" min-width="150">
        <template slot-scope="{ row }">
          <el-select v-model="row.config.actionType" data-testid="auto-action-capability" :disabled="readonly" @change="selectCapability(row, $event)">
            <el-option v-for="item in catalog" :key="item.actionType" :label="item.actionType" :value="item.actionType" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="触发点" width="120"><template slot-scope="{ row }"><el-select v-model="row.config.triggerAt" :disabled="readonly" @change="commit"><el-option v-for="item in triggerOptions(row)" :key="item" :label="item" :value="item" /></el-select></template></el-table-column>
      <el-table-column label="重试与动作参数" min-width="280">
        <template slot-scope="{ row }">
          <div v-for="field in fields(row)" :key="field.name" class="field-line">
            <label>{{ field.label || field.name }}</label>
            <el-input-number v-if="field.type === 'number'" v-model="row.config[field.name]" :data-testid="`auto-action-field-${field.name}`" :disabled="readonly" :min="field.min || 1" controls-position="right" @change="commit" />
            <el-input v-else v-model.trim="row.config[field.name]" :data-testid="`auto-action-field-${field.name}`" :disabled="readonly" @change="commit" />
          </div>
          <el-input :value="json(row.config.precondition)" :disabled="readonly" placeholder="前置条件 JSON（可选）" @change="setPrecondition(row, $event)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="65"><template slot-scope="{ $index }"><el-button type="text" :disabled="readonly" @click="remove($index)">删除</el-button></template></el-table-column>
    </el-table>
    <el-alert v-for="message in localErrors" :key="message" :title="message" type="warning" :closable="false" />
    <el-alert v-if="jsonError" :title="jsonError" type="error" :closable="false" />
  </section>
</template>

<script>
import { listTodoAutoActionCapabilities } from '@/api/todo-definition'

function clone(value) { return JSON.parse(JSON.stringify(value == null ? [] : value)) }
function normalize(value) { return (Array.isArray(value) ? clone(value) : []).map(item => ({ config: { ...(item.config || {}) } })) }

export default {
  name: 'AutoActionEditor',
  props: { value: { type: Array, default: () => [] }, readonly: Boolean },
  data() { return { actions: normalize(this.value), catalog: [], loading: false, catalogError: '', jsonError: '' } },
  computed: {
    localErrors() {
      const errors = []
      const keys = this.actions.map(item => item.config.ruleKey).filter(Boolean)
      if (new Set(keys).size !== keys.length) errors.push('自动动作规则 Key 必须唯一')
      this.actions.forEach((item, index) => {
        const row = item.config
        if (!row.ruleKey || row.ruleKey.length > 96) errors.push(`第 ${index + 1} 条动作的规则 Key 无效`)
        if (!this.capability(row.actionType)) errors.push(`第 ${index + 1} 条动作必须选择服务端能力`)
        if (row.capability !== row.actionType) errors.push(`第 ${index + 1} 条动作的 capability 必须与 actionType 一致`)
        if (!this.triggerOptions(item).includes(row.triggerAt)) errors.push(`第 ${index + 1} 条动作的触发点无效`)
        this.fields(item).forEach(field => {
          const value = row[field.name]
          if (field.type !== 'number') {
            if (field.required && !String(value || '').trim()) errors.push(`${field.name} is required`)
            return
          }
          if (field.required && !(Number(value) >= Number(field.min || 1))) errors.push(`${field.name} 必须填写正整数`)
          if (value != null && value !== '' && !(Number(value) >= Number(field.min || 1))) errors.push(`${field.name} 必须大于等于 ${field.min || 1}`)
        })
      })
      return [...new Set(errors)]
    }
  },
  created() { this.loadCatalog() },
  watch: { value: { deep: true, handler(value) { this.actions = normalize(value) } } },
  methods: {
    loadCatalog() {
      this.loading = true; this.catalogError = ''
      return listTodoAutoActionCapabilities().then(response => { this.catalog = response.data || []; if (!this.catalog.length) this.catalogError = '服务端没有注册可用的自动动作能力' })
        .catch(error => { this.catalogError = error.msg || error.message || '自动动作能力目录加载失败'; this.catalog = [] })
        .finally(() => { this.loading = false })
    },
    capability(type) { return this.catalog.find(item => item.actionType === type) },
    triggerOptions(row) { const item = this.capability(row.config.actionType); return item ? (item.triggerAt || []) : [] },
    fields(row) { const item = this.capability(row.config.actionType); return item ? [...(item.retryFields || []), ...(item.requiredFields || [])] : [] },
    json(value) { return value && Object.keys(value).length ? JSON.stringify(value) : '' },
    add() {
      const item = this.catalog[0]
      if (!item) return
      const row = { config: { ruleKey: `auto_${this.actions.length + 1}`, actionType: item.actionType, capability: item.capability || item.actionType, triggerAt: (item.triggerAt || [])[0] } }
      this.applyDefaults(row, item); this.actions.push(row); this.commit()
    },
    selectCapability(row, type) {
      const item = this.capability(type)
      this.$set(row.config, 'capability', item ? (item.capability || type) : '')
      this.$set(row.config, 'triggerAt', item && item.triggerAt && item.triggerAt[0])
      this.applyDefaults(row, item); this.commit()
    },
    applyDefaults(row, item) {
      if (!item) return
      ;[...(item.retryFields || []), ...(item.requiredFields || [])].forEach(field => {
        if (row.config[field.name] == null && field.defaultValue != null) this.$set(row.config, field.name, field.defaultValue)
      })
      const allowed = new Set([...(item.retryFields || []), ...(item.requiredFields || [])].map(field => field.name))
      if (!allowed.has('targetOwnerId')) this.$delete(row.config, 'targetOwnerId')
    },
    setPrecondition(row, value) {
      this.jsonError = ''
      if (!String(value || '').trim()) { this.$delete(row.config, 'precondition'); this.commit(); return }
      try { this.$set(row.config, 'precondition', JSON.parse(value)); this.commit() } catch (_) { this.jsonError = 'precondition 不是有效 JSON' }
    },
    remove(index) { this.actions.splice(index, 1); this.commit() },
    commit() { this.$emit('input', clone(this.actions)); this.$emit('change') },
    validate() {
      if (this.actions.length && (this.catalogError || !this.catalog.length)) return Promise.reject(new Error(this.catalogError || '自动动作能力目录不可用'))
      return this.jsonError || this.localErrors.length ? Promise.reject(new Error(this.jsonError || this.localErrors[0])) : Promise.resolve()
    }
  }
}
</script>

<style scoped>
.definition-section{margin:18px 0;padding:14px;border:1px solid #dfe6ef;border-radius:8px}.definition-section header{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}.definition-section h4{margin:0 0 8px}.definition-section small{color:#64748b}.catalog-tags{display:flex;align-items:center;gap:6px;margin-bottom:10px;color:#64748b;font-size:12px}.field-line{display:flex;align-items:center;gap:8px;margin:3px 0}.field-line label{width:95px;color:#64748b;font-size:12px}.definition-section .el-alert{margin-top:8px}
</style>
