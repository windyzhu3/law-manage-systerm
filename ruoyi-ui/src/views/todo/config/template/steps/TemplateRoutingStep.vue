<template>
  <section data-termination-semantics="END">
    <el-alert title="路由图使用运行时支持的 TASK、DECISION、FORK、JOIN、LOOP、END 节点；发布前由服务端预检可达性。" type="info" :closable="false" show-icon />
    <routing-graph-editor ref="editor" :value="value" :readonly="readonly" :current-version-id="versionId" :versions="versions" :routing-targets="routingTargets" :payload-schema-json="payloadSchemaJson" @input="$emit('input',$event)" @change="$emit('dirty-change')" />
  </section>
</template>
<script>
import RoutingGraphEditor from '../../components/RoutingGraphEditor'
import { listTemplateRoutingTargetCatalog, listTemplateEventCatalog } from '@/api/todo-config'
export default {
  name: 'TemplateRoutingStep', components: { RoutingGraphEditor },
  props: { value: { type: Object, required: true }, readonly: Boolean, versionId: [Number, String], versions: { type: Array, default: () => [] }, eventType: String, payloadVersion: [Number, String] },
  data() { return { routingTargets: [], eventCatalog: [] } },
  computed: { payloadSchemaJson() { const item = this.eventCatalog.find(row => row.eventType === this.eventType && Number(row.payloadVersion || 1) === Number(this.payloadVersion || 1)); return item ? item.payloadSchemaJson : '{}' } },
  created() { this.loadCatalogs() },
  methods: { async loadCatalogs() { const responses = await Promise.all([listTemplateRoutingTargetCatalog(), listTemplateEventCatalog()]); this.routingTargets = responses[0].data || []; this.eventCatalog = responses[1].data || [] }, validate() { return this.$refs.editor ? this.$refs.editor.validate().then(() => true).catch(error => { this.$modal.msgError(error.message); return false }) : Promise.resolve(true) } }
}
</script>
