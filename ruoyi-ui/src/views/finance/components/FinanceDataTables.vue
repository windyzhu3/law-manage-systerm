<template>
  <div>
  
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

