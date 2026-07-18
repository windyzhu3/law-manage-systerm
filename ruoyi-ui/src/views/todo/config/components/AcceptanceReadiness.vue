<template>
  <div class="acceptance-readiness">
    <el-alert v-if="!report.gateReady" title="G-07 阶段一验收门禁未就绪，不能批准准入证据" type="warning" :closable="false" show-icon />
    <el-alert class="mock-warning" title="现有 28 个 Mock E2E 不计入 G-07" description="这些用例只验证静态 dist 与 Mock API，不等同于真实业务验收证据。" type="info" :closable="false" show-icon />
    <el-row :gutter="12" class="metrics">
      <el-col v-for="item in metrics" :key="item.label" :span="4"><el-card shadow="never"><div class="metric-value">{{ item.value }}</div><div class="metric-label">{{ item.label }}</div></el-card></el-col>
    </el-row>

    <el-card shadow="never" class="section-card">
      <div slot="header"><span>G-07 准入要求</span><el-tag :type="report.gateReady ? 'success' : 'danger'" style="float:right">{{ report.gateReady ? 'READY' : 'BLOCKED' }}</el-tag></div>
      <el-table :data="report.requirements || []" size="mini">
        <el-table-column prop="requirementCode" label="要求编码" min-width="220" />
        <el-table-column prop="requirementName" label="要求" min-width="220" />
        <el-table-column label="状态" width="150"><template slot-scope="scope"><el-tag :type="statusType(scope.row.readinessStatus)">{{ statusText(scope.row.readinessStatus) }}</el-tag></template></el-table-column>
        <el-table-column prop="sourceRef" label="事实来源" min-width="260" show-overflow-tooltip />
      </el-table>
    </el-card>

    <el-card shadow="never" class="section-card">
      <div slot="header"><span>验收场景与黄金数据</span><el-button v-hasPermi="['todo:admission:edit']" type="primary" size="mini" style="float:right" @click="editScenario(null)">新增场景</el-button></div>
      <el-table :data="scenarios" size="mini" empty-text="尚未登记验收场景">
        <el-table-column prop="scenarioCode" label="场景编码" min-width="210" />
        <el-table-column prop="scenarioName" label="场景名称" min-width="180" />
        <el-table-column prop="datasetVersion" label="黄金数据版本" width="120" />
        <el-table-column prop="ownerNickName" label="Owner" width="110" />
        <el-table-column prop="acceptorNickName" label="业务验收人" width="120" />
        <el-table-column prop="reviewerNickName" label="独立 Reviewer" width="130" />
        <el-table-column prop="dueAt" label="截止时间" width="170" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column label="操作" width="90" fixed="right"><template slot-scope="scope"><el-button v-hasPermi="['todo:admission:edit']" type="text" @click="editScenario(scope.row)">编辑</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="section-card">
      <div slot="header"><span>114 项 AT 映射</span></div>
      <el-form :inline="true" size="mini" class="filters">
        <el-form-item label="模板"><el-input v-model="filters.templateCode" clearable placeholder="TD-001" /></el-form-item>
        <el-form-item label="维度"><el-select v-model="filters.dimensionCode" clearable><el-option v-for="item in dimensions" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="filters.status" clearable><el-option v-for="item in mappingStatuses" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="$emit('mapping-filter', { ...filters })">查询</el-button><el-button v-hasPermi="['todo:admission:edit']" :disabled="!selection.length" @click="batchVisible = true">批量绑定</el-button></el-form-item>
      </el-form>
      <el-table :data="mappings" size="mini" row-key="mappingId" @selection-change="selection = $event">
        <el-table-column type="selection" width="44" />
        <el-table-column prop="acceptanceRef" label="AT 引用" min-width="210" />
        <el-table-column prop="templateCode" label="模板" width="90" />
        <el-table-column prop="dimensionCode" label="维度" width="95" />
        <el-table-column prop="scenarioCode" label="场景" min-width="190" />
        <el-table-column prop="plannedTestRef" label="计划测试引用" min-width="240" show-overflow-tooltip />
        <el-table-column prop="reviewerNickName" label="独立 Reviewer" width="130" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column label="操作" width="90" fixed="right"><template slot-scope="scope"><el-button v-hasPermi="['todo:admission:edit']" type="text" @click="editMapping(scope.row)">编辑</el-button></template></el-table-column>
      </el-table>
    </el-card>

    <acceptance-scenario-dialog :visible.sync="scenarioVisible" :row="currentScenario" :users="users" :scenario-statuses="scenarioStatuses" @saved="saved" />
    <acceptance-mapping-dialog :visible.sync="mappingVisible" :row="currentMapping" :users="users" :scenarios="scenarios" :mapping-statuses="mappingStatuses" @saved="saved" />
    <el-dialog title="批量绑定 AT" :visible.sync="batchVisible" width="560px">
      <el-alert title="批量操作只绑定场景和测试引用，不会批量审批。" type="warning" :closable="false" />
      <el-form label-width="110px" style="margin-top:16px">
        <el-form-item label="已选 AT">{{ selection.length }} 项</el-form-item>
        <el-form-item label="验收场景"><el-select v-model="batch.scenarioId" filterable style="width:100%"><el-option v-for="item in scenarios" :key="item.scenarioId" :label="`${item.scenarioCode}｜${item.scenarioName}`" :value="item.scenarioId" /></el-select></el-form-item>
        <el-form-item label="计划测试引用"><el-input v-model="batch.plannedTestRef" /></el-form-item>
        <el-form-item label="证据说明"><el-input v-model="batch.evidenceNote" type="textarea" /></el-form-item>
      </el-form>
      <span slot="footer"><el-button @click="batchVisible=false">取消</el-button><el-button type="primary" :loading="batchSaving" @click="saveBatch">绑定为 MAPPED</el-button></span>
    </el-dialog>
  </div>
