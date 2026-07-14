<template>
  <div class="lead-page" :class="'lead-size-' + appSize">
    <div class="page-heading">
      <div><span class="eyebrow">LEAD MANAGEMENT</span><h2>{{ pageTitle }}</h2><p>{{ pageDescription }}</p></div>
      <el-button v-if="isLeadList && mode !== 'recycle'" v-hasPermi="['lead:add']" type="primary" icon="el-icon-plus" @click="openLeadDialog()">新增线索</el-button>
    </div>

    <dashboard-view v-if="mode === 'dashboard'" :data="dashboard" @create="openLeadDialog()" @pool="goPool" />

    <template v-else-if="isLeadList">
      <lead-hero @create="openLeadDialog()" @pool="goPool" />
      <lead-metrics :data="dashboard" />
      <div class="list-layout">
        <main>
          <div class="table-card">
            <div class="filter-toolbar">
              <el-input v-model="query.leadName" prefix-icon="el-icon-search" placeholder="搜索线索名称、单位" clearable @keyup.enter.native="search" />
              <el-select v-model="query.sourceCode" placeholder="来源：全部" clearable><el-option v-for="item in settingOptions('source')" :key="item.settingCode" :label="item.settingName" :value="item.settingCode" /></el-select>
              <el-select v-if="mode !== 'pool' && mode !== 'recycle'" v-model="query.status" placeholder="状态：全部" clearable><el-option v-for="item in dict.type.law_lead_status" :key="item.value" :label="item.label" :value="item.value" /></el-select>
              <el-select v-model="query.priority" placeholder="优先级：全部" clearable><el-option v-for="item in dict.type.law_lead_priority" :key="item.value" :label="item.label" :value="item.value" /></el-select>
              <el-button type="primary" icon="el-icon-search" @click="search">查询</el-button><el-button icon="el-icon-refresh" @click="resetSearch">重置</el-button>
            </div>
            <div class="status-tabs">
              <button v-for="item in quickTabs" :key="item.value" :class="{ active: query.status === item.value }" @click="quickStatus(item.value)">{{ item.label }} <em>{{ item.count }}</em></button>
              <el-button v-if="mode === 'recycle'" v-hasPermi="['lead:recycle:restore']" size="mini" :disabled="!ids.length" icon="el-icon-refresh-left" @click="restoreSelected">恢复选中</el-button>
            </div>
            <div class="lead-table-wrap">
              <el-table v-loading="loading" :data="leadList" size="mini" @selection-change="selection => ids = selection.map(item => item.leadId)">
                <el-table-column type="selection" width="42" align="center" />
                <el-table-column label="线索编号" prop="leadNo" min-width="142" align="center" />
                <el-table-column label="客户信息" min-width="150" align="center"><template slot-scope="{row}"><a v-if="canQueryLead" class="lead-link" @click="openDetail(row)">{{ row.contactName || row.leadName }}</a><span v-else>{{ row.contactName || row.leadName }}</span><span class="sub-text">{{ maskMobile(row.mobile) }}</span></template></el-table-column>
                <el-table-column label="来源" width="94" align="center"><template slot-scope="{row}">{{ settingName('source',row.sourceCode) }}</template></el-table-column>
                <el-table-column label="法律需求" prop="legalDemand" min-width="140" align="center" show-overflow-tooltip />
                <el-table-column label="当前状态" width="96" align="center"><template slot-scope="{row}"><dict-tag :options="dict.type.law_lead_status" :value="row.status" /></template></el-table-column>
                <el-table-column label="负责人" width="92" align="center"><template slot-scope="{row}"><span class="owner-cell"><i>{{ avatar(row.ownerName) }}</i>{{ row.ownerName || '待领取' }}</span></template></el-table-column>
                <el-table-column label="最后跟进" min-width="132" align="center"><template slot-scope="{row}"><span :class="{overdue:isOverdue(row.nextFollowTime)}">{{ row.lastFollowTime || '暂未跟进' }}</span></template></el-table-column>
                <el-table-column label="创建时间" prop="createTime" min-width="132" align="center" />
                <el-table-column label="操作" :width="operationColumnWidth" align="center" class-name="small-padding fixed-width lead-operation-column" fixed="right"><template slot-scope="{row}">
                  <template v-if="mode === 'recycle'"><el-button v-hasPermi="['lead:recycle:restore']" size="mini" type="text" icon="el-icon-refresh-left" @click="restoreOne(row)">恢复</el-button><el-button v-hasPermi="['lead:recycle:purge']" size="mini" type="text" icon="el-icon-delete" class="danger-text" @click="purgeOne(row)">彻底删除</el-button></template>
                  <template v-else-if="mode === 'pool'"><el-button v-if="canClaim(row)" v-hasPermi="['lead:pool:claim']" size="mini" type="text" icon="el-icon-user" @click="claim(row)">领取</el-button><el-button v-hasPermi="leadQueryPerms" size="mini" type="text" icon="el-icon-view" @click="openDetail(row)">详情</el-button></template>
                  <template v-else><span class="action-buttons"><el-button v-hasPermi="leadQueryPerms" size="mini" type="text" icon="el-icon-view" @click="openDetail(row)">查看</el-button><el-button v-if="canEdit(row)" v-hasPermi="['lead:edit']" size="mini" type="text" icon="el-icon-edit" @click="openLeadDialog(row)">编辑</el-button><el-button v-if="canFollow(row)" v-hasPermi="leadFollowPerms" size="mini" type="text" icon="el-icon-chat-line-round" @click="openFollowDialog(row)">跟进</el-button><el-button v-if="canAssign(row)" v-hasPermi="['lead:assign']" size="mini" type="text" icon="el-icon-user" @click="openAssignDialog(row)">分配</el-button><el-button v-if="canMovePool(row)" v-hasPermi="leadMovePoolPerms" size="mini" type="text" icon="el-icon-office-building" @click="toPool(row)">公海</el-button><el-button v-if="canConvert(row)" v-hasPermi="leadConvertPerms" size="mini" type="text" icon="el-icon-circle-check" @click="convert(row)">转化</el-button><el-button v-hasPermi="['lead:remove']" size="mini" type="text" icon="el-icon-delete" class="danger-text" @click="remove(row)">删除</el-button></span></template>
                </template></el-table-column>
              </el-table>
            </div>
            <pagination v-show="total>0" :total="total" :page.sync="query.pageNum" :limit.sync="query.pageSize" @pagination="loadLeadList" />
          </div>
        </main>
      </div>
    </template>

    <template v-else-if="mode === 'followup'">
      <div class="table-card timeline-page">
        <div class="section-title"><div><h3>跟进记录</h3><p>沉淀每一次客户触达和后续计划</p></div></div>
        <div v-loading="loading" class="follow-list">
          <article v-for="item in followupList" :key="item.followupId"><i :class="'follow-icon type-' + item.followType"><svg-icon :icon-class="followIcon(item.followType)" /></i><div><h4>{{ item.leadName }} <span>{{ followTypeName(item.followType) }}</span></h4><p>{{ item.content || '暂无详细记录' }}</p><small>{{ item.followUserName || '-' }} · {{ item.createTime || '-' }}<em v-if="item.nextFollowTime">下次跟进：{{ item.nextFollowTime }}</em></small></div><el-button v-hasPermi="['lead:followup:remove']" type="text" class="danger-text" @click="removeFollowup(item)">删除</el-button></article>
          <el-empty v-if="!followupList.length" description="暂无跟进记录" />
        </div>
        <pagination v-show="total>0" :total="total" :page.sync="followQuery.pageNum" :limit.sync="followQuery.pageSize" @pagination="loadFollowups" />
      </div>
    </template>

    <template v-else-if="mode === 'settings'">
      <div class="setting-head"><div><h3>线索配置</h3><p>维护线索来源、业务标签和无效原因</p></div><el-button v-hasPermi="['lead:settings:add']" type="primary" size="small" icon="el-icon-plus" @click="openSettingDialog()">新增配置</el-button></div>
      <div class="setting-tabs"><el-tabs v-model="settingType" @tab-click="loadSettings()"><el-tab-pane v-for="item in dict.type.law_lead_setting_type" :key="item.value" :label="item.label" :name="item.value" /></el-tabs></div>
      <div class="setting-grid"><article v-for="item in settings" :key="item.settingId"><i :style="{background:item.color}" /><div><h4>{{ item.settingName }}</h4><p>{{ item.settingCode }}</p></div><span :class="{disabled:item.status!=='0'}">{{ item.status==='0'?'启用':'停用' }}</span><footer><el-button v-hasPermi="['lead:settings:edit']" type="text" @click="openSettingDialog(item)">编辑</el-button><el-button v-hasPermi="['lead:settings:remove']" type="text" class="danger-text" @click="removeSetting(item)">删除</el-button></footer></article><el-empty v-if="!settings.length" description="暂无配置项" /></div>
    </template>

    <el-dialog :title="leadForm.leadId?'编辑线索':'新增线索'" :visible.sync="leadDialog" width="720px" append-to-body>
      <el-form ref="leadForm" :model="leadForm" :rules="leadRules" label-width="92px"><el-row :gutter="14">
        <el-col :span="12"><el-form-item label="线索名称" prop="leadName"><el-input v-model="leadForm.leadName" /></el-form-item></el-col><el-col :span="12"><el-form-item label="单位名称"><el-input v-model="leadForm.companyName" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="联系人" prop="contactName"><el-input v-model="leadForm.contactName" /></el-form-item></el-col><el-col :span="12"><el-form-item label="手机号" prop="mobile"><el-input v-model="leadForm.mobile" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="微信"><el-input v-model="leadForm.wechat" /></el-form-item></el-col><el-col :span="12"><el-form-item label="来源" prop="sourceCode"><el-select v-model="leadForm.sourceCode"><el-option v-for="item in settingOptions('source')" :key="item.settingCode" :label="item.settingName" :value="item.settingCode" /></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="优先级" prop="priority"><el-select v-model="leadForm.priority"><el-option v-for="item in dict.type.law_lead_priority" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="预计金额"><el-input-number v-model="leadForm.estimatedAmount" :min="0" :precision="2" /></el-form-item></el-col>
        <el-col :span="24"><el-form-item label="法律需求" prop="legalDemand"><el-input v-model="leadForm.legalDemand" type="textarea" :rows="3" /></el-form-item></el-col><el-col :span="24"><el-form-item label="备注"><el-input v-model="leadForm.remark" type="textarea" :rows="2" /></el-form-item></el-col>
      </el-row></el-form><div slot="footer"><el-button @click="leadDialog=false">取消</el-button><el-button type="primary" @click="saveLead">保存</el-button></div>
    </el-dialog>
    <el-dialog title="记录跟进" :visible.sync="followDialog" width="560px" append-to-body><el-form ref="followFormRef" :model="followForm" :rules="followRules" label-width="90px"><el-form-item label="跟进方式" prop="followType"><el-select v-model="followForm.followType"><el-option v-for="item in dict.type.law_lead_follow_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-form-item label="跟进结果" prop="followResult"><el-input v-model="followForm.followResult" /></el-form-item><el-form-item label="跟进内容" prop="content"><el-input v-model="followForm.content" type="textarea" :rows="4" maxlength="1000" show-word-limit /></el-form-item><el-form-item label="下次跟进"><el-date-picker v-model="followForm.nextFollowTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" /></el-form-item></el-form><div slot="footer"><el-button @click="followDialog=false">取消</el-button><el-button type="primary" @click="saveFollowup">保存记录</el-button></div></el-dialog>
    <el-dialog :title="settingForm.settingId?'编辑配置':'新增配置'" :visible.sync="settingDialog" width="480px" append-to-body><el-form ref="settingFormRef" :model="settingForm" :rules="settingRules" label-width="86px"><el-form-item label="类型" prop="settingType"><el-select v-model="settingForm.settingType"><el-option v-for="item in dict.type.law_lead_setting_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-form-item label="名称" prop="settingName"><el-input v-model="settingForm.settingName" /></el-form-item><el-form-item label="编码" prop="settingCode"><el-input v-model="settingForm.settingCode" /></el-form-item><el-form-item label="颜色"><el-color-picker v-model="settingForm.color" /></el-form-item><el-form-item label="排序"><el-input-number v-model="settingForm.orderNum" :min="0" /></el-form-item></el-form><div slot="footer"><el-button @click="settingDialog=false">取消</el-button><el-button type="primary" @click="saveSetting">保存</el-button></div></el-dialog>
    <lead-assign-dialog v-model="assignDialog" :lead="assignLeadRow" :owners="owners" :source-name="settingName('source',assignLeadRow.sourceCode)" :status-name="statusMeta(assignLeadRow.status).label" :priority-name="dictLabel('law_lead_priority', assignLeadRow.priority)" :size-class="'lead-size-' + appSize" @submit="saveAssign" />
    <lead-detail-drawer v-model="detailDrawer" :lead="detail" :followups="detailFollowups" :source-name="settingName('source',detail && detail.sourceCode)" :status="statusMeta(detail && detail.status)" :priority-name="dictLabel('law_lead_priority', detail && detail.priority)" :pool-status-name="dictLabel('law_lead_pool_status', detail && detail.poolStatus)" :follow-type-options="dict.type.law_lead_follow_type" :follow-perms="leadFollowPerms" :size-class="'lead-size-' + appSize" @refresh="loadDetailFollowups" @edit="openLeadDialog" @assign="openAssignDialog" @follow="openFollowDialog" />
  </div>
