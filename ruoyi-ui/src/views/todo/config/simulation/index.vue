<template>
  <config-page-shell
    title="模拟测试"
    subtitle="先用批量健康检查确认所有已发布模板，再针对真实业务对象执行单次只读模拟。"
    :loading="loading"
    :empty="!loading && !rows.length"
    empty-text="暂无可模拟的已发布待办模板"
  >
    <template #primary-action>
      <el-button v-hasPermi="['todo:simulation:simulate']" size="small" icon="el-icon-s-check" :loading="diagnosing" @click="runPublishedDiagnostics">批量健康检查</el-button>
      <el-button v-hasPermi="['todo:simulation:simulate']" type="primary" size="small" icon="el-icon-video-play" @click="openSimulation()">开始单次模拟</el-button>
    </template>
    <template #metrics>
      <config-metric-card label="已发布模板" :value="total" icon="el-icon-document-checked" />
      <config-metric-card label="有效事件版本" :value="activeEvents.length" icon="el-icon-connection" tone="success" />
      <config-metric-card label="批量通过" :value="diagnostic.passed || 0" icon="el-icon-circle-check" tone="success" />
      <config-metric-card label="待处理" :value="diagnosticProblems" icon="el-icon-warning-outline" :tone="diagnosticProblems ? 'warning' : 'success'" />
    </template>
    <template #filters>
      <el-alert class="readonly-notice" title="只读模拟，不创建真实待办，也不修改客户、合同、案件或财务数据" type="info" :closable="false" show-icon />
    </template>

    <el-card v-if="diagnostic.total" class="diagnostic-card" shadow="never">
      <div slot="header" class="diagnostic-header">
        <div><strong>已发布模板健康检查</strong><small>使用事件目录中的标准样例，依次验证触发、负责人、SLA、完成条件和路由。</small></div>
        <el-tag :type="diagnostic.failed ? 'danger' : diagnostic.warning ? 'warning' : 'success'">{{ diagnostic.failed ? '存在阻断' : diagnostic.warning ? '需要确认' : '全部通过' }}</el-tag>
      </div>
      <el-table :data="diagnostic.items" size="small" border>
        <el-table-column prop="templateName" label="模板" min-width="170"><template slot-scope="{ row }"><strong>{{ row.templateName }}</strong><small class="code">{{ row.templateCode }}</small></template></el-table-column>
        <el-table-column prop="eventType" label="事件" min-width="185" />
        <el-table-column prop="ownerStatus" label="负责人" width="110"><template slot-scope="{ row }"><el-tag size="mini" :type="row.ownerStatus === 'RESOLVED' ? 'success' : 'warning'">{{ ownerLabel(row.ownerStatus) }}</el-tag></template></el-table-column>
        <el-table-column prop="status" label="检查结果" width="105"><template slot-scope="{ row }"><el-tag size="mini" :type="statusTone(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="message" label="诊断说明" min-width="230" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right"><template slot-scope="{ row }"><el-button type="text" @click="openDiagnostic(row)">单独模拟</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <div slot="header" class="list-header"><strong>可模拟模板</strong><small>仅展示当前发布版本</small></div>
      <el-table :data="rows" @row-click="openSimulation">
        <el-table-column prop="templateName" label="模板名称" min-width="180" />
        <el-table-column prop="templateCode" label="模板编码" min-width="170" />
        <el-table-column prop="businessType" label="业务对象" width="130"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_todo_business_type" :value="row.businessType" /></template></el-table-column>
        <el-table-column prop="eventType" label="触发事件" min-width="190" />
        <el-table-column prop="publishedVersionNo" label="发布版本" width="100"><template slot-scope="{ row }">V{{ row.publishedVersionNo || '-' }}</template></el-table-column>
        <el-table-column label="操作" width="100"><template slot-scope="{ row }"><el-button v-hasPermi="['todo:simulation:simulate']" type="text" @click.stop="openSimulation(row)">模拟</el-button></template></el-table-column>
      </el-table>
      <pagination v-show="total>0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="load" />
    </el-card>
    <template #persistent><simulation-drawer v-model="drawerOpen" :events="activeEvents" :initial-template="selected" :initial-event="handoffEvent" @simulated="lastStatus='已完成'" /></template>
  </config-page-shell>
