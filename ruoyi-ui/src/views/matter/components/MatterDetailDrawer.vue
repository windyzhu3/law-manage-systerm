<template>
  <el-drawer
    :visible.sync="innerVisible"
    size="82%"
    :custom-class="drawerClass"
    append-to-body
    @close="$emit('update:visible', false)"
  >
    <div slot="title" class="drawer-title" :class="sizeClass">
      <span>MATTER PROFILE</span>
      <strong>案件详情</strong>
    </div>

    <div class="detail-page matter-detail-page" :class="sizeClass">
      <section class="summary-card matter-summary">
        <div class="doc-icon"><svg-icon icon-class="documentation" /></div>
        <div class="summary-main">
          <h3>{{ valueOf(caseInfo, 'case_name', 'caseName') || '-' }}</h3>
          <p>
            {{ valueOf(caseInfo, 'case_no', 'caseNo') || '待生成编号' }}
            <span>·</span>
            {{ valueOf(caseInfo, 'customer_name', 'customerName') || '-' }}
          </p>
          <div class="summary-tags">
            <dict-tag :options="dict.type.law_case_status" :value="caseStatus" />
            <dict-tag :options="dict.type.law_case_stage" :value="caseStage" />
            <dict-tag :options="dict.type.law_case_risk_level" :value="riskLevel" />
            <dict-tag v-if="feeStatus" :options="dict.type.law_case_fee_status" :value="feeStatus" />
            <dict-tag v-if="archiveStatus" :options="dict.type.law_case_archive_status" :value="archiveStatus" />
          </div>
        </div>
        <div class="summary-actions matter-actions">
          <el-button v-hasPermi="['matter:edit']" :size="controlSize" type="primary" icon="el-icon-edit" :disabled="!canOperate(caseInfo)" @click="$emit('edit', caseInfo)">编辑案件</el-button>
          <el-button v-hasPermi="['matter:progress:add']" :size="controlSize" icon="el-icon-plus" :disabled="!canOperate(caseInfo)" @click="$emit('progress', caseInfo)">新增进度</el-button>
          <el-button v-hasPermi="['matter:node:add']" :size="controlSize" icon="el-icon-date" :disabled="!canOperate(caseInfo)" @click="$emit('node', caseInfo)">关键节点</el-button>
          <el-button v-hasPermi="['matter:expense:add']" :size="controlSize" icon="el-icon-money" :disabled="!canOperate(caseInfo)" @click="$emit('expense', caseInfo)">费用录入</el-button>
          <el-button v-hasPermi="['finance:receivable:list', 'finance:expense:list']" :size="controlSize" icon="el-icon-data-analysis" @click="$emit('finance', caseInfo)">财务视图</el-button>
          <el-button v-hasPermi="['matter:archive:apply']" :size="controlSize" icon="el-icon-folder-checked" :disabled="!canOperate(caseInfo)" @click="$emit('archive', caseInfo)">结案申请</el-button>
          <el-button v-hasPermi="['matter:document:add']" :size="controlSize" icon="el-icon-upload" :disabled="!canOperate(caseInfo)" @click="$emit('document', caseInfo)">文档资料</el-button>
        </div>
      </section>

      <section class="matter-kpis">
        <article v-for="item in overviewCards" :key="item.key">
          <i :class="item.icon" />
          <div>
            <strong>{{ item.value }}</strong>
            <span>{{ item.label }}</span>
          </div>
        </article>
      </section>

      <div class="matter-layout">
        <main class="matter-main-column">
          <section class="info-card">
            <header>
              <h4>基础信息</h4>
              <span class="section-hint">案件来源、办理阶段与核心业务事实</span>
            </header>
            <dl class="matter-field-grid">
              <div v-for="item in baseInfoItems" :key="item.label" :class="{ wide: item.wide }">
                <dt>{{ item.label }}</dt>
                <dd :class="item.className">{{ item.value || '-' }}</dd>
              </div>
            </dl>
          </section>

          <section class="info-card">
            <header>
              <h4>类型字段</h4>
              <span class="section-count">{{ typeFieldItems.length }} 项</span>
            </header>
            <dl v-if="typeFieldItems.length" class="matter-field-grid type-field-grid">
              <div v-for="item in typeFieldItems" :key="item.label" :class="{ wide: item.wide }">
                <dt>{{ item.label }}</dt>
                <dd>{{ item.value || '-' }}</dd>
              </div>
            </dl>
            <el-empty v-else description="暂无案件类型字段" :image-size="72" />
          </section>

          <section class="info-card">
            <header>
              <h4>进度记录</h4>
              <span class="section-count">{{ progressList.length }} 条</span>
            </header>
            <div v-if="progressList.length" class="matter-timeline">
              <article v-for="item in progressList" :key="valueOf(item, 'progress_id', 'progressId') || valueOf(item, 'create_time', 'createTime')">
                <div class="timeline-dot blue"><i class="el-icon-document-checked" /></div>
                <div class="timeline-body">
                  <div class="timeline-head">
                    <strong>{{ valueOf(item, 'content') || '-' }}</strong>
                    <small>{{ valueOf(item, 'record_time', 'recordTime', 'create_time', 'createTime') || '-' }}</small>
                  </div>
                  <p v-if="valueOf(item, 'next_plan', 'nextPlan')">下一步：{{ valueOf(item, 'next_plan', 'nextPlan') }}</p>
                  <div class="timeline-meta">
                    <span>记录人：{{ valueOf(item, 'record_user_name', 'recordUserName', 'create_by', 'createBy') || '-' }}</span>
                    <el-tag :size="controlSize" :type="valueOf(item, 'sync_customer', 'syncCustomer') === 'Y' ? 'success' : 'info'">
                      {{ valueOf(item, 'sync_customer', 'syncCustomer') === 'Y' ? '已同步客户' : '未同步客户' }}
                    </el-tag>
                    <el-button
                      v-if="valueOf(item, 'attachment_url', 'attachmentUrl')"
                      :size="controlSize"
                      type="text"
                      icon="el-icon-paperclip"
                      @click="$emit('open-file', valueOf(item, 'attachment_url', 'attachmentUrl'))"
                    >
                      查看附件
                    </el-button>
                  </div>
                </div>
              </article>
            </div>
            <el-empty v-else description="暂无进度记录" :image-size="78" />
          </section>

          <section class="info-card">
            <header>
              <h4>关键节点</h4>
              <span class="section-count">{{ completedNodeCount }}/{{ nodeList.length }} 已完成</span>
            </header>
            <div v-if="nodeList.length" class="node-board">
              <article v-for="item in nodeList" :key="valueOf(item, 'node_id', 'nodeId') || valueOf(item, 'node_name', 'nodeName')">
                <div class="node-card-head">
                  <strong>{{ valueOf(item, 'node_name', 'nodeName') || '-' }}</strong>
                  <dict-tag :options="dict.type.law_case_node_status" :value="valueOf(item, 'node_status', 'nodeStatus')" />
                </div>
                <dl class="node-meta">
                  <div><dt>节点类型</dt><dd>{{ dictLabel('law_case_node_type', valueOf(item, 'node_type', 'nodeType')) }}</dd></div>
                  <div><dt>计划日期</dt><dd :class="{ overdue: isOverdue(valueOf(item, 'plan_date', 'planDate')) }">{{ valueOf(item, 'plan_date', 'planDate') || '-' }}</dd></div>
                  <div><dt>实际日期</dt><dd>{{ valueOf(item, 'actual_date', 'actualDate') || '-' }}</dd></div>
                  <div><dt>负责人</dt><dd>{{ valueOf(item, 'owner_name', 'ownerName') || '-' }}</dd></div>
                  <div class="wide"><dt>地点</dt><dd>{{ nodeLocation(item) }}</dd></div>
                </dl>
                <div class="node-material-summary">
                  <span>材料准备</span>
                  <b>{{ nodeMaterialStats(item).ready }}/{{ nodeMaterialStats(item).total }}</b>
                </div>
                <matter-material-list
                  v-if="valueOf(item, 'materials') && valueOf(item, 'materials').length"
                  :value="normalizeMaterials(valueOf(item, 'materials'))"
                  :options="dict.type.law_case_material_status"
                  show-upload
                  readonly
                  @open="$emit('open-file', $event)"
                />
              </article>
            </div>
            <el-empty v-else description="暂无关键节点" :image-size="78" />
          </section>

          <section class="info-card">
            <header>
              <h4>费用记录</h4>
              <span class="section-count">支出合计 {{ formatMoney(detailExpenseTotal) }}</span>
            </header>
            <div class="finance-overview">
              <div><strong>{{ formatMoney(detailFinance.contractAmount) }}</strong><span>合同金额</span></div>
              <div><strong>{{ formatMoney(detailFinance.receivedAmount) }}</strong><span>已收款</span></div>
              <div><strong>{{ formatMoney(detailFinance.pendingAmount) }}</strong><span>待收款</span></div>
              <div><strong>{{ expenseOverview.missingVoucher }}</strong><span>缺凭证</span></div>
            </div>
            <div v-if="expenseList.length" class="compact-list">
              <article v-for="item in expenseList" :key="valueOf(item, 'expense_id', 'expenseId')">
                <div>
                  <strong>{{ dictLabel('law_case_expense_type', valueOf(item, 'expense_type', 'expenseType')) }} · {{ formatMoney(valueOf(item, 'amount')) }}</strong>
                  <span>{{ valueOf(item, 'occur_date', 'occurDate') || '-' }} · 经办人：{{ valueOf(item, 'handler_name', 'handlerName') || '-' }}</span>
                </div>
                <div class="tag-group">
                  <dict-tag :options="dict.type.law_case_pay_status" :value="valueOf(item, 'pay_status', 'payStatus')" />
                  <dict-tag :options="dict.type.law_case_reimburse_status" :value="valueOf(item, 'reimburse_status', 'reimburseStatus')" />
                  <dict-tag :options="dict.type.law_case_voucher_status" :value="valueOf(item, 'voucher_status', 'voucherStatus')" />
                </div>
              </article>
            </div>
            <el-empty v-else description="暂无费用记录" :image-size="78" />
          </section>

          <section class="info-card">
            <header>
              <h4>文档资料</h4>
              <span class="section-count">{{ documentList.length }} 份</span>
            </header>
            <div v-if="documentList.length" class="document-grid">
              <article v-for="item in documentList" :key="valueOf(item, 'document_id', 'documentId')">
                <i :class="documentIcon(item)" />
                <div>
                  <strong @click="$emit('open-file', valueOf(item, 'file_url', 'fileUrl'))">
                    {{ valueOf(item, 'file_name', 'fileName') || fileNameFromUrl(valueOf(item, 'file_url', 'fileUrl')) }}
                  </strong>
                  <span>{{ dictLabel('law_case_document_type', valueOf(item, 'document_type', 'documentType')) }} · {{ valueOf(item, 'create_by', 'createBy') || '-' }}</span>
                  <small>{{ valueOf(item, 'create_time', 'createTime') || '-' }}</small>
                  <p v-if="valueOf(item, 'remark')">{{ valueOf(item, 'remark') }}</p>
                </div>
                <el-button v-hasPermi="['matter:document:remove']" :size="controlSize" type="text" class="danger-text" :disabled="!canOperate(caseInfo)" @click="$emit('remove-document', item)">删除</el-button>
              </article>
            </div>
            <el-empty v-else description="暂无文档资料" :image-size="78" />
          </section>
        </main>

        <aside class="matter-side-column">
          <section class="info-card">
            <header><h4>业务概览</h4></header>
            <div class="overview-grid">
              <div><b>{{ progressList.length }}</b><span>进度记录</span></div>
              <div><b>{{ nodeList.length }}</b><span>关键节点</span></div>
              <div><b>{{ materialStats.percent }}%</b><span>材料完整度</span></div>
              <div><b>{{ formatMoney(detailFinance.pendingAmount) }}</b><span>待收款</span></div>
            </div>
          </section>

          <section class="info-card">
            <header><h4>结案归档</h4></header>
            <template v-if="archiveInfo">
              <dl class="side-field-list">
                <div v-for="item in archiveInfoItems" :key="item.label" :class="{ wide: item.wide }">
                  <dt>{{ item.label }}</dt>
                  <dd>{{ item.value || '-' }}</dd>
                </div>
              </dl>
              <div class="archive-strip">
                <span>费用：{{ dictLabel('law_case_fee_clear_status', valueOf(archiveInfo, 'fee_clear_status', 'feeClearStatus')) }}</span>
                <span>满意度：{{ valueOf(archiveInfo, 'satisfaction') || '-' }}</span>
                <span>{{ valueOf(archiveInfo, 'readonly_flag', 'readonlyFlag') === 'Y' ? '已锁定' : '未锁定' }}</span>
              </div>
              <div class="side-materials">
                <div class="side-materials-head">
                  <span>归档材料</span>
                  <b>{{ materialStats.archiveReady }}/{{ materialStats.archiveTotal }}</b>
                </div>
                <matter-material-list
                  v-if="archiveMaterials.length"
                  :value="normalizeMaterials(archiveMaterials)"
                  :options="dict.type.law_case_material_status"
                  show-upload
                  readonly
                  @open="$emit('open-file', $event)"
                />
                <el-empty v-else description="暂无归档材料" :image-size="64" />
              </div>
            </template>
            <el-empty v-else description="暂无结案归档信息" :image-size="72" />
          </section>

          <section class="info-card">
            <header>
              <h4>状态记录</h4>
              <span class="section-count">{{ statusList.length }} 条</span>
            </header>
            <div v-if="statusList.length" class="side-timeline">
              <article v-for="item in statusList" :key="valueOf(item, 'log_id', 'logId') || valueOf(item, 'create_time', 'createTime')">
                <i />
                <div>
                  <strong>{{ dictLabel('law_case_status_action', valueOf(item, 'action_type', 'actionType')) }}</strong>
                  <p>{{ valueOf(item, 'content') || '-' }}</p>
                  <span>{{ valueOf(item, 'create_by', 'createBy') || '-' }} · {{ valueOf(item, 'create_time', 'createTime') || '-' }}</span>
                </div>
              </article>
            </div>
            <el-empty v-else description="暂无状态记录" :image-size="72" />
          </section>

        </aside>
      </div>
    </div>
  </el-drawer>
