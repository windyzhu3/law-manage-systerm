<template>
  <section v-if="draft || definitionError" class="template-summary-panel">
    <el-alert v-if="definitionError" title="模板规范定义损坏，系统已按只读方式阻止编辑和发布。" type="error" :closable="false" show-icon />
    <template v-if="draft">
    <el-descriptions :column="2" border><el-descriptions-item label="模板编码">{{ draft.templateCode || '-' }}</el-descriptions-item><el-descriptions-item label="模板名称">{{ draft.templateName || '-' }}</el-descriptions-item><el-descriptions-item label="业务类型">{{ draft.businessType || '-' }}</el-descriptions-item><el-descriptions-item label="发布状态">{{ draft.versionStatus || '-' }}</el-descriptions-item></el-descriptions>
    <el-row :gutter="12" class="summary-grid">
      <el-col :span="12"><el-card shadow="never"><div slot="header">触发事件</div><strong>{{ draft.event.eventType || '未配置' }}</strong><p>载荷版本 v{{ draft.event.payloadVersion || 1 }}</p><pre>{{ pretty(draft.event.condition) }}</pre></el-card></el-col>
      <el-col :span="12"><el-card shadow="never"><div slot="header">负责人规则</div><strong>{{ draft.owner.config.type || '未配置' }}</strong><pre>{{ pretty(draft.owner.config) }}</pre></el-card></el-col>
      <el-col :span="12"><el-card shadow="never"><div slot="header">SLA</div><p>{{ referenceLabel('SLA') }}</p><pre>{{ pretty(draft.sla.config) }}</pre></el-card></el-col>
      <el-col :span="12"><el-card shadow="never"><div slot="header">DoD</div><p>{{ referenceLabel('DOD') }}</p><pre>{{ pretty(draft.dod.config) }}</pre></el-card></el-col>
      <el-col :span="12"><el-card shadow="never"><div slot="header">下一步规则</div><p>{{ routingSummary }}</p></el-card></el-col>
      <el-col :span="12"><el-card shadow="never"><div slot="header">版本摘要</div><p>版本：v{{ draft.versionNo || 1 }}</p><p>变更：{{ draft.changeSummary || '-' }}</p><p>影响：{{ draft.impactScope || '-' }}</p></el-card></el-col>
      <el-col :span="24"><el-card shadow="never" class="todo-card-preview"><div slot="header">待办卡片预览</div><div class="preview-head"><strong>{{ cardTitle }}</strong><el-tag :type="priorityTone">{{ draft.priority || 'NORMAL' }}</el-tag></div><p>负责人：{{ owner }}</p><p>到期状态：由 {{ slaName }} 计算</p><p class="preview-description">{{ draft.description || '暂无待办说明' }}</p></el-card></el-col>
    </el-row>
    </template>
  </section>
</template>
<script>
export default {
  name: 'TemplateSummaryPanel', props: { draft: { type: Object, default: null }, definitionError: String },
  computed: { routingSummary() { const routing = this.draft.routing && this.draft.routing.config ? this.draft.routing.config : {}; return `${(routing.nodes || []).length} 个节点，${(routing.edges || []).length} 条连线` }, cardTitle() { const ui = this.draft.ui && this.draft.ui.config ? this.draft.ui.config : {}; return ui.cardTitle || this.draft.templateName || '待办标题' }, owner() { const config = this.draft.owner && this.draft.owner.config ? this.draft.owner.config : {}; return config.type ? `${config.type}${config.candidates && config.candidates.length ? ` · ${config.candidates.join('、')}` : ''}` : '未配置' }, slaName() { return this.referenceLabel('SLA') }, priorityTone() { return { URGENT: 'danger', HIGH: 'warning', NORMAL: '', LOW: 'info' }[this.draft.priority] || '' } },
  methods: { pretty(value) { return JSON.stringify(value || {}, null, 2) }, referenceLabel(type) { const refs = (this.draft.ruleReferences || []).filter(item => item.type === type); return refs.length ? refs.map(item => item.ruleName || item.ruleCode || item.id).join('、') : '未绑定规则库' } }
}
</script>
<style scoped lang="scss">@import '../styles/config-center.scss';.summary-grid{margin-top:14px}.summary-grid .el-col{margin-bottom:12px}.summary-grid .el-card{min-height:170px}.summary-grid p{margin:7px 0;color:#64748b}.summary-grid pre{max-height:90px;margin:8px 0 0;overflow:auto;white-space:pre-wrap;word-break:break-word}</style>