</template>

<script>
import ConfigPageShell from '../shared/ConfigPageShell'
import ConfigMetricCard from '../shared/ConfigMetricCard'
import SimulationDrawer from './SimulationDrawer'
import { diagnosePublishedConfigurations, listTodoTemplates, listTemplateEventCatalog } from '@/api/todo-config'

export default {
  name: 'TodoConfigSimulation', components: { ConfigPageShell, ConfigMetricCard, SimulationDrawer }, dicts: ['law_todo_business_type'],
  data() { return { loading: false, diagnosing: false, rows: [], total: 0, eventCatalog: [], query: { pageNum: 1, pageSize: 20, publishStatus: 'PUBLISHED' }, drawerOpen: false, selected: null, handoffEvent: null, lastStatus: '未运行', diagnostic: { total: 0, passed: 0, warning: 0, failed: 0, items: [] } } },
  computed: { activeEvents() { return this.eventCatalog.filter(item => item.status === 'ACTIVE') }, diagnosticProblems() { return Number(this.diagnostic.warning || 0) + Number(this.diagnostic.failed || 0) } },
  created() { this.loadCatalog().then(() => this.openHandoff()); this.load() },
  methods: {
    async loadCatalog() { const response = await listTemplateEventCatalog(); this.eventCatalog = response.data || [] },
    async load() { this.loading = true; try { const response = await listTodoTemplates(this.query); this.rows = response.rows || []; this.total = Number(response.total || 0) } finally { this.loading = false } },
    async runPublishedDiagnostics() { this.diagnosing = true; try { const response = await diagnosePublishedConfigurations(); this.diagnostic = response.data || { total: 0, passed: 0, warning: 0, failed: 0, items: [] }; if (this.diagnostic.failed) this.$modal.msgError(`检查完成：${this.diagnostic.failed} 个模板存在阻断`); else if (this.diagnostic.warning) this.$modal.msgWarning(`检查完成：${this.diagnostic.warning} 个模板需要确认`); else this.$modal.msgSuccess(`检查完成：${this.diagnostic.passed} 个模板全部通过`) } finally { this.diagnosing = false } },
    openHandoff() { const query = this.$route.query || {}; if (!query.eventType) return; this.handoffEvent = this.activeEvents.find(item => item.eventType === query.eventType && Number(item.payloadVersion) === Number(query.payloadVersion || 1)) || null; if (this.handoffEvent) this.openSimulation() },
    openSimulation(row) { this.selected = row || null; if (row) this.handoffEvent = null; this.drawerOpen = true },
    openDiagnostic(item) { const row = this.rows.find(candidate => Number(candidate.templateId) === Number(item.templateId)) || { templateId: item.templateId, templateCode: item.templateCode, templateName: item.templateName, businessType: item.businessType, publishedVersionId: item.versionId, eventType: item.eventType }; this.openSimulation(row) },
    statusTone(status) { return status === 'PASSED' ? 'success' : status === 'WARNING' ? 'warning' : 'danger' },
    statusLabel(status) { return ({ PASSED: '通过', WARNING: '需确认', FAILED: '阻断' })[status] || status },
    ownerLabel(status) { return ({ RESOLVED: '已解析', UNRESOLVED: '未解析', UNKNOWN: '未知', SKIPPED: '未执行' })[status] || status }
  }
}
</script>

<style scoped lang="scss">
@import '../styles/config-center.scss';
.readonly-notice{width:100%}.diagnostic-card{margin-bottom:16px}.diagnostic-header,.list-header{display:flex;align-items:center;justify-content:space-between;gap:16px}.diagnostic-header small,.list-header small,.code{display:block;margin-top:4px;color:#8392a5;font-size:12px}.diagnostic-card strong{color:#253858}.diagnostic-card ::v-deep .el-table__row td{vertical-align:top}
</style>