</template>

<script>
import businessUi from '@/views/business/mixins/businessUi'
import MatterMaterialList from './MatterMaterialList'

export default {
  name: 'MatterDetailDrawer',
  components: { MatterMaterialList },
  mixins: [businessUi],
  dicts: ['law_case_status', 'law_case_stage', 'law_case_risk_level', 'law_case_type', 'law_case_cause', 'law_case_fee_status', 'law_case_archive_status', 'law_case_node_status', 'law_case_node_type', 'law_case_material_status', 'law_case_expense_type', 'law_case_pay_status', 'law_case_reimburse_status', 'law_case_voucher_status', 'law_case_close_result', 'law_case_fee_clear_status', 'law_case_status_action', 'law_case_document_type'],
  props: {
    visible: { type: Boolean, default: false },
    matter: { type: Object, default: () => ({}) },
    sizeClass: { type: String, default: '' }
  },
  computed: {
    innerVisible: {
      get() { return this.visible },
      set(value) { this.$emit('update:visible', value) }
    },
    caseInfo() {
      return this.matter || {}
    },
    drawerClass() {
      return ['matter-detail-drawer', this.sizeClass].filter(Boolean).join(' ')
    },
    caseStatus() {
      return this.valueOf(this.caseInfo, 'case_status', 'caseStatus')
    },
    caseStage() {
      return this.valueOf(this.caseInfo, 'case_stage', 'caseStage')
    },
    riskLevel() {
      return this.valueOf(this.caseInfo, 'risk_level', 'riskLevel')
    },
    feeStatus() {
      return this.valueOf(this.caseInfo, 'fee_status', 'feeStatus')
    },
    archiveStatus() {
      return this.valueOf(this.caseInfo, 'archive_status', 'archiveStatus')
    },
    progressList() {
      return this.valueOf(this.caseInfo, 'progress') || []
    },
    nodeList() {
      return this.valueOf(this.caseInfo, 'nodes') || []
    },
    expenseList() {
      return this.valueOf(this.caseInfo, 'expenses') || []
    },
    documentList() {
      return this.valueOf(this.caseInfo, 'documents') || []
    },
    statusList() {
      return this.valueOf(this.caseInfo, 'statusLogs', 'status_logs') || []
    },
    archiveInfo() {
      return this.valueOf(this.caseInfo, 'archive')
    },
    archiveMaterials() {
      return this.archiveInfo ? (this.valueOf(this.archiveInfo, 'materials') || []) : []
    },
    detailExpenseTotal() {
      return this.expenseList.reduce((sum, item) => sum + Number(this.valueOf(item, 'amount', 'expense_amount', 'expenseAmount') || 0), 0)
    },
    detailFinance() {
      const contractAmount = Number(this.valueOf(this.caseInfo, 'contractSignAmount', 'contract_sign_amount') || 0)
      const receivableAmount = Number(this.valueOf(this.caseInfo, 'contractReceivableAmount', 'contract_receivable_amount') || contractAmount || 0)
      const receivedAmount = Number(this.valueOf(this.caseInfo, 'contractReceivedAmount', 'contract_received_amount') || 0)
      return {
        contractAmount,
        receivableAmount,
        receivedAmount,
        pendingAmount: Math.max(receivableAmount - receivedAmount, 0)
      }
    },
    expenseOverview() {
      return {
        paid: this.expenseList.filter(item => this.valueOf(item, 'pay_status', 'payStatus') === 'paid').length,
        pending: this.expenseList.filter(item => this.valueOf(item, 'pay_status', 'payStatus') !== 'paid').length,
        missingVoucher: this.expenseList.filter(item => {
          const status = this.valueOf(item, 'voucher_status', 'voucherStatus')
          return ['missing', '', undefined, null].includes(status)
        }).length
      }
    },
    completedNodeCount() {
      return this.nodeList.filter(item => {
        const status = String(this.valueOf(item, 'node_status', 'nodeStatus') || '').toLowerCase()
        return ['done', 'ready', 'completed', 'complete', 'finished'].includes(status)
      }).length
    },
    baseInfoItems() {
      return [
        { label: '案件类型', value: this.dictLabel('law_case_type', this.valueOf(this.caseInfo, 'case_type', 'caseType')) },
        { label: '案由', value: this.dictLabel('law_case_cause', this.valueOf(this.caseInfo, 'cause')) || this.valueOf(this.caseInfo, 'cause') },
        { label: '争议金额', value: this.formatMoney(this.valueOf(this.caseInfo, 'dispute_amount', 'disputeAmount')) },
        { label: '法院/机构', value: this.valueOf(this.caseInfo, 'court_name', 'courtName') },
        { label: '案号', value: this.valueOf(this.caseInfo, 'case_filing_no', 'caseFilingNo') },
        { label: '来源合同', value: this.valueOf(this.caseInfo, 'contract_no', 'contractNo') },
        { label: '当前节点', value: this.valueOf(this.caseInfo, 'current_node', 'currentNode') },
        { label: '下次关键日期', value: this.valueOf(this.caseInfo, 'next_key_date', 'nextKeyDate'), className: this.isOverdue(this.valueOf(this.caseInfo, 'next_key_date', 'nextKeyDate')) ? 'overdue' : '' },
        { label: '主办律师', value: this.valueOf(this.caseInfo, 'main_lawyer_name', 'mainLawyerName') },
        { label: '协办律师', value: this.valueOf(this.caseInfo, 'assistant_lawyer_names', 'assistantLawyerNames') },
        { label: '负责人', value: this.valueOf(this.caseInfo, 'ownerName', 'owner_name') },
        { label: '所属部门', value: this.valueOf(this.caseInfo, 'deptName', 'dept_name') },
        { label: '最近进度', value: this.valueOf(this.caseInfo, 'recent_progress', 'recentProgress'), wide: true },
        { label: '案件概况', value: this.valueOf(this.caseInfo, 'case_summary', 'caseSummary'), wide: true }
      ]
    },
    typeFieldItems() {
      return (this.valueOf(this.caseInfo, 'fieldValues', 'field_values') || []).map(field => ({
        label: this.valueOf(field, 'field_name', 'fieldName', 'field_code', 'fieldCode'),
        value: this.formatFieldValue(field)
      }))
    },
    archiveInfoItems() {
      const archive = this.archiveInfo || {}
      return [
        { label: '结案结果', value: this.dictLabel('law_case_close_result', this.valueOf(archive, 'close_result', 'closeResult')) },
        { label: '结案日期', value: this.valueOf(archive, 'close_date', 'closeDate') },
        { label: '归档编号', value: this.valueOf(archive, 'archive_no', 'archiveNo') },
        { label: '归档状态', value: this.dictLabel('law_case_archive_status', this.valueOf(archive, 'archive_status', 'archiveStatus')) },
        { label: '实际回款', value: this.formatMoney(this.valueOf(archive, 'actual_received_amount', 'actualReceivedAmount')) },
        { label: '办案总结', value: this.valueOf(archive, 'summary'), wide: true }
      ]
    },
    materialStats() {
      const nodeMaterials = this.nodeList.reduce((items, node) => items.concat(this.valueOf(node, 'materials') || []), [])
      const nodeTotal = nodeMaterials.length
      const archiveTotal = this.archiveMaterials.length
      const nodeReady = this.readyMaterialCount(nodeMaterials)
      const archiveReady = this.readyMaterialCount(this.archiveMaterials)
      const total = nodeTotal + archiveTotal
      const ready = nodeReady + archiveReady
      return {
        nodeTotal,
        archiveTotal,
        nodeReady,
        archiveReady,
        total,
        ready,
        percent: total ? Math.round((ready / total) * 100) : 0
      }
    },
    overviewCards() {
      return [
        { key: 'progress', label: '进度记录', value: this.progressList.length, icon: 'el-icon-document-checked' },
        { key: 'nodes', label: '关键节点', value: `${this.completedNodeCount}/${this.nodeList.length}`, icon: 'el-icon-date' },
        { key: 'materials', label: '材料完整度', value: `${this.materialStats.percent}%`, icon: 'el-icon-folder-checked' },
        { key: 'expense', label: '案件支出', value: this.formatMoney(this.detailExpenseTotal), icon: 'el-icon-money' },
        { key: 'pending', label: '待收款', value: this.formatMoney(this.detailFinance.pendingAmount), icon: 'el-icon-wallet' }
      ]
    }
  },
  methods: {
    valueOf(source = {}, ...keys) {
      for (const key of keys) {
        if (source && source[key] !== undefined && source[key] !== null && source[key] !== '') return source[key]
      }
      return undefined
    },
    rowStatus(row = {}) {
      return this.valueOf(row, 'case_status', 'caseStatus')
    },
    canOperate(row) {
      return row && this.rowStatus(row) === 'processing'
    },
    isOverdue(date) {
      return date && new Date(date).getTime() < Date.now()
    },
    readyMaterialCount(list = []) {
      return (list || []).filter(item => {
        const status = String(this.valueOf(item, 'materialStatus', 'material_status') || '').toLowerCase()
        return ['ready', 'completed', 'done', 'uploaded'].includes(status)
      }).length
    },
    nodeMaterialStats(node = {}) {
      const materials = this.valueOf(node, 'materials') || []
      return {
        total: materials.length,
        ready: this.readyMaterialCount(materials)
      }
    },
    normalizeMaterials(list) {
      return (list || []).map(item => ({
        ...item,
        materialName: this.valueOf(item, 'materialName', 'material_name'),
        materialStatus: this.valueOf(item, 'materialStatus', 'material_status'),
        fileUrl: this.valueOf(item, 'fileUrl', 'file_url'),
        fileName: this.valueOf(item, 'fileName', 'file_name')
      }))
    },
    nodeLocation(item) {
      const place = this.valueOf(item, 'court_place', 'courtPlace')
      const room = this.valueOf(item, 'court_room', 'courtRoom')
      if (place && room) return `${place} · ${room}`
      return place || room || '-'
    },
    documentIcon(item) {
      const name = String(this.valueOf(item, 'file_name', 'fileName', 'file_url', 'fileUrl') || '').toLowerCase()
      if (name.includes('.pdf')) return 'el-icon-document'
      if (name.includes('.doc') || name.includes('.docx')) return 'el-icon-document-copy'
      if (name.includes('.zip') || name.includes('.rar')) return 'el-icon-folder'
      return 'el-icon-paperclip'
    },
    formatFieldValue(field = {}) {
      const value = this.valueOf(field, 'fieldValue', 'field_value')
      if (value === undefined || value === null || String(value).trim() === '') return '-'
      const code = this.valueOf(field, 'fieldCode', 'field_code')
      const config = (this.valueOf(this.caseInfo, 'fieldConfigs', 'field_configs') || []).find(item => item.field_code === code || item.fieldCode === code)
      if (config && this.valueOf(config, 'field_type', 'fieldType') === 'switch') return value === 'Y' ? '是' : '否'
      return value
    },
    formatMoney(value) {
      if (value === undefined || value === null || value === '') return '-'
      const number = Number(value)
      if (Number.isNaN(number)) return '-'
      return '¥ ' + number.toLocaleString()
    }
  }
}
</script>

