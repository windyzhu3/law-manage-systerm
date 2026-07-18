<template>
  <el-drawer
    :visible.sync="innerVisible"
    size="82%"
    :custom-class="drawerClass"
    append-to-body
    @close="close"
  >
    <div slot="title" class="drawer-title" :class="sizeClass">
      <span>CASE FINANCE PROFILE</span>
      <strong>财务视图</strong>
    </div>

    <div v-loading="loading" class="detail-page finance-detail-page" :class="sizeClass">
      <section class="summary-card finance-summary">
        <div class="doc-icon"><i class="el-icon-data-analysis" /></div>
        <div class="summary-main">
          <h3>{{ summary.caseName || '-' }}</h3>
          <p>
            {{ summary.caseNo || '待生成编号' }}
            <span>·</span>
            {{ summary.customerName || '-' }}
          </p>
          <div class="summary-tags">
            <el-tag :size="controlSize" effect="plain">合同：{{ summary.contractNo || '-' }}</el-tag>
            <el-tag :size="controlSize" type="success" effect="plain">主办：{{ summary.mainLawyerName || '-' }}</el-tag>
            <el-tag :size="controlSize" type="info" effect="plain">协办：{{ summary.assistantLawyerNames || '暂无' }}</el-tag>
          </div>
        </div>
        <div class="finance-summary-amount">
          <span>合同金额</span>
          <strong>{{ formatMoney(summary.contractAmount) }}</strong>
        </div>
      </section>

      <business-todo-summary v-if="Number(caseId) > 0" business-type="FINANCE" :business-id="caseId" :business-no="summary.caseNo" />
      <section class="finance-kpis">
        <article v-for="item in cards" :key="item.key">
          <i :class="item.icon" />
          <div>
            <strong>{{ formatMoney(item.value) }}</strong>
            <span>{{ item.title }}</span>
            <small>{{ item.desc }}</small>
          </div>
        </article>
      </section>

      <div class="finance-layout">
        <main class="finance-main-column">
          <section class="info-card">
            <header>
              <h4>收费计划</h4>
              <span class="section-hint">计划应收与实际已收对比</span>
            </header>
            <div v-if="feePlanBars.length" class="plan-chart">
              <article v-for="item in feePlanBars" :key="item.key" class="plan-stage">
                <div class="bar-pair">
                  <i class="plan-bar" :style="{ height: item.planHeight }" :title="'计划应收 ' + formatMoney(item.receivableAmount)" />
                  <i class="received-bar" :style="{ height: item.receivedHeight }" :title="'实际已收 ' + formatMoney(item.receivedAmount)" />
                </div>
                <strong>第 {{ item.periodNo || '-' }} 期</strong>
                <span>{{ item.planReceiveDate || '未设日期' }}</span>
                <em :class="{ danger: item.pendingAmount > 0 }">待收 {{ formatMoney(item.pendingAmount) }}</em>
                <div class="tag-group">
                  <dict-tag :options="dictOptions.law_contract_receive_status || []" :value="item.confirmStatus" />
                  <dict-tag :options="dictOptions.law_contract_invoice_status || []" :value="item.invoiceStatus" />
                </div>
              </article>
            </div>
            <el-empty v-else :image-size="78" description="暂无收费计划" />
            <div v-if="feePlanBars.length" class="chart-legend">
              <span><i class="plan" />计划应收</span>
              <span><i class="received" />实际已收</span>
            </div>
          </section>

          <section class="info-card">
            <header>
              <h4>费用构成</h4>
              <span class="section-hint">按费用类型汇总案件支出</span>
            </header>
            <div v-if="expenseTypeStats.length" class="expense-composition">
              <i class="expense-donut" :style="expenseDonutStyle">
                <b>{{ formatShortMoney(summary.expenseTotal) }}</b>
                <span>总支出</span>
              </i>
              <ul>
                <li v-for="item in expenseTypeStats" :key="item.type">
                  <em :style="{ background: item.color }" />
                  <span>{{ item.label }}</span>
                  <strong>{{ formatMoney(item.amount) }}</strong>
                  <small>{{ item.percent }}</small>
                </li>
              </ul>
            </div>
            <el-empty v-else :image-size="78" description="暂无案件费用" />
          </section>

          <section class="info-card span-2">
            <header>
              <h4>收益结构</h4>
              <span class="section-hint">从合同金额到案件收益的只读拆解</span>
            </header>
            <div class="income-bars">
              <div v-for="item in incomeBars" :key="item.key">
                <span>{{ item.label }}</span>
                <i><em :class="item.color" :style="{ width: item.width }" /></i>
                <strong>{{ formatMoney(item.value) }}</strong>
              </div>
            </div>
          </section>

          <section class="info-card span-2">
            <header>
              <h4>近期费用</h4>
              <span class="section-hint">最近发生的案件费用记录</span>
            </header>
            <div v-if="recentExpenses.length" class="compact-list">
              <article v-for="item in recentExpenses" :key="item.expenseId || item.expense_id || item.createTime">
                <div>
                  <strong>{{ dictLabel('law_case_expense_type', item.expenseType || item.expense_type) }} · {{ formatMoney(item.amount) }}</strong>
                  <span>{{ item.occurDate || item.occur_date || '-' }} · 经办人：{{ item.handlerName || item.handler_name || '-' }}</span>
                </div>
                <div class="tag-group">
                  <dict-tag :options="dictOptions.law_case_pay_status || []" :value="item.payStatus || item.pay_status" />
                  <dict-tag :options="dictOptions.law_case_reimburse_status || []" :value="item.reimburseStatus || item.reimburse_status" />
                  <dict-tag :options="dictOptions.law_case_voucher_status || []" :value="item.voucherStatus || item.voucher_status" />
                </div>
              </article>
            </div>
            <el-empty v-else :image-size="78" description="暂无近期费用" />
          </section>
        </main>

        <aside class="finance-side-column">
          <section class="info-card">
            <header><h4>业务概览</h4></header>
            <div class="overview-grid">
              <div><b>{{ feePlans.length }}</b><span>收费期次</span></div>
              <div><b>{{ expenses.length }}</b><span>费用记录</span></div>
              <div><b>{{ formatPercent(summary.grossMargin) }}</b><span>毛利率</span></div>
              <div><b>{{ formatMoney(summary.pendingTotal) }}</b><span>待收余额</span></div>
            </div>
          </section>

          <section class="info-card">
            <header><h4>费用状态</h4></header>
            <div class="state-grid">
              <div><span>已付款</span><strong>{{ formatMoney(summary.paidExpenseTotal) }}</strong></div>
              <div><span>未付款</span><strong>{{ formatMoney(summary.unpaidExpenseTotal) }}</strong></div>
              <div><span>已报销</span><strong>{{ reimbursedCount }} 条</strong></div>
              <div><span>缺凭证</span><strong>{{ summary.voucherMissingCount || 0 }} 条</strong></div>
            </div>
          </section>

          <section class="info-card">
            <header>
              <h4>风险提醒</h4>
              <span class="section-count">{{ controlAlerts.length }} 项</span>
            </header>
            <div v-if="controlAlerts.length" class="side-alert-list">
              <article v-for="item in controlAlerts" :key="item.key" :class="item.type">
                <i :class="item.icon" />
                <div>
                  <strong>{{ item.title }}</strong>
                  <p>{{ item.desc }}</p>
                  <span>{{ item.value }}</span>
                </div>
              </article>
            </div>
            <el-empty v-else :image-size="72" description="暂无财务风险" />
          </section>
        </aside>
      </div>
    </div>
  </el-drawer>