</template>

<script>
import AcceptanceScenarioDialog from './AcceptanceScenarioDialog'
import AcceptanceMappingDialog from './AcceptanceMappingDialog'
import { batchBindAcceptanceMappings } from '@/api/todo-definition'

export default {
  name: 'AcceptanceReadiness',
  components: { AcceptanceScenarioDialog, AcceptanceMappingDialog },
  props: { report: { type: Object, default: () => ({ requirements: [] }) }, scenarios: { type: Array, default: () => [] }, mappings: { type: Array, default: () => [] }, options: { type: Object, default: () => ({}) } },
  data() { return { filters: { templateCode: '', dimensionCode: '', status: '' }, selection: [], scenarioVisible: false, mappingVisible: false, currentScenario: null, currentMapping: null, batchVisible: false, batchSaving: false, batch: { scenarioId: null, plannedTestRef: '', evidenceNote: '' } } },
  computed: {
    users() { return this.options.users || [] }, dimensions() { return this.options.dimensions || ['OWNER', 'SLA', 'DOD', 'ROUTE', 'HANDLER', 'UI'] },
    scenarioStatuses() { return this.options.scenarioStatuses || ['DRAFT', 'IN_REVIEW', 'APPROVED', 'REJECTED'] },
    mappingStatuses() { return this.options.mappingStatuses || ['UNMAPPED', 'MAPPED', 'IN_REVIEW', 'APPROVED', 'REJECTED'] },
    metrics() { return [
      { label: '一期模板', value: `${this.report.phaseOneTemplateCount || 0}/19` },
      { label: 'AT 目录', value: `${this.report.catalogAtCount || 0}/114` },
      { label: '已映射', value: `${this.report.mappedAtCount || 0}/114` },
      { label: '已批准 AT', value: `${this.report.approvedAtCount || 0}/114` },
      { label: '已批准场景', value: `${this.report.approvedScenarioCount || 0}/${this.report.scenarioCount || 0}` },
      { label: '黄金数据 Ready', value: this.report.goldenScenarioReadyCount || 0 }
    ] }
  },
  methods: {
    editScenario(row) { this.currentScenario = row ? { ...row } : null; this.scenarioVisible = true },
    editMapping(row) { this.currentMapping = { ...row }; this.mappingVisible = true },
    saved() { this.$emit('refresh') },
    statusType(status) { return status === 'READY' ? 'success' : status === 'RUNTIME_MISSING' ? 'danger' : status === 'RUNTIME_INCOMPLETE' ? 'warning' : 'info' },
    statusText(status) { return ({ READY: 'Ready', SOURCE_UNRESOLVED: '来源待确认', RUNTIME_MISSING: '运行态缺失', RUNTIME_INCOMPLETE: '运行态不完整' })[status] || status },
    saveBatch() {
      if (!this.batch.scenarioId || !String(this.batch.plannedTestRef || '').trim()) return this.$modal.msgError('场景和计划测试引用不能为空')
      const data = { actionId: `acceptance-batch-${Date.now()}`, mappings: this.selection.map(row => ({ mappingId: row.mappingId, version: row.version })), ...this.batch }
      this.batchSaving = true
      batchBindAcceptanceMappings(data).then(() => { this.batchVisible = false; this.selection = []; this.$emit('refresh') }).finally(() => { this.batchSaving = false })
    }
  }
}
</script>

<style scoped>
.mock-warning,.metrics,.section-card{margin-top:16px}.metric-value{font-size:22px;font-weight:600;color:#303133}.metric-label{margin-top:6px;color:#909399}.filters{margin-bottom:4px}
</style>
