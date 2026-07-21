<!-- E2E-only component harness; no production menu or migration routes here. -->
<template>
  <div class="app-container">
    <el-page-header content="待办流程配置中心" />
    <el-tabs v-model="tab">
      <el-tab-pane label="准入总览" name="admissionOverview"><foundation-admission-overview :report="foundationAdmission" /></el-tab-pane>
      <el-tab-pane label="模板定义" name="templates"><template-list :rows="templates" :loading="loading" @versions="versions" @copy="copyTemplate" /></el-tab-pane>
      <el-tab-pane label="触发规则" name="triggers"><trigger-rule-table :rows="triggers" :templates="templates" :on-refresh="load" /></el-tab-pane>
      <el-tab-pane label="工作日历" name="calendars"><work-calendar-table :rows="calendars" :on-refresh="load" /></el-tab-pane>
      <el-tab-pane label="决策登记" name="decisions"><decision-registry :rows="decisions" :on-refresh="load" /></el-tab-pane>
      <el-tab-pane label="准入证据" name="admission"><admission-evidence-registry :rows="admissionEvidence" :on-refresh="load" /></el-tab-pane>
      <el-tab-pane label="基础资源" name="resources"><foundation-resource-readiness :report="foundationResources" /></el-tab-pane>
      <el-tab-pane label="历史迁移" name="migration"><historical-migration-readiness :report="historicalMigration" /></el-tab-pane>
      <el-tab-pane label="文件安全" name="fileSecurity"><file-security-readiness :report="fileSecurity" /></el-tab-pane>
      <el-tab-pane label="财务准入" name="finance"><finance-readiness :report="financeReadiness" /></el-tab-pane>
      <el-tab-pane label="阶段一验收" name="acceptance"><acceptance-readiness :report="acceptanceReadiness" :scenarios="acceptanceScenarios" :mappings="acceptanceMappings" :options="acceptanceOptions" @refresh="loadAcceptance" @mapping-filter="loadAcceptanceMappings" /></el-tab-pane>
    </el-tabs>
    <version-drawer :visible.sync="drawer" :versions="versionRows" :template-id="current && current.template_id" @versions-refreshed="versionRows = $event" @publish="publishDraft" @copy-version="copyVersion" />
  </div>
</template>
<script>
import TemplateList from './TemplateList'
import VersionDrawer from './VersionDrawer'
import TriggerRuleTable from './TriggerRuleTable'
import WorkCalendarTable from './WorkCalendarTable'
import DecisionRegistry from './DecisionRegistry'
import AdmissionEvidenceRegistry from './AdmissionEvidenceRegistry'
import FoundationResourceReadiness from './FoundationResourceReadiness'
import HistoricalMigrationReadiness from './HistoricalMigrationReadiness'
import FileSecurityReadiness from './FileSecurityReadiness'
import FinanceReadiness from './FinanceReadiness'
import AcceptanceReadiness from './AcceptanceReadiness'
import FoundationAdmissionOverview from './FoundationAdmissionOverview'
import { listDefinitions, listDefinitionVersions, copyDefinition, copyDefinitionVersion, publishDefinition, listTriggerRules, listWorkCalendars, listTodoDecisions, listAdmissionEvidence, getFoundationAdmissionReadiness, getFoundationResourceReadiness, getHistoricalMigrationReadiness, getFileSecurityReadiness, getFinanceReadiness, getAcceptanceReadiness, listAcceptanceScenarios, listAcceptanceMappings, getAcceptanceGovernanceOptions } from '@/api/todo-definition'

