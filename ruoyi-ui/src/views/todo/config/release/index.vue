<template>
  <config-page-shell title="发布记录" subtitle="基于不可变模板版本与发布动作账本聚合，历史版本始终只读。" :loading="loading" :empty="!loading&&!rows.length" empty-text="暂无发布记录">
    <template #primary-action><el-button v-hasPermi="['todo:release:list']" size="small" icon="el-icon-download" @click="exportRelease">导出当前查询</el-button></template>
    <template #metrics>
      <config-metric-card label="发布版本" :value="total" icon="el-icon-finished" />
      <config-metric-card label="当前页已发布" :value="publishedCount" icon="el-icon-circle-check" tone="success" />
      <config-metric-card label="当前页已退役" :value="retiredCount" icon="el-icon-remove-outline" tone="warning" />
      <config-metric-card label="当前页回滚来源" :value="rollbackCount" icon="el-icon-refresh-left" />
    </template>
    <template #filters>
      <el-card shadow="never" class="filter-card"><el-form :inline="true" size="small">
        <el-form-item><el-input v-model="query.keyword" clearable placeholder="模板名称或编码" @keyup.enter.native="search" /></el-form-item>
        <el-form-item><el-select v-model="query.status" clearable placeholder="发布状态"><el-option v-for="item in dict.type.law_todo_version_status" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item><el-input v-model="query.publisher" clearable placeholder="发布人" /></el-form-item>
        <el-form-item><el-date-picker v-model="dateRange" type="datetimerange" value-format="yyyy-MM-ddTHH:mm:ss" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间" /></el-form-item>
        <el-form-item><el-button type="primary" icon="el-icon-search" @click="search">查询</el-button><el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button></el-form-item>
      </el-form></el-card>
    </template>
    <el-card shadow="never">
      <el-table :data="rows" @row-click="openDetail">
        <el-table-column prop="templateName" label="模板" min-width="170" />
        <el-table-column prop="templateCode" label="模板编码" min-width="150" />
        <el-table-column label="版本" width="80"><template slot-scope="{row}">v{{ row.versionNo }}</template></el-table-column>
        <el-table-column prop="status" label="发布状态" width="100"><template slot-scope="{row}"><dict-tag :options="dict.type.law_todo_version_status" :value="row.status" /></template></el-table-column>
        <el-table-column prop="changeSummary" label="变更说明" min-width="190" show-overflow-tooltip />
        <el-table-column prop="impactScope" label="影响范围" min-width="140" show-overflow-tooltip />
        <el-table-column prop="publishedBy" label="发布人" width="110" />
        <el-table-column label="发布时间" width="165"><template slot-scope="{row}">{{ format(row.publishedTime || row.updateTime) }}</template></el-table-column>
        <el-table-column label="操作" width="90"><template slot-scope="{row}"><el-button v-hasPermi="['todo:release:list']" type="text" @click.stop="openDetail(row)">详情</el-button></template></el-table-column>
      </el-table>
      <pagination v-show="total>0" :total="total" :page.sync="pageNum" :limit.sync="pageSize" @pagination="load" />
    </el-card>
    <release-record-drawer v-model="drawerOpen" :record="selected" @draft-created="load" />
  </config-page-shell>
</template>
<script>
import ConfigPageShell from '../shared/ConfigPageShell'
import ConfigMetricCard from '../shared/ConfigMetricCard'
import ReleaseRecordDrawer from './ReleaseRecordDrawer'
import { listReleaseRecords } from '@/api/todo-config'
import { collectReleasePages } from './release-model'
export default {
  name: 'TodoConfigRelease', components: { ConfigPageShell, ConfigMetricCard, ReleaseRecordDrawer },
  dicts: ['law_todo_version_status'],
  data() { return { loading: false, rows: [], total: 0, pageNum: 1, pageSize: 20, query: { keyword: '', status: '', publisher: '', beginTime: '', endTime: '' }, dateRange: [], drawerOpen: false, selected: null } },
  computed: { publishedCount() { return this.rows.filter(row => row.status === 'PUBLISHED').length }, retiredCount() { return this.rows.filter(row => row.status === 'RETIRED').length }, rollbackCount() { return this.rows.filter(row => row.rollbackSourceVersionId).length } },
  created() { this.load() },
  methods: {
    params(overrides) { return { ...this.query, beginTime: this.dateRange && this.dateRange[0], endTime: this.dateRange && this.dateRange[1], offset: (this.pageNum - 1) * this.pageSize, limit: this.pageSize, ...(overrides || {}) } },
    async load() { this.loading = true; try { const response = await listReleaseRecords(this.params()); this.rows = response.rows || []; this.total = Number(response.total || 0) } finally { this.loading = false } },
    search() { this.pageNum = 1; this.load() }, resetQuery() { this.query = { keyword: '', status: '', publisher: '', beginTime: '', endTime: '' }; this.dateRange = []; this.pageNum = 1; this.load() },
    openDetail(row) { this.selected = row; this.drawerOpen = true }, format(value) { return value ? this.parseTime(value, '{y}-{m}-{d} {h}:{i}:{s}') : '-' },
    async exportRelease() { try { const rows = await collectReleasePages(params => listReleaseRecords(params), this.params({ offset: 0 }), { pageSize: 500, maxRows: 10000 }); const headers = ['模板名称','模板编码','版本号','发布状态','变更说明','影响范围','发布人','发布时间','回滚来源版本ID']; const values = rows.map(row => [row.templateName,row.templateCode,row.versionNo,row.status,row.changeSummary,row.impactScope,row.publishedBy,row.publishedTime || row.updateTime,row.rollbackSourceVersionId]); const csv = [headers,...values].map(items => items.map(value => `"${String(value == null ? '' : value).replace(/"/g,'""')}"`).join(',')).join('\r\n'); const blob = new Blob(['\ufeff'+csv], { type: 'text/csv;charset=utf-8' }); const link = document.createElement('a'); link.href = URL.createObjectURL(blob); link.download = `todo-release-${Date.now()}.csv`; link.click(); URL.revokeObjectURL(link.href) } catch (error) { this.$modal.msgError((error && error.message) || '导出发布记录失败') } }
  }
}
</script>
<style scoped lang="scss">@import '../styles/config-center.scss';.filter-card{width:100%}.filter-card ::v-deep .el-card__body{padding:14px 16px 0}.filter-card .el-input{width:180px}</style>
