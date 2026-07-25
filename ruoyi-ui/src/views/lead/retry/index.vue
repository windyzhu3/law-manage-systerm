<template>
  <div class="lead-workbench" data-permission="lead:retry:list">
    <header class="lead-workbench__heading">
      <div><h2>线索重试队列</h2><p>集中处理 T0、T+1、T+2 七个联系窗口；办理动作仍由 Todo Engine 校验和流转。</p></div>
      <el-button class="lead-retry-button" icon="el-icon-refresh" :loading="loading" @click="load">刷新</el-button>
    </header>
    <div class="lead-workbench__toolbar">
      <el-input v-model.trim="query.keyword" clearable prefix-icon="el-icon-search" placeholder="线索编号、名称、手机号" @keyup.enter.native="search" />
      <el-select v-model="query.status" clearable placeholder="窗口状态">
        <el-option label="待处理" value="PENDING" /><el-option label="已接通" value="CONNECTED" /><el-option label="已耗尽" value="EXHAUSTED" />
      </el-select>
      <el-button type="primary" icon="el-icon-search" @click="search">查询</el-button><el-button @click="reset">重置</el-button>
    </div>
    <div v-if="error" role="alert"><el-alert class="lead-state-error" :title="error" type="error" :closable="false" show-icon /></div>
    <section class="lead-workbench__card">
      <el-table v-loading="loading" :data="rows" row-key="retryRecordId">
        <el-table-column label="线索" min-width="190"><template slot-scope="{ row }"><div class="lead-identity" :data-testid="`lead-row-${row.leadNo}`"><strong>{{ row.leadName }}</strong><small>{{ row.leadNo }} · {{ maskedMobile(row.mobile) }}</small></div></template></el-table-column>
        <el-table-column label="当前窗口" width="130"><template slot-scope="{ row }"><strong>{{ windowLabel(row.windowCode) }}</strong><small class="block-muted">第 {{ row.attemptNo || 0 }} 次</small></template></el-table-column>
        <el-table-column label="窗口时段" min-width="185"><template slot-scope="{ row }"><span>{{ timeText(row.windowStartAt) }}</span><small class="block-muted">截止 {{ timeText(row.windowDueAt || row.dueAt) }}</small></template></el-table-column>
        <el-table-column label="负责人" width="130"><template slot-scope="{ row }">{{ row.todoOwnerName || row.businessOwnerName || '-' }}</template></el-table-column>
        <el-table-column label="SLA" width="120"><template slot-scope="{ row }"><el-tag size="mini" :type="slaMeta(row).type">{{ slaMeta(row).label }}</el-tag></template></el-table-column>
        <el-table-column label="结果" width="120"><template slot-scope="{ row }">{{ resultLabel(row.result || row.factStatus) }}</template></el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template slot-scope="{ row }">
            <el-button v-hasPermi="['lead:retry:handle']" type="primary" plain size="small" :disabled="!canHandle(row)" @click="handle(row)">处理</el-button>
            <el-button type="text" @click="timeline(row)">时间线</el-button>
          </template>
        </el-table-column>
        <template slot="empty"><el-empty description="暂无待处理重试窗口" :image-size="72" /></template>
      </el-table>
      <pagination v-show="total>0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="load" />
    </section>

    <el-drawer title="重试计划时间线" :visible.sync="timelineOpen" size="680px" append-to-body>
      <div class="drawer-body">
        <section class="lead-drawer-section"><h3>{{ selected.leadName || '-' }}</h3><p>{{ selected.leadNo }} · 当前 {{ windowLabel(selected.windowCode) }}</p></section>
        <section class="lead-drawer-section"><lead-retry-timeline v-if="selected.leadId" :lead-id="selected.leadId" /></section>
        <section class="lead-drawer-section"><lead-call-timeline v-if="selected.leadId" :lead-id="selected.leadId" /></section>
      </div>
    </el-drawer>
    <business-todo-drawer :visible.sync="todoOpen" business-type="LEAD" :business-id="selected.leadId || 0" @changed="load" />
  </div>
</template>

<script>
import BusinessTodoDrawer from '@/views/todo/components/BusinessTodoDrawer'
import LeadRetryTimeline from '../components/LeadRetryTimeline'
import LeadCallTimeline from '../components/LeadCallTimeline'
import { listLeadRetry } from '@/api/lead'
import { allowed, errorMessage, isPendingTodo, maskedMobile, slaMeta, timeText } from '../lead-todo-ui'

export default {
  name: 'LeadRetryWorkbench',
  components: { BusinessTodoDrawer, LeadRetryTimeline, LeadCallTimeline },
  data() {
    return { loading: false, error: '', rows: [], total: 0, query: { pageNum: 1, pageSize: 10, status: 'PENDING', keyword: '' }, selected: {}, timelineOpen: false, todoOpen: false }
  },
  created() { this.load() },
  methods: {
    maskedMobile, slaMeta, timeText,
    windowLabel(value) { return ({ T0: 'T0', T1_AM: 'T+1 上午', T1_NOON: 'T+1 中午', T1_PM: 'T+1 下午', T2_AM: 'T+2 上午', T2_NOON: 'T+2 中午', T2_PM: 'T+2 下午', EXHAUSTED: '已耗尽' })[value] || value || '-' },
    resultLabel(value) { return ({ CONNECTED: '已接通', NEXT_WINDOW: '下一窗口', EXHAUSTED: '已耗尽', PENDING: '待处理' })[value] || value || '待处理' },
    canHandle(row) { return isPendingTodo(row) && ['claim', 'start', 'submit', 'complete'].some(action => allowed(row, action)) },
    load() {
      this.loading = true; this.error = ''
      listLeadRetry(this.query).then(response => { this.rows = response.rows || []; this.total = response.total || 0 })
        .catch(error => { this.error = errorMessage(error, '重试队列加载失败') })
        .finally(() => { this.loading = false })
    },
    search() { this.query.pageNum = 1; this.load() },
    reset() { this.query = { pageNum: 1, pageSize: 10, status: 'PENDING', keyword: '' }; this.load() },
    timeline(row) { this.selected = row; this.timelineOpen = true },
    handle(row) { this.selected = row; this.todoOpen = true }
  }
}
</script>

<style scoped lang="scss">
@import "../lead-todo-workbench.scss";
.block-muted{display:block;margin-top:5px;color:#64748b}.drawer-body{padding:18px}
</style>
