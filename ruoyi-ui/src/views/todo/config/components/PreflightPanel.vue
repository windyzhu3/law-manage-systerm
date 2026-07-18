<template>
  <section class="preflight" data-testid="preflight-panel">
    <header>
      <div><h4>发布预检</h4><small v-if="report && report.definitionHash">定义哈希：{{ report.definitionHash }}</small></div>
      <el-button v-hasPermi="['todo:definition:preflight']" data-testid="preflight-run" size="mini" type="primary" :loading="loading" :disabled="disabled" @click="run">运行预检</el-button>
    </header>
    <el-alert v-if="dirty" title="草稿有未保存修改，请先保存后重新预检" type="warning" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div v-if="report">
      <el-tag :type="publishable ? 'success' : 'danger'">{{ publishable ? '预检通过' : '存在阻断项' }}</el-tag>
      <el-table v-if="issues.length" :data="issues" size="mini" border>
        <el-table-column prop="severity" label="级别" width="75" />
        <el-table-column prop="code" label="编码" min-width="190" />
        <el-table-column prop="path" label="路径" min-width="150" />
        <el-table-column prop="message" label="说明" min-width="220" />
      </el-table>
      <el-collapse v-if="report.compiledJson" class="compiled"><el-collapse-item title="编译产物"><pre data-testid="preflight-compiled-json">{{ report.compiledJson }}</pre></el-collapse-item></el-collapse>
    </div>
  </section>
</template>

<script>
import { preflightDefinition } from '@/api/todo-definition'

export default {
  name: 'PreflightPanel',
  props: { versionId: [Number, String], disabled: Boolean, dirty: Boolean },
  data() { return { loading: false, error: '', report: null } },
  computed: {
    publishable() { return !!this.report && Array.isArray(this.report.errors) && this.report.errors.length === 0 },
    issues() {
      if (!this.report) return []
      return [...(this.report.errors || []).map(item => ({ severity: 'ERROR', ...item })), ...(this.report.warnings || []).map(item => ({ severity: 'WARNING', ...item }))]
    }
  },
  watch: { versionId() { this.clear() } },
  methods: {
    run() {
      if (this.disabled || !this.versionId) return Promise.resolve()
      this.loading = true; this.error = ''; this.report = null; this.$emit('cleared')
      return preflightDefinition(this.versionId).then(response => {
        const data = response.data || {}
        this.report = data.report || null
        this.$emit('result', { versionId: Number(data.versionId || this.versionId), report: this.report })
        return this.report
      }).catch(error => { this.error = error.msg || error.message || '发布预检失败'; this.$emit('cleared'); throw error })
        .finally(() => { this.loading = false })
    },
    clear() { this.report = null; this.error = ''; this.$emit('cleared') }
  }
}
</script>

<style scoped>
.preflight{margin-top:16px;padding:14px;border:1px solid #dfe6ef;border-radius:8px}.preflight header{display:flex;align-items:center;justify-content:space-between;margin-bottom:10px}.preflight h4{margin:0 0 5px}.preflight small{color:#64748b}.preflight .el-table,.preflight .el-alert,.compiled{margin-top:10px}.compiled pre{max-height:220px;overflow:auto;white-space:pre-wrap;word-break:break-word;padding:8px;background:#f8fafc}
</style>
