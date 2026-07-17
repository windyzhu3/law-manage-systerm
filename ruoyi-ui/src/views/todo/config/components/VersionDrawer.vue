<template>
  <el-drawer title="模板版本" :visible.sync="open" size="620px" @close="$emit('update:visible', false)">
    <div class="drawer-body">
      <el-table :data="versions">
        <el-table-column prop="version_no" label="版本" width="80" />
        <el-table-column prop="status" label="状态" />
        <el-table-column prop="published_time" label="发布时间" />
        <el-table-column label="操作">
          <template slot-scope="{row}">
            <el-button type="text" @click="inspect(row)">查看</el-button>
            <el-button v-if="row.status === 'DRAFT'" v-hasPermi="['todo:definition:edit']" type="text" @click="edit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" v-hasPermi="['todo:definition:publish']" type="text" @click="$emit('publish', row)">发布</el-button>
            <el-button v-hasPermi="['todo:definition:edit']" type="text" @click="$emit('copy-version', row)">复制草稿</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-alert v-if="definitionError" :title="definitionError" type="error" :closable="false" />
      <definition-form v-if="editing" :key="editing.version_id || editing.versionId" ref="definition" :value="editing" :readonly="editing.status !== 'DRAFT'" @invalid-definition="definitionError = $event" />
      <el-button v-if="editing && editing.status === 'DRAFT'" type="primary" :disabled="!!definitionError" @click="save">保存草稿</el-button>
    </div>
  </el-drawer>
</template>
<script>
import { listDefinitionVersions, updateDefinitionDraft } from '@/api/todo-definition'
import DefinitionForm from './DefinitionForm'
import { replaceDraftWithServerVersion } from '../definition-codec'

export default {
  name: 'VersionDrawer',
  components: { DefinitionForm },
  props: { visible: Boolean, versions: { type: Array, default: () => [] }, templateId: [Number, String] },
  data() { return { editing: null, definitionError: null } },
  computed: { open: { get() { return this.visible }, set(value) { this.$emit('update:visible', value) } } },
  methods: {
    inspect(row) { this.definitionError = null; this.editing = { ...row } },
    edit(row) { this.definitionError = null; this.editing = { ...row } },
    save() {
      if (this.definitionError) return
      const row = this.editing
      this.$refs.definition.validate()
        .then(payload => updateDefinitionDraft(row.version_id || row.versionId, { actionId: `draft-${Date.now()}`, versionId: row.version_id || row.versionId, ...payload }))
        .then(() => this.refreshSavedDraft(row.version_id || row.versionId))
        .then(() => this.$modal.msgSuccess('草稿已保存'))
        .catch(error => this.$modal.msgError(error.message || '保存草稿失败'))
    },
    refreshSavedDraft(versionId) {
      const templateId = this.templateId || this.editing.template_id || this.editing.templateId
      if (!templateId) return Promise.reject(new Error('Unable to refresh draft without a template id'))
      return listDefinitionVersions(templateId).then(response => {
        const rows = response.data || []
        this.editing = replaceDraftWithServerVersion(rows, versionId)
        this.definitionError = null
        this.$emit('versions-refreshed', rows)
      })
    }
  }
}
</script>
<style scoped>.drawer-body{padding:18px}</style>