<style scoped lang="scss">
@import "../../business/detail-drawer.scss";

.matter-detail-page {
  padding-top: 2px;
}

.matter-summary {
  align-items: flex-start;
  margin-bottom: 12px;
}

.summary-main p span {
  margin: 0 6px;
  color: #cbd5e1;
}

.matter-actions {
  max-width: 500px;
}

.matter-kpis {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;

  article {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 0;
    padding: 12px 14px;
    border: 1px solid #e8edf6;
    border-radius: 12px;
    background: #fff;
    box-shadow: 0 6px 14px rgba(36, 73, 135, .035);
  }

  i {
    display: flex;
    align-items: center;
    justify-content: center;
    flex: 0 0 34px;
    width: 34px;
    height: 34px;
    border-radius: 12px;
    color: #2563eb;
    background: #eef5ff;
    font-size: var(--biz-font-section);
  }

  strong,
  span {
    display: block;
    min-width: 0;
  }

  strong {
    color: #102a6b;
    font-size: var(--biz-font-section);
    line-height: 1.2;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    margin-top: 4px;
    color: #64748b;
    font-size: var(--biz-font-small);
  }
}

.matter-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 8px;
  align-items: start;
}

.matter-main-column,
.matter-side-column {
  display: grid;
  gap: 10px;
  padding: 0;
}

