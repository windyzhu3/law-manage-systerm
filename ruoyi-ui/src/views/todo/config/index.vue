<template>
  <div class="app-container">
    <el-page-header content="待办流程配置中心" />
    <el-tabs v-model="tab">
      <el-tab-pane label="模板定义" name="templates"><template-list :rows="templates" :loading="loading" @versions="versions" @copy="copyTemplate" /></el-tab-pane>
      <el-tab-pane label="触发规则" name="triggers"><trigger-rule-table :rows="triggers" /></el-tab-pane>
      <el-tab-pane label="工作日历" name="calendars"><work-calendar-table :rows="calendars" /></el-tab-pane>
    </el-tabs>
    <version-drawer :visible.sync="drawer" :versions="versionRows" :template-id="current && current.template_id" @versions-refreshed="versionRows = $event" @publish="publishDraft" @copy-version="copyVersion" />
  </div>
</template>
<script>
import TemplateList from './components/TemplateList'
import VersionDrawer from './components/VersionDrawer'
import TriggerRuleTable from './components/TriggerRuleTable'
import WorkCalendarTable from './components/WorkCalendarTable'
import { listDefinitions, listDefinitionVersions, copyDefinition, copyDefinitionVersion, publishDefinition, listTriggerRules, listWorkCalendars } from '@/api/todo-definition'

export default {
  name: 'TodoConfig',
  components: { TemplateList, VersionDrawer, TriggerRuleTable, WorkCalendarTable },
  data() { return { tab: 'templates', loading: false, templates: [], triggers: [], calendars: [], versionRows: [], drawer: false, current: null } },
  created() { this.load() },
  methods: {
    load() {
      this.loading = true
      Promise.all([listDefinitions(), listTriggerRules(), listWorkCalendars()])
        .then(([templates, triggers, calendars]) => { this.templates = templates.data || []; this.triggers = triggers.data || []; this.calendars = calendars.data || [] })
        .finally(() => { this.loading = false })
    },
    versions(row) { this.current = row; listDefinitionVersions(row.template_id).then(response => { this.versionRows = response.data || []; this.drawer = true }) },
    copyTemplate(row) {
      this.$prompt('请输入新模板编码', '复制模板', { inputPattern: /^[A-Z][A-Z0-9_]+$/ })
        .then(({ value }) => copyDefinition(row.template_id, { actionId: `copy-${Date.now()}`, newTemplateCode: value, newTemplateName: `${row.template_name}副本` }))
        .then(() => { this.$modal.msgSuccess('已创建草稿副本'); this.load() })
    },
    copyVersion(row) { copyDefinitionVersion(this.current.template_id, row.version_no, { actionId: `copy-version-${Date.now()}`, newVersionNo: Math.max(...this.versionRows.map(version => version.version_no)) + 1 }).then(() => this.versions(this.current)) },
    publishDraft(row) {
      this.$confirm('发布后该版本不可修改，确认发布？', '发布确认', { type: 'warning' })
        .then(() => publishDefinition(row.version_id, { actionId: `publish-${Date.now()}`, versionId: row.version_id }))
        .then(() => { this.$modal.msgSuccess('版本已发布'); this.versions(this.current) })
    }
  }
}
</script>
