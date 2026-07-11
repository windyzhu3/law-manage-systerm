<template>
  <div>
    <el-dialog title="确认回款" :visible="paymentVisible" width="520px" :custom-class="dialogClass" append-to-body @close="$emit('update:paymentVisible', false)">
      <el-form ref="payment" :model="paymentForm" :rules="paymentRules" label-width="110px">
        <el-form-item label="客户名称"><span>{{ paymentForm.customerName || '-' }}</span></el-form-item>
        <el-form-item label="应收金额"><span>{{ formatMoney(paymentForm.receivableAmount) }}</span></el-form-item>
        <el-form-item label="本次回款" prop="receivedAmount"><el-input-number v-model="paymentForm.receivedAmount" :size="controlSize" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="付款方式" prop="paymentMethod"><el-select v-model="paymentForm.paymentMethod" :size="controlSize" placeholder="请选择付款方式"><el-option v-for="item in options.law_finance_payment_method" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="处理备注"><el-input v-model="paymentForm.reason" :size="controlSize" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="$emit('update:paymentVisible', false)">取消</el-button><el-button :size="controlSize" type="primary" @click="$emit('submit-payment')">确认入账</el-button></div>
    </el-dialog>

    <el-dialog title="驳回回款" :visible="rejectVisible" width="520px" :custom-class="dialogClass" append-to-body @close="$emit('update:rejectVisible', false)">
      <el-form ref="reject" :model="rejectForm" :rules="rejectRules" label-width="100px">
        <el-form-item label="客户名称"><span>{{ rejectForm.customerName || '-' }}</span></el-form-item>
        <el-form-item label="驳回原因" prop="reason"><el-input v-model="rejectForm.reason" :size="controlSize" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="$emit('update:rejectVisible', false)">取消</el-button><el-button :size="controlSize" type="danger" @click="$emit('submit-reject')">确认驳回</el-button></div>
    </el-dialog>

    <el-dialog title="开票处理" :visible="invoiceVisible" width="520px" :custom-class="dialogClass" append-to-body @close="$emit('update:invoiceVisible', false)">
      <el-form ref="invoice" :model="invoiceForm" :rules="invoiceRules" label-width="110px">
        <el-form-item label="客户名称"><span>{{ invoiceForm.customerName || '-' }}</span></el-form-item>
        <el-form-item label="可开票金额"><span>{{ formatMoney(invoiceForm.receivedAmount || invoiceForm.receivableAmount) }}</span></el-form-item>
        <el-form-item label="开票动作" prop="invoiceStatus"><el-radio-group v-model="invoiceForm.invoiceStatus"><el-radio v-if="invoiceForm.currentInvoiceStatus !== '2'" label="2">部分开票</el-radio><el-radio label="1">{{ invoiceForm.currentInvoiceStatus === '2' ? '补齐开票' : '已开票' }}</el-radio></el-radio-group></el-form-item>
        <el-form-item label="发票类型" prop="invoiceType"><el-select v-model="invoiceForm.invoiceType" :size="controlSize" placeholder="请选择发票类型"><el-option v-for="item in options.law_finance_invoice_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="处理备注"><el-input v-model="invoiceForm.reason" :size="controlSize" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="$emit('update:invoiceVisible', false)">取消</el-button><el-button :size="controlSize" type="primary" @click="$emit('submit-invoice')">保存</el-button></div>
    </el-dialog>

    <el-dialog title="费用处理" :visible="expenseVisible" width="640px" :custom-class="dialogClass" append-to-body @close="$emit('update:expenseVisible', false)">
      <el-form ref="expense" :model="expenseForm" :rules="expenseRules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="费用类型" prop="expenseType"><el-select v-model="expenseForm.expenseType" :size="controlSize"><el-option v-for="item in options.law_case_expense_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="费用金额" prop="amount"><el-input-number v-model="expenseForm.amount" :size="controlSize" :min="0" :precision="2" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="发生日期" prop="occurDate"><el-date-picker v-model="expenseForm.occurDate" :size="controlSize" value-format="yyyy-MM-dd" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="付款状态" prop="payStatus"><el-select v-model="expenseForm.payStatus" :size="controlSize"><el-option v-for="item in options.law_case_pay_status" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="报销状态" prop="reimburseStatus"><el-select v-model="expenseForm.reimburseStatus" :size="controlSize"><el-option v-for="item in options.law_case_reimburse_status" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="凭证状态" prop="voucherStatus"><el-select v-model="expenseForm.voucherStatus" :size="controlSize"><el-option v-for="item in options.law_case_voucher_status" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="费用凭证"><file-upload v-model="expenseForm.voucherUrl" :limit="1" @input="$emit('sync-expense-file')" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="expenseForm.remark" :size="controlSize" type="textarea" :rows="3" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="$emit('update:expenseVisible', false)">取消</el-button><el-button :size="controlSize" type="primary" @click="$emit('submit-expense')">保存</el-button></div>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: 'FinanceActionDialogs',
  props: {
    paymentVisible: Boolean, rejectVisible: Boolean, invoiceVisible: Boolean, expenseVisible: Boolean,
    paymentForm: { type: Object, required: true }, rejectForm: { type: Object, required: true }, invoiceForm: { type: Object, required: true }, expenseForm: { type: Object, required: true },
    paymentRules: { type: Object, required: true }, rejectRules: { type: Object, required: true }, invoiceRules: { type: Object, required: true }, expenseRules: { type: Object, required: true },
    options: { type: Object, default: () => ({}) }, controlSize: { type: String, default: 'small' }, dialogClass: { type: String, default: '' }, formatMoney: { type: Function, required: true }
  },
  methods: { validate(type, callback) { const form = this.$refs[type]; if (form) form.validate(callback) } }
}
</script>
