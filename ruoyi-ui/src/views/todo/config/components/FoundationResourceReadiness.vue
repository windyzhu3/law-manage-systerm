<template>
  <section class="foundation-resource-readiness" data-testid="foundation-resource-readiness">
    <el-alert
      title="这里只展示仓库要求与运行态差距；未确认值不会自动写入系统字典，角色也不会自动创建或授权。"
      type="warning"
      :closable="false"
      show-icon
    />
    <div class="summary-grid">
      <el-card shadow="never"><small>总资源</small><strong>{{ number('total') }}</strong></el-card>
      <el-card shadow="never"><small>已就绪</small><strong class="ready">{{ number('ready') }}</strong></el-card>
      <el-card shadow="never"><small>来源待确认</small><strong class="warning">{{ number('sourceUnresolved') }}</strong></el-card>
      <el-card shadow="never"><small>运行态缺失</small><strong class="danger">{{ number('runtimeMissing') }}</strong></el-card>
      <el-card shadow="never"><small>运行态不完整</small><strong class="danger">{{ number('runtimeIncomplete') }}</strong></el-card>
    </div>
    <el-alert
      :title="report.gateReady ? 'G-02 资源门禁已就绪' : 'G-02 资源门禁未就绪，不能批准准入证据'"
      :type="report.gateReady ? 'success' : 'error'"
      :closable="false"
      show-icon
    />
    <div class="filters">
      <el-input v-model="keyword" clearable size="small" placeholder="搜索资源编码或领域" />
      <el-select v-model="status" clearable size="small" placeholder="全部状态">
        <el-option v-for="item in statuses" :key="item" :label="statusLabel(item)" :value="item" />
      </el-select>
      <el-select v-model="phase" clearable size="small" placeholder="全部阶段">
        <el-option label="阶段一" value="PHASE_ONE" /><el-option label="阶段二" value="PHASE_TWO" />
      </el-select>
    </div>
    <el-table :data="filtered" style="margin-top: 12px">
      <el-table-column label="资源" min-width="220">
        <template slot-scope="scope"><div><el-tag size="mini" type="info">{{ scope.row.resourceType }}</el-tag> {{ scope.row.resourceCode }}</div><small>{{ scope.row.domainCode }} · {{ scope.row.deliveryPhase }}</small></template>
      </el-table-column>
      <el-table-column label="来源状态" width="150">
        <template slot-scope="scope"><el-tag :type="sourceType(scope.row.sourceStatus)">{{ scope.row.sourceStatus }}</el-tag><div v-if="scope.row.decisionRef"><small>{{ scope.row.decisionRef }}</small></div></template>
      </el-table-column>
      <el-table-column label="运行态" width="150">
        <template slot-scope="scope"><el-tag :type="readinessType(scope.row.readinessStatus)">{{ statusLabel(scope.row.readinessStatus) }}</el-tag><div><small>{{ scope.row.activeItemCount }}/{{ requiredCount(scope.row) }}</small></div></template>
      </el-table-column>
      <el-table-column label="仓库明确值" min-width="220" show-overflow-tooltip>
        <template slot-scope="scope">{{ expectedValues(scope.row.expectedValuesJson) }}</template>
      </el-table-column>
      <el-table-column label="来源" min-width="260" show-overflow-tooltip prop="sourceRef" />
      <el-table-column label="说明" min-width="260" show-overflow-tooltip prop="remark" />
    </el-table>
  </section>
</template>

<script>
export default {
  name: 'FoundationResourceReadiness',
  props: { report: { type: Object, default: () => ({ resources: [] }) } },
  data() { return { keyword: '', status: '', phase: '', statuses: ['SOURCE_UNRESOLVED', 'RUNTIME_MISSING', 'RUNTIME_INCOMPLETE', 'READY'] } },
  computed: {
    filtered() {
      const keyword = this.keyword.trim().toLowerCase()
      return (this.report.resources || []).filter(row => {
        const text = `${row.resourceCode || ''} ${row.domainCode || ''}`.toLowerCase()
        return (!keyword || text.includes(keyword)) && (!this.status || row.readinessStatus === this.status) && (!this.phase || row.deliveryPhase === this.phase)
      })
    }
  },
  methods: {
    number(key) { return Number(this.report[key] || 0) },
    requiredCount(row) { return Math.max(Number(row.minimumActiveItems || 0), Number(row.expectedItemCount || 0)) },
    expectedValues(json) {
      if (!json) return '-'
      try { const values = typeof json === 'string' ? JSON.parse(json) : json; return values.map(item => item.value || item.label).filter(Boolean).join('、') || '-' } catch (_) { return '格式错误' }
    },
    statusLabel(status) { return ({ SOURCE_UNRESOLVED: '来源待确认', RUNTIME_MISSING: '运行态缺失', RUNTIME_INCOMPLETE: '运行态不完整', READY: '已就绪' })[status] || status },
    sourceType(status) { return status === 'CONFIRMED' ? 'success' : status === 'CONFLICTING' ? 'danger' : 'warning' },
    readinessType(status) { return ({ READY: 'success', SOURCE_UNRESOLVED: 'warning', RUNTIME_MISSING: 'danger', RUNTIME_INCOMPLETE: 'danger' })[status] || 'info' }
  }
}
</script>

<style scoped>
.summary-grid{display:grid;grid-template-columns:repeat(5,minmax(130px,1fr));gap:12px;margin:14px 0}.summary-grid .el-card strong{display:block;margin-top:6px;font-size:26px}.summary-grid small,.el-table small{color:#64748b}.ready{color:#16a34a}.warning{color:#d97706}.danger{color:#dc2626}.filters{display:flex;gap:10px;margin-top:14px}.filters .el-input{max-width:300px}.filters .el-select{width:180px}@media(max-width:1000px){.summary-grid{grid-template-columns:repeat(2,1fr)}}
</style>
