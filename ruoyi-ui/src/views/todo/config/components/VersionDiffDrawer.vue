<template>
  <el-drawer title="版本语义差异" :visible.sync="open" size="820px" append-to-body data-testid="version-diff-drawer">
    <div class="body">
      <div class="toolbar">
        <el-select v-model="leftVersionId" placeholder="基准版本"><el-option v-for="row in versions" :key="id(row)" :label="label(row)" :value="id(row)" /></el-select>
        <span>对比</span>
        <el-select v-model="rightVersionId" placeholder="目标版本"><el-option v-for="row in versions" :key="id(row)" :label="label(row)" :value="id(row)" /></el-select>
        <el-button data-testid="diff-run" type="primary" :loading="loading" :disabled="!leftVersionId || !rightVersionId || leftVersionId === rightVersionId" @click="run">比较</el-button>
      </div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <div v-if="result" data-testid="diff-result">
        <p>整体风险：<el-tag :type="riskType(result.overallRisk)">{{ result.overallRisk || 'NONE' }}</el-tag></p>
        <el-table :data="result.changes || []" size="mini" border>
          <el-table-column prop="section" label="区段" width="100" />
          <el-table-column prop="path" label="路径" min-width="140" />
          <el-table-column prop="type" label="类型" width="95" />
          <el-table-column label="变更前" min-width="170"><template slot-scope="{ row }"><pre>{{ safe(row.before) }}</pre></template></el-table-column>
          <el-table-column label="变更后" min-width="170"><template slot-scope="{ row }"><pre>{{ safe(row.after) }}</pre></template></el-table-column>
          <el-table-column prop="risk" label="风险" width="90" />
          <el-table-column prop="reason" label="原因" min-width="150" />
        </el-table>
      </div>
    </div>
  </el-drawer>
</template>

<script>
import { diffDefinitionVersions } from '@/api/todo-definition'

export default {
  name: 'VersionDiffDrawer',
  props: { visible: Boolean, versions: { type: Array, default: () => [] }, currentVersionId: [Number, String] },
  data() { return { leftVersionId: null, rightVersionId: null, loading: false, error: '', result: null } },
  computed: { open: { get() { return this.visible }, set(value) { this.$emit('update:visible', value) } } },
  watch: {
    visible(value) { if (value) this.initialize() },
    versions() { if (this.visible) this.initialize() }
  },
  methods: {
    id(row) { return Number(row.version_id || row.versionId) },
    label(row) { return `v${row.version_no || row.versionNo} · ${row.status}` },
    initialize() {
      const ids = this.versions.map(this.id).filter(Boolean)
      this.rightVersionId = Number(this.currentVersionId) || ids[ids.length - 1] || null
      this.leftVersionId = ids.find(id => id !== this.rightVersionId) || null
      this.error = ''; this.result = null
    },
    safe(value) { return typeof value === 'string' ? value : JSON.stringify(value == null ? null : value, null, 2) },
    riskType(value) { return value === 'BLOCKING' || value === 'HIGH' ? 'danger' : value === 'MEDIUM' ? 'warning' : 'success' },
    run() {
      this.loading = true; this.error = ''; this.result = null
      return diffDefinitionVersions(this.leftVersionId, this.rightVersionId).then(response => { this.result = response.data || {}; return this.result })
        .catch(error => { this.error = error.msg || error.message || '版本比较失败' })
        .finally(() => { this.loading = false })
    }
  }
}
</script>

<style scoped>
.body{padding:0 18px 24px}.toolbar{display:flex;align-items:center;gap:10px;margin-bottom:14px}.body pre{max-height:160px;overflow:auto;white-space:pre-wrap;word-break:break-all;margin:0;padding:6px;background:#f8fafc}
</style>
