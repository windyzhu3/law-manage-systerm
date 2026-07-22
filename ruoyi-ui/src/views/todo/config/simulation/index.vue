<template>
  <config-page-shell
    title="模拟测试"
    subtitle="在不写入业务事实、不创建真实待办的前提下，验证模板匹配和执行路径。"
    :loading="loading"
    :empty="!loading && !rows.length"
    empty-text="暂无可模拟的待办模板"
  >
    <template #primary-action>
      <el-button v-hasPermi="['todo:simulation:simulate']" type="primary" size="small" icon="el-icon-video-play" @click="openSimulation()">开始模拟</el-button>
    </template>
    <template #metrics>
      <config-metric-card label="可用模板" :value="total" icon="el-icon-document" />
      <config-metric-card label="有效事件版本" :value="activeEvents.length" icon="el-icon-connection" tone="success" />
      <config-metric-card label="本次模拟状态" :value="lastStatus" icon="el-icon-data-analysis" :tone="lastStatus==='已完成'?'success':'warning'" />
      <config-metric-card label="副作用" value="0" helper="不创建真实待办" icon="el-icon-lock" />
    </template>
    <template #filters>
      <el-alert class="readonly-notice" title="只读模拟，不创建真实待办" type="info" :closable="false" show-icon />
    </template>
    <el-card shadow="never">
      <el-table :data="rows" @row-click="openSimulation">
        <el-table-column prop="templateName" label="模板名称" min-width="180" />
        <el-table-column prop="templateCode" label="模板编码" min-width="160" />
        <el-table-column prop="businessType" label="业务对象" width="130"><template slot-scope="{row}"><dict-tag :options="dict.type.law_todo_business_type" :value="row.businessType" /></template></el-table-column>
        <el-table-column prop="eventType" label="触发事件" min-width="180" />
        <el-table-column prop="publishStatus" label="版本状态" width="110" />
        <el-table-column label="操作" width="100"><template slot-scope="{row}"><el-button v-hasPermi="['todo:simulation:simulate']" type="text" @click.stop="openSimulation(row)">模拟</el-button></template></el-table-column>
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
import { listTodoTemplates, listTemplateEventCatalog } from '@/api/todo-config'

export default {
  name: 'TodoConfigSimulation',
  components: { ConfigPageShell, ConfigMetricCard, SimulationDrawer },
  dicts: ['law_todo_business_type'],
  data() { return { loading: false, rows: [], total: 0, eventCatalog: [], query: { pageNum: 1, pageSize: 20 }, drawerOpen: false, selected: null, handoffEvent: null, lastStatus: '未运行' } },
  computed: { activeEvents() { return this.eventCatalog.filter(item => item.status === 'ACTIVE') } },
  created() { this.loadCatalog().then(() => this.openHandoff()); this.load() },
  methods: {
    async loadCatalog() { const response = await listTemplateEventCatalog(); this.eventCatalog = response.data || [] },
    async load() { this.loading = true; try { const response = await listTodoTemplates(this.query); this.rows = response.rows || []; this.total = Number(response.total || 0) } finally { this.loading = false } },
    openHandoff() { const query = this.$route.query || {}; if (!query.eventType) return; this.handoffEvent = this.activeEvents.find(item => item.eventType === query.eventType && Number(item.payloadVersion) === Number(query.payloadVersion || 1)) || null; if (this.handoffEvent) this.openSimulation() },
    openSimulation(row) { this.selected = row || null; if (row) this.handoffEvent = null; this.drawerOpen = true }
  }
}
</script>

<style scoped lang="scss">
@import '../styles/config-center.scss';
.readonly-notice{width:100%}
</style>
