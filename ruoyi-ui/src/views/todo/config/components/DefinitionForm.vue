<template><div data-testid="definition-form">
  <el-form v-if="!definitionError" ref="form" :model="form" :rules="rules" label-width="110px">
    <event-condition-builder v-model="form.event" :readonly="readonly" />
    <owner-rule-builder v-model="form.owner.config" :readonly="readonly" />
    <dod-form-builder v-model="form.dod.config" :readonly="readonly" />
    <sla-rule-builder v-model="form.sla.config" :readonly="readonly" />
    <routing-graph-editor ref="routing" v-model="form.routing.config" :readonly="readonly" :current-version-id="currentVersionId" :versions="versions" />
    <auto-action-editor ref="autoActions" v-model="form.autoActions" :readonly="readonly" />
    <el-alert title="已发布版本为只读快照；请复制为新草稿后再调整。" type="info" :closable="false" />
    <el-form-item label="规则预览"><pre>{{ preview }}</pre></el-form-item>
  </el-form>
  <el-alert v-else :title="definitionError" type="error" :closable="false" />
</div></template>
<script>
import EventConditionBuilder from './EventConditionBuilder'
import OwnerRuleBuilder from './OwnerRuleBuilder'
import DodFormBuilder from './DodFormBuilder'
import SlaRuleBuilder from './SlaRuleBuilder'
import RoutingGraphEditor from './RoutingGraphEditor'
import AutoActionEditor from './AutoActionEditor'
import { hydrateDefinition, serializeDefinition, toDraftPayload, definitionSourceToken } from '../definition-codec'

function definitionState(value) {
  try { return { form: hydrateDefinition(value), expectedDefinitionJson: definitionSourceToken(value), definitionError: null } } catch (error) {
    return { form: hydrateDefinition({}), expectedDefinitionJson: null, definitionError: `定义快照不可编辑：${error.message}` }
  }
}

export default {
  name: 'DefinitionForm',
  components: { EventConditionBuilder, OwnerRuleBuilder, DodFormBuilder, SlaRuleBuilder, RoutingGraphEditor, AutoActionEditor },
  props: { value: { type: Object, default: () => ({}) }, readonly: Boolean, currentVersionId: [Number, String], versions: { type: Array, default: () => [] } },
  data() {
    const state = definitionState(this.value)
    return {
      ...state, sourceToken: JSON.stringify(serializeDefinition(state.form)), hydrating: true,
      rules: {
        'event.eventType': [{ required: true, message: '请选择触发事件' }],
        'sla.config.minutes': [{ required: true, type: 'number', min: 1, message: 'SLA 必须大于 0' }]
      }
    }
  },
  computed: { preview() { return JSON.stringify(serializeDefinition(this.form), null, 2) } },
  created() { if (this.definitionError) this.$emit('invalid-definition', this.definitionError) },
  mounted() { this.$nextTick(() => { this.sourceToken = JSON.stringify(serializeDefinition(this.form)); this.hydrating = false }) },
  watch: {
    value: { deep: true, handler(next) {
      const state = definitionState(next)
      this.hydrating = true; this.form = state.form; this.expectedDefinitionJson = state.expectedDefinitionJson; this.definitionError = state.definitionError
      this.sourceToken = JSON.stringify(serializeDefinition(state.form)); this.$emit('invalid-definition', this.definitionError)
      this.$nextTick(() => { this.hydrating = false })
    } },
    form: { deep: true, handler(value) {
      if (this.hydrating) return
      const token = JSON.stringify(serializeDefinition(value))
      if (token !== this.sourceToken) { this.sourceToken = token; this.$emit('changed', token) }
    } }
  },
  methods: {
    validate() {
      if (this.definitionError) return Promise.reject(new Error(this.definitionError))
      if (this.readonly) return Promise.reject(new Error('published definition is read-only'))
      const formValidation = new Promise((resolve, reject) => this.$refs.form.validate(ok => ok ? resolve() : reject(new Error('定义基础字段无效'))))
      return Promise.all([formValidation, this.$refs.routing.validate(), this.$refs.autoActions.validate()])
        .then(() => toDraftPayload(this.form, this.expectedDefinitionJson))
    }
  }
}
</script>
<style scoped>pre{max-height:180px;overflow:auto;padding:10px;background:#f8fafc;border-radius:6px}</style>
