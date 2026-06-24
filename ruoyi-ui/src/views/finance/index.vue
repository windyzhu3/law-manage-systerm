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
            <button v-for="item in reminderCards" :key="item.key" @click="switchMode(item.mode)">
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
            <el-table-column label="操作" width="100" align="center" fixed="right"><template slot-scope="{ row }"><el-button :size="controlSize" type="text" @click="openPayment(row)">确认</el-button></template></el-table-column>
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
        </div>
        <div class="biz-filter-actions">
          <el-button :size="controlSize" plain icon="el-icon-refresh" @click="reset">重置</el-button>
        </div>
      </template>

      <el-table v-if="mode === 'receivable'" v-loading="loading" :data="list" :size="controlSize">
        <el-table-column label="应收编号" prop="receivableNo" min-width="150" show-overflow-tooltip />
        <el-table-column label="客户/合同" min-width="210" show-overflow-tooltip><template slot-scope="{ row }"><span class="biz-link">{{ row.customerName }}</span><small class="sub-text">{{ row.contractNo }} / {{ row.contractName }}</small></template></el-table-column>
        <el-table-column label="案件编号" prop="caseNo" width="140" show-overflow-tooltip />
        <el-table-column label="应收金额" width="112" align="right"><template slot-scope="{ row }">{{ formatMoney(row.receivableAmount) }}</template></el-table-column>
        <el-table-column label="已收金额" width="112" align="right"><template slot-scope="{ row }">{{ formatMoney(row.receivedAmount) }}</template></el-table-column>
        <el-table-column label="待收金额" width="112" align="right"><template slot-scope="{ row }">{{ formatMoney(row.pendingAmount) }}</template></el-table-column>
        <el-table-column label="计划回款日" prop="planReceiveDate" width="120" />
        <el-table-column label="账龄" width="90" align="center"><template slot-scope="{ row }"><span :class="{ overdue: row.receivableStatus === 'overdue' }">{{ row.agingDays || 0 }} 天</span></template></el-table-column>
        <el-table-column label="回款状态" width="108" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_finance_receivable_status" :value="row.receivableStatus" /></template></el-table-column>
        <el-table-column label="开票状态" width="108" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_invoice_status" :value="row.invoiceStatus" /></template></el-table-column>
        <el-table-column label="操作" width="220" align="center" class-name="small-padding fixed-width biz-operation-column" fixed="right">
          <template slot-scope="{ row }">
            <span class="action-buttons">
              <el-button v-if="row.caseId" :size="controlSize" type="text" @click="openCaseFinance(row)">财务视图</el-button>
              <el-button v-if="row.confirmStatus === '0'" v-hasPermi="['finance:payment:confirm']" :size="controlSize" type="text" @click="openPayment(row)">确认回款</el-button>
              <el-button v-if="canInvoice(row)" v-hasPermi="['finance:invoice:handle']" :size="controlSize" type="text" @click="openInvoice(row)">开票</el-button>
            </span>
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else-if="mode === 'payment'" v-loading="loading" :data="list" :size="controlSize">
        <el-table-column label="回款单号" prop="paymentNo" min-width="150" show-overflow-tooltip />
        <el-table-column label="客户/合同" min-width="220" show-overflow-tooltip><template slot-scope="{ row }"><span class="biz-link">{{ row.customerName }}</span><small class="sub-text">{{ row.contractNo }} / {{ row.caseNo || '-' }}</small></template></el-table-column>
        <el-table-column label="本次金额" width="120" align="right"><template slot-scope="{ row }">{{ formatMoney(row.receivedAmount || row.receivableAmount) }}</template></el-table-column>
        <el-table-column label="计划日期" prop="planReceiveDate" width="120" />
        <el-table-column label="提交状态" width="108" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_receive_status" :value="row.confirmStatus" /></template></el-table-column>
        <el-table-column label="负责人" prop="ownerName" width="110" />
        <el-table-column label="操作" width="230" align="center" class-name="small-padding fixed-width biz-operation-column" fixed="right">
          <template slot-scope="{ row }">
            <span class="action-buttons">
              <el-button v-if="row.caseId" :size="controlSize" type="text" @click="openCaseFinance(row)">财务视图</el-button>
              <el-button v-if="row.confirmStatus === '0'" v-hasPermi="['finance:payment:confirm']" :size="controlSize" type="text" @click="openPayment(row)">确认</el-button>
              <el-button v-if="row.confirmStatus === '0'" v-hasPermi="['finance:payment:reject']" :size="controlSize" type="text" class="danger-text" @click="openReject(row)">驳回</el-button>
              <el-button v-if="canInvoice(row)" v-hasPermi="['finance:invoice:handle']" :size="controlSize" type="text" @click="openInvoice(row)">开票</el-button>
            </span>
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else-if="mode === 'invoice'" v-loading="loading" :data="list" :size="controlSize">
        <el-table-column label="开票申请单" prop="invoiceApplyNo" min-width="150" show-overflow-tooltip />
        <el-table-column label="发票号码" prop="invoiceNo" min-width="140" show-overflow-tooltip><template slot-scope="{ row }">{{ row.invoiceNo || '-' }}</template></el-table-column>
        <el-table-column label="客户/合同" min-width="220" show-overflow-tooltip><template slot-scope="{ row }"><span class="biz-link">{{ row.customerName }}</span><small class="sub-text">{{ row.contractNo }} / {{ row.caseNo || '-' }}</small></template></el-table-column>
        <el-table-column label="开票金额" width="120" align="right"><template slot-scope="{ row }">{{ formatMoney(row.receivedAmount || row.receivableAmount) }}</template></el-table-column>
        <el-table-column label="回款状态" width="108" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_receive_status" :value="row.confirmStatus" /></template></el-table-column>
        <el-table-column label="开票状态" width="108" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_invoice_status" :value="row.invoiceStatus" /></template></el-table-column>
        <el-table-column label="操作" width="190" align="center" class-name="small-padding fixed-width biz-operation-column" fixed="right">
          <template slot-scope="{ row }">
            <span class="action-buttons">
              <el-button v-if="row.caseId" :size="controlSize" type="text" @click="openCaseFinance(row)">财务视图</el-button>
              <el-button v-if="canInvoice(row)" v-hasPermi="['finance:invoice:handle']" :size="controlSize" type="text" @click="openInvoice(row)">{{ row.invoiceStatus === '2' ? '补齐开票' : '开票' }}</el-button>
            </span>
          </template>
        </el-table-column>
      </el-table>

      <el-table v-else v-loading="loading" :data="list" :size="controlSize">
        <el-table-column label="费用编号" prop="expense_no" width="150" show-overflow-tooltip />
        <el-table-column label="案件/客户" min-width="220" show-overflow-tooltip><template slot-scope="{ row }"><span class="biz-link">{{ row.caseName || row.case_name }}</span><small class="sub-text">{{ row.customerName || row.customer_name || '-' }}</small></template></el-table-column>
        <el-table-column label="合同编号" prop="contractNo" width="140" show-overflow-tooltip />
        <el-table-column label="费用类型" width="112" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_expense_type" :value="row.expense_type" /></template></el-table-column>
        <el-table-column label="金额" width="120" align="right"><template slot-scope="{ row }">{{ formatMoney(row.amount) }}</template></el-table-column>
        <el-table-column label="发生日期" prop="occur_date" width="120" />
        <el-table-column label="付款" width="96" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_pay_status" :value="row.pay_status" /></template></el-table-column>
        <el-table-column label="报销" width="96" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_reimburse_status" :value="row.reimburse_status" /></template></el-table-column>
        <el-table-column label="凭证" width="96" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_voucher_status" :value="row.voucher_status" /></template></el-table-column>
        <el-table-column label="负责人" prop="handler_name" width="110" />
        <el-table-column label="操作" width="170" align="center" class-name="small-padding fixed-width biz-operation-column" fixed="right">
          <template slot-scope="{ row }">
            <span class="action-buttons">
              <el-button :size="controlSize" type="text" @click="openCaseFinance(row)">财务视图</el-button>
              <el-button v-hasPermi="['finance:expense:edit']" :size="controlSize" type="text" @click="openExpense(row)">处理</el-button>
            </span>
          </template>
        </el-table-column>
      </el-table>
    </biz-table-card>

    <section v-else class="report-grid">
      <article class="finance-card report-main">
        <div class="finance-card-title"><div><h3>收入趋势表</h3><p>确认回款的月度趋势</p></div></div>
        <svg class="trend-chart report-trend" viewBox="0 0 700 180" preserveAspectRatio="none">
          <polyline class="trend-grid" points="0,150 700,150" />
          <polyline class="trend-grid" points="0,90 700,90" />
          <polyline class="trend-line" :points="reportTrendPoints" />
        </svg>
      </article>
      <article class="finance-card" v-for="card in reportCards" :key="card.key">
        <div class="report-stat">
          <span>{{ card.title }}</span>
          <strong>{{ formatMoney(card.value) }}</strong>
          <small>{{ card.desc }}</small>
        </div>
      </article>
      <article class="finance-card">
        <div class="finance-card-title"><div><h3>律师创收统计</h3><p>按合同承办律师统计确认回款</p></div></div>
        <div class="bar-list"><div v-for="item in report.lawyerRevenue || []" :key="item.itemName"><span>{{ item.itemName }}</span><i><em :style="{ width: barWidth(item.itemValue, report.lawyerRevenue) }" /></i><strong>{{ formatMoney(item.itemValue) }}</strong></div></div>
      </article>
      <article class="finance-card">
        <div class="finance-card-title"><div><h3>案件成本分析</h3><p>按案件类型汇总办案费用</p></div></div>
        <div class="bar-list"><div v-for="item in report.caseCost || []" :key="item.itemName"><span>{{ dictLabel('law_case_type', item.itemName) }}</span><i><em :style="{ width: barWidth(item.itemValue, report.caseCost) }" /></i><strong>{{ formatMoney(item.itemValue) }}</strong></div></div>
      </article>
      <article class="finance-card report-main">
        <div class="finance-card-title"><div><h3>线索来源转化</h3><p>按线索来源统计转化率和预计转化金额</p></div></div>
        <div class="source-conversion-list">
          <div v-for="item in leadSourceConversion" :key="item.itemName">
            <span>{{ item.itemName }}</span>
            <i><em :style="{ width: conversionBarWidth(item) }" /></i>
            <strong>{{ Number(item.conversionRate || 0).toFixed(2) }}%</strong>
            <small>{{ item.convertedCount || 0 }}/{{ item.leadCount || 0 }} · {{ formatMoney(item.estimatedAmount) }}</small>
          </div>
          <el-empty v-if="!leadSourceConversion.length" :image-size="80" description="暂无线索转化数据" />
        </div>
      </article>
    </section>

    <el-dialog title="确认回款" :visible.sync="paymentOpen" width="520px" :custom-class="dialogClass" append-to-body>
      <el-form ref="paymentFormRef" :model="paymentForm" :rules="paymentRules" label-width="110px">
        <el-form-item label="客户名称"><span>{{ paymentForm.customerName || '-' }}</span></el-form-item>
        <el-form-item label="应收金额"><span>{{ formatMoney(paymentForm.receivableAmount) }}</span></el-form-item>
        <el-form-item label="本次回款" prop="receivedAmount"><el-input-number v-model="paymentForm.receivedAmount" :size="controlSize" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="处理备注"><el-input v-model="paymentForm.reason" :size="controlSize" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="paymentOpen=false">取消</el-button><el-button :size="controlSize" type="primary" @click="submitPayment">确认入账</el-button></div>
    </el-dialog>

    <el-dialog title="驳回回款" :visible.sync="rejectOpen" width="520px" :custom-class="dialogClass" append-to-body>
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" label-width="100px">
        <el-form-item label="客户名称"><span>{{ rejectForm.customerName || '-' }}</span></el-form-item>
        <el-form-item label="驳回原因" prop="reason"><el-input v-model="rejectForm.reason" :size="controlSize" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="rejectOpen=false">取消</el-button><el-button :size="controlSize" type="danger" @click="submitReject">确认驳回</el-button></div>
    </el-dialog>

    <el-dialog title="开票处理" :visible.sync="invoiceOpen" width="520px" :custom-class="dialogClass" append-to-body>
      <el-form ref="invoiceFormRef" :model="invoiceForm" :rules="invoiceRules" label-width="110px">
        <el-form-item label="客户名称"><span>{{ invoiceForm.customerName || '-' }}</span></el-form-item>
        <el-form-item label="可开票金额"><span>{{ formatMoney(invoiceForm.receivedAmount || invoiceForm.receivableAmount) }}</span></el-form-item>
        <el-form-item label="开票动作" prop="invoiceStatus">
          <el-radio-group v-model="invoiceForm.invoiceStatus">
            <el-radio v-if="invoiceForm.currentInvoiceStatus !== '2'" label="2">部分开票</el-radio>
            <el-radio label="1">{{ invoiceForm.currentInvoiceStatus === '2' ? '补齐开票' : '已开票' }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="invoiceOpen=false">取消</el-button><el-button :size="controlSize" type="primary" @click="submitInvoice">保存</el-button></div>
    </el-dialog>

    <el-dialog title="费用处理" :visible.sync="expenseOpen" width="640px" :custom-class="dialogClass" append-to-body>
      <el-form ref="expenseFormRef" :model="expenseForm" :rules="expenseRules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="费用类型" prop="expenseType"><el-select v-model="expenseForm.expenseType" :size="controlSize"><el-option v-for="item in dict.type.law_case_expense_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="费用金额" prop="amount"><el-input-number v-model="expenseForm.amount" :size="controlSize" :min="0" :precision="2" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="发生日期" prop="occurDate"><el-date-picker v-model="expenseForm.occurDate" :size="controlSize" value-format="yyyy-MM-dd" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="付款状态" prop="payStatus"><el-select v-model="expenseForm.payStatus" :size="controlSize"><el-option v-for="item in dict.type.law_case_pay_status" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="报销状态" prop="reimburseStatus"><el-select v-model="expenseForm.reimburseStatus" :size="controlSize"><el-option v-for="item in dict.type.law_case_reimburse_status" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="凭证状态" prop="voucherStatus"><el-select v-model="expenseForm.voucherStatus" :size="controlSize"><el-option v-for="item in dict.type.law_case_voucher_status" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="expenseForm.remark" :size="controlSize" type="textarea" :rows="3" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="expenseOpen=false">取消</el-button><el-button :size="controlSize" type="primary" @click="submitExpense">保存</el-button></div>
    </el-dialog>

    <el-drawer
      title="案件财务视图"
      :visible.sync="caseFinanceOpen"
      size="920px"
      custom-class="finance-case-drawer"
      append-to-body
    >
      <div v-loading="caseFinanceLoading" class="case-finance-body">
        <section class="case-finance-summary">
          <div>
            <span>案件</span>
            <h2>{{ caseFinanceSummary.caseName || '-' }}</h2>
            <p>{{ caseFinanceSummary.caseNo || '-' }} · {{ caseFinanceSummary.customerName || '-' }}</p>
          </div>
          <div>
            <span>来源合同</span>
            <h3>{{ caseFinanceSummary.contractNo || '-' }}</h3>
            <p>{{ caseFinanceSummary.contractName || '-' }}</p>
          </div>
          <div>
            <span>主办律师</span>
            <h3>{{ caseFinanceSummary.mainLawyerName || '-' }}</h3>
            <p>{{ caseFinanceSummary.assistantLawyerNames || '暂无协办' }}</p>
          </div>
        </section>

        <section class="case-finance-metrics">
          <div v-for="item in caseFinanceCards" :key="item.key">
            <i :class="item.icon" />
            <span>{{ item.title }}</span>
            <strong>{{ item.formatter ? item.formatter(item.value) : item.value }}</strong>
            <small>{{ item.desc }}</small>
          </div>
        </section>

        <section class="case-finance-grid">
          <article class="finance-card">
            <div class="finance-card-title"><div><h3>收费计划</h3><p>合同计划、回款和开票状态</p></div></div>
            <el-table :data="caseFinance.feePlans || []" :size="controlSize" max-height="280">
              <el-table-column label="期次" prop="periodNo" width="70" align="center" />
              <el-table-column label="应收金额" width="110" align="right"><template slot-scope="{ row }">{{ formatMoney(row.receivableAmount) }}</template></el-table-column>
              <el-table-column label="已收金额" width="110" align="right"><template slot-scope="{ row }">{{ formatMoney(row.receivedAmount) }}</template></el-table-column>
              <el-table-column label="计划日期" prop="planReceiveDate" width="110" />
              <el-table-column label="回款" width="96" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_receive_status" :value="row.confirmStatus" /></template></el-table-column>
              <el-table-column label="开票" width="96" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_contract_invoice_status" :value="row.invoiceStatus" /></template></el-table-column>
            </el-table>
          </article>

          <article class="finance-card">
            <div class="finance-card-title"><div><h3>案件费用</h3><p>办案支出、付款、报销和凭证状态</p></div></div>
            <el-table :data="caseFinance.expenses || []" :size="controlSize" max-height="280">
              <el-table-column label="费用类型" min-width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_expense_type" :value="row.expenseType" /></template></el-table-column>
              <el-table-column label="金额" width="110" align="right"><template slot-scope="{ row }">{{ formatMoney(row.amount) }}</template></el-table-column>
              <el-table-column label="发生日期" prop="occurDate" width="110" />
              <el-table-column label="付款" width="88" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_pay_status" :value="row.payStatus" /></template></el-table-column>
              <el-table-column label="凭证" width="88" align="center"><template slot-scope="{ row }"><dict-tag :options="dict.type.law_case_voucher_status" :value="row.voucherStatus" /></template></el-table-column>
            </el-table>
          </article>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import BizPageHeader from '@/views/business/components/BizPageHeader'
import BizHero from '@/views/business/components/BizHero'
import BizMetrics from '@/views/business/components/BizMetrics'
import BizTableCard from '@/views/business/components/BizTableCard'
import businessUi from '@/views/business/mixins/businessUi'
import {
  getFinanceDashboard,
  listReceivable,
  listPayment,
  listInvoice,
  listFinanceExpense,
  getCaseFinance,
  getFinanceReport,
  confirmPayment,
  rejectPayment,
  handleInvoice,
  updateFinanceExpense
} from '@/api/finance'
import '@/views/business/business.scss'
import '@/views/business/business-dialog.scss'

export default {
  name: 'FinanceCenter',
  components: { BizPageHeader, BizHero, BizMetrics, BizTableCard },
  mixins: [businessUi],
  dicts: [
    'law_finance_receivable_status',
    'law_finance_age_bucket',
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
      leadFunnel: [],
      leadSourceConversion: [],
      pendingPayments: [],
      dueReceivables: [],
      query: { pageNum: 1, pageSize: 10 },
      paymentOpen: false,
      rejectOpen: false,
      invoiceOpen: false,
      expenseOpen: false,
      caseFinanceOpen: false,
      caseFinanceLoading: false,
      caseFinance: {},
      paymentForm: {},
      rejectForm: {},
      invoiceForm: {},
      expenseForm: {},
      paymentRules: { receivedAmount: [{ required: true, message: '请输入本次回款金额', trigger: 'blur' }] },
      rejectRules: { reason: [{ required: true, message: '请填写驳回原因', trigger: 'blur' }] },
      invoiceRules: { invoiceStatus: [{ required: true, message: '请选择开票动作', trigger: 'change' }] },
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
      const data = this.dashboard.cards || []
      return data.map(item => ({ key: item.metricKey, value: item.metricValue }))
    },
    metricConfig() {
      return {
        receivableTotal: { title: '应收总额', icon: 'el-icon-coin', color: 'blue', formatter: this.formatMoney },
        receivedTotal: { title: '已收金额', icon: 'el-icon-wallet', color: 'green', formatter: this.formatMoney },
        pendingTotal: { title: '待收金额', icon: 'el-icon-bank-card', color: 'orange', formatter: this.formatMoney },
        overdueTotal: { title: '逾期金额', icon: 'el-icon-warning-outline', color: 'red', formatter: this.formatMoney },
        monthReceived: { title: '本月回款', icon: 'el-icon-data-line', color: 'cyan', formatter: this.formatMoney },
        pendingInvoiceTotal: { title: '待开票金额', icon: 'el-icon-document-checked', color: 'purple', formatter: this.formatMoney }
      }
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
    totalTrendAmount() {
      return (this.trend || []).reduce((sum, item) => sum + Number(item.itemValue || 0), 0)
    },
    trendPointList() {
      return this.buildTrendPoints(this.trend, 520)
    },
    trendPoints() {
      return this.trendPointList.map(item => `${item.x},${item.y}`).join(' ')
    },
    reportTrendPoints() {
      return this.buildTrendPoints(this.report.trend || [], 700).map(item => `${item.x},${item.y}`).join(' ')
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
        { key: 'overdueReceivable', title: '逾期应收', desc: '超过计划回款日的款项', value: this.num(map.overdueReceivable), mode: 'receivable', icon: 'el-icon-warning-outline' },
        { key: 'pendingInvoice', title: '待开票', desc: '已回款但未完全开票', value: this.num(map.pendingInvoice), mode: 'invoice', icon: 'el-icon-document-checked' },
        { key: 'highRisk', title: '高风险款项', desc: '高风险合同未收款项', value: this.num(map.highRisk), mode: 'receivable', icon: 'el-icon-bell' }
      ]
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
    caseFinanceSummary() {
      return this.caseFinance.summary || {}
    },
    caseFinanceCards() {
      const row = this.caseFinanceSummary
      return [
        { key: 'contractAmount', title: '合同金额', value: row.contractAmount || 0, desc: '来源合同签约金额', icon: 'el-icon-document', formatter: this.formatMoney },
        { key: 'receivedTotal', title: '已收金额', value: row.receivedTotal || 0, desc: '已确认回款', icon: 'el-icon-wallet', formatter: this.formatMoney },
        { key: 'pendingTotal', title: '待收金额', value: row.pendingTotal || 0, desc: '收费计划未收余额', icon: 'el-icon-bank-card', formatter: this.formatMoney },
        { key: 'expenseTotal', title: '案件费用', value: row.expenseTotal || 0, desc: '办案支出合计', icon: 'el-icon-money', formatter: this.formatMoney },
        { key: 'grossProfit', title: '案件毛利', value: row.grossProfit || 0, desc: `毛利率 ${Number(row.grossMargin || 0).toFixed(2)}%`, icon: 'el-icon-data-analysis', formatter: this.formatMoney }
      ]
    }
  },
  watch: {
    '$route.query.module'(value) {
      this.mode = value || 'overview'
      this.resetQuery()
      this.load()
    }
  },
  created() {
    this.load()
  },
  methods: {
    switchMode(mode) {
      this.$router.push({ path: '/finance/' + mode, query: { module: mode } }).catch(() => {})
    },
    load() {
      if (this.mode === 'overview') return this.loadDashboard()
      if (this.mode === 'report') return this.loadReport()
      return this.loadPage()
    },
    loadDashboard() {
      this.loading = true
      getFinanceDashboard().then(res => {
        const data = res.data || {}
        this.dashboard = data
        this.trend = data.trend || []
        this.aging = data.aging || []
        this.links = data.links || []
        this.reminders = data.reminders || []
        this.leadFunnel = data.leadFunnel || []
        this.leadSourceConversion = data.leadSourceConversion || []
        this.pendingPayments = (data.pendingPayments || []).slice(0, 5)
        this.dueReceivables = (data.dueReceivables || []).slice(0, 5)
      }).finally(() => { this.loading = false })
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
      this.loadPage()
    },
    reset() {
      this.resetQuery()
      this.load()
    },
    resetQuery() {
      this.query = { pageNum: 1, pageSize: 10 }
      this.total = 0
      this.list = []
    },
    openPayment(row) {
      this.paymentForm = { ...row, receivedAmount: Number(row.pendingAmount || row.receivableAmount || 0) }
      this.paymentOpen = true
    },
    submitPayment() {
      this.$refs.paymentFormRef.validate(valid => {
        if (!valid) return
        if (Number(this.paymentForm.receivedAmount) <= 0) {
          this.$modal.msgError('本次回款金额必须大于 0')
          return
        }
        confirmPayment({ planId: this.paymentForm.planId, receivedAmount: this.paymentForm.receivedAmount }).then(() => {
          this.$modal.msgSuccess('回款已确认')
          this.paymentOpen = false
          this.load()
        })
      })
    },
    openReject(row) {
      this.rejectForm = { ...row, reason: '' }
      this.rejectOpen = true
    },
    submitReject() {
      this.$refs.rejectFormRef.validate(valid => {
        if (!valid) return
        rejectPayment({ planId: this.rejectForm.planId, reason: this.rejectForm.reason }).then(() => {
          this.$modal.msgSuccess('已驳回回款')
          this.rejectOpen = false
          this.load()
        })
      })
    },
    openInvoice(row) {
      this.invoiceForm = { ...row, currentInvoiceStatus: row.invoiceStatus, invoiceStatus: row.invoiceStatus === '2' ? '1' : '2' }
      this.invoiceOpen = true
    },
    submitInvoice() {
      this.$refs.invoiceFormRef.validate(valid => {
        if (!valid) return
        handleInvoice({ planId: this.invoiceForm.planId, invoiceStatus: this.invoiceForm.invoiceStatus }).then(() => {
          this.$modal.msgSuccess('开票状态已更新')
          this.invoiceOpen = false
          this.load()
        })
      })
    },
    openCaseFinance(row) {
      const caseId = row.caseId || row.case_id
      if (!caseId) {
        this.$modal.msgError('当前费用未关联案件')
        return
      }
      this.caseFinanceOpen = true
      this.caseFinanceLoading = true
      getCaseFinance(caseId).then(res => {
        this.caseFinance = res.data || {}
      }).finally(() => {
        this.caseFinanceLoading = false
      })
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
      this.$refs.expenseFormRef.validate(valid => {
        if (!valid) return
        if (Number(this.expenseForm.amount) <= 0) {
          this.$modal.msgError('费用金额必须大于 0')
          return
        }
        updateFinanceExpense(this.expenseForm).then(() => {
          this.$modal.msgSuccess('费用状态已更新')
          this.expenseOpen = false
          this.load()
        })
      })
    },
    canInvoice(row) {
      return row.confirmStatus === '1' && row.invoiceStatus !== '1'
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
    num(item) {
      return item ? Number(item.metricValue || 0) : 0
    },
    keyed(rows, valueKey) {
      return (rows || []).reduce((target, item) => {
        target[item.metricKey] = valueKey ? item[valueKey] : item
        return target
      }, {})
    },
    formatMoney(value) {
      if (value == null || value === '') return '¥ 0'
      return '¥ ' + Number(value || 0).toLocaleString()
    }
  }
}
</script>

<style scoped lang="scss">
.finance-overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(280px, .75fr) minmax(280px, .75fr);
  gap: 16px;
  margin-top: 16px;
}

.finance-card {
  border: 1px solid #e8edf6;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 7px 16px rgba(36, 73, 135, .045);
  padding: 16px;
}

.trend-card {
  grid-row: span 2;
}

.finance-card-title,
.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;

  h3 {
    margin: 0;
    color: #0f172a;
    font-size: var(--biz-font-card);
  }

  p {
    margin: 4px 0 0;
    color: #94a3b8;
    font-size: var(--biz-font-mini);
  }

  strong {
    color: #2563eb;
    font-size: var(--biz-font-section);
  }
}

