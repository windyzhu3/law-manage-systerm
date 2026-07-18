<template>
  <section class="historical-migration-readiness" data-testid="historical-migration-readiness">
    <el-alert title="此页面只核验历史迁移契约，不会选择默认业务线，也不会修改历史案件或待办。" type="warning" :closable="false" show-icon />
    <div v-loading="preflightLoading" class="preflight-section">
      <div class="preflight-heading">
        <div>
          <h3>历史数据预检</h3>
          <small>实时只读盘点，不修改案件、待办或 G-04 状态</small>
        </div>
        <el-button v-hasPermi="['todo:admission:export']" type="primary" size="small" :loading="exporting" @click="exportExceptions">导出异常候选清单</el-button>
      </div>
      <el-alert v-if="preflightError" :title="preflightError" type="error" :closable="false" show-icon />
      <template v-else>
        <div class="preflight-summary-grid">
          <el-card shadow="never"><small>有效历史案件</small><strong>{{ preflightNumber('activeCaseCount') }}</strong></el-card>
          <el-card shadow="never"><small>已删除案件</small><strong>{{ preflightNumber('deletedCaseCount') }}</strong></el-card>
          <el-card shadow="never"><small>历史待办</small><strong>{{ preflightNumber('historicalTodoCount') }}</strong></el-card>
          <el-card shadow="never"><small>孤儿版本引用</small><strong class="danger">{{ preflightNumber('orphanTodoVersionCount') }}</strong></el-card>
          <el-card shadow="never"><small>异常候选案件</small><strong class="warning">{{ preflightNumber('exceptionCandidateCount') }}</strong></el-card>
        </div>
        <el-alert title="清单尚未分类、尚未签字，不会自动改变 G-04" type="warning" :closable="false" show-icon />
        <el-table class="preflight-groups" :data="preflight.groups || []" border size="small" empty-text="暂无历史案件分组">
          <el-table-column prop="caseStatus" label="案件状态" min-width="160" />
          <el-table-column prop="caseType" label="案件类型" min-width="180" />
          <el-table-column prop="caseCount" label="案件数量" width="130" />
        </el-table>
        <div v-if="lastExport" class="last-export">
          <span>导出行数：{{ lastExport.rowCount }}</span>
          <span>分类状态：<strong>{{ lastExport.classificationState }}</strong></span>
          <span>CSV SHA-256：</span><code>{{ lastExport.csvSha256 }}</code>
        </div>
      </template>
    </div>
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
import { saveAs } from 'file-saver'
import { getHistoricalMigrationPreflight, exportHistoricalMigrationExceptions } from '@/api/todo-definition'

export default {
  name: 'HistoricalMigrationReadiness',
  props: { report: { type: Object, default: () => ({ requirements: [] }) } },
  data() {
    return { preflight: { groups: [] }, preflightLoading: false, preflightError: '', exporting: false, lastExport: null }
  },
  mounted() { this.loadPreflight() },
  methods: {
    number(key) { return Number(this.report[key] || 0) },
    preflightNumber(key) { return Number(this.preflight[key] || 0) },
    loadPreflight() {
      this.preflightLoading = true
      this.preflightError = ''
      return getHistoricalMigrationPreflight().then(response => { this.preflight = response.data || { groups: [] } })
        .catch(() => { this.preflightError = '历史数据预检加载失败，原准入目录未受影响' })
        .finally(() => { this.preflightLoading = false })
    },
    exportExceptions() {
      this.exporting = true
      return exportHistoricalMigrationExceptions().then(result => {
        saveAs(result.blob, 'g04-historical-case-preflight.zip')
        this.lastExport = { rowCount: result.rowCount, csvSha256: result.csvSha256, classificationState: 'UNREVIEWED' }
      }).finally(() => { this.exporting = false })
    },
    sourceLabel(status) { return ({ CONFIRMED: '仓库已确认', NEEDS_DECISION: '待业务决策', NEEDS_EVIDENCE: '待迁移证据' })[status] || status },
    readinessLabel(status) { return ({ READY: '已就绪', SOURCE_UNRESOLVED: '来源未闭环', RUNTIME_MISSING: '运行态缺失', RUNTIME_INVALID: '运行态无效' })[status] || status },
    sourceType(status) { return status === 'CONFIRMED' ? 'success' : 'warning' },
    readinessType(status) { return status === 'READY' ? 'success' : status === 'SOURCE_UNRESOLVED' ? 'warning' : 'danger' }
  }
}
</script>

<style scoped>
.preflight-section{margin:14px 0;padding:16px;border:1px solid #e5e7eb;border-radius:4px}.preflight-heading{display:flex;align-items:center;justify-content:space-between;gap:16px}.preflight-heading h3{margin:0 0 4px}.preflight-heading small{color:#64748b}.preflight-summary-grid,.summary-grid{display:grid;grid-template-columns:repeat(5,minmax(130px,1fr));gap:12px;margin:14px 0}.preflight-summary-grid strong,.summary-grid strong{display:block;margin-top:6px;font-size:26px}.preflight-summary-grid small,.summary-grid small{color:#64748b}.preflight-groups{margin-top:14px}.last-export{display:flex;align-items:center;flex-wrap:wrap;gap:8px 16px;margin-top:14px;padding:12px;background:#f8fafc}.last-export code{word-break:break-all}.ready{color:#16a34a}.warning{color:#d97706}.danger{color:#dc2626}.inventory{margin:14px 0}@media(max-width:1000px){.preflight-summary-grid,.summary-grid{grid-template-columns:repeat(2,1fr)}}
</style>
