<template>
  <div class="lead-workbench" data-permission="lead:assignment-policy:list">
    <header class="lead-workbench__heading">
      <div><h2>轮转与重试策略</h2><p>按销售组和来源配置候选人员、稳定顺序及 T0/T+1/T+2 七个重试窗口。</p></div>
      <div><el-button icon="el-icon-refresh" :loading="loading" @click="load">刷新</el-button><el-button v-hasPermi="['lead:assignment-policy:edit']" type="primary" icon="el-icon-plus" @click="create">新建策略</el-button></div>
    </header>
    <div v-if="error" role="alert"><el-alert class="lead-state-error" :title="error" type="error" :closable="false" show-icon><el-button type="text" @click="load">重新加载</el-button></el-alert></div>
    <section class="lead-workbench__card policy-grid" v-loading="loading">
      <article v-for="policy in policies" :key="policy.policyId" class="policy-card" :data-testid="`policy-card-${policy.policyCode}`">
        <header><div><strong>{{ policy.policyName }}</strong><small>{{ policy.policyCode }}</small></div><el-tag size="mini" :type="policy.status==='ACTIVE'?'success':'info'">{{ policy.status }}</el-tag></header>
        <dl><dt>销售部门</dt><dd>{{ deptLabel(policy.salesDeptId) }}</dd><dt>来源</dt><dd>{{ policy.sourceCode }}</dd><dt>版本</dt><dd>v{{ policy.rowVersion }}</dd><dt>候选人数</dt><dd>{{ policy.candidates.length }}</dd></dl>
        <div class="candidate-strip">
          <span v-for="candidate in policy.candidates" :key="candidate.userId">
            <b>{{ candidate.sortOrder + 1 }}</b>{{ candidate.nickName || candidate.userName }}
            <i :class="{ unavailable: candidate.availabilityStatus !== 'AVAILABLE' }">{{ availabilityLabel(candidate.availabilityStatus) }}{{ candidate.delegateUserId ? ` · 委托给 ${ownerName(candidate.delegateUserId)}` : '' }}</i>
          </span>
        </div>
        <footer><span>运行游标由 Todo Engine 原子维护（只读）</span><el-button v-hasPermi="['lead:assignment-policy:edit']" type="text" @click="edit(policy)">编辑策略</el-button></footer>
      </article>
      <el-empty v-if="!loading && !policies.length" description="暂无轮转策略，请先创建销售组策略" :image-size="72" />
    </section>

    <el-drawer :title="form.policyId ? '编辑轮转策略' : '新建轮转策略'" :visible.sync="drawer" size="900px" append-to-body :close-on-click-modal="false" data-testid="policy-editor-drawer">
      <div class="policy-editor" data-testid="policy-editor">
        <div v-if="resourceError" role="alert"><el-alert :title="resourceError" type="error" :closable="false" show-icon /></div>
        <el-form ref="policyForm" :model="form" :rules="rules" label-width="112px">
          <section class="lead-drawer-section">
            <h3>适用范围</h3>
            <el-row :gutter="16">
              <el-col :xs="24" :sm="12"><el-form-item label="策略名称" prop="policyName"><el-input v-model.trim="form.policyName" maxlength="128" /></el-form-item></el-col>
              <el-col :xs="24" :sm="12"><el-form-item label="销售部门" prop="salesDeptId"><treeselect v-model="form.salesDeptId" :options="departments" :show-count="true" placeholder="请选择销售部门" /></el-form-item></el-col>
              <el-col :xs="24" :sm="12"><el-form-item label="线索来源" prop="sourceCode"><el-select v-model="form.sourceCode" filterable style="width:100%"><el-option label="默认（全部来源）" value="*" /><el-option v-for="item in sourceOptions" :key="item.settingCode" :label="item.settingName" :value="item.settingCode" /></el-select></el-form-item></el-col>
              <el-col :xs="24" :sm="12"><el-form-item label="时区" prop="timezone"><el-select v-model="form.timezone" style="width:100%"><el-option label="中国标准时间" value="Asia/Shanghai" /></el-select></el-form-item></el-col>
            </el-row>
          </section>
          <section class="lead-drawer-section">
            <h3>轮转候选人</h3>
            <p>使用姓名选择，不录入原始 ID；列表顺序即稳定轮转顺序，请假人员会由后端自动跳过，有委托时转给受托人。</p>
            <el-form-item label="候选人" prop="candidateUserIds">
              <el-select v-model="form.candidateUserIds" multiple filterable style="width:100%" placeholder="请选择本部门有效人员">
                <el-option v-for="user in candidateOptions" :key="user.userId" :label="userLabel(user)" :value="user.userId" />
              </el-select>
            </el-form-item>
            <div class="selected-candidates">
              <span v-for="(userId,index) in form.candidateUserIds" :key="userId"><b>{{ index+1 }}</b>{{ ownerName(userId) }}</span>
            </div>
          </section>
          <section class="lead-drawer-section">
            <h3>执行版本</h3>
            <el-row :gutter="16">
              <el-col :xs="24" :sm="12"><el-form-item label="TD-003版本" prop="templateVersionId"><el-select v-model="form.templateVersionId" style="width:100%" placeholder="请选择已发布版本"><el-option v-for="item in publishedVersions" :key="item.versionId" :label="`TD-003 · v${item.versionNo}`" :value="item.versionId" /></el-select></el-form-item></el-col>
              <el-col :xs="24" :sm="12"><el-form-item label="规则版本" prop="ruleVersionId"><el-input-number v-model="form.ruleVersionId" :min="1" :precision="0" style="width:100%" /></el-form-item></el-col>
            </el-row>
          </section>
          <section class="lead-drawer-section">
            <h3>七个联系窗口</h3>
            <el-table :data="form.windows" row-key="windowCode" size="small">
              <el-table-column label="窗口" width="120"><template slot-scope="{ row }"><strong :data-testid="`retry-window-${row.windowCode}`">{{ windowLabel(row.windowCode) }}</strong><small class="window-code">{{ row.windowCode }}</small></template></el-table-column>
              <el-table-column label="日期" width="80"><template slot-scope="{ row }">T+{{ row.dayOffset }}</template></el-table-column>
              <el-table-column label="开始" min-width="130"><template slot-scope="{ row }"><el-input-number v-if="row.windowCode==='T0'" v-model="row.startOffsetMinutes" :min="0" :precision="0" controls-position="right" /><el-time-select v-else v-model="row.startTime" :picker-options="{start:'00:00',step:'00:30',end:'23:30'}" /></template></el-table-column>
              <el-table-column label="结束/持续" min-width="130"><template slot-scope="{ row }"><el-input-number v-if="row.windowCode==='T0'" v-model="row.durationMinutes" :min="1" :precision="0" controls-position="right" /><el-time-select v-else v-model="row.endTime" :picker-options="{start:'00:30',step:'00:30',end:'23:59',minTime:row.startTime}" /></template></el-table-column>
              <el-table-column label="最大拨打" width="120"><template slot-scope="{ row }"><el-input-number v-model="row.maxAttempts" :min="1" :precision="0" controls-position="right" /></template></el-table-column>
            </el-table>
          </section>
        </el-form>
        <div v-if="conflict" class="policy-conflict" role="alert">
          <el-alert :title="`服务器版本已更新为 v${conflict.latest.rowVersion}，当前草稿基于 v${conflict.draftVersion}`" type="warning" :closable="false" show-icon>
            <p>草稿已完整保留。请比较后选择使用服务器内容，或将草稿更新到最新版本号再重试。</p>
            <el-button @click="useLatest">使用服务器版本</el-button>
            <el-button data-testid="policy-conflict-keep-draft" type="primary" plain @click="keepDraftOnLatest">保留草稿并更新版本</el-button>
          </el-alert>
        </div>
        <div v-if="submitError" role="alert"><el-alert :title="submitError" type="error" :closable="false" show-icon /></div>
      </div>
      <div class="lead-fixed-actions"><el-button @click="drawer=false">取消</el-button><el-button data-testid="policy-save-submit" v-hasPermi="['lead:assignment-policy:edit']" type="primary" :loading="saving" :disabled="saving || !!resourceError" @click="save">保存并启用</el-button></div>
    </el-drawer>
  </div>
