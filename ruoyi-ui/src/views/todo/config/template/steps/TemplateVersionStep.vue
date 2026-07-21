<template>
  <section>
    <el-alert title="已发布版本只读。编辑应在草稿版本进行；回滚会创建新草稿，不覆盖历史版本。" type="info" :closable="false" show-icon />
    <el-table :data="versions" border stripe class="top-gap"><el-table-column prop="versionNo" label="版本" width="90"><template slot-scope="{ row }">v{{ field(row,'versionNo','version_no') }}</template></el-table-column><el-table-column prop="status" label="状态" width="110" /><el-table-column label="变更摘要" min-width="180"><template slot-scope="{ row }">{{ field(row,'changeSummary','change_summary') || '-' }}</template></el-table-column><el-table-column label="影响范围" min-width="160"><template slot-scope="{ row }">{{ field(row,'impactScope','impact_scope') || '-' }}</template></el-table-column><el-table-column label="来源版本" width="100"><template slot-scope="{ row }">{{ field(row,'sourceVersionId','source_version_id') || '-' }}</template></el-table-column><el-table-column label="发布人" width="110"><template slot-scope="{ row }">{{ field(row,'publishedBy','published_by') || '-' }}</template></el-table-column><el-table-column label="发布时间" width="170"><template slot-scope="{ row }">{{ format(field(row,'publishedTime','published_time')) }}</template></el-table-column></el-table>
    <el-form label-width="100px" class="top-gap"><el-row :gutter="16"><el-col :span="10"><el-form-item label="左侧版本"><el-select v-model="left" clearable class="full"><el-option v-for="item in versions" :key="versionId(item)" :label="`v${field(item,'versionNo','version_no')} · ${item.status}`" :value="versionId(item)" /></el-select></el-form-item></el-col><el-col :span="10"><el-form-item label="右侧版本"><el-select v-model="right" clearable class="full"><el-option v-for="item in versions" :key="versionId(item)" :label="`v${field(item,'versionNo','version_no')} · ${item.status}`" :value="versionId(item)" /></el-select></el-form-item></el-col><el-col :span="4"><el-button :disabled="!left || !right || left===right" :loading="loadingDiff" @click="compare">对比</el-button></el-col></el-row></el-form>
    <el-card v-if="diff" shadow="never"><div slot="header">版本差异</div><pre>{{ JSON.stringify(diff,null,2) }}</pre></el-card>
  </section>
</template>
<script>
import { diffReleaseRecords } from '@/api/todo-config'
export default {
  name: 'TemplateVersionStep', props: { versions: { type: Array, default: () => [] } },
  data() { return { left: null, right: null, diff: null, loadingDiff: false } },
  methods: { field(row, camel, snake) { return row && (row[camel] !== undefined ? row[camel] : row[snake]) }, versionId(row) { return Number(this.field(row,'versionId','version_id')) }, format(value) { return value ? this.parseTime(value, '{y}-{m}-{d} {h}:{i}:{s}') : '-' }, async compare() { this.loadingDiff = true; try { const response = await diffReleaseRecords(this.left, this.right); this.diff = response.data || {} } catch (error) { this.$modal.msgError((error && (error.msg || error.message)) || '版本对比失败') } finally { this.loadingDiff = false } }, validate() { return Promise.resolve(true) } }
}
</script>
<style scoped>.full{width:100%}.top-gap{margin-top:16px}pre{white-space:pre-wrap;word-break:break-word}</style>
