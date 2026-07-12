import {
  getFinanceDashboard,
  listReceivable,
  listPayment,
  listInvoice,
  listFinanceExpense,
  getFinanceReport,
  confirmPayment,
  rejectPayment,
  handleInvoice,
  updateFinanceExpense
} from '@/api/finance'

export default {
  computed: {
    tableModes() {
      return ['receivable', 'payment', 'invoice', 'expense']
    },
    heroMeta() {
      const metas = {
        overview: ['FINANCE CONTROL', '贯穿合同、案件与回款的财务驾驶舱', '围绕应收、回款、开票、费用与报表形成闭环管控。'],
        receivable: ['ACCOUNTS RECEIVABLE', '掌控应收计划与账龄风险', '从合同收费计划沉淀应收数据，及时发现逾期与高风险款项。'],
        payment: ['PAYMENT CONFIRM', '让每一笔回款都有据可循', '财务确认、驳回和开票动作复用合同收费计划状态机。'],
        invoice: ['INVOICE MANAGEMENT', '回款与开票状态同步推进', '已确认回款后进入开票处理，支持部分开票与补齐开票。'],
        expense: ['CASE EXPENSE', '案件费用与合同收益联动管控', '汇总律师办案费用、凭证、付款与报销状态。'],
        report: ['FINANCE REPORTS', '用图表看清收入、成本与欠费', '提供回款趋势、账龄、律师创收和案件成本分析。']
      }
      const item = metas[this.mode] || metas.overview
      return { eyebrow: item[0], title: item[1], description: item[2] }
    },
    metrics() {
      if (this.mode === 'receivable') {
        return [
          { metricKey: 'receivableTotal', metricValue: this.formatMoney(this.dashboardValue('receivableTotal')) },
          { metricKey: 'pendingTotal', metricValue: this.formatMoney(this.dashboardValue('pendingTotal')) },
          { metricKey: 'overdueTotal', metricValue: this.formatMoney(this.dashboardValue('overdueTotal')) },
          { metricKey: 'receivedTotal', metricValue: this.formatMoney(this.dashboardValue('receivedTotal')) },
          { metricKey: 'pendingCount', metricValue: this.listCount(row => row.confirmStatus === '0') },
          { metricKey: 'overdueCount', metricValue: this.listCount(row => row.receivableStatus === 'overdue') }
        ]
      }
      if (this.mode === 'payment') {
        return [
          { metricKey: 'pendingPaymentAmount', metricValue: this.formatMoney(this.dashboardValue('pendingPaymentAmount')) },
          { metricKey: 'pendingPaymentCount', metricValue: this.listCount(row => row.confirmStatus === '0') },
          { metricKey: 'confirmedAmount', metricValue: this.formatMoney(this.listSum(row => row.confirmStatus === '1' ? row.receivedAmount || row.receivableAmount : 0)) },
          { metricKey: 'rejectedCount', metricValue: this.listCount(row => row.confirmStatus === '2') },
          { metricKey: 'partialCount', metricValue: this.listCount(row => row.receivableStatus === 'partial') },
          { metricKey: 'invoiceReadyAmount', metricValue: this.formatMoney(this.dashboardValue('pendingInvoiceTotal')) }
        ]
      }
      if (this.mode === 'invoice') {
        const reminderMap = this.keyed(this.reminders || [])
        return [
          { metricKey: 'pendingInvoiceTotal', metricValue: this.formatMoney(this.dashboardValue('pendingInvoiceTotal')) },
          { metricKey: 'invoicedTotal', metricValue: this.formatMoney(this.dashboardValue('invoicedTotal')) },
          { metricKey: 'pendingInvoiceCount', metricValue: this.num(reminderMap.pendingInvoice) },
          { metricKey: 'partialInvoiceCount', metricValue: this.listCount(row => row.invoiceStatus === '2') },
          { metricKey: 'invoicedCount', metricValue: this.listCount(row => row.invoiceStatus === '1') },
          { metricKey: 'unInvoiceCount', metricValue: this.listCount(row => row.invoiceStatus === '0') }
        ]
      }
      if (this.mode === 'expense') {
        return [
          { metricKey: 'monthExpense', metricValue: this.formatMoney(this.dashboardValue('monthExpense')) },
          { metricKey: 'pageExpenseAmount', metricValue: this.formatMoney(this.listSum(row => this.field(row, 'amount', 'amount'))) },
          { metricKey: 'unpaidCount', metricValue: this.listCount(row => this.field(row, 'payStatus', 'pay_status') !== 'paid') },
          { metricKey: 'paidAmount', metricValue: this.formatMoney(this.listSum(row => this.field(row, 'payStatus', 'pay_status') === 'paid' ? this.field(row, 'amount', 'amount') : 0)) },
          { metricKey: 'unReimburseCount', metricValue: this.listCount(row => !['reimbursed', 'done', 'paid'].includes(this.field(row, 'reimburseStatus', 'reimburse_status'))) },
          { metricKey: 'voucherMissingCount', metricValue: this.listCount(row => this.field(row, 'voucherStatus', 'voucher_status') !== 'uploaded') }
        ]
      }
      const map = this.keyed(this.dashboard.cards || [])
      const keys = ['monthReceived', 'receivableTotal', 'invoicedTotal', 'monthExpense', 'pendingPaymentAmount', 'overdueTotal']
      return keys.map(key => ({ metricKey: key, metricValue: this.formatMoney(map[key] ? map[key].metricValue : 0) }))
    },
    metricConfig() {
      if (this.mode === 'receivable') {
        return [
          { key: 'receivableTotal', label: '应收总额', icon: 'money', color: 'blue', hint: '合同收费计划累计' },
          { key: 'pendingTotal', label: '待收金额', icon: 'time', color: 'orange', hint: '未完成回款金额' },
          { key: 'overdueTotal', label: '逾期应收', icon: 'time-range', color: 'orange', hint: '超过计划回款日' },
          { key: 'receivedTotal', label: '已收金额', icon: 'money', color: 'green', hint: '已确认入账' },
          { key: 'pendingCount', label: '待确认计划', icon: 'documentation', color: 'cyan', hint: '当前筛选待确认' },
          { key: 'overdueCount', label: '逾期笔数', icon: 'chart', color: 'violet', hint: '当前筛选逾期项' }
        ]
      }
      if (this.mode === 'payment') {
        return [
          { key: 'pendingPaymentAmount', label: '待确认回款', icon: 'time', color: 'orange', hint: '待财务确认入账' },
          { key: 'pendingPaymentCount', label: '待处理笔数', icon: 'documentation', color: 'cyan', hint: '当前筛选待确认' },
          { key: 'confirmedAmount', label: '已确认金额', icon: 'money', color: 'green', hint: '当前筛选已入账' },
          { key: 'rejectedCount', label: '驳回笔数', icon: 'time-range', color: 'orange', hint: '当前筛选异常回款' },
          { key: 'partialCount', label: '部分回款', icon: 'chart', color: 'violet', hint: '仍需补齐回款' },
          { key: 'invoiceReadyAmount', label: '可开票金额', icon: 'documentation', color: 'blue', hint: '已收未完全开票' }
        ]
      }
      if (this.mode === 'invoice') {
        return [
          { key: 'pendingInvoiceTotal', label: '待开票金额', icon: 'time', color: 'orange', hint: '已收未完全开票' },
          { key: 'invoicedTotal', label: '已开票金额', icon: 'documentation', color: 'green', hint: '已完成开票' },
          { key: 'pendingInvoiceCount', label: '待开票笔数', icon: 'date', color: 'blue', hint: '全局待处理数量' },
          { key: 'partialInvoiceCount', label: '部分开票', icon: 'chart', color: 'violet', hint: '当前筛选需补齐' },
          { key: 'invoicedCount', label: '已开票笔数', icon: 'documentation', color: 'cyan', hint: '当前筛选已完成' },
          { key: 'unInvoiceCount', label: '未开票笔数', icon: 'time-range', color: 'orange', hint: '当前筛选未处理' }
        ]
      }
      if (this.mode === 'expense') {
        return [
          { key: 'monthExpense', label: '本月费用支出', icon: 'money', color: 'orange', hint: '案件费用本月发生' },
          { key: 'pageExpenseAmount', label: '筛选费用合计', icon: 'chart', color: 'blue', hint: '当前筛选页金额' },
          { key: 'unpaidCount', label: '待付款费用', icon: 'time', color: 'orange', hint: '付款状态未完成' },
          { key: 'paidAmount', label: '已付款金额', icon: 'money', color: 'green', hint: '当前筛选已付款' },
          { key: 'unReimburseCount', label: '待报销费用', icon: 'documentation', color: 'violet', hint: '报销状态未完成' },
          { key: 'voucherMissingCount', label: '凭证缺失', icon: 'date-range', color: 'cyan', hint: '需补充费用凭证' }
        ]
      }
      return [
        { key: 'monthReceived', label: '本月回款总额', icon: 'money', color: 'blue', hint: '较上月动态统计' },
        { key: 'receivableTotal', label: '应收总额', icon: 'money', color: 'violet', hint: '合同收费计划累计' },
        { key: 'invoicedTotal', label: '已开票金额', icon: 'documentation', color: 'green', hint: '已确认开票金额' },
        { key: 'monthExpense', label: '本月费用支出', icon: 'money', color: 'orange', hint: '案件费用本月发生' },
        { key: 'pendingPaymentAmount', label: '待确认回款', icon: 'time', color: 'cyan', hint: '待财务确认入账' },
        { key: 'overdueTotal', label: '逾期应收金额', icon: 'time', color: 'orange', hint: '超过计划回款日' }
      ]
    },
    searchPlaceholder() {
      return this.mode === 'expense' ? '搜索费用编号、案件、客户' : '搜索合同编号、案件编号、客户名称'
    },
    agingStats() {
      const labels = { current: '未逾期', d30: '1-30天', d60: '31-60天', d90: '61-90天', d90plus: '90天以上' }
      const colors = ['#2563eb', '#06b6d4', '#22c55e', '#f97316', '#ef4444']
      return ['current', 'd30', 'd60', 'd90', 'd90plus'].map((key, index) => {
        const row = (this.aging || []).find(item => item.itemName === key) || {}
        return { itemName: key, label: labels[key], itemValue: Number(row.itemValue || 0), color: colors[index] }
      })
    },
    agingTotal() {
      return this.agingStats.reduce((sum, item) => sum + item.itemValue, 0)
    },
    agingDonutStyle() {
      if (!this.agingTotal) return { background: '#eef2ff' }
      let start = 0
      const parts = this.agingStats.map(item => {
        const deg = item.itemValue / this.agingTotal * 360
        const text = `${item.color} ${start}deg ${start + deg}deg`
        start += deg
        return text
      })
      return { background: `conic-gradient(${parts.join(',')})` }
    },
    agingStats() {
      const labels = { current: '0-30天', d30: '31-60天', d60: '61-90天', d90: '90天以上', d90plus: '严重逾期' }
      const colors = ['#2563eb', '#06b6d4', '#22c55e', '#f97316', '#ef4444']
      const rows = ['current', 'd30', 'd60', 'd90', 'd90plus'].map((key, index) => {
        const row = (this.aging || []).find(item => item.itemName === key) || {}
        return { itemName: key, label: labels[key], itemValue: Number(row.itemValue || 0), amountValue: Number(row.amountValue || 0), color: colors[index] }
      })
      const total = rows.reduce((sum, item) => sum + item.amountValue, 0)
      return rows.map(item => ({ ...item, percent: total ? (item.amountValue * 100 / total).toFixed(1) + '%' : '0%' }))
    },
    agingAmountTotal() {
      return this.agingStats.reduce((sum, item) => sum + item.amountValue, 0)
    },
    agingDonutStyle() {
      if (!this.agingAmountTotal) return { background: '#eef2ff' }
      let start = 0
      const parts = this.agingStats.map(item => {
        const deg = item.amountValue / this.agingAmountTotal * 360
        const text = `${item.color} ${start}deg ${start + deg}deg`
        start += deg
        return text
      })
      return { background: `conic-gradient(${parts.join(',')})` }
    },
    totalTrendAmount() {
      return (this.trend || []).reduce((sum, item) => sum + Number(item.itemValue || 0), 0)
    },
    trendPointList() {
      return this.buildTrendPoints(this.trend, 520)
    },
    trendPoints() {
      return this.trendPointList.map(item => `${item.x},${item.y}`).join(' ')
    },
    reportTrendPointList() {
      return this.buildTrendPoints(this.report.trend || [], 700)
    },
    reportTrendPoints() {
      return this.reportTrendPointList.map(item => `${item.x},${item.y}`).join(' ')
    },
    linkCards() {
      const map = this.keyed(this.links || [])
      return [
        { key: 'contracts', title: '关联合同', value: map.contracts ? map.contracts.metricValue : 0, desc: this.formatMoney(map.contracts && map.contracts.amountValue), icon: 'el-icon-document' },
        { key: 'matters', title: '关联案件', value: map.matters ? map.matters.metricValue : 0, desc: this.formatMoney(map.matters && map.matters.amountValue), icon: 'el-icon-suitcase' },
        { key: 'expenses', title: '案件费用', value: map.expenses ? map.expenses.metricValue : 0, desc: this.formatMoney(map.expenses && map.expenses.amountValue), icon: 'el-icon-money' }
      ]
    },
    leadFunnelMap() {
      return this.keyed(this.leadFunnel || [])
    },
    leadConversionRate() {
      const total = Number(this.leadFunnelMap.leadTotal && this.leadFunnelMap.leadTotal.metricValue || 0)
      const converted = Number(this.leadFunnelMap.convertedLeads && this.leadFunnelMap.convertedLeads.metricValue || 0)
      return total ? (converted * 100 / total).toFixed(1) : '0.0'
    },
    leadFunnelCards() {
      const map = this.leadFunnelMap
      const total = Math.max(Number(map.leadTotal && map.leadTotal.metricValue || 0), 1)
      const signedAmount = map.signedContracts && map.signedContracts.amountValue
      return [
        { key: 'leadTotal', title: '线索总量', value: Number(map.leadTotal && map.leadTotal.metricValue || 0), desc: this.formatMoney(map.leadTotal && map.leadTotal.amountValue), width: '100%' },
        { key: 'convertedLeads', title: '已转化线索', value: Number(map.convertedLeads && map.convertedLeads.metricValue || 0), desc: this.formatMoney(map.convertedLeads && map.convertedLeads.amountValue), width: Math.max(8, Number(map.convertedLeads && map.convertedLeads.metricValue || 0) / total * 100) + '%' },
        { key: 'linkedCustomers', title: '生成客户', value: Number(map.linkedCustomers && map.linkedCustomers.metricValue || 0), desc: '客户去重后关联', width: Math.max(8, Number(map.linkedCustomers && map.linkedCustomers.metricValue || 0) / total * 100) + '%' },
        { key: 'signedContracts', title: '签约合同', value: Number(map.signedContracts && map.signedContracts.metricValue || 0), desc: this.formatMoney(signedAmount), width: Math.max(8, Number(map.signedContracts && map.signedContracts.metricValue || 0) / total * 100) + '%' }
      ]
    },
    reminderCards() {
      const map = this.keyed(this.reminders || [])
      return [
        { key: 'overdueReceivable', title: '逾期应收', desc: '超过计划回款日的款项', value: this.num(map.overdueReceivable), mode: 'receivable', query: { overdueOnly: '1' }, icon: 'el-icon-warning-outline' },
        { key: 'pendingInvoice', title: '待开��_m�G����ƭy�', color: 'green' },
        { key: 'invoicedAmount', title: '已开票', value: value('invoicedAmount'), icon: 'el-icon-document-checked', color: 'cyan' },
        { key: 'expenseAmount', title: '费用支出', value: value('expenseAmount'), icon: 'el-icon-suitcase', color: 'orange' },
        { key: 'netIncome', title: '净收入', value: value('netIncome'), icon: 'el-icon-pie-chart', color: 'blue' }
      ]
      return rows.map((item, index) => {
        const next = rows[index + 1]
        const rate = next && item.value ? (next.value * 100 / item.value).toFixed(1) + '%' : '--'
        return { ...item, rate }
      })
    },
    receiveTrendRows() {
      return this.completeMonthlyRows(this.receiveTrend && this.receiveTrend.length ? this.receiveTrend : this.trend, {
        itemValue: 0,
        itemCount: 0
      })
    },
    receiveBarList() {
      return this.buildBars(this.receiveTrendRows, 'itemValue')
    },
    receiveCountPointList() {
      return this.buildLinePoints(this.receiveTrendRows, 'itemCount')
    },
    receiveCountPoints() {
      return this.receiveCountPointList.map(item => `${item.x},${item.y}`).join(' ')
    },
    invoiceExpenseRows() {
      return this.completeMonthlyRows(this.invoiceExpenseTrend || [], {
        invoiceAmount: 0,
        expenseAmount: 0
      })
    },
    invoiceBarList() {
      return this.buildBars(this.invoiceExpenseRows, 'invoiceAmount')
    },
    expensePointList() {
      return this.buildLinePoints(this.invoiceExpenseRows, 'expenseAmount')
    },
    expenseLinePoints() {
      return this.expensePointList.map(item => `${item.x},${item.y}`).join(' ')
    },
    overdueReceivables() {
      return (this.dueReceivables || []).filter(item => Number(item.agingDays || 0) > 0 || item.receivableStatus === 'overdue').slice(0, 5)
    },
    reportCards() {
      const map = this.keyed(this.report.cards || [], 'metricValue')
      return [
        { key: 'income', title: '收入明细', value: map.income || 0, desc: '已确认回款' },
        { key: 'cost', title: '案件成本', value: map.cost || 0, desc: '办案费用合计' },
        { key: 'arrears', title: '欠费提醒', value: map.arrears || 0, desc: '未收款余额' },
        { key: 'invoicePending', title: '待开票', value: map.invoicePending || 0, desc: '已收未开票' }
      ]
    },
    reportRangeText() {
      return this.query.beginDate && this.query.endDate ? `${this.query.beginDate} 至 ${this.query.endDate} ` : '近 6 个月'
    },
    listDatePlaceholder() {
      if (this.mode === 'expense') return ['费用开始日期', '费用结束日期']
      if (this.mode === 'payment' || this.mode === 'invoice') return ['处理开始日期', '处理结束日期']
      return ['计划开始日期', '计划结束日期']
    },
  },
  methods: {
    switchMode(mode, extraQuery = {}) {
      this.$router.push({ path: '/finance/' + mode, query: { module: mode, ...extraQuery } }).catch(() => {})
    },
    load() {
      if (this.mode === 'overview') return this.loadDashboard()
      if (!this.dashboard.cards) this.loadDashboard(false)
      if (this.mode === 'report') return this.loadReport()
      return this.loadPage()
    },
    loadDashboard(useLoading = true) {
      if (useLoading) this.loading = true
      return getFinanceDashboard().then(res => {
        const data = res.data || {}
        this.dashboard = data
        this.trend = data.trend || []
        this.aging = data.aging || []
        this.links = data.links || []
        this.reminders = data.reminders || []
        this.flow = data.flow || []
        this.receiveTrend = data.receiveTrend || data.trend || []
        this.invoiceExpenseTrend = data.invoiceExpenseTrend || []
        this.invoiceActivities = (data.invoiceActivities || []).slice(0, 5)
        this.financeSummaryRows = data.financeSummaryRows || []
        this.leadFunnel = data.leadFunnel || []
        this.leadSourceConversion = data.leadSourceConversion || []
        this.pendingPayments = (data.pendingPayments || []).slice(0, 5)
        this.dueReceivables = (data.dueReceivables || []).slice(0, 5)
      }).finally(() => {
        if (useLoading) this.loading = false
      })
    },
    loadReport() {
      this.loading = true
      getFinanceReport(this.query).then(res => {
        const data = res.data || {}
        this.report = data
        this.leadFunnel = data.leadFunnel || []
        this.leadSourceConversion = data.leadSourceConversion || []
      }).finally(() => { this.loading = false })
    },
    loadPage() {
      this.loading = true
      const api = {
        receivable: listReceivable,
        payment: listPayment,
        invoice: listInvoice,
        expense: listFinanceExpense
      }[this.mode]
      api(this.query).then(res => {
        this.list = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    search() {
      this.query.pageNum = 1
      this.load()
    },
    reset() {
      const routeQuery = { ...(this.$route.query || {}) }
      delete routeQuery.module
      if (Object.keys(routeQuery).length) {
        this.$router.push({ path: '/finance/' + this.mode, query: { module: this.mode } }).catch(() => {})
        return
      }
      this.resetQuery(false)
      this.load()
    },
    resetQuery(useRouteQuery = true) {
      const routeQuery = useRouteQuery ? { ...(this.$route.query || {}) } : {}
      delete routeQuery.module
      this.query = { pageNum: 1, pageSize: 10, ...routeQuery }
      this.listDateRange = this.query.beginDate && this.query.endDate ? [this.query.beginDate, this.query.endDate] : []
      this.reportDateRange = []
      this.total = 0
      this.list = []
    },
    handleListDateChange(value) {
      this.query.beginDate = value && value.length ? value[0] : undefined
      this.query.endDate = value && value.length ? value[1] : undefined
      this.search()
    },
    handleReportDateChange(value) {
      this.query.beginDate = value && value.length ? value[0] : undefined
      this.query.endDate = value && value.length ? value[1] : undefined
      this.loadReport()
    },
    resetReportRange() {
      this.reportDateRange = []
      this.query.beginDate = undefined
      this.query.endDate = undefined
      this.loadReport()
    },
    openPayment(row) {
      this.paymentForm = {
        ...row,
        receivedAmount: Number(row.pendingAmount || row.receivableAmount || 0),
        paymentMethod: row.paymentMethod || this.dictDefault('law_finance_payment_method')
      }
      this.paymentOpen = true
    },
    submitPayment() {
      this.$refs.actionDialogs.validate('payment', valid => {
        if (!valid) return
        if (Number(this.paymentForm.receivedAmount) <= 0) {
          this.$modal.msgError('本次回款金额必须大于 0')
          return
        }
        confirmPayment({ planId: this.paymentForm.planId, receivedAmount: this.paymentForm.receivedAmount, paymentMethod: this.paymentForm.paymentMethod, reason: this.paymentForm.reason }).then(() => {
          this.$modal.msgSuccess('回款已确认')
          this.paymentOpen = false
          this.load()
          this.refreshCaseFinance()
        })
      })
    },
    openReject(row) {
      this.rejectForm = { ...row, reason: '' }
      this.rejectOpen = true
    },
    submitReject() {
      this.$refs.actionDialogs.validate('reject', valid => {
        if (!valid) return
        rejectPayment({ planId: this.rejectForm.planId, reason: this.rejectForm.reason }).then(() => {
          this.$modal.msgSuccess('已驳回回款')
          this.rejectOpen = false
          this.load()
        })
      })
    },
    openInvoice(row) {
      this.invoiceForm = {
        ...row,
        currentInvoiceStatus: row.invoiceStatus,
        invoiceStatus: row.invoiceStatus === '2' ? '1' : '2',
        invoiceType: row.invoiceType || this.dictDefault('law_finance_invoice_type'),
        reason: ''
      }
      this.invoiceOpen = true
    },
    submitInvoice() {
      this.$refs.actionDialogs.validate('invoice', valid => {
        if (!valid) return
        handleInvoice({ planId: this.invoiceForm.planId, invoiceStatus: this.invoiceForm.invoiceStatus, invoiceType: this.invoiceForm.invoiceType, reason: this.invoiceForm.reason }).then(() => {
          this.$modal.msgSuccess('开票状态已更新')
          this.invoiceOpen = false
          this.load()
          this.refreshCaseFinance()
        })
      })
    },
    openCaseFinance(row) {
      const caseId = row.caseId || row.case_id
      if (!caseId) {
        this.$modal.msgError('当前费用未关联案件')
        return
      }
      this.caseFinanceCaseId = caseId
      this.caseFinanceOpen = true
    },
    refreshCaseFinance() {
      if (this.caseFinanceOpen && this.$refs.caseFinanceDrawer) {
        this.$refs.caseFinanceDrawer.load()
      }
    },
    openExpense(row) {
      this.expenseForm = {
        expenseId: row.expense_id || row.expenseId,
        caseId: row.case_id || row.caseId,
        expenseType: row.expense_type || row.expenseType,
        amount: Number(row.amount || 0),
        occurDate: row.occur_date || row.occurDate,
        payStatus: row.pay_status || row.payStatus,
        reimburseStatus: row.reimburse_status || row.reimburseStatus,
        voucherStatus: row.voucher_status || row.voucherStatus,
        handlerId: row.handler_id || row.handlerId,
        handlerName: row.handler_name || row.handlerName,
        voucherUrl: row.voucher_url || row.voucherUrl,
        voucherName: row.voucher_name || row.voucherName,
        remark: row.remark
      }
      this.expenseOpen = true
    },
    submitExpense() {
      this.$refs.actionDialogs.validate('expense', valid => {
        if (!valid) return
        if (Number(this.expenseForm.amount) <= 0) {
          this.$modal.msgError('费用金额必须大于 0')
          return
        }
        updateFinanceExpense(this.expenseForm).then(() => {
          this.$modal.msgSuccess('费用状态已更新')
          this.expenseOpen = false
          this.load()
          this.refreshCaseFinance()
        })
      })
    },
    syncExpenseFile(value) {
      this.expenseForm.voucherName = this.fileNameFromUrl(value)
      this.expenseForm.voucherStatus = value ? 'uploaded' : 'missing'
    },
    canInvoice(row) {
      return row.confirmStatus === '1' && row.invoiceStatus !== '1'
    },
    canCollect(row) {
      return row.confirmStatus === '0' || (row.confirmStatus === '1' && Number(row.pendingAmount || 0) > 0)
    },
    dashboardValue(key) {
      const map = this.keyed(this.dashboard.cards || [])
      return Number(map[key] && map[key].metricValue || 0)
    },
    listSum(getter) {
      return (this.list || []).reduce((sum, row) => sum + Number(getter(row) || 0), 0)
    },
    listCount(predicate) {
      return (this.list || []).filter(row => predicate(row)).length
    },
    completeMonthlyRows(rows, defaults) {
      const map = (rows || []).reduce((target, item) => {
        target[item.itemName] = item
        return target
      }, {})
      return this.lastSixMonths().map(month => ({ itemName: month, ...defaults, ...(map[month] || {}) }))
    },
    lastSixMonths() {
      const now = new Date()
      const months = []
      for (let i = 5; i >= 0; i--) {
        const date = new Date(now.getFullYear(), now.getMonth() - i, 1)
        months.push(date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0'))
      }
      return months
    },
    buildBars(rows, valueKey) {
      const list = rows && rows.length ? rows : []
      const max = Math.max(...list.map(item => Number(item[valueKey] || 0)), 1)
      const gap = list.length ? 520 / list.length : 520
      const width = Math.max(18, Math.min(42, gap * 0.34))
      return list.map((item, index) => {
        const value = Number(item[valueKey] || 0)
        const height = Math.max(6, value / max * 130)
        return { key: item.itemName + '-' + index, x: Math.round(index * gap + gap / 2 - width / 2), y: Math.round(175 - height), width, height, title: `${item.itemName}：${this.formatMoney(value)}` }
      })
    },
    buildLinePoints(rows, valueKey) {
      const list = rows && rows.length ? rows : []
      const max = Math.max(...list.map(item => Number(item[valueKey] || 0)), 1)
      const gap = list.length <= 1 ? 520 : 520 / (list.length - 1)
      return list.map((item, index) => {
        const value = Number(item[valueKey] || 0)
        return { key: item.itemName + '-' + index, x: Math.round(index * gap), y: Math.round(175 - value / max * 130), title: `${item.itemName}：${value.toLocaleString()}` }
      })
    },
    buildTrendPoints(rows, width) {
      const list = rows && rows.length ? rows : [{ itemName: '-', itemValue: 0 }]
      const max = Math.max(...list.map(item => Number(item.itemValue || 0)), 1)
      const gap = list.length === 1 ? width : width / (list.length - 1)
      return list.map((item, index) => ({ x: Math.round(index * gap), y: Math.round(150 - Number(item.itemValue || 0) / max * 110) }))
    },
    barWidth(value, rows) {
      const max = Math.max(...(rows || []).map(item => Number(item.itemValue || 0)), 1)
      return Math.max(8, Number(value || 0) / max * 100) + '%'
    },
    conversionBarWidth(item) {
      return Math.max(8, Number(item.conversionRate || 0)) + '%'
    },
    field(row, camelKey, snakeKey) {
      if (!row) return undefined
      return row[camelKey] !== undefined && row[camelKey] !== null ? row[camelKey] : row[snakeKey]
    },
    num(item) {
      return item ? Number(item.metricValue || 0) : 0
    },
    keyed(rows, valueKey) {
      return (rows || []).reduce((target, item) => {
        target[item.metricKey] = valueKey ? item[valueKey] : item
        return target
      }, {})
    },
    formatPlainMoney(value) {
      return Number(value || 0).toLocaleString()
    },
    formatShortMoney(value) {
      const amount = Number(value || 0)
      if (Math.abs(amount) >= 10000) return (amount / 10000).toLocaleString(undefined, { maximumFractionDigits: 1 }) + '万'
      return amount.toLocaleString()
    },
    formatCompactMoney(value) {
      const amount = Number(value || 0)
      if (Math.abs(amount) >= 10000) return (amount / 10000).toLocaleString(undefined, { maximumFractionDigits: 1 }) + '万'
      return amount.toLocaleString()
    },
    shortDate(value) {
      return value ? String(value).slice(5, 10) : '-'
    },
    formatSummaryValue(row) {
      return row.metricKey === 'receiveCount' ? Number(row.monthValue || 0).toLocaleString() : this.formatCompactMoney(row.monthValue)
    },
    formatSummaryYearValue(row) {
      return row.metricKey === 'receiveCount' ? Number(row.yearValue || 0).toLocaleString() : this.formatCompactMoney(row.yearValue)
    },
    formatMoney(value) {
      if (value == null || value === '') return '¥ 0'
      return '¥ ' + Number(value || 0).toLocaleString()
    }
  }
}

