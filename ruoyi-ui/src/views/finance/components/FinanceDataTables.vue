<template>
  <div>
    <el-table v-if="mode === 'receivable'" v-loading="loading" :data="list" :size="controlSize">
      <el-table-column label="合同编号" min-width="145"><template slot-scope="{ row }">{{ field(row, 'contractNo', 'contract_no') || '-' }}</template></el-table-column>
      <el-table-column label="客户名称" min-width="150"><template slot-scope="{ row }"><span class="biz-link">{{ field(row, 'customerName', 'customer_name') || '-' }}</span></template></el-table-column>
      <el-table-column label="应收金额" width="120" align="right"><template slot-scope="{ row }">{{ formatMoney(field(row, 'receivableAmount', 'receivable_amount')) }}</template></el-table-column>
      <el-table-column label="已收金额" width="120" align="right"><template slot-scope="{ row }">{{ formatMoney(field(row, 'receivedAmount', 'received_amount')) }}</template></el-table-column>
      <el-table-column label="待收金额" width="120" align="right"><template slot-scope="{ row }">{{ formatMoney(field(row, 'pendingAmount', 'pending_amount')) }}</template></el-table-column>
      <el-table-column label="计划收款日" width="125"><template slot-scope="{ row }">{{ field(row, 'planReceiveDate', 'plan_receive_date') || '-' }}</template></el-table-column>
      <el-table-column label="回款状态" width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dictOptions.law_contract_receive_status" :value="field(row, 'confirmStatus', 'confirm_status')" /></template></el-table-column>
      <el-table-column label="操作" width="90" align="center" fixed="right"><template slot-scope="{ row }"><el-button :size="controlSize" type="text" @click="openCaseFinance(row)">详情</el-button></template></el-table-column>
    </el-table>

    <el-table v-else-if="mode === 'payment'" v-loading="loading" :data="list" :size="controlSize">
      <el-table-column label="合同编号" min-width="145"><template slot-scope="{ row }">{{ field(row, 'contractNo', 'contract_no') || '-' }}</template></el-table-column>
      <el-table-column label="客户名称" min-width="150"><template slot-scope="{ row }"><span class="biz-link">{{ field(row, 'customerName', 'customer_name') || '-' }}</span></template></el-table-column>
      <el-table-column label="应收金额" width="120" align="right"><template slot-scope="{ row }">{{ formatMoney(field(row, 'receivableAmount', 'receivable_amount')) }}</template></el-table-column>
      <el-table-column label="本次待收" width="120" align="right"><template slot-scope="{ row }">{{ formatMoney(field(row, 'pendingAmount', 'pending_amount')) }}</template></el-table-column>
      <el-table-column label="计划收款日" width="125"><template slot-scope="{ row }">{{ field(row, 'planReceiveDate', 'plan_receive_date') || '-' }}</template></el-table-column>
      <el-table-column label="回款状态" width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dictOptions.law_contract_receive_status" :value="field(row, 'confirmStatus', 'confirm_status')" /></template></el-table-column>
      <el-table-column label="操作" width="150" align="center" fixed="right">
        <template slot-scope="{ row }">
          <el-button v-hasPermi="['finance:payment:confirm']" :size="controlSize" type="text" :disabled="!canCollect(row)" @click="openPayment(row)">确认</el-button>
          <el-button v-hasPermi="['finance:payment:reject']" :size="controlSize" type="text" class="danger-text" :disabled="!canCollect(row)" @click="openReject(row)">驳回</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-table v-else-if="mode === 'invoice'" v-loading="loading" :data="list" :size="controlSize">
      <el-table-column label="合同编号" min-width="145"><template slot-scope="{ row }">{{ field(row, 'contractNo', 'contract_no') || '-' }}</template></el-table-column>
      <el-table-column label="客户名称" min-width="150"><template slot-scope="{ row }"><span class="biz-link">{{ field(row, 'customerName', 'customer_name') || '-' }}</span></template></el-table-column>
      <el-table-column label="已收金额" width="120" align="right"><template slot-scope="{ row }">{{ formatMoney(field(row, 'receivedAmount', 'received_amount')) }}</template></el-table-column>
      <el-table-column label="开票状态" width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dictOptions.law_contract_invoice_status" :value="field(row, 'invoiceStatus', 'invoice_status')" /></template></el-table-column>
      <el-table-column label="操作" width="100" align="center" fixed="right"><template slot-scope="{ row }"><el-button v-hasPermi="['finance:invoice:handle']" :size="controlSize" type="text" :disabled="!canInvoice(row)" @click="openInvoice(row)">开票</el-button></template></el-table-column>
    </el-table>

    <el-table v-else-if="mode === 'expense'" v-loading="loading" :data="list" :size="controlSize">
      <el-table-column label="案件编号" min-width="145"><template slot-scope="{ row }">{{ field(row, 'caseNo', 'case_no') || '-' }}</template></el-table-column>
      <el-table-column label="案件名称" min-width="180"><template slot-scope="{ row }"><span class="biz-link">{{ field(row, 'caseName', 'case_name') || '-' }}</span></template></el-table-column>
      <el-table-column label="费用类型" width="110" align="center"><template slot-scope="{ row }"><dict-tag :options="dictOptions.law_case_expense_type" :value="field(row, 'expenseType', 'expense_type')" /></template></el-table-column>
      <el-table-column label="金额" width="120" align="right"><template slot-scope="{ row }">{{ formatMoney(field(row, 'amount', 'amount')) }}</template></el-table-column>
      <el-table-column label="发生日期" width="125"><template slot-scope="{ row }">{{ field(row, 'occurDate', 'occur_date') || '-' }}</template></el-table-column>
      <el-table-column label="操作" width="100" align="center" fixed="right"><template slot-scope="{ row }"><el-button v-hasPermi="['finance:expense:edit']" :size="controlSize" type="text" @click="openExpense(row)">处理</el-button></template></el-table-column>
    </el-table>
  </div>
</template>

<script>
export default {
  name: 'FinanceDataTables',
  props: {
    mode: { type: String, required: true },
    loading: Boolean,
    list: { type: Array, default: () => [] },
    controlSize: { type: String, default: 'small' },
    dictOptions: { type: Object, default: () => ({}) },
    formatMoney: { type: Function, required: true },
    field: { type: Function, required: true },
    canCollect: { type: Function, required: true },
    canInvoice: { type: Function, required: true },
    openCaseFinance: { type: Function, required: true },
    openPayment: { type: Function, required: true },
    openReject: { type: Function, required: true },
    openInvoice: { type: Function, required: true },
    openExpense: { type: Function, required: true }
  }
}
</script>

