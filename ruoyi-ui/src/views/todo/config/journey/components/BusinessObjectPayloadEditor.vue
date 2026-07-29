<template>
  <section class="payload-editor" data-testid="business-object-payload-editor">
    <div class="payload-editor__heading">
      <div>
        <h3>选择测试对象</h3>
        <p>只展示当前账号有权查看的真实业务对象；无匹配结果时可选择只读样例。</p>
      </div>
      <el-tag v-if="selected && selected.sample" type="warning">只读样例</el-tag>
    </div>

    <div class="payload-editor__selector">
      <el-select
        :value="selectedId"
        filterable
        remote
        clearable
        placeholder="按编号或名称搜索业务对象"
        :remote-method="query => $emit('search', query)"
        :loading="loading"
        :disabled="readonly"
        data-testid="business-object-selector"
        @change="$emit('select', $event)"
      >
        <el-option
          v-for="item in objects"
          :key="item.businessId"
          :label="objectLabel(item)"
          :value="item.businessId"
        >
          <span>{{ item.businessName || item.businessNo }}</span>
          <small>{{ item.businessNo }}</small>
          <el-tag v-if="item.sample" size="mini" type="warning">只读样例</el-tag>
        </el-option>
      </el-select>
      <el-button
        plain
        icon="el-icon-magic-stick"
        :disabled="readonly"
        :loading="hydrating"
        @click="$emit('sample-load')"
      >
        一键加载只读样例
      </el-button>
    </div>

    <el-alert
      v-if="selected && selected.sample"
      class="payload-editor__sample"
      title="这是只读样例，仅用于验证配置，绝不会生成或写入运行时待办。"
      type="warning"
      :closable="false"
      show-icon
    />

    <template v-if="hydration">
      <span v-if="hasSensitiveFields" class="payload-editor__masked" aria-label="敏感字段已脱敏">••••••</span>
      <creation-validation-panel :coverage="coverage" :issues="blockingIssues" />
      <event-input-panel :fields="eventInputFields" :readonly="readonly" @override="$emit('override', $event)" />
      <todo-creation-preview :template-name="templateName" :fields="eventInputFields" :ready="coverage === 100 && !blockingIssues.length" />
      <advanced-payload-override :fields="advancedFields" />
    </template>

    <el-empty v-else-if="selected && !loading" description="选择对象后加载服务器载荷" :image-size="72">
      <el-button type="primary" plain :loading="hydrating" @click="$emit('hydrate')">加载测试载荷</el-button>
    </el-empty>
  </section>
</template>

<script>
import {
  applyHydrationOverrides,
  buildHydratedPayloadRows,
  creationCoverage,
  remainingHydrationBlockers
} from '../journey-step-model'
import CreationValidationPanel from './CreationValidationPanel'
import EventInputPanel from './EventInputPanel'
import TodoCreationPreview from './TodoCreationPreview'
import AdvancedPayloadOverride from './AdvancedPayloadOverride'

export default {
  name: 'BusinessObjectPayloadEditor',
  components: { CreationValidationPanel, EventInputPanel, TodoCreationPreview, AdvancedPayloadOverride },
  props: {
    objects: { type: Array, default: () => [] },
    selectedId: { type: [Number, String], default: null },
    selected: { type: Object, default: null },
    hydration: { type: Object, default: null },
    manualOverrides: { type: Object, default: () => ({}) },
    loading: Boolean,
    hydrating: Boolean,
    readonly: Boolean,
    templateName: { type: String, default: '' }
  },
  computed: {
    rows() {
      return buildHydratedPayloadRows(this.hydration || {}, this.manualOverrides)
    },
    coverage() {
      return creationCoverage(this.hydration || {}, this.manualOverrides)
    },
    eventInputFields() {
      return applyHydrationOverrides((this.hydration && this.hydration.eventInput) || [], this.manualOverrides)
    },
    blockingIssues() {
      return remainingHydrationBlockers(this.hydration || {}, this.manualOverrides)
    },
    advancedFields() {
      if (!this.hydration) return []
      return []
        .concat(this.hydration.completionFields || [])
        .concat(this.hydration.routingFields || [])
        .concat(this.hydration.advancedFields || [])
    },
    hasSensitiveFields() {
      return (this.hydration.fields || []).some(field => field.sensitive)
    }
  },
  methods: {
    objectLabel(item) {
      return `${item.businessName || item.businessNo || '未命名对象'} · ${item.businessNo || item.businessId}`
    },
    sourceLabel(source) {
      return {
        BUSINESS_OBJECT: '业务对象',
        EVENT_SAMPLE: '事件样例',
        SYSTEM_DEFAULT: '系统默认',
        MANUAL_OVERRIDE: '手工补充',
        MISSING: '可选未填写'
      }[source] || source
    },
    sourceType(source) {
      return source === 'MANUAL_OVERRIDE' ? 'warning' : source === 'MISSING' ? 'danger' : 'info'
    }
  }
}
</script>

<style scoped>
.payload-editor { border: 1px solid #D9E1EA; border-radius: 8px; padding: 20px; background: #FFFFFF; }
.payload-editor__heading { display: flex; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.payload-editor__heading h3 { margin: 0 0 6px; color: #0B2A55; }
.payload-editor__heading p, .payload-editor__coverage small { margin: 0; color: #66758A; }
.payload-editor__sample, .payload-editor__coverage { margin-top: 16px; }
.payload-editor__selector { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 10px; }
.payload-editor__fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-top: 18px; }
.payload-editor__field { border-top: 1px solid #E8EDF3; padding-top: 12px; }
.payload-editor__label { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.payload-editor__masked { min-height: 40px; line-height: 40px; padding: 0 15px; border: 1px solid #D9E1EA; border-radius: 4px; color: #66758A; background: #F4F7FA; letter-spacing: 3px; }
.el-select-dropdown__item small { float: right; color: #8492A6; margin-right: 10px; }
@media (max-width: 720px) {
  .payload-editor__fields, .payload-editor__selector { grid-template-columns: 1fr; }
}
</style>
