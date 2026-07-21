<template>
  <config-detail-drawer ref="drawer" :visible="value" title="发布版本详情" size="720px" @update:visible="$emit('input',$event)" @reset="reset">
    <el-alert title="不可变发布版本" description="详情、差异和规则快照均来自已发布事实；复制与回滚只会生成新草稿，不修改当前版本。" type="info" :closable="false" show-icon />
    <div v-loading="loading" class="release-detail">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="模板">{{ detail.templateName }}（{{ detail.templateCode }}）</el-descriptions-item><el-descriptions-item label="版本">v{{ detail.versionNo }}</el-descriptions-item>
        <el-descriptions-item label="发布状态">{{ detail.status }}</el-descriptions-item><el-descriptions-item label="发布人">{{ detail.publishedBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="变更摘要">{{ detail.changeSummary || '-' }}</el-descriptions-item><el-descriptions-item label="影响范围">{{ detail.impactScope || '-' }}</el-descriptions-item>
        <el-descriptions-item label="回滚来源">{{ detail.rollbackSourceVersionId || '-' }}</el-descriptions-item><el-descriptions-item label="发布时间">{{ format(detail.publishedTime || detail.updateTime) }}</el-descriptions-item>
      </el-descriptions>
      <el-tabs v-if="detail" v-model="tab">
        <el-tab-pane label="版本详情" name="detail"><pre>{{ pretty(versionDetail) }}</pre></el-tab-pane>
        <el-tab-pane label="规则快照" name="rules"><pre>{{ pretty(ruleSnapshot) }}</pre></el-tab-pane>
        <el-tab-pane label="语义差异" name="diff">
          <el-form :inline="true" size="small"><el-form-item label="对比版本"><el-select v-model="compareVersionId" clearable><el-option v-for="item in comparableVersions" :key="versionId(item)" :label="`v${field(item,'versionNo','version_no')} · ${item.status}`" :value="versionId(item)" /></el-select></el-form-item><el-button v-hasPermi="['todo:release:diff']" :disabled="!compareVersionId" :loading="diffLoading" @click="loadDiff">生成差异</el-button></el-form>
          <version-semantic-diff :value="diff" />
        </el-tab-pane>
        <el-tab-pane label="动作账本" name="action"><pre>{{ pretty(detail.action) }}</pre></el-tab-pane>
      </el-tabs>
    </div>
    <template #footer>
      <el-button @click="$refs.drawer.requestProgrammaticClose()">关闭</el-button>
      <el-button v-hasPermi="['todo:template:copy']" :loading="mutating" @click="copyAsNewVersion">复制为新版本</el-button>
      <el-button v-hasPermi="['todo:release:rollback']" type="primary" :loading="mutating" @click="rollbackAsDraft">生成回滚草稿</el-button>
    </template>
  </config-detail-drawer>
</template>
<script>
import ConfigDetailDrawer from '../shared/ConfigDetailDrawer'
import VersionSemanticDiff from './VersionSemanticDiff'
import { getReleaseRecord, listTemplateVersions, diffReleaseRecords, copyReleaseDraft, rollbackReleaseDraft } from '@/api/todo-config'
export default {
  name: 'ReleaseRecordDrawer', components: { ConfigDetailDrawer, VersionSemanticDiff }, props: { value: Boolean, record: { type: Object, default: null } },
  data() { return { loading: false, mutating: false, detail: null, versions: [], versionDetail: null, tab: 'detail', compareVersionId: null, diff: null, diffLoading: false } },
  computed: { comparableVersions() { return this.versions.filter(item => this.versionId(item) !== Number(this.detail && this.detail.versionId)) }, ruleSnapshot() { const row = this.versionDetail || {}; return { owner: this.parse(this.field(row,'ownerRuleJson','owner_rule_json')), sla: this.parse(this.field(row,'slaRuleJson','sla_rule_json')), dod: this.parse(this.field(row,'dodRuleJson','dod_rule_json')), routing: this.parse(this.field(row,'nextRuleJson','next_rule_json')), ui: this.parse(this.field(row,'uiSchemaJson','ui_schema_json')) } } },
  watch: { value(opened) { if (opened) this.open() }, record() { if (this.value) this.open() } },
  methods: {
    field(row, camel, snake) { return row && (row[camel] !== undefined ? row[camel] : row[snake]) }, versionId(row) { return Number(this.field(row,'versionId','version_id')) }, parse(value) { if (!value) return {}; if (typeof value !== 'string') return value; try { return JSON.parse(value) } catch (_) { return { invalidJson: value } } }, pretty(value) { return JSON.stringify(value || {}, null, 2) }, format(value) { return value ? this.parseTime(value, '{y}-{m}-{d} {h}:{i}:{s}') : '-' }, actionId(action) { return `release-${action}-${Date.now()}-${Math.random().toString(16).slice(2)}` },
    async open() { if (!this.record) return; this.loading = true; try { const response = await getReleaseRecord(this.record.versionId); this.detail = response.data || this.record; const versions = await listTemplateVersions(this.detail.templateId); this.versions = versions.data || []; this.versionDetail = this.versions.find(item => this.versionId(item) === Number(this.detail.versionId)) || null } finally { this.loading = false } },
    async loadDiff() { if (!this.compareVersionId) return; this.diffLoading = true; try { const response = await diffReleaseRecords(this.compareVersionId, this.detail.versionId); this.diff = response.data || null } finally { this.diffLoading = false } },
    nextVersionNo() { return Math.max(Number(this.detail && this.detail.versionNo) || 0, ...this.versions.map(item => Number(this.field(item,'versionNo','version_no')) || 0)) + 1 },
    async copyAsNewVersion() { await this.$confirm('将基于此不可变发布版本生成新的可编辑草稿，确认继续吗？','复制为新版本',{type:'warning'}); this.mutating = true; try { await copyReleaseDraft(this.detail.versionId,{ actionId:this.actionId('copy'), newVersionNo:this.nextVersionNo() }); this.$modal.msgSuccess('已生成新版本草稿'); this.$emit('draft-created'); this.$refs.drawer.closeAfterSave() } finally { this.mutating = false } },
    async rollbackAsDraft() { await this.$confirm('回滚不会修改历史版本，只会以当前版本为来源生成新草稿。确认继续吗？','生成回滚草稿',{type:'warning'}); this.mutating = true; try { await rollbackReleaseDraft(this.detail.versionId,{ actionId:this.actionId('rollback'), newVersionNo:this.nextVersionNo() }); this.$modal.msgSuccess('已生成回滚草稿'); this.$emit('draft-created'); this.$refs.drawer.closeAfterSave() } finally { this.mutating = false } },
    reset() { this.detail = null; this.versions = []; this.versionDetail = null; this.tab = 'detail'; this.compareVersionId = null; this.diff = null }
  }
}
</script>
<style scoped lang="scss">@import '../styles/config-center.scss';.release-detail{margin-top:16px}.release-detail pre{max-height:430px;margin:0;padding:12px;overflow:auto;border:1px solid #e8edf6;border-radius:6px;background:#f8fafc;white-space:pre-wrap;word-break:break-word}</style>
