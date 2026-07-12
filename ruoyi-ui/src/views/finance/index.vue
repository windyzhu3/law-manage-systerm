<template>
  <div class="biz-page finance-page" :class="'biz-size-' + appSize">
    <biz-page-header eyebrow="FINANCE CENTER" :title="pageTitle" :description="pageDescription">
      <el-button v-if="mode === 'overview'" :size="controlSize" plain icon="el-icon-tickets" @click="switchMode('receivable')">查看应收</el-button>
      <el-button v-if="mode === 'overview'" :size="controlSize" plain icon="el-icon-wallet" @click="switchMode('payment')">待确认回款</el-button>
      <el-button v-if="mode === 'overview'" :size="controlSize" type="primary" icon="el-icon-document-checked" @click="switchMode('invoice')">开票管理</el-button>
    </biz-page-header>
    <biz-hero :eyebrow="heroMeta.eyebrow" :title="heroMeta.title" :description="heroMeta.description" />
    <biz-metrics :metrics="metrics" :config="metricConfig" />

    <template v-if="mode === 'overview'">
      <finance-flow-overview :cards="financeFlowCards" :money-formatter="formatMoney" />

      <section class="finance-charts-grid">
        <article class="finance-card finance-chart-card">
          <div class="finance-card-title">
            <div>
              <h3>回款趋势（万元）</h3>
              <p>近 6 个月确认回款金额与回款笔数</p>
            </div>
            <el-button :size="controlSize" type="text" @click="switchMode('payment')">更多</el-button>
          </div>
          <div v-if="receiveTrendRows.length" class="combo-chart">
            <svg viewBox="0 0 520 210" preserveAspectRatio="none">
              <polyline class="trend-grid" points="0,175 520,175" />
              <polyline class="trend-grid" points="0,120 520,120" />
              <polyline class="trend-grid" points="0,65 520,65" />
              <rect v-for="bar in receiveBarList" :key="bar.key" class="chart-hover-bar" :x="bar.x" :y="bar.y" :width="bar.width" :height="bar.height" rx="4">
                <title>{{ bar.title }}</title>
              </rect>
              <polyline class="trend-line green" :points="receiveCountPoints" />
              <circle v-for="point in receiveCountPointList" :key="point.key" class="chart-hover-point" :cx="point.x" :cy="point.y" r="4">
                <title>{{ point.title }}</title>
              </circle>
            </svg>
            <div class="chart-labels"><span v-for="item in receiveTrendRows" :key="item.itemName">{{ item.itemName }}</span></div>
            <div class="chart-legend"><span><i class="blue" />回款金额</span><span><i class="green" />回款笔数</span></div>
          </div>
          <el-empty v-else :image-size="72" description="暂无回款趋势数据" />
        </article>

        <article class="finance-card finance-chart-card">
          <div class="finance-card-title">
            <div>
              <h3>应收账龄分布</h3>
              <p>未收款项按账龄区间分布</p>
            </div>
          </div>
          <div class="donut-wrap">
            <i class="donut" :style="agingDonutStyle"><b>{{ formatShortMoney(agingAmountTotal) }}</b><span>应收总额</span></i>
            <ul>
              <li v-for="item in agingStats" :key="item.itemName">
                <em :style="{ background: item.color }" />
                <span>{{ item.label }}</span>
                <strong>{{ formatMoney(item.amountValue) }}</strong>
                <small>{{ item.percent }}</small>
              </li>
            </ul>
          </div>
        </article>

        <article class="finance-card finance-chart-card">
          <div class="finance-card-title">
            <div>
              <h3>开票 / 费用趋势（万元）</h3>
              <p>已开票金额与案件费用支出趋势</p>
            </div>
            <el-button :size="controlSize" type="text" @click="switchMode('invoice')">更多</el-button>
          </div>
          <div v-if="invoiceExpenseRows.length" class="combo-chart">
            <svg viewBox="0 0 520 210" preserveAspectRatio="none">
              <polyline class="trend-grid" points="0,175 520,175" />
              <polyline class="trend-grid" points="0,120 520,120" />
              <polyline class="trend-grid" points="0,65 520,65" />
              <rect v-for="bar in invoiceBarList" :key="bar.key" class="chart-hover-bar" :x="bar.x" :y="bar.y" :width="bar.width" :height="bar.height" rx="4">
                <title>{{ bar.title }}</title>
              </rect>
              <polyline class="trend-line green" :points="expenseLinePoints" />
              <circle v-for="point in expensePointList" :key="point.key" class="chart-hover-point" :cx="point.x" :cy="point.y" r="4">
                <title>{{ point.title }}</title>
              </circle>
            </svg>
            <div class="chart-labels"><span v-for="item in invoiceExpenseRows" :key="item.itemName">{{ item.itemName }}</span></div>
            <div class="chart-legend"><span><i class="blue" />开票金额</span><span><i class="green" />费用支出</span></div>
          </div>
          <el-empty v-else :image-size="72" description="暂无开票/费用趋势数据" />
        </article>
      </section>

      <finance-overview-tables
        :size="controlSize"
        :pending-payments="pendingPayments"
        :overdue-receivables="overdueReceivables"
        :invoice-activities="invoiceActivities"
        :summary-rows="financeSummaryRows"
        :invoice-status-options="dict.type.law_contract_invoice_status"
        :compact-money="formatCompactMoney"
        :short-date="shortDate"
        :summary-value="formatSummaryValue"
        :summary-year-value="formatSummaryYearValue"
        @navigate="switchMode"
      />
      <section v-if="false" class="finance-overview-tables">
        <biz-table-card :toolbar="false" :pagination="false">
          <template slot="header">
            <div class="section-heading">
              <h3>待确认回款列表</h3>
              <el-button :size="controlSize" type="text" @click="switchMode('payment')">更多</el-button>
            </div>
          </template>
          <el-table :data="pendingPayments" :size="controlSize" class="overview-mini-table">
            <el-table-column label="客户名称" min-width="118" show-overflow-tooltip>
              <template slot-scope="{ row }"><span class="biz-link">{{ row.customerName || '-' }}</span></template>
            </el-table-column>
            <el-table-column label="金额" width="82" align="right"><template slot-scope="{ row }">{{ formatCompactMoney(row.receivableAmount) }}</template></el-table-column>
            <el-table-column label="到账日" width="76"><template slot-scope="{ row }">{{ shortDate(row.planReceiveDate) }}</template></el-table-column>
          </el-table>
        </biz-table-card>

        <biz-table-card :toolbar="false" :pagination="false">
          <template slot="header">
            <div class="section-heading">
              <h3>逾期应收客户</h3>
              <el-button :size="controlSize" type="text" @click="switchMode('receivable', { overdueOnly: '1' })">更多</el-button>
            </div>
          </template>
          <el-table :data="overdueReceivables" :size="controlSize" class="overview-mini-table">
            <el-table-column label="客户名称" min-width="118" show-overflow-tooltip>
              <template slot-scope="{ row }"><span class="biz-link">{{ row.customerName || '-' }}</span></template>
            </el-table-column>
            <el-table-column label="天数" width="62" align="center"><template slot-scope="{ row }">{{ row.agingDays || 0 }}天</template></el-table-column>
            <el-table-column label="金额" width="82" align="right"><template slot-scope="{ row }">{{ formatCompactMoney(row.pendingAmount) }}</template></el-table-column>
          </el-table>
        </biz-table-card>

        <biz-table-card :toolbar="false" :pagination="false">
          <template slot="header">
            <div class="section-heading">
              <h3>近期发票动态</h3>
              <el-button :size="controlSize" type="text" @click="switchMode('invoice')">更多</el-button>
            </div>
          </template>
          <el-table :data="invoiceActivities" :size="controlSize" class="overview-mini-table">
            <el-table-column label="客户名称" prop="customerName" min-width="122" show-overflow-tooltip />
            <el-table-column label="金额" width="82" align="right"><template slot-scope="{ row }">{{ formatCompactMoney(row.amount) }}</template></el-table-column>
            <el-table-column label="状态" width="68" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_invoice_status" :value="row.invoiceStatus" /></template></el-table-column>
          </el-table>
        </biz-table-card>

        <biz-table-card :toolbar="false" :pagination="false">
          <template slot="header">
            <div class="section-heading">
              <h3>费用 / 回款概览表</h3>
              <el-button :size="controlSize" type="text" @click="switchMode('report')">查看报表</el-button>
            </div>
          </template>
          <el-table :data="financeSummaryRows" :size="controlSize" class="overview-mini-table">
            <el-table-column label="项目" prop="itemName" min-width="76" />
            <el-table-column label="本月" width="76" align="right"><template slot-scope="{ row }">{{ formatSummaryValue(row) }}</template></el-table-column>
            <el-table-column label="本年" width="82" align="right"><template slot-scope="{ row }">{{ formatSummaryYearValue(row) }}</template></el-table-column>
          </el-table>
        </biz-table-card>
      </section>
    </template>

    <template v-else-if="false && mode === 'overview'">
      <section class="finance-overview-grid">
        <article class="finance-card trend-card">
          <div class="finance-card-title">
            <div>
              <h3>回款趋势</h3>
              <p>近 6 个月确认回款走势</p>
            </div>
            <strong>{{ formatMoney(totalTrendAmount) }}</strong>
          </div>
          <svg class="trend-chart" viewBox="0 0 520 180" preserveAspectRatio="none">
            <polyline class="trend-grid" points="0,150 520,150" />
            <polyline class="trend-grid" points="0,105 520,105" />
            <polyline class="trend-grid" points="0,60 520,60" />
            <polyline class="trend-line" :points="trendPoints" />
            <circle v-for="(point, index) in trendPointList" :key="index" :cx="point.x" :cy="point.y" r="4" />
          </svg>
          <div class="trend-labels">
            <span v-for="item in trend" :key="item.itemName">{{ item.itemName }}</span>
          </div>
        </article>

        <article class="finance-card aging-card">
          <div class="finance-card-title">
            <div>
              <h3>账龄分布</h3>
              <p>未收款计划按逾期天数分布</p>
            </div>
          </div>
          <div class="donut-wrap">
            <i class="donut" :style="agingDonutStyle"><b>{{ agingTotal }}</b><span>笔应收</span></i>
            <ul>
              <li v-for="item in agingStats" :key="item.itemName">
                <em :style="{ background: item.color }" />
                <span>{{ item.label }}</span>
                <strong>{{ item.itemValue || 0 }}</strong>
              </li>
            </ul>
          </div>
        </article>

        <article class="finance-card link-card">
          <div class="finance-card-title">
            <div>
              <h3>模块联动概览</h3>
              <p>合同、案件、费用的财务关系</p>
            </div>
          </div>
          <div class="link-items">
            <div v-for="item in linkCards" :key="item.key">
              <i :class="item.icon" />
              <span>{{ item.title }}</span>
              <strong>{{ item.value }}</strong>
              <small>{{ item.desc }}</small>
            </div>
          </div>
        </article>

        <article class="finance-card funnel-card">
          <div class="finance-card-title">
            <div>
              <h3>线索转化漏斗</h3>
              <p>从线索预计金额到客户签约金额</p>
            </div>
            <strong>{{ leadConversionRate }}%</strong>
          </div>
          <div class="funnel-list">
            <div v-for="item in leadFunnelCards" :key="item.key">
              <span>{{ item.title }}</span>
              <i><em :style="{ width: item.width }" /></i>
              <strong>{{ item.value }}</strong>
              <small>{{ item.desc }}</small>
            </div>
          </div>
        </article>

        <article class="finance-card reminder-card">
          <div class="finance-card-title">
            <div>
              <h3>财务提醒</h3>
              <p>优先处理影响现金流的事项</p>
            </div>
          </div>
          <div class="reminder-list">
            <button v-for="item in reminderCards" :key="item.key" @click="switchMode(item.mode, item.query)">
              <i :class="item.icon" />
              <span><b>{{ item.title }}</b><small>{{ item.desc }}</small></span>
              <strong>{{ item.value }}</strong>
              <em class="el-icon-arrow-right" />
            </button>
          </div>
        </article>
      </section>

      <section class="finance-tables-grid">
        <biz-table-card :toolbar="false" :pagination="false">
          <template slot="header">
            <div class="section-heading">
              <h3>待确认回款</h3>
              <el-button :size="controlSize" type="text" @click="switchMode('payment')">查看全部</el-button>
            </div>
          </template>
          <el-table :data="pendingPayments" :size="controlSize">
            <el-table-column label="客户/合同" min-width="190">
              <template slot-scope="{ row }">
                <span class="biz-link">{{ row.customerName || '-' }}</span>
                <small class="sub-text">{{ row.contractNo || '-' }}</small>
              </template>
            </el-table-column>
            <el-table-column label="应收金额" width="120" align="right"><template slot-scope="{ row }">{{ formatMoney(row.receivableAmount) }}</template></el-table-column>
            <el-table-column label="计划日期" prop="planReceiveDate" width="120" />
            <el-table-column label="操作" width="100" align="center" fixed="right"><template slot-scope="{ row }"><el-button v-hasPermi="['finance:payment:confirm']" :size="controlSize" type="text" @click="openPayment(row)">确认</el-button></template></el-table-column>
          </el-table>
        </biz-table-card>

        <biz-table-card :toolbar="false" :pagination="false">
          <template slot="header">
            <div class="section-heading">
              <h3>即将到期应收</h3>
              <el-button :size="controlSize" type="text" @click="switchMode('receivable')">查看全部</el-button>
            </div>
          </template>
          <el-table :data="dueReceivables" :size="controlSize">
            <el-table-column label="客户/案件" min-width="190">
              <template slot-scope="{ row }">
                <span class="biz-link">{{ row.customerName || '-' }}</span>
                <small class="sub-text">{{ row.caseNo || row.contractNo || '-' }}</small>
              </template>
            </el-table-column>
            <el-table-column label="待收金额" width="120" align="right"><template slot-scope="{ row }">{{ formatMoney(row.pendingAmount) }}</template></el-table-column>
            <el-table-column label="账龄" width="90" align="center"><template slot-scope="{ row }">{{ row.agingDays || 0 }} 天</template></el-table-column>
            <el-table-column label="状态" width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_finance_receivable_status" :value="row.receivableStatus" /></template></el-table-column>
          </el-table>
        </biz-table-card>
      </section>
    </template>

    <biz-table-card
      v-else-if="tableModes.includes(mode)"
      :show-search.sync="showSearch"
      :total="total"
      :page.sync="query.pageNum"
      :limit.sync="query.pageSize"
      @query="search"
      @pagination="loadPage"
    >
      <template slot="filters">
        <div class="biz-filter-main">
          <el-input v-model="query.keyword" :size="controlSize" prefix-icon="el-icon-search" :placeholder="searchPlaceholder" clearable @clear="search" @keyup.enter.native="search" />
          <el-select v-if="mode === 'receivable'" v-model="query.ageBucket" :size="controlSize" placeholder="账龄区间" clearable @change="search">
            <el-option v-for="item in dict.type.law_finance_age_bucket" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-if="mode === 'receivable' || mode === 'payment'" v-model="query.confirmStatus" :size="controlSize" placeholder="回款状态" clearable @change="search">
            <el-option v-for="item in dict.type.law_contract_receive_status" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-if="mode === 'receivable' || mode === 'invoice'" v-model="query.invoiceStatus" :size="controlSize" placeholder="开票状态" clearable @change="search">
            <el-option v-for="item in dict.type.law_contract_invoice_status" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-if="mode === 'expense'" v-model="query.expenseType" :size="controlSize" placeholder="费用类型" clearable @change="search">
            <el-option v-for="item in dict.type.law_case_expense_type" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-if="mode === 'expense'" v-model="query.payStatus" :size="controlSize" placeholder="付款状态" clearable @change="search">
            <el-option v-for="item in dict.type.law_case_pay_status" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-if="mode === 'expense'" v-model="query.reimburseStatus" :size="controlSize" placeholder="报销状态" clearable @change="search">
            <el-option v-for="item in dict.type.law_case_reimburse_status" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-if="mode === 'expense'" v-model="query.voucherStatus" :size="controlSize" placeholder="凭证状态" clearable @change="search">
            <el-option v-for="item in dict.type.law_case_voucher_status" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-date-picker
            v-model="listDateRange"
            :size="controlSize"
            type="daterange"
            value-format="yyyy-MM-dd"
            range-separator="至"
            :start-placeholder="listDatePlaceholder[0]"
            :end-placeholder="listDatePlaceholder[1]"
            clearable
            @change="handleListDateChange"
          />
        </div>
        <div class="biz-filter-actions">
          <el-button :size="controlSize" plain icon="el-icon-refresh" @click="reset">重置</el-button>
        </div>
      </template>

      <finance-data-tables
        :mode="mode"
        :loading="loading"
        :list="list"
        :control-size="controlSize"
        :dict-options="dict.type"
        :format-money="formatMoney"
        :field="field"
        :can-collect="canCollect"
        :can-invoice="canInvoice"
        :open-case-finance="openCaseFinance"
        :open-payment="openPayment"
        :open-reject="openReject"
        :open-invoice="openInvoice"
        :open-expense="openExpense"
      />
    </biz-table-card>

    <finance-report-panel
      v-else
      :report-date-range.sync="reportDateRange"
      :control-size="controlSize"
      :report="report"
      :report-cards="reportCards"
      :report-range-text="reportRangeText"
      :report-trend-points="reportTrendPoints"
      :report-trend-point-list="reportTrendPointList"
      :lead-source-conversion="leadSourceConversion"
      :handle-report-date-change="handleReportDateChange"
      :reset-report-range="resetReportRange"
      :format-money="formatMoney"
      :bar-width="barWidth"
      :dict-label="dictLabel"
      :conversion-bar-width="conversionBarWidth"
    />

    <finance-action-dialogs
      ref="actionDialogs"
      :payment-visible.sync="paymentOpen"
      :reject-visible.sync="rejectOpen"
      :invoice-visible.sync="invoiceOpen"
      :expense-visible.sync="expenseOpen"
      :payment-form="paymentForm"
      :reject-form="rejectForm"
      :invoice-form="invoiceForm"
      :expense-form="expenseForm"
      :payment-rules="paymentRules"
      :reject-rules="rejectRules"
      :invoice-rules="invoiceRules"
      :expense-rules="expenseRules"
      :options="dict.type"
      :control-size="controlSize"
      :dialog-class="dialogClass"
      :format-money="formatMoney"
      @submit-payment="submitPayment"
      @submit-reject="submitReject"
      @submit-invoice="submitInvoice"
      @submit-expense="submitExpense"
      @sync-expense-file="syncExpenseFile"
    />

    <case-finance-drawer
      ref="caseFinanceDrawer"
      :visible.sync="caseFinanceOpen"
      :case-id="caseFinanceCaseId"
      :control-size="controlSize"
      :size-class="'biz-size-' + appSize"
      :dict-options="dict.type"
    />
  </div>
