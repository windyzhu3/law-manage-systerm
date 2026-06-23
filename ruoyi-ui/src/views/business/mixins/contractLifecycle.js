export default {
  computed: {
    contractStates() {
      return {
        auditPending: this.dictValue('law_contract_audit_status', '0'),
        auditReviewing: this.dictValue('law_contract_audit_status', '1'),
        auditPassed: this.dictValue('law_contract_audit_status', '2'),
        auditRejected: this.dictValue('law_contract_audit_status', '3'),
        auditBack: this.dictValue('law_contract_audit_status', '4'),
        statusDraft: this.dictValue('law_contract_status', '0'),
        statusPerforming: this.dictValue('law_contract_status', '1'),
        statusArchived: this.dictValue('law_contract_status', '2'),
        statusVoided: this.dictValue('law_contract_status', '3'),
        statusTerminated: this.dictValue('law_contract_status', '4'),
        signSigned: this.dictValue('law_contract_sign_status', '1'),
        signPartial: this.dictValue('law_contract_sign_status', '2'),
        receivePending: this.dictValue('law_contract_receive_status', '0'),
        receiveConfirmed: this.dictValue('law_contract_receive_status', '1'),
        receiveRejected: this.dictValue('law_contract_receive_status', '2'),
        invoiceNone: this.dictValue('law_contract_invoice_status', '0'),
        invoiceIssued: this.dictValue('law_contract_invoice_status', '1'),
        invoicePartial: this.dictValue('law_contract_invoice_status', '2')
      }
    },
    contractTerminalStatuses() {
      return [
        this.contractStates.statusArchived,
        this.contractStates.statusVoided,
        this.contractStates.statusTerminated
      ]
    }
  },
  methods: {
    contractStatusOf(row) {
      return row && (row.contractStatus !== undefined ? row.contractStatus : row.contract_status)
    },
    auditStatusOf(row) {
      return row && (row.auditStatus !== undefined ? row.auditStatus : row.audit_status)
    },
    signStatusOf(row) {
      return row && (row.signStatus !== undefined ? row.signStatus : row.sign_status)
    },
    receiveStatusOf(row) {
      return row && (row.confirm_status !== undefined ? row.confirm_status : row.confirmStatus)
    },
    invoiceStatusOf(row) {
      return row && (row.invoice_status !== undefined ? row.invoice_status : row.invoiceStatus)
    },
    isContractAuditReviewing(row) {
      return row && this.sameValue(this.auditStatusOf(row), this.contractStates.auditReviewing)
    },
    canSubmitContract(row) {
      return row && this.hasValue([this.contractStates.auditPending, this.contractStates.auditRejected, this.contractStates.auditBack], this.auditStatusOf(row)) &&
        this.sameValue(this.contractStatusOf(row), this.contractStates.statusDraft)
    },
    canEditContract(row) {
      return row && this.hasValue([this.contractStates.auditPending, this.contractStates.auditRejected, this.contractStates.auditBack], this.auditStatusOf(row)) &&
        !this.hasValue(this.contractTerminalStatuses, this.contractStatusOf(row))
    },
    canRemoveContract(row) {
      return row && !this.sameValue(this.auditStatusOf(row), this.contractStates.auditReviewing) &&
        !this.hasValue([this.contractStates.statusPerforming, this.contractStates.statusArchived], this.contractStatusOf(row))
    },
    canSignContract(row) {
      return row && this.sameValue(this.auditStatusOf(row), this.contractStates.auditPassed) &&
        !this.sameValue(this.signStatusOf(row), this.contractStates.signSigned) &&
        !this.hasValue(this.contractTerminalStatuses, this.contractStatusOf(row)) &&
        (
          this.sameValue(this.contractStatusOf(row), this.contractStates.statusDraft) ||
          (this.sameValue(this.contractStatusOf(row), this.contractStates.statusPerforming) &&
            this.sameValue(this.signStatusOf(row), this.contractStates.signPartial))
        )
    },
    canArchiveContract(row) {
      return row && this.sameValue(this.contractStatusOf(row), this.contractStates.statusPerforming)
    },
    canVoidContract(row) {
      return row && !this.sameValue(this.auditStatusOf(row), this.contractStates.auditReviewing) &&
        this.sameValue(this.contractStatusOf(row), this.contractStates.statusDraft)
    },
    canTerminateContract(row) {
      return row && this.sameValue(this.contractStatusOf(row), this.contractStates.statusPerforming)
    },
    canEditContractResource(row) {
      return row && !this.hasValue(this.contractTerminalStatuses, this.contractStatusOf(row))
    },
    canCollectFee(row) {
      return row && this.sameValue(this.contractStatusOf(row), this.contractStates.statusPerforming)
    },
    canConfirmFee(row) {
      return this.canCollectFee(row) && this.isFeePending(row)
    },
    canRejectFee(row) {
      return this.canCollectFee(row) && this.isFeePending(row)
    },
    canInvoiceFee(row) {
      return this.canCollectFee(row) && this.isFeeConfirmed(row) &&
        (this.isFeeInvoiceNone(row) || this.isFeeInvoicePartial(row))
    },
    isFeeConfirmed(row) {
      return row && this.sameValue(this.receiveStatusOf(row), this.contractStates.receiveConfirmed)
    },
    isFeePending(row) {
      return row && this.sameValue(this.receiveStatusOf(row), this.contractStates.receivePending)
    },
    isFeeInvoiced(row) {
      return row && this.sameValue(this.invoiceStatusOf(row), this.contractStates.invoiceIssued)
    },
    isFeeInvoiceNone(row) {
      return row && this.sameValue(this.invoiceStatusOf(row), this.contractStates.invoiceNone)
    },
    isFeeInvoicePartial(row) {
      return row && this.sameValue(this.invoiceStatusOf(row), this.contractStates.invoicePartial)
    },
    canEditFeePlan(row) {
      return this.canEditContractResource(row) && !this.isFeeConfirmed(row) && this.isFeeInvoiceNone(row)
    },
    feePlanEditTip(row) {
      if (!this.canEditContractResource(row)) return '归档、作废或终止的合同不允许维护收费计划'
      if (this.isFeeConfirmed(row)) return '已确认收款的计划不可编辑或删除'
      if (!this.isFeeInvoiceNone(row)) return '已开票或部分开票的计划不可编辑或删除'
      return ''
    },
    contractResourceEditTip(row) {
      if (!this.canEditContractResource(row)) return '归档、作废或终止的合同不允许维护附件'
      return ''
    }
  }
}
