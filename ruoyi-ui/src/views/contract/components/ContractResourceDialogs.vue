<template><div>
    <el-dialog :title="contractForm.contractId ? '编辑合同' : '新建合同'" :visible="contractOpen" @close="$emit('update:contractOpen', false)" width="760px" :custom-class="dialogClass" append-to-body>
      <el-form ref="contract" :model="contractForm" :rules="contractRules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="关联客户" prop="customerId">
              <el-select v-model="contractForm.customerId" :size="controlSize" filterable remote reserve-keyword placeholder="搜索并选择客户" :remote-method="searchCustomerOptions" :loading="customerSelectLoading" @change="selectCustomerForContract">
                <el-option v-for="item in customerOptions" :key="item.customerId" :label="item.customerName" :value="item.customerId" :disabled="!canOperateCustomer(item)">
                  <span>{{ item.customerName }}</span>
                  <span class="select-sub">{{ item.mobile || item.companyName || item.customerNo }}</span>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="客户名称" prop="customerName"><el-input v-model="contractForm.customerName" :size="controlSize" disabled /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="合同名称" prop="contractName"><el-input v-model="contractForm.contractName" :size="controlSize" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="案件类型" prop="caseType"><el-select v-model="contractForm.caseType" :size="controlSize"><el-option v-for="item in dictOptions.law_contract_case_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="签约金额" prop="signAmount"><el-input-number v-model="contractForm.signAmount" :size="controlSize" :min="0" :precision="2" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="收费方式" prop="feeType"><el-select v-model="contractForm.feeType" :size="controlSize"><el-option v-for="item in dictOptions.law_contract_fee_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="签订方式" prop="signMethod"><el-select v-model="contractForm.signMethod" :size="controlSize"><el-option v-for="item in dictOptions.law_contract_sign_method" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="风险等级" prop="riskLevel"><el-select v-model="contractForm.riskLevel" :size="controlSize"><el-option v-for="item in dictOptions.law_contract_risk_level" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="contractForm.remark" :size="controlSize" type="textarea" :rows="3" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="$emit('update:contractOpen', false)">取消</el-button><el-button :size="controlSize" type="primary" @click="saveContract">确定</el-button></div>
    </el-dialog>

    <el-dialog :title="templateForm.templateId ? '编辑模板' : '新增模板'" :visible="templateOpen" @close="$emit('update:templateOpen', false)" width="560px" :custom-class="dialogClass" append-to-body>
      <el-form ref="template" :model="templateForm" :rules="templateRules" label-width="100px">
        <el-form-item label="模板名称" prop="templateName"><el-input v-model="templateForm.templateName" :size="controlSize" /></el-form-item>
        <el-form-item label="案件类型" prop="caseType"><el-select v-model="templateForm.caseType" :size="controlSize"><el-option v-for="item in dictOptions.law_contract_case_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="模板文件" prop="fileUrl"><file-upload v-model="templateForm.fileUrl" :limit="1" @input="syncTemplateMeta" /></el-form-item>
        <el-form-item label="文件名"><el-input v-model="templateForm.fileName" :size="controlSize" /></el-form-item>
        <el-form-item label="版本"><el-input v-model="templateForm.versionNo" :size="controlSize" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="$emit('update:templateOpen', false)">取消</el-button><el-button :size="controlSize" type="primary" @click="saveTemplate">确定</el-button></div>
    </el-dialog>

    <el-dialog :title="feeForm.planId ? '编辑收费计划' : '新增收费计划'" :visible="feeOpen" @close="$emit('update:feeOpen', false)" width="560px" :custom-class="dialogClass" append-to-body>
      <el-form ref="fee" :model="feeForm" :rules="feeRules" label-width="110px">
        <el-form-item label="所属合同" prop="contractId">
          <el-select v-model="feeForm.contractId" :size="controlSize" filterable remote reserve-keyword placeholder="搜索并选择合同" :remote-method="searchContractOptions" :loading="contractSelectLoading" @change="selectContractForFee">
            <el-option v-for="item in contractOptions" :key="item.contractId" :label="item.contractName" :value="item.contractId" :disabled="!canEditContractResource(item)">
              <span>{{ item.contractName }}</span>
              <span class="select-sub">{{ item.contractNo || item.customerName }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="期数" prop="periodNo"><el-input-number v-model="feeForm.periodNo" :size="controlSize" :min="1" /></el-form-item>
        <el-form-item label="应收金额" prop="receivableAmount"><el-input-number v-model="feeForm.receivableAmount" :size="controlSize" :min="0.01" :precision="2" /></el-form-item>
        <el-form-item label="实收金额"><el-input-number v-model="feeForm.receivedAmount" :size="controlSize" :min="0" :precision="2" /></el-form-item>
        <el-form-item label="计划收款日" prop="planReceiveDate"><el-date-picker v-model="feeForm.planReceiveDate" :size="controlSize" value-format="yyyy-MM-dd" /></el-form-item>
        <el-form-item label="确认状态"><el-select v-model="feeForm.confirmStatus" :size="controlSize" disabled><el-option v-for="item in dictOptions.law_contract_receive_status" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="开票状态"><el-select v-model="feeForm.invoiceStatus" :size="controlSize" disabled><el-option v-for="item in dictOptions.law_contract_invoice_status" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="$emit('update:feeOpen', false)">取消</el-button><el-button :size="controlSize" type="primary" @click="saveFee">确定</el-button></div>
    </el-dialog>

    <el-dialog title="上传附件" :visible="attachmentOpen" @close="$emit('update:attachmentOpen', false)" width="560px" :custom-class="dialogClass" append-to-body>
      <el-form ref="attachment" :model="attachmentForm" :rules="attachmentRules" label-width="100px">
        <el-form-item label="所属合同" prop="contractId">
          <el-select v-model="attachmentForm.contractId" :size="controlSize" filterable remote reserve-keyword placeholder="搜索并选择合同" :remote-method="searchContractOptions" :loading="contractSelectLoading" @change="selectContractForAttachment">
            <el-option v-for="item in contractOptions" :key="item.contractId" :label="item.contractName" :value="item.contractId" :disabled="!canEditContractResource(item)">
              <span>{{ item.contractName }}</span>
              <span class="select-sub">{{ item.contractNo || item.customerName }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="附件" prop="fileUrl"><file-upload v-model="attachmentForm.fileUrl" :limit="1" @input="syncAttachmentMeta" /></el-form-item>
        <el-form-item label="文件名" prop="fileName"><el-input v-model="attachmentForm.fileName" :size="controlSize" /></el-form-item>
        <el-form-item label="文件类型"><el-input v-model="attachmentForm.fileType" :size="controlSize" disabled /></el-form-item>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="$emit('update:attachmentOpen', false)">取消</el-button><el-button :size="controlSize" type="primary" @click="saveAttachment">确定</el-button></div>
    </el-dialog>

    <el-dialog title="编辑编号规则" :visible="ruleOpen" @close="$emit('update:ruleOpen', false)" width="480px" :custom-class="dialogClass" append-to-body>
      <el-form ref="rule" :model="ruleForm" :rules="ruleRules" label-width="100px">
        <el-form-item label="规则名称" prop="ruleName"><el-input v-model="ruleForm.ruleName" :size="controlSize" /></el-form-item>
        <el-form-item label="前缀" prop="prefix"><el-input v-model="ruleForm.prefix" :size="controlSize" /></el-form-item>
        <el-form-item label="日期格式" prop="datePattern"><el-input v-model="ruleForm.datePattern" :size="controlSize" /></el-form-item>
        <el-form-item label="流水长度" prop="serialLength"><el-input-number v-model="ruleForm.serialLength" :size="controlSize" :min="3" :max="12" /></el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="ruleForm.status">
            <el-radio v-for="item in dictOptions.sys_normal_disable" :key="item.value" :label="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <div slot="footer"><el-button :size="controlSize" @click="$emit('update:ruleOpen', false)">取消</el-button><el-button :size="controlSize" type="primary" @click="saveRule">确定</el-button></div>
    </el-dialog>
</div></template>
<script>
export default {
  name: 'ContractResourceDialogs',
  props: {
    contractOpen:Boolean,templateOpen:Boolean,feeOpen:Boolean,attachmentOpen:Boolean,ruleOpen:Boolean,
    contractForm:Object,contractRules:Object,templateForm:Object,templateRules:Object,feeForm:Object,feeRules:Object,
    attachmentForm:Object,attachmentRules:Object,ruleForm:Object,ruleRules:Object,
    customerOptions:Array,contractOptions:Array,customerSelectLoading:Boolean,contractSelectLoading:Boolean,
    dictOptions:Object,controlSize:String,dialogClass:String,
    searchCustomerOptions:Function,selectCustomerForContract:Function,canOperateCustomer:Function,
    syncTemplateMeta:Function,searchContractOptions:Function,selectContractForFee:Function,
    selectContractForAttachment:Function,canEditContractResource:Function,syncAttachmentMeta:Function,
    saveContract:Function,saveTemplate:Function,saveFee:Function,saveAttachment:Function,saveRule:Function
  },
  methods:{validate(type,cb){const f=this.$refs[type];if(f)f.validate(cb)},clear(type,fields){const f=this.$refs[type];if(f)f.clearValidate(fields)}}
}
</script>

