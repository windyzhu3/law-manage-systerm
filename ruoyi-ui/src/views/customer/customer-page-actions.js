import { getCustomerDashboard, listCustomer, getCustomer, addCustomer, updateCustomer, delCustomer, listContact, addContact, updateContact, delContact, listFollowup, addFollowup, delFollowup, listTag, addTag, updateTag, delTag, getCustomerTags, setCustomerTags, listMergeCandidates, listMergeLogs, mergeCustomer, listCustomerOwner } from '@/api/customer'
import { listContract } from '@/api/contract'
import { listSetting } from '@/api/lead'

export default {
  methods: {
    normalizeMode(value) {
      return this.availableModes.includes(value) ? value : 'list'
    },
    loadPage() {
      if (this.mode === 'list') this.loadCustomers()
      else if (this.mode === 'contact') this.loadContacts()
      else if (this.mode === 'followup') this.loadFollowups()
      else if (this.mode === 'tag') this.loadTags()
      else if (this.mode === 'merge') this.loadMerge()
    },
    loadCustomers() {
      this.loading = true
      getCustomerDashboard().then(res => { this.metrics = (res.data && res.data.cards) || [] })
      listCustomer(this.query).then(res => {
        this.list = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    loadContacts() {
      this.loading = true
      listContact(this.query).then(res => {
        this.contacts = res.rows || []
        this.total = res.total || 0
        const customerIds = new Set(this.contacts.map(item => item.customer_id || item.customerId).filter(Boolean))
        this.contactMetrics = [
          { metricKey: 'total', metricValue: this.total },
          { metricKey: 'keyContacts', metricValue: this.contacts.filter(item => this.sameValue(item.key_contact, this.dictValue('law_yes_no_flag', '1'))).length },
          { metricKey: 'customers', metricValue: customerIds.size },
          { metricKey: 'withMobile', metricValue: this.contacts.filter(item => item.mobile).length }
        ]
      }).finally(() => { this.loading = false })
    },
    loadFollowups() {
      this.loading = true
      listFollowup(this.query).then(res => {
        this.followups = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    loadTags() { listTag({}).then(res => { this.tags = res.data || [] }) },
    loadMerge() {
      listMergeCandidates(this.mergeQuery).then(res => {
        this.mergeList = res.data || []
        this.mergeMetrics = [
          { metricKey: 'candidates', metricValue: this.mergeList.length },
          { metricKey: 'withMobile', metricValue: this.mergeList.filter(item => item.mobile).length },
          { metricKey: 'withCompany', metricValue: this.mergeList.filter(item => item.companyName || item.company_name).length },
          { metricKey: 'withCreditCode', metricValue: this.mergeList.filter(item => item.creditCode || item.credit_code).length }
        ]
      })
    },
    search() { this.query.pageNum = 1; this.loadPage() },
    reset() {
      const pageSize = this.query.pageSize || 10
      if (this.mode === 'list') this.query = { pageNum: 1, pageSize, customerName: '', customerType: '', industry: '', tagId: '', ownerId: '', customerLevel: '' }
      else if (this.mode === 'contact') this.query = { pageNum: 1, pageSize, contactName: '', customerName: '', mobile: '' }
      else this.query = { pageNum: 1, pageSize }
      this.loadPage()
    },
    openAdvanced() { this.advancedOpen = true },
    applyAdvanced() { this.advancedOpen = false; this.search() },
    resetAdvanced() { this.query.ownerId = ''; this.query.customerLevel = ''; this.applyAdvanced() },
    resetMerge() { this.mergeQuery = {}; this.loadMerge() },
    tagValue(item) { return item.tag_id !== undefined ? item.tag_id : item.tagId },
    tagLabel(item) { return item.tag_name || item.tagName },
    customerTags(row) {
      const names = String(row.tagNames || '').split(',').filter(Boolean)
      const colors = String(row.tagColors || '').split(',')
      return names.map((name, index) => ({ name, color: colors[index] || '#2563eb' }))
    },
    customerContractCountOf(row) {
      const value = row.contractCount !== undefined ? row.contractCount : row.contract_count
      const numberValue = Number(value)
      return Number.isNaN(numberValue) ? 0 : numberValue
    },
    followIcon(type) { return ({ wechat: 'message', meeting: 'peoples', email: 'email' })[type] || 'phone' },
    searchCustomerOptions(keyword) {
      this.customerSelectLoading = true
      listCustomer({ pageNum: 1, pageSize: 20, customerName: keyword || '' }).then(res => {
        this.customerOptions = res.rows || []
      }).finally(() => { this.customerSelectLoading = false })
    },
    clearFormValidate(refName, props) {
      this.$nextTick(() => {
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
    ensureCustomerOption(row) {
      const customerId = row && (row.customerId || row.customer_id)
      const customerName = row && (row.customerName || row.customer_name)
      if (customerId && customerName && !this.customerOptions.some(item => item.customerId === customerId)) {
        this.customerOptions.unshift({ customerId, customerName, mobile: row.mobile, companyName: row.companyName || row.company_name, customerNo: row.customerNo || row.customer_no, status: row.status })
      }
    },
    selectCustomerForContact(customerId) {
      const item = this.customerOptions.find(customer => String(customer.customerId) === String(customerId))
      if (item) this.contactForm.customerName = item.customerName
      this.clearFormValidate('contactFormRef', ['customerId'])
    },
    selectCustomerForFollow(customerId) {
      const item = this.customerOptions.find(customer => String(customer.customerId) === String(customerId))
      if (item) this.followForm.customerName = item.customerName
      this.clearFormValidate('followFormRef', ['customerId'])
    },
    selectMainCustomer(customerId) {
      const item = this.customerOptions.find(customer => String(customer.customerId) === String(customerId))
      if (item) this.mergeForm.mainCustomerName = item.customerName
      this.clearFormValidate('mergeFormRef', ['mainCustomerId'])
    },
    defaultLeadSource() {
      const item = this.leadSourceOptions.find(option => option.status === '0') || this.leadSourceOptions[0]
      return item ? item.settingCode : ''
    },
    defaultOwnerId() {
      const item = this.ownerOptions[0]
      return item ? item.userId : ''
    },
    openCustomer(row) {
      if (row && !this.canOperateCustomer(row)) return
      this.form = row ? this.normalizeMapRow(row, [
        ['customerId', 'customer_id'],
        ['customerName', 'customer_name'],
        ['customerType', 'customer_type'],
        ['companyName', 'company_name'],
        ['creditCode', 'credit_code'],
        ['customerLevel', 'customer_level'],
        ['mainDemand', 'main_demand'],
        ['sourceCode', 'source_code'],
        ['ownerId', 'owner_id']
      ]) : {
        customerType: this.dictDefault('law_customer_type'),
        customerLevel: this.dictDefault('law_customer_level'),
        industry: this.dictDefault('law_customer_industry'),
        sourceCode: this.defaultLeadSource(),
        ownerId: this.defaultOwnerId()
      }
      this.customerOpen = true
      this.clearFormValidate('customerForm')
    },
    openDetail(row) {
      const customerId = row.customerId
      this.detailOpen = true
      this.loadDetail(customerId, row)
    },
    loadDetail(customerId, seed = {}) {
      this.detailCustomerId = customerId
      this.detailCustomer = { ...seed }
      this.detailContacts = []
      this.detailFollowups = []
      this.detailContracts = []
      this.detailMergeLogs = []
      const contactRequest = this.canReadContact ? listContact({ customerId, pageNum: 1, pageSize: 5 }) : Promise.resolve({ rows: [] })
      const followupRequest = this.canReadFollowup ? listFollowup({ customerId, pageNum: 1, pageSize: 5 }) : Promise.resolve({ rows: [] })
      const contractRequest = this.canReadContract ? listContract({ customerId, pageNum: 1, pageSize: 5 }) : Promise.resolve({ rows: [] })
      const mergeLogRequest = this.canReadMergeLog ? listMergeLogs({ customerId, pageNum: 1, pageSize: 5 }) : Promise.resolve({ rows: [] })
      Promise.all([
        getCustomer(customerId),
        contactRequest,
        followupRequest,
        contractRequest,
        mergeLogRequest
      ]).then(([detail, contacts, followups, contracts, mergeLogs]) => {
        this.detailCustomer = detail.data || {}
        this.detailContacts = contacts.rows || []
        this.detailFollowups = followups.rows || []
        this.detailContracts = contracts.rows || []
        this.detailMergeLogs = mergeLogs.rows || []
      })
    },
    refreshDetailIfOpen(customerId) {
      const targetId = customerId || this.detailCustomerId
      if (this.detailOpen && targetId) {
        this.loadDetail(targetId, this.detailCustomer)
      }
    },
    saveCustomer() {
      this.$refs.customerForm.validate(valid => {
        if (!valid) return
        ;(this.form.customerId ? updateCustomer : addCustomer)(this.form).then(() => {
          this.$modal.msgSuccess('保存成功')
          this.customerOpen = false
          this.loadPage()
          this.refreshDetailIfOpen(this.form.customerId)
        })
      })
    },
    removeCustomer(row) { this.$modal.confirm('确认删除该客户吗？').then(() => delCustomer(row.customerId)).then(() => { this.$modal.msgSuccess('删除成功'); this.loadPage() }).catch(() => {}) },
    openImport() { this.$refs.importRef.open() },
    handleExport() { this.download('customer/export', { ...this.query }, `customer_${Date.now()}.xlsx`) },
    openContact(row) {
      this.ensureCustomerOption(row)
      const isContactRow = row && row.contact_id
      this.contactForm = isContactRow
        ? { contactId: row.contact_id, customerId: row.customer_id || row.customerId, contactName: row.contact_name, mobile: row.mobile, relationType: row.relation_type, keyContact: row.key_contact }
        : { customerId: row && row.customerId, customerName: row && row.customerName, keyContact: this.dictDefault('law_yes_no_flag'), relationType: this.dictDefault('law_contact_relation') }
      if (!row) this.searchCustomerOptions('')
      this.contactOpen = true
      this.clearFormValidate('contactFormRef')
    },
    saveContact() {
      this.$refs.contactFormRef.validate(valid => {
        if (!valid) return
        ;(this.contactForm.contactId ? updateContact : addContact)(this.contactForm).then(() => { this.$modal.msgSuccess('保存成功'); this.contactOpen = false; this.loadPage(); this.refreshDetailIfOpen(this.contactForm.customerId) })
      })
    },
    removeContact(row) {
      this.$modal.confirm('确认删除该联系人吗？').then(() => delContact(row.contact_id)).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.loadPage()
        this.refreshDetailIfOpen(row.customer_id || row.customerId)
      }).catch(() => {})
    },
    openFollowup(row) {
      if (row && !this.canOperateCustomer(row)) return
      this.ensureCustomerOption(row)
      const followType = this.dictDefault('law_customer_follow_type')
      this.followForm = row ? { customerId: row.customerId || row.customer_id, followType } : { followType }
      if (!row) this.searchCustomerOptions('')
      this.followOpen = true
      this.clearFormValidate('followFormRef')
    },
    saveFollowup() {
      this.$refs.followFormRef.validate(valid => {
        if (!valid) return
        addFollowup(this.followForm).then(() => { this.$modal.msgSuccess('保存成功'); this.followOpen = false; this.loadPage(); this.refreshDetailIfOpen(this.followForm.customerId) })
      })
    },
    removeFollowup(row) {
      this.$modal.confirm('确认删除该跟进记录吗？').then(() => delFollowup(row.followup_id)).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.loadPage()
        this.refreshDetailIfOpen(row.customer_id || row.customerId)
      }).catch(() => {})
    },
    openTag(row) {
      this.tagForm = row ? { tagId: row.tag_id, tagName: row.tag_name, tagColor: row.tag_color, orderNum: row.order_num, status: row.status } : {
        tagColor: '#3b82f6',
        orderNum: 0,
        status: this.dictDefault('sys_normal_disable')
      }
      this.tagOpen = true
      this.clearFormValidate('tagFormRef')
    },
    saveTag() {
      this.$refs.tagFormRef.validate(valid => {
        if (!valid) return
        ;(this.tagForm.tagId ? updateTag : addTag)(this.tagForm).then(() => { this.$modal.msgSuccess('保存成功'); this.tagOpen = false; this.loadPage() })
      })
    },
    removeTag(row) { this.$modal.confirm('确认删除该客户标签吗？').then(() => delTag(row.tag_id)).then(() => { this.$modal.msgSuccess('删除成功'); this.loadPage() }).catch(() => {}) },
    openCustomerTags(row) {
      if (!this.canOperateCustomer(row)) return
      this.customerTagForm = { customerId: row.customerId, customerName: row.customerName, tagIds: [] }
      this.customerTagOptions = []
      Promise.all([
        listTag({ status: this.dictValue('sys_normal_disable', '0') }),
        getCustomerTags(row.customerId)
      ]).then(([tags, selected]) => {
        this.customerTagOptions = tags.data || []
        this.customerTagForm.tagIds = (selected.data || []).map(item => Number(item))
        this.customerTagOpen = true
      })
    },
    saveCustomerTags() {
      setCustomerTags(this.customerTagForm.customerId, this.customerTagForm.tagIds).then(() => {
        this.$modal.msgSuccess('标签保存成功')
        this.customerTagOpen = false
        this.loadPage()
        this.refreshDetailIfOpen(this.customerTagForm.customerId)
      })
    },
    openMerge(row) {
      this.ensureCustomerOption(row)
      this.mergeForm = {
        mainCustomerId: null,
        mainCustomerName: '',
        mergedCustomerId: row.customerId,
        mergedCustomerName: row.customerName,
        content: '手工合并'
      }
      this.searchCustomerOptions('')
      this.mergeOpen = true
      this.clearFormValidate('mergeFormRef')
    },
    saveMerge() {
      this.$refs.mergeFormRef.validate(valid => {
        if (!valid) return
        if (this.mergeForm.mainCustomerId === this.mergeForm.mergedCustomerId) {
          this.$modal.msgError('主客户和待合并客户不能相同')
          return
        }
        mergeCustomer(this.mergeForm).then(() => {
          this.$modal.msgSuccess('合并成功')
          this.mergeOpen = false
          this.loadMerge()
          this.refreshDetailIfOpen(this.mergeForm.mainCustomerId)
        })
      })
    },
    newContract(row) {
      if (!this.canOperateCustomer(row)) return
      this.$router.push({ path: '/contract/list', query: { module: 'list', customerId: row.customerId, customerName: row.customerName, createContract: '1' } })
    },
    viewMatter(row) {
      this.$router.push({ path: '/matter/list', query: { module: 'list', customerId: row.customerId } })
    }
  }
}

