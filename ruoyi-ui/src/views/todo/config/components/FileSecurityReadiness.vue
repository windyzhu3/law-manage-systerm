<template>
  <section data-testid="file-security-readiness">
    <el-alert title="技术控制通过不等于安全评审通过；本页不会自动签字、授权或批准 G-05。" type="warning" :closable="false" show-icon />
    <div class="summary-grid">
      <el-card shadow="never"><small>已就绪</small><strong class="ready">{{ n('ready') }}/{{ n('total') }}</strong></el-card>
      <el-card shadow="never"><small>证据/评审待闭环</small><strong class="warning">{{ n('sourceUnresolved') }}</strong></el-card>
      <el-card shadow="never"><small>运行态缺失</small><strong class="danger">{{ n('runtimeMissing') }}</strong></el-card>
      <el-card shadow="never"><small>单次关系令牌</small><strong>{{ yes(report.tokenControlReady) }}</strong></el-card>
      <el-card shadow="never"><small>访问审计/清理补偿</small><strong>{{ yes(report.accessAuditReady && report.cleanupCompensationReady) }}</strong></el-card>
    </div>
    <el-alert :title="report.gateReady ? 'G-05 文件安全门禁已就绪' : 'G-05 文件安全门禁未就绪，不能批准准入证据'" :type="report.gateReady ? 'success' : 'error'" :closable="false" show-icon />
    <el-table :data="report.requirements || []" border size="small" class="controls">
      <el-table-column prop="requirementCode" label="控制编码" min-width="220" />
      <el-table-column prop="requirementName" label="安全控制" min-width="190" />
      <el-table-column label="来源状态" width="130"><template slot-scope="scope"><el-tag :type="scope.row.sourceStatus === 'CONFIRMED' ? 'success' : 'warning'">{{ source(scope.row.sourceStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="就绪状态" width="130"><template slot-scope="scope"><el-tag :type="scope.row.readinessStatus === 'READY' ? 'success' : scope.row.readinessStatus === 'SOURCE_UNRESOLVED' ? 'warning' : 'danger'">{{ readiness(scope.row.readinessStatus) }}</el-tag></template></el-table-column>
      <el-table-column prop="sourceRef" label="仓库证据" min-width="280" show-overflow-tooltip />
      <el-table-column prop="remark" label="说明" min-width="260" show-overflow-tooltip />
    </el-table>
  </section>
</template>
<script>
export default { name: 'FileSecurityReadiness', props: { report: { type: Object, default: () => ({ requirements: [] }) } }, methods: {
  n(key) { return Number(this.report[key] || 0) }, yes(value) { return value ? 'READY' : '缺失' },
  source(value) { return ({ CONFIRMED: '仓库已确认', NEEDS_EVIDENCE: '待验收证据', NEEDS_REVIEW: '待安全评审' })[value] || value },
  readiness(value) { return ({ READY: '已就绪', SOURCE_UNRESOLVED: '来源未闭环', RUNTIME_MISSING: '运行态缺失' })[value] || value }
} }
</script>
<style scoped>.summary-grid{display:grid;grid-template-columns:repeat(5,minmax(130px,1fr));gap:12px;margin:14px 0}.summary-grid strong{display:block;margin-top:6px;font-size:22px}.summary-grid small{color:#64748b}.ready{color:#16a34a}.warning{color:#d97706}.danger{color:#dc2626}.controls{margin-top:14px}@media(max-width:1000px){.summary-grid{grid-template-columns:repeat(2,1fr)}}</style>