export default {
  name: 'FoundationConfigurationHarness', components: { TemplateList, VersionDrawer, TriggerRuleTable, WorkCalendarTable, DecisionRegistry, AdmissionEvidenceRegistry, FoundationAdmissionOverview, FoundationResourceReadiness, HistoricalMigrationReadiness, FileSecurityReadiness, FinanceReadiness, AcceptanceReadiness },
  data() { return { tab: 'templates', loading: false, templates: [], triggers: [], calendars: [], decisions: [], admissionEvidence: [], foundationAdmission: { overallStatus: 'NOT_ADMITTED', admitted: false, readyGateCount: 0, totalGateCount: 8, gates: [] }, foundationResources: { resources: [] }, historicalMigration: { requirements: [] }, fileSecurity: { requirements: [] }, financeReadiness: { requirements: [] }, acceptanceReadiness: { requirements: [] }, acceptanceScenarios: [], acceptanceMappings: [], acceptanceOptions: {}, versionRows: [], drawer: false, current: null } },
  created() { this.load() },
  methods: {
    load() {
      this.loading = true
      return Promise.all([listDefinitions(), listTriggerRules(), listWorkCalendars(), listTodoDecisions(), listAdmissionEvidence(), getFoundationAdmissionReadiness(), getFoundationResourceReadiness(), getHistoricalMigrationReadiness(), getFileSecurityReadiness(), getFinanceReadiness(), getAcceptanceReadiness(), listAcceptanceScenarios(), listAcceptanceMappings(), getAcceptanceGovernanceOptions()])
        .then(([templates, triggers, calendars, decisions, admission, admissionOverview, resources, migration, fileSecurity, finance, acceptance, scenarios, mappings, options]) => { this.templates = templates.data || []; this.triggers = triggers.data || []; this.calendars = calendars.data || []; this.decisions = decisions.data || []; this.admissionEvidence = admission.data || []; this.foundationAdmission = admissionOverview.data || this.foundationAdmission; this.foundationResources = resources.data || { resources: [] }; this.historicalMigration = migration.data || { requirements: [] }; this.fileSecurity = fileSecurity.data || { requirements: [] }; this.financeReadiness = finance.data || { requirements: [] }; this.acceptanceReadiness = acceptance.data || { requirements: [] }; this.acceptanceScenarios = scenarios.data || []; this.acceptanceMappings = mappings.data || []; this.acceptanceOptions = options.data || {} })
        .finally(() => { this.loading = false })
    },
    loadAcceptance() { return Promise.all([getAcceptanceReadiness(), listAcceptanceScenarios(), listAcceptanceMappings(), getAcceptanceGovernanceOptions()]).then(([readiness, scenarios, mappings, options]) => { this.acceptanceReadiness = readiness.data || { requirements: [] }; this.acceptanceScenarios = scenarios.data || []; this.acceptanceMappings = mappings.data || []; this.acceptanceOptions = options.data || {} }) },
    loadAcceptanceMappings(filters) { return listAcceptanceMappings(filters).then(response => { this.acceptanceMappings = response.data || [] }) },
    versions(row) { this.current = row; listDefinitionVersions(row.template_id).then(response => { this.versionRows = response.data || []; this.drawer = true }) },
    copyTemplate(row) { this.$prompt('请输入新模板编码', '复制模板', { inputPattern: /^[A-Z][A-Z0-9_]+$/ }).then(({ value }) => copyDefinition(row.template_id, { actionId: `copy-${Date.now()}`, newTemplateCode: value, newTemplateName: `${row.template_name}副本` })).then(() => { this.$modal.msgSuccess('已创建草稿副本'); this.load() }) },
    copyVersion(row) { copyDefinitionVersion(this.current.template_id, row.version_no, { actionId: `copy-version-${Date.now()}`, newVersionNo: Math.max(...this.versionRows.map(version => version.version_no)) + 1 }).then(() => this.versions(this.current)) },
    publishDraft(row) { this.$confirm('发布后该版本不可修改，确认发布？', '发布确认', { type: 'warning' }).then(() => publishDefinition(row.version_id, { actionId: `publish-${Date.now()}`, versionId: row.version_id })).then(() => { this.$modal.msgSuccess('版本已发布'); this.versions(this.current) }) }
  }
}
</script>