</template>

<script>
import { getCaseFinance } from '@/api/finance'
import BusinessTodoSummary from '@/views/todo/components/BusinessTodoSummary'

export default {
  name: 'CaseFinanceDrawer',
  components: { BusinessTodoSummary },
  props: {
    visible: { type: Boolean, default: false },
    caseId: { type: [String, Number], default: null },
    controlSize: { type: String, default: 'small' },
    sizeClass: { type: String, default: '' },
    dictOptions: { type: Object, default: () => ({}) }
  },
  data() {
    return {
      innerVisible: false,
      loading: false,
      caseFinance: {}
    }
  },
  computed: {
    drawerClass() {
      return ['finance-case-drawer', this.sizeClass].filter(Boolean).join(' ')
    },
    summary() {
      return this.caseFinance.summary || {}
    },
    feePlans() {
      return this.caseFinance.feePlans || []
    },
    expenses() {
      return this.caseFinance.expenses || []
    },
    cards() {
      const row = this.summary
      return [
        { key: 'receivableTotal', title: '应收总额', value: row.receivableTotal || 0, desc: '收费计划累计', icon: 'el-icon-date' },
        { key: 'receivedTotal', title: '已收金额', value: row.receivedTotal || 0, desc: '已确认回款', icon: 'el-icon-wallet' },
        { key: 'pendingTotal', title: '待收金额', value: row.pendingTotal || 0, desc: '未完成回款', icon: 'el-icon-bank-card' },
        { key: 'expenseTotal', title: '案件支出', value: row.expenseTotal || 0, desc: '办案费用合计', icon: 'el-icon-money' },
        { key: 'grossProfit', title: '案件收益', value: row.grossProfit || 0, desc: `毛利率 ${this.formatPercent(row.grossMargin)}`, icon: 'el-icon-data-analysis' }
      ]
    },
    feePlanBars() {
      const max = Math.max(...this.feePlans.map(item => Number(item.receivableAmount || item.receivedAmount || 0)), 1)
      return this.feePlans.map((item, index) => {
        const receivableAmount = Number(item.receivableAmount || 0)
        const receivedAmount = Number(item.receivedAmount || 0)
        return {
          ...item,
          key: item.planId || index,
          receivableAmount,
          receivedAmount,
          pendingAmount: Number(item.pendingAmount || 0),
          planHeight: this.barHeight(receivableAmount, max),
          receivedHeight: this.barHeight(receivedAmount, max)
        }
      })
    },
    expenseTypeStats() {
      const colors = ['#2563eb', '#22c55e', '#f97316', '#7c3aed', '#06b6d4', '#ef4444']
      const grouped = this.expenses.reduce((target, item) => {
        const key = item.expenseType || item.expense_type || 'unknown'
        target[key] = (target[key] || 0) + Number(item.amount || 0)
        return target
      }, {})
      const total = Object.values(grouped).reduce((sum, value) => sum + Number(value || 0), 0)
      return Object.keys(grouped).map((key, index) => ({
        type: key,
        label: this.dictLabel('law_case_expense_type', key),
        amount: grouped[key],
        color: colors[index % colors.length],
        percent: total ? (grouped[key] * 100 / total).toFixed(1) + '%' : '0%'
      }))
    },
    expenseDonutStyle() {
      const total = this.expenseTypeStats.reduce((sum, item) => sum + Number(item.amount || 0), 0)
      if (!total) return { background: '#eef2ff' }
      let start = 0
      const parts = this.expenseTypeStats.map(item => {
        const deg = item.amount / total * 360
        const text = `${item.color} ${start}deg ${start + deg}deg`
        start += deg
        return text
      })
      return { background: `conic-gradient(${parts.join(',')})` }
    },
    reimbursedCount() {
      return this.expenses.filter(item => ['reimbursed', 'done', 'paid'].includes(item.reimburseStatus || item.reimburse_status)).length
    },
    recentExpenses() {
      return this.expenses.slice(0, 4)
    },
    incomeBars() {
      const base = Math.max(
        Number(this.summary.contractAmount || 0),
        Number(this.summary.receivableTotal || 0),
        Number(this.summary.receivedTotal || 0),
        1
      )
      return [
        { key: 'contract', label: '合同金额', value: Number(this.summary.contractAmount || 0), color: 'blue' },
        { key: 'receivable', label: '应收总额', value: Number(this.summary.receivableTotal || 0), color: 'cyan' },
        { key: 'received', label: '已收金额', value: Number(this.summary.receivedTotal || 0), color: 'green' },
        { key: 'expense', label: '案件支出', value: Number(this.summary.expenseTotal || 0), color: 'orange' },
        { key: 'profit', label: '案件收益', value: Number(this.summary.grossProfit || 0), color: 'violet' }
      ].map(item => ({ ...item, width: Math.max(6, Math.min(100, Math.abs(item.value) / base * 100)) + '%' }))
    },
    controlAlerts() {
      const row = this.summary
      const alerts = []
      if (Number(row.overdueTotal || 0) > 0) {
        alerts.push({ key: 'overdue', type: 'danger', title: '存在逾期应收', desc: '收费计划已超过计划回款日', value: this.formatMoney(row.overdueTotal), icon: 'el-icon-warning-outline' })
      }
      if (Number(row.pendingTotal || 0) > 0) {
        alerts.push({ key: 'pending', type: 'warning', title: '仍有待收余额', desc: '该案件仍有未完成回款金额', value: this.formatMoney(row.pendingTotal), icon: 'el-icon-bank-card' })
      }
      if (Number(row.unpaidExpenseTotal || 0) > 0) {
        alerts.push({ key: 'unpaid', type: 'warning', title: '存在未付款费用', desc: '案件费用付款状态未完全闭环', value: this.formatMoney(row.unpaidExpenseTotal), icon: 'el-icon-money' })
      }
      if (Number(row.voucherMissingCount || 0) > 0) {
        alerts.push({ key: 'voucher', type: 'primary', title: '费用凭证缺失', desc: '存在未上传或未完善凭证的费用', value: row.voucherMissingCount + ' 条', icon: 'el-icon-document-delete' })
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
    barHeight(value, max) {
      return Math.max(8, Number(value || 0) / max * 118) + 'px'
    },
    dictLabel(type, value) {
      const rows = this.dictOptions[type] || []
      const item = rows.find(option => String(option.value) === String(value))
      return item ? item.label : (value || '-')
    },
    formatPercent(value) {
      return Number(value || 0).toFixed(2) + '%'
    },
    formatShortMoney(value) {
      const num = Number(value || 0)
      if (Math.abs(num) >= 10000) return (num / 10000).toLocaleString('zh-CN', { maximumFractionDigits: 1 }) + '万'
      return num.toLocaleString('zh-CN')
    },
    formatMoney(value) {
      if (value === undefined || value === null || value === '') return '-'
      const num = Number(value || 0)
      if (Number.isNaN(num)) return '-'
      return '¥ ' + num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    }
  }
}
</script>

<style lang="scss" scoped>
@import "../../business/detail-drawer.scss";

.finance-detail-page {
  padding-top: 2px;
}

.finance-summary {
  align-items: flex-start;
  margin-bottom: 12px;
}

.summary-main p span {
  margin: 0 6px;
  color: #cbd5e1;
}

.finance-summary-amount {
  flex: 0 0 190px;
  min-width: 180px;
  padding: 12px 14px;
  border: 1px solid #e8edf6;
  border-radius: 12px;
  background: #f8fbff;

  span,
  strong {
    display: block;
  }

  span {
    color: #94a3b8;
    font-size: var(--biz-font-mini);
  }

  strong {
    margin-top: 6px;
    color: #2563eb;
    font-size: var(--biz-font-section);
    line-height: 1.25;
  }
}

.finance-kpis {
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
  span,
  small {
    display: block;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #102a6b;
    font-size: var(--biz-font-section);
    line-height: 1.2;
  }

  span {
    margin-top: 4px;
    color: #64748b;
    font-size: var(--biz-font-small);
  }

  small {
    margin-top: 3px;
    color: #94a3b8;
    font-size: var(--biz-font-mini);
  }
}

.finance-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 10px;
  align-items: start;
}

.finance-main-column {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, .85fr);
  gap: 10px;
}

