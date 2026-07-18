<template>
  <el-drawer title="模板版本" :visible.sync="open" size="86%" @close="$emit('update:visible', false)">
    <div class="drawer-body">
      <el-table :data="versions">
        <el-table-column prop="version_no" label="版本" width="80" />
        <el-table-column prop="status" label="状态" />
        <el-table-column prop="published_time" label="发布时间" />
        <el-table-column label="操作">
          <template slot-scope="{row}">
            <el-button type="text" @click="inspect(row)">查看</el-button>
            <el-button v-if="row.status === 'DRAFT'" v-hasPermi="['todo:definition:edit']" :data-testid="`definition-edit-${id(row)}`" type="text" @click="edit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" v-hasPermi="['todo:definition:publish']" :data-testid="`publish-${id(row)}`" type="text" :disabled="!canPublish(row)" @click="publish(row)">发布</el-button>
            <el-button v-hasPermi="['todo:definition:edit']" type="text" @click="$emit('copy-version', row)">复制草稿</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-alert v-if="definitionError" :title="definitionError" type="error" :closable="false" />
      <definition-form v-if="editing" :key="id(editing)" ref="definition" :value="editing" :readonly="editing.status !== 'DRAFT'" :current-version-id="id(editing)" :versions="versions" @invalid-definition="definitionError = $event" @changed="draftChanged" />
      <div v-if="editing" class="actions">
        <el-button v-if="editing.status === 'DRAFT'" type="primary" :disabled="!!definitionError || saving" :loading="saving" @click="save">保存草稿</el-button>
        <el-button v-hasPermi="['todo:definition:simulate']" data-testid="simulation-open" @click="simulationOpen = true">模拟</el-button>
        <el-button v-hasPermi="['todo:definition:diff']" data-testid="diff-open" :disabled="versions.length < 2" @click="diffOpen = true">版本比较</el-button>
      </div>
      <preflight-panel v-if="editing && editing.status === 'DRAFT'" ref="preflight" :version-id="id(editing)" :dirty="dirty" :disabled="dirty || saving || !!definitionError" @result="preflightResult" @cleared="clearGate" />
    </div>
    <simulation-drawer :visible.sync="simulationOpen" :version-id="editing && id(editing)" />
    <version-diff-drawer :visible.sync="diffOpen" :versions="versions" :current-version-id="editing && id(editing)" />
  </el-drawer>
</template>
<script>
import { listDefinitionVersions, updateDefinitionDraft } from '@/api/todo-definition'
import DefinitionForm from './DefinitionForm'
import PreflightPanel from './PreflightPanel'
import SimulationDrawer from './SimulationDrawer'
import VersionDiffDrawer from './VersionDiffDrawer'
import { replaceDraftWithServerVersion, definitionSourceToken } from '../definition-codec'

export default {
  name: 'VersionDrawer',
  components: { DefinitionForm, PreflightPanel, SimulationDrawer, VersionDiffDrawer },
  props: { visible: Boolean, versions: { type: Array, default: () => [] }, templateId: [Number, String] },
  data() { return { editing: null, definitionError: null, dirty: false, saving: false, gate: null, simulationOpen: false, diffOpen: false } },
  computed: { open: { get() { return this.visible }, set(value) { this.$emit('update:visible', value) } } },
  watch: { visible(value) { if (!value) { this.editing = null; this.clearGate() } } },
  methods: {
    id(row) { return Number(row && (row.version_id || row.versionId)) },
    inspect(row) { this.definitionError = null; this.dirty = false; this.clearGate(); this.editing = { ...row } },
    edit(row) { this.definitionError = null; this.dirty = false; this.clearGate(); this.editing = { ...row } },
    draftChanged() { this.dirty = true; this.clearGate() },
    clearGate() { this.gate = null },
    preflightResult(value) {
      const report = value && value.report
      this.gate = report && (report.errors || []).length === 0
        ? { versionId: Number(value.versionId), definitionToken: definitionSourceToken(this.editing), hash: report.definitionHash }
        : null
    },
    canPublish(row) {
      return !!this.gate && !this.dirty && this.gate.versionId === this.id(row) && this.editing && this.id(this.editing) === this.id(row) && this.gate.definitionToken === definitionSourceToken(this.editing)
    },
    publish(row) { if (this.canPublish(row)) this.$emit('publish', row) },
    save() {
      if (this.definitionError) return
      const row = this.editing
      this.saving = true; this.clearGate()
      this.$refs.definition.validate()
        .then(payload => updateDefinitionDraft(this.id(row), { actionId: `draft-${Date.now()}`, versionId: this.id(row), ...payload }))
        .then(() => this.refreshSavedDraft(this.id(row)))
        .then(() => this.$modal.msgSuccess('草稿已保存'))
        .catch(error => this.$modal.msgError(error.message || '保存草稿失败'))
        .finally(() => { this.saving = false })
    },
    refreshSavedDraft(versionId) {
      const templateId = this.templateId || this.editing.template_id || this.editing.templateId
      if (!templateId) return Promise.reject(new Error('Unable to refresh draft without a template id'))
      return listDefinitionVersions(templateId).then(response => {
        const rows = response.data || []
        this.editing = replaceDraftWithServerVersion(rows, versionId)
        this.definitionError = null; this.dirty = false; this.clearGate()
        this.$emit('versions-refreshed', rows)
      })
    }
  }
}
</script>
<style scoped>.drawer-body{padding:18px}.actions{display:flex;gap:8px;margin:14px 0}</style>
