<template>
  <section data-testid="finance-readiness">
    <el-alert title="本页不生成收费节点、风险公式或财务签字；Q-009/Q-012 未冻结前保持阻断。" type="warning" :closable="false" show-icon />
    <div class="summary-grid">
      <el-card shadow="never"><small>已就绪</small><strong class="ready">{{ n('ready') }}/{{ n('total') }}</strong></el-card>
      <el-card shadow="never"><small>待决策/签字</small><strong class="warning">{{ n('sourceUnresolved') }}</strong></el-card>
      <el-card shadow="never"><small>结构缺失</small><strong class="danger">{{ n('runtimeMissing') }}</strong></el-card>
      <el-card shadow="never"><small>节点字段</small><strong>{{ n('nodeFeeColumns') }}/6</strong></el-card>
      <el-card shadow="never"><small>风险结构</small><strong>{{ n('riskSchemaObjects') }}/4</strong></el-card>
    </div>
    <el-alert :title="report.gateReady ? 'G-06 财务门禁已就绪' : 'G-06 财务门禁未就绪，不能批准准入证据'" :type="report.gateReady ? 'success' : 'error'" :closable="false" show-icon />
    <el-table :data="report.requirements || []" border size="small" class="requirements">
      <el-table-column prop="requirementCode" label="要求编码" min-width="220" />
      <el-table-column prop="requirementName" label="财务要求" min-width="180" />
      <el-table-column label="来源" width="130"><template slot-scope="scope"><el-tag :type="scope.row.sourceStatus === 'CONFIRMED' ? 'success' : 'warning'">{{ source(scope.row.sourceStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="就绪" width="130"><template slot-scope="scope"><el-tag :type="scope.row.readinessStatus === 'READY' ? 'success' : scope.row.readinessStatus === 'SOURCE_UNRESOLVED' ? 'warning' : 'danger'">{{ readiness(scope.row.readinessStatus) }}</el-tag></template></el-table-column>
      <el-table-column prop="decisionRef" label="决策" width="90" />
      <el-table-column prop="sourceRef" label="仓库证据" min-width="260" show-overflow-tooltip />
      <el-table-column prop="remark" label="说明" min-width="260" show-overflow-tooltip />
    </el-table>
  </section>
</template>
<script>
export default {
  name: 'FinanceReadiness',
  props: { report: { type: Object, default: () => ({ requirements: [] }) } },
  methods: {
    n(key) { return Number(this.report[key] || 0) },
    source(value) { return ({ CONFIRMED: '仓库已确认', NEEDS_DECISION: '待业务决策', NEEDS_REVIEW: '待财务签字' })[value] || value },
    readiness(value) { return ({ READY: '已就绪', SOURCE_UNRESOLVED: '来源未闭环', RUNTIME_MISSING: '结构缺失' })[value] || value }
  }
}
</script>
<style scoped>.summary-grid{display:grid;grid-template-columns:repeat(5,minmax(130px,1fr));gap:12px;margin:14px 0}.summary-grid strong{display:block;margin-top:6px;font-size:22px}.summary-grid small{color:#64748b}.ready{color:#16a34a}.warning{color:#d97706}.danger{color:#dc2626}.requirements{margin-top:14px}@media(max-width:1000px){.summary-grid{grid-template-columns:repeat(2,1fr)}}</style>
