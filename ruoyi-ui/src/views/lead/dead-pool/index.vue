<template>
  <div class="lead-workbench" data-permission="lead:dead-pool:list">
    <header class="lead-workbench__heading">
      <div><h2>Dead-Pool</h2><p>已确认无效线索的独立隔离区。这里不属于公海，不提供普通领取操作。</p></div>
      <el-button class="lead-retry-button" icon="el-icon-refresh" :loading="loading" @click="load">刷新</el-button>
    </header>
    <div class="lead-workbench__toolbar">
      <el-input v-model.trim="query.keyword" clearable prefix-icon="el-icon-search" placeholder="线索编号、名称、手机号" @keyup.enter.native="search" />
      <el-select v-model="query.reasonCode" clearable placeholder="无效原因">
        <el-option v-for="item in reasonOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button type="primary" icon="el-icon-search" @click="search">查询</el-button><el-button @click="reset">重置</el-button>
    </div>
    <div v-if="error" role="alert"><el-alert class="lead-state-error" :title="error" type="error" :closable="false" show-icon /></div>
    <section class="lead-workbench__card">
      <el-table v-loading="loading" :data="rows" row-key="leadId">
        <el-table-column label="线索" min-width="200"><template slot-scope="{ row }"><div class="lead-identity" :data-testid="`lead-row-${row.leadNo}`"><strong>{{ row.leadName }}</strong><small>{{ row.leadNo }} · {{ maskedMobile(row.mobile) }}</small></div></template></el-table-column>
        <el-table-column label="确认无效原因" min-width="180"><template slot-scope="{ row }"><el-tag size="mini" type="danger">{{ reasonLabel(row.reasonCode) }}</el-tag><p class="cell-detail">{{ row.detail || '-' }}</p></template></el-table-column>
        <el-table-column label="事实来源" min-width="150"><template slot-scope="{ row }"><strong>{{ row.reviewerName || '-' }}</strong><small class="block-muted">{{ timeText(row.factTime) }}</small></template></el-table-column>
        <el-table-column label="隔离状态" width="120"><template><el-tag type="info" size="mini">DEAD_POOL</el-tag></template></el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template slot-scope="{ row }">
            <el-button type="primary" plain size="small" @click="detail(row)">详情</el-button>
            <el-button v-hasPermi="['lead:dead-pool:restore']" type="text" @click="openRestore(row)">恢复</el-button>
          </template>
        </el-table-column>
        <template slot="empty"><el-empty description="Dead-Pool 暂无线索" :image-size="72" /></template>
      </el-table>
      <pagination v-show="total>0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="load" />
    </section>

    <el-drawer title="Dead-Pool 线索详情" :visible.sync="detailOpen" size="760px" append-to-body>
      <div v-loading="detailLoading" class="drawer-body">
        <div v-if="detailError" role="alert"><el-alert :title="detailError" type="error" :closable="false" show-icon><el-button type="text" @click="loadDetail">重新加载</el-button></el-alert></div>
        <section class="lead-drawer-section">
          <h3>{{ leadDetail.contactName || selected.leadName || '-' }}</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="线索编号">{{ leadDetail.leadNo || selected.leadNo }}</el-descriptions-item>
            <el-descriptions-item label="处置位置">Dead-Pool</el-descriptions-item>
            <el-descriptions-item label="无效原因">{{ reasonLabel(selected.reasonCode) }}</el-descriptions-item>
            <el-descriptions-item label="确认时间">{{ timeText(selected.factTime) }}</el-descriptions-item>
            <el-descriptions-item label="复核说明" :span="2">{{ selected.detail || '-' }}</el-descriptions-item>
          </el-descriptions>
        </section>
        <section class="lead-drawer-section"><lead-call-timeline v-if="selected.leadId" :lead-id="selected.leadId" /></section>
        <section class="lead-drawer-section"><business-todo-summary v-if="selected.leadId" business-type="LEAD" :business-id="selected.leadId" :business-no="selected.leadNo" /></section>
      </div>
    </el-drawer>
    <el-dialog title="恢复 Dead-Pool 线索" :visible.sync="restoreOpen" width="540px" append-to-body :close-on-click-modal="false" @closed="discardRestore">
      <el-alert title="恢复后线索进入普通公海，由后续分配或领取规则处理；不会直接指定负责人。" type="warning" :closable="false" show-icon />
      <el-form ref="restoreForm" :model="form" :rules="rules" label-width="88px" class="restore-form">
        <el-form-item label="恢复原因" prop="reason"><el-input v-model.trim="form.reason" type="textarea" :rows="4" maxlength="1000" show-word-limit @input="semanticChange" /></el-form-item>
      </el-form>
      <div v-if="submitError" role="alert"><el-alert :title="submitError" type="error" :closable="false" show-icon /></div>
      <div slot="footer"><el-button @click="discardRestore">取消</el-button><el-button data-testid="dead-pool-restore-submit" type="primary" :loading="submitting" :disabled="submitting" @click="restore">确认恢复到公海</el-button></div>
    </el-dialog>
  </div>
