<template>
  <div class="app-container">
    <el-page-header content="待办流程配置中心" />
    <el-tabs v-model="tab">
      <el-tab-pane label="模板定义" name="templates"><template-list :rows="templates" :loading="loading" @versions="versions" @copy="copyTemplate" /></el-tab-pane>
      <el-tab-pane label="触发规则" name="triggers"><trigger-rule-table :rows="triggers" :templates="templates" :on-refresh="load" /></el-tab-pane>
      <el-tab-pane label="工作日历" name="calendars"><work-calendar-table :rows="calendars" :on-refresh="load" /></el-tab-pane>
      <el-tab-pane label="决策登记" name="decisions"><decision-registry :rows="decisions" :on-refresh="load" /></el-tab-pane>
      <el-tab-pane label="准入证据" name="admission"><admission-evidence-registry :rows="admissionEvidence" :on-refresh="load" /></el-tab-pane>
      <el-tab-pane label="基础资源" name="resources"><foundation-resource-readiness :report="foundationResources" /></el-tab-pane>
      <el-tab-pane label="历史迁移" name="migration"><historical-migration-readiness :report="historicalMigration" /></el-tab-pane>
      <el-tab-pane label="文件安全" name="fileSecurity"><file-security-readiness :report="fileSecurity" /></el-tab-pane>
    </el-tabs>
    <version-drawer :visible.sync="drawer" :versions="versionRows" :template-id="current && current.template_id" @versions-refreshed="versionRows = $event" @publish="publishDraft" @copy-version="copyVersion" />
  </div>
</template>
<script>
import TemplateList from './components/TemplateList'
import VersionDrawer from './components/VersionDrawer'
import TriggerRuleTable from './components/TriggerRuleTable'
import WorkCalendarTable from './components/WorkCalendarTable'
import DecisionRegistry from './components/DecisionRegistry'
import AdmissionEvidenceRegistry from './components/AdmissionEvidenceRegistry'
import FoundationResourceReadiness from './components/FoundationResourceReadiness'
import HistoricalMigrationReadiness from './components/HistoricalMigrationReadiness'
import FileSecurityReadiness from './components/FileSecurityReadiness'
import { listDefinitions, listDefinitionVersions, copyDefinition, copyDefinitionVersion, publishDefinition, listTriggerRules, listWorkCalendars, listTodoDecisions, listAdmissionEvidence, getFoundationResourceReadiness, getHistoricalMigrationReadiness, getFileSecurityReadiness } from '@/api/todo-definition'

export default {
  name: 'TodoConfig', components: { TemplateList, VersionDrawer, TriggerRuleTable, WorkCalendarTable, DecisionRegistry, AdmissionEvidenceRegistry, FoundationResourceReadiness, HistoricalMigrationReadiness, FileSecurityReadiness },
  data() { return { tab: 'templates', loading: false, templates: [], triggers: [], calendars: [], decisions: [], admissionEvidence: [], foundationResources: { resources: [] }, historicalMigration: { requirements: [] }, fileSecurity: { requirements: [] }, versionRows: [], drawer: false, current: null } },
  created() { this.load() },
  methods: {
    load() {
      this.loading = true
      return Promise.all([listDefinitions(), listTriggerRules(), listWorkCalendars(), listTodoDecisions(), listAdmissionEvidence(), getFoundationResourceReadiness(), getHistoricalMigrationReadiness(), getFileSecurityReadiness()])
        .then(([templates, triggers, calendars, decisions, admission, resources, migration, fileSecurity]) => { this.templates = templates.data || []; this.triggers = triggers.data || []; this.calendars = calendars.data || []; this.decisions = decisions.data || []; this.admissionEvidence = admission.data || []; this.foundationResources = resources.data || { resources: [] }; this.historicalMigration = migration.data || { requirements: [] }; this.fileSecurity = fileSecurity.data || { requirements: [] } })
        .finally(() => { this.loading = false })
    },
    versions(row) { this.current = row; listDefinitionVersions(row.template_id).then(response => { this.versionRows = response.data || []; this.drawer = true }) },
    copyTemplate(row) { this.$prompt('请输入新模板编码', '复制模板', { inputPattern: /^[A-Z][A-Z0-9_]+$/ }).then(({ value }) => copyDefinition(row.template_id, { actionId: `copy-${Date.now()}`, newTemplateCode: value, newTemplateName: `${row.template_name}副本` })).then(() => { this.$modal.msgSuccess('已创建草稿副本'); this.load() }) },
    copyVersion(row) { copyDefinitionVersion(this.current.template_id, row.version_no, { actionId: `copy-version-${Date.now()}`, newVersionNo: Math.max(...this.versionRows.map(version => version.version_no)) + 1 }).then(() => this.versions(this.current)) },
    publishDraft(row) { this.$confirm('发布后该版本不可修改，确认发布？', '发布确认', { type: 'warning' }).then(() => publishDefinition(row.version_id, { actionId: `publish-${Date.now()}`, versionId: row.version_id })).then(() => { this.$modal.msgSuccess('版本已发布'); this.versions(this.current) }) }
  }
}
</script>