</template>

<script>
import Treeselect from '@riophae/vue-treeselect'
import '@riophae/vue-treeselect/dist/vue-treeselect.css'
import { deptTreeSelect } from '@/api/system/user'
import { listDefinitionVersions, listDefinitions } from '@/api/todo-definition'
import { listAssignmentPolicy, listLeadOwner, listSetting, saveAssignmentPolicy } from '@/api/lead'
import { errorCode, errorMessage, parseRetryRule, policyWindows } from '../lead-todo-ui'

export default {
  name: 'LeadAssignmentPolicy',
  components: { Treeselect },
  data() {
    return {
      loading: false, saving: false, error: '', resourceError: '', submitError: '', conflict: null, policies: [], owners: [], departments: [], sourceOptions: [], publishedVersions: [], drawer: false, form: {},
      rules: {
        policyName: [{ required: true, message: '请填写策略名称', trigger: 'blur' }], salesDeptId: [{ required: true, message: '请选择销售部门', trigger: 'change' }],
        sourceCode: [{ required: true, message: '请选择来源', trigger: 'change' }], timezone: [{ required: true, message: '请选择时区', trigger: 'change' }],
        candidateUserIds: [{ type: 'array', required: true, min: 1, message: '至少选择一名候选人', trigger: 'change' }],
        templateVersionId: [{ required: true, message: '请选择 TD-003 已发布版本', trigger: 'change' }], ruleVersionId: [{ required: true, message: '请填写规则版本', trigger: 'change' }]
      }
    }
  },
  computed: {
    candidateOptions() { return this.owners.filter(user => !this.form.salesDeptId || Number(user.deptId) === Number(this.form.salesDeptId)) }
  },
  created() { this.load() },
  methods: {
    availabilityLabel(value) { return ({ AVAILABLE: '可轮转', LEAVE: '请假', UNAVAILABLE: '不可用' })[value] || value || '可轮转' },
    deptLabel(id) { const node = this.findDept(this.departments, id); return node ? node.label : `部门 #${id}` },
    findDept(nodes, id) { for (const node of nodes || []) { if (Number(node.id) === Number(id)) return node; const child = this.findDept(node.children, id); if (child) return child } return null },
    userLabel(user) { return `${user.nickName || user.userName}${user.deptName ? ` · ${user.deptName}` : ''}` },
    ownerName(id) { const user = this.owners.find(item => Number(item.userId) === Number(id)); return user ? (user.nickName || user.userName) : `用户 #${id}` },
    windowLabel(code) { return ({ T0: 'T0', T1_AM: 'T+1 上午', T1_NOON: 'T+1 中午', T1_PM: 'T+1 下午', T2_AM: 'T+2 上午', T2_NOON: 'T+2 中午', T2_PM: 'T+2 下午' })[code] || code },
    async load() {
      this.loading = true; this.error = ''
      try {
        const [policyResponse, deptResponse] = await Promise.all([listAssignmentPolicy(), deptTreeSelect()])
        this.policies = policyResponse.data || []; this.departments = deptResponse.data || []
      } catch (error) { this.error = errorMessage(error, '轮转策略加载失败') } finally { this.loading = false }
    },
    emptyForm() { return { policyId: null, policyName: '', salesDeptId: null, sourceCode: '*', expectedVersion: 0, templateVersionId: null, ruleVersionId: 1, timezone: 'Asia/Shanghai', candidateUserIds: [], windows: policyWindows() } },
    create() { this.openEditor(this.emptyForm()) },
    edit(policy) {
      this.openEditor(this.policyForm(policy))
    },
    policyForm(policy) {
      const retry = parseRetryRule(policy)
      return { policyId: policy.policyId, policyName: policy.policyName, salesDeptId: policy.salesDeptId, sourceCode: policy.sourceCode, expectedVersion: policy.rowVersion, templateVersionId: retry.templateVersionId, ruleVersionId: retry.ruleVersionId, timezone: retry.timezone, candidateUserIds: policy.candidates.map(item => item.userId), windows: retry.windows }
    },
    async openEditor(form) {
      this.form = JSON.parse(JSON.stringify(form)); this.drawer = true; this.resourceError = ''; this.submitError = ''; this.conflict = null
      try {
        const [owners, settings, definitions] = await Promise.all([listLeadOwner(), listSetting({ settingType: 'source' }), listDefinitions()])
        this.owners = owners.data || []; this.sourceOptions = (settings.data || []).filter(item => item.status === '0')
        const template = (definitions.data || []).find(item => (item.templateCode || item.template_code) === 'TD-003')
        if (!template) throw new Error('未找到 TD-003 模板')
        const versions = await listDefinitionVersions(template.templateId || template.template_id)
        this.publishedVersions = (versions.data || [])
          .filter(item => (item.status || item.versionStatus || item.version_status) === 'PUBLISHED')
          .map(item => ({ ...item, versionId: item.versionId || item.version_id, versionNo: item.versionNo || item.version_no }))
        if (!this.publishedVersions.length) throw new Error('TD-003 尚无已发布版本')
      } catch (error) { this.resourceError = errorMessage(error, '策略编辑资源加载失败') }
      this.$nextTick(() => this.$refs.policyForm && this.$refs.policyForm.clearValidate())
    },
    validWindows() {
      return this.form.windows.length === 7 && this.form.windows.every(row => row.maxAttempts > 0 && (row.windowCode === 'T0' ? row.durationMinutes > 0 : row.startTime && row.endTime && row.startTime < row.endTime))
    },
    save() {
      this.$refs.policyForm.validate(valid => {
        if (!valid) return
        if (!this.validWindows()) return this.$modal.msgError('请完整配置七个窗口，且结束时间必须晚于开始时间')
        this.saving = true; this.submitError = ''
        saveAssignmentPolicy(this.form).then(response => {
          this.$modal.msgSuccess('轮转策略已保存'); this.drawer = false
          const saved = response.data || {}; const index = this.policies.findIndex(item => item.policyId === saved.policyId)
          if (index >= 0) this.$set(this.policies, index, saved); else this.policies.push(saved)
        }).catch(error => {
          if (errorCode(error) === 'CONCURRENT_MODIFICATION') this.recoverConflict()
          else this.submitError = errorMessage(error, '策略保存失败')
        })
          .finally(() => { this.saving = false })
      })
    },
    async recoverConflict() {
      const draftVersion = this.form.expectedVersion
      try {
        const response = await listAssignmentPolicy()
        this.policies = response.data || []
        const latest = this.policies.find(item => Number(item.policyId) === Number(this.form.policyId))
        if (!latest) throw new Error('服务器上的策略已不存在')
        this.conflict = { latest, draftVersion }
        this.submitError = ''
      } catch (error) {
        this.submitError = errorMessage(error, '版本冲突且最新策略加载失败，请保留当前页面并联系管理员')
      }
    },
    useLatest() {
      this.form = JSON.parse(JSON.stringify(this.policyForm(this.conflict.latest)))
      this.conflict = null
      this.$nextTick(() => this.$refs.policyForm && this.$refs.policyForm.clearValidate())
    },
    keepDraftOnLatest() {
      this.form.expectedVersion = this.conflict.latest.rowVersion
      this.conflict = null
      this.$modal.msgWarning('草稿已保留并更新为服务器最新版本号，请比较确认后重新保存')
    }
  }
}
</script>

