<template>
  <div class="foundation-admission-overview">
    <el-alert title="技术就绪不替代授权业务决策、独立评审或签字。" type="warning" :closable="false" show-icon />
    <el-card class="summary-card">
      <div class="summary-heading">
        <div>
          <div class="summary-label">v0.2 Foundation 总准入</div>
          <strong class="summary-count">{{ readyGateCount }}/{{ totalGateCount }}</strong>
        </div>
        <el-tag :type="report.admitted ? 'success' : 'danger'" size="medium">
          {{ report.overallStatus || 'NOT_ADMITTED' }}
        </el-tag>
      </div>
      <el-progress :percentage="percentage" :status="report.admitted ? 'success' : 'exception'" />
      <p class="summary-note">只有 G-01～G-08 全部 READY，才允许进入阶段一业务实现；本页没有人工覆盖入口。</p>
    </el-card>

    <el-table :data="report.gates || []" border class="gate-table">
      <el-table-column prop="gateCode" label="门禁" width="82" align="center" />
      <el-table-column prop="title" label="准入条件" min-width="190" />
      <el-table-column label="状态" width="100" align="center">
        <template slot-scope="scope"><el-tag :type="scope.row.ready ? 'success' : 'danger'">{{ scope.row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="技术/来源" width="110" align="center">
        <template slot-scope="scope"><el-tag size="mini" :type="scope.row.technicalReady ? 'success' : 'warning'">{{ scope.row.technicalReady ? 'READY' : 'BLOCKED' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="独立证据" width="115" align="center">
        <template slot-scope="scope"><span>{{ scope.row.evidenceStatus }}</span></template>
      </el-table-column>
      <el-table-column prop="summary" label="实测摘要" min-width="230" />
      <el-table-column label="阻断原因" min-width="300">
        <template slot-scope="scope">
          <span v-if="!scope.row.blockers || !scope.row.blockers.length" class="ready-text">无</span>
          <ul v-else class="blockers"><li v-for="item in scope.row.blockers" :key="item">{{ item }}</li></ul>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>
export default {
  name: 'FoundationAdmissionOverview',
  props: {
    report: {
      type: Object,
      default: () => ({ overallStatus: 'NOT_ADMITTED', admitted: false, readyGateCount: 0, totalGateCount: 8, gates: [] })
    }
  },
  computed: {
    readyGateCount() { return Number(this.report.readyGateCount || 0) },
    totalGateCount() { return Number(this.report.totalGateCount || 8) },
    percentage() { return this.totalGateCount ? Math.round(this.readyGateCount * 100 / this.totalGateCount) : 0 }
  }
}
</script>

<style scoped>
.foundation-admission-overview{display:grid;gap:16px}.summary-card{margin-top:12px}.summary-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:14px}.summary-label{color:#606266;font-size:14px}.summary-count{display:block;margin-top:4px;font-size:30px;color:#303133}.summary-note{margin:12px 0 0;color:#606266}.gate-table{width:100%}.blockers{margin:0;padding-left:18px;color:#f56c6c}.ready-text{color:#67c23a}
</style>
