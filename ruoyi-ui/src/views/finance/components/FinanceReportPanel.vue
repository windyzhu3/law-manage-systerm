<template>
  <div>
  <div class="finance-report-toolbar finance-card">
  <div>
  <h3>报表周期</h3>
  <p>按回款、费用发生、线索转化和签约日期汇总财务数据</p>
  </div>
  <div class="report-toolbar-actions">
  <el-date-picker
  :value="reportDateRange" @input="$emit('update:reportDateRange', $event)"
  :size="controlSize"
  type="daterange"
  value-format="yyyy-MM-dd"
  range-separator="至"
  start-placeholder="开始日期"
  end-placeholder="结束日期"
  clearable
  @change="handleReportDateChange"
  />
  <el-button :size="controlSize" plain icon="el-icon-refresh" @click="resetReportRange">重置</el-button>
  </div>
  </div>
  
  <section class="report-grid">
  <article class="finance-card report-main">
  <div class="finance-card-title"><div><h3>收入趋势表</h3><p>{{ reportRangeText }}确认回款的月度趋势</p></div></div>
  <template v-if="(report.trend || []).length">
  <svg class="trend-chart report-trend" viewBox="0 0 700 180" preserveAspectRatio="none">
  <polyline class="trend-grid" points="0,150 700,150" />
  <polyline class="trend-grid" points="0,90 700,90" />
  <polyline class="trend-line" :points="reportTrendPoints" />
  <circle v-for="(point, index) in reportTrendPointList" :key="index" :cx="point.x" :cy="point.y" r="4" />
  </svg>
  <div class="trend-labels">
  <span v-for="item in report.trend" :key="item.itemName">{{ item.itemName }}</span>
  </div>
  </template>
  <el-empty v-else :image-size="80" description="暂无收入趋势数据" />
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
  <div v-if="(report.lawyerRevenue || []).length" class="bar-list"><div v-for="item in report.lawyerRevenue || []" :key="item.itemName"><span>{{ item.itemName }}</span><i><em :style="{ width: barWidth(item.itemValue, report.lawyerRevenue) }" /></i><strong>{{ formatMoney(item.itemValue) }}</strong></div></div>
  <el-empty v-else :image-size="72" description="暂无律师创收数据" />
  </article>
  <article class="finance-card">
  <div class="finance-card-title"><div><h3>案件成本分析</h3><p>按案件类型汇总办案费用</p></div></div>
  <div v-if="(report.caseCost || []).length" class="bar-list"><div v-for="item in report.caseCost || []" :key="item.itemName"><span>{{ dictLabel('law_case_type', item.itemName) }}</span><i><em :style="{ width: barWidth(item.itemValue, report.caseCost) }" /></i><strong>{{ formatMoney(item.itemValue) }}</strong></div></div>
  <el-empty v-else :image-size="72" description="暂无案件成本数据" />
  </article>
  <article class="finance-card">
  <div class="finance-card-title"><div><h3>销售回款统计</h3><p>按合同负责人统计确认回款</p></div></div>
  <div v-if="(report.salesCollection || []).length" class="bar-list"><div v-for="item in report.salesCollection || []" :key="item.itemName"><span>{{ item.itemName }}</span><i><em :style="{ width: barWidth(item.itemValue, report.salesCollection) }" /></i><strong>{{ formatMoney(item.itemValue) }}</strong></div></div>
  <el-empty v-else :image-size="72" description="暂无销售回款数据" />
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
  </div>
</template>

<script>
export default {
  name: 'FinanceReportPanel',
  props: {
    reportDateRange: { type: Array, default: () => [] },
    controlSize: { type: String, default: 'small' },
    report: { type: Object, default: () => ({}) },
    reportCards: { type: Array, default: () => [] },
    reportRangeText: { type: String, default: '' },
    reportTrendPoints: { type: String, default: '' },
    reportTrendPointList: { type: Array, default: () => [] },
    leadSourceConversion: { type: Array, default: () => [] },
    handleReportDateChange: { type: Function, required: true },
    resetReportRange: { type: Function, required: true },
    formatMoney: { type: Function, required: true },
    barWidth: { type: Function, required: true },
    dictLabel: { type: Function, required: true },
    conversionBarWidth: { type: Function, required: true }
  }
}
</script>

