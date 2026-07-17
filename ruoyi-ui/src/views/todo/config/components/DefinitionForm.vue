<template>
  <el-form ref="form" :model="form" :rules="rules" label-width="110px">
    <event-condition-builder v-model="form.event" :readonly="readonly" />
    <owner-rule-builder v-model="form.owner.config" :readonly="readonly" />
    <dod-form-builder v-model="form.dod.config" :readonly="readonly" />
    <sla-rule-builder v-model="form.sla.config" :readonly="readonly" />
    <el-form-item label="下一版本">
      <el-input-number v-model="form.routing.config.templateVersionId" :disabled="readonly" :min="1" />
    </el-form-item>
    <el-alert title="已发布版本为只读快照；请复制为新草稿后再调整。" type="info" :closable="false" />
    <el-form-item label="规则预览"><pre>{{ preview }}</pre></el-form-item>
  </el-form>
</template>
<script>
import EventConditionBuilder from './EventConditionBuilder'
import OwnerRuleBuilder from './OwnerRuleBuilder'
import DodFormBuilder from './DodFormBuilder'
import SlaRuleBuilder from './SlaRuleBuilder'
import { hydrateDefinition, serializeDefinition, toDraftPayload } from '../definition-codec'

export default {
  name: 'DefinitionForm',
  components: { EventConditionBuilder, OwnerRuleBuilder, DodFormBuilder, SlaRuleBuilder },
  props: { value: { type: Object, default: () => ({}) }, readonly: Boolean },
  data() {
    return {
      form: hydrateDefinition(this.value),
      expectedDefinitionJson: this.value.definition_json || this.value.definitionJson || null,
      rules: {
        'event.eventType': [{ required: true, message: '请选择触发事件' }],
        'sla.config.minutes': [{ required: true, type: 'number', min: 1, message: 'SLA 必须大于 0' }]
      }
    }
  },
  computed: { preview() { return JSON.stringify(serializeDefinition(this.form), null, 2) } },
  watch: { value: { deep: true, handler(next) { this.form = hydrateDefinition(next); this.expectedDefinitionJson = next.definition_json || next.definitionJson || null } } },
  methods: {
    validate() {
      if (this.readonly) return Promise.reject(new Error('published definition is read-only'))
      return new Promise((resolve, reject) => this.$refs.form.validate(ok => ok ? resolve(toDraftPayload(this.form, this.expectedDefinitionJson)) : reject(new Error('invalid'))))
    }
  }
}
</script>
<style scoped>pre{max-height:180px;overflow:auto;padding:10px;background:#f8fafc;border-radius:6px}</style>