.trend-chart {
  width: 100%;
  height: 180px;

  .trend-grid {
    fill: none;
    stroke: #eef2f7;
    stroke-width: 1;
  }

  .trend-line {
    fill: none;
    stroke: #2563eb;
    stroke-width: 4;
    stroke-linecap: round;
    stroke-linejoin: round;
  }

  circle {
    fill: #fff;
    stroke: #2563eb;
    stroke-width: 3;
  }
}

.trend-labels {
  display: flex;
  justify-content: space-between;
  color: #94a3b8;
  font-size: var(--biz-font-mini);
}

.donut-wrap {
  display: flex;
  align-items: center;
  gap: 18px;
}

.donut {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 118px;
  height: 118px;
  border-radius: 50%;
  flex: 0 0 118px;

  &::after {
    content: '';
    position: absolute;
    inset: 22px;
    border-radius: 50%;
    background: #fff;
  }

  b,
  span {
    position: relative;
    z-index: 1;
  }

  b {
    color: #0f172a;
    font-size: var(--biz-font-metric);
  }

  span {
    color: #64748b;
    font-size: var(--biz-font-mini);
  }
}

.donut-wrap ul {
  flex: 1;
  padding: 0;
  margin: 0;
  list-style: none;

  li {
    display: grid;
    grid-template-columns: 10px 1fr auto;
    align-items: center;
    gap: 8px;
    margin: 8px 0;
    color: #64748b;
    font-size: var(--biz-font-small);
  }

  em {
    width: 8px;
    height: 8px;
    border-radius: 999px;
  }

  strong {
    color: #0f172a;
  }
}