.matter-side-column .info-card {
  box-shadow: none;
}

.matter-side-column {
  .overview-grid div,
  .archive-strip span,
  .side-field-list div,
  .side-timeline article {
    padding: 0;
  }

  .side-materials,
  .side-materials-head {
    margin-top: 0;
  }
}

.info-card {
  padding: 14px 15px;

  header {
    gap: 10px;
    min-height: 24px;
  }
}

.section-hint,
.section-count {
  color: #64748b;
  font-size: var(--biz-font-small);
  white-space: nowrap;
}

.section-count {
  color: #2563eb;
  font-weight: 600;
}

.matter-field-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px 18px;
  margin: 0;

  div {
    min-width: 0;
  }

  .wide {
    grid-column: span 3;
  }
}

.type-field-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px 16px;

  .wide {
    grid-column: span 4;
  }
}

.side-field-list {
  display: grid;
  gap: 10px;
  margin: 0;

  div {
    min-width: 0;
    padding-bottom: 10px;
    border-bottom: 1px dashed #edf1f7;
  }

  div:last-child {
    padding-bottom: 0;
    border-bottom: 0;
  }
}

.matter-timeline,
.side-timeline {
  display: grid;
  gap: 10px;
}

.matter-timeline article {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 10px;
  padding: 12px;
  border: 1px solid #edf1f7;
  border-radius: 12px;
  background: #fbfdff;
}