</template>

<script>
import BizPageHeader from '@/views/business/components/BizPageHeader'
import BizHero from '@/views/business/components/BizHero'
import BizMetrics from '@/views/business/components/BizMetrics'
import BizTableCard from '@/views/business/components/BizTableCard'
import CaseFinanceDrawer from './components/CaseFinanceDrawer'
import FinanceFlowOverview from './components/FinanceFlowOverview'
import FinanceOverviewTables from './components/FinanceOverviewTables'
import FinanceActionDialogs from './components/FinanceActionDialogs'
import FinanceDataTables from './components/FinanceDataTables'
import FinanceReportPanel from './components/FinanceReportPanel'
import businessUi from '@/views/business/mixins/businessUi'
import financePageModel from './finance-page-model'
import '@/views/business/business.scss'
import '@/views/business/business-dialog.scss'

export default {
  name: 'FinanceCenter',
  components: { BizPageHeader, BizHero, BizMetrics, BizTableCard, CaseFinanceDrawer, FinanceFlowOverview, FinanceOverviewTables, FinanceActionDialogs, FinanceDataTables, FinanceReportPanel },
  mixins: [businessUi, financePageModel],
  dicts: [
    'law_finance_receivable_status',
    'law_finance_age_bucket',
    'law_finance_payment_method',
    'law_finance_invoice_type',
    'law_contract_receive_status',
    'law_contract_invoice_status',
    'law_contract_risk_level',
    'law_case_type',
    'law_case_expense_type',
    'law_case_pay_status',
    'law_case_reimburse_status',
    'law_case_voucher_status'
  ],
  data() {
    return {
      mode: this.$route.query.module || 'overview',
      businessPageMeta: {
        defaultTitle: '财务总览',
        titles: {
          overview: '财务总览',
          receivable: '应收管理',
          payment: '回款确认',
          invoice: '发票管理',
          expense: '费用报销',
          report: '财务报表'
        },
        descriptions: {
          overview: '联动合同、案件与回款，构建闭环财务管理',
          receivable: '统一管理合同收费计划、账龄风险与待收金额',
          payment: '处理回款确认、驳回和异常款项，保障现金流准确入账',
          invoice: '跟踪已回款计划的开票状态，支持部分开票和补齐开票',
          expense: '归集律师办案费用，联动案件与合同成本控制',
          report: '以图表呈现收入、欠费、账龄、律师创收和案件成本'
        }
      },
      loading: false,
      showSearch: true,
      total: 0,
      list: [],
      dashboard: {},
      report: {},
      trend: [],
      aging: [],
      links: [],
      reminders: [],
      flow: [],
      receiveTrend: [],
      invoiceExpenseTrend: [],
      invoiceActivities: [],
      financeSummaryRows: [],
      leadFunnel: [],
      leadSourceConversion: [],
      pendingPayments: [],
      dueReceivables: [],
      query: { pageNum: 1, pageSize: 10 },
      reportDateRange: [],
      listDateRange: [],
      paymentOpen: false,
      rejectOpen: false,
      invoiceOpen: false,
      expenseOpen: false,
      caseFinanceOpen: false,
      caseFinanceCaseId: null,
      paymentForm: {},
      rejectForm: {},
      invoiceForm: {},
      expenseForm: {},
      paymentRules: {
        receivedAmount: [{ required: true, message: '请输入本次回款金额', trigger: 'blur' }],
        paymentMethod: [{ required: true, message: '请选择付款方式', trigger: 'change' }]
      },
      rejectRules: { reason: [{ required: true, message: '请填写驳回原因', trigger: 'blur' }] },
      invoiceRules: {
        invoiceStatus: [{ required: true, message: '请选择开票动作', trigger: 'change' }],
        invoiceType: [{ required: true, message: '请选择发票类型', trigger: 'change' }]
      },
      expenseRules: {
        expenseType: [{ required: true, message: '请选择费用类型', trigger: 'change' }],
        amount: [{ required: true, message: '请输入费用金额', trigger: 'blur' }],
        occurDate: [{ required: true, message: '请选择发生日期', trigger: 'change' }],
        payStatus: [{ required: true, message: '请选择付款状态', trigger: 'change' }],
        reimburseStatus: [{ required: true, message: '请选择报销状态', trigger: 'change' }],
        voucherStatus: [{ required: true, message: '请选择凭证状态', trigger: 'change' }]
      }
    }
  },
  watch: {
    '$route.fullPath'() {
      this.mode = this.$route.query.module || 'overview'
      this.resetQuery()
      this.load()
    }
  },
  created() {
    this.load()
  }
}
</script>

<style scoped lang="scss" src="./finance-page.scss"></style>
