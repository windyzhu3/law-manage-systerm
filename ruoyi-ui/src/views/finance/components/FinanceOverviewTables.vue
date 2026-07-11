<template>
  <section class="finance-overview-tables">
    <biz-table-card :toolbar="false" :pagination="false">
      <template slot="header"><div class="section-heading"><h3>待确认回款列表</h3><el-button :size="size" type="text" @click="$emit('navigate', 'payment')">更多</el-button></div></template>
      <el-table :data="pendingPayments" :size="size" class="overview-mini-table">
        <el-table-column label="客户名称" min-width="118" show-overflow-tooltip><template slot-scope="{ row }"><span class="biz-link">{{ row.customerName || '-' }}</span></template></el-table-column>
        <el-table-column label="金额" width="82" align="right"><template slot-scope="{ row }">{{ compactMoney(row.receivableAmount) }}</template></el-table-column>
        <el-table-column label="到账日" width="76"><template slot-scope="{ row }">{{ shortDate(row.planReceiveDate) }}</template></el-table-column>
      </el-table>
    </biz-table-card>

    <biz-table-card :toolbar="false" :pagination="false">
      <template slot="header"><div class="section-heading"><h3>逾期应收客户</h3><el-button :size="size" type="text" @click="$emit('navigate', 'receivable', { overdueOnly: '1' })">更多</el-button></div></template>
      <el-table :data="overdueReceivables" :size="size" class="overview-mini-table">
        <el-table-column label="客户名称" min-width="118" show-overflow-tooltip><template slot-scope="{ row }"><span class="biz-link">{{ row.customerName || '-' }}</span></template></el-table-column>
        <el-table-column label="天数" width="62" align="center"><template slot-scope="{ row }">{{ row.agingDays || 0 }}天</template></el-table-column>
        <el-table-column label="金额" width="82" align="right"><template slot-scope="{ row }">{{ compactMoney(row.pendingAmount) }}</template></el-table-column>
      </el-table>
    </biz-table-card>

    <biz-table-card :toolbar="false" :pagination="false">
      <template slot="header"><div class="section-heading"><h3>近期发票动态</h3><el-button :size="size" type="text" @click="$emit('navigate', 'invoice')">更多</el-button></div></template>
      <el-table :data="invoiceActivities" :size="size" class="overview-mini-table">
        <el-table-column label="客户名称" prop="customerName" min-width="122" show-overflow-tooltip />
        <el-table-column label="金额" width="82" align="right"><template slot-scope="{ row }">{{ compactMoney(row.amount) }}</template></el-table-column>
        <el-table-column label="状态" width="68" align="center"><template slot-scope="{ row }"><dict-tag :options="invoiceStatusOptions" :value="row.invoiceStatus" /></template></el-table-column>
      </el-table>
    </biz-table-card>

    <biz-table-card :toolbar="false" :pagination="false">
      <template slot="header"><div class="section-heading"><h3>费用 / 回款概览表</h3><el-button :size="size" type="text" @click="$emit('navigate', 'report')">查看报表</el-button></div></template>
      <el-table :data="summaryRows" :size="size" class="overview-mini-table">
        <el-table-column label="项目" prop="itemName" min-width="76" />
        <el-table-column label="本月" width="76" align="right"><template slot-scope="{ row }">{{ summaryValue(row) }}</template></el-table-column>
        <el-table-column label="本年" width="82" align="right"><template slot-scope="{ row }">{{ summaryYearValue(row) }}</template></el-table-column>
      </el-table>
    </biz-table-card>
  </section>
</template>

<script>
import BizTableCard from '@/views/business/components/BizTableCard'

export default {
  name: 'FinanceOverviewTables',
  components: { BizTableCard },
  props: {
    size: { type: String, default: 'small' },
    pendingPayments: { type: Array, default: () => [] },
    overdueReceivables: { type: Array, default: () => [] },
    invoiceActivities: { type: Array, default: () => [] },
    summaryRows: { type: Array, default: () => [] },
    invoiceStatusOptions: { type: Array, default: () => [] },
    compactMoney: { type: Function, required: true },
    shortDate: { type: Function, required: true },
    summaryValue: { type: Function, required: true },
    summaryYearValue: { type: Function, required: true }
  }
}
</script>

<style scoped lang="scss">
.finance-overview-tables { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-top: 16px; ::v-deep .biz-table-card { min-width: 0; } }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 12px; h3 { margin: 0; color: #0f172a; font-size: var(--biz-font-card); } }
.overview-mini-table { ::v-deep .el-table__cell { padding: 6px 0; font-size: var(--biz-font-mini); } ::v-deep th.el-table__cell { background: #f8fafc; } ::v-deep .cell { padding-left: 6px; padding-right: 6px; } ::v-deep .el-table__body-wrapper { overflow-x: hidden; } }
@media (max-width: 1180px) { .finance-overview-tables { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
