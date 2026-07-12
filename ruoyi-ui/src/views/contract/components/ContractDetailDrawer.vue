<template>
  <el-drawer :visible.sync="innerVisible" size="74%" :custom-class="drawerClass" append-to-body @close="$emit('update:visible', false)">
    <div slot="title" class="drawer-title" :class="sizeClass">
      <span>CONTRACT PROFILE</span>
      <strong>合同详情</strong>
    </div>
    <div class="detail-page" :class="sizeClass">
      <section class="summary-card">
        <div class="doc-icon"><svg-icon icon-class="documentation" /></div>
        <div class="summary-main">
          <h3>{{ contract.contractName || '-' }}</h3>
          <p>{{ contract.contractNo || '待生成编号' }} · {{ contract.customerName || '-' }}</p>
          <div class="summary-tags summary-status">
            <dict-tag :options="dict.type.law_contract_audit_status" :value="auditStatusOf(contract)" />
            <dict-tag :options="dict.type.law_contract_sign_status" :value="signStatusOf(contract)" />
            <dict-tag :options="dict.type.law_contract_status" :value="contractStatusOf(contract)" />
          </div>
        </div>
        <div class="summary-actions managed-actions">
          <el-button v-hasPermi="['contract:edit']" :size="controlSize" type="primary" icon="el-icon-edit" :disabled="!canEdit" @click="$emit('edit', contract)">编辑</el-button>
          <el-button v-hasPermi="['contract:submit']" :size="controlSize" icon="el-icon-s-check" :disabled="!canSubmit" @click="$emit('submit', contract)">提交审批</el-button>
          <el-button v-hasPermi="['contract:approval:handle']" :size="controlSize" icon="el-icon-check" :disabled="!isAuditReviewing" @click="$emit('approval', contract)">审批</el-button>
          <el-button v-hasPermi="['contract:fee:add']" :size="controlSize" icon="el-icon-money" :disabled="!canEditResource" @click="$emit('fee', contract)">新增收费</el-button>
          <el-button v-hasPermi="['contract:attachment:add']" :size="controlSize" icon="el-icon-upload2" :disabled="!canEditResource" @click="$emit('attachment', contract)">上传附件</el-button>
          <el-button v-hasPermi="['contract:sign']" :size="controlSize" icon="el-icon-finished" :disabled="!canSign" @click="$emit('sign', contract)">{{ signActionText }}</el-button>
          <el-button v-hasPermi="['matter:list', 'matter:mine:list']" :size="controlSize" icon="el-icon-folder-opened" @click="$emit('matter', contract)">查看案件</el-button>
        </div>
      </section>

      <business-todo-summary v-if="contract.contractId" business-type="CONTRACT" :business-id="contract.contractId" :business-no="contract.contractNo" />

      <div class="detail-grid">
        <section class="info-card span-2">
          <header><h4>基础信息</h4></header>
          <dl class="info-list">
            <div><dt>客户名称</dt><dd>{{ contract.customerName || '-' }}</dd></div>
            <div><dt>案件类型</dt><dd>{{ dictLabel('law_contract_case_type', contract.caseType) }}</dd></div>
            <div><dt>签约金额</dt><dd>{{ formatMoney(contract.signAmount) }}</dd></div>
            <div><dt>收费方式</dt><dd>{{ dictLabel('law_contract_fee_type', contract.feeType) }}</dd></div>
            <div><dt>签订方式</dt><dd>{{ dictLabel('law_contract_sign_method', contract.signMethod) }}</dd></div>
            <div><dt>风险等级</dt><dd>{{ dictLabel('law_contract_risk_level', contract.riskLevel) }}</dd></div>
            <div><dt>负责人</dt><dd>{{ contract.ownerName || '-' }}</dd></div>
            <div><dt>所属部门</dt><dd>{{ contract.deptName || '-' }}</dd></div>
            <div><dt>签订日期</dt><dd>{{ contract.signDate || '-' }}</dd></div>
            <div><dt>有效期</dt><dd>{{ contract.effectiveDate || '-' }} 至 {{ contract.expireDate || '-' }}</dd></div>
            <div class="wide"><dt>备注</dt><dd>{{ contract.remark || '-' }}</dd></div>
          </dl>
        </section>

        <section class="info-card">
          <header><h4>收费计划</h4></header>
          <div v-if="fees.length" class="mini-list">
            <article v-for="item in fees" :key="item.plan_id">
              <b>第 {{ item.period_no }} 期 · {{ formatMoney(item.receivable_amount) }}</b>
              <span>{{ item.plan_receive_date || '-' }} · {{ dictLabel('law_contract_receive_status', receiveStatusOf(item)) }} · {{ dictLabel('law_contract_invoice_status', invoiceStatusOf(item)) }}</span>
            </article>
          </div>
          <el-empty v-else description="暂无收费计划" :image-size="80" />
        </section>

        <section class="info-card">
          <header><h4>合同附件</h4></header>
          <div v-if="attachments.length" class="mini-list">
            <article v-for="item in attachments" :key="item.attachment_id">
              <b><a @click="openAttachmentFile(item)">{{ item.file_name || fileNameFromUrl(item.file_url) }}</a></b>
              <span>{{ item.file_type || '文件' }} · {{ item.create_time || '-' }}</span>
            </article>
          </div>
          <el-empty v-else description="暂无附件" :image-size="80" />
        </section>

        <section class="info-card">
          <header><h4>审批记录</h4></header>
          <div v-if="approvals.length" class="timeline">
            <article v-for="item in approvals" :key="item.approval_id || item.create_time">
              <i class="el-icon-check" />
              <div>
                <h5>{{ dictLabel('law_contract_approval_action', item.approval_action) }} <span>{{ item.approver_name || item.create_by || '-' }}</span></h5>
                <p>{{ item.approval_opinion || item.opinion || '-' }}</p>
                <small>{{ item.create_time || '-' }}</small>
              </div>
            </article>
          </div>
          <el-empty v-else description="暂无审批记录" :image-size="80" />
        </section>

        <section class="info-card">
          <header><h4>状态记录</h4></header>
          <div v-if="statuses.length" class="timeline">
            <article v-for="item in statuses" :key="item.log_id || item.create_time">
              <i class="el-icon-time" />
              <div>
                <h5>{{ dictLabel('law_contract_status_action', item.action_type) }} <span>{{ item.create_by || '-' }}</span></h5>
                <p>{{ item.content || '-' }}</p>
                <small>{{ item.create_time || '-' }}</small>
              </div>
            </article>
          </div>
          <el-empty v-else description="暂无状态记录" :image-size="80" />
        </section>
      </div>
    </div>
  </el-drawer>
