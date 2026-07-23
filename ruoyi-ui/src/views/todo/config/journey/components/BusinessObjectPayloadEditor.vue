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
      <el-button plain icon="el-icon-magic-stick" :disabled="readonly" @click="$emit('sample')">加载只读样例</el-button>
    </div>

    <el-alert
      v-if="selected && selected.sample"
      class="payload-editor__sample"
      title="这是只读样例，仅用于验证配置，绝不会生成或写入运行时待办。"
      type="warning"
      :closable="false"
      show-icon
    />

    <div v-if="hydration" class="payload-editor__coverage">
      <span>载荷覆盖率</span>
      <el-progress :percentage="coverage" :status="coverage === 100 ? 'success' : undefined" />
      <small>每个值均标注来源；仅“手工补充”会随本次试运行提交。</small>
    </div>

    <div v-if="rows.length" class="payload-editor__fields">
      <div v-for="row in rows" :key="row.path" class="payload-editor__field">
        <div class="payload-editor__label">
          <strong>{{ row.label || row.path }}</strong>
          <el-tag size="mini" :type="sourceType(row.source)">{{ sourceLabel(row.source) }}</el-tag>
        </div>
        <el-input
          v-if="row.editable"
          :value="row.displayValue"
          :disabled="readonly"
          :placeholder="row.source === 'MISSING' ? '可为本次试运行手工补充' : ''"
          @input="$emit('override', { path: row.path, value: $event })"
        />
        <div v-else class="payload-editor__masked" aria-label="敏感字段已脱敏">••••••</div>
      </div>
    </div>

    <el-empty v-else-if="selected && !loading" description="选择对象后加载服务器载荷" :image-size="72">
      <el-button type="primary" plain :loading="hydrating" @click="$emit('hydrate')">加载测试载荷</el-button>
    </el-empty>
  </section>
</template>

<script>
import { buildHydratedPayloadRows } from '../journey-step-model'

export default {
  name: 'BusinessObjectPayloadEditor',
  props: {
    objects: { type: Array, default: () => [] },
    selectedId: { type: [Number, String], default: null },
    selected: { type: Object, default: null },
    hydration: { type: Object, default: null },
    manualOverrides: { type: Object, default: () => ({}) },
    loading: Boolean,
    hydrating: Boolean,
    readonly: Boolean
  },
  computed: {
    rows() {
      return buildHydratedPayloadRows(this.hydration || {}, this.manualOverrides)
    },
    coverage() {
      return Number((this.hydration && (this.hydration.coveragePercent || this.hydration.coverage)) || 0)
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
        MISSING: '待补充'
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
