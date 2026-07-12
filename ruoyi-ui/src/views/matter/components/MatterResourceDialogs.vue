<template><div>
  <el-dialog :title="expenseForm.expenseId ? '编辑费用' : '新增费用'" :visible="expenseOpen" width="620px" :custom-class="dialogClass" append-to-body @close="$emit('update:expenseOpen', false)">
    <el-form ref="expense" :model="expenseForm" :rules="expenseRules" label-width="100px">
      <el-form-item label="所属案件" prop="caseId"><el-select v-model="expenseForm.caseId" :size="controlSize" filterable remote reserve-keyword placeholder="搜索案件" :remote-method="searchMatterOptions" :loading="matterSelectLoading"><el-option v-for="item in matterOptions" :key="item.case_id" :label="item.case_name" :value="item.case_id" /></el-select></el-form-item>
      <el-form-item label="费用类型" prop="expenseType"><el-select v-model="expenseForm.expenseType" :size="controlSize"><el-option v-for="item in dictOptions.law_case_expense_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item label="金额" prop="amount"><el-input-number v-model="expenseForm.amount" :size="controlSize" :min="0.01" :precision="2" /></el-form-item>
      <el-form-item label="发生日期" prop="occurDate"><el-date-picker v-model="expenseForm.occurDate" :size="controlSize" value-format="yyyy-MM-dd" /></el-form-item>
      <el-form-item label="付款状态"><el-select v-model="expenseForm.payStatus" :size="controlSize"><el-option v-for="item in dictOptions.law_case_pay_status" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item label="报销状态"><el-select v-model="expenseForm.reimburseStatus" :size="controlSize"><el-option v-for="item in dictOptions.law_case_reimburse_status" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item label="凭证"><file-upload v-model="expenseForm.voucherUrl" :limit="1" @input="syncExpenseFile" /></el-form-item>
    </el-form>
    <div slot="footer"><el-button :size="controlSize" @click="$emit('update:expenseOpen', false)">取消</el-button><el-button :size="controlSize" type="primary" @click="saveExpense">确定</el-button></div>
  </el-dialog>
  <el-dialog title="案件文档" :visible="documentOpen" width="560px" :custom-class="dialogClass" append-to-body @close="$emit('update:documentOpen', false)">
    <el-form ref="document" :model="documentForm" :rules="documentRules" label-width="100px">
      <el-form-item label="所属案件" prop="caseId"><el-select v-model="documentForm.caseId" :size="controlSize" filterable remote reserve-keyword placeholder="搜索案件" :remote-method="searchMatterOptions" :loading="matterSelectLoading"><el-option v-for="item in matterOptions" :key="item.case_id" :label="item.case_name" :value="item.case_id" /></el-select></el-form-item>
      <el-form-item label="文档名称" prop="fileName"><el-input v-model="documentForm.fileName" :size="controlSize" /></el-form-item>
      <el-form-item label="文档类型" prop="documentType"><el-select v-model="documentForm.documentType" :size="controlSize"><el-option v-for="item in dictOptions.law_case_document_type" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item label="文件上传" prop="fileUrl"><file-upload v-model="documentForm.fileUrl" :limit="1" @input="syncDocumentFile" /></el-form-item>
      <el-form-item label="备注"><el-input v-model="documentForm.remark" :size="controlSize" type="textarea" :rows="3" /></el-form-item>
    </el-form>
    <div slot="footer"><el-button :size="controlSize" @click="$emit('update:documentOpen', false)">取消</el-button><el-button :size="controlSize" type="primary" @click="saveDocument">确定</el-button></div>
  </el-dialog>
</div></template>
<script>
export default {name:'MatterResourceDialogs',props:{expenseOpen:Boolean,documentOpen:Boolean,expenseForm:Object,expenseRules:Object,documentForm:Object,documentRules:Object,matterOptions:Array,matterSelectLoading:Boolean,dictOptions:Object,controlSize:String,dialogClass:String,searchMatterOptions:Function,syncExpenseFile:Function,syncDocumentFile:Function,saveExpense:Function,saveDocument:Function},methods:{validate(type,cb){const f=this.$refs[type];if(f)f.validate(cb)},clear(type){const f=this.$refs[type];if(f)f.clearValidate()}}}
</script>
