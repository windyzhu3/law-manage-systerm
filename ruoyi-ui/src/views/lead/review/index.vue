<template>
  <div class="lead-workbench" data-permission="lead:invalid-review:list">
    <header class="lead-workbench__heading">
      <div><h2>疑似无效复核</h2><p>主管在24小时内核验销售判断；到期未处理时，流程将按规则默认确认无效。</p></div>
      <el-button class="lead-retry-button" icon="el-icon-refresh" :loading="loading" @click="load">刷新</el-button>
    </header>
    <div class="lead-workbench__toolbar">
      <el-input v-model.trim="query.keyword" clearable prefix-icon="el-icon-search" placeholder="线索编号、名称、手机号" @keyup.enter.native="search" />
      <el-select v-model="query.status" clearable placeholder="待办状态">
        <el-option label="待处理" value="PENDING" /><el-option label="已完成" value="COMPLETED" />
      </el-select>
      <el-button type="primary" icon="el-icon-search" data-testid="lead-search-submit" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>
    <div v-if="error" role="alert"><el-alert class="lead-state-error" :title="error" type="error" :closable="false" show-icon /></div>
    <section class="lead-workbench__card">
      <el-table v-loading="loading" :data="rows" row-key="reviewId">
        <el-table-column label="线索" min-width="190">
          <template slot-scope="{ row }"><div class="lead-identity" :data-testid="`lead-row-${row.leadNo}`"><strong>{{ row.leadName }}</strong><small>{{ row.leadNo }} · {{ maskedMobile(row.mobile) }}</small></div></template>
        </el-table-column>
        <el-table-column label="提交事实" min-width="210">
          <template slot-scope="{ row }"><strong>{{ reasonLabel(row.reasonCode) }}</strong><p class="cell-detail">{{ row.detail || '未填写销售说明' }}</p></template>
        </el-table-column>
        <el-table-column label="复核人" min-width="120"><template slot-scope="{ row }">{{ row.reviewerName || row.todoOwnerName || '-' }}</template></el-table-column>
        <el-table-column label="SLA" min-width="170">
          <template slot-scope="{ row }"><div class="lead-sla"><el-tag size="mini" :type="slaMeta(row).type">{{ slaMeta(row).label }}</el-tag><div><time>{{ timeText(row.dueAt) }}</time><small v-if="row.escalated">已升级主管</small></div></div></template>
        </el-table-column>
        <el-table-column label="状态" width="110"><template slot-scope="{ row }"><el-tag size="mini" :type="todoMeta(row.todoStatus)[1]">{{ todoMeta(row.todoStatus)[0] }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template slot-scope="{ row }">
            <el-button v-hasPermi="['lead:invalid-review:handle']" type="primary" plain size="small" :disabled="!canHandle(row)" :data-testid="`lead-review-${row.leadNo}`" @click="openReview(row)">复核</el-button>
            <el-button type="text" @click="openEvidence(row)">证据</el-button>
          </template>
        </el-table-column>
        <template slot="empty"><el-empty description="暂无待复核线索" :image-size="72" /></template>
      </el-table>
      <pagination v-show="total>0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="load" />
    </section>

    <el-drawer title="复核证据" :visible.sync="evidenceOpen" size="700px" append-to-body>
      <div class="drawer-body">
        <section class="lead-drawer-section"><h3>销售提交</h3><p>{{ selected.detail || '未填写销售说明' }}</p><el-tag>{{ reasonLabel(selected.reasonCode) }}</el-tag></section>
        <section class="lead-drawer-section"><lead-call-timeline v-if="selected.leadId" :lead-id="selected.leadId" /></section>
        <section class="lead-drawer-section"><h3>全部业务文件证据</h3><business-file-evidence-list v-if="selected.leadId" business-type="LEAD" :business-id="selected.leadId" /></section>
      </div>
    </el-drawer>
    <el-dialog title="确认无效判断" :visible.sync="reviewOpen" width="600px" append-to-body :close-on-click-modal="false">
      <el-alert title="提交后不可直接撤销；“确认无效”将进入独立 Dead-Pool，“误判有效”将重开首联。" type="warning" :closable="false" show-icon />
      <el-form ref="reviewForm" :model="form" :rules="rules" label-width="104px" class="review-form">
        <el-form-item label="复核结论" prop="reviewResult">
          <el-radio-group v-model="form.reviewResult" @change="semanticChange">
            <el-radio-button label="TRUE_INVALID">确认无效</el-radio-button>
            <el-radio-button label="MISJUDGED_VALID">误判有效</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="复核意见" prop="reviewOpinion"><el-input v-model.trim="form.reviewOpinion" type="textarea" :rows="4" maxlength="1000" show-word-limit @input="semanticChange" /></el-form-item>
        <el-form-item label="补充证据">
          <business-file-picker v-model="files" business-type="LEAD" :business-id="selected.leadId || 0" material-type="INVALID_REVIEW_EVIDENCE" :limit="5" @change="semanticChange" />
        </el-form-item>
      </el-form>
      <div v-if="submitError" role="alert"><el-alert :title="submitError" type="error" :closable="false" show-icon /></div>
      <div slot="footer"><el-button @click="cancelReview">取消</el-button><el-button data-testid="invalid-review-submit" type="primary" :loading="submitting" :disabled="submitting || !canHandle(selected)" @click="submit">确认提交</el-button></div>
    </el-dialog>
  </div>
</template>

<script>
import BusinessFilePicker from '@/components/BusinessFile/BusinessFilePicker'
import BusinessFileEvidenceList from '@/components/BusinessFile/BusinessFileEvidenceList'
import LeadCallTimeline from '../components/LeadCallTimeline'
import { listInvalidReview, completeInvalidReview } from '@/api/lead'
import { allowed, errorMessage, isPendingTodo, maskedMobile, slaMeta, stableActionId, timeText, todoStatusMeta } from '../lead-todo-ui'

export default {
  name: 'LeadInvalidReview',
  components: { BusinessFilePicker, BusinessFileEvidenceList, LeadCallTimeline },
  data() {
    return {
      loading: false, submitting: false, error: '', submitError: '', rows: [], total: 0,
      query: { pageNum: 1, pageSize: 10, status: 'PENDING', keyword: '' },
      selected: {}, evidenceOpen: false, reviewOpen: false, files: [], form: {},
      rules: {
        reviewResult: [{ required: true, message: '请选择复核结论', trigger: 'change' }],
        reviewOpinion: [{ required: true, message: '请填写复核意见', trigger: 'blur' }]
      }
    }
  },
  created() { this.load() },
  methods: {
    maskedMobile, slaMeta, timeText, todoMeta: todoStatusMeta,
    reasonLabel(value) { return ({ NO_DEMAND: '无需求', DENY_SUBMISSION: '否认提交', COMPETITOR_INTERFERENCE: '竞品干扰', OTHER: '其他' })[value] || value || '-' },
    canHandle(row) { return isPendingTodo(row) && ['claim', 'start', 'submit', 'complete'].some(action => allowed(row, action)) },
    load() {
      this.loading = true; this.error = ''
      listInvalidReview(this.query).then(response => { this.rows = response.rows || []; this.total = response.total || 0 })
        .catch(error => { this.error = errorMessage(error, '复核队列加载失败') })
        .finally(() => { this.loading = false })
    },
    search() { this.query.pageNum = 1; this.load() },
    reset() { this.query = { pageNum: 1, pageSize: 10, status: 'PENDING', keyword: '' }; this.load() },
    openEvidence(row) { this.selected = row; this.evidenceOpen = true },
    openReview(row) {
      this.selected = row; this.form = { reviewResult: '', reviewOpinion: '', actionId: stableActionId('TD002_REVIEW', row.todoId) }; this.files = []; this.submitError = ''; this.reviewOpen = true
      this.$nextTick(() => this.$refs.reviewForm && this.$refs.reviewForm.clearValidate())
    },
    submit() {
      this.$refs.reviewForm.validate(valid => {
        if (!valid) return
        this.submitting = true; this.submitError = ''
        completeInvalidReview(this.selected.todoId, {
          actionId: this.form.actionId,
          ...this.form,
          fileObjectIds: (this.files || []).map(file => Number(file.fileObjectId)).filter(Boolean)
        }).then(() => {
          this.$modal.msgSuccess('复核结论已提交'); this.reviewOpen = false; this.form = {}; this.load()
        }).catch(error => { this.submitError = errorMessage(error, '复核提交失败') })
          .finally(() => { this.submitting = false })
      })
    },
    semanticChange() {
      if (this.submitError) {
        this.form.actionId = stableActionId('TD002_REVIEW', this.selected.todoId)
        this.submitError = ''
      }
    },
    cancelReview() {
      this.reviewOpen = false
      this.form = {}
    }
  }
}
</script>

<style scoped lang="scss">
@import "../lead-todo-workbench.scss";
.cell-detail{margin:5px 0 0;color:#64748b;line-height:1.45}.lead-sla small{display:block;margin-top:4px;color:#b45309}.drawer-body{padding:18px}.review-form{margin-top:18px}
</style>
