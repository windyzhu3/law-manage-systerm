<template>
  <section class="historical-migration-readiness" data-testid="historical-migration-readiness">
    <el-alert title="此页面只核验历史迁移契约，不会选择默认业务线，也不会修改历史案件或待办。" type="warning" :closable="false" show-icon />
    <div class="summary-grid">
      <el-card shadow="never"><small>历史案件</small><strong>{{ number('historicalCaseCount') }}</strong></el-card>
      <el-card shadow="never"><small>历史待办</small><strong>{{ number('historicalTodoCount') }}</strong></el-card>
      <el-card shadow="never"><small>已就绪要求</small><strong class="ready">{{ number('ready') }}/{{ number('total') }}</strong></el-card>
      <el-card shadow="never"><small>来源未闭环</small><strong class="warning">{{ number('sourceUnresolved') }}</strong></el-card>
      <el-card shadow="never"><small>孤儿版本引用</small><strong class="danger">{{ number('orphanTodoVersionCount') }}</strong></el-card>
    </div>
    <el-alert :title="report.gateReady ? 'G-04 历史迁移门禁已就绪' : 'G-04 历史迁移门禁未就绪，不能批准准入证据'" :type="report.gateReady ? 'success' : 'error'" :closable="false" show-icon />
    <el-descriptions :column="2" border size="small" class="inventory">
      <el-descriptions-item label="案件业务线字段">{{ report.caseBusinessLineColumnExists ? '已存在' : '缺失' }}</el-descriptions-item>
      <el-descriptions-item label="运行态异常">缺失 {{ number('runtimeMissing') }} / 无效 {{ number('runtimeInvalid') }}</el-descriptions-item>
    </el-descriptions>
    <el-table :data="report.requirements || []" border size="small">
      <el-table-column prop="requirementCode" label="要求编码" min-width="210" />
      <el-table-column prop="requirementName" label="迁移要求" min-width="180" />
      <el-table-column label="来源状态" width="130"><template slot-scope="scope"><el-tag :type="sourceType(scope.row.sourceStatus)">{{ sourceLabel(scope.row.sourceStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="运行判定" width="140"><template slot-scope="scope"><el-tag :type="readinessType(scope.row.readinessStatus)">{{ readinessLabel(scope.row.readinessStatus) }}</el-tag></template></el-table-column>
      <el-table-column prop="decisionRef" label="决策" width="90"><template slot-scope="scope">{{ scope.row.decisionRef || '-' }}</template></el-table-column>
      <el-table-column prop="sourceRef" label="仓库证据" min-width="260" show-overflow-tooltip />
      <el-table-column prop="remark" label="说明" min-width="280" show-overflow-tooltip />
    </el-table>
  </section>
</template>

<script>
export default {
  name: 'HistoricalMigrationReadiness',
  props: { report: { type: Object, default: () => ({ requirements: [] }) } },
  methods: {
    number(key) { return Number(this.report[key] || 0) },
    sourceLabel(status) { return ({ CONFIRMED: '仓库已确认', NEEDS_DECISION: '待业务决策', NEEDS_EVIDENCE: '待迁移证据' })[status] || status },
    readinessLabel(status) { return ({ READY: '已就绪', SOURCE_UNRESOLVED: '来源未闭环', RUNTIME_MISSING: '运行态缺失', RUNTIME_INVALID: '运行态无效' })[status] || status },
    sourceType(status) { return status === 'CONFIRMED' ? 'success' : 'warning' },
    readinessType(status) { return status === 'READY' ? 'success' : status === 'SOURCE_UNRESOLVED' ? 'warning' : 'danger' }
  }
}
</script>

<style scoped>
.summary-grid{display:grid;grid-template-columns:repeat(5,minmax(130px,1fr));gap:12px;margin:14px 0}.summary-grid strong{display:block;margin-top:6px;font-size:26px}.summary-grid small{color:#64748b}.ready{color:#16a34a}.warning{color:#d97706}.danger{color:#dc2626}.inventory{margin:14px 0}@media(max-width:1000px){.summary-grid{grid-template-columns:repeat(2,1fr)}}
</style>