.timeline-dot {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  color: #fff;
  background: #2563eb;

  &.blue {
    background: linear-gradient(135deg, #2563eb, #22c3ee);
  }
}

.timeline-body {
  min-width: 0;
}

.timeline-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;

  strong {
    color: #253858;
    font-size: var(--biz-font-small);
    line-height: 1.6;
  }

  small {
    flex: 0 0 auto;
    color: #94a3b8;
    font-size: var(--biz-font-mini);
  }
}

.timeline-body p {
  margin: 5px 0;
  color: #64748b;
  font-size: var(--biz-font-small);
  line-height: 1.6;
}

.timeline-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: #94a3b8;
  font-size: var(--biz-font-mini);
}

.node-board {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.node-board article {
  min-width: 0;
  padding: 13px;
  border: 1px solid #e8edf6;
  border-radius: 12px;
  background: #fbfdff;
}

.node-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;

  strong {
    min-width: 0;
    color: #102a6b;
    font-size: var(--biz-font-card, 14px);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.node-meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
  margin: 0;

  .wide {
    grid-column: span 2;
  }
}

.node-material-summary,
.side-materials-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 11px 0 8px;
  color: #64748b;
  font-size: var(--biz-font-small);

  b {
    color: #2563eb;
  }
}

.finance-overview,
.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 9px;
  margin-bottom: 10px;
}