</template>

<script>
import DashboardView from './dashboard'
import LeadHero from './components/LeadHero'
import LeadMetrics from './components/LeadMetrics'
import LeadAssignDialog from './components/LeadAssignDialog'
import LeadDetailDrawer from './components/LeadDetailDrawer'
import { getDashboard,listLeadOwner,listLead,getLead,addLead,updateLead,delLead,restoreLead,purgeLead,assignLead,moveLeadToPool,claimLead,convertLead,listFollowup,addFollowup,delFollowup,listSetting,addSetting,updateSetting,delSetting } from '@/api/lead'
export default {
  name: 'Lead',
  dicts: ['law_lead_status', 'law_lead_priority', 'law_lead_follow_type', 'law_lead_pool_status', 'law_lead_setting_type'],
  components: { DashboardView, LeadHero, LeadMetrics, LeadAssignDialog, LeadDetailDrawer },
  data() {
    return {
      mode: 'dashboard',
      loading: false,
      total: 0,
      leadList: [],
      followupList: [],
      detailFollowups: [],
      ids: [],
      owners: [],
      settings: [],
      allSettings: [],
      dashboard: {},
      query: { pageNum: 1, pageSize: 10, leadName: '', sourceCode: '', status: '', priority: '' },
      followQuery: { pageNum: 1, pageSize: 10 },
      settingType: 'source',
      leadDialog: false,
      followDialog: false,
      assignDialog: false,
      settingDialog: false,
      detailDrawer: false,
      detail: null,
      assignLeadRow: {},
      leadForm: {},
      followForm: {},
      settingForm: {},
      leadRules: {
        leadName: [{ required: true, message: '请输入线索名称', trigger: 'blur' }],
        contactName: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
        mobile: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
        sourceCode: [{ required: true, message: '请选择来源', trigger: 'change' }],
        priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
        legalDemand: [{ required: true, message: '请输入法律需求', trigger: 'blur' }]
      },
      followRules: {
        followType: [{ required: true, message: '请选择跟进方式', trigger: 'change' }],
        followResult: [{ required: true, message: '请输入跟进结果', trigger: 'blur' }],
        content: [{ required: true, message: '请输入跟进内容', trigger: 'blur' }]
      },
      settingRules: {
        settingType: [{ required: true, message: '请选择配置类型', trigger: 'change' }],
        settingName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
        settingCode: [{ required: true, message: '请输入编码', trigger: 'blur' }]
      }
    }
  },
  computed: {
    appSize() { return this.$store.getters.size || 'medium' },
    isLeadList() { return ['all', 'mine', 'pool', 'recycle'].includes(this.mode) },
    leadQueryPerms() {
      if (this.mode === 'mine') return ['lead:mine:query']
      if (this.mode === 'pool') return ['lead:pool:query']
      if (this.mode === 'recycle') return ['lead:recycle:query']
      return ['lead:query']
    },
    canQueryLead() { return this.$auth.hasPermiOr(this.leadQueryPerms) },
    leadFollowPerms() { return this.mode === 'mine' ? ['lead:mine:followup'] : ['lead:followup:add'] },
    leadMovePoolPerms() { return this.mode === 'mine' ? ['lead:mine:pool:move'] : ['lead:pool:move'] },
    leadConvertPerms() { return this.mode === 'mine' ? ['lead:mine:convert'] : ['lead:convert'] },
    operationColumnWidth() {
      if (this.mode === 'recycle') return 150
      if (this.mode === 'pool') return 118
      return 310
    },
    pageTitle() { return ({ dashboard: '线索中心', all: '全部线索', mine: '我的线索', pool: '线索公海', followup: '跟进记录', recycle: '线索回收站', settings: '线索设置' })[this.mode] || '线索管理' },
    pageDescription() { return ({ dashboard: '统一管理线索获取、分配与转化，提升线索利用效率', all: '统一管理律所全部潜在客户线索', mine: '专注处理由我负责的客户咨询', pool: '领取和分配尚未归属的线索资源', followup: '沉淀每一次客户沟通和后续计划', recycle: '恢复误删线索或执行彻底删除', settings: '维护来源、标签和无效原因' })[this.mode] || '' },
    quickTabs() {
      const map = {}
      ;(this.dashboard.statuses || []).forEach(item => { map[String(item.itemName)] = item.itemValue })
      return [
        { value: '', label: '全部', count: this.metric('total') },
        { value: '0', label: this.dictLabel('law_lead_status', '0'), count: map['0'] || 0 },
        { value: '1', label: this.dictLabel('law_lead_status', '1'), count: map['1'] || 0 },
        { value: '2', label: this.dictLabel('law_lead_status', '2'), count: map['2'] || 0 },
        { value: '3', label: this.dictLabel('law_lead_status', '3'), count: map['3'] || 0 }
      ]
    }
  },
  watch: {
    '$route.query.module': {
      immediate: true,
      handler(value) {
        this.mode = value || 'dashboard'
        this.loadPage()
      }
    }
  },
  created() {
    this.loadSettings(true)
    if (this.$auth.hasPermiOr(['lead:add', 'lead:edit', 'lead:assign'])) {
      listLeadOwner().then(res => { this.owners = res.data || [] })
    }
  },
  methods: {
    loadPage() {
      if (this.mode === 'dashboard') this.loadDashboard()
      else if (this.isLeadList) { this.loadDashboard(); this.loadLeadList() }
      else if (this.mode === 'followup') this.loadFollowups()
      else if (this.mode === 'settings') this.loadSettings()
    },
    loadDashboard() { getDashboard().then(res => { this.dashboard = res.data || {} }) },
    loadLeadList() {
      this.loading = true
      listLead({ ...this.query, listMode: this.mode }).then(res => {
        this.leadList = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    loadFollowups() {
      this.loading = true
      listFollowup(this.followQuery).then(res => {
        this.followupList = res.rows || []
        this.total = res.total || 0
      }).finally(() => { this.loading = false })
    },
    loadDetailFollowups() {
      if (!this.detail) return
      listFollowup({ pageNum: 1, pageSize: 100, leadId: this.detail.leadId }).then(res => { this.detailFollowups = res.rows || [] })
    },
    loadSettings(all) {
      listSetting(all ? {} : { settingType: this.settingType }).then(res => {
        if (all) this.allSettings = res.data || []
        else this.settings = res.data || []
      })
    },
    metric(key) {
      const item = (this.dashboard.cards || []).find(card => card.metricKey === key)
      return item ? item.metricValue : 0
    },
    search() { this.query.pageNum = 1; this.loadLeadList() },
    resetSearch() {
      this.query = { pageNum: 1, pageSize: 10, leadName: '', sourceCode: '', status: '', priority: '' }
      this.loadLeadList()
    },
    quickStatus(value) { this.query.status = value; this.search() },
    dictLabel(type, value) { return this.selectDictLabel(this.dict.type[type] || [], value) || (value || '-') },
    dictTagType(type, value) {
      const item = (this.dict.type[type] || []).find(option => String(option.value) === String(value))
      return item && item.raw ? item.raw.listClass : 'info'
    },
    statusMeta(value) { return { label: this.dictLabel('law_lead_status', value), type: this.dictTagType('law_lead_status', value) } },
    settingOptions(type) { return this.allSettings.filter(item => item.settingType === type && item.status === '0') },
    settingName(type, code) {
      const item = this.allSettings.find(option => option.settingType === type && option.settingCode === code)
      return item ? item.settingName : (code || '-')
    },
    maskMobile(value) { return value ? value.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2') : '-' },
    avatar(value) { return value ? value.slice(0, 1) : '?' },
    isOverdue(time) { return time && new Date(time).getTime() < Date.now() },
    isTerminal(row) { return row && ['3', '4', '5'].includes(row.status) },
    isPool(row) { return row && row.poolStatus === '1' },
    hasOwner(row) { return !!(row && row.ownerId) },
    canEdit(row) { return !this.isTerminal(row) },
    canAssign(row) { return !this.isTerminal(row) },
    canMovePool(row) { return !this.isTerminal(row) && !this.isPool(row) },
    canClaim(row) { return !this.isTerminal(row) && this.isPool(row) },
    canFollow(row) { return !this.isTerminal(row) && !this.isPool(row) && this.hasOwner(row) },
    canConvert(row) { return !this.isTerminal(row) && !this.isPool(row) && this.hasOwner(row) && ['1', '2'].includes(row.status) },
    followTypeName(value) { return this.dictLabel('law_lead_follow_type', value) },
    followIcon(value) { return ({ phone: 'phone', wechat: 'wechat', meeting: 'peoples', email: 'email' })[value] || 'message' },
    goPool() { this.$router.push({ path: '/lead/pool', query: { module: 'pool' } }) },
    openLeadDialog(row) {
      this.leadForm = row ? { ...row } : { leadName: '', companyName: '', contactName: '', mobile: '', wechat: '', sourceCode: 'online', priority: '2', estimatedAmount: 0, legalDemand: '', remark: '' }
      this.leadDialog = true
    },
    saveLead() {
      this.$refs.leadForm.validate(valid => {
        if (!valid) return
        const action = this.leadForm.leadId ? updateLead : addLead
        action(this.leadForm).then(() => { this.$modal.msgSuccess('保存成功'); this.leadDialog = false; this.loadPage() })
      })
    },
    openDetail(row) { getLead(row.leadId).then(res => { this.detail = res.data; this.detailDrawer = true; this.loadDetailFollowups() }) },
    openFollowDialog(row) {
      this.followForm = { leadId: row.leadId, followType: 'phone', followResult: '', content: '', nextFollowTime: '' }
      this.followDialog = true
      this.$nextTick(() => { if (this.$refs.followFormRef) this.$refs.followFormRef.clearValidate() })
    },
    saveFollowup() {
      this.$refs.followFormRef.validate(valid => {
        if (!valid) return
        addFollowup(this.followForm).then(() => {
          this.$modal.msgSuccess('跟进记录已保存')
          this.followDialog = false
          this.loadPage()
          if (this.detail && this.detail.leadId === this.followForm.leadId) this.loadDetailFollowups()
        })
      })
    },
    openAssignDialog(row) { this.assignLeadRow = { ...row }; this.assignDialog = true },
    saveAssign(form) { assignLead(form).then(() => { this.$modal.msgSuccess('分配成功'); this.assignDialog = false; this.loadLeadList() }) },
    toPool(row) {
      this.$prompt('请输入进入公海的原因', '进入公海', { inputValue: '主动释放' }).then(({ value }) => moveLeadToPool({ leadId: row.leadId, reason: value })).then(() => {
        this.$modal.msgSuccess('已进入公海')
        this.loadLeadList()
      }).catch(() => {})
    },
    claim(row) { claimLead(row.leadId).then(() => { this.$modal.msgSuccess('领取成功'); this.loadLeadList() }) },
    convert(row) {
      this.$modal.confirm('确认将该线索标记为已转化吗？').then(() => convertLead(row.leadId)).then(() => {
        this.$modal.msgSuccess('转化成功')
        this.loadLeadList()
      }).catch(() => {})
    },
    remove(row) {
      this.$modal.confirm('确认将该线索移入回收站吗？').then(() => delLead(row.leadId)).then(() => {
        this.$modal.msgSuccess('已移入回收站')
        this.loadLeadList()
      }).catch(() => {})
    },
    restoreOne(row) { restoreLead(row.leadId).then(() => { this.$modal.msgSuccess('恢复成功'); this.loadLeadList() }) },
    restoreSelected() { restoreLead(this.ids.join(',')).then(() => { this.$modal.msgSuccess('恢复成功'); this.loadLeadList() }) },
    purgeOne(row) {
      this.$modal.confirm('彻底删除后不可恢复，确认继续吗？').then(() => purgeLead(row.leadId)).then(() => {
        this.$modal.msgSuccess('彻底删除成功')
        this.loadLeadList()
      }).catch(() => {})
    },
    removeFollowup(row) {
      this.$modal.confirm('确认删除该跟进记录吗？').then(() => delFollowup(row.followupId)).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.loadFollowups()
      }).catch(() => {})
    },
    openSettingDialog(row) {
      this.settingForm = row ? { ...row } : { settingType: this.settingType, settingCode: '', settingName: '', color: '#3b82f6', orderNum: 0, status: '0' }
      this.settingDialog = true
      this.$nextTick(() => { if (this.$refs.settingFormRef) this.$refs.settingFormRef.clearValidate() })
    },
    saveSetting() {
      this.$refs.settingFormRef.validate(valid => {
        if (!valid) return
        const action = this.settingForm.settingId ? updateSetting : addSetting
        action(this.settingForm).then(() => {
          this.$modal.msgSuccess('保存成功')
          this.settingDialog = false
          this.loadSettings()
          this.loadSettings(true)
        })
      })
    },
    removeSetting(row) {
      this.$modal.confirm('确认删除该配置吗？').then(() => delSetting(row.settingId)).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.loadSettings()
        this.loadSettings(true)
      }).catch(() => {})
    }
  }
}
</script>

