<template><div>
    <el-dialog :title="matterForm.caseId ? '编辑案件' : '新增案件'" :visible="matterOpen" @close="$emit('update:matterOpen', false)" width="760px" :custom-class="dialogClass" append-to-body>
      <el-form ref="matter" :model="matterForm" :rules="matterRules" label-width="100px"><el-row :gutter="12">
        <el-col :span="24" v-if="matterForm.contractId || matterForm.customerName || matterForm.contractNo">
          <div class="system-info-strip">
            <div><span>来源合同</span><b>{{ matterForm.contractNo || '-' }}</b></div>
            <div><span>客户</span><b>{{ matterForm.customerName || '-' }}</b></div>
            <div><span>案件类型</span><b>{{ dictLabel('law_case_type', matterForm.caseType) || '-' }}</b></div>
            <div><span>争议/合同金额</span><b>{{ formatMoney(matterForm.disputeAmount) }}</b></div>
          </div>
        </el-col>
        <el-col :span="12"><el-form-item label="来源合同" prop="contractId"><el-select v-model="matterForm.contractId" :size="controlSize" filterable remote reserve-keyword placeholder="搜索合同" :remote-method="searchContracts" :loading="contractLoading" :disabled="!!matterForm.caseId" @change="selectContract"><el-option v-for="item in contractOptions" :key="item.contractId" :label="item.contractName" :value="item.contractId"><span>{{ item.contractName }}</span><span class="select-sub">{{ item.contractNo || item.customerName }}</span></el-option></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="案件名称" prop="caseName"><el-input v-model="matterForm.caseName" :size="controlSize" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="案件类型" prop="caseType"><el-select v-model="matterForm.caseType" :size="controlSize" :disabled="!!matterForm.contractId" @change="handleMatterTypeChange"><el-option v-for="item in dictOptions.law_case_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="办理阶段"><el-select v-model="matterForm.caseStage" :size="controlSize" disabled><el-option v-for="item in dictOptions.law_case_stage" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="风险等级"><el-select v-model="matterForm.riskLevel" :size="controlSize"><el-option v-for="item in dictOptions.law_case_risk_level" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="案由"><el-select v-model="matterForm.cause" :size="controlSize" filterable clearable placeholder="请选择案由"><el-option v-for="item in dictOptions.law_case_cause" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="争议金额"><el-input-number v-model="matterForm.disputeAmount" :size="controlSize" :min="0" :precision="2" disabled /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="法院/机构"><el-input v-model="matterForm.courtName" :size="controlSize" /></el-form-item></el-col>
        <el-col :span="24"><el-form-item label="案件概况"><el-input v-model="matterForm.caseSummary" :size="controlSize" type="textarea" :rows="3" /></el-form-item></el-col>
        <el-col v-if="fieldConfigs.length" :span="24"><div class="form-section-title">专属信息 <span>{{ dictLabel('law_case_type', matterForm.caseType) }}</span></div></el-col>
        <el-col v-for="field in fieldConfigs" :key="field.field_code" :span="field.field_type === 'textarea' ? 24 : 12">
          <el-form-item :label="field.field_name" :required="field.required_flag === 'Y'">
            <el-input v-if="field.field_type === 'textarea'" v-model="field.fieldValue" :size="controlSize" type="textarea" :rows="3" :placeholder="field.placeholder || ('请输入' + field.field_name)" />
            <el-date-picker v-else-if="field.field_type === 'date'" v-model="field.fieldValue" :size="controlSize" value-format="yyyy-MM-dd" :placeholder="field.placeholder || ('请选择' + field.field_name)" />
            <el-input-number v-else-if="field.field_type === 'number'" v-model="field.fieldValue" :size="controlSize" :min="0" :precision="2" />
            <el-switch v-else-if="field.field_type === 'switch'" v-model="field.fieldValue" active-value="Y" inactive-value="N" />
            <el-input v-else v-model="field.fieldValue" :size="controlSize" :placeholder="field.placeholder || ('请输入' + field.field_name)" />
            <div v-if="field.help_text" class="field-help">{{ field.help_text }}</div>
          </el-form-item>
        </el-col>
      </el-row></el-form>
      <div slot="footer"><el-button :size="controlSize" @click="$emit('update:matterOpen', false)">取消</el-button><el-button :size="controlSize" type="primary" @click="saveMatter">确定</el-button></div>
    </el-dialog>

    <el-dialog :title="progressForm.progressId ? '编辑进度' : '新增进度'" :visible="progressOpen" @close="$emit('update:progressOpen', false)" width="620px" :custom-class="dialogClass" append-to-body>
      <el-form ref="progress" :model="progressForm" :rules="progressRules" label-width="100px">
        <el-form-item label="所属案件" prop="caseId"><el-select v-model="progressForm.caseId" :size="controlSize" filterable remote reserve-keyword placeholder="搜索案件" :remote-method="searchMatterOptions" :loading="matterSelectLoading"><el-option v-for="item in matterOptions" :key="item.case_id" :label="item.case_name" :value="item.case_id"><span>{{ item.case_name }}</span><span class="select-sub">{{ item.case_no }}</span></el-option></el-select></el-form-item>
        <el-form-item label="进展内容" prop="content"><el-input v-model="progressForm.content" :size="controlSize" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="下一步计划"><el-input v-model="progressForm.nextPlan" :size="controlSize" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="同步客户"><el-switch v-model="progressForm.syncCustomer" active-value="Y" inactive-value="N" /></el-form-item>
        <el-form-item label="附件"><file-upload v-model="progressForm.attachmentUrl" :limit="1" @input="syncProgressFile" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="$emit('update:progressOpen', false)">取消</el-button><el-button :size="controlSize" type="primary" @click="saveProgress">确定</el-button></div>
    </el-dialog>
</div></template>
<script>
export default {name:'MatterCoreDialogs',props:{matterOpen:Boolean,progressOpen:Boolean,matterForm:Object,matterRules:Object,progressForm:Object,progressRules:Object,matterOptions:Array,matterSelectLoading:Boolean,dictOptions:Object,controlSize:String,dialogClass:String,searchMatterOptions:Function,saveMatter:Function,saveProgress:Function},methods:{validate(type,cb){const f=this.$refs[type];if(f)f.validate(cb)},clear(type){const f=this.$refs[type];if(f)f.clearValidate()}}}
</script>

