<template>
  <section class="journey-step routing-step">
    <header class="journey-step__header">
      <span class="journey-step__eyebrow">第六步 · 后续路由</span>
      <h2>这张待办完成后，业务应该走向哪里？</h2>
      <p>使用业务结果配置下一张待办或结束；复杂并行场景可选择汇合方式，技术拓扑保留在高级设置。</p>
    </header>

    <el-alert
      v-if="legacyGraph"
      title="当前模板使用了高级拓扑"
      description="原有路由图已完整保留。只有在下方新增或修改业务结果时，才会转换为标准业务路由。"
      type="info"
      :closable="false"
      show-icon
    />

    <business-routing-editor
      :rows="outcomes"
      :options="routingOptions"
      :routing-targets="resources.routingTargets || []"
      :fields="resources.fields || []"
      :outcome-set="resources.businessOutcomeSet || {}"
      :current-version-id="currentVersionId"
      :business-type="businessType"
      :readonly="readonly"
      @change="businessChanged"
      @issue-change="$emit('issue-change', $event)"
    />

    <el-alert
      v-if="Number(step.issueCount) > 0"
      class="routing-step__issues"
      :title="`服务端发现 ${step.issueCount} 个路由问题`"
      description="无目标、循环无退出、无效版本和不可达分支以右侧服务端检查结果为准，请按提示修复后再发布。"
      type="warning"
      :closable="false"
      show-icon
    />

    <el-collapse class="routing-advanced">
      <el-collapse-item name="graph">
        <template slot="title"><i class="el-icon-share" />高级设置：拓扑图</template>
        <p>仅供熟悉节点、连线和分支汇合的管理员使用。普通业务路由请在上方维护。</p>
        <routing-graph-editor
          :value="config"
          :readonly="readonly"
          :current-version-id="currentVersionId"
          :routing-targets="resources.routingTargets || []"
          :payload-schema-json="payloadSchemaJson"
          @input="advancedChanged"
        />
      </el-collapse-item>
    </el-collapse>
  </section>
</template>

<script>
import RoutingGraphEditor from '../../components/RoutingGraphEditor'
import BusinessRoutingEditor from '../components/BusinessRoutingEditor'
import { buildBusinessRoutingPatch } from '../journey-step-model'

export default {
  name: 'RoutingStep',
  components: { RoutingGraphEditor, BusinessRoutingEditor },
  props: {
    step: { type: Object, default: () => ({}) },
    value: { type: Object, default: () => ({}) },
    resources: { type: Object, default: () => ({}) },
    currentVersionId: [Number, String],
    businessType: String,
    readonly: Boolean
  },
  computed: {
    config() { return this.value.config || {} },
    outcomes() { return Array.isArray(this.config.businessOutcomes) ? this.config.businessOutcomes : [] },
    routingOptions() { return this.config.businessRouting || { mode: 'SEQUENTIAL', joinMode: 'ALL' } },
    legacyGraph() {
      return !this.outcomes.length &&
        (Array.isArray(this.config.nodes) && this.config.nodes.length > 0)
    },
    payloadSchemaJson() {
      const properties = {}
      ;(this.resources.fields || []).forEach(field => {
        properties[field.code] = { type: field.type || 'string', title: field.name || field.code }
      })
      return JSON.stringify({ type: 'object', properties })
    }
  },
  methods: {
    businessChanged(rows, options) {
      this.$emit('change', buildBusinessRoutingPatch(rows, {
        ...options,
        currentVersionId: this.currentVersionId
      }, this.value))
    },
    advancedChanged(config) { this.$emit('change', { config }) },
    focusField() {}
  }
}
</script>

<style scoped lang="scss">
.journey-step__header {
  margin-bottom: 22px;
  h2 { margin: 5px 0 8px; font-size: 22px; line-height: 32px; color: #0B2A55; }
  p { margin: 0; font-size: 14px; line-height: 23px; color: #65758A; }
}
.journey-step__eyebrow { font-size: 12px; color: #C89A3D; }
.routing-step > .el-alert { margin-bottom: 14px; }
.routing-step__issues { margin-top: 14px; }
.routing-advanced {
  margin-top: 14px;

  ::v-deep .el-collapse-item__header {
    padding: 0 14px;
    color: #0B2A55;
    background: #F7F9FC;
  }

  ::v-deep .el-collapse-item__content { padding: 14px; }
  .el-icon-share { margin-right: 7px; color: #C89A3D; }
  p { margin: 0 0 10px; color: #65758A; }
}
</style>
