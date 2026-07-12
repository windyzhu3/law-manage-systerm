<template>
  <div>
  <el-dialog :title="assignForm.batch ? '批量分配案件' : '案件分配'" :visible="assignOpen" @close="$emit('update:assignOpen', false)" width="980px" :custom-class="dialogClass" append-to-body>
  <div class="assign-dialog-grid">
  <section>
  <div class="case-summary">
  <span>案件编号<b>{{ assignCaseRow.case_no }}</b></span>
  <span>案件名称<b>{{ assignCaseRow.case_name }}</b></span>
  <span>客户名称<b>{{ assignCaseRow.customer_name }}</b></span>
  <span>案件类型<b>{{ dictLabel('law_case_type', assignCaseRow.case_type) }}</b></span>
  <span>紧急程度<b>{{ dictLabel('law_case_urgency', assignCaseRow.urgency) }}</b></span>
  <span>来源合同<b>{{ assignCaseRow.contract_no }}</b></span>
  <span v-if="assignForm.batch">批量数量<b>{{ assignForm.caseIds.length }} 件</b></span>
  </div>
  <el-form ref="assign" :model="assignForm" :rules="assignRules" label-width="110px">
  <el-form-item label="分配方式" prop="assignMethod"><el-radio-group v-model="assignForm.assignMethod"><el-radio v-for="item in dictOptions.law_case_assign_method" :key="item.value" :label="item.value">{{ item.label }}</el-radio></el-radio-group></el-form-item>
  <el-form-item label="主办律师" prop="mainLawyerId"><el-select v-model="assignForm.mainLawyerId" :size="controlSize" filterable placeholder="请选择主办律师"><el-option v-for="item in mainLawyerOptions" :key="item.userId" :label="item.nickName" :value="item.userId" /></el-select></el-form-item>
  <el-form-item label="协办律师"><el-select v-model="assignForm.assistantLawyerIds" :size="controlSize" multiple filterable placeholder="请选择协办律师"><el-option v-for="item in assistantLawyerOptions" :key="item.userId" :label="item.nickName" :value="item.userId" /></el-select></el-form-item>
  <el-row :gutter="12">
  <el-col :span="12"><el-form-item label="优先级" prop="priority"><el-select v-model="assignForm.priority" :size="controlSize"><el-option v-for="item in dictOptions.law_case_priority" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
  <el-col :span="12"><el-form-item label="预计周期"><el-input v-model="assignForm.estimatedCycle" :size="controlSize" placeholder="如 7 天" /></el-form-item></el-col>
  </el-row>
  <el-row :gutter="12">
  <el-col :span="12"><el-form-item label="开案日期"><el-date-picker v-model="assignForm.planStartDate" :size="controlSize" value-format="yyyy-MM-dd" type="date" placeholder="请选择" /></el-form-item></el-col>
  <el-col :span="12"><el-form-item label="预计工作量"><el-input-number v-model="assignForm.estimatedWorkload" :size="controlSize" :min="1" /></el-form-item></el-col>
  </el-row>
  <el-form-item label="分配原因" prop="assignReason"><el-select v-model="assignForm.assignReason" :size="controlSize"><el-option v-for="item in dictOptions.law_case_assign_reason" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
  <el-form-item label="备注"><el-input v-model="assignForm.remark" :size="controlSize" type="textarea" :rows="3" maxlength="200" show-word-limit /></el-form-item>
  <el-form-item label="站内通知"><el-radio-group v-model="assignForm.notifyFlag"><el-radio label="Y">是</el-radio><el-radio label="N">否</el-radio></el-radio-group></el-form-item>
  </el-form>
  </section>
  <section class="lawyer-reference">
  <header><b>人员参考</b><el-button :size="controlSize" type="text" @click="loadLawyers">换一批</el-button></header>
  <article v-for="item in mainLawyerOptions.slice(0, 5)" :key="item.userId" :class="{ active: sameValue(assignForm.mainLawyerId, item.userId) }" @click="selectAssignLawyer(item)">
  <div class="reference-lawyer-head">
  <span class="owner-cell"><i>{{ avatar(item.lawyerName) }}</i>{{ item.lawyerName }}</span>
  <dict-tag :options="dictOptions.law_lawyer_role" :value="item.lawyerRole" />
  </div>
  <span class="tag-pills compact"><i v-for="specialty in specialtyList(item.specialties)" :key="specialty">{{ dictLabel('law_case_type', specialty) }}</i></span>
  <el-progress :percentage="Number(item.loadRate || 0)" :color="loadColor(item.loadRate)" />
  <small>在办 {{ item.activeCases || 0 }} · 本月分案 {{ item.monthAssigned || 0 }} · {{ dictLabel('law_lawyer_match_level', matchLevel(item)) }}</small>
  </article>
  </section>
  </div>
  <div slot="footer"><el-button :size="controlSize" @click="$emit('update:assignOpen', false)">取消</el-button><el-button :size="controlSize" type="primary" @click="saveAssign">确认分案</el-button></div>
  </el-dialog>
  
  <el-dialog title="发起转案" :visible="transferOpen" @close="$emit('update:transferOpen', false)" width="620px" :custom-class="dialogClass" append-to-body>
  <el-form ref="transfer" :model="transferForm" :rules="transferRules" label-width="110px">
  <el-form-item label="案件" prop="caseId"><el-select v-model="transferForm.caseId" :size="controlSize" filterable remote reserve-keyword placeholder="搜索并选择办理中案件" :remote-method="searchProcessingCases"><el-option v-for="item in processingCases" :key="item.case_id" :label="item.case_name" :value="item.case_id"><span>{{ item.case_name }}</span><span class="select-sub">{{ item.case_no }} · {{ item.main_lawyer_name }}</span></el-option></el-select></el-form-item>
  <el-form-item label="拟转入律师" prop="toLawyerId"><el-select v-model="transferForm.toLawyerId" :size="controlSize" filterable placeholder="请选择律师"><el-option v-for="item in mainLawyerOptions" :key="item.userId" :label="item.nickName" :value="item.userId" /></el-select></el-form-item>
  <el-form-item label="转案原因" prop="transferReason"><el-select v-model="transferForm.transferReason" :size="controlSize"><el-option v-for="item in dictOptions.law_case_transfer_reason" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
  <el-form-item label="风险等级" prop="riskLevel"><el-select v-model="transferForm.riskLevel" :size="controlSize"><el-option v-for="item in dictOptions.law_case_risk_level" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
  <el-form-item label="转案详情" prop="detail"><el-input v-model="transferForm.detail" :size="controlSize" type="textarea" :rows="4" /></el-form-item>
  </el-form>
  <div slot="footer"><el-button :size="controlSize" @click="$emit('update:transferOpen', false)">取消</el-button><el-button :size="controlSize" type="primary" @click="saveTransfer">提交</el-button></div>
  </el-dialog>
  
  <el-dialog title="编辑律师档案" :visible="profileFormOpen" @close="$emit('update:profileFormOpen', false)" width="560px" :custom-class="dialogClass" append-to-body>
  <el-form ref="profile" :model="profileForm" :rules="profileRules" label-width="110px">
  <el-form-item label="律师"><el-input :value="profileForm.nickName || profileForm.lawyerName || profileForm.userName" :size="controlSize" disabled /></el-form-item>
  <el-form-item label="业务角色" prop="lawyerRole"><el-select v-model="profileForm.lawyerRole" :size="controlSize" placeholder="请选择业务角色"><el-option v-for="item in dictOptions.law_lawyer_role" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
  <el-form-item label="专业方向" prop="specialties"><el-select v-model="profileForm.specialties" :size="controlSize" multiple placeholder="请选择专业方向"><el-option v-for="item in dictOptions.law_case_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
  <el-row :gutter="12">
  <el-col :span="12"><el-form-item label="负载上限" prop="loadLimit"><el-input-number v-model="profileForm.loadLimit" :size="controlSize" :min="1" :max="999" /></el-form-item></el-col>
  <el-col :span="12"><el-form-item label="平均响应" prop="avgResponseHours"><el-input-number v-model="profileForm.avgResponseHours" :size="controlSize" :min="0" :max="999" :precision="1" /></el-form-item></el-col>
  </el-row>
  <el-form-item label="是否可分案" prop="assignEnabled"><el-radio-group v-model="profileForm.assignEnabled"><el-radio label="Y">是</el-radio><el-radio label="N">否</el-radio></el-radio-group></el-form-item>
  <el-form-item label="备注"><el-input v-model="profileForm.remark" :size="controlSize" type="textarea" :rows="3" maxlength="200" show-word-limit /></el-form-item>
  </el-form>
  <div slot="footer"><el-button :size="controlSize" @click="$emit('update:profileFormOpen', false)">取消</el-button><el-button :size="controlSize" type="primary" @click="saveProfile">保存</el-button></div>
  </el-dialog>
  </div>
</template>
<script>
export default {
  name: 'CaseActionDialogs',
  props: {
    assignOpen: Boolean, transferOpen: Boolean, profileFormOpen: Boolean,
    assignForm: Object, assignCaseRow: Object, assignRules: Object,
    transferForm: Object, transferRules: Object, processingCases: Array,
    profileForm: Object, profileRules: Object,
    mainLawyerOptions: Array, assistantLawyerOptions: Array, dictOptions: Object,
    controlSize: String, dialogClass: String,
    dictLabel: Function, sameValue: Function, avatar: Function, specialtyList: Function,
    loadColor: Function, matchLevel: Function, loadLawyers: Function,
    selectAssignLawyer: Function, searchProcessingCases: Function,
    saveAssign: Function, saveTransfer: Function, saveProfile: Function
  },
  methods: {
    validate(type, callback) { const form = this.$refs[type]; if (form) form.validate(callback) },
    clear(type) { const form = this.$refs[type]; if (form) form.clearValidate() }
  }
}
</script>