</template>

<script>
import businessUi from '@/views/business/mixins/businessUi'
import contractLifecycle from '@/views/business/mixins/contractLifecycle'
import BusinessTodoSummary from '@/views/todo/components/BusinessTodoSummary'

export default {
  name: 'ContractDetailDrawer',
  components: { BusinessTodoSummary },
  mixins: [businessUi, contractLifecycle],
  dicts: ['law_contract_case_type', 'law_contract_fee_type', 'law_contract_sign_method', 'law_contract_sign_status', 'law_contract_audit_status', 'law_contract_approval_action', 'law_contract_status', 'law_contract_status_action', 'law_contract_risk_level', 'law_contract_receive_status', 'law_contract_invoice_status'],
  props: {
    visible: { type: Boolean, default: false },
    contract: { type: Object, default: () => ({}) },
    fees: { type: Array, default: () => [] },
    attachments: { type: Array, default: () => [] },
    approvals: { type: Array, default: () => [] },
    statuses: { type: Array, default: () => [] },
    sizeClass: { type: String, default: '' }
  },
  computed: {
    innerVisible: {
      get() { return this.visible },
      set(value) { this.$emit('update:visible', value) }
    },
    isAuditReviewing() { return this.isContractAuditReviewing(this.contract) },
    canSubmit() { return this.canSubmitContract(this.contract) },
    canEdit() { return this.canEditContract(this.contract) },
    canSign() { return this.canSignContract(this.contract) },
    canArchive() { return this.canArchiveContract(this.contract) },
    canVoid() { return this.canVoidContract(this.contract) },
    canTerminate() { return this.canTerminateContract(this.contract) },
    canEditResource() { return this.canEditContractResource(this.contract) },
    signActionText() {
      return this.sameValue(this.signStatusOf(this.contract), this.contractStates.signPartial) ? '补齐签署' : '签署'
    },
    drawerClass() {
      return ['contract-detail-drawer', this.sizeClass].filter(Boolean).join(' ')
    }
  },
  methods: {
    openAttachmentFile(item) {
      this.openBusinessFile(item.file_url || item.fileUrl)
    }
  }
}
</script>

<style scoped lang="scss">
@import "../../business/detail-drawer.scss";
</style>