</template>

<script>
import LeadCallTimeline from '../components/LeadCallTimeline'
import BusinessTodoSummary from '@/views/todo/components/BusinessTodoSummary'
import { getLead, listDeadPool, restoreDeadPoolLead } from '@/api/lead'
import { errorMessage, maskedMobile, stableActionId, timeText } from '../lead-todo-ui'

export default {
  name: 'LeadDeadPool',
  components: { LeadCallTimeline, BusinessTodoSummary },
  data() {
    return {
      loading: false, detailLoading: false, submitting: false, error: '', detailError: '', submitError: '', rows: [], total: 0,
      query: { pageNum: 1, pageSize: 10, reasonCode: '', keyword: '' }, selected: {}, leadDetail: {},
      detailOpen: false, restoreOpen: false, form: { reason: '', actionId: '' },
      reasonOptions: [
        { value: 'NO_DEMAND', label: '无需求' }, { value: 'DENY_SUBMISSION', label: '否认提交' },
        { value: 'COMPETITOR_INTERFERENCE', label: '竞品干扰' }, { value: 'OTHER', label: '其他' }
      ],
      rules: { reason: [{ required: true, message: '请填写恢复原因', trigger: 'blur' }] }
    }
  },
  created() { this.load() },
  methods: {
    maskedMobile, timeText,
    reasonLabel(value) { const item = this.reasonOptions.find(option => option.value === value); return item ? item.label : (value || '-') },
    load() {
      this.loading = true; this.error = ''
      listDeadPool(this.query).then(response => { this.rows = response.rows || []; this.total = response.total || 0 })
        .catch(error => { this.error = errorMessage(error, 'Dead-Pool 加载失败') })
        .finally(() => { this.loading = false })
    },
    search() { this.query.pageNum = 1; this.load() },
    reset() { this.query = { pageNum: 1, pageSize: 10, reasonCode: '', keyword: '' }; this.load() },
    detail(row) { this.selected = row; this.leadDetail = {}; this.detailOpen = true; this.loadDetail() },
    loadDetail() {
      this.detailLoading = true; this.detailError = ''
      getLead(this.selected.leadId).then(response => { this.leadDetail = response.data || {} })
        .catch(error => { this.detailError = errorMessage(error, '线索详情加载失败') })
        .finally(() => { this.detailLoading = false })
    },
    openRestore(row) {
      this.selected = row; this.form = { reason: '', actionId: stableActionId('DEAD_POOL_RESTORE', row.leadId) }; this.submitError = ''; this.restoreOpen = true
      this.$nextTick(() => this.$refs.restoreForm && this.$refs.restoreForm.clearValidate())
    },
    restore() {
      this.$refs.restoreForm.validate(valid => {
        if (!valid) return
        this.submitting = true; this.submitError = ''
        restoreDeadPoolLead(this.selected.leadId, { actionId: this.form.actionId, reason: this.form.reason })
          .then(() => { this.$modal.msgSuccess('线索已恢复到公海'); this.restoreOpen = false; this.form = {}; this.load() })
          .catch(error => { this.submitError = errorMessage(error, '恢复失败') })
          .finally(() => { this.submitting = false })
      })
    },
    semanticChange() {
      if (this.submitError) {
        this.form.actionId = stableActionId('DEAD_POOL_RESTORE', this.selected.leadId)
        this.submitError = ''
      }
    },
    discardRestore() {
      this.restoreOpen = false
      this.form = {}
      this.submitError = ''
    }
  }
}
</script>

<style scoped lang="scss">
@import "../lead-todo-workbench.scss";
.cell-detail{margin:6px 0 0;color:#64748b}.block-muted{display:block;margin-top:5px;color:#64748b}.drawer-body{padding:18px}.restore-form{margin-top:18px}
</style>