.finance-side-column {
  display: grid;
  gap: 10px;
}

.finance-side-column .info-card {
  box-shadow: none;
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

.plan-chart {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(86px, 1fr));
  gap: 10px;
  align-items: end;
  min-height: 228px;
}

.plan-stage {
  display: grid;
  justify-items: center;
  gap: 5px;
  min-width: 0;

  strong {
    color: #334155;
    font-size: var(--biz-font-small);
    line-height: 1.4;
  }

  span,
  em {
    color: #64748b;
    font-size: var(--biz-font-mini);
    font-style: normal;
    line-height: 1.5;
  }

  em.danger {
    color: #ef4444;
  }
}

.bar-pair {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 7px;
  width: 50px;
  height: 130px;
  padding: 8px 6px;
  border-radius: 12px;
  background: #f8fafc;

  i {
    width: 14px;
    min-height: 8px;
    border-radius: 999px 999px 4px 4px;
  }
}

.plan-bar {
  background: linear-gradient(180deg, #60a5fa, #2563eb);
}

.received-bar {
  background: linear-gradient(180deg, #86efac, #22c55e);
}

.tag-group {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  align-items: center;
  justify-content: flex-end;
}

.plan-stage .tag-group {
  justify-content: center;
}

.chart-legend {
  display: flex;
  gap: 14px;
  margin-top: 10px;
  color: #64748b;
  font-size: var(--biz-font-mini);

  span {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  i {
    width: 9px;
    height: 9px;
    border-radius: 50%;
  }

  .plan { background: #2563eb; }
  .received { background: #22c55e; }
}

.expense-composition {
  display: grid;
  grid-template-columns: 146px minmax(0, 1fr);
  gap: 14px;
  align-items: center;
  min-height: 228px;
}

.expense-donut {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 146px;
  height: 146px;
  border-radius: 50%;

  &::after {
    content: '';
    position: absolute;
    inset: 28px;
    border-radius: 50%;
    background: #fff;
  }

  b,
  span {
    position: relative;
    z-index: 1;
  }

  b {
    color: #102a6b;
    font-size: var(--biz-font-section);
    line-height: 1.2;
  }

  span {
    color: #64748b;
    font-size: var(--biz-font-mini);
  }
}

.expense-composition ul {
  padding: 0;
  margin: 0;
  list-style: none;

  li {
    display: grid;
    grid-template-columns: 9px minmax(0, 1fr) auto;
    align-items: center;
    gap: 7px;
    margin: 8px 0;
    color: #64748b;
    font-size: var(--biz-font-mini);
  }

  em {
    width: 8px;
    height: 8px;
    border-radius: 999px;
  }

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: #334155;
  }

  small {
    grid-column: 2 / 4;
    color: #94a3b8;
  }
}

.income-bars {
  display: grid;
  gap: 10px;

  div {
    display: grid;
    grid-template-columns: 78px minmax(0, 1fr) 136px;
    align-items: center;
    gap: 10px;
    font-size: var(--biz-font-small);
  }

  span {
    color: #64748b;
  }

  i {
    height: 8px;
    overflow: hidden;
    border-radius: 999px;
    background: #eef2f7;
  }

  em {
    display: block;
    height: 100%;
    border-radius: inherit;
  }

  strong {
    color: #334155;
    text-align: right;
  }

  .blue { background: #2563eb; }
  .cyan { background: #06b6d4; }
  .green { background: #22c55e; }
  .orange { background: #f97316; }
  .violet { background: #7c3aed; }
}

.compact-list {
  display: grid;
  gap: 0;

  article {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: center;
    gap: 10px;
    padding: 10px 0;
    border-top: 1px solid #edf1f7;

    &:first-child {
      border-top: 0;
      padding-top: 0;
    }

    &:last-child {
      padding-bottom: 0;
    }
  }

  strong {
    display: block;
    color: #334155;
    font-size: var(--biz-font-small);
  }

  span {
    display: block;
    margin-top: 4px;
    color: #94a3b8;
    font-size: var(--biz-font-mini);
  }
}

.overview-grid,
.state-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;

  div {
    min-width: 0;
    padding: 10px;
    border-radius: 10px;
    background: #f8fafc;
  }

  b,
  strong,
  span {
    display: block;
    min-width: 0;
  }

  b,
  strong {
    color: #102a6b;
    font-size: var(--biz-font-small);
    line-height: 1.35;
    overflow-wrap: anywhere;
  }

  span {
    margin-top: 4px;
    color: #64748b;
    font-size: var(--biz-font-mini);
  }
}

.side-alert-list {
  display: grid;
  gap: 9px;

  article {
    display: grid;
    grid-template-columns: 34px minmax(0, 1fr);
    gap: 9px;
    padding: 10px;
    border-radius: 10px;
    background: #f8fafc;
  }

  i {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 34px;
    border-radius: 50%;
  }

  strong,
  p,
  span {
    display: block;
    margin: 0;
  }

  strong {
    color: #334155;
    font-size: var(--biz-font-small);
  }

  p {
    margin-top: 3px;
    color: #64748b;
    font-size: var(--biz-font-mini);
    line-height: 1.6;
  }

  span {
    margin-top: 5px;
    color: #102a6b;
    font-size: var(--biz-font-small);
    font-weight: 600;
  }

  .danger i {
    color: #ef4444;
    background: #fee2e2;
  }

  .warning i {
    color: #f97316;
    background: #ffedd5;
  }

  .primary i {
    color: #2563eb;
    background: #eaf2ff;
  }
}

@media (max-width: 1280px) {
  .finance-kpis,
  .finance-layout,
  .finance-main-column {
    grid-template-columns: 1fr;
  }

  .finance-summary {
    flex-direction: column;
  }

  .finance-summary-amount {
    width: 100%;
    flex-basis: auto;
  }
}
</style>
