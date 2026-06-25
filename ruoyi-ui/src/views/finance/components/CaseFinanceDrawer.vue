<template>
  <el-drawer
    title="案件财务视图"
    :visible.sync="innerVisible"
    size="920px"
    custom-class="finance-case-drawer"
    append-to-body
    @close="close"
  >
    <div v-loading="loading" class="case-finance-body">
      <section class="case-finance-summary">
        <div>
          <span>案件</span>
          <h2>{{ summary.caseName || '-' }}</h2>
          <p>{{ summary.caseNo || '-' }} · {{ summary.customerName || '-' }}</p>
        </div>
        <div>
          <span>来源合同</span>
          <h3>{{ summary.contractNo || '-' }}</h3>
          <p>{{ summary.contractName || '-' }}</p>
        </div>
        <div>
          <span>主办律师</span>
          <h3>{{ summary.mainLawyerName || '-' }}</h3>
          <p>{{ summary.assistantLawyerNames || '暂无协办' }}</p>
        </div>
      </section>

      <section class="case-finance-metrics">
        <div v-for="item in cards" :key="item.key">
          <i :class="item.icon" />
          <span>{{ item.title }}</span>
          <strong>{{ item.formatter ? item.formatter(item.value) : item.value }}</strong>
          <small>{{ item.desc }}</small>
        </div>
      </section>

      <section v-if="controlAlerts.length" class="case-finance-alerts">
        <div v-for="item in controlAlerts" :key="item.key" :class="['finance-alert', item.type]">
          <i :class="item.icon" />
          <span>
            <b>{{ item.title }}</b>
            <small>{{ item.desc }}</small>
          </span>
          <strong>{{ item.value }}</strong>
        </div>
      </section>

      <section class="case-finance-grid">
        <article class="finance-card">
          <div class="finance-card-title">
            <div>
              <h3>收费计划</h3>
              <p>合同计划、回款和开票状态</p>
            </div>
          </div>
          <el-table :data="caseFinance.feePlans || []" :size="controlSize" max-height="280">
            <el-table-column label="期次" prop="periodNo" width="70" align="center" />
            <el-table-column label="应收金额" width="110" align="right">
              <template slot-scope="{ row }">{{ formatMoney(row.receivableAmount) }}</template>
            </el-table-column>
            <el-table-column label="已收金额" width="110" align="right">
              <template slot-scope="{ row }">{{ formatMoney(row.receivedAmount) }}</template>
            </el-table-column>
            <el-table-column label="待收金额" width="110" align="right">
              <template slot-scope="{ row }"><span :class="{ 'danger-text': Number(row.pendingAmount || 0) > 0 }">{{ formatMoney(row.pendingAmount) }}</span></template>
            </el-table-column>
            <el-table-column label="计划日期" prop="planReceiveDate" width="110" />
            <el-table-column label="回款" width="96" align="center">
              <template slot-scope="{ row }">
                <dict-tag :options="dictOptions.law_contract_receive_status || []" :value="row.confirmStatus" />
              </template>
            </el-table-column>
            <el-table-column label="付款方式" width="110" align="center">
              <template slot-scope="{ row }">
                <dict-tag :options="dictOptions.law_finance_payment_method || []" :value="row.paymentMethod" />
              </template>
            </el-table-column>
            <el-table-column label="开票" width="96" align="center">
              <template slot-scope="{ row }">
                <dict-tag :options="dictOptions.law_contract_invoice_status || []" :value="row.invoiceStatus" />
              </template>
            </el-table-column>
            <el-table-column label="发票类型" width="120" align="center">
              <template slot-scope="{ row }">
                <dict-tag :options="dictOptions.law_finance_invoice_type || []" :value="row.invoiceType" />
              </template>
            </el-table-column>
            <el-table-column label="备注" prop="remark" min-width="120" show-overflow-tooltip />
          </el-table>
        </article>

        <article class="finance-card">
          <div class="finance-card-title">
            <div>
              <h3>案件费用</h3>
              <p>办案支出、付款、报销和凭证状态</p>
            </div>
          </div>
          <el-table :data="caseFinance.expenses || []" :size="controlSize" max-height="280">
            <el-table-column label="费用类型" min-width="110" align="center">
              <template slot-scope="{ row }">
                <dict-tag :options="dictOptions.law_case_expense_type || []" :value="row.expenseType" />
              </template>
            </el-table-column>
            <el-table-column label="金额" width="110" align="right">
              <template slot-scope="{ row }">{{ formatMoney(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="发生日期" prop="occurDate" width="110" />
            <el-table-column label="付款" width="88" align="center">
              <template slot-scope="{ row }">
                <dict-tag :options="dictOptions.law_case_pay_status || []" :value="row.payStatus" />
              </template>
            </el-table-column>
            <el-table-column label="凭证" width="88" align="center">
              <template slot-scope="{ row }">
                <dict-tag :options="dictOptions.law_case_voucher_status || []" :value="row.voucherStatus" />
              </template>
            </el-table-column>
            <el-table-column label="凭证文件" min-width="120" show-overflow-tooltip>
              <template slot-scope="{ row }">{{ row.voucherName || '-' }}</template>
            </el-table-column>
          </el-table>
        </article>
      </section>
    </div>
  </el-drawer>
</template>

<script>
import { getCaseFinance } from '@/api/finance'

export default {
  name: 'CaseFinanceDrawer',
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    caseId: {
      type: [String, Number],
      default: null
    },
    controlSize: {
      type: String,
      default: 'small'
    },
    dictOptions: {
      type: Object,
      default: () => ({})
    }
  },
  data() {
    return {
      innerVisible: false,
      loading: false,
      caseFinance: {}
    }
  },
  computed: {
    summary() {
      return this.caseFinance.summary || {}
    },
    cards() {
      const row = this.summary
      return [
        { key: 'contractAmount', title: '合同金额', value: row.contractAmount || 0, desc: '来源合同签约金额', icon: 'el-icon-document', formatter: this.formatMoney },
        { key: 'receivedTotal', title: '已收金额', value: row.receivedTotal || 0, desc: '已确认回款', icon: 'el-icon-wallet', formatter: this.formatMoney },
        { key: 'pendingTotal', title: '待收金额', value: row.pendingTotal || 0, desc: '收费计划未收余额', icon: 'el-icon-bank-card', formatter: this.formatMoney },
        { key: 'expenseTotal', title: '案件费用', value: row.expenseTotal || 0, desc: '办案支出合计', icon: 'el-icon-money', formatter: this.formatMoney },
        { key: 'grossProfit', title: '案件毛利', value: row.grossProfit || 0, desc: `毛利率 ${Number(row.grossMargin || 0).toFixed(2)}%`, icon: 'el-icon-data-analysis', formatter: this.formatMoney }
      ]
    },
    controlAlerts() {
      const row = this.summary
      const alerts = []
      if (Number(row.overdueTotal || 0) > 0) {
        alerts.push({ key: 'overdue', type: 'danger', title: '存在逾期应收', desc: '该案件有关联合同收费计划已超过计划回款日', value: this.formatMoney(row.overdueTotal), icon: 'el-icon-warning-outline' })
      }
      if (Number(row.pendingTotal || 0) > 0) {
        alerts.push({ key: 'pending', type: 'warning', title: '仍有待收余额', desc: '建议跟进回款计划或拆分后续回款安排', value: this.formatMoney(row.pendingTotal), icon: 'el-icon-bank-card' })
      }
      if (Number(row.voucherMissingCount || 0) > 0) {
        alerts.push({ key: 'voucher', type: 'primary', title: '费用凭证缺失', desc: '存在未上传或未完善凭证的案件费用', value: row.voucherMissingCount + ' 笔', icon: 'el-icon-document-delete' })
      }
      return alerts
    }
  },
  watch: {
    visible: {
      immediate: true,
      handler(value) {
        this.innerVisible = value
        if (value) this.load()
      }
    },
    caseId() {
      if (this.innerVisible) this.load()
    }
  },
  methods: {
    close() {
      this.$emit('update:visible', false)
    },
    load() {
      if (!this.caseId) {
        this.caseFinance = {}
        return
      }
      this.loading = true
      getCaseFinance(this.caseId).then(res => {
        this.caseFinance = res.data || {}
      }).finally(() => {
        this.loading = false
      })
    },
    formatMoney(value) {
      const num = Number(value || 0)
      return '¥ ' + num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    }
  }
}
</script>

<style lang="scss" scoped>
.case-finance-body {
  padding: 0 20px 20px;
}

.case-finance-summary {
  display: grid;
  grid-template-columns: 1.4fr 1fr 1fr;
  gap: 12px;
  padding: 16px;
  border: 1px solid #e8edf6;
  border-radius: 14px;
  background: linear-gradient(135deg, #f8fbff 0%, #eef6ff 100%);

  span {
    color: #64748b;
    font-size: var(--biz-font-mini);
  }

  h2,
  h3 {
    margin: 6px 0;
    color: #0f172a;
  }

  h2 {
    font-size: var(--biz-font-title);
  }

  h3 {
    font-size: var(--biz-font-card);
  }

  p {
    margin: 0;
    color: #64748b;
    font-size: var(--biz-font-small);
  }
}

.case-finance-metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin: 14px 0;

  div {
    display: grid;
    grid-template-columns: 36px 1fr;
    gap: 4px 10px;
    padding: 14px;
    border: 1px solid #e8edf6;
    border-radius: 12px;
    background: #fff;
  }

  i {
    grid-row: span 3;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    border-radius: 50%;
    color: #2563eb;
    background: #eef4ff;
  }

  span {
    color: #64748b;
    font-size: var(--biz-font-mini);
  }

  strong {
    color: #0f172a;
    font-size: var(--biz-font-section);
  }

  small {
    color: #94a3b8;
    font-size: var(--biz-font-mini);
  }
}

.case-finance-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.case-finance-alerts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.finance-alert {
  display: grid;
  grid-template-columns: 38px 1fr auto;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid #e8edf6;
  border-radius: 12px;
  background: #fff;

  i {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 38px;
    height: 38px;
    border-radius: 50%;
  }

  b {
    display: block;
    color: #0f172a;
    font-size: var(--biz-font-small);
  }

  small {
    display: block;
    margin-top: 3px;
    color: #64748b;
    font-size: var(--biz-font-mini);
  }

  strong {
    color: #0f172a;
    font-size: var(--biz-font-card);
  }

  &.danger i {
    color: #ef4444;
    background: #fee2e2;
  }

  &.warning i {
    color: #f97316;
    background: #ffedd5;
  }

  &.primary i {
    color: #2563eb;
    background: #eaf2ff;
  }
}

.finance-card {
  padding: 16px;
  border: 1px solid #e8edf6;
  border-radius: 16px;
  background: #fff;
}

.finance-card-title {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;

  h3 {
    margin: 0;
    color: #0f172a;
    font-size: var(--biz-font-section);
  }

  p {
    margin: 4px 0 0;
    color: #64748b;
    font-size: var(--biz-font-mini);
  }
}

@media (max-width: 1280px) {
  .case-finance-summary,
  .case-finance-metrics,
  .case-finance-alerts,
  .case-finance-grid {
    grid-template-columns: 1fr;
  }
}

.danger-text {
  color: #ef4444;
  font-weight: 600;
}
</style>