<style lang="scss" scoped>
.lead-page{min-height:calc(100vh - 84px);padding:20px;background:#f6f8fc}.page-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:15px;h2{margin:4px 0;color:#172b4d;font-size:25px}p{margin:0;color:#7b8ba3;font-size:13px}}.eyebrow{color:#2874f0;font-size:10px;font-weight:700;letter-spacing:1.5px}.list-layout{display:grid;grid-template-columns:minmax(0,1fr) 242px;gap:14px;margin-top:14px}.table-card,.setting-tabs,.setting-head,.setting-grid article{border:1px solid #e8edf6;border-radius:10px;background:#fff;box-shadow:0 7px 16px rgba(36,73,135,.045)}.table-card{padding:12px}.filter-toolbar{display:flex;gap:8px;align-items:center;::v-deep .el-input{width:205px}::v-deep .el-select .el-input{width:116px}::v-deep .el-input__inner{height:34px;line-height:34px;font-size:12px}}.status-tabs{display:flex;align-items:center;gap:7px;margin:12px 0 9px;button{padding:5px 10px;border:1px solid transparent;border-radius:14px;color:#64748b;background:#f7f9fc;font-size:11px;cursor:pointer}button.active{border-color:#dce9ff;color:#2563eb;background:#eff5ff}em{margin-left:3px;color:#94a3b8;font-style:normal}.el-button{margin-left:auto}}.lead-table-wrap{overflow:hidden;border:1px solid #edf1f7;border-radius:7px}::v-deep .el-table{color:#475569;font-size:11px}::v-deep .el-table th.el-table__cell{padding:9px 0;color:#334155;background:#f8fafc}::v-deep .el-table td.el-table__cell{padding:7px 0}.lead-link{display:block;color:#334155;cursor:pointer}.sub-text{display:block;margin-top:3px;color:#94a3b8;font-size:10px}.status-pill{padding:4px 8px;border-radius:12px;font-size:10px}.status-0{color:#2563eb;background:#edf4ff}.status-1{color:#0891b2;background:#ecfbff}.status-2{color:#f97316;background:#fff4e8}.status-3{color:#059669;background:#eafaf3}.status-4,.status-5{color:#64748b;background:#f1f5f9}.owner-cell{display:flex;align-items:center;gap:5px;i{display:flex;align-items:center;justify-content:center;width:20px;height:20px;border-radius:50%;color:#2563eb;background:#eef4ff;font-size:10px;font-style:normal}}.overdue,.danger-text{color:#ef4444!important}.timeline-page{padding:18px}.section-title h3,.setting-head h3{margin:0;color:#253858;font-size:18px}.section-title p,.setting-head p{margin:6px 0 0;color:#94a3b8;font-size:12px}.follow-list{margin-top:14px}.follow-list article{display:grid;grid-template-columns:34px 1fr auto;gap:12px;padding:14px 5px;border-top:1px solid #edf1f7}.follow-icon{display:flex;align-items:center;justify-content:center;width:31px;height:31px;border-radius:50%;color:#fff;background:#2563eb}.type-wechat{background:#10b981}.type-meeting{background:#f59e0b}.type-email{background:#8b5cf6}.follow-list h4{margin:0;color:#334155;font-size:14px}.follow-list h4 span{margin-left:6px;color:#2563eb;font-size:11px}.follow-list p{margin:7px 0;color:#64748b;font-size:12px}.follow-list small{color:#94a3b8}.follow-list em{margin-left:16px;color:#6182b6;font-style:normal}.setting-head{display:flex;align-items:center;justify-content:space-between;padding:17px}.setting-tabs{margin-top:13px;padding:0 16px;::v-deep .el-tabs__header{margin:0}::v-deep .el-tabs__content{display:none}}.setting-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-top:13px}.setting-grid article{display:grid;grid-template-columns:13px 1fr auto;gap:10px;align-items:center;padding:15px;i{width:11px;height:11px;border-radius:50%}h4{margin:0;color:#334155;font-size:14px}p{margin:5px 0 0;color:#94a3b8;font-size:11px}span{padding:4px 7px;border-radius:10px;color:#059669;background:#eafaf3;font-size:10px}.disabled{color:#64748b;background:#f1f5f9}footer{grid-column:2 / 4;margin-top:7px;border-top:1px solid #edf1f7;padding-top:5px}}@media(max-width:1300px){.list-layout{display:block}.list-layout ::v-deep .lead-sidebar{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:13px}}@media(max-width:760px){.lead-page{padding:14px}.filter-toolbar{flex-wrap:wrap}.filter-toolbar ::v-deep .el-input{width:100%}.filter-toolbar ::v-deep .el-select .el-input{width:135px}.setting-grid{grid-template-columns:1fr}.list-layout ::v-deep .lead-sidebar{display:block}.list-layout ::v-deep .side-card{margin-top:12px}}
.lead-page{--lead-font-base:13px;--lead-font-small:12px;--lead-font-mini:11px;--lead-font-title:25px;--lead-font-section:18px;--lead-font-card:14px;--lead-font-hero:27px;--lead-font-metric:24px;font-size:var(--lead-font-base)}.lead-page.lead-size-default{--lead-font-base:14px;--lead-font-small:13px;--lead-font-mini:12px;--lead-font-title:27px;--lead-font-section:19px;--lead-font-card:15px;--lead-font-hero:29px;--lead-font-metric:25px}.lead-page.lead-size-small{--lead-font-base:12px;--lead-font-small:11px;--lead-font-mini:10px;--lead-font-title:23px;--lead-font-section:17px;--lead-font-card:13px;--lead-font-hero:25px;--lead-font-metric:23px}.lead-page.lead-size-mini{--lead-font-base:11px;--lead-font-small:10px;--lead-font-mini:9px;--lead-font-title:21px;--lead-font-section:16px;--lead-font-card:12px;--lead-font-hero:23px;--lead-font-metric:22px}.list-layout{display:block}.page-heading h2{font-size:var(--lead-font-title)}.page-heading p,.section-title p,.setting-head p{font-size:var(--lead-font-base)}.eyebrow,.sub-text,.status-pill,.owner-cell i,.setting-grid article span{font-size:var(--lead-font-mini)}.filter-toolbar ::v-deep .el-input__inner,.status-tabs button,.lead-page ::v-deep .el-table{font-size:var(--lead-font-small)}.section-title h3,.setting-head h3{font-size:var(--lead-font-section)}.follow-list h4,.setting-grid article h4{font-size:var(--lead-font-card)}.follow-list h4 span,.setting-grid article p{font-size:var(--lead-font-mini)}.follow-list p{font-size:var(--lead-font-small)}.action-buttons{white-space:nowrap}::v-deep .lead-operation-column .cell{white-space:nowrap}::v-deep .lead-operation-column .el-button--mini{font-size:var(--lead-font-small)}::v-deep .lead-operation-column .el-button + .el-button{margin-left:4px}
</style>
