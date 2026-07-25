<template>
  <section class="retry-timeline" aria-label="线索重试窗口时间线">
    <header>
      <div><h3>重试窗口</h3><p>T0、T+1、T+2 按策略依次执行，已结束窗口保留完整审计。</p></div>
      <el-button type="text" icon="el-icon-refresh" :loading="loading" aria-label="刷新重试时间线" @click="load">刷新</el-button>
    </header>
    <el-alert v-if="error" role="alert" :title="error" type="error" :closable="false" show-icon>
      <el-button type="text" @click="load">重新加载</el-button>
    </el-alert>
    <div v-loading="loading" class="retry-timeline__body">
      <el-steps v-if="rows.length" direction="vertical" :active="activeIndex" finish-status="success" process-status="process">
        <el-step v-for="row in rows" :key="row.retryRecordId || row.occurrenceId || row.windowCode">
          <template slot="title">
            <span>{{ windowLabel(row.windowCode) }}</span>
            <el-tag size="mini" :type="resultType(row.result || row.factStatus)">{{ resultLabel(row.result || row.factStatus) }}</el-tag>
          </template>
          <template slot="description">
            <div class="retry-timeline__description">
              <span>第 {{ row.attemptNo || 0 }} 次拨打</span>
              <span>{{ timeText(row.windowStartAt) }} — {{ timeText(row.windowDueAt) }}</span>
              <span>下一窗口：{{ windowLabel(row.nextWindowCode) }}</span>
              <small v-if="row.detail">{{ row.detail }}</small>
            </div>
          </template>
        </el-step>
      </el-steps>
      <el-empty v-else-if="!loading" description="尚未创建重试计划" :image-size="64" />
    </div>
  </section>
</template>

<script>
import { getLeadRetryTimeline } from '@/api/lead'
import { errorMessage, timeText } from '../lead-todo-ui'

export default {
  name: 'LeadRetryTimeline',
  props: { leadId: { type: [Number, String], required: true }, value: { type: Array, default: null } },
  data() { return { loading: false, error: '', loadedRows: [] } },
  computed: {
    rows() { return Array.isArray(this.value) ? this.value : this.loadedRows },
    activeIndex() {
      const pending = this.rows.findIndex(row => !['CONNECTED', 'EXHAUSTED', 'COMPLETED', 'CANCELLED'].includes(row.result || row.factStatus))
      return pending < 0 ? this.rows.length : pending
    }
  },
  watch: { leadId: { immediate: true, handler() { if (!Array.isArray(this.value)) this.load() } } },
  methods: {
    timeText,
    windowLabel(value) {
      return ({ T0: 'T0 首次窗口', T1_AM: 'T+1 上午', T1_NOON: 'T+1 中午', T1_PM: 'T+1 下午',
        T2_AM: 'T+2 上午', T2_NOON: 'T+2 中午', T2_PM: 'T+2 下午', EXHAUSTED: '已耗尽' })[value] || value || '-'
    },
    resultLabel(value) { return ({ CONNECTED: '已接通', NEXT_WINDOW: '进入下一窗口', EXHAUSTED: '已耗尽', PENDING: '待处理', COMPLETED: '已完成', CANCELLED: '已取消' })[value] || value || '待执行' },
    resultType(value) { return ({ CONNECTED: 'success', COMPLETED: 'success', EXHAUSTED: 'info', CANCELLED: 'info', NEXT_WINDOW: 'warning' })[value] || 'primary' },
    load() {
      if (!(Number(this.leadId) > 0) || Array.isArray(this.value)) return
      this.loading = true; this.error = ''
      getLeadRetryTimeline(this.leadId).then(response => { this.loadedRows = response.data || [] })
        .catch(error => { this.error = errorMessage(error, '重试时间线加载失败') })
        .finally(() => { this.loading = false })
    }
  }
}
</script>

<style scoped>
.retry-timeline header{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:14px}.retry-timeline h3{margin:0;color:#0f172a;font-size:16px}.retry-timeline header p{margin:5px 0 0;color:#64748b;font-size:12px}.retry-timeline__body{min-height:100px}.retry-timeline .el-tag{margin-left:8px}.retry-timeline__description{display:grid;gap:4px;padding:7px 0 16px;color:#64748b}.retry-timeline__description small{color:#334155}
</style>