<style scoped lang="scss">
@import "../lead-todo-workbench.scss";
.policy-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(360px,1fr));gap:14px;padding:14px}.policy-card{padding:16px;border:1px solid #e2e8f0;border-radius:8px}.policy-card>header,.policy-card>footer{display:flex;align-items:center;justify-content:space-between;gap:12px}.policy-card header strong,.policy-card header small{display:block}.policy-card header small{margin-top:4px;color:#64748b}.policy-card dl{display:grid;grid-template-columns:80px 1fr 80px 1fr;gap:8px;margin:16px 0;color:#475569}.policy-card dt{color:#64748b}.policy-card dd{margin:0}.candidate-strip{display:flex;flex-wrap:wrap;gap:7px;margin-bottom:14px}.candidate-strip span,.selected-candidates span{padding:6px 8px;border-radius:6px;background:#f1f5f9}.candidate-strip b,.selected-candidates b{margin-right:6px;color:#0369a1}.candidate-strip i{display:block;margin-top:3px;color:#15803d;font-size:11px;font-style:normal}.candidate-strip i.unavailable{color:#b45309}.policy-card footer{padding-top:12px;border-top:1px solid #e2e8f0;color:#64748b;font-size:12px}.policy-editor{padding:0 18px 68px;background:#f8fafc}.selected-candidates{display:flex;flex-wrap:wrap;gap:7px;margin-left:112px}.window-code{display:block;margin-top:3px;color:#64748b}.policy-editor .el-input-number,.policy-editor .el-date-editor{width:100%}.policy-conflict{margin:12px 0}.policy-conflict p{margin:8px 0;color:#475569}@media(max-width:760px){.policy-grid{grid-template-columns:1fr}.policy-card dl{grid-template-columns:90px 1fr}.selected-candidates{margin-left:0}}
</style>
