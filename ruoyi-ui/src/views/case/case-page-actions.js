import { getCaseDashboard, listCaseLawyer, listCase, getCase, assignCase, batchAssignCase, listAssignment, listLawyerLoad, listLawyerSpecialty, listLawyerProfile, updateLawyerProfile, updateLawyerProfileStatus, listTransfer, requestTransfer, approveTransfer, listConfirm, handleConfirm, listCaseStatus } from '@/api/case'

export default {
  methods: {
    defaultQuery() {
      const query = { pageNum: 1, pageSize: 10 }
      if (this.$route.query.caseId) query.caseId = this.$route.query.caseId
      return query
    },
    goMode(mode) {
      this.$router.push({ path: this.$route.path, query: { module: mode } })
    },
    reset(keepMode = true) {
      this.query = this.defaultQuery()
      if (keepMode) this.loadPage()
      else this.$nextTick(this.loadPage)
    },
    search() {
      this.query.pageNum = 1
      this.loadPage()
    },
    loadPage() {
      this.loadDashboard()
      this.loadLawyerOptions()
      if (this.mode === 'assign') return this.loadAssignments()
      if (this.mode === 'transfer') return this.loadTransfers()
      if (this.mode === 'lawyer') return this.loadLawyers()
      if (this.mode === 'confirm') return this.loadConfirms()
      if (this.mode === 'status') return this.loadStatuses()
      return this.loadCases()
    },
    loadDashboard() {
      getCaseDashboard().then(res => {
        const data = res.data || {}
        this.metrics = data.cards || []
        this.lawyerLoads = data.lawyers || this.lawyerLoads
        this.specialtyStats = data.specialties || this.specialtyStats
      })
    },
    loadLawyerOptions() {
      listCaseLawyer().then(res => { this.lawyerOptions = res.data || [] })
    },
    loadCases() {
      this.loading = true
      listCase({ ...this.query, mode: 'pending' }).then(res => {
        this.cases = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    loadAssignments() {
      this.loading = true
      listAssignment(this.query).then(res => {
        this.assignments = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    loadTransfers() {
      this.loading = true
      listTransfer(this.query).then(res => {
        this.transfers = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    loadLawyers() {
      this.loading = true
      listLawyerLoad(this.query).then(res => {
        this.lawyerLoads = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
      listLawyerSpecialty(this.query).then(res => { this.specialtyStats = res.data || [] })
    },
    openProfileConfig() {
      this.profileOpen = true
      this.resetProfileQuery()
    },
    resetProfileQuery(load = true) {
      this.profileQuery = { pageNum: 1, pageSize: 10 }
      if (load) this.loadProfiles()
    },
    setProfileAssignable(value) {
      this.$set(this.profileQuery, 'assignEnabled', value)
      this.profileQuery.pageNum = 1
      this.loadProfiles()
    },
    loadProfiles() {
      this.profileLoading = true
      listLawyerProfile(this.profileQuery).then(res => {
        this.profileList = res.rows || []
        this.profileTotal = res.total || 0
      }).finally(() => {
        this.profileLoading = false
      })
    },
    openProfileForm(row) {
      const userId = row && row.userId
      if (!userId) return
      this.setProfileForm(row)
      this.profileFormOpen = true
      this.$nextTick(() => this.$refs.actionDialogs && this.$refs.actionDialogs.clear('profile'))
    },
    setProfileForm(data) {
      this.profileForm = {
        ...data,
        lawyerRole: data.lawyerRole || 'lawyer',
        specialties: this.specialtyList(data.specialties),
        loadLimit: Number(data.loadLimit || 100),
        avgResponseHours: Number(data.avgResponseHours || 4),
        assignEnabled: data.assignEnabled || 'N'
      }
    },
    filterProfileFallback() {
      const keyword = String(this.profileQuery.keyword || '').trim()
      const role = this.profileQuery.lawyerRole
      const specialty = this.profileQuery.specialty
      const assignEnabled = this.profileQuery.assignEnabled
      return (this.lawyerLoads || []).filter(item => {
        const keywordMatched = !keyword || [item.userName, item.nickName, item.lawyerName, item.deptName, item.specialties].some(value => String(value || '').indexOf(keyword) > -1)
        const roleMatched = !role || item.lawyerRole === role
        const specialtyMatched = !specialty || this.specialtyList(item.specialties).includes(specialty)
        const assignMatched = !assignEnabled || (item.assignEnabled || 'Y') === assignEnabled
        return keywordMatched && roleMatched && specialtyMatched && assignMatched
      })
    },
    saveProfile() {
      this.$refs.actionDialogs.validate('profile', valid => {
        if (!valid) return
        const payload = {
          ...this.profileForm,
          specialties: (this.profileForm.specialties || []).join(',')
        }
        updateLawyerProfile(payload).then(() => {
          this.$modal.msgSuccess('律师档案已保存')
          this.profileFormOpen = false
          this.loadProfiles()
          this.loadLawyers()
          this.loadLawyerOptions()
          this.loadDashboard()
        }).catch(() => {
          this.$modal.msgError('律师档案保存失败，请确认后端已重启并且 case:lawyer:config 权限已初始化')
        })
      })
    },
    changeProfileStatus(row) {
      if (row.assignEnabled === 'Y' && !row.profileId) {
        row.assignEnabled = 'N'
        this.$modal.msgWarning('请先编辑并保存律师档案后再启用分案')
        this.openProfileForm({ ...row, assignEnabled: 'Y' })
        return
      }
      updateLawyerProfileStatus({ userId: row.userId, assignEnabled: row.assignEnabled }).then(() => {
        this.$modal.msgSuccess(row.assignEnabled === 'Y' ? '已启用分案' : '已禁用分案')
        this.loadProfiles()
        this.loadLawyers()
        this.loadLawyerOptions()
      }).catch(() => {
        row.assignEnabled = row.assignEnabled === 'Y' ? 'N' : 'Y'
      })
    },
    loadConfirms() {
      this.loading = true
      listConfirm(this.query).then(res => {
        this.confirms = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    loadStatuses() {
      this.loading = true
      listCaseStatus(this.query).then(res => {
        this.statuses = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    openDetail(row) {
      const caseId = this.caseIdOf(row)
      this.detailCase = row || {}
      this.detailOpen = true
      if (!caseId) return
      getCase(caseId).then(res => {
        this.detailCase = res.data || row || {}
      })
    },
    openCaseFlow(row) {
      const caseId = this.caseIdOf(row)
      this.flowCase = row || {}
      this.flowStatuses = []
      this.flowOpen = true
      if (!caseId) return
      this.flowLoading = true
      listCaseStatus({ caseId, pageNum: 1, pageSize: 20 }).then(res => {
        this.flowStatuses = res.rows || []
      }).finally(() => { this.flowLoading = false })
    },
    openAssign(row, rows) {
      const selected = rows && rows.length ? rows : (row ? [row] : [])
      this.assignCaseRow = row || {}
      this.assignForm = {
        caseId: row && row.case_id,
        caseIds: selected.map(item => item.case_id),
        batch: selected.length > 1,
        assignMethod: this.dictDefault('law_case_assign_method'),
        priority: row && row.priority || this.dictDefault('law_case_priority'),
        assignReason: this.dictDefault('law_case_assign_reason'),
        estimatedWorkload: row && Number(row.estimated_workload || 24),
        notifyFlag: 'Y',
        assistantLawyerIds: []
      }
      this.assignOpen = true
      this.$nextTick(() => this.$refs.actionDialogs && this.$refs.actionDialogs.clear('assign'))
    },
    selectAssignLawyer(item) {
      this.$set(this.assignForm, 'mainLawyerId', item.userId)
    },
    saveAssign() {
      this.$refs.actionDialogs.validate('assign', valid => {
        if (!valid) return
        const assistants = this.lawyerOptions.filter(item => (this.assignForm.assistantLawyerIds || []).some(id => String(id) === String(item.userId)))
        const payload = {
          ...this.assignForm,
          assistantLawyerIds: (this.assignForm.assistantLawyerIds || []).join(','),
          assistantLawyerNames: assistants.map(item => item.nickName).join(',')
        }
        const request = payload.batch ? batchAssignCase : assignCase
        request(payload).then(() => {
          this.assignOpen = false
          this.assignCaseRow = {}
          this.$modal.msgSuccess('分案成功')
          this.assignOpen = false
          this.selectedRows = []
          this.loadPage()
          this.$nextTick(() => { this.assignOpen = false })
        })
      })
    },
    assignRecommended(lawyer) {
      listCase({ pageNum: 1, pageSize: 1, mode: 'pending' }).then(res => {
        const row = (res.rows || [])[0]
        if (!row) {
          this.$modal.msgWarning('暂无待分案案件')
          return
        }
        this.openAssign(row)
        this.$set(this.assignForm, 'mainLawyerId', lawyer.userId)
      })
    },
    openLawyerDetail(row) {
      this.lawyerDetail = row || {}
      this.lawyerDetailOpen = true
    },
    searchProcessingCases(keyword) {
      listCase({ pageNum: 1, pageSize: 20, mode: 'processing', keyword }).then(res => { this.processingCases = res.rows || [] })
    },
    openTransfer() {
      this.transferForm = { transferReason: this.dictDefault('law_case_transfer_reason'), riskLevel: this.dictDefault('law_case_risk_level') }
      this.searchProcessingCases('')
      this.transferOpen = true
      this.$nextTick(() => this.$refs.actionDialogs && this.$refs.actionDialogs.clear('transfer'))
    },
    saveTransfer() {
      this.$refs.actionDialogs.validate('transfer', valid => {
        if (!valid) return
        requestTransfer(this.transferForm).then(() => {
          this.$modal.msgSuccess('转案申请已提交')
          this.transferOpen = false
          this.loadPage()
        })
      })
    },
    openApproval(row) {
      this.approvalTransfer = row
      this.approvalForm = { transferId: row.transfer_id, action: 'passed', opinion: '' }
      this.transferDetailOpen = true
      this.$nextTick(() => { if (this.$refs.transferApprovalFormRef) this.$refs.transferApprovalFormRef.clearValidate() })
    },
    saveApproval() {
      if (!this.approvalForm.transferId) return
      this.$refs.transferApprovalFormRef.validate(valid => {
        if (!valid) return
        approveTransfer(this.approvalForm).then(() => {
          this.$modal.msgSuccess('审批完成')
          this.transferDetailOpen = false
          this.approvalForm = {}
          this.approvalTransfer = {}
          this.loadTransfers()
          this.loadDashboard()
        })
      })
    },
    handleConfirmRow(row, result) {
      const text = result === 'accepted' ? '确认接收' : '拒绝接案'
      handleConfirm({ confirmId: row.confirm_id, confirmResult: result, remark: text }).then(() => {
        this.$modal.msgSuccess(text + '成功')
        this.loadConfirms()
        this.loadDashboard()
      })
    },
    avatar(name) {
      return (name || '-').slice(0, 1)
    },
    dictDefault(type) {
      const item = (this.dict.type[type] || []).find(item => item.raw && item.raw.isDefault === 'Y') || (this.dict.type[type] || [])[0]
      return item ? item.value : undefined
    },
    dictLabel(type, value) {
      const item = (this.dict.type[type] || []).find(item => item.value === value)
      return item ? item.label : value || '-'
    },
    specialtyList(value) {
      return String(value || '').split(',').filter(Boolean)
    },
    sameValue(a, b) {
      return String(a) === String(b)
    },
    caseIdOf(row) {
      return row && (row.case_id || row.caseId)
    },
    loadColor(value) {
      const rate = Number(value || 0)
      if (rate >= 80) return '#ef4444'
      if (rate >= 50) return '#f59e0b'
      return '#10b981'
    },
    loadStatus(value) {
      const rate = Number(value || 0)
      if (rate >= 80) return 'high'
      if (rate < 20) return 'idle'
      return 'normal'
    },
    matchLevel(row) {
      const rate = Number(row && row.loadRate || 0)
      if (rate < 50) return 'high'
      if (rate < 80) return 'medium'
      return 'low'
    },
    lawyerSuggestion(row) {
      const rate = Number(row && row.loadRate || 0)
      if (row && row.assignEnabled === 'N') return '该律师当前已关闭可分案，不建议继续分配新案件。'
      if (row && row.lawyerRole === 'assistant') return '该人员为实习律师，适合作为协办律师参与案件，不建议作为主办律师。'
      if (rate >= 80) return '当前负载较高，建议仅分配紧急且专业高度匹配的案件，或优先考虑其他律师。'
      if (rate >= 50) return '当前负载适中，可承接普通案件，建议关注预计工作量和响应时长。'
      return '当前负载较低，适合优先分配专业匹配的新案件。'
    }
  }
}

