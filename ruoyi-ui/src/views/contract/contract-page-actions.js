import { getContractDashboard, listContractOwner, listContract, getContract, addContract, updateContract, delContract, submitContract, approvalContract, signContract, archiveContract, voidContract, terminateContract, listRule, updateRule, listTemplate, addTemplate, updateTemplate, delTemplate, listApproval, listFee, addFee, updateFee, delFee, confirmFee, rejectFee, invoiceFee, listAttachment, addAttachment, delAttachment, listStatus } from '@/api/contract'
import { listCustomer } from '@/api/customer'

export default {
  methods: {
    sumBy(list, getter) {
      return (list || []).reduce((total, item) => {
        const value = Number(getter(item) || 0)
        return total + (Number.isNaN(value) ? 0 : value)
      }, 0)
    },
    normalizeMode(value) {
      return this.availableModes.includes(value) ? value : 'list'
    },
    loadPage() {
      if (this.mode === 'list') this.loadContracts()
      else if (this.mode === 'approval') this.loadApprovalContracts()
      else if (this.mode === 'template') this.loadTemplates()
      else if (this.mode === 'fee') this.loadFees()
      else if (this.mode === 'attachment') this.loadAttachments()
      else if (this.mode === 'status') this.loadStatuses()
      else if (this.mode === 'rule') this.loadRules()
    },
    loadContracts() {
      this.loading = true
      getContractDashboard().then(res => { this.metrics = (res.data && res.data.cards) || [] })
      listContract(this.query).then(res => {
        this.contracts = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    loadApprovalContracts() {
      this.loading = true
      listContract({ ...this.query, auditStatus: this.dictValue('law_contract_audit_status', '1') }).then(res => {
        this.contracts = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    loadTemplates() { this.loading = true; listTemplate(this.query).then(res => { this.templates = res.rows || []; this.total = res.total || 0 }).finally(() => { this.loading = false }) },
    loadFees() { this.loading = true; listFee(this.query).then(res => { this.fees = res.rows || []; this.total = res.total || 0 }).finally(() => { this.loading = false }) },
    loadAttachments() { this.loading = true; listAttachment(this.query).then(res => { this.attachments = res.rows || []; this.total = res.total || 0 }).finally(() => { this.loading = false }) },
    loadStatuses() { this.loading = true; listStatus(this.query).then(res => { this.statuses = res.rows || []; this.total = res.total || 0 }).finally(() => { this.loading = false }) },
    loadRules() { listRule().then(res => { this.rules = res.data || [] }) },
    search() { this.query.pageNum = 1; this.loadPage() },
    reset() { this.query = { pageNum: 1, pageSize: this.query.pageSize || 10 }; this.loadPage() },
    applyAdvanced() { this.advancedOpen = false; this.search() },
    resetAdvanced() { this.query.signStatus = ''; this.query.ownerId = ''; this.applyAdvanced() },
    applyCustomerRouteQuery() {
      const { customerId, customerName, createContract } = this.$route.query
      if (!customerId) return
      const normalizedCustomerId = Number(customerId)
      const routeCustomerId = Number.isNaN(normalizedCustomerId) ? customerId : normalizedCustomerId
      this.contractForm = {
        ...this.contractForm,
        customerId: routeCustomerId,
        customerName
      }
      if (customerName && !this.customerOptions.some(item => String(item.customerId) === String(routeCustomerId))) {
        this.customerOptions.unshift({ customerId: routeCustomerId, customerName, status: this.normalCustomerStatus })
      }
      if (createContract === '1') {
        this.$nextTick(() => this.openContract())
      }
    },
    openAttachmentFile(row) {
      this.openBusinessFile(row.file_url || row.fileUrl)
    },
    openTemplateFile(row) {
      this.openBusinessFile(row.fileUrl || row.file_url)
    },
    isEnabled(value) { return this.sameValue(value, this.dictValue('sys_normal_disable', '0')) },
    searchCustomerOptions(keyword) {
      this.customerSelectLoading = true
      listCustomer({ pageNum: 1, pageSize: 20, customerName: keyword || '' }).then(res => {
        this.customerOptions = res.rows || []
      }).finally(() => { this.customerSelectLoading = false })
    },
    searchContractOptions(keyword) {
      this.contractSelectLoading = true
      listContract({ pageNum: 1, pageSize: 20, contractName: keyword || '' }).then(res => {
        this.contractOptions = res.rows || []
      }).finally(() => { this.contractSelectLoading = false })
    },
    ensureCustomerOption(row) {
      if (row && row.customerId && row.customerName && !this.customerOptions.some(item => item.customerId === row.customerId)) {
        this.customerOptions.unshift({ customerId: row.customerId, customerName: row.customerName, mobile: row.mobile, companyName: row.companyName, customerNo: row.customerNo, status: row.status })
      }
    },
    ensureContractOption(row) {
      const contractId = row && (row.contractId || row.contract_id)
      const contractName = row && (row.contractName || row.contract_name)
      if (contractId && contractName && !this.contractOptions.some(item => item.contractId === contractId)) {
        this.contractOptions.unshift({ contractId, contractName, contractNo: row.contractNo || row.contract_no, customerName: row.customerName, contractStatus: this.contractStatusOf(row) })
      }
    },
    clearFormValidate(refName, props) {
      this.$nextTick(() => {
        const resourceRefs = { contractForm: 'contract', templateFormRef: 'template', feeFormRef: 'fee', attachmentFormRef: 'attachment', ruleFormRef: 'rule' }
        if (resourceRefs[refName] && this.$refs.resourceDialogs) {
          this.$refs.resourceDialogs.clear(resourceRefs[refName], props)
          return
        }
        if (this.$refs[refName]) {
          this.$refs[refName].clearValidate(props)
        }
      })
    },
    normalizeMapRow(row, fields) {
      const result = { ...row }
      fields.forEach(([camel, snake]) => {
        if (result[camel] === undefined && result[snake] !== undefined) {
          result[camel] = result[snake]
        }
      })
      return result
    },
    selectCustomerForContract(customerId) {
      const item = this.customerOptions.find(customer => String(customer.customerId) === String(customerId))
      if (item) this.contractForm.customerName = item.customerName
      this.clearFormValidate('contractForm', ['customerId', 'customerName'])
    },
    selectContractForFee(contractId) {
      const item = this.contractOptions.find(contract => String(contract.contractId) === String(contractId))
      if (item) this.feeForm.contractName = item.contractName
      this.clearFormValidate('feeFormRef', ['contractId'])
    },
    selectContractForAttachment(contractId) {
      const item = this.contractOptions.find(contract => String(contract.contractId) === String(contractId))
      if (item) this.attachmentForm.contractName = item.contractName
      this.clearFormValidate('attachmentFormRef', ['contractId'])
    },
    openContract(row) {
      this.ensureCustomerOption(row)
      this.contractForm = row ? {
        ...this.normalizeMapRow(row, [
          ['contractId', 'contract_id'],
          ['contractName', 'contract_name'],
          ['customerId', 'customer_id'],
          ['customerName', 'customer_name'],
          ['caseType', 'case_type'],
          ['signAmount', 'sign_amount'],
          ['feeType', 'fee_type'],
          ['signMethod', 'sign_method'],
          ['riskLevel', 'risk_level']
        ]),
        signStatus: undefined
      } : {
        ...this.contractForm,
        caseType: this.dictDefault('law_contract_case_type'),
        feeType: this.dictDefault('law_contract_fee_type'),
        signMethod: this.dictDefault('law_contract_sign_method'),
        auditStatus: this.dictDefault('law_contract_audit_status'),
        contractStatus: this.dictDefault('law_contract_status'),
        riskLevel: this.dictDefault('law_contract_risk_level'),
        signAmount: 0
      }
      if (!row && !this.contractForm.customerId) this.searchCustomerOptions('')
      this.contractOpen = true
      this.clearFormValidate('contractForm')
    },
    openDetail(row) {
      const contractId = row.contractId
      this.detailOpen = true
      this.loadDetail(contractId, row)
    },
    loadDetail(contractId, seed = {}) {
      this.detailContract = { ...seed }
      this.detailFees = []
      this.detailAttachments = []
      this.detailApprovals = []
      this.detailStatuses = []
      const feeRequest = this.canReadFee ? listFee({ contractId, pageNum: 1, pageSize: 5 }) : Promise.resolve({ rows: [] })
      const attachmentRequest = this.canReadAttachment ? listAttachment({ contractId, pageNum: 1, pageSize: 5 }) : Promise.resolve({ rows: [] })
      const approvalRequest = this.canReadApproval ? listApproval({ contractId, pageNum: 1, pageSize: 5 }) : Promise.resolve({ rows: [] })
      const statusRequest = this.canReadStatus ? listStatus({ contractId, pageNum: 1, pageSize: 5 }) : Promise.resolve({ rows: [] })
      Promise.all([
        getContract(contractId),
        feeRequest,
        attachmentRequest,
        approvalRequest,
        statusRequest
      ]).then(([detail, fees, attachments, approvals, statuses]) => {
        this.detailContract = detail.data || {}
        this.detailFees = fees.rows || []
        this.detailAttachments = attachments.rows || []
        this.detailApprovals = approvals.rows || []
        this.detailStatuses = statuses.rows || []
      })
    },
    refreshDetailIfOpen(row) {
      const contractId = row && (row.contractId || row.contract_id)
      if (this.detailOpen && contractId) {
        this.loadDetail(contractId, row)
      }
    },
    viewMatter(row) {
      this.$router.push({ path: '/matter/list', query: { module: 'list', contractId: row.contractId || row.contract_id } })
    },
    handleDetailAction(action, row) {
      action(row)
    },
    saveContract() {
      this.$refs.resourceDialogs.validate('contract', valid => {
        if (!valid) return
        ;(this.contractForm.contractId ? updateContract : addContract)(this.contractForm).then(() => {
          this.$modal.msgSuccess('保存成功')
          this.contractOpen = false
          this.loadPage()
          this.refreshDetailIfOpen({ contractId: this.contractForm.contractId })
        })
      })
    },
    removeContract(row) { this.$modal.confirm('确认删除该合同吗？').then(() => delContract(row.contractId)).then(() => { this.$modal.msgSuccess('删除成功'); this.loadPage() }).catch(() => {}) },
    submitOne(row) {
      return submitContract(row.contractId).then(() => {
        this.$modal.msgSuccess('已提交审批')
        this.loadPage()
        this.refreshDetailIfOpen(row)
      })
    },
    signActionText(row) {
      return this.sameValue(this.signStatusOf(row), this.contractStates.signPartial) ? '补齐签署' : '签署'
    },
    invoiceActionText(row) {
      return this.isFeeInvoicePartial(row) ? '补齐开票' : '开票'
    },
    openApproval(row) {
      const action = this.dictDefault('law_contract_approval_action') || (this.approvalActionOptions[0] && this.approvalActionOptions[0].value) || 'pass'
      this.approvalForm = { contractId: row.contractId || row.contract_id, action, opinion: '' }
      this.approvalOpen = true
      this.$nextTick(() => this.$refs.lifecycleDialogs && this.$refs.lifecycleDialogs.clearApproval())
    },
    saveApproval() {
      this.$refs.lifecycleDialogs.validateApproval(valid => {
        if (!valid) return
        approvalContract(this.approvalForm).then(() => { this.$modal.msgSuccess('审批完成'); this.approvalOpen = false; this.loadPage(); this.refreshDetailIfOpen({ contractId: this.approvalForm.contractId }) })
      })
    },
    signOne(row) {
      const partial = this.sameValue(this.signStatusOf(row), this.contractStates.signPartial)
      this.signForm = {
        contractId: row.contractId || row.contract_id,
        signStatus: partial ? this.contractStates.signSigned : this.contractStates.signPartial,
        partial,
        row
      }
      this.signOpen = true
    },
    saveSign() {
      if (!this.signForm.signStatus) {
        this.$modal.msgWarning('请选择签署动作')
        return
      }
      signContract({ contractId: this.signForm.contractId, signStatus: this.signForm.signStatus }).then(() => {
        this.$modal.msgSuccess('签署成功')
        this.signOpen = false
        this.loadPage()
        this.refreshDetailIfOpen(this.signForm.row)
      }).catch(() => {})
    },
    archiveOne(row) { this.$prompt('请输入归档说明', '合同归档', this.messageBoxOptions({ inputValue: '合同归档', inputPattern: /\S+/, inputErrorMessage: '归档说明必填' })).then(({ value }) => archiveContract({ contractId: row.contractId, reason: value })).then(() => { this.$modal.msgSuccess('归档成功'); this.loadPage(); this.refreshDetailIfOpen(row) }).catch(() => {}) },
    voidOne(row) { this.$prompt('请输入作废原因', '合同作废', this.messageBoxOptions({ inputValue: '合同作废', inputPattern: /\S+/, inputErrorMessage: '作废原因必填' })).then(({ value }) => voidContract({ contractId: row.contractId, reason: value })).then(() => { this.$modal.msgSuccess('作废成功'); this.loadPage(); this.refreshDetailIfOpen(row) }).catch(() => {}) },
    terminateOne(row) { this.$prompt('请输入终止原因', '合同终止', this.messageBoxOptions({ inputPattern: /\S+/, inputErrorMessage: '终止原因必填' })).then(({ value }) => terminateContract({ contractId: row.contractId, reason: value })).then(() => { this.$modal.msgSuccess('终止成功'); this.loadPage(); this.refreshDetailIfOpen(row) }).catch(() => {}) },
    openImport() { this.$refs.importRef.open() },
    handleExport() { this.download('contract/export', { ...this.query }, `contract_${Date.now()}.xlsx`) },
    openTemplate(row) {
      this.templateForm = row ? this.normalizeMapRow(row, [
        ['templateId', 'template_id'],
        ['templateName', 'template_name'],
        ['caseType', 'case_type'],
        ['fileName', 'file_name'],
        ['fileUrl', 'file_url'],
        ['versionNo', 'version_no']
      ]) : { status: this.dictDefault('sys_normal_disable'), versionNo: 'v1', caseType: this.dictDefault('law_contract_case_type') }
      this.templateOpen = true
      this.clearFormValidate('templateFormRef')
    },
    syncFileMeta(form, value, nameKey = 'fileName', typeKey = 'fileType') {
      if (!value) return
      this.$set(form, nameKey, this.fileNameFromUrl(value))
      if (typeKey) this.$set(form, typeKey, this.fileExtFromUrl(value))
    },
    syncTemplateMeta(value) {
      this.syncFileMeta(this.templateForm, value, 'fileName', null)
      this.clearFormValidate('templateFormRef', ['fileUrl'])
    },
    saveTemplate() {
      this.$refs.resourceDialogs.validate('template', valid => {
        if (!valid) return
        this.syncTemplateMeta(this.templateForm.fileUrl)
        ;(this.templateForm.templateId ? updateTemplate : addTemplate)(this.templateForm).then(() => { this.$modal.msgSuccess('保存成功'); this.templateOpen = false; this.loadPage() })
      })
    },
    removeTemplate(row) { this.$modal.confirm('确认删除该合同模板吗？启用中的模板需要先停用。').then(() => delTemplate(row.templateId)).then(() => { this.$modal.msgSuccess('删除成功'); this.loadPage() }).catch(() => {}) },
    openFee(row) {
      this.ensureContractOption(row)
      const isFeeRow = row && row.plan_id
      this.feeForm = isFeeRow
        ? { planId: row.plan_id, contractId: row.contract_id, periodNo: row.period_no, receivableAmount: row.receivable_amount, planReceiveDate: row.plan_receive_date, receivedAmount: row.received_amount, confirmStatus: row.confirm_status, invoiceStatus: row.invoice_status }
        : { contractId: row && row.contractId, contractName: row && row.contractName, periodNo: 1, receivableAmount: 0, receivedAmount: 0, confirmStatus: this.dictDefault('law_contract_receive_status'), invoiceStatus: this.dictDefault('law_contract_invoice_status') }
      if (!row) this.searchContractOptions('')
      this.feeOpen = true
      this.clearFormValidate('feeFormRef')
    },
    saveFee() {
      this.$refs.resourceDialogs.validate('fee', valid => {
        if (!valid) return
        ;(this.feeForm.planId ? updateFee : addFee)(this.feeForm).then(() => { this.$modal.msgSuccess('保存成功'); this.feeOpen = false; this.loadPage(); this.refreshDetailIfOpen({ contractId: this.feeForm.contractId }) })
      })
    },
    removeFee(row) { this.$modal.confirm('确认删除该收费计划吗？已确认或已开票的计划不可删除。').then(() => delFee(row.plan_id)).then(() => { this.$modal.msgSuccess('删除成功'); this.loadPage(); this.refreshDetailIfOpen(row) }).catch(() => {}) },
    confirmFeeOne(row) { this.$prompt('请输入实收金额', '确认收款', this.messageBoxOptions({ inputValue: String(row.receivable_amount || 0), inputPattern: /^(?!0+(\.0+)?$)\d+(\.\d{1,2})?$/, inputErrorMessage: '请输入大于0的金额' })).then(({ value }) => confirmFee({ planId: row.plan_id, receivedAmount: value })).then(() => { this.$modal.msgSuccess('确认成功'); this.loadPage(); this.refreshDetailIfOpen(row) }).catch(() => {}) },
    rejectFeeOne(row) { this.$prompt('请输入驳回原因', '驳回收款', this.messageBoxOptions({ inputPattern: /\S+/, inputErrorMessage: '驳回原因必填' })).then(({ value }) => rejectFee({ planId: row.plan_id, reason: value })).then(() => { this.$modal.msgSuccess('已驳回'); this.loadPage(); this.refreshDetailIfOpen(row) }).catch(() => {}) },
    invoiceFeeOne(row) {
      const partial = this.isFeeInvoicePartial(row)
      this.invoiceForm = {
        planId: row.plan_id || row.planId,
        invoiceStatus: partial ? this.contractStates.invoiceIssued : this.contractStates.invoicePartial,
        partial,
        row
      }
      this.invoiceOpen = true
    },
    saveInvoice() {
      if (!this.invoiceForm.invoiceStatus) {
        this.$modal.msgWarning('请选择开票动作')
        return
      }
      invoiceFee({ planId: this.invoiceForm.planId, invoiceStatus: this.invoiceForm.invoiceStatus }).then(() => {
        this.$modal.msgSuccess('开票状态已更新')
        this.invoiceOpen = false
        this.loadPage()
        this.refreshDetailIfOpen(this.invoiceForm.row)
      }).catch(() => {})
    },
    openAttachment(row) {
      this.ensureContractOption(row)
      const isAttachmentRow = row && row.attachment_id
      this.attachmentForm = isAttachmentRow
        ? { attachmentId: row.attachment_id, contractId: row.contract_id, fileName: row.file_name, fileUrl: row.file_url, fileType: row.file_type, fileSize: row.file_size }
        : { contractId: row && row.contractId, contractName: row && row.contractName }
      if (!row) this.searchContractOptions('')
      this.attachmentOpen = true
    },
    syncAttachmentMeta(value) {
      this.syncFileMeta(this.attachmentForm, value)
      this.clearFormValidate('attachmentFormRef', ['fileUrl', 'fileName'])
    },
    saveAttachment() {
      this.$refs.resourceDialogs.validate('attachment', valid => {
        if (!valid) return
        this.syncAttachmentMeta(this.attachmentForm.fileUrl)
        addAttachment(this.attachmentForm).then(() => { this.$modal.msgSuccess('保存成功'); this.attachmentOpen = false; this.loadPage(); this.refreshDetailIfOpen({ contractId: this.attachmentForm.contractId }) })
      })
    },
    removeAttachment(row) {
      this.$modal.confirm('确认删除该合同附件吗？').then(() => delAttachment(row.attachment_id)).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.loadPage()
        this.refreshDetailIfOpen(row)
      }).catch(() => {})
    },
    openRule(row) {
      this.ruleForm = { ...row, status: row.status || this.dictDefault('sys_normal_disable') }
      this.ruleOpen = true
      this.clearFormValidate('ruleFormRef')
    },
    saveRule() {
      this.$refs.resourceDialogs.validate('rule', valid => {
        if (!valid) return
        updateRule(this.ruleForm).then(() => { this.$modal.msgSuccess('保存成功'); this.ruleOpen = false; this.loadPage() })
      })
    }
  }
}