.link-items {
  display: grid;
  gap: 10px;

  div {
    display: grid;
    grid-template-columns: 36px 1fr auto;
    align-items: center;
    gap: 10px;
    padding: 11px;
    border: 1px solid #edf2f7;
    border-radius: 10px;
    background: #f8fbff;
  }

  i {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    border-radius: 50%;
    color: #2563eb;
    background: #eaf2ff;
  }

  span {
    color: #334155;
    font-size: var(--biz-font-small);
  }

  strong {
    color: #0f172a;
    font-size: var(--biz-font-section);
  }

  small {
    grid-column: 2 / 4;
    color: #94a3b8;
  }
}

.funnel-list {
  display: grid;
  gap: 11px;

  div {
    display: grid;
    grid-template-columns: 86px 1fr auto;
    align-items: center;
    gap: 10px;
  }

  span {
    color: #334155;
    font-size: var(--biz-font-small);
  }

  i {
    height: 8px;
    border-radius: 999px;
    background: #eef2ff;
    overflow: hidden;
  }

  em {
    display: block;
    height: 100%;
    border-radius: inherit;
    background: linear-gradient(90deg, #2563eb, #38bdf8);
  }

  strong {
    color: #0f172a;
    font-size: var(--biz-font-card);
  }

  small {
    grid-column: 2 / 4;
    color: #94a3b8;
    font-size: var(--biz-font-mini);
  }
}

.reminder-list {
  display: grid;
  gap: 10px;

  button {
    display: grid;
    grid-template-columns: 40px 1fr auto 14px;
    align-items: center;
    gap: 10px;
    width: 100%;
    padding: 12px;
    border: 1px solid #edf2f7;
    border-radius: 10px;
    background: #fff;
    text-align: left;
    cursor: pointer;
  }

  i:first-child {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: 50%;
    color: #2563eb;
    background: #eef4ff;
    font-size: 18px;
  }

  b,
  small {
    display: block;
  }

  b {
    color: #0f172a;
    font-size: var(--biz-font-small);
  }

  small {
    margin-top: 3px;
    color: #94a3b8;
  }

  strong {
    color: #ef4444;
    font-size: var(--biz-font-section);
  }
}

.finance-tables-grid,
.report-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.report-main {
  grid-column: 1 / -1;
}

.report-stat {
  display: grid;
  gap: 8px;

  span {
    color: #64748b;
    font-size: var(--biz-font-small);
  }

  strong {
    color: #0f172a;
    font-size: var(--biz-font-metric);
  }

  small {
    color: #94a3b8;
  }
}

.bar-list {
  display: grid;
  gap: 10px;

  div {
    display: grid;
    grid-template-columns: 96px 1fr 120px;
    align-items: center;
    gap: 10px;
    font-size: var(--biz-font-small);
  }

  i {
    height: 8px;
    border-radius: 999px;
    background: #edf2f7;
    overflow: hidden;
  }

  em {
    display: block;
    height: 100%;
    border-radius: inherit;
    background: linear-gradient(90deg, #2563eb, #06b6d4);
  }

  strong {
    text-align: right;
    color: #0f172a;
  }
}

.source-conversion-list {
  display: grid;
  gap: 12px;

  > div {
    display: grid;
    grid-template-columns: 150px minmax(160px, 1fr) 72px 180px;
    align-items: center;
    gap: 12px;
  }

  span {
    color: #334155;
    font-size: var(--biz-font-small);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  i {
    height: 9px;
    border-radius: 999px;
    background: #eef2ff;
    overflow: hidden;
  }

  em {
    display: block;
    height: 100%;
    border-radius: inherit;
    background: linear-gradient(90deg, #7c3aed, #22d3ee);
  }

  strong {
    color: #2563eb;
    font-size: var(--biz-font-card);
  }

  small {
    color: #64748b;
    font-size: var(--biz-font-mini);
    text-align: right;
  }
}

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

.overdue {
  color: #ef4444;
  font-weight: 700;
}

@media (max-width: 1280px) {
  .finance-overview-grid,
  .finance-tables-grid,
  .report-grid {
    grid-template-columns: 1fr;
  }
}
</style>
