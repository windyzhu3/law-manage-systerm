export default {
  computed: {
    customerStates() {
      return {
        normal: this.dictValue('law_customer_status', '0'),
        disabled: this.dictValue('law_customer_status', '1'),
        merged: this.dictValue('law_customer_status', '2')
      }
    },
    normalCustomerStatus() {
      return this.customerStates.normal
    }
  },
  methods: {
    customerStatusOf(row) {
      return row && (row.status !== undefined ? row.status : row.customer_status)
    },
    customerContractCountOf(row) {
      return Number((row && (row.contractCount !== undefined ? row.contractCount : row.contract_count)) || 0)
    },
    canOperateCustomer(row) {
      return row && this.sameValue(this.customerStatusOf(row) || this.normalCustomerStatus, this.normalCustomerStatus)
    },
    canRemoveCustomer(row) {
      return this.canOperateCustomer(row) && this.customerContractCountOf(row) === 0
    },
    customerRemoveTip(row) {
      if (!this.canOperateCustomer(row)) return '当前客户状态不允许删除'
      if (this.customerContractCountOf(row) > 0) return '客户已关联合同，请先处理合同或执行客户合并'
      return ''
    }
  }
}