.overview-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-bottom: 0;
}

.finance-overview div,
.overview-grid div {
  min-width: 0;
  padding: 11px;
  border-radius: 10px;
  background: #f6f9ff;

  strong,
  span,
  b {
    display: block;
  }

  strong,
  b {
    color: #102a6b;
    font-size: var(--biz-font-section);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    margin-top: 4px;
    color: #64748b;
    font-size: var(--biz-font-small);
  }
}

.compact-list {
  display: grid;
  gap: 8px;
}

.compact-list article {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 0;
  border-top: 1px solid #edf1f7;

  &:first-child {
    border-top: 0;
  }

  strong,
  span {
    display: block;
  }

  strong {
    color: #253858;
    font-size: var(--biz-font-small);
  }

  span {
    margin-top: 4px;
    color: #94a3b8;
    font-size: var(--biz-font-mini);
  }
}

.tag-group {
  display: flex;
  flex: 0 0 auto;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.document-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.document-grid article {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: start;
  padding: 12px;
  border: 1px solid #edf1f7;
  border-radius: 12px;
  background: #fbfdff;

  > i {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 34px;
    border-radius: 10px;
    color: #2563eb;
    background: #eef5ff;
    font-size: var(--biz-font-section);
  }

  strong {
    display: block;
    color: #2563eb;
    font-size: var(--biz-font-small);
    cursor: pointer;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span,
  small,
  p {
    display: block;
    margin-top: 4px;
    color: #94a3b8;
    font-size: var(--biz-font-mini);
  }

  p {
    color: #64748b;
    line-height: 1.5;
  }
}

.archive-strip {
  display: grid;
  gap: 8px;
  margin: 12px 0;

  span {
    padding: 8px 10px;
    border-radius: 8px;
    background: #f6f9ff;
    color: #64748b;
    font-size: var(--biz-font-small);
  }
}

.side-materials {
  margin-top: 10px;
}

.side-timeline article {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 8px;
  padding-bottom: 12px;
  position: relative;

  &:not(:last-child)::after {
    content: "";
    position: absolute;
    left: 5px;
    top: 15px;
    bottom: -3px;
    width: 1px;
    background: #e2e8f0;
  }

  i {
    position: relative;
    z-index: 1;
    width: 11px;
    height: 11px;
    margin-top: 4px;
    border: 3px solid #dbeafe;
    border-radius: 50%;
    background: #2563eb;
  }

  strong {
    display: block;
    color: #253858;
    font-size: var(--biz-font-small);
  }

  p {
    margin: 4px 0;
    color: #64748b;
    font-size: var(--biz-font-small);
    line-height: 1.5;
  }

  span {
    color: #94a3b8;
    font-size: var(--biz-font-mini);
  }
}

.overdue {
  color: #ef4444 !important;
}

.danger-text {
  color: #ef4444 !important;
}

@media (max-width: 1280px) {
  .matter-kpis {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .matter-layout {
    grid-template-columns: 1fr;
  }

  .matter-side-column {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .matter-kpis,
  .matter-side-column,
  .node-board,
  .document-grid,
  .finance-overview {
    grid-template-columns: 1fr;
  }

  .matter-field-grid {
    grid-template-columns: 1fr;

    .wide {
      grid-column: auto;
    }
  }

  .compact-list article,
  .timeline-head {
    flex-direction: column;
  }
}
</style>
