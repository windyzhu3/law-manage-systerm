<template>
  <div class="definition-builder">
    <el-form-item label="触发事件">
      <el-select :value="model.eventType" :disabled="readonly" filterable @input="change('eventType', $event)">
        <el-option v-for="item in events" :key="eventKey(item)" :label="eventLabel(item)" :value="item.event_type" />
      </el-select>
      <el-select :value="model.payloadVersion" :disabled="readonly" @input="change('payloadVersion', $event)">
        <el-option v-for="version in versions" :key="version" :label="`载荷版本 ${version}`" :value="version" />
      </el-select>
    </el-form-item>
    <el-form-item label="事件条件">
      <template v-if="simpleCondition">
        <el-input :value="model.condition.field" :disabled="readonly" placeholder="载荷字段" @input="changeCondition('field', $event)" />
        <el-select :value="model.condition.operator" :disabled="readonly" @input="changeCondition('operator', $event)">
          <el-option v-for="operator in operators" :key="operator" :label="operator" :value="operator" />
        </el-select>
        <el-input :value="model.condition.value" :disabled="readonly" placeholder="比较值" @input="changeCondition('value', $event)" />
      </template>
      <el-alert v-else title="此版本包含组合条件，保留原有配置；可在后续条件编辑器中调整。" type="info" :closable="false" />
    </el-form-item>
  </div>
</template>
<script>
import { listTodoEventCatalog } from '@/api/todo-definition'
export default {
  name: 'EventConditionBuilder',
  props: { value: { type: Object, default: () => ({}) }, readonly: Boolean },
  data() { return { events: [], operators: ['EQ', 'NE', 'IN', 'NOT_IN', 'GT', 'GTE', 'LT', 'LTE', 'EXISTS', 'EMPTY', 'NOT_EMPTY'] } },
  computed: {
    model() { return { eventType: '', payloadVersion: 1, condition: {}, ...this.value } },
    versions() { const versions = this.events.filter(item => item.event_type === this.model.eventType).map(item => Number(item.payload_version)); return versions.length ? versions : [this.model.payloadVersion] },
    simpleCondition() { const condition = this.model.condition || {}; return !condition.operator || !Array.isArray(condition.conditions) }
  },
  created() { listTodoEventCatalog().then(response => { this.events = response.data || [] }) },
  methods: {
    eventKey(item) { return `${item.event_type}:${item.payload_version}` },
    eventLabel(item) { return `${item.event_type}（载荷 v${item.payload_version}）` },
    change(key, value) { const next = { ...this.model, [key]: value }; if (key === 'eventType') { const version = this.events.find(item => item.event_type === value); if (version) next.payloadVersion = Number(version.payload_version) } this.$emit('input', next) },
    changeCondition(key, value) { this.$emit('input', { ...this.model, condition: { ...(this.model.condition || {}), [key]: value } }) }
  }
}
</script>
<style scoped>.definition-builder .el-select,.definition-builder .el-input{margin-right:8px;margin-bottom:8px;max-width:230px}</style>
